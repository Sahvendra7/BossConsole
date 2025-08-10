package ai.rever.boss.components.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import ai.rever.boss.services.supabase.AuthService
import ai.rever.boss.viewmodels.LoginViewModel
import BossDarkBackground
import BossDarkSurface
import BossDarkBorder
import BossDarkTextPrimary
import BossDarkTextSecondary
import BossDarkAccent
import BossDarkError

@Composable
fun EmailVerificationDialog(
    onDismiss: () -> Unit,
    onVerified: () -> Unit,
    viewModel: LoginViewModel
) {
    var verificationToken by remember { mutableStateOf("") }
    
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            elevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            backgroundColor = BossDarkSurface
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Email,
                    contentDescription = "Email Verification",
                    modifier = Modifier.size(48.dp),
                    tint = BossDarkAccent
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "Email Verification",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = BossDarkTextPrimary
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Please check your email and enter the verification token or paste the verification URL below:",
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    color = BossDarkTextSecondary
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                OutlinedTextField(
                    value = verificationToken,
                    onValueChange = { 
                        verificationToken = it
                        viewModel.clearError()
                    },
                    label = { Text("Verification Token or URL", color = BossDarkTextSecondary) },
                    placeholder = { Text("Enter token or paste verification URL", color = BossDarkTextSecondary.copy(alpha = 0.6f)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                    maxLines = 3,
                    enabled = !isLoading,
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        textColor = BossDarkTextPrimary,
                        backgroundColor = BossDarkBackground,
                        focusedBorderColor = BossDarkAccent,
                        unfocusedBorderColor = BossDarkBorder,
                        cursorColor = BossDarkAccent
                    )
                )
                
                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage!!,
                        color = BossDarkError,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        enabled = !isLoading,
                        colors = ButtonDefaults.outlinedButtonColors(
                            backgroundColor = BossDarkBackground,
                            contentColor = BossDarkTextPrimary
                        )
                    ) {
                        Text("Cancel", color = BossDarkTextPrimary)
                    }
                    
                    Button(
                        onClick = {
                            // Extract token from URL if full URL is provided
                            val token = if (verificationToken.contains("token=")) {
                                verificationToken.substringAfter("token=").substringBefore("&")
                            } else {
                                verificationToken.trim()
                            }
                            
                            viewModel.verifyEmail(token, onVerified)
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !isLoading && verificationToken.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = BossDarkAccent,
                            contentColor = BossDarkBackground
                        )
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = BossDarkBackground,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Verify")
                        }
                    }
                }
            }
        }
    }
}