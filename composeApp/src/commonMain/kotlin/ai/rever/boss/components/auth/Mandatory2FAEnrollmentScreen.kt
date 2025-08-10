package ai.rever.boss.components.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.rever.boss.components.dialogs.TwoFactorEnrollDialog
import ai.rever.boss.services.supabase.AuthService
import kotlinx.coroutines.launch
import BossDarkBackground
import BossDarkSurface
import BossDarkAccent
import BossDarkTextPrimary
import BossDarkTextSecondary
import BossDarkBorder
import BossDarkError

/**
 * Mandatory 2FA enrollment screen that blocks access to the app until 2FA is set up
 * Styled to match the settings screen for consistency
 */
@Composable
fun Mandatory2FAEnrollmentScreen(
    onEnrollmentComplete: () -> Unit,
    onLogout: () -> Unit
) {
    var showEnrollDialog by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BossDarkBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(40.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Section - matching settings style
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Icon(
                        Icons.Default.Security,
                        contentDescription = "Security",
                        modifier = Modifier.size(28.dp),
                        tint = BossDarkAccent
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Two-Factor Authentication Required",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = BossDarkTextPrimary
                    )
                }
                
                Text(
                    text = "Secure your BOSS workspace with an additional layer of protection",
                    fontSize = 14.sp,
                    color = BossDarkTextSecondary.copy(alpha = 0.8f),
                    modifier = Modifier.padding(start = 40.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Required Badge - matching settings warning style
            Card(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = BossDarkError.copy(alpha = 0.1f),
                shape = RoundedCornerShape(8.dp),
                elevation = 0.dp,
                border = BorderStroke(1.dp, BossDarkError.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = "Warning",
                        modifier = Modifier.size(20.dp),
                        tint = BossDarkError
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Action Required",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = BossDarkError
                        )
                        Text(
                            text = "2FA must be enabled to access your workspace",
                            fontSize = 13.sp,
                            color = BossDarkError.copy(alpha = 0.8f)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Setup Instructions - matching settings tips style
            SettingSection(
                title = "Setup Instructions",
                description = "What you'll need"
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = BossDarkAccent.copy(alpha = 0.05f),
                    shape = RoundedCornerShape(8.dp),
                    elevation = 0.dp,
                    border = BorderStroke(1.dp, BossDarkAccent.copy(alpha = 0.2f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        SetupStep(
                            number = "1",
                            text = "Install an authenticator app (Google Authenticator, Authy, or 1Password)"
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        SetupStep(
                            number = "2",
                            text = "Scan the QR code or enter the secret key manually"
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        SetupStep(
                            number = "3",
                            text = "Enter the 6-digit verification code to confirm setup"
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        SetupStep(
                            number = "4",
                            text = "Save your backup codes in a secure location"
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Action Buttons - matching settings button style
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Sign Out Button - secondary style
                OutlinedButton(
                    onClick = onLogout,
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = BossDarkTextSecondary
                    ),
                    border = BorderStroke(1.dp, BossDarkBorder)
                ) {
                    Icon(
                        Icons.Outlined.Logout,
                        contentDescription = "Sign Out",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Sign Out", fontSize = 14.sp)
                }
                
                // Setup 2FA Button - primary style
                Button(
                    onClick = { showEnrollDialog = true },
                    modifier = Modifier.weight(2f).height(44.dp),
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = BossDarkAccent,
                        contentColor = Color.White
                    ),
                    elevation = ButtonDefaults.elevation(0.dp)
                ) {
                    Icon(
                        Icons.Outlined.Security,
                        contentDescription = "Setup",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Set Up Two-Factor Authentication",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
    
    // 2FA Enrollment Dialog
    if (showEnrollDialog) {
        TwoFactorEnrollDialog(
            onDismiss = { 
                // Cannot dismiss - 2FA is mandatory
            },
            onEnrollmentComplete = { factorId ->
                coroutineScope.launch {
                    // Mark 2FA as enrolled
                    showEnrollDialog = false
                    onEnrollmentComplete()
                }
            },
            isMandatory = true
        )
    }
}

@Composable
private fun SettingSection(
    title: String,
    description: String,
    content: @Composable () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = BossDarkTextPrimary
        )
        Text(
            text = description,
            fontSize = 13.sp,
            color = BossDarkTextSecondary.copy(alpha = 0.7f),
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
        )
        content()
    }
}

@Composable
private fun SetupStep(
    number: String,
    text: String
) {
    Row(
        verticalAlignment = Alignment.Top
    ) {
        Card(
            modifier = Modifier.size(24.dp),
            backgroundColor = BossDarkAccent,
            shape = RoundedCornerShape(12.dp),
            elevation = 0.dp
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Text(
                    text = number,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            fontSize = 13.sp,
            color = BossDarkTextSecondary,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}