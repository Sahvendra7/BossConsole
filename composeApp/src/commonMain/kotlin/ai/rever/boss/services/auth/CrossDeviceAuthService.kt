package ai.rever.boss.services.auth

import ai.rever.boss.components.dialogs.openUrlInBrowser
import ai.rever.boss.services.passkey.SupabasePasskeyService
import ai.rever.boss.services.passkey.supabase.PasskeyAuthenticationResult
import ai.rever.boss.services.supabase.CrossDeviceAuthenticationRequired
import io.ktor.http.encodeURLParameter
import kotlinx.coroutines.delay

/**
 * Handles cross-device authentication coordination and QR code flows
 */
internal object CrossDeviceAuthService {
    
    /**
     * Authenticate with cross-device WebAuthn credential using browser flow
     */
    suspend fun authenticateWithCrossDeviceWebAuthn(email: String, credentialId: String): Result<Unit> {
        return try {
            println("Starting cross-device WebAuthn authentication for credential: $credentialId")
            
            // Request authentication challenge from server with sessionId
            val sessionId = java.util.UUID.randomUUID().toString()
            val challengeResult = SupabasePasskeyService.requestAuthenticationChallenge(email, sessionId)
            if (challengeResult.isFailure) {
                return Result.failure(Exception("Failed to get authentication challenge: ${challengeResult.exceptionOrNull()?.message}"))
            }
            
            val challenge = challengeResult.getOrThrow()
            
            // Create the mobile authentication URL
            // Uses RESTful endpoint: GET /auth/mobile
            val baseUrl = "https://api.risaboss.com"
            val mobileAuthUrl = buildString {
                append("$baseUrl/functions/v1/passkey/auth/mobile")
                append("?challenge=${challenge.challenge}")
                append("&email=${email.encodeURLParameter()}")
                append("&sessionId=$sessionId")
                append("&rpId=api.risaboss.com")
                append("&credentialId=${credentialId.encodeURLParameter()}")
            }
            
            println("Cross-device authentication URL: $mobileAuthUrl")
            
            // Open the URL in the system browser
            println("About to open authentication URL in browser...")
            try {
                // Use platform-specific URL opening
                openUrlInBrowser(mobileAuthUrl)
                println("URL opening call completed successfully")
            } catch (e: Exception) {
                println("Failed to open URL in browser: ${e.message}")
                e.printStackTrace()
            }
            
            // Poll for completion (simplified - in production you'd want better polling)
            delay(2000) // Give time for browser to open
            
            // For now, return success - in a full implementation you'd poll the server
            // for authentication completion like we do in enrollment
            Result.success(Unit)
            
        } catch (e: Exception) {
            println("Cross-device WebAuthn authentication failed: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * Check authentication status for cross-device flow
     */
    suspend fun checkAuthenticationStatus(challenge: String, sessionId: String? = null): Result<Boolean> {
        return try {
            val result = SupabasePasskeyService.checkAuthenticationStatus(challenge, sessionId)
            result.fold(
                onSuccess = { authData ->
                    if (authData.success) {
                        // Authentication completed, set up session using PasskeySessionHandler
                        PasskeySessionHandler.completeAuthentication(authData)
                        Result.success(true)
                    } else {
                        Result.success(false)
                    }
                },
                onFailure = { error ->
                    // If challenge not found, it's still pending
                    if (error.message?.contains("not found") == true) {
                        Result.success(false)
                    } else {
                        Result.failure(error)
                    }
                }
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Poll for cross-device authentication completion
     */
    suspend fun pollForAuthenticationCompletion(challenge: String, sessionId: String? = null): Result<PasskeyAuthenticationResult> {
        return try {
            println("Polling for cross-device authentication completion...")
            
            var attempts = 0
            val maxAttempts = 60 // 2 minutes with 2-second intervals
            
            while (attempts < maxAttempts) {
                delay(2000) // Wait 2 seconds between attempts
                attempts++
                
                println("Polling attempt $attempts/$maxAttempts...")
                
                // Check if authentication was completed by polling the challenge
                val checkResult = SupabasePasskeyService.checkAuthenticationStatus(challenge, sessionId)
                
                if (checkResult.isSuccess) {
                    val authData = checkResult.getOrThrow()
                    if (authData.success) {
                        println("Cross-device authentication completed successfully!")
                        return Result.success(authData)
                    }
                } else {
                    // If checking failed due to challenge not found, continue polling
                    val error = checkResult.exceptionOrNull()
                    if (error?.message?.contains("not found") != true) {
                        // If it's not a "not found" error, something else went wrong
                        return Result.failure(error ?: Exception("Authentication check failed"))
                    }
                }
            }
            
            // Timeout reached
            Result.failure(Exception("Authentication timeout - QR code was not scanned within 2 minutes"))
        } catch (e: Exception) {
            println("Error during authentication polling: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * Handle cross-device authentication exception and coordinate QR flow
     */
    suspend fun handleCrossDeviceAuthentication(
        exception: CrossDeviceAuthenticationRequired,
        onAuthenticationComplete: suspend (PasskeyAuthenticationResult) -> Result<Unit>
    ): Result<Unit> {
        return try {
            println("Cross-device authentication required - starting cross-device flow")
            
            // Open browser with the QR URL for mobile authentication
            try {
                openUrlInBrowser(exception.qrCodeUrl)
                println("Opened mobile authentication URL: ${exception.qrCodeUrl}")
            } catch (e: Exception) {
                println("Failed to open mobile authentication URL: ${e.message}")
                return Result.failure(Exception("Failed to open mobile authentication: ${e.message}"))
            }
            
            // Poll for authentication completion instead of calling completeAuthentication
            println("Polling for cross-device authentication completion...")
            val pollingResult = pollForAuthenticationCompletion(exception.challenge, exception.sessionId)
            
            if (pollingResult.isSuccess) {
                val authData = pollingResult.getOrThrow()
                return onAuthenticationComplete(authData)
            } else {
                return Result.failure(pollingResult.exceptionOrNull() ?: Exception("Cross-device authentication failed"))
            }
        } catch (e: Exception) {
            println("Cross-device authentication handling failed: ${e.message}")
            Result.failure(e)
        }
    }
}