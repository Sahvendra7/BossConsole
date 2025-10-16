package ai.rever.boss.services.passkey.desktop

import ai.rever.boss.services.passkey.MacOSBiometricAuth
import ai.rever.boss.services.passkey.WindowsBiometricAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Provides platform-specific biometric authentication capabilities
 * Handles Touch ID on macOS, Windows Hello on Windows, and fallback for Linux
 */
class BiometricAuthProvider {
    
    private val currentPlatform = System.getProperty("os.name").lowercase()
    
    /**
     * Check if biometric authentication is supported and available on the current platform
     */
    suspend fun isBiometricSupported(): Boolean = withContext(Dispatchers.IO) {
        try {
            when {
                currentPlatform.contains("mac") -> {
                    val available = MacOSBiometricAuth.isBiometricAvailable()
                    println("BiometricAuthProvider: macOS Touch ID availability: $available")
                    available
                }
                currentPlatform.contains("windows") -> {
                    val available = WindowsBiometricAuth.isBiometricAvailable()
                    println("BiometricAuthProvider: Windows Hello availability: $available")
                    available
                }
                currentPlatform.contains("linux") -> {
                    println("BiometricAuthProvider: Linux biometric support not available")
                    false
                }
                else -> {
                    println("BiometricAuthProvider: Unknown platform: $currentPlatform")
                    false
                }
            }
        } catch (e: Exception) {
            println("BiometricAuthProvider: Error checking biometric support: ${e.message}")
            false
        }
    }

    /**
     * Check if the current platform is macOS
     */
    fun isMacOS(): Boolean = currentPlatform.contains("mac")
    
    /**
     * Check if the current platform is Windows
     */
    fun isWindows(): Boolean = currentPlatform.contains("windows")

    /**
     * Get the current platform identifier
     */
    fun getCurrentPlatform(): String {
        return when {
            currentPlatform.contains("mac") -> "mac"
            currentPlatform.contains("windows") -> "windows"
            currentPlatform.contains("linux") -> "linux"
            else -> "unknown"
        }
    }
}
