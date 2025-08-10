package ai.rever.boss.viewmodels

import ai.rever.boss.services.supabase.AuthService
import ai.rever.boss.services.supabase.AuthState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class LoginViewModel {
    private val viewModelScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    private val _requires2FA = MutableStateFlow(false)
    val requires2FA: StateFlow<Boolean> = _requires2FA.asStateFlow()
    
    // Store credentials for auto sign-in after email verification
    private var pendingSignUpEmail: String = ""
    private var pendingSignUpPassword: String = ""
    
    init {
        // Listen for email verification events from deep links
        viewModelScope.launch {
            AuthService.emailVerificationEvent.collectLatest { token ->
                token?.let {
                    println("LoginViewModel: Received email verification event with token: $it")
                    // Auto-trigger verification with stored credentials if available
                    handleAutoEmailVerification(it)
                    // Clear the event after processing
                    AuthService.clearEmailVerificationEvent()
                }
            }
        }
    }
    
    fun signIn(email: String, password: String, onSuccess: () -> Unit) {
        if (email.isBlank() || password.isBlank()) {
            _errorMessage.value = "Please enter email and password"
            return
        }
        
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            
            AuthService.signIn(email, password).fold(
                onSuccess = {
                    // Sign in successful - AuthService will handle 2FA state management
                    // LoginScreen will react to AuthState changes automatically
                    println("LoginViewModel: Sign in successful, AuthService managing state")
                    _isLoading.value = false
                },
                onFailure = { error ->
                    _errorMessage.value = error.message
                    _isLoading.value = false
                }
            )
        }
    }
    
    fun signUp(email: String, password: String, onSuccess: () -> Unit) {
        if (email.isBlank() || password.isBlank()) {
            _errorMessage.value = "Please enter email and password"
            return
        }
        
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            
            AuthService.signUp(email, password).fold(
                onSuccess = {
                    // Store credentials for auto sign-in after email verification
                    pendingSignUpEmail = email
                    pendingSignUpPassword = password
                    
                    _errorMessage.value = "Account created! Please check your email to confirm."
                    onSuccess()
                },
                onFailure = { error ->
                    _errorMessage.value = error.message
                }
            )
            
            _isLoading.value = false
        }
    }
    
    fun verifyEmail(token: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            
            // Use auto sign-in after email verification if we have stored credentials
            if (pendingSignUpEmail.isNotBlank() && pendingSignUpPassword.isNotBlank()) {
                println("LoginViewModel: Auto signing in after email verification")
                AuthService.verifyEmailAndSignIn(token, pendingSignUpEmail, pendingSignUpPassword).fold(
                    onSuccess = {
                        // Clear stored credentials
                        pendingSignUpEmail = ""
                        pendingSignUpPassword = ""
                        
                        _errorMessage.value = "Email verified and signed in successfully!"
                        onSuccess()
                    },
                    onFailure = { error ->
                        _errorMessage.value = error.message ?: "Verification or sign-in failed"
                    }
                )
            } else {
                // Fallback to regular email verification
                AuthService.verifyEmail(token).fold(
                    onSuccess = {
                        _errorMessage.value = "Email verified successfully!"
                        onSuccess()
                    },
                    onFailure = { error ->
                        _errorMessage.value = error.message ?: "Verification failed"
                    }
                )
            }
            
            _isLoading.value = false
        }
    }
    
    fun verify2FAEnrollment(factorId: String, code: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            
            AuthService.verify2FAEnrollment(factorId, code).fold(
                onSuccess = {
                    _errorMessage.value = "2FA successfully enabled!"
                    onSuccess()
                },
                onFailure = { error ->
                    _errorMessage.value = error.message ?: "Verification failed"
                }
            )
            
            _isLoading.value = false
        }
    }
    
    fun verify2FAChallenge(factorId: String, challengeId: String, code: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            
            AuthService.verify2FAChallenge(factorId, challengeId, code).fold(
                onSuccess = {
                    onSuccess()
                },
                onFailure = { error ->
                    _errorMessage.value = error.message ?: "2FA verification failed"
                }
            )
            
            _isLoading.value = false
        }
    }
    
    fun clearError() {
        _errorMessage.value = null
    }
    
    /**
     * Handle automatic email verification from deep link
     */
    private fun handleAutoEmailVerification(token: String) {
        println("LoginViewModel: Handling auto email verification")
        
        if (pendingSignUpEmail.isNotBlank() && pendingSignUpPassword.isNotBlank()) {
            println("LoginViewModel: Found pending credentials, starting auto sign-in process")
            
            viewModelScope.launch {
                _isLoading.value = true
                _errorMessage.value = null
                
                // Use auto sign-in after email verification with stored credentials
                AuthService.verifyEmailAndSignIn(token, pendingSignUpEmail, pendingSignUpPassword).fold(
                    onSuccess = {
                        println("LoginViewModel: Auto email verification and sign-in successful!")
                        
                        // Clear stored credentials
                        pendingSignUpEmail = ""
                        pendingSignUpPassword = ""
                        
                        _isLoading.value = false
                        _errorMessage.value = "Email verified and signed in successfully!"
                        
                        // The AuthState change will be handled by the UI components
                    },
                    onFailure = { error ->
                        println("LoginViewModel: Auto email verification failed: ${error.message}")
                        _isLoading.value = false
                        _errorMessage.value = error.message ?: "Auto verification failed"
                    }
                )
            }
        } else {
            println("LoginViewModel: No pending credentials found for auto verification")
            _errorMessage.value = "Email verification received, but no pending sign-up found"
        }
    }
    
    fun resetPasswordRequest(email: String, onSuccess: () -> Unit) {
        if (email.isBlank()) {
            _errorMessage.value = "Please enter your email"
            return
        }
        
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            
            AuthService.resetPassword(email).fold(
                onSuccess = {
                    println("Password reset email sent successfully")
                    onSuccess()
                },
                onFailure = { error ->
                    println("Password reset failed: ${error.message}")
                    _errorMessage.value = error.message
                }
            )
            
            _isLoading.value = false
        }
    }
}