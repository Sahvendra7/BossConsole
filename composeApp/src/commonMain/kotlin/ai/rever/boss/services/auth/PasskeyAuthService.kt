package ai.rever.boss.services.auth

import ai.rever.boss.services.passkey.PasskeyService
import ai.rever.boss.services.passkey.SupabasePasskeyService
import ai.rever.boss.services.supabase.CrossDeviceAuthenticationRequired
import java.util.Base64
import java.util.UUID
import kotlin.time.ExperimentalTime

/**
 * Handles WebAuthn/passkey authentication (core authentication only)
 *
 * This service focuses on the core authentication ceremony:
 * - Registering new passkeys
 * - Authenticating with existing passkeys
 * - Checking passkey support and availability
 *
 * Related services:
 * - PasskeySessionHandler: Handles session establishment after authentication
 * - PasskeyCredentialManager: Handles credential CRUD operations
 *
 * This separation follows the Single Responsibility Principle.
 */
@OptIn(ExperimentalTime::class)
internal object PasskeyAuthService {
    private var passkeyService: PasskeyService? = null

    /**
     * Set the platform-specific passkey service implementation for authentication operations.
     * Used for browser-based WebAuthn registration and authentication flows.
     */
    fun setPasskeyService(service: PasskeyService) {
        passkeyService = service
        println("PasskeyAuthService: Platform passkey service initialized")
    }

    /**
     * Get passkey state flow from the passkey service
     */
    fun getPasskeyState() = passkeyService?.passkeyState

    /**
     * Check if passkey authentication is available
     */
    suspend fun isPasskeySupported(): Boolean {
        return passkeyService?.isPasskeySupported() ?: false
    }

    /**
     * Register a new passkey for the current user
     * Integrates with Supabase backend for credential storage and verification
     */
    suspend fun registerPasskey(): Result<String> {
        return try {
            val currentUser = AuthStateManager.currentUser.value ?: return Result.failure(Exception("No user logged in"))
            val passkeyService = passkeyService ?: return Result.failure(Exception("Passkey service not available"))
            
            println("Starting simplified passkey registration for user: ${currentUser.id}")
            
            val displayName = currentUser.email.ifBlank {
                "BOSS User ${currentUser.id.take(8)}"
            }
            
            // Step 1: Request registration challenge from Supabase without forcing authenticator type
            // Let the browser/platform choose the best available method
            val challengeResult = SupabasePasskeyService.requestRegistrationChallenge(
                userId = currentUser.id,
                displayName = displayName,
                authenticatorSelection = null  // Let browser decide
            )
            
            if (challengeResult.isFailure) {
                return Result.failure(challengeResult.exceptionOrNull() ?: Exception("Failed to get challenge"))
            }
            
            val challenge = challengeResult.getOrThrow()
            
            // Step 2: Create passkey using platform service
            val registrationResult = passkeyService.registerPasskey(
                userId = currentUser.id,
                displayName = displayName,
                challenge = Base64.getUrlDecoder().decode(challenge.challenge),
                rpId = challenge.rpId
            )
            
            if (registrationResult.isFailure) {
                return Result.failure(registrationResult.exceptionOrNull() ?: Exception("Passkey registration failed"))
            }
            
            val registration = registrationResult.getOrThrow()
            println("PasskeyAuthService: About to call completeRegistration with registration: ${registration.credentialId}")
            
            // Check if this is a browser-initiated registration (skip completion)
            if (registration.credentialId.startsWith("browser-registration-")) {
                println("PasskeyAuthService: Browser registration detected, skipping completeRegistration call")
                return Result.success("Browser registration initiated - complete in browser")
            }
            
            // Step 3: Complete registration with Supabase backend
            val completionResult = try {
                println("PasskeyAuthService: Calling SupabasePasskeyService.completeRegistration...")
                SupabasePasskeyService.completeRegistration(
                    userId = currentUser.id,
                    registration = registration,
                    challenge = challenge.challenge
                )
            } catch (e: Exception) {
                println("PasskeyAuthService: Exception in completeRegistration: ${e.message}")
                println("PasskeyAuthService: Stack trace: ${e.stackTraceToString()}")
                throw e
            }
            
            if (completionResult.isFailure) {
                return Result.failure(completionResult.exceptionOrNull() ?: Exception("Failed to complete registration"))
            }
            
            val credential = completionResult.getOrThrow()
            println("Passkey registered successfully: ${credential.credential_id}")
            
            Result.success(credential.credential_id)
        } catch (e: Exception) {
            println("Passkey registration failed: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * Authenticate using passkey
     * Supports both user-identified and usernameless authentication
     */
    suspend fun authenticateWithPasskey(email: String? = null, credentialId: String? = null): Result<Unit> {
        return try {
            val passkeyService = passkeyService ?: return Result.failure(Exception("Passkey service not available"))
            
            println("Starting passkey authentication for user: ${email ?: "usernameless"}")
            
            // Step 1: Request authentication challenge from Supabase with sessionId for cross-device coordination
            val sessionId = UUID.randomUUID().toString()
            val challengeResult = SupabasePasskeyService.requestAuthenticationChallenge(email, sessionId)
            
            if (challengeResult.isFailure) {
                return Result.failure(challengeResult.exceptionOrNull() ?: Exception("Failed to get challenge"))
            }
            
            val challenge = challengeResult.getOrThrow()
            
            // Step 2: Try platform authentication first, let browser handle fallbacks
            // No more manual flow determination - let WebAuthn API decide the best method
            println("Attempting passkey authentication with browser-native flow selection")

            // Filter to specific credential if provided (for multi-passkey selection)
            val filteredCredentials = if (credentialId != null) {
                challenge.allowCredentials?.filter { it.id == credentialId }
            } else {
                challenge.allowCredentials
            }

            val allowedCredentials = filteredCredentials?.map { it.id }
            val allowedCredentialTransports = filteredCredentials?.associate {
                it.id to (it.transports ?: emptyList())
            }
            val assertionResult = passkeyService.authenticateWithPasskey(
                challenge = Base64.getUrlDecoder().decode(challenge.challenge),
                allowedCredentials = allowedCredentials,
                rpId = challenge.rpId,
                userEmail = email ?: return Result.failure(Exception("User email is required for passkey authentication")),
                sessionId = sessionId,
                allowedCredentialTransports = allowedCredentialTransports
            )
    
            if (assertionResult.isFailure) {
                val exception = assertionResult.exceptionOrNull()
                
                // Check if this is a cross-device authentication requirement
                return if (exception is CrossDeviceAuthenticationRequired) {
                    val result = CrossDeviceAuthService.handleCrossDeviceAuthentication(exception) { authData ->
                        // Add email to authData if missing (cross-device flow doesn't always return it)
                        val enrichedAuthData = if (authData.email == null && email != null) {
                            authData.copy(email = email)
                        } else {
                            authData
                        }
                        // Use PasskeySessionHandler for session establishment
                        PasskeySessionHandler.completeAuthentication(enrichedAuthData)
                    }

                    // Reset passkey state after cross-device authentication
                    if (result.isSuccess) {
                        passkeyService.resetState()
                    }

                    result
                } else {
                    Result.failure(exception ?: Exception("Passkey authentication failed"))
                }
            }
    
            val assertion = assertionResult.getOrThrow()
    
            // Step 3: Complete authentication with Supabase backend (only for local auth success)
            val authResult = SupabasePasskeyService.completeAuthentication(
                assertion = assertion,
                challenge = challenge.challenge
            )
            
            if (authResult.isFailure) {
                return Result.failure(authResult.exceptionOrNull() ?: Exception("Failed to complete authentication"))
            }
            
            val authData = authResult.getOrThrow()

            // Step 4: Use PasskeySessionHandler to complete authentication
            val sessionResult = PasskeySessionHandler.completeAuthentication(authData)

            // Reset passkey state on successful authentication
            if (sessionResult.isSuccess) {
                passkeyService.resetState()
            }

            return sessionResult
        } catch (e: Exception) {
            println("Passkey authentication failed: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Get user's registered passkeys
     * Delegated to PasskeyCredentialManager
     */
    suspend fun getUserPasskeys(): Result<List<ai.rever.boss.services.passkey.PasskeyInfo>> {
        return PasskeyCredentialManager.getUserPasskeys()
    }

    /**
     * Delete a passkey
     * Delegated to PasskeyCredentialManager
     */
    suspend fun deletePasskey(credentialId: String): Result<Unit> {
        return PasskeyCredentialManager.deletePasskey(credentialId)
    }

    /**
     * Reset passkey state to Idle
     * Called after successful authentication or on logout
     */
    fun resetPasskeyState() {
        passkeyService?.resetState()
    }
}
