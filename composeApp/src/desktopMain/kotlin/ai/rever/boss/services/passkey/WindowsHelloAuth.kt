package ai.rever.boss.services.passkey

import kotlinx.coroutines.*
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.Toolkit
import javax.imageio.ImageIO

/**
 * Windows Hello biometric authentication using Windows.Security.Credentials.UI APIs
 * Provides real biometric authentication prompts on Windows 10/11
 */
object WindowsHelloAuth {

    private val isAvailable: Boolean by lazy {
        try {
            System.getProperty("os.name").lowercase().contains("windows") && PowerShellExecutor.isPowerShellAvailable()
        } catch (e: Exception) {
            println("WindowsHelloAuth: Error checking Windows: ${e.message}")
            false
        }
    }

    /**
     * Check if Windows Hello is available on this device
     */
    fun isBiometricAvailable(): Boolean {
        if (!isAvailable) return false
        
        return try {
            println("WindowsHelloAuth: Biometric authentication available on Windows")
            true
        } catch (e: Exception) {
            println("WindowsHelloAuth: Error checking biometric availability: ${e.message}")
            false
        }
    }

    /**
     * Authenticate user with Windows Hello (PIN, Fingerprint, Face, etc.)
     */
    suspend fun authenticateWithBiometric(reason: String = "Authenticate with Windows Hello"): Result<Boolean> {
        if (!isAvailable) {
            return Result.failure(Exception("Windows Hello not available"))
        }

        return try {
            authenticateWithPowerShell(reason)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Authenticate using PowerShell script for Windows Hello APIs
     */
    private suspend fun authenticateWithPowerShell(reason: String): Result<Boolean> = suspendCoroutine { continuation ->
        GlobalScope.launch(Dispatchers.IO) {
            try {
                println("WindowsHelloAuth: Starting Windows Hello authentication...")
                println("WindowsHelloAuth: Triggering biometric prompt - please look at Windows Hello dialog")
                
                // Show system notification to draw user attention
                try {
                    if (SystemTray.isSupported()) {
                        val systemTray = SystemTray.getSystemTray()
                        val image = Toolkit.getDefaultToolkit().createImage(javaClass.getResource("/boss_icon.png"))
                        val trayIcon = TrayIcon(image, "BOSS Authentication")
                        trayIcon.isImageAutoSize = true
                        trayIcon.displayMessage(
                            "Windows Hello Authentication",
                            "Please authenticate using Windows Hello",
                            TrayIcon.MessageType.INFO
                        )
                    }
                } catch (e: Exception) {
                    println("WindowsHelloAuth: Could not show system notification: ${e.message}")
                }
                
                // Execute the PowerShell script that will auto-focus the Windows Hello dialog
                val output = PowerShellExecutor.executePowerShellScript("WindowsHelloAuthentication.ps1", reason)
                
                println("WindowsHelloAuth: PowerShell result: $output")

                when {
                    output.contains("SUCCESS") -> {
                        println("WindowsHelloAuth: Windows Hello authentication successful")
                        continuation.resume(Result.success(true))
                    }
                    output.contains("FAILED") -> {
                        println("WindowsHelloAuth: Windows Hello authentication failed or cancelled")
                        continuation.resume(Result.success(false))
                    }
                    output.contains("UNAVAILABLE") -> {
                        println("WindowsHelloAuth: Windows Hello unavailable")
                        continuation.resume(Result.failure(Exception("Windows Hello unavailable")))
                    }
                    else -> {
                        println("WindowsHelloAuth: Unknown result: $output")
                        continuation.resume(Result.failure(Exception("Unknown authentication result: $output")))
                    }
                }

            } catch (e: Exception) {
                println("WindowsHelloAuth: Exception during authentication: ${e.message}")
                continuation.resume(Result.failure(e))
            }
        }
    }
}