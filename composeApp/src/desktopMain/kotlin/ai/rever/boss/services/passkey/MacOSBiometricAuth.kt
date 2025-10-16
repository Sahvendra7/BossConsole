package ai.rever.boss.services.passkey

/**
 * Unified interface for macOS biometric authentication and keychain operations
 * Delegates to specialized components for Touch ID auth and keychain management
 */
object MacOSBiometricAuth {

    /**
     * Check if biometric authentication is available on this device
     */
    fun isBiometricAvailable(): Boolean = MacOSTouchIDAuth.isBiometricAvailable()

    /**
     * Delete a passkey from macOS Keychain
     */
    suspend fun deletePasskey(credentialId: String): Result<Boolean> =
        MacOSKeychainManager.deletePasskey(credentialId)

    /**
     * List all BOSS passkeys stored in keychain
     */
    suspend fun listPasskeys(): Result<List<String>> =
        MacOSKeychainManager.listPasskeys()
}
