package ai.rever.boss.services.passkey.supabase

import ai.rever.boss.services.passkey.*
import io.ktor.client.statement.*
import io.ktor.http.*

/**
 * Exception thrown when no passkeys are found for a user
 */
class NoPasskeysFoundException(message: String) : Exception(message)

/**
 * Handles passkey authentication flow operations
 */
internal object PasskeyAuthenticationHandler {
    
    /**
     * Request passkey authentication challenge
     */
    suspend fun requestChallenge(
        email: String? = null,
        sessionId: String? = null
    ): Result<PasskeyAuthenticationChallenge> {
        return try {
            println("🔍 [DEBUG] Requesting passkey authentication challenge for user: ${email ?: "usernameless"}")
            println("🔍 [DEBUG] SessionId: $sessionId")
            
            val challenge = PasskeyDataMapper.generateChallenge()
            println("🔍 [DEBUG] Generated challenge: ${challenge.take(20)}...")
            
            val requestData = PasskeyDataMapper.createAuthenticationRequest(
                email = email,
                challenge = challenge,
                sessionId = sessionId
            )
            println("🔍 [DEBUG] Request data: ${PasskeyDataMapper.publicJson.encodeToString(PasskeyAuthenticationRequest.serializer(), requestData)}")
            
            println("🔍 [DEBUG] About to call SupabaseApiClient.invokeAuthenticationChallenge()")
            
            // Call Edge Function for authentication challenge - operation is in body
            val response = SupabaseApiClient.invokeAuthenticationChallenge(requestData)
            
            println("🔍 [DEBUG] Got response, status: ${response.status}")
            val responseText = response.bodyAsText()
            println("🔍 [DEBUG] Response text: $responseText")
            
            // Check if the response indicates no passkeys found (404)
            if (response.status.value == 404 && responseText.contains("No passkeys found")) {
                println("🔍 [DEBUG] No passkeys found for user - this is expected for users without passkeys")
                return Result.failure(NoPasskeysFoundException("No passkeys found for user"))
            }
            
            // Check for other error statuses
            if (response.status.value >= 400) {
                println("❌ [ERROR] Server returned error status: ${response.status}")
                return Result.failure(Exception("Server error: $responseText"))
            }
            
            val challengeResponse = PasskeyDataMapper.parseAuthenticationChallenge(responseText)
            println("🔍 [DEBUG] Parsed challenge response successfully")
            
            Result.success(challengeResponse)
        } catch (e: Exception) {
            println("❌ [ERROR] Failed to request authentication challenge: ${e.message}")
            println("❌ [ERROR] Exception type: ${e::class.simpleName}")
            Result.failure(e)
        }
    }
    
    /**
     * Complete passkey authentication
     */
    suspend fun completeAuthentication(
        assertion: PasskeyAssertion,
        challenge: String
    ): Result<PasskeyAuthenticationResult> {
        return try {
            println("Completing passkey authentication for credential: ${assertion.credentialId}")
            
            val authenticationData = PasskeyDataMapper.createAuthenticationData(
                assertion = assertion,
                challenge = challenge
            )
            
            // Call Edge Function for authentication completion
            val response = SupabaseApiClient.completeAuthentication(authenticationData)
            
            val responseText = response.bodyAsText()
            val authResult = PasskeyDataMapper.parseAuthenticationResult(responseText)
            
            if (authResult.success) {
                println("Passkey authentication completed successfully for user: ${authResult.userId}")
                Result.success(authResult)
            } else {
                println("Passkey authentication failed: ${authResult.error}")
                Result.failure(Exception(authResult.error ?: "Authentication failed"))
            }
        } catch (e: Exception) {
            println("Failed to complete authentication: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * Check authentication status for cross-device flows
     */
    suspend fun checkStatus(challenge: String, sessionId: String? = null): Result<PasskeyAuthenticationResult> {
        return try {
            println("Checking authentication status for challenge: ${challenge.take(10)}...")
            
            // Use sessionId for status check endpoint (GET /auth/status/{sessionId})
            val effectiveSessionId = sessionId ?: challenge // Fallback to challenge if no sessionId

            val response = SupabaseApiClient.checkAuthenticationStatus(effectiveSessionId)
            
            val responseText = response.bodyAsText()
            println("Authentication status check response: $responseText")
            
            if (!response.status.isSuccess()) {
                return Result.failure(Exception("Failed to check authentication status: HTTP ${response.status.value}"))
            }
            
            val authResult = PasskeyDataMapper.parseAuthenticationResult(responseText)
            Result.success(authResult)
            
        } catch (e: Exception) {
            println("Error checking authentication status: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * Validate authentication request parameters
     */
    fun validateAuthenticationRequest(
        email: String?,
        sessionId: String?
    ): Result<Unit> {
        return when {
            email != null && email.isBlank() -> Result.failure(
                IllegalArgumentException("Email cannot be blank if provided")
            )
            email != null && !isValidEmail(email) -> Result.failure(
                IllegalArgumentException("Invalid email format")
            )
            sessionId != null && sessionId.isBlank() -> Result.failure(
                IllegalArgumentException("Session ID cannot be blank if provided")
            )
            sessionId != null && sessionId.length > 128 -> Result.failure(
                IllegalArgumentException("Session ID cannot exceed 128 characters")
            )
            else -> Result.success(Unit)
        }
    }
    
    /**
     * Validate authentication completion data
     */
    fun validateAuthenticationCompletion(
        assertion: PasskeyAssertion,
        challenge: String
    ): Result<Unit> {
        return when {
            challenge.isBlank() -> Result.failure(
                IllegalArgumentException("Challenge cannot be blank")
            )
            assertion.credentialId.isBlank() -> Result.failure(
                IllegalArgumentException("Credential ID cannot be blank")
            )
            assertion.authenticatorData.isBlank() -> Result.failure(
                IllegalArgumentException("Authenticator data cannot be blank")
            )
            assertion.signature.isBlank() -> Result.failure(
                IllegalArgumentException("Signature cannot be blank")
            )
            assertion.clientDataJSON.isBlank() -> Result.failure(
                IllegalArgumentException("Client data JSON cannot be blank")
            )
            else -> Result.success(Unit)
        }
    }
    
    /**
     * Validate authentication status check parameters
     */
    fun validateStatusCheck(
        challenge: String,
        sessionId: String?
    ): Result<Unit> {
        return when {
            challenge.isBlank() -> Result.failure(
                IllegalArgumentException("Challenge cannot be blank")
            )
            sessionId != null && sessionId.isBlank() -> Result.failure(
                IllegalArgumentException("Session ID cannot be blank if provided")
            )
            else -> Result.success(Unit)
        }
    }

    /**
     * Simple email validation
     */
    private fun isValidEmail(email: String): Boolean {
        val emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$".toRegex()
        return email.matches(emailRegex)
    }
}
