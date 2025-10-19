package ai.rever.boss.services.auth

import ai.rever.boss.services.supabase.SupabaseConfig
import ai.rever.boss.services.supabase.models.UserInfo
import ai.rever.boss.services.supabase.AuthService
import ai.rever.boss.services.supabase.RoleService
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.user.UserSession
import kotlin.time.ExperimentalTime

/**
 * SessionManager - Single source of truth for session operations
 *
 * This service centralizes all session management logic, including:
 * - Establishing sessions with both JWT tokens and user data
 * - Loading existing sessions from persistent storage
 * - Clearing sessions on logout
 *
 * WHY THIS EXISTS:
 * Custom authentication providers (like passkeys) generate custom JWT tokens
 * that don't populate session.user in Supabase-KT. This is expected behavior.
 * SessionManager coordinates between Supabase auth (JWT tokens) and
 * UserDataStorage (user information) to provide a complete session management solution.
 */
@OptIn(ExperimentalTime::class)
object SessionManager {

    /**
     * Authentication method enum for tracking how user authenticated
     */
    enum class AuthMethod {
        PASSKEY,
        MAGIC_LINK,
    }

    /**
     * Establish a new session with both JWT tokens and user information
     *
     * This method:
     * 1. Imports the session using Supabase-KT's importSession() for proper persistence
     * 2. Persists user data separately using UserDataStorage (needed for custom JWTs)
     * 3. Updates AuthStateManager with current user and authenticated state
     *
     * @param accessToken JWT access token from authentication
     * @param refreshToken JWT refresh token for session renewal
     * @param userInfo User information (id, email, etc.)
     * @param authMethod How the user authenticated (for tracking purposes)
     * @param expiresIn Token expiration time in seconds (default: 3600 = 1 hour)
     * @return Result indicating success or failure
     */
    suspend fun establishSession(
        accessToken: String,
        refreshToken: String,
        userInfo: UserInfo,
        authMethod: AuthMethod,
        expiresIn: Long = 3600L
    ): Result<Unit> {
        return try {
            println("SessionManager: Establishing session for ${userInfo.email} (via ${authMethod.name.lowercase()})")

            // Step 1: Import session into Supabase Auth for persistence and auto-refresh
            try {
                SupabaseConfig.client.auth.importSession(
                    UserSession(
                        accessToken = accessToken,
                        refreshToken = refreshToken,
                        expiresIn = expiresIn,
                        tokenType = "Bearer",
                        user = null  // Supabase-KT doesn't populate this for custom JWTs - expected behavior
                    )
                )
                println("SessionManager: ✅ Session imported successfully with importSession()")
                println("SessionManager: ✅ Session will persist across app restarts")
                println("SessionManager: ✅ Access token will auto-refresh using refresh token")
            } catch (e: Exception) {
                println("SessionManager: ❌ Failed to import session: ${e.message}")
                return Result.failure(Exception("Failed to establish Supabase session: ${e.message}"))
            }

            // Step 2: Verify session was established
            val currentSession = SupabaseConfig.client.auth.currentSessionOrNull()
            if (currentSession == null) {
                println("SessionManager: ❌ Session verification failed - no current session")
                return Result.failure(Exception("Failed to establish Supabase session"))
            }
            println("SessionManager: ✅ Session verification successful")

            // Step 3: Persist user data separately (required for custom auth providers)
            // This is NOT a workaround - it's the correct pattern for custom JWTs
            UserDataStorage.saveUserData(userInfo, authenticatedVia = authMethod.name.lowercase())
            println("SessionManager: ✅ User data persisted to storage")

            // Step 4: Update global auth state
            AuthStateManager.setCurrentUser(userInfo)

            // Set biometric flag for passkey authentication (bypasses 2FA)
            if (authMethod == AuthMethod.PASSKEY) {
                AuthStateManager.setAuthenticatedViaBiometric(true)
            }

            // Set magic link flag for magic link authentication (bypasses 2FA)
            if (authMethod == AuthMethod.MAGIC_LINK) {
                AuthStateManager.setAuthenticatedViaMagicLink(true)
            }

            AuthStateManager.setAuthState(AuthService.AuthState.Authenticated)
            println("SessionManager: ✅ Auth state updated to Authenticated")

            println("SessionManager: Session establishment complete for ${userInfo.email}")
            Result.success(Unit)
        } catch (e: Exception) {
            println("SessionManager: Session establishment failed: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Load an existing session from persistent storage
     *
     * This method is called during app initialization to restore previous sessions.
     * It loads user data from UserDataStorage since custom JWT sessions don't
     * populate session.user in Supabase-KT.
     *
     * @return Result containing UserInfo if a stored session exists, null otherwise
     */
    suspend fun loadSession(): Result<UserInfo?> {
        return try {
            // Check if we have a valid Supabase session
            val currentSession = SupabaseConfig.client.auth.currentSessionOrNull()

            if (currentSession != null) {
                println("SessionManager: Found existing Supabase session")

                // Try to get user from session.user first (standard Supabase auth)
                val sessionUser = currentSession.user
                if (sessionUser?.id?.isNotEmpty() == true) {
                    println("SessionManager: Using user data from session.user (standard auth)")
                    // Parse role claims from JWT
                    val roleClaims = RoleService.parseRoleClaimsFromSession(currentSession)

                    val userInfo = UserInfo(
                        id = sessionUser.id,
                        email = sessionUser.email ?: "",
                        createdAt = sessionUser.createdAt?.toString() ?: "",
                        roleClaims = roleClaims
                    )
                    return Result.success(userInfo)
                }

                // Session user is null (custom JWT) - load from persistent storage
                println("SessionManager: session.user is null (custom JWT), loading from storage")
                val storedUser = UserDataStorage.loadUserData()

                if (storedUser != null) {
                    println("SessionManager: ✅ Loaded user from storage (${storedUser.email})")
                    return Result.success(storedUser)
                } else {
                    println("SessionManager: ⚠️ No stored user data found")
                    return Result.success(null)
                }
            } else {
                println("SessionManager: No existing Supabase session")
                return Result.success(null)
            }
        } catch (e: Exception) {
            println("SessionManager: Failed to load session: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Clear the current session and all associated data
     *
     * This method:
     * 1. Signs out from Supabase Auth (clears JWT tokens)
     * 2. Clears persisted user data from UserDataStorage
     * 3. Resets AuthStateManager to unauthenticated state
     *
     * @return Result indicating success or failure
     */
    suspend fun clearSession(): Result<Unit> {
        return try {
            println("SessionManager: Clearing session")

            // Get current user ID before clearing for cleanup purposes
            AuthStateManager.currentUser.value?.id

            // Step 1: Sign out from Supabase (network request)
            try {
                SupabaseConfig.client.auth.signOut()
                println("SessionManager: ✅ Signed out from Supabase")
            } catch (e: Exception) {
                println("SessionManager: ⚠️ Supabase signOut failed: ${e.message}")
                // Continue with cleanup even if network request fails
            }

            // Step 2: Clear persisted user data
            UserDataStorage.clearUserData()
            println("SessionManager: ✅ Cleared user data storage")

            // Step 3: Reset auth state
            AuthStateManager.reset()
            println("SessionManager: ✅ Reset auth state")

            println("SessionManager: Session cleared successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            println("SessionManager: Failed to clear session: ${e.message}")
            Result.failure(e)
        }
    }

}
