package ai.rever.boss.services.passkey.desktop

import ai.rever.boss.services.passkey.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Manages credential storage, retrieval, and lifecycle for desktop platforms
 * Handles both platform-specific credential stores and file-based key storage
 */
class PasskeyCredentialManager(
    private val biometricAuthProvider: BiometricAuthProvider
) {

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
                    rpId = PasskeyConfigHelper.getRpId(),
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

}
