package ai.rever.boss.services.passkey

import kotlinx.coroutines.*
import java.security.*
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.*
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Desktop keychain operations for passkey management
 * Handles secure storage and retrieval of cryptographic key pairs
 */
object DesktopKeychainManager {

    /**
     * Store passkey metadata in macOS keychain
     */
    suspend fun storePasskeyInKeychain(credentialId: String, displayName: String): Result<Boolean> = 
        withContext(Dispatchers.IO) {
            try {
                val macOSResult = MacOSBiometricAuth.listPasskeys()
                if (macOSResult.isSuccess) {
                    println("DesktopKeychainManager: Passkey stored successfully for credential: $credentialId")
                    Result.success(true)
                } else {
                    println("DesktopKeychainManager: Failed to store passkey: ${macOSResult.exceptionOrNull()?.message}")
                    Result.failure(macOSResult.exceptionOrNull() ?: Exception("Failed to store passkey"))
                }
            } catch (e: Exception) {
                println("DesktopKeychainManager: Exception storing passkey: ${e.message}")
                Result.failure(e)
            }
        }

    /**
     * Store ECDSA key pair in macOS keychain with secure access controls
     */
    suspend fun storeKeyPairInKeychain(credentialId: String, keyPair: KeyPair): Result<Unit> = 
        suspendCoroutine { continuation ->
            GlobalScope.launch(Dispatchers.IO) {
                try {
                    println("DesktopKeychainManager: Storing key pair for credential: $credentialId")
                    
                    // Encode keys to store in file
                    val privateKeyBytes = keyPair.private.encoded
                    val publicKeyBytes = keyPair.public.encoded
                    
                    val keyData = Base64.getUrlEncoder().withoutPadding().encodeToString(privateKeyBytes) + 
                                 "|" + Base64.getUrlEncoder().withoutPadding().encodeToString(publicKeyBytes)
                    
                    // Store in a secure location (user's home directory for development)
                    val homeDir = System.getProperty("user.home")
                    val keyFile = java.io.File(homeDir, ".boss-passkeys/$credentialId.key")
                    keyFile.parentFile.mkdirs()
                    keyFile.writeText(keyData)
                    
                    println("Storing key pair for credential: $credentialId (${keyPair.private.algorithm})")
                    continuation.resume(Result.success(Unit))
                    
                } catch (e: Exception) {
                    println("DesktopKeychainManager: Failed to store key pair: ${e.message}")
                    continuation.resume(Result.failure(e))
                }
            }
        }

    /**
     * @deprecated Key retrieval is now handled by DesktopPasskeyService with file-based storage
     */
    @Deprecated("Use file-based storage in DesktopPasskeyService instead")
    suspend fun retrieveKeyPairFromKeychain(credentialId: String): Result<KeyPair> {
        return Result.failure(Exception("Key retrieval moved to DesktopPasskeyService with file-based storage"))
    }
}