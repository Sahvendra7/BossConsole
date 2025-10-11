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
 * Two-Factor Authentication view model handling 2FA-specific state and flows
 * Responsible for: biometric 2FA verification, 2FA state management
 *
 * Note: Currently empty as 2FA logic has been integrated directly into authentication flows.
 * This class is kept for potential future 2FA-specific features.
 */
class TwoFactorViewModel {
    private val viewModelScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
}