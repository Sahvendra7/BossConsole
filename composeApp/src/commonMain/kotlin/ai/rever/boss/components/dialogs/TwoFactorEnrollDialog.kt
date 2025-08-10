package ai.rever.boss.components.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import ai.rever.boss.services.supabase.AuthService
import ai.rever.boss.services.supabase.TwoFactorEnrollment
import ai.rever.boss.viewmodels.LoginViewModel
import ai.rever.boss.utils.QRCodeProvider
import BossDarkBackground
import BossDarkSurface
import BossDarkAccent
import BossDarkTextPrimary
import BossDarkTextSecondary
import BossDarkBorder
import BossDarkError

@Composable
fun TwoFactorEnrollDialog(
    onDismiss: () -> Unit,
    onEnrolled: () -> Unit,
    viewModel: LoginViewModel,
    isMandatory: Boolean = false
) {
    var enrollmentData by remember { mutableStateOf<TwoFactorEnrollment?>(null) }
    var verificationCode by remember { mutableStateOf("") }
    var currentStep by remember { mutableStateOf(1) } // 1: Setup, 2: Verify
    
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val clipboardManager = LocalClipboardManager.current
    
    // Start enrollment process
    LaunchedEffect(Unit) {
        AuthService.enroll2FA().fold(
            onSuccess = { enrollment ->
                enrollmentData = enrollment
            },
            onFailure = { error ->
                // Handle enrollment error through viewModel
            }
        )
    }
    
    Dialog(onDismissRequest = { if (!isMandatory) onDismiss() }) {
        Card(
            shape = RoundedCornerShape(12.dp),
            elevation = 4.dp,
            backgroundColor = BossDarkSurface,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 500.dp)
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header with icon and title - matching settings style
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Security,
                        contentDescription = "Two-Factor Authentication",
                        modifier = Modifier.size(24.dp),
                        tint = BossDarkAccent
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Two-Factor Authentication Setup",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = BossDarkTextPrimary
                        )
                        Text(
                            text = "Step ${currentStep} of 2",
                            fontSize = 13.sp,
                            color = BossDarkTextSecondary.copy(alpha = 0.7f)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                when (currentStep) {
                    1 -> {
                        // Setup Step - matching settings card style
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            backgroundColor = BossDarkBackground,
                            shape = RoundedCornerShape(8.dp),
                            elevation = 0.dp,
                            border = BorderStroke(1.dp, BossDarkBorder.copy(alpha = 0.3f))
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Scan QR Code",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = BossDarkTextPrimary,
                                    modifier = Modifier.padding(bottom = 16.dp)
                                )
                                
                                // QR Code
                                enrollmentData?.let { data ->
                                    val qrImage = QRCodeProvider.generateQRCode(data.uri)
                                    qrImage?.let {
                                        Image(
                                            bitmap = it,
                                            contentDescription = "QR Code",
                                            modifier = Modifier.size(200.dp)
                                        )
                                    }
                                } ?: Box(
                                    modifier = Modifier
                                        .size(200.dp)
                                        .background(BossDarkBorder.copy(alpha = 0.1f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        color = BossDarkAccent,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                                
                                Spacer(modifier = Modifier.height(20.dp))
                                
                                // Manual entry option
                                Text(
                                    text = "Or enter this code manually:",
                                    fontSize = 13.sp,
                                    color = BossDarkTextSecondary,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                                
                                // Secret key display with copy button
                                enrollmentData?.let { data ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        backgroundColor = BossDarkBackground,
                                        shape = RoundedCornerShape(6.dp),
                                        elevation = 0.dp,
                                        border = BorderStroke(1.dp, BossDarkBorder.copy(alpha = 0.2f))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = data.secret,
                                                fontSize = 12.sp,
                                                fontFamily = FontFamily.Monospace,
                                                color = BossDarkAccent,
                                                modifier = Modifier.weight(1f)
                                            )
                                            IconButton(
                                                onClick = {
                                                    clipboardManager.setText(AnnotatedString(data.secret))
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    Icons.Outlined.ContentCopy,
                                                    contentDescription = "Copy",
                                                    tint = BossDarkTextSecondary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // Instructions - matching settings tips style
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            backgroundColor = BossDarkAccent.copy(alpha = 0.05f),
                            shape = RoundedCornerShape(8.dp),
                            elevation = 0.dp,
                            border = BorderStroke(1.dp, BossDarkAccent.copy(alpha = 0.2f))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                InstructionItem(
                                    icon = Icons.Outlined.PhoneAndroid,
                                    text = "Open your authenticator app"
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                InstructionItem(
                                    icon = Icons.Outlined.QrCodeScanner,
                                    text = "Scan the QR code or enter the secret manually"
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                InstructionItem(
                                    icon = Icons.Outlined.Pin,
                                    text = "Get the 6-digit code from your app"
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        // Continue button
                        Button(
                            onClick = { currentStep = 2 },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp),
                            enabled = enrollmentData != null,
                            shape = RoundedCornerShape(6.dp),
                            colors = ButtonDefaults.buttonColors(
                                backgroundColor = BossDarkAccent,
                                contentColor = Color.White
                            ),
                            elevation = ButtonDefaults.elevation(0.dp)
                        ) {
                            Text(
                                text = "I've Added the Account",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    
                    2 -> {
                        // Verification Step
                        Text(
                            text = "Enter Verification Code",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = BossDarkTextPrimary,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        
                        Text(
                            text = "Enter the 6-digit code from your authenticator app",
                            fontSize = 13.sp,
                            color = BossDarkTextSecondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(bottom = 20.dp)
                        )
                        
                        // Verification code input
                        OutlinedTextField(
                            value = verificationCode,
                            onValueChange = { 
                                if (it.length <= 6 && it.all { char -> char.isDigit() }) {
                                    verificationCode = it
                                }
                            },
                            placeholder = { 
                                Text(
                                    "000000",
                                    color = BossDarkTextSecondary.copy(alpha = 0.5f)
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(
                                textAlign = TextAlign.Center,
                                fontSize = 24.sp,
                                letterSpacing = 8.sp,
                                fontFamily = FontFamily.Monospace
                            ),
                            colors = TextFieldDefaults.outlinedTextFieldColors(
                                textColor = BossDarkTextPrimary,
                                backgroundColor = BossDarkBackground,
                                focusedBorderColor = BossDarkAccent,
                                unfocusedBorderColor = BossDarkBorder,
                                cursorColor = BossDarkAccent
                            ),
                            shape = RoundedCornerShape(8.dp)
                        )
                        
                        // Error message
                        errorMessage?.let { error ->
                            Spacer(modifier = Modifier.height(12.dp))
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                backgroundColor = BossDarkError.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(6.dp),
                                elevation = 0.dp
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Outlined.ErrorOutline,
                                        contentDescription = "Error",
                                        modifier = Modifier.size(16.dp),
                                        tint = BossDarkError
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = error,
                                        fontSize = 13.sp,
                                        color = BossDarkError
                                    )
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        // Action buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Back button
                            OutlinedButton(
                                onClick = { currentStep = 1 },
                                modifier = Modifier.weight(1f).height(44.dp),
                                enabled = !isLoading,
                                shape = RoundedCornerShape(6.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = BossDarkTextSecondary
                                ),
                                border = BorderStroke(1.dp, BossDarkBorder)
                            ) {
                                Icon(
                                    Icons.Outlined.ArrowBack,
                                    contentDescription = "Back",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Back", fontSize = 14.sp)
                            }
                            
                            // Verify button
                            Button(
                                onClick = {
                                    enrollmentData?.let { data ->
                                        viewModel.verify2FAEnrollment(
                                            data.id,
                                            verificationCode,
                                            onEnrolled
                                        )
                                    }
                                },
                                modifier = Modifier.weight(1f).height(44.dp),
                                enabled = verificationCode.length == 6 && !isLoading,
                                shape = RoundedCornerShape(6.dp),
                                colors = ButtonDefaults.buttonColors(
                                    backgroundColor = BossDarkAccent,
                                    contentColor = Color.White
                                ),
                                elevation = ButtonDefaults.elevation(0.dp)
                            ) {
                                if (isLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        color = Color.White,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        Icons.Outlined.CheckCircle,
                                        contentDescription = "Verify",
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Verify", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Cancel button (hidden if mandatory)
                if (!isMandatory) {
                    TextButton(
                        onClick = onDismiss,
                        enabled = !isLoading
                    ) {
                        Text(
                            "Cancel",
                            fontSize = 14.sp,
                            color = BossDarkTextSecondary
                        )
                    }
                }
            }
        }
    }
}

/**
 * Overloaded version for mandatory 2FA enrollment without viewModel
 */
@Composable
fun TwoFactorEnrollDialog(
    onDismiss: () -> Unit,
    onEnrollmentComplete: ((String) -> Unit),
    isMandatory: Boolean = false
) {
    val viewModel = remember { LoginViewModel() }
    TwoFactorEnrollDialog(
        onDismiss = onDismiss,
        onEnrolled = { onEnrollmentComplete("") },
        viewModel = viewModel,
        isMandatory = isMandatory
    )
}

@Composable
private fun InstructionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = BossDarkAccent
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            fontSize = 13.sp,
            color = BossDarkTextSecondary
        )
    }
}