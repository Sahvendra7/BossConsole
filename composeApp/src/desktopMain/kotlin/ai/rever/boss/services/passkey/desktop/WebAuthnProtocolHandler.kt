package ai.rever.boss.services.passkey.desktop

import ai.rever.boss.services.passkey.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets
import java.security.interfaces.ECPublicKey
import java.util.*

/**
 * Handles WebAuthn protocol operations including client data creation,
 * attestation object generation, and result parsing
 */
class WebAuthnProtocolHandler {
    
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    
    /**
     * Create proper WebAuthn clientDataJSON and encode it as base64url
     */
    fun createClientDataJSON(challenge: String, type: String, origin: String = "https://api.risaboss.com"): String {
        @Serializable
        data class ClientData(
            val type: String,
            val challenge: String,
            val origin: String,
            val crossOrigin: Boolean
        )
        
        val clientData = ClientData(
            type = type,
            challenge = challenge,
            origin = origin,
            crossOrigin = false
        )
        
        val clientDataJSON = json.encodeToString(clientData)
        val clientDataBytes = clientDataJSON.toByteArray(StandardCharsets.UTF_8)
        
        // Encode as base64url (WebAuthn standard)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(clientDataBytes)
    }
    
    /**
     * Create a minimal valid CBOR-encoded attestation object
     * Based on the format expected by the server's extractPublicKeyFromAttestation function
     */
    fun createCBORAttestationObject(rpId: String, publicKey: ECPublicKey, credentialId: String): String {
        // Extract the public key coordinates
        val w = publicKey.w
        
        // Convert coordinates to 32-byte arrays
        val xBytes = w.affineX.toByteArray().let { bytes ->
            when {
                bytes.size == 32 -> bytes
                bytes.size > 32 -> bytes.takeLast(32).toByteArray()
                else -> ByteArray(32 - bytes.size) + bytes
            }
        }
        val yBytes = w.affineY.toByteArray().let { bytes ->
            when {
                bytes.size == 32 -> bytes
                bytes.size > 32 -> bytes.takeLast(32).toByteArray()
                else -> ByteArray(32 - bytes.size) + bytes
            }
        }
        
        // Create minimal CBOR attestation object
        val authData = createAuthenticatorData(rpId, credentialId, xBytes, yBytes)
        val cborMap = createCBORAttestationMap(authData)
        
        return Base64.getUrlEncoder().withoutPadding().encodeToString(cborMap)
    }
    
    /**
     * Create WebAuthn authenticator data with proper structure
     */
    private fun createAuthenticatorData(rpId: String, credentialId: String, xCoord: ByteArray, yCoord: ByteArray): ByteArray {
        val rpIdHash = WebAuthnCryptography.createRpIdHash(rpId)
        val flags = byteArrayOf(0x45) // UP=1, UV=1, AT=1
        val counter = byteArrayOf(0x00, 0x00, 0x00, 0x01)
        val aaguid = ByteArray(16) // All zeros for our implementation
        val credIdBytes = credentialId.toByteArray()
        val credIdLen = byteArrayOf((credIdBytes.size shr 8).toByte(), credIdBytes.size.toByte())
        
        // Create COSE key (simplified EC2 format)
        val coseKey = createCOSEKey(xCoord, yCoord)
        
        return rpIdHash + flags + counter + aaguid + credIdLen + credIdBytes + coseKey
    }
    
    /**
     * Create COSE key structure for EC2 keys
     */
    private fun createCOSEKey(xCoord: ByteArray, yCoord: ByteArray): ByteArray {
        // COSE EC2 key structure that matches server expectation
        // Map with keys: 1=kty, 3=alg, -1=crv, -2=x, -3=y
        return byteArrayOf(
            0xA5.toByte(), // Map with 5 elements
            0x01, 0x02,    // 1: 2 (EC2 key type)
            0x03, 0x26,    // 3: -7 (ES256 algorithm)
            0x20, 0x01,    // -1: 1 (P-256 curve)
            0x21, 0x58, 0x20 // -2: byte string(32) for x coordinate
        ) + xCoord + byteArrayOf(
            0x22, 0x58, 0x20 // -3: byte string(32) for y coordinate  
        ) + yCoord
    }
    
    /**
     * Create CBOR attestation map structure
     */
    private fun createCBORAttestationMap(authData: ByteArray): ByteArray {
        // Simple CBOR map: {"fmt": "none", "attStmt": {}, "authData": authData}
        val authDataBytes = authData
        
        // Simplified CBOR encoding - this creates the basic structure the server can parse
        return byteArrayOf(
            0xA3.toByte(), // Map with 3 elements
            0x63, // Text string length 3
        ) + "fmt".toByteArray() + byteArrayOf(
            0x64, // Text string length 4  
        ) + "none".toByteArray() + byteArrayOf(
            0x67, // Text string length 7
        ) + "attStmt".toByteArray() + byteArrayOf(
            0xA0.toByte(), // Empty map
            0x68, // Text string length 8
        ) + "authData".toByteArray() + byteArrayOf(
            0x59, (authDataBytes.size shr 8).toByte(), authDataBytes.size.toByte() // Byte string with length
        ) + authDataBytes
    }
    
    /**
     * Parse WebAuthn registration result from JavaScript
     */
    fun parseWebAuthnRegistration(credentialData: String): PasskeyRegistration? {
        return try {
            val data = json.decodeFromString<Map<String, Any?>>(credentialData)
            PasskeyRegistration(
                credentialId = data["credentialId"] as? String ?: return null,
                publicKey = data["publicKey"] as? String ?: return null,
                attestationObject = data["attestationObject"] as? String ?: return null,
                clientDataJSON = data["clientDataJSON"] as? String ?: return null,
                transports = (data["transports"] as? List<*>)?.mapNotNull { it as? String } ?: listOf("internal")
            )
        } catch (e: Exception) {
            println("WebAuthnProtocolHandler: Failed to parse WebAuthn registration: ${e.message}")
            null
        }
    }
    
    /**
     * Parse WebAuthn assertion result from JavaScript
     */
    fun parseWebAuthnAssertion(credentialData: String): PasskeyAssertion? {
        return try {
            val data = json.decodeFromString<Map<String, String?>>(credentialData)
            PasskeyAssertion(
                credentialId = data["credentialId"] ?: return null,
                authenticatorData = data["authenticatorData"] ?: return null,
                signature = data["signature"] ?: return null,
                clientDataJSON = data["clientDataJSON"] ?: return null,
                userHandle = data["userHandle"]
            )
        } catch (e: Exception) {
            println("WebAuthnProtocolHandler: Failed to parse WebAuthn assertion: ${e.message}")
            null
        }
    }
}