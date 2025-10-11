package ai.rever.boss.services.passkey

import java.nio.charset.StandardCharsets
import java.security.*
import java.security.interfaces.ECPrivateKey
import java.security.spec.ECGenParameterSpec
import java.util.*

/**
 * WebAuthn cryptographic operations
 * Handles ECDSA key generation, signatures, and format conversions
 */
object WebAuthnCryptography {

    /**
     * Generate a new ECDSA key pair for WebAuthn using P-256 curve
     */
    fun generateECKeyPair(): KeyPair {
        val keyPairGenerator = KeyPairGenerator.getInstance("EC")
        val spec = ECGenParameterSpec("secp256r1") // P-256 curve, standard for WebAuthn
        keyPairGenerator.initialize(spec, SecureRandom())
        return keyPairGenerator.generateKeyPair()
    }
    
    /**
     * Create a proper SHA-256 hash of the RP ID
     */
    fun createRpIdHash(rpId: String): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(rpId.toByteArray(StandardCharsets.UTF_8))
    }
    
    /**
     * Create a real ECDSA signature over the authenticator data and client data hash
     */
    fun createWebAuthnSignature(
        privateKey: ECPrivateKey,
        authenticatorData: ByteArray,
        clientDataHash: ByteArray
    ): ByteArray {
        val signature = Signature.getInstance("SHA256withECDSA")
        signature.initSign(privateKey)
        
        // Sign authenticatorData || clientDataHash (WebAuthn spec)
        signature.update(authenticatorData)
        signature.update(clientDataHash)
        
        val derSignature = signature.sign()
        
        // Convert DER-encoded signature to raw format for WebAuthn compatibility
        // Java produces DER format, but Web Crypto API expects raw r||s format
        return convertDERSignatureToRaw(derSignature)
    }
    
    /**
     * Convert DER-encoded ECDSA signature to raw format (r||s concatenation)
     * required for WebAuthn compatibility with Web Crypto API
     */
    fun convertDERSignatureToRaw(derSignature: ByteArray): ByteArray {
        // DER format: 0x30 [total-length] 0x02 [R-length] [R] 0x02 [S-length] [S]
        // Raw format: [R-32-bytes] [S-32-bytes] for P-256
        
        if (derSignature.size < 6 || derSignature[0] != 0x30.toByte()) {
            throw IllegalArgumentException("Invalid DER signature format")
        }
        
        var offset = 2 // Skip 0x30 and total length
        
        // Read R component
        if (derSignature[offset] != 0x02.toByte()) {
            throw IllegalArgumentException("Expected INTEGER tag for R component")
        }
        offset++ // Skip INTEGER tag
        
        val rLength = derSignature[offset].toInt() and 0xFF
        offset++ // Skip R length
        
        val rBytes = derSignature.copyOfRange(offset, offset + rLength)
        offset += rLength
        
        // Read S component
        if (derSignature[offset] != 0x02.toByte()) {
            throw IllegalArgumentException("Expected INTEGER tag for S component")
        }
        offset++ // Skip INTEGER tag
        
        val sLength = derSignature[offset].toInt() and 0xFF
        offset++ // Skip S length
        
        val sBytes = derSignature.copyOfRange(offset, offset + sLength)
        
        // Pad or trim both R and S to exactly 32 bytes for P-256
        val rPadded = padOrTrimTo32Bytes(rBytes)
        val sPadded = padOrTrimTo32Bytes(sBytes)
        
        // Concatenate r || s
        return rPadded + sPadded
    }
    
    /**
     * Pad with leading zeros or trim leading zeros to exactly 32 bytes
     * Required for P-256 curve signature components
     */
    private fun padOrTrimTo32Bytes(bytes: ByteArray): ByteArray {
        return when {
            bytes.size == 32 -> bytes
            bytes.size < 32 -> {
                // Pad with leading zeros
                ByteArray(32 - bytes.size) + bytes
            }
            bytes.size > 32 -> {
                // Trim leading zeros (but keep at least 32 bytes)
                var start = 0
                while (start < bytes.size - 32 && bytes[start] == 0.toByte()) {
                    start++
                }
                bytes.copyOfRange(start, start + 32)
            }
            else -> bytes
        }
    }
    
    /**
     * Create SHA-256 hash of WebAuthn client data JSON
     */
    fun createClientDataHash(clientDataJSONBase64Url: String): ByteArray {
        // Decode base64url to get the actual JSON bytes
        val clientDataBytes = Base64.getUrlDecoder().decode(clientDataJSONBase64Url)
        
        // Hash the JSON bytes
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(clientDataBytes)
    }
}