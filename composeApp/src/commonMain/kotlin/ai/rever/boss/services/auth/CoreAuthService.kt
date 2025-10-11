package ai.rever.boss.services.auth

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import ai.rever.boss.services.supabase.SupabaseConfig
import io.github.jan.supabase.auth.auth
import ai.rever.boss.services.auth.AuthStateManager
import ai.rever.boss.services.supabase.models.UserInfo
import ai.rever.boss.services.supabase.AuthService
import io.github.jan.supabase.auth.status.SessionStatus
import kotlin.time.ExperimentalTime

/**
 * Core authentication orchestration service
 * Coordinates between different authentication services
 */
@OptIn(ExperimentalTime::class)
internal object CoreAuthService {
    // Coroutine scope for auth service
    private val authScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    /**
     * Initialize the auth service and check for existing session
     */
    suspend fun initialize() {
        try {
            // Initialize Supabase with build-time configuration
            if (!SupabaseConfig.isInitialized.value) {
                SupabaseConfig.initializeFromEnvironment()
            }
            
            // Wait for session to load from storage and then set proper state
            authScope.launch {
                SupabaseConfig.client.auth.sessionStatus.collect { sessionStatus ->
                    println("CoreAuthService.initialize: SessionStatus changed to: ${sessionStatus::class.simpleName}")
                    
                    when (sessionStatus) {
                        is SessionStatus.Authenticated -> {
                            val user = sessionStatus.session.user
                            val userId = user?.id ?: ""
                            println("CoreAuthService.initialize: Session authenticated, User ID: $userId")

                            // Only update user info if not already set (e.g., by PasskeyAuthService)
                            // or if session.user has valid data
                            val currentUser = AuthStateManager.currentUser.value
                            if (currentUser == null || (userId.isNotEmpty() && currentUser.id != userId)) {
                                if (userId.isNotEmpty()) {
                                    // Update user info from session.user (standard Supabase auth)
                                    AuthStateManager.setCurrentUser(UserInfo(
                                        id = userId,
                                        email = user?.email ?: "",
                                        createdAt = user?.createdAt?.toString() ?: ""
                                    ))
                                    println("CoreAuthService.initialize: Updated user info from session.user")
                                } else {
                                    // Session user is null (custom JWT) - load from SessionManager
                                    SessionManager.loadSession().fold(
                                        onSuccess = { storedUser ->
                                            if (storedUser != null) {
                                                AuthStateManager.setCurrentUser(storedUser)
                                                println("CoreAuthService.initialize: Loaded user info via SessionManager (ID: ${storedUser.id}, Email: ${storedUser.email})")
                                            } else {
                                                println("CoreAuthService.initialize: No user data available (session.user is null and no stored data)")
                                            }
                                        },
                                        onFailure = { error ->
                                            println("CoreAuthService.initialize: Failed to load session: ${error.message}")
                                        }
                                    )
                                }
                            } else if (userId.isEmpty() && currentUser != null) {
                                // Session user is null but we have user data (custom auth like passkey)
                                println("CoreAuthService.initialize: Keeping existing user info from custom auth (ID: ${currentUser.id})")
                            }
                            
                            // All authentication methods (magic link, passkey, biometric) provide inherent 2FA
                            // No additional verification needed - set to Authenticated
                            AuthStateManager.setAuthState(AuthService.AuthState.Authenticated)
                            println("CoreAuthService.initialize: Setting state to Authenticated")
                        }
                        is SessionStatus.NotAuthenticated -> {
                            AuthStateManager.setCurrentUser(null)
                            AuthStateManager.setAuthState(AuthService.AuthState.NotAuthenticated)
                            // Reset magic link flag when session ends
                            AuthStateManager.setAuthenticatedViaMagicLink(false)
                            println("CoreAuthService.initialize: Setting state to NotAuthenticated")
                        }
                        else -> {
                            // Keep loading state for any other status while we wait
                            if (AuthStateManager.authState.value is AuthService.AuthState.Loading) {
                                println("CoreAuthService.initialize: Still waiting for session status: $sessionStatus")
                            } else {
                                println("CoreAuthService.initialize: Other session status: $sessionStatus")
                            }
                        }
                    }
                }
            }
            
        } catch (e: Exception) {
            AuthStateManager.setAuthState(AuthService.AuthState.Error(e.message ?: "Failed to initialize authentication"))
        }
    }
    
    /**
     * Sign out the current user
     */
    suspend fun signOut(): Result<Unit> {
        return try {
            // Use SessionManager for centralized session clearing
            // This handles: Supabase signOut, UserDataStorage clearing, and AuthStateManager reset
            SessionManager.clearSession().fold(
                onSuccess = {
                    println("CoreAuthService: Session cleared successfully via SessionManager")
                },
                onFailure = { error ->
                    println("CoreAuthService: SessionManager.clearSession failed: ${error.message}")
                    // Continue even if SessionManager fails
                }
            )

            Result.success(Unit)
        } catch (e: Exception) {
            println("Logout failed: ${e.message}")
            Result.failure(e)
        }
    }
}