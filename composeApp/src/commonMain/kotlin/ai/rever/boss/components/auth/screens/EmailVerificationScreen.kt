package ai.rever.boss.components.auth.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import BossDarkTextPrimary
import BossDarkTextSecondary
import BossDarkAccent
import BossDarkSurface
import ai.rever.boss.components.auth.forms.*
import ai.rever.boss.components.dialogs.EmailVerificationDialog
import ai.rever.boss.viewmodels.LoginViewModel

@Composable
fun EmailVerificationScreen(
    viewModel: LoginViewModel,
    onVerified: () -> Unit,
    onBack: () -> Unit
) {
    var showVerificationDialog by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        BossLogo()
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Verification Card with narrower max width
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 350.dp),
            shape = RoundedCornerShape(8.dp),
            elevation = 2.dp,
            backgroundColor = BossDarkSurface
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Check Your Email",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = BossDarkTextPrimary
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "We've sent a verification link to your email.\nClick the link to verify your account, or use the button below if the link doesn't work.",
                    fontSize = 14.sp,
                    color = BossDarkTextSecondary,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )
                
                Spacer(modifier = Modifier.height(20.dp))
                
                // Verify Link Button
                Button(
                    onClick = { showVerificationDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(4.dp),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = BossDarkAccent,
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        text = "Verify Email",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Back Button
                TextButton(
                    onClick = onBack
                ) {
                    Text(
                        text = "Back to Sign Up",
                        fontSize = 14.sp,
                        color = BossDarkAccent,
                        textDecoration = TextDecoration.Underline
                    )
                }
            }
        }
    }
    
    // Email Verification Dialog
    if (showVerificationDialog) {
        EmailVerificationDialog(
            onDismiss = { showVerificationDialog = false },
            onVerified = onVerified,
            viewModel = viewModel
        )
    }
}