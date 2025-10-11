package ai.rever.boss.services.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ai.rever.boss.services.supabase.models.UserInfo
import ai.rever.boss.services.supabase.AuthService

/**
 * Manages authentication state and user session information
 */
internal object AuthStateManager {
    // Main authentication state
    private val _authState = MutableStateFlow<AuthService.AuthState>(AuthService.AuthState.Loading)
    val authState: StateFlow<AuthService.AuthState> = _authState.asStateFlow()
    
    // Current user information
    private val _currentUser = MutableStateFlow<UserInfo?>(null)
    val currentUser: StateFlow<UserInfo?> = _currentUser.asStateFlow()
    
    // Email verification events from deep links
    private val _emailVerificationEvent = MutableStateFlow<String?>(null)
    val emailVerificationEvent: StateFlow<String?> = _emailVerificationEvent.asStateFlow()
    
    // Cross-device authentication state
    private val _currentAuthenticationQRUrl = MutableStateFlow<String?>(null)
    val currentAuthenticationQRUrl: StateFlow<String?> = _currentAuthenticationQRUrl.asStateFlow()
    
    private val _currentAuthenticationChallenge = MutableStateFlow<String?>(null)
    val currentAuthenticationChallenge: StateFlow<String?> = _currentAuthenticationChallenge.asStateFlow()
    
    // Authentication flags
    var authenticatedViaBiometric = false
        private set
    
    var authenticatedViaMagicLink = false
        private set
    
    var pendingTwoFactorVerification = false
        private set
    
    /**
     * Update authentication state
     */
    fun setAuthState(state: AuthService.AuthState) {
        _authState.value = state
    }
    
    /**
     * Update current user information
     */
    fun setCurrentUser(user: UserInfo?) {
        _currentUser.value = user
    }
    
    /**
     * Set biometric authentication flag
     */
    fun setAuthenticatedViaBiometric(value: Boolean) {
        authenticatedViaBiometric = value
    }

    /**
     * Set magic link authentication flag
     */
    fun setAuthenticatedViaMagicLink(value: Boolean) {
        authenticatedViaMagicLink = value
    }
    
    /**
     * Set pending 2FA verification flag
     */
    fun setPendingTwoFactorVerification(value: Boolean) {
        pendingTwoFactorVerification = value
    }
    
    /**
     * Trigger email verification event
     */
    fun triggerEmailVerificationEvent(token: String) {
        _emailVerificationEvent.value = token
    }
    
    /**
     * Clear email verification event
     */
    fun clearEmailVerificationEvent() {
        _emailVerificationEvent.value = null
    }
    
    /**
     * Set cross-device authentication QR URL
     */
    fun setAuthenticationQRUrl(url: String?) {
        _currentAuthenticationQRUrl.value = url
    }
    
    /**
     * Set cross-device authentication challenge
     */
    fun setAuthenticationChallenge(challenge: String?) {
        _currentAuthenticationChallenge.value = challenge
    }
    
    /**
     * Reset all authentication state
     */
    fun reset() {
        _currentUser.value = null
        _authState.value = AuthService.AuthState.NotAuthenticated
        authenticatedViaBiometric = false
        authenticatedViaMagicLink = false
        pendingTwoFactorVerification = false
        _emailVerificationEvent.value = null
        _currentAuthenticationQRUrl.value = null
        _currentAuthenticationChallenge.value = null
    }
}