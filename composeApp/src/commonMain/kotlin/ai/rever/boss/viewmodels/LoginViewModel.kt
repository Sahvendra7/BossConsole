package ai.rever.boss.viewmodels

import ai.rever.boss.services.supabase.models.*
import ai.rever.boss.viewmodels.auth.AuthOptions
import ai.rever.boss.viewmodels.auth.AuthOptionsManager
import kotlinx.coroutines.flow.StateFlow

class LoginViewModel {
    // Component ViewModels
    private val coreLoginViewModel = CoreLoginViewModel()
    private val passkeyAuthViewModel = PasskeyAuthViewModel()
    private val twoFactorViewModel = TwoFactorViewModel()
    private val authOptionsManager = AuthOptionsManager()
    
    // Exposed state flows that delegate to appropriate component ViewModels
    val isLoading: StateFlow<Boolean> = coreLoginViewModel.isLoading
    val errorMessage: StateFlow<String?> = coreLoginViewModel.errorMessage

    // Cross-device authentication state from PasskeyAuthViewModel
    val showCrossDeviceQR: StateFlow<Boolean> = passkeyAuthViewModel.showCrossDeviceQR
    val crossDeviceQRUrl: StateFlow<String?> = passkeyAuthViewModel.crossDeviceQRUrl
    val crossDeviceChallenge: StateFlow<String?> = passkeyAuthViewModel.crossDeviceChallenge
    val crossDeviceSessionId: StateFlow<String?> = passkeyAuthViewModel.crossDeviceSessionId

    fun verifyEmail(token: String, onSuccess: () -> Unit) {
        coreLoginViewModel.verifyEmail(token, onSuccess)
    }
    
    fun clearError() {
        coreLoginViewModel.clearError()
    }
    
    fun sendMagicLink(email: String, onSuccess: () -> Unit) {
        coreLoginViewModel.sendMagicLink(email, onSuccess)
    }

    /**
     * Authenticate with email and Touch ID - streamlined flow
     */
    fun authenticateWithEmailAndPasskey(email: String, onSuccess: () -> Unit) {
        passkeyAuthViewModel.authenticateWithEmailAndPasskey(email, onSuccess)
    }

    /**
     * Dismiss the cross-device QR dialog
     */
    fun dismissCrossDeviceQR() {
        passkeyAuthViewModel.dismissCrossDeviceQR()
    }
    
    /**
     * Check if a user exists with the given email and return their authentication options
     */
    fun checkUserExists(email: String, onResult: (AuthOptions) -> Unit) {
        authOptionsManager.checkUserExists(email, onResult)
    }
}

