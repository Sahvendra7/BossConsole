package ai.rever.boss.viewmodels

import ai.rever.boss.services.supabase.AuthService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Core login view model handling passwordless authentication flows
 * Responsible for: magic link authentication, email verification
 *
 * Note: Password-based authentication (signIn/signUp) has been removed.
 * This app uses passwordless authentication only (passkeys + magic links).
 */
class CoreLoginViewModel {
    private val viewModelScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()


    fun sendMagicLink(email: String, onSuccess: () -> Unit) {
        if (email.isBlank()) {
            _errorMessage.value = "Please enter your email"
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            AuthService.sendMagicLink(email).fold(
                onSuccess = {
                    println("Magic link sent successfully")
                    // Don't set success message as error - navigate to waiting screen instead
                    _errorMessage.value = null
                    onSuccess()
                },
                onFailure = { error ->
                    println("Magic link sending failed: ${error.message}")
                    _errorMessage.value = error.message
                }
            )

            _isLoading.value = false
        }
    }

    /**
     * Clear error message
     */
    fun clearError() {
        _errorMessage.value = null
    }

}
