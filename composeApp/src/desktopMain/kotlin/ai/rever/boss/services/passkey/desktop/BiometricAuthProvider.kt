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
     * Perform biometric authentication with platform-specific prompt
     */
    suspend fun authenticateWithBiometric(prompt: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            when {
                currentPlatform.contains("mac") -> {
                    val result = MacOSBiometricAuth.authenticateWithBiometric(prompt)
                    if (result.isSuccess && result.getOrNull() == true) {
                        println("BiometricAuthProvider: macOS Touch ID authentication successful")
                    } else {
                        println("BiometricAuthProvider: macOS Touch ID authentication failed: ${result.exceptionOrNull()?.message}")
                    }
                    result
                }
                currentPlatform.contains("windows") -> {
                    val result = WindowsBiometricAuth.authenticateWithBiometric(prompt)
                    if (result.isSuccess && result.getOrNull() == true) {
                        println("BiometricAuthProvider: Windows Hello authentication successful")
                    } else {
                        println("BiometricAuthProvider: Windows Hello authentication failed: ${result.exceptionOrNull()?.message}")
                    }
                    result
                }
                else -> {
                    val error = Exception("Biometric authentication not supported on platform: $currentPlatform")
                    println("BiometricAuthProvider: ${error.message}")
                    Result.failure(error)
                }
            }
        } catch (e: Exception) {
            println("BiometricAuthProvider: Biometric authentication error: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * Get the platform-specific biometric authentication method name
     */
    fun getPlatformBiometricName(): String {
        return when {
            currentPlatform.contains("mac") -> "Touch ID"
            currentPlatform.contains("windows") -> "Windows Hello"
            currentPlatform.contains("linux") -> "Biometric"
            else -> "Biometric Authentication"
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
     * Check if the current platform is Linux
     */
    fun isLinux(): Boolean = currentPlatform.contains("linux")
    
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