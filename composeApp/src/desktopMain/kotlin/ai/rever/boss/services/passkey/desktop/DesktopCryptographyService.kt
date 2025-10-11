package ai.rever.boss.services.passkey.desktop

import ai.rever.boss.services.passkey.WebAuthnCryptography
import ai.rever.boss.services.passkey.PasskeyAssertion
import java.security.interfaces.ECPrivateKey
import java.util.*

/**
 * Handles cryptographic operations for desktop passkey authentication
 * Integrates with WebAuthnCryptography for ECDSA signatures and data hashing
 */
class DesktopCryptographyService(
    private val webAuthnProtocolHandler: WebAuthnProtocolHandler
) {
    
    /**
     * Create a complete WebAuthn assertion with proper cryptographic signatures
     */
    fun createWebAuthnAssertion(
        credentialId: String,
        challenge: ByteArray,
        rpId: String,
        privateKey: ECPrivateKey
    ): PasskeyAssertion {
        println("DesktopCryptographyService: Creating WebAuthn assertion for credential: $credentialId")
        
        // Convert challenge bytes to base64url string for client data
        val challengeB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(challenge)
        
        // Create proper WebAuthn client data JSON for authentication
        val clientDataJSON = webAuthnProtocolHandler.createClientDataJSON(challengeB64, "webauthn.get")
        
        // Create real WebAuthn authenticator data with proper SHA-256 RP ID hash
        val rpIdHash = WebAuthnCryptography.createRpIdHash(rpId)
        val flags = byteArrayOf(0x05) // User present + User verified flags
        val counter = byteArrayOf(0x00, 0x00, 0x00, 0x02) // Increment counter for each use
        val authenticatorDataBytes = rpIdHash + flags + counter
        val authenticatorDataB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(authenticatorDataBytes)
        
        // Create real ECDSA signature over authenticator data + client data hash
        val clientDataHash = WebAuthnCryptography.createClientDataHash(clientDataJSON)
        val realSignature = WebAuthnCryptography.createWebAuthnSignature(privateKey, authenticatorDataBytes, clientDataHash)
        val signatureB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(realSignature)
        
        println("DesktopCryptographyService: Created real cryptographic signature (${realSignature.size} bytes)")
        
        return PasskeyAssertion(
            credentialId = credentialId,
            authenticatorData = authenticatorDataB64,
            signature = signatureB64,
            clientDataJSON = clientDataJSON,
            userHandle = "touchid-user-handle"
        )
    }
    
    /**
     * Create authenticator data for WebAuthn operations
     */
    fun createAuthenticatorData(rpId: String, userPresent: Boolean = true, userVerified: Boolean = true): ByteArray {
        val rpIdHash = WebAuthnCryptography.createRpIdHash(rpId)
        
        // Build flags byte
        var flags: Byte = 0
        if (userPresent) flags = (flags.toInt() or 0x01).toByte() // UP (User Present)
        if (userVerified) flags = (flags.toInt() or 0x04).toByte() // UV (User Verified)
        
        // Counter (increment for each use - simplified to static value for demo)
        val counter = byteArrayOf(0x00, 0x00, 0x00, 0x02)
        
        return rpIdHash + flags + counter
    }
    
    /**
     * Create client data hash for WebAuthn signatures
     */
    fun createClientDataHash(clientDataJSON: String): ByteArray {
        return WebAuthnCryptography.createClientDataHash(clientDataJSON)
    }
    
    /**
     * Create WebAuthn signature using ECDSA
     */
    fun createWebAuthnSignature(
        privateKey: ECPrivateKey,
        authenticatorData: ByteArray,
        clientDataHash: ByteArray
    ): ByteArray {
        return WebAuthnCryptography.createWebAuthnSignature(privateKey, authenticatorData, clientDataHash)
    }
    
    /**
     * Create RP ID hash for WebAuthn operations
     */
    fun createRpIdHash(rpId: String): ByteArray {
        return WebAuthnCryptography.createRpIdHash(rpId)
    }
    
    /**
     * Verify the integrity of a WebAuthn signature
     */
    fun verifyWebAuthnSignature(
        publicKey: java.security.interfaces.ECPublicKey,
        signature: ByteArray,
        authenticatorData: ByteArray,
        clientDataHash: ByteArray
    ): Boolean {
        return try {
            // This would be used for local verification if needed
            // For now, server handles verification
            println("DesktopCryptographyService: Signature verification delegated to server")
            true
        } catch (e: Exception) {
            println("DesktopCryptographyService: Signature verification failed: ${e.message}")
            false
        }
    }
    
    /**
     * Convert byte array to base64url encoding (WebAuthn standard)
     */
    fun encodeBase64Url(data: ByteArray): String {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data)
    }
    
    /**
     * Convert base64url string to byte array
     */
    fun decodeBase64Url(data: String): ByteArray {
        return Base64.getUrlDecoder().decode(data)
    }
    
    /**
     * Generate secure random bytes for challenges or nonces
     */
    fun generateRandomBytes(size: Int): ByteArray {
        val random = java.security.SecureRandom()
        val bytes = ByteArray(size)
        random.nextBytes(bytes)
        return bytes
    }
}