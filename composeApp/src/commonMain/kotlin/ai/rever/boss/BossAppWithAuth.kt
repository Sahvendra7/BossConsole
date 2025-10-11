package ai.rever.boss

import androidx.compose.runtime.*
import androidx.compose.runtime.key
import ai.rever.boss.components.auth.LoginScreen
import ai.rever.boss.components.dialogs.SupabaseSettingsDialog
import ai.rever.boss.services.supabase.AuthService
import ai.rever.boss.utils.DeepLinkHandler
import ai.rever.boss.services.auth.PasskeySessionEventHandler
import ai.rever.boss.services.auth.CrossDeviceAuthService
import ai.rever.boss.utils.WindowFocusManager
import com.arkivanov.decompose.ComponentContext
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.Text
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import BossDarkBackground
import BossDarkAccent
import BossDarkTextSecondary

/**
 * Main app entry point with authentication
 */
@Composable
fun ComponentContext.BossAppWithAuth() {
    var showSupabaseSettings by remember { mutableStateOf(false) }
    val authState by AuthService.authState.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    
    // Initialize authentication service
    LaunchedEffect(Unit) {
        AuthService.initialize()
    }
    
    // Handle deep links for email verification
    val deepLink by DeepLinkHandler.deepLinkFlow.collectAsState()
    
    LaunchedEffect(deepLink) {
        deepLink?.let { uri ->
            println("Received deep link in app: $uri")

            // Check if it's a passkey callback
            if (uri.contains("passkey/registered") || uri.contains("passkey/authenticated")) {
                println("BossAppWithAuth: Received passkey callback: $uri")

                // Bring window to front
                WindowFocusManager.bringToFront()

                // Extract sessionId from URL
                val sessionId = try {
                    val regex = Regex("sessionId=([^&]+)")
                    regex.find(uri)?.groupValues?.get(1)
                } catch (e: Exception) {
                    null
                }

                if (sessionId != null) {
                    when {
                        uri.contains("passkey/registered") -> {
                            println("BossAppWithAuth: Passkey registration completed for session: $sessionId")
                            PasskeySessionEventHandler.handleRegistrationCompleted(sessionId)
                        }
                        uri.contains("passkey/authenticated") -> {
                            println("BossAppWithAuth: Passkey authentication completed for session: $sessionId")

                            // Trigger the polling check to complete authentication
                            coroutineScope.launch {
                                // The CrossDeviceAuthService is already polling, but we can trigger
                                // an immediate check when we receive the deep link
                                val metadata = PasskeySessionEventHandler.getSessionMetadata(sessionId)
                                if (metadata != null) {
                                    println("BossAppWithAuth: Checking authentication status for session: $sessionId")

                                    // Notify that authentication completed
                                    PasskeySessionEventHandler.handleAuthenticationCompleted(sessionId)
                                } else {
                                    println("BossAppWithAuth: No metadata found for session: $sessionId")
                                }
                            }
                        }
                    }
                } else {
                    println("BossAppWithAuth: Failed to extract sessionId from deep link: $uri")
                }

                // Clear the deep link after processing
                DeepLinkHandler.clearDeepLink()
            }
            // Check if it's an auth verification link
            else if (uri.contains("auth/verify")) {
                val token = DeepLinkHandler.extractVerificationToken(uri)
                val type = DeepLinkHandler.extractVerificationType(uri)

                if (token != null) {
                    println("Extracted verification token: $token, type: $type")

                    coroutineScope.launch {
                        when (type) {
                            "magiclink" -> {
                                // Handle magic link authentication
                                println("BossAppWithAuth: Starting magic link authentication process")

                                AuthService.verifyEmail(token, type).fold(
                                    onSuccess = {
                                        println("BossAppWithAuth: Magic link authentication successful")
                                    },
                                    onFailure = { error ->
                                        println("BossAppWithAuth: Magic link authentication failed: ${error.message}")
                                    }
                                )
                            }

                            else -> {
                                // Handle any other verification types (signup, recovery, etc.)
                                println("BossAppWithAuth: Processing authentication token with type: $type")

                                AuthService.verifyEmail(token, type ?: "signup").fold(
                                    onSuccess = {
                                        println("BossAppWithAuth: Email verified successfully via deep link")

                                        // If user is not authenticated, this means the verification was successful
                                        // The user should now be able to sign in
                                        if (authState is AuthService.AuthState.NotAuthenticated) {
                                            // Trigger a refresh to check if user can now sign in
                                            AuthService.initialize()
                                        }
                                    },
                                    onFailure = { error ->
                                        println("BossAppWithAuth: Email verification failed: ${error.message}")
                                    }
                                )

                                // Also trigger event for LoginViewModel if it exists
                                println("BossAppWithAuth: Also triggering event for LoginViewModel (if exists)")
                                AuthService.triggerEmailVerificationEvent(token)
                            }
                        }
                    }

                    // Clear the deep link after processing
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
        
        is AuthService.AuthState.NotAuthenticated, is AuthService.AuthState.Error, is AuthService.AuthState.Requires2FA -> {
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
    
    // Supabase settings dialog
    if (showSupabaseSettings) {
        SupabaseSettingsDialog(
            onDismiss = { showSupabaseSettings = false },
            onConfigured = {
                coroutineScope.launch {
                    AuthService.initialize()
                }
            }
        )
    }
    
    // Password reset dialog
}

@Composable
private fun LoadingScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BossDarkBackground),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // BOSS Logo
            Image(
                painter = painterResource("boss_icon.png"),
                contentDescription = "BOSS Logo",
                modifier = Modifier.size(80.dp)
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Loading indicator
            CircularProgressIndicator(
                color = BossDarkAccent,
                modifier = Modifier.size(40.dp)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Loading text
            Text(
                text = "Loading BOSS...",
                color = BossDarkTextSecondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}