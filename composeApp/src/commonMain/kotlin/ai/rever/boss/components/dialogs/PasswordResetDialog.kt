package ai.rever.boss.components.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import ai.rever.boss.services.supabase.AuthService
import ai.rever.boss.utils.PasswordValidator
import kotlinx.coroutines.launch
// BOSS theme imports
import BossDarkBackground
import BossDarkSurface
import BossDarkBorder
import BossDarkTextPrimary
import BossDarkTextSecondary
import BossDarkAccent
import BossDarkError

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PasswordResetDialog(
    accessToken: String,
    onDismiss: () -> Unit,
    onPasswordResetComplete: () -> Unit
) {
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showNewPassword by remember { mutableStateOf(false) }
    var showConfirmPassword by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var passwordValidationErrors by remember { mutableStateOf<List<String>>(emptyList()) }
    
    // Calculate password strength for visual feedback
    val passwordStrength = if (newPassword.isNotBlank()) {
        PasswordValidator.getPasswordStrength(newPassword)
    } else 0
    
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val coroutineScope = rememberCoroutineScope()
    
    fun validatePasswords(): Boolean {
        // Validate password strength first
        val validation = PasswordValidator.validatePassword(newPassword)
        if (!validation.isValid) {
            passwordValidationErrors = validation.errors
            errorMessage = validation.errors.firstOrNull()
            return false
        }
        
        // Check if passwords match
        if (newPassword != confirmPassword) {
            errorMessage = "Passwords do not match"
            passwordValidationErrors = emptyList()
            return false
        }
        
        // All validations passed
        errorMessage = null
        passwordValidationErrors = emptyList()
        return true
    }
    
    fun resetPassword() {
        if (!validatePasswords()) return
        
        isLoading = true
        errorMessage = null
        
        coroutineScope.launch {
            try {
                AuthService.updatePassword(accessToken, newPassword).fold(
                    onSuccess = {
                        println("Password updated successfully")
                        onPasswordResetComplete()
                    },
                    onFailure = { error ->
                        errorMessage = error.message ?: "Failed to reset password"
                    }
                )
            } catch (e: Exception) {
                errorMessage = e.message ?: "Failed to reset password"
            } finally {
                isLoading = false
            }
        }
    }
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            backgroundColor = BossDarkSurface,
            elevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Reset Password",
                    modifier = Modifier.size(48.dp),
                    tint = BossDarkAccent
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "Reset Password",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = BossDarkTextPrimary,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Enter your new password below",
                    fontSize = 14.sp,
                    color = BossDarkTextSecondary,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // New Password Field
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { 
                        newPassword = it
                        errorMessage = null // Clear error when user types
                        passwordValidationErrors = emptyList() // Clear validation errors
                    },
                    label = { 
                        Text("New Password", color = BossDarkTextSecondary) 
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = "Password",
                            tint = BossDarkTextSecondary
                        )
                    },
                    trailingIcon = {
                        IconButton(
                            onClick = { showNewPassword = !showNewPassword }
                        ) {
                            Icon(
                                imageVector = if (showNewPassword) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = if (showNewPassword) "Hide password" else "Show password",
                                tint = BossDarkTextSecondary
                            )
                        }
                    },
                    visualTransformation = if (showNewPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) }
                    ),
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        textColor = BossDarkTextPrimary,
                        backgroundColor = BossDarkBackground,
                        cursorColor = BossDarkAccent,
                        focusedBorderColor = BossDarkAccent,
                        unfocusedBorderColor = BossDarkBorder,
                        focusedLabelColor = BossDarkAccent
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Confirm Password Field
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { 
                        confirmPassword = it
                        errorMessage = null // Clear error when user types
                    },
                    label = { 
                        Text("Confirm Password", color = BossDarkTextSecondary) 
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = "Confirm Password",
                            tint = BossDarkTextSecondary
                        )
                    },
                    trailingIcon = {
                        IconButton(
                            onClick = { showConfirmPassword = !showConfirmPassword }
                        ) {
                            Icon(
                                imageVector = if (showConfirmPassword) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = if (showConfirmPassword) "Hide password" else "Show password",
                                tint = BossDarkTextSecondary
                            )
                        }
                    },
                    visualTransformation = if (showConfirmPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            keyboardController?.hide()
                            resetPassword()
                        }
                    ),
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        textColor = BossDarkTextPrimary,
                        backgroundColor = BossDarkBackground,
                        cursorColor = BossDarkAccent,
                        focusedBorderColor = BossDarkAccent,
                        unfocusedBorderColor = BossDarkBorder,
                        focusedLabelColor = BossDarkAccent
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Password Strength Indicator
                if (newPassword.isNotBlank()) {
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Password Strength:",
                                fontSize = 12.sp,
                                color = BossDarkTextSecondary
                            )
                            Text(
                                text = PasswordValidator.getPasswordStrengthDescription(passwordStrength),
                                fontSize = 12.sp,
                                color = PasswordValidator.getPasswordStrengthColor(passwordStrength),
                                fontWeight = FontWeight.Medium
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        // Strength progress bar
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .background(
                                    BossDarkBorder,
                                    RoundedCornerShape(2.dp)
                                )
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(passwordStrength / 100f)
                                    .fillMaxHeight()
                                    .background(
                                        PasswordValidator.getPasswordStrengthColor(passwordStrength),
                                        RoundedCornerShape(2.dp)
                                    )
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Password Requirements (shown when password is weak or has errors)
                if (passwordValidationErrors.isNotEmpty() && newPassword.isNotBlank()) {
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Password Requirements:",
                            fontSize = 12.sp,
                            color = BossDarkTextSecondary,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        passwordValidationErrors.forEach { error ->
                            Text(
                                text = "• $error",
                                fontSize = 11.sp,
                                color = BossDarkError,
                                modifier = Modifier.padding(start = 8.dp, bottom = 2.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                
                // General Error Message
                errorMessage?.let { error ->
                    Text(
                        text = error,
                        color = BossDarkError,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                
                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Cancel Button
                    OutlinedButton(
                        onClick = onDismiss,
                        enabled = !isLoading,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = BossDarkTextSecondary
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp, 
                            BossDarkBorder
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel")
                    }
                    
                    // Reset Password Button
                    Button(
                        onClick = ::resetPassword,
                        enabled = !isLoading && newPassword.isNotBlank() && confirmPassword.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = BossDarkAccent,
                            contentColor = Color.White,
                            disabledBackgroundColor = BossDarkBorder,
                            disabledContentColor = BossDarkTextSecondary
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(16.dp)
                            )
                        } else {
                            Text("Reset Password")
                        }
                    }
                }
            }
        }
    }
}