package ai.rever.boss.components.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import BossDarkBackground
import BossTheme
import ai.rever.boss.components.auth.screens.LoginFormScreen
import ai.rever.boss.components.auth.screens.MagicLinkWaitingScreen
import ai.rever.boss.components.dialogs.CrossDeviceAuthenticationDialog
import ai.rever.boss.services.supabase.AuthService
import ai.rever.boss.viewmodels.LoginViewModel
import ai.rever.boss.utils.DeepLinkHandler

enum class AuthScreen {
    LOGIN,
    MAGIC_LINK_WAITING
}

/**
 * Main container for passwordless authentication.
 * Manages cross-device authentication and magic link flows.
 */
@Composable
fun AuthScreenContainer(
    onLoginSuccess: () -> Unit
) {
    // Use a stable key to prevent ViewModel recreation during AuthState changes
    val viewModel = remember("login_viewmodel") { LoginViewModel() }
    var currentScreen by remember { mutableStateOf(AuthScreen.LOGIN) }
    var magicLinkEmail by remember { mutableStateOf("") }
    
    println("AuthScreenContainer: Recomposed - viewModel: ${viewModel.hashCode()}")
    
    // Watch AuthService state directly to handle 2FA
    val authState by AuthService.authState.collectAsState()
    
    // React to AuthState changes (only for certain transitions)
    LaunchedEffect(authState) {
        println("AuthScreenContainer: AuthState changed to: $authState, currentScreen: $currentScreen")
        when (authState) {
            is AuthService.AuthState.Authenticated -> {
                println("AuthScreenContainer: User authenticated - will be handled by parent component")
                onLoginSuccess()
            }
            else -> {
                // Keep on current screen for all other states
            }
        }
    }
    
    // Handle deep links while on magic link waiting screen
    val deepLink by DeepLinkHandler.deepLinkFlow.collectAsState()
    LaunchedEffect(deepLink, currentScreen) {
        val link = deepLink
        if (currentScreen == AuthScreen.MAGIC_LINK_WAITING && link != null && link.contains("auth/verify")) {
            println("AuthScreenContainer: Received deep link while on waiting screen: $link")
            // Deep link will be processed by BossAppWithAuth, just clear it here to avoid reprocessing
            DeepLinkHandler.clearDeepLink()
        }
    }
    
    // Debug current screen changes
    LaunchedEffect(currentScreen) {
        println("AuthScreenContainer: currentScreen changed to: $currentScreen")
    }
    
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val showCrossDeviceQR by viewModel.showCrossDeviceQR.collectAsState()
    val crossDeviceQRUrl by viewModel.crossDeviceQRUrl.collectAsState()
    val crossDeviceChallenge by viewModel.crossDeviceChallenge.collectAsState()
    val crossDeviceSessionId by viewModel.crossDeviceSessionId.collectAsState()
    
    BossTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BossDarkBackground)
        ) {
            when (currentScreen) {
                AuthScreen.LOGIN -> {
                    LoginFormScreen(
                        viewModel = viewModel,
                        onLoginSuccess = onLoginSuccess,
                        isLoading = isLoading,
                        errorMessage = errorMessage,
                        onMagicLinkSent = { email ->
                            magicLinkEmail = email
                            currentScreen = AuthScreen.MAGIC_LINK_WAITING
                        }
                    )
                }
                
                AuthScreen.MAGIC_LINK_WAITING -> {
                    MagicLinkWaitingScreen(
                        email = magicLinkEmail,
                        viewModel = viewModel,
                        onBack = { 
                            currentScreen = AuthScreen.LOGIN 
                        },
                        onSuccess = onLoginSuccess,
                        isLoading = isLoading,
                        errorMessage = errorMessage
                    )
                }
            }
        }
        
        // Cross-Device Authentication QR Dialog
        if (showCrossDeviceQR && crossDeviceQRUrl != null) {
            CrossDeviceAuthenticationDialog(
                qrCodeUrl = crossDeviceQRUrl,
                challenge = crossDeviceChallenge,
                sessionId = crossDeviceSessionId,
                onDismiss = { viewModel.dismissCrossDeviceQR() },
                onSuccess = { 
                    // Authentication successful
                    println("Cross-device authentication successful")
                }
            )
        }
    }
}
