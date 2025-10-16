package ai.rever.boss.services.passkey

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Windows Credential Manager integration for storing and retrieving passkey credentials
 * Uses Windows Credential Manager (cmdkey) for secure credential storage
 */
object WindowsCredentialManager {

    private val isWindows: Boolean by lazy {
        try {
            System.getProperty("os.name").lowercase().contains("windows")
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Delete a passkey from Windows Credential Manager
     */
    suspend fun deletePasskey(credentialId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        if (!isWindows) {
            return@withContext Result.failure(Exception("Windows Credential Manager not available on this platform"))
        }

        try {
            println("WindowsCredentialManager: Deleting passkey from Credential Manager: $credentialId")
            
            val targetName = "BOSS_Passkey_$credentialId"
            val command = listOf(
                "cmdkey",
                "/delete:$targetName"
            )

            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()

            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()

            if (exitCode == 0) {
                println("WindowsCredentialManager: Successfully deleted passkey: $credentialId")
                Result.success(true)
            } else {
                println("WindowsCredentialManager: Failed to delete passkey: $output")
                Result.failure(Exception("Failed to delete passkey from Credential Manager: $output"))
            }

        } catch (e: Exception) {
            println("WindowsCredentialManager: Error deleting passkey: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * List all BOSS passkeys stored in Windows Credential Manager
     */
    suspend fun listPasskeys(): Result<List<String>> = withContext(Dispatchers.IO) {
        if (!isWindows) {
            return@withContext Result.failure(Exception("Windows Credential Manager not available on this platform"))
        }

        try {
            println("WindowsCredentialManager: Listing passkeys from Credential Manager...")
            
            val command = listOf("cmdkey", "/list")

            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()

            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()

            if (exitCode == 0) {
                // Parse the output to find BOSS passkeys
                val passkeys = output.lines()
                    .filter { it.contains("BOSS_Passkey_") }
                    .mapNotNull { line ->
                        val match = Regex("Target: BOSS_Passkey_(.+)").find(line)
                        match?.groupValues?.get(1)
                    }

                println("WindowsCredentialManager: Found ${passkeys.size} passkeys")
                Result.success(passkeys)
            } else {
                println("WindowsCredentialManager: Failed to list passkeys: $output")
                Result.failure(Exception("Failed to list passkeys from Credential Manager: $output"))
            }

        } catch (e: Exception) {
            println("WindowsCredentialManager: Error listing passkeys: ${e.message}")
            Result.failure(e)
        }
    }

}
