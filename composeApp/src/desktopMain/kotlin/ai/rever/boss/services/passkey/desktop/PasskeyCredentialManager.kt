package ai.rever.boss.services.passkey.desktop

import ai.rever.boss.services.passkey.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.KeyPair
import java.security.KeyFactory
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.*

/**
 * Manages credential storage, retrieval, and lifecycle for desktop platforms
 * Handles both platform-specific credential stores and file-based key storage
 */
class PasskeyCredentialManager(
    private val biometricAuthProvider: BiometricAuthProvider,
    private val keychainService: PlatformKeychainService
) {
    
    /**
     * Determine credential type from credential ID to route authentication correctly
     */
    fun getCredentialType(credentialId: String): CredentialType {
        return when {
            credentialId.startsWith("touchid-credential-") -> CredentialType.MAC_TOUCHID
            credentialId.startsWith("windowshello-credential-") -> CredentialType.WINDOWS_HELLO
            credentialId.startsWith("webauthn-") -> CredentialType.BROWSER_WEBAUTHN
            else -> CredentialType.BROWSER_WEBAUTHN // Default to browser for unknown types
        }
    }
    
    /**
     * Check if there are any stored passkeys for the current user
     */
    suspend fun hasPasskeys(): Boolean {
        return try {
            val passkeys = getAvailablePasskeys().getOrNull()
            !passkeys.isNullOrEmpty()
        } catch (e: Exception) {
            println("PasskeyCredentialManager: Error checking for passkeys: ${e.message}")
            false
        }
    }
    
    /**
     * Get list of available passkeys based on current platform
     */
    suspend fun getAvailablePasskeys(): Result<List<PasskeyInfo>> = withContext(Dispatchers.IO) {
        try {
            val credentialIds = when {
                biometricAuthProvider.isMacOS() -> {
                    MacOSBiometricAuth.listPasskeys().getOrNull() ?: emptyList()
                }
                biometricAuthProvider.isWindows() -> {
                    WindowsBiometricAuth.listPasskeys().getOrNull() ?: emptyList()
                }
                else -> {
                    emptyList()
                }
            }
            
            val passkeyInfos = credentialIds.map { credentialId ->
                PasskeyInfo(
                    credentialId = credentialId,
                    displayName = when {
                        biometricAuthProvider.isMacOS() -> "Touch ID (macOS)"
                        biometricAuthProvider.isWindows() -> "Windows Hello"
                        else -> "Desktop Credential"
                    },
                    createdAt = System.currentTimeMillis(),
                    lastUsed = null,
                    rpId = "api.risaboss.com",
                    transports = listOf("internal")
                )
            }
            
            println("PasskeyCredentialManager: Found ${passkeyInfos.size} passkeys on ${biometricAuthProvider.getCurrentPlatform()}")
            Result.success(passkeyInfos)
        } catch (e: Exception) {
            println("PasskeyCredentialManager: Error getting available passkeys: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * Retrieve a key pair from file storage based on credential ID
     */
    suspend fun retrieveKeyPair(credentialId: String): Result<KeyPair> = withContext(Dispatchers.IO) {
        try {
            // Determine the key storage directory based on platform
            val keyStorageDir = when {
                biometricAuthProvider.isMacOS() -> {
                    val homeDir = System.getProperty("user.home")
                    "$homeDir/.boss-passkeys"
                }
                biometricAuthProvider.isWindows() -> {
                    val appDataDir = System.getenv("APPDATA") ?: System.getenv("USERPROFILE")
                    "$appDataDir/.boss-passkeys"
                }
                else -> {
                    val homeDir = System.getProperty("user.home")
                    "$homeDir/.boss-passkeys"
                }
            }
            
            val keyFile = java.io.File(keyStorageDir, "$credentialId.key")
            
            if (!keyFile.exists()) {
                // Check if credential is in platform-specific storage for better error message
                val storedCredentials = when {
                    biometricAuthProvider.isMacOS() -> MacOSBiometricAuth.listPasskeys().getOrNull() ?: emptyList()
                    biometricAuthProvider.isWindows() -> WindowsBiometricAuth.listPasskeys().getOrNull() ?: emptyList()
                    else -> emptyList()
                }
                
                return@withContext if (storedCredentials.contains(credentialId)) {
                    Result.failure(Exception("Credential found in storage but key file missing: $credentialId"))
                } else {
                    Result.failure(Exception("Credential not found: $credentialId"))
                }
            }
            
            val keyData = keyFile.readText()
            val parts = keyData.split("|")
            if (parts.size != 2) {
                return@withContext Result.failure(Exception("Invalid key data format for credential: $credentialId"))
            }
            
            // Decode the private and public keys
            val privateKeyBytes = Base64.getUrlDecoder().decode(parts[0])
            val publicKeyBytes = Base64.getUrlDecoder().decode(parts[1])
            
            // Recreate the KeyPair from the stored bytes
            val keyFactory = KeyFactory.getInstance("EC")
            
            val privateKeySpec = PKCS8EncodedKeySpec(privateKeyBytes)
            val privateKey = keyFactory.generatePrivate(privateKeySpec)
            
            val publicKeySpec = X509EncodedKeySpec(publicKeyBytes)
            val publicKey = keyFactory.generatePublic(publicKeySpec)
            
            val keyPair = KeyPair(publicKey, privateKey)
            println("PasskeyCredentialManager: Successfully retrieved key pair for credential: $credentialId")
            Result.success(keyPair)
            
        } catch (e: Exception) {
            println("PasskeyCredentialManager: Failed to retrieve key pair: ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        }
    }
    
    /**
     * Delete a passkey credential from both platform storage and key files
     */
    suspend fun deletePasskey(credentialId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            var deleteResult: Result<Boolean> = Result.success(false)
            
            when {
                biometricAuthProvider.isMacOS() -> {
                    deleteResult = MacOSBiometricAuth.deletePasskey(credentialId)
                }
                biometricAuthProvider.isWindows() -> {
                    deleteResult = WindowsBiometricAuth.deletePasskey(credentialId)
                }
                else -> {
                    println("PasskeyCredentialManager: Passkey deletion not implemented for this platform")
                }
            }
            
            if (deleteResult.isSuccess && deleteResult.getOrNull() == true) {
                // Clean up the key file
                try {
                    val keyStorageDir = when {
                        biometricAuthProvider.isMacOS() -> {
                            val homeDir = System.getProperty("user.home")
                            "$homeDir/.boss-passkeys"
                        }
                        biometricAuthProvider.isWindows() -> {
                            val appDataDir = System.getenv("APPDATA") ?: System.getenv("USERPROFILE")
                            "$appDataDir/.boss-passkeys"
                        }
                        else -> {
                            val homeDir = System.getProperty("user.home")
                            "$homeDir/.boss-passkeys"
                        }
                    }
                    
                    val keyFile = java.io.File(keyStorageDir, "$credentialId.key")
                    if (keyFile.exists()) {
                        keyFile.delete()
                        println("PasskeyCredentialManager: Deleted key file for credential: $credentialId")
                    }
                } catch (e: Exception) {
                    println("PasskeyCredentialManager: Warning - failed to delete key file: ${e.message}")
                }
                
                println("PasskeyCredentialManager: Successfully deleted passkey: $credentialId")
                Result.success(Unit)
            } else {
                val error = deleteResult.exceptionOrNull()
                println("PasskeyCredentialManager: Failed to delete passkey: ${error?.message}")
                Result.failure(error ?: Exception("Failed to delete passkey"))
            }
        } catch (e: Exception) {
            println("PasskeyCredentialManager: Error deleting passkey: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * Credential types for routing authentication
     */
    enum class CredentialType {
        MAC_TOUCHID,        // Use Mac TouchID biometric auth
        WINDOWS_HELLO,      // Use Windows Hello biometric auth  
        BROWSER_WEBAUTHN    // Use browser WebAuthn (cross-device, iCloud synced, etc.)
    }
}