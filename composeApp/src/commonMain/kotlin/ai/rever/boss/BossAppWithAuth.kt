package ai.rever.boss

import androidx.compose.runtime.*
import androidx.compose.runtime.key
import ai.rever.boss.components.auth.LoginScreen
import ai.rever.boss.components.auth.Mandatory2FAEnrollmentScreen
import ai.rever.boss.components.dialogs.SupabaseSettingsDialog
import ai.rever.boss.components.dialogs.PasswordResetDialog
import ai.rever.boss.services.supabase.AuthService
import ai.rever.boss.services.supabase.AuthState
import ai.rever.boss.utils.DeepLinkHandler
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
    var needs2FAEnrollment by remember { mutableStateOf(false) }
    var showPasswordResetDialog by remember { mutableStateOf(false) }
    var passwordResetToken by remember { mutableStateOf<String?>(null) }
    
    // Initialize authentication service
    LaunchedEffect(Unit) {
        AuthService.initialize()
    }
    
    // Handle deep links for email verification
    val deepLink by DeepLinkHandler.deepLinkFlow.collectAsState()
    
    LaunchedEffect(deepLink) {
        deepLink?.let { uri ->
            println("Received deep link in app: $uri")
            
            // Check if it's an auth verification link
            if (uri.contains("auth/verify")) {
                val token = DeepLinkHandler.extractVerificationToken(uri)
                val type = DeepLinkHandler.extractVerificationType(uri)
                
                if (token != null) {
                    println("Extracted verification token: $token, type: $type")
                    
                    coroutineScope.launch {
                        when (type) {
                            "recovery" -> {
                                // Handle password reset
                                println("BossAppWithAuth: Processing password reset")
                                
                                AuthService.processPasswordReset(token).fold(
                                    onSuccess = {
                                        println("BossAppWithAuth: Password reset token processed successfully")
                                        
                                        // Show password reset dialog
                                        passwordResetToken = token
                                        showPasswordResetDialog = true
                                    },
                                    onFailure = { error ->
                                        println("BossAppWithAuth: Password reset failed: ${error.message}")
                                    }
                                )
                            }
                            
                            else -> {
                                // Handle email verification (signup confirmation)
                                println("BossAppWithAuth: Processing email verification")
                                
                                AuthService.verifyEmail(token).fold(
                                    onSuccess = {
                                        println("BossAppWithAuth: Email verified successfully via deep link")
                                        
                                        // If user is not authenticated, this means the verification was successful
                                        // The user should now be able to sign in
                                        if (authState is AuthState.NotAuthenticated) {
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
        is AuthState.Loading -> {
            // Show loading screen
            println("BossAppWithAuth: Showing loading screen")
            LoadingScreen()
        }
        
        is AuthState.NotAuthenticated, is AuthState.Error, is AuthState.Requires2FA -> {
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
        
        is AuthState.Authenticated -> {
            // Check if 2FA enrollment is required
            LaunchedEffect(authState) {
                needs2FAEnrollment = AuthService.requires2FAEnrollment()
            }
            
            if (needs2FAEnrollment) {
                // Show mandatory 2FA enrollment screen
                Mandatory2FAEnrollmentScreen(
                    onEnrollmentComplete = {
                        needs2FAEnrollment = false
                    },
                    onLogout = {
                        coroutineScope.launch {
                            AuthService.signOut()
                        }
                    }
                )
            } else {
                // Show main BOSS app
                BossApp()
            }
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
    if (showPasswordResetDialog && passwordResetToken != null) {
        PasswordResetDialog(
            accessToken = passwordResetToken!!, 
            onDismiss = {
                showPasswordResetDialog = false
                passwordResetToken = null
            },
            onPasswordResetComplete = {
                showPasswordResetDialog = false
                passwordResetToken = null
                // TODO: Maybe show success message or navigate appropriately
            }
        )
    }
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