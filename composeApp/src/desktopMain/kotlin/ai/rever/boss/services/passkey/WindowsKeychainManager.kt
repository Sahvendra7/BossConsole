package ai.rever.boss.services.passkey

import java.io.File
import java.security.KeyPair
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.util.*

/**
 * Windows-specific keychain manager for storing cryptographic keys
 * Uses file system storage with Windows-specific security features
 */
object WindowsKeychainManager {

    private val isWindows: Boolean by lazy {
        try {
            System.getProperty("os.name").lowercase().contains("windows")
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Store a key pair securely for Windows
     * Uses AppData folder with appropriate permissions
     */
    suspend fun storeKeyPairInKeychain(credentialId: String, keyPair: KeyPair): Result<Unit> {
        if (!isWindows) {
            return Result.failure(Exception("Windows keychain not available on this platform"))
        }

        return try {
            println("WindowsKeychainManager: Storing key pair for credential: $credentialId")
            
            val appDataDir = System.getenv("APPDATA") ?: System.getenv("USERPROFILE")
            val bossDir = File(appDataDir, ".boss-passkeys")
            
            if (!bossDir.exists()) {
                bossDir.mkdirs()
                // Set directory permissions (Windows-specific)
                setWindowsDirectoryPermissions(bossDir)
            }

            val keyFile = File(bossDir, "$credentialId.key")
            
            // Encode keys as base64url
            val privateKey = keyPair.private as ECPrivateKey
            val publicKey = keyPair.public as ECPublicKey
            
            val privateKeyB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(privateKey.encoded)
            val publicKeyB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(publicKey.encoded)
            
            val keyData = "$privateKeyB64|$publicKeyB64"
            keyFile.writeText(keyData)
            
            // Set file permissions (Windows-specific)
            setWindowsFilePermissions(keyFile)

            println("WindowsKeychainManager: Successfully stored key pair for credential: $credentialId")
            
            // Also store in Windows Credential Manager for metadata
            WindowsCredentialManager.storePasskey(credentialId, "Windows Hello Credential")
            
            Result.success(Unit)
            
        } catch (e: Exception) {
            println("WindowsKeychainManager: Failed to store key pair: ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * Store a passkey in Windows keychain (credential manager + file storage)
     */
    suspend fun storePasskeyInKeychain(credentialId: String, displayName: String): Result<Unit> {
        if (!isWindows) {
            return Result.failure(Exception("Windows keychain not available on this platform"))
        }

        return try {
            println("WindowsKeychainManager: Storing passkey metadata for credential: $credentialId")
            
            val result = WindowsCredentialManager.storePasskey(credentialId, displayName)
            if (result.isSuccess) {
                println("WindowsKeychainManager: Passkey stored successfully for credential: $credentialId")
                Result.success(Unit)
            } else {
                println("WindowsKeychainManager: Failed to store passkey: ${result.exceptionOrNull()?.message}")
                Result.failure(result.exceptionOrNull() ?: Exception("Failed to store passkey"))
            }
            
        } catch (e: Exception) {
            println("WindowsKeychainManager: Error storing passkey: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Set Windows-specific directory permissions
     */
    private fun setWindowsDirectoryPermissions(directory: File) {
        try {
            // Use icacls to set proper permissions on Windows
            val command = listOf(
                "icacls",
                directory.absolutePath,
                "/inheritance:d",
                "/grant:r",
                "${System.getProperty("user.name")}:(OI)(CI)F"
            )
            
            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()
            
            process.waitFor()
            println("WindowsKeychainManager: Set directory permissions for ${directory.absolutePath}")
            
        } catch (e: Exception) {
            println("WindowsKeychainManager: Warning - could not set directory permissions: ${e.message}")
        }
    }

    /**
     * Set Windows-specific file permissions
     */
    private fun setWindowsFilePermissions(file: File) {
        try {
            // Use icacls to set proper file permissions on Windows
            val command = listOf(
                "icacls",
                file.absolutePath,
                "/inheritance:d",
                "/grant:r",
                "${System.getProperty("user.name")}:F"
            )
            
            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()
            
            process.waitFor()
            println("WindowsKeychainManager: Set file permissions for ${file.absolutePath}")
            
        } catch (e: Exception) {
            println("WindowsKeychainManager: Warning - could not set file permissions: ${e.message}")
        }
    }
}