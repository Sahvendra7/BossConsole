package ai.rever.boss.services.passkey

import kotlinx.coroutines.*
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

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

    /**
     * Authenticate user with Touch ID / Face ID
     */
    suspend fun authenticateWithBiometric(reason: String = "Authenticate with Touch ID or Face ID"): Result<Boolean> {
        if (!isAvailable) {
            return Result.failure(Exception("Biometric authentication not available"))
        }

        return try {
            authenticateWithSwift(reason)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Authenticate using external Swift script for LocalAuthentication framework
     */
    private suspend fun authenticateWithSwift(reason: String): Result<Boolean> = suspendCoroutine { continuation ->
        GlobalScope.launch(Dispatchers.IO) {
            try {
                println("MacOSTouchIDAuth: Starting Touch ID authentication...")
                
                val output = SwiftScriptExecutor.executeSwiftFile("TouchIDAuthentication.swift", reason)
                
                println("MacOSTouchIDAuth: Swift result: $output")

                when {
                    output.contains("SUCCESS") -> {
                        println("MacOSTouchIDAuth: Touch ID authentication successful")
                        continuation.resume(Result.success(true))
                    }
                    output.contains("FAILED") -> {
                        println("MacOSTouchIDAuth: Touch ID authentication failed or cancelled")
                        continuation.resume(Result.success(false))
                    }
                    output.contains("UNAVAILABLE") -> {
                        println("MacOSTouchIDAuth: Biometric authentication unavailable")
                        continuation.resume(Result.failure(Exception("Biometric authentication unavailable")))
                    }
                    else -> {
                        println("MacOSTouchIDAuth: Unknown result: $output")
                        continuation.resume(Result.failure(Exception("Unknown authentication result: $output")))
                    }
                }

            } catch (e: Exception) {
                println("MacOSTouchIDAuth: Exception during authentication: ${e.message}")
                continuation.resume(Result.failure(e))
            }
        }
    }
}