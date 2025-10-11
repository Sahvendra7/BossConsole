package ai.rever.boss.services.passkey

/**
 * Unified interface for Windows biometric authentication and credential operations
 * Delegates to specialized components for Windows Hello auth and credential management
 */
object WindowsBiometricAuth {

    /**
     * Check if biometric authentication is available on this device
     */
    fun isBiometricAvailable(): Boolean = WindowsHelloAuth.isBiometricAvailable()

    /**
     * Authenticate user with Windows Hello (PIN, Fingerprint, Face ID, etc.)
     */
    suspend fun authenticateWithBiometric(reason: String = "Authenticate with Windows Hello"): Result<Boolean> =
        WindowsHelloAuth.authenticateWithBiometric(reason)

    /**
     * Delete a passkey from Windows Credential Manager
     */
    suspend fun deletePasskey(credentialId: String): Result<Boolean> =
        WindowsCredentialManager.deletePasskey(credentialId)

    /**
     * List all BOSS passkeys stored in Windows Credential Manager
     */
    suspend fun listPasskeys(): Result<List<String>> =
        WindowsCredentialManager.listPasskeys()
}