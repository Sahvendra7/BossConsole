package ai.rever.boss

import androidx.compose.runtime.*
import androidx.compose.runtime.key
import ai.rever.boss.components.auth.LoginScreen
import ai.rever.boss.components.misc.LoadingScreen
import ai.rever.boss.services.supabase.AuthService
import ai.rever.boss.utils.DeepLinkHandler
import ai.rever.boss.services.auth.PasskeySessionEventHandler
import ai.rever.boss.utils.WindowFocusManager
import com.arkivanov.decompose.ComponentContext
import kotlinx.coroutines.launch


/**
 * Main app entry point with authentication
 */
@Composable
fun ComponentContext.BossAppWithAuth() {
    val authState by AuthService.authState.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    
    // Initialize authentication service
    LaunchedEffect(Unit) {
        AuthService.initialize()
    }
    
    // Handle deep links for email verification
    val deepLink by DeepLinkHandler.deepLinkFlow.collectAsState()

    LaunchedEffect(deepLink) {
        // Todo: Why this can not be in DeepLinkHandler itself, may be we can just have
        //  LaunchedEffect here and rest of the code inside DeepLinkHandler
        deepLink?.let { uri ->
            println("Received deep link in app: $uri")

            // Bring window to front
            WindowFocusManager.bringToFront()

            val sessionId = try {
                val regex = Regex("sessionId=([^&]+)")
                regex.find(uri)?.groupValues?.get(1)
            } catch (_: Exception) {
                println("BossAppWithAuth: Failed to extract sessionId from deep link: $uri")
                null
            }

            when {
                uri.contains("passkey/registered") -> {
                    sessionId?.let { id ->
                        println("BossAppWithAuth: Passkey registration completed for session: $id")
                        PasskeySessionEventHandler.handleRegistrationCompleted(id)
                    }
                    DeepLinkHandler.clearDeepLink()
                }
                uri.contains("passkey/authenticated") -> {
                    sessionId?.let { id ->
                        println("BossAppWithAuth: Passkey authentication completed for session: $id")

                        // Trigger the polling check to complete authentication
                        coroutineScope.launch {
                            // The CrossDeviceAuthService is already polling, but we can trigger
                            // an immediate check when we receive the deep link
                            val metadata = PasskeySessionEventHandler.getSessionMetadata(id)
                            metadata?.let { session ->
                                println("BossAppWithAuth: Checking authentication status for session: $id")

                                // Notify that authentication completed
                                PasskeySessionEventHandler.handleAuthenticationCompleted(id)
                            } ?: run {
                                println("BossAppWithAuth: No metadata found for session: $id")
                            }
                        }
                    }
                    DeepLinkHandler.clearDeepLink()
                }
                uri.contains("auth/verify") -> {
                    val token = DeepLinkHandler.extractVerificationToken(uri)
                    val type = DeepLinkHandler.extractVerificationType(uri) ?: "magiclink"
                    token?.let { token ->
                        println("Extracted verification token: $token, type: $type")
                        coroutineScope.launch {
                            // Handle magic link authentication
                            println("BossAppWithAuth: Starting magic link authentication process")

                            AuthService.verifyEmail(token, type).fold(
                                onSuccess = {
                                    println("BossAppWithAuth: Magic link authentication successful")
                                    if (authState is AuthService.AuthState.NotAuthenticated) {
                                        // Trigger a refresh to check if user can now sign in
                                        AuthService.initialize()
                                    }

                                },
                                onFailure = { error ->
                                    println("BossAppWithAuth: Magic link authentication failed: ${error.message}")
                                }
                            )
                        }
                    }

                    DeepLinkHandler.clearDeepLink()
                }
            }
        }
    }
    
    // Debug auth state changes
    LaunchedEffect(authState) {
        println("BossAppWithAuth: AuthState changed to: $authState")
    }
    
    when (authState) {
        is AuthService.AuthState.Loading -> {
            // Show loading screen
            println("BossAppWithAuth: Showing loading screen")
            LoadingScreen()
        }
        
        is AuthService.AuthState.NotAuthenticated,
        is AuthService.AuthState.Error -> {
            // Show login screen (it will handle 2FA verification internally)
            // Use key() to prevent recreation when switching between these states
            key("login_screen") {
                LoginScreen(
                    onLoginSuccess = {
                        // This will be called after successful login (and 2FA if required)
                    }
                )
            }
        }
        
        is AuthService.AuthState.Authenticated -> {
            // Show main BOSS app - all auth methods provide inherent 2FA
            BossApp()
        }
    }
}
