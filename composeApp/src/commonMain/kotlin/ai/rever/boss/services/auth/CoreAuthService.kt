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
import ai.rever.boss.services.supabase.RoleService
import ai.rever.boss.services.network.NetworkMonitorService
import ai.rever.boss.utils.VersionVerifier
import ai.rever.boss.utils.logging.BossLogger
import ai.rever.boss.utils.logging.LogCategory
import ai.rever.boss.utils.logging.LogSanitizer
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.ExperimentalTime

/**
 * Core authentication orchestration service
 * Coordinates between different authentication services
 */
@OptIn(ExperimentalTime::class)
internal object CoreAuthService {
    // Coroutine scope for auth service
    private val authScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val logger = BossLogger.forComponent("CoreAuthService")

    // Track when session status first resolves (authenticated or not)
    // Used by UI to know when auth system is ready
    private val _isSessionResolved = MutableStateFlow(false)
    val isSessionResolved: StateFlow<Boolean> = _isSessionResolved.asStateFlow()

    // Prevent duplicate initialization attempts (race condition fix)
    private var isInitializing = false

    /**
     * Initialize the auth service and check for existing session
     */
    fun initialize() {
        try {
            // Verify version consistency at startup (Issue #111 fix)
            VersionVerifier.verifyVersionConsistency()

            // Check network connectivity before initializing Supabase
            authScope.launch {
                val isConnected = NetworkMonitorService.checkConnectivity()
                if (!isConnected) {
                    logger.warn(LogCategory.NETWORK, "No network connectivity, entering offline state")
                    handleOfflineStart()
                    return@launch
                }

                // Network is available, proceed with normal initialization
                initializeWithNetwork()
            }
        } catch (e: Exception) {
            AuthStateManager.setAuthState(AuthService.AuthState.Error(e.message ?: "Failed to initialize authentication"))
        }
    }

    /**
     * Initialize authentication with network available
     * Uses isInitializing flag to prevent duplicate initialization attempts
     */
    private fun initializeWithNetwork() {
        // Prevent duplicate initialization (race condition fix)
        if (isInitializing) {
            logger.debug(LogCategory.AUTH, "Already initializing, skipping")
            return
        }
        isInitializing = true

        // Stop any running auto-retry since we're now initializing
        NetworkMonitorService.stopAutoRetry()

        try {
            // Initialize Supabase with build-time configuration
            if (!SupabaseConfig.isInitialized.value) {
                SupabaseConfig.initializeFromEnvironment()
            }

            // Wait for session to load from storage and then set proper state
            authScope.launch {
                SupabaseConfig.client.auth.sessionStatus.collect { sessionStatus ->
                    logger.debug(LogCategory.AUTH, "SessionStatus changed", mapOf("status" to sessionStatus::class.simpleName))

                    when (sessionStatus) {
                        is SessionStatus.Authenticated -> {
                            // Mark session as resolved (user is authenticated)
                            _isSessionResolved.value = true

                            val user = sessionStatus.session.user
                            val userId = user?.id ?: ""
                            logger.debug(LogCategory.AUTH, "Session authenticated", mapOf("hasUserId" to userId.isNotEmpty()))

                            // Only update user info if not already set (e.g., by PasskeyAuthService)
                            // or if session.user has valid data
                            val currentUser = AuthStateManager.currentUser.value
                            if (currentUser == null || (userId.isNotEmpty() && currentUser.id != userId)) {
                                if (userId.isNotEmpty()) {
                                    // Update user info from session.user (standard Supabase auth)
                                    // Parse role claims from JWT
                                    val roleClaims = RoleService.parseRoleClaimsFromSession(sessionStatus.session)

                                    AuthStateManager.setCurrentUser(UserInfo(
                                        id = userId,
                                        email = user?.email ?: "",
                                        createdAt = user?.createdAt?.toString() ?: "",
                                        roleClaims = roleClaims
                                    ))
                                    logger.debug(LogCategory.AUTH, "Updated user info from session.user", mapOf(
                                        "hasRoleClaims" to (roleClaims != null),
                                        "isAdmin" to (roleClaims?.isAdmin ?: false)
                                    ))
                                } else {
                                    // Session user is null (custom JWT) - load from SessionManager
                                    SessionManager.loadSession().fold(
                                        onSuccess = { storedUser ->
                                            if (storedUser != null) {
                                                AuthStateManager.setCurrentUser(storedUser)
                                                logger.debug(LogCategory.AUTH, "Loaded user info via SessionManager", mapOf(
                                                    "email" to LogSanitizer.maskEmail(storedUser.email)
                                                ))
                                            } else {
                                                logger.debug(LogCategory.AUTH, "No user data available (session.user is null and no stored data)")
                                            }
                                        },
                                        onFailure = { error ->
                                            logger.warn(LogCategory.AUTH, "Failed to load session", error = error)
                                        }
                                    )
                                }
                            } else if (userId.isEmpty()) {
                                // Session user is null but we have user data (custom auth like passkey)
                                logger.debug(LogCategory.AUTH, "Keeping existing user info from custom auth")
                            }

                            // All authentication methods (magic link, passkey, biometric) provide inherent 2FA
                            // No additional verification needed - set to Authenticated
                            AuthStateManager.setAuthState(AuthService.AuthState.Authenticated)
                            logger.info(LogCategory.AUTH, "Auth state set to Authenticated")
                        }
                        is SessionStatus.NotAuthenticated -> {
                            // Mark session as resolved (user is not authenticated)
                            _isSessionResolved.value = true

                            AuthStateManager.setCurrentUser(null)
                            AuthStateManager.setAuthState(AuthService.AuthState.NotAuthenticated)
                            // Reset magic link flag when session ends
                            AuthStateManager.setAuthenticatedViaMagicLink(false)
                            logger.info(LogCategory.AUTH, "Auth state set to NotAuthenticated")
                        }
                        else -> {
                            // Keep loading state for any other status while we wait
                            if (AuthStateManager.authState.value is AuthService.AuthState.Loading) {
                                logger.debug(LogCategory.AUTH, "Still waiting for session status", mapOf("status" to sessionStatus.toString()))
                            } else {
                                logger.debug(LogCategory.AUTH, "Other session status", mapOf("status" to sessionStatus.toString()))
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
     * Handle offline state at startup
     * Sets auth state to Offline and starts auto-retry
     */
    private fun handleOfflineStart() {
        _isSessionResolved.value = true
        AuthStateManager.setAuthState(AuthService.AuthState.Offline)

        // Start auto-retry in background
        NetworkMonitorService.startAutoRetry {
            logger.info(LogCategory.NETWORK, "Network restored, retrying initialization")
            initializeWithNetwork()
        }
    }

    /**
     * Retry initialization after network is restored
     * Called from OfflineScreen retry button
     */
    suspend fun retryInitialization(): Boolean {
        val isConnected = NetworkMonitorService.manualRetry()
        if (isConnected) {
            logger.info(LogCategory.NETWORK, "Network restored, initializing")
            initializeWithNetwork()
        }
        return isConnected
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
                    logger.info(LogCategory.AUTH, "Session cleared successfully via SessionManager")
                },
                onFailure = { error ->
                    logger.warn(LogCategory.AUTH, "SessionManager.clearSession failed", error = error)
                    // Continue even if SessionManager fails
                }
            )

            // Reset passkey state on logout
            PasskeyAuthService.resetPasskeyState()

            Result.success(Unit)
        } catch (e: Exception) {
            logger.error(LogCategory.AUTH, "Logout failed", error = e)
            Result.failure(e)
        }
    }
}

