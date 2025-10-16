package ai.rever.boss.viewmodels

import ai.rever.boss.services.supabase.AuthService
import ai.rever.boss.services.supabase.CrossDeviceAuthenticationRequired
import ai.rever.boss.services.supabase.models.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Passkey authentication view model handling WebAuthn flows
 * Responsible for: passkey authentication, registration, cross-device authentication
 */
class PasskeyAuthViewModel {
    private val viewModelScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    // Cross-device authentication state
    private val _showCrossDeviceQR = MutableStateFlow(false)
    val showCrossDeviceQR: StateFlow<Boolean> = _showCrossDeviceQR.asStateFlow()
    
    private val _crossDeviceQRUrl = MutableStateFlow<String?>(null)
    val crossDeviceQRUrl: StateFlow<String?> = _crossDeviceQRUrl.asStateFlow()
    
    private val _crossDeviceChallenge = MutableStateFlow<String?>(null)
    val crossDeviceChallenge: StateFlow<String?> = _crossDeviceChallenge.asStateFlow()
    
    private val _crossDeviceSessionId = MutableStateFlow<String?>(null)
    val crossDeviceSessionId: StateFlow<String?> = _crossDeviceSessionId.asStateFlow()

    /**
     * Authenticate with email and Touch ID - streamlined flow
     */
    fun authenticateWithEmailAndPasskey(email: String, onSuccess: () -> Unit) {
        if (email.isBlank()) {
            _errorMessage.value = "Please enter your email"
            return
        }
        
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            
            // Use email-based passkey authentication 
            // This will trigger Touch ID and identify the user from their credential
            AuthService.authenticateWithPasskey(email = email).fold(
                onSuccess = {
                    println("PasskeyAuthViewModel: Email + Touch ID authentication successful")
                    _isLoading.value = false
                    onSuccess()
                },
                onFailure = { error ->
                    println("PasskeyAuthViewModel: Email + Touch ID authentication failed: ${error.message}")
                    
                    // Check if this is a cross-device authentication requirement
                    if (error is CrossDeviceAuthenticationRequired) {
                        // Handle cross-device authentication flow
                        _showCrossDeviceQR.value = true
                        _crossDeviceQRUrl.value = error.qrCodeUrl
                        _crossDeviceChallenge.value = error.challenge
                        _crossDeviceSessionId.value = error.sessionId
                        _isLoading.value = false
                        return@fold
                    }
                    
                    _errorMessage.value = when {
                        error.message?.contains("not supported") == true -> 
                            "Touch ID authentication is not supported on this device"
                        error.message?.contains("cancelled") == true -> 
                            "Touch ID authentication was cancelled"
                        error.message?.contains("not available") == true -> 
                            "Touch ID not available. Please ensure you have set up Touch ID on your Mac"
                        error.message?.contains("unavailable") == true -> 
                            "Touch ID authentication is not available"
                        else -> error.message ?: "Email + Touch ID authentication failed"
                    }
                    _isLoading.value = false
                }
            )
        }
    }

    /**
     * Dismiss the cross-device QR dialog
     */
    fun dismissCrossDeviceQR() {
        _showCrossDeviceQR.value = false
        _crossDeviceQRUrl.value = null
        _crossDeviceChallenge.value = null
    }
    
}
