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
import ai.rever.boss.components.auth.screens.PasskeySelectionScreen
import ai.rever.boss.components.auth.screens.PasskeyWaitingScreen
import ai.rever.boss.components.dialogs.CrossDeviceAuthenticationDialog
import ai.rever.boss.services.auth.MagicLinkErrorService
import ai.rever.boss.services.supabase.AuthService
import ai.rever.boss.viewmodels.LoginViewModel
import ai.rever.boss.utils.DeepLinkHandler

enum class AuthScreen {
    LOGIN,
    MAGIC_LINK_WAITING,
    PASSKEY_SELECTION,
    PASSKEY_WAITING,
    PASSKEY_BROWSER
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
    var passkeyEmail by remember { mutableStateOf("") }
    var passkeySelectionEmail by remember { mutableStateOf("") }
    var passkeyBrowserUrl by remember { mutableStateOf("") }
    var passkeyBrowserSessionId by remember { mutableStateOf("") }

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

    // Monitor passkey state for embedded browser trigger
    val passkeyStateFlow = viewModel.passkeyAuthViewModel.passkeyState
    val passkeyState by passkeyStateFlow?.collectAsState() ?: remember { mutableStateOf(null) }
    LaunchedEffect(passkeyState) {
        if (passkeyState is ai.rever.boss.services.passkey.PasskeyState.ShowEmbeddedBrowser) {
            val browserState = passkeyState as ai.rever.boss.services.passkey.PasskeyState.ShowEmbeddedBrowser
            println("AuthScreenContainer: Passkey state changed to ShowEmbeddedBrowser, navigating to browser screen")
            passkeyBrowserUrl = browserState.url
            passkeyBrowserSessionId = browserState.sessionId
            currentScreen = AuthScreen.PASSKEY_BROWSER
        }
    }

    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val showCrossDeviceQR by viewModel.showCrossDeviceQR.collectAsState()
    val crossDeviceQRUrl by viewModel.crossDeviceQRUrl.collectAsState()
    val crossDeviceChallenge by viewModel.crossDeviceChallenge.collectAsState()
    val crossDeviceSessionId by viewModel.crossDeviceSessionId.collectAsState()

    // Observe magic link verification errors from deep link handler
    val magicLinkVerificationError by MagicLinkErrorService.verificationError.collectAsState()
    LaunchedEffect(magicLinkVerificationError) {
        magicLinkVerificationError?.let { error ->
            println("AuthScreenContainer: Received magic link verification error: $error")
            // Set error in viewModel so MagicLinkWaitingScreen can display it
            viewModel.setMagicLinkVerificationError(error)
            // Clear the service error after setting it in ViewModel
            MagicLinkErrorService.clearError()
        }
    }

    // Clear magic link errors when navigating away from magic link screens
    LaunchedEffect(currentScreen) {
        if (currentScreen != AuthScreen.MAGIC_LINK_WAITING &&
            currentScreen != AuthScreen.LOGIN) {
            viewModel.clearMagicLinkError()
            MagicLinkErrorService.clearError()
        }
    }

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
                        },
                        onPasskeyAuthInitiated = { email ->
                            passkeyEmail = email
                            currentScreen = AuthScreen.PASSKEY_WAITING
                            // Initiate passkey authentication when navigating to waiting screen
                            viewModel.authenticateWithEmailAndPasskey(email) {
                                onLoginSuccess()
                            }
                        },
                        onPasskeySelectionRequired = { email ->
                            passkeySelectionEmail = email
                            currentScreen = AuthScreen.PASSKEY_SELECTION
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

                AuthScreen.PASSKEY_SELECTION -> {
                    PasskeySelectionScreen(
                        email = passkeySelectionEmail,
                        viewModel = viewModel,
                        onPasskeySelected = { credentialId ->
                            passkeyEmail = passkeySelectionEmail
                            currentScreen = AuthScreen.PASSKEY_WAITING
                            // Authenticate with selected passkey
                            viewModel.passkeyAuthViewModel.authenticateWithSpecificPasskey(
                                passkeySelectionEmail,
                                credentialId
                            ) {
                                onLoginSuccess()
                            }
                        },
                        onBack = {
                            viewModel.passkeyAuthViewModel.cancelAuthentication()
                            currentScreen = AuthScreen.LOGIN
                        }
                    )
                }

                AuthScreen.PASSKEY_WAITING -> {
                    PasskeyWaitingScreen(
                        email = passkeyEmail,
                        viewModel = viewModel,
                        onBack = {
                            viewModel.passkeyAuthViewModel.cancelAuthentication()
                            currentScreen = AuthScreen.LOGIN
                        },
                        onSuccess = onLoginSuccess
                    )
                }

                AuthScreen.PASSKEY_BROWSER -> {
                    ai.rever.boss.components.auth.screens.PasskeyBrowserScreen(
                        url = passkeyBrowserUrl,
                        sessionId = passkeyBrowserSessionId,
                        onSuccess = {
                            println("AuthScreenContainer: Passkey browser authentication successful")
                            onLoginSuccess()
                        },
                        onBack = {
                            viewModel.passkeyAuthViewModel.cancelAuthentication()
                            currentScreen = AuthScreen.LOGIN
                        }
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
