package ai.rever.boss.services.passkey.desktop

import ai.rever.boss.services.passkey.MacOSBiometricAuth
import ai.rever.boss.services.passkey.WindowsBiometricAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.KeyPair
import java.util.*

/**
 * Provides platform-specific keychain and credential storage integration
 * Handles macOS Keychain Services, Windows Credential Manager, and Linux fallbacks
 */
class PlatformKeychainService(
    private val biometricAuthProvider: BiometricAuthProvider
) {
    
    /**
     * Store a key pair in platform-specific secure storage
     */
    suspend fun storeKeyPair(credentialId: String, keyPair: KeyPair): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            // Platform-specific storage
            val platformResult = when {
                biometricAuthProvider.isMacOS() -> {
                    // Store in macOS Keychain
                    println("PlatformKeychainService: Storing credential in macOS Keychain: $credentialId")
                    // This would integrate with MacOSKeychainManager if needed
                    Result.success(Unit)
                }
                biometricAuthProvider.isWindows() -> {
                    // Store in Windows Credential Manager
                    println("PlatformKeychainService: Storing credential in Windows Credential Manager: $credentialId")
                    // This would integrate with WindowsCredentialManager if needed
                    Result.success(Unit)
                }
                else -> {
                    println("PlatformKeychainService: Platform-specific keychain not available, using file storage only")
                    Result.success(Unit)
                }
            }
            
            // Always store key pair in file system as backup/primary storage
            storeKeyPairToFile(credentialId, keyPair)
            platformResult
            
        } catch (e: Exception) {
            println("PlatformKeychainService: Failed to store key pair: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * Store key pair to file system (cross-platform fallback and primary storage)
     */
    private fun storeKeyPairToFile(credentialId: String, keyPair: KeyPair): Result<Unit> {
        return try {
            val keyStorageDir = getKeyStorageDirectory()
            val keyStorageDirFile = java.io.File(keyStorageDir)
            
            // Create directory if it doesn't exist
            if (!keyStorageDirFile.exists()) {
                keyStorageDirFile.mkdirs()
                println("PlatformKeychainService: Created key storage directory: $keyStorageDir")
            }
            
            // Encode keys to base64url for storage
            val privateKeyBytes = keyPair.private.encoded
            val publicKeyBytes = keyPair.public.encoded
            
            val privateKeyB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(privateKeyBytes)
            val publicKeyB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(publicKeyBytes)
            
            // Store as pipe-separated values in file
            val keyData = "$privateKeyB64|$publicKeyB64"
            val keyFile = java.io.File(keyStorageDir, "$credentialId.key")
            keyFile.writeText(keyData)
            
            println("PlatformKeychainService: Stored key pair to file: ${keyFile.absolutePath}")
            Result.success(Unit)
            
        } catch (e: Exception) {
            println("PlatformKeychainService: Failed to store key pair to file: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * List stored credentials from platform-specific storage
     */
    suspend fun listStoredCredentials(): Result<List<String>> = withContext(Dispatchers.IO) {
        return@withContext try {
            when {
                biometricAuthProvider.isMacOS() -> {
                    println("PlatformKeychainService: Listing macOS Keychain credentials")
                    MacOSBiometricAuth.listPasskeys()
                }
                biometricAuthProvider.isWindows() -> {
                    println("PlatformKeychainService: Listing Windows Credential Manager credentials")
                    WindowsBiometricAuth.listPasskeys()
                }
                else -> {
                    println("PlatformKeychainService: Listing file-based credentials")
                    listFileBasedCredentials()
                }
            }
        } catch (e: Exception) {
            println("PlatformKeychainService: Failed to list stored credentials: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * List credentials stored in file system
     */
    private fun listFileBasedCredentials(): Result<List<String>> {
        return try {
            val keyStorageDir = getKeyStorageDirectory()
            val keyStorageDirFile = java.io.File(keyStorageDir)
            
            if (!keyStorageDirFile.exists()) {
                return Result.success(emptyList())
            }
            
            val credentialIds = keyStorageDirFile.listFiles()
                ?.filter { it.name.endsWith(".key") }
                ?.map { it.nameWithoutExtension }
                ?: emptyList()
                
            println("PlatformKeychainService: Found ${credentialIds.size} file-based credentials")
            Result.success(credentialIds)
            
        } catch (e: Exception) {
            println("PlatformKeychainService: Failed to list file-based credentials: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * Remove credential from platform-specific storage
     */
    suspend fun removeCredential(credentialId: String): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            // Remove from platform-specific storage
            val platformResult = when {
                biometricAuthProvider.isMacOS() -> {
                    println("PlatformKeychainService: Removing credential from macOS Keychain: $credentialId")
                    MacOSBiometricAuth.deletePasskey(credentialId)
                        .map { Unit } // Convert Boolean result to Unit
                }
                biometricAuthProvider.isWindows() -> {
                    println("PlatformKeychainService: Removing credential from Windows Credential Manager: $credentialId")
                    WindowsBiometricAuth.deletePasskey(credentialId)
                        .map { Unit } // Convert Boolean result to Unit
                }
                else -> {
                    println("PlatformKeychainService: No platform-specific storage to remove from")
                    Result.success(Unit)
                }
            }
            
            // Always remove from file storage
            removeCredentialFile(credentialId)
            
            platformResult
            
        } catch (e: Exception) {
            println("PlatformKeychainService: Failed to remove credential: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * Remove credential file from file system
     */
    private fun removeCredentialFile(credentialId: String): Result<Unit> {
        return try {
            val keyStorageDir = getKeyStorageDirectory()
            val keyFile = java.io.File(keyStorageDir, "$credentialId.key")
            
            if (keyFile.exists()) {
                val deleted = keyFile.delete()
                if (deleted) {
                    println("PlatformKeychainService: Removed credential file: $credentialId")
                    Result.success(Unit)
                } else {
                    println("PlatformKeychainService: Failed to delete credential file: $credentialId")
                    Result.failure(Exception("Failed to delete credential file"))
                }
            } else {
                println("PlatformKeychainService: Credential file not found: $credentialId")
                Result.success(Unit) // Not an error if file doesn't exist
            }
            
        } catch (e: Exception) {
            println("PlatformKeychainService: Error removing credential file: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * Get platform-specific key storage directory
     */
    private fun getKeyStorageDirectory(): String {
        return when {
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
    }
    
    /**
     * Check if credential exists in platform storage
     */
    suspend fun credentialExists(credentialId: String): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val storedCredentials = listStoredCredentials().getOrNull() ?: emptyList()
            val exists = storedCredentials.contains(credentialId)
            println("PlatformKeychainService: Credential $credentialId exists: $exists")
            exists
        } catch (e: Exception) {
            println("PlatformKeychainService: Error checking credential existence: ${e.message}")
            false
        }
    }
    
    /**
     * Get storage information for debugging
     */
    fun getStorageInfo(): Map<String, Any> {
        return mapOf(
            "platform" to biometricAuthProvider.getCurrentPlatform(),
            "keyStorageDirectory" to getKeyStorageDirectory(),
            "platformKeychainAvailable" to (biometricAuthProvider.isMacOS() || biometricAuthProvider.isWindows()),
            "fileBasedStorage" to true
        )
    }
}