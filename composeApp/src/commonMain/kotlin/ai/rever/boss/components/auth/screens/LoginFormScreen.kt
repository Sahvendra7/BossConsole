package ai.rever.boss.components.auth.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import BossDarkBorder
import BossDarkTextPrimary
import BossDarkTextSecondary
import BossDarkAccent
import ai.rever.boss.components.auth.forms.*
import ai.rever.boss.viewmodels.LoginViewModel
import ai.rever.boss.viewmodels.auth.AuthOptions

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun LoginFormScreen(
    viewModel: LoginViewModel,
    onLoginSuccess: () -> Unit,
    isLoading: Boolean,
    errorMessage: String?,
    onMagicLinkSent: (String) -> Unit = {}
) {
    var email by remember { mutableStateOf("") }
    var authOptions by remember { mutableStateOf<AuthOptions?>(null) }
    var checkingUserExists by remember { mutableStateOf(false) }
    var showAuthOptions by remember { mutableStateOf(false) }

    LocalSoftwareKeyboardController.current
    
    // Reset auth options when email changes
    LaunchedEffect(email) {
        if (showAuthOptions && email.isNotBlank() && email.contains("@")) {
            // Email changed, reset auth options to force re-check
            authOptions = null
            showAuthOptions = false
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        BossLogo()
        
        Spacer(modifier = Modifier.height(24.dp))
        
        AuthCard {
            AuthCardTitle("Sign In")
            
            // Email Field
            EmailField(
                value = email,
                onValueChange = { 
                    email = it
                    // Reset to email step when email changes
                    if (showAuthOptions) {
                        showAuthOptions = false
                        authOptions = null
                    }
                },
                enabled = !isLoading,
                keyboardActions = KeyboardActions(
                    onGo = { 
                        if (email.isNotBlank() && email.contains("@") && !showAuthOptions) {
                            checkingUserExists = true
                            viewModel.checkUserExists(email) { options ->
                                authOptions = options
                                showAuthOptions = true
                                checkingUserExists = false
                            }
                        }
                    }
                )
            )
            
            // Error Message
            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(8.dp))
                ErrorMessage(errorMessage)
            }
            
            Spacer(modifier = Modifier.height(16.dp))

            // Continue Button (when email not yet validated)
            val isEmailValidAndReady = !showAuthOptions && !checkingUserExists &&
                email.isNotBlank() && email.contains("@")
            if (isEmailValidAndReady) {
                PrimaryActionButton(
                    text = "Continue",
                    onClick = {
                        if (email.isNotBlank() && email.contains("@")) {
                            checkingUserExists = true
                            viewModel.checkUserExists(email) { options ->
                                authOptions = options
                                showAuthOptions = true
                                checkingUserExists = false
                            }
                        }
                    },
                    enabled = !isLoading && !checkingUserExists && email.isNotBlank() && email.contains("@"),
                    isLoading = checkingUserExists
                )
            }
            
            // Authentication Options (after email validation)
            if (showAuthOptions) {
                when (val options = authOptions) {
                    null -> {
                        // Loading state - show checking indicator
                        LoadingIndicator()
                    }
                    is AuthOptions.Invalid -> {
                        // Show error message
                        Text(
                            text = options.message,
                            color = BossDarkTextPrimary,
                            fontSize = 12.sp,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    
                    is AuthOptions.WithPasskey -> {
                        // User has passkeys - show simple passkey option
                        Button(
                            onClick = {
                                viewModel.authenticateWithEmailAndPasskey(email) {
                                    onLoginSuccess()
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            enabled = !isLoading,
                            shape = RoundedCornerShape(4.dp),
                            colors = ButtonDefaults.buttonColors(
                                backgroundColor = BossDarkAccent,
                                contentColor = Color.White
                            )
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Fingerprint,
                                    contentDescription = "Passkey",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Sign in with passkey",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        OutlinedButton(
                            onClick = { 
                                // Show magic link authentication as alternative
                                authOptions = AuthOptions.MagicLinkOnly(email)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            enabled = !isLoading,
                            shape = RoundedCornerShape(4.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = BossDarkTextPrimary
                            ),
                            border = BorderStroke(1.dp, BossDarkBorder)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Password",
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Send magic link",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    
                    is AuthOptions.MagicLinkOnly -> {
                        // User exists but no passkeys - show magic link authentication only
                        Text(
                            "We'll send you a secure magic link to sign in - no password needed!",
                            color = BossDarkTextSecondary,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // Send Magic Link Button
                        PrimaryActionButton(
                            text = "Send Magic Link",
                            onClick = {
                                viewModel.sendMagicLink(email) {
                                    // Magic link sent successfully - navigate to waiting screen
                                    onMagicLinkSent(email)
                                }
                            },
                            enabled = !isLoading,
                            isLoading = isLoading
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
