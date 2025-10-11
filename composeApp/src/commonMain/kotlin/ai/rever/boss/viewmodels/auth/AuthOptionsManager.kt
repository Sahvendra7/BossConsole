package ai.rever.boss.viewmodels.auth

import ai.rever.boss.services.supabase.AuthService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Authentication options manager handling user existence checks and option coordination
 * Responsible for: checking user existence, determining available authentication options
 */
class AuthOptionsManager {
    private val viewModelScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    /**
     * Check if a user exists with the given email and return their authentication options
     */
    fun checkUserExists(email: String, onResult: (AuthOptions) -> Unit) {
        if (email.isBlank()) {
            onResult(AuthOptions.Invalid("Please enter a valid email address"))
            return
        }
        
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            
            AuthService.checkUserExists(email).fold(
                onSuccess = { userExistence ->
                    println("AuthOptionsManager: User existence check completed - exists: ${userExistence.exists}, hasPasskeys: ${userExistence.hasPasskeys}")
                    
                    val authOptions = when {
                        userExistence.exists && userExistence.hasPasskeys -> {
                            // User has passkeys - show simplified passkey + password options
                            AuthOptions.WithPasskey(email)
                        }
                        else -> {
                            // User doesn't exist or has no passkeys - show magic link only
                            AuthOptions.MagicLinkOnly(email)
                        }
                    }
                    
                    _isLoading.value = false
                    onResult(authOptions)
                },
                onFailure = { error ->
                    println("AuthOptionsManager: User existence check failed: ${error.message}")
                    // On error, default to magic link authentication
                    _isLoading.value = false
                    onResult(AuthOptions.MagicLinkOnly(email))
                }
            )
        }
    }
    
    fun clearError() {
        _errorMessage.value = null
    }
}

/**
 * Simplified authentication options available for a user
 * Passwordless authentication only - magic link provides inherent 2FA
 */
sealed class AuthOptions {
    data class Invalid(val message: String) : AuthOptions()
    data class WithPasskey(val email: String) : AuthOptions()
    data class MagicLinkOnly(val email: String) : AuthOptions()
}