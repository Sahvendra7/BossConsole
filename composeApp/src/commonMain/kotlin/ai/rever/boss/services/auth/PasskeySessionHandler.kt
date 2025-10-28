package ai.rever.boss.services.auth

import ai.rever.boss.services.passkey.supabase.PasskeyAuthenticationResult
import ai.rever.boss.services.supabase.SupabaseConfig
import ai.rever.boss.services.supabase.models.UserInfo
import ai.rever.boss.services.supabase.RoleService
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.OTP
import io.github.jan.supabase.auth.user.UserSession
import kotlin.time.ExperimentalTime

/**
 * PasskeySessionHandler - Handles session establishment after passkey authentication
 *
 * This service is responsible for:
 * - Completing passkey authentication by setting up user sessions
 * - Coordinating with SessionManager for session establishment
 * - Handling both successful and failed authentication flows
 *
 * Separated from PasskeyAuthService to follow Single Responsibility Principle.
 * PasskeyAuthService handles the authentication ceremony,
 * PasskeySessionHandler handles what happens after successful authentication.
 */
@OptIn(ExperimentalTime::class)
object PasskeySessionHandler {

    /**
     * Complete passkey authentication by setting up user session
     *
     * This method:
     * 1. Validates authentication result
     * 2. Checks for session tokens from server
     * 3. Uses SessionManager to establish the session
     * 4. Falls back to magic link if tokens are missing
     *
     * @param authData Authentication result from passkey verification
     * @return Result indicating success or failure
     */
    suspend fun completeAuthentication(authData: PasskeyAuthenticationResult): Result<Unit> {
        return try {
            if (!authData.success) {
                return Result.failure(Exception(authData.error ?: "Authentication failed"))
            }

            // Validate required fields
            if (authData.userId == null || authData.email == null) {
                return Result.failure(Exception("Authentication failed: missing user ID or email"))
            }

            println("PasskeySessionHandler: Processing authentication for user: ${authData.email}")

            // Check if we have session tokens from server
            if (authData.accessToken != null && authData.refreshToken != null) {
                println("PasskeySessionHandler: Server provided real Supabase session tokens")

                // Parse role claims from access token if available
                val roleClaims = authData.accessToken.let { token ->
                    try {
                        // Create temporary session for parsing
                        val tempSession = UserSession(
                            accessToken = token,
                            refreshToken = authData.refreshToken,
                            expiresIn = authData.expiresAt?.minus(java.time.Instant.now().epochSecond) ?: 3600,
                            tokenType = "bearer",
                            user = null
                        )
                        RoleService.parseRoleClaimsFromSession(tempSession)
                    } catch (e: Exception) {
                        println("PasskeySessionHandler: Failed to parse role claims: ${e.message}")
                        null
                    }
                }

                // Create user info from authentication response
                val userInfo = UserInfo(
                    id = authData.userId,
                    email = authData.email,
                    createdAt = java.time.Instant.now().toString(),
                    roleClaims = roleClaims
                )

                // Calculate expiration time
                val expiresIn = authData.expiresAt?.minus(java.time.Instant.now().epochSecond) ?: 3600L

                // Use SessionManager for centralized session establishment
                // This handles: importSession, UserDataStorage, and AuthStateManager updates
                SessionManager.establishSession(
                    accessToken = authData.accessToken,
                    refreshToken = authData.refreshToken,
                    userInfo = userInfo,
                    authMethod = SessionManager.AuthMethod.PASSKEY,
                    expiresIn = expiresIn
                ).fold(
                    onSuccess = {
                        println("PasskeySessionHandler: ✅ Session established successfully")
                    },
                    onFailure = { error ->
                        println("PasskeySessionHandler: ❌ Failed to establish session: ${error.message}")
                        return Result.failure(error)
                    }
                )
            } else {
                // No tokens provided - trigger magic link authentication as fallback
                println("PasskeySessionHandler: ⚠️ No session tokens from passkey auth - requesting magic link for session creation")

                try {
                    // Request a magic link to establish a proper session
                    SupabaseConfig.client.auth.signInWith(OTP) {
                        email = authData.email
                    }
                    println("PasskeySessionHandler: ✉️ Magic link sent to ${authData.email} - please check email to complete sign-in")
                    return Result.failure(Exception("Magic link sent to ${authData.email}. Please check your email to complete sign-in."))
                } catch (e: Exception) {
                    println("PasskeySessionHandler: ❌ Failed to send magic link: ${e.message}")
                    return Result.failure(Exception("Passkey authentication succeeded but failed to create session. Please try magic link login."))
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            println("PasskeySessionHandler: Failed to complete authentication: ${e.message}")
            Result.failure(e)
        }
    }
}
