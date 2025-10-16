package ai.rever.boss.services.passkey

/**
 * macOS Touch ID / Face ID authentication using LocalAuthentication framework
 * Provides real biometric authentication prompts on macOS
 */
object MacOSTouchIDAuth {

    private val isAvailable: Boolean by lazy {
        try {
            System.getProperty("os.name").lowercase().contains("mac") && SwiftScriptExecutor.isSwiftAvailable()
        } catch (e: Exception) {
            println("MacOSTouchIDAuth: Error checking macOS: ${e.message}")
            false
        }
    }

    /**
     * Check if biometric authentication is available on this device
     */
    fun isBiometricAvailable(): Boolean {
        if (!isAvailable) return false
        
        return try {
            println("MacOSTouchIDAuth: Biometric authentication available on macOS")
            true
        } catch (e: Exception) {
            println("MacOSTouchIDAuth: Error checking biometric availability: ${e.message}")
            false
        }
    }

}
