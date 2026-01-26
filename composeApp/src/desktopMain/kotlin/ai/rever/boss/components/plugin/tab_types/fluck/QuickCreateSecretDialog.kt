package ai.rever.boss.components.plugin.tab_types.fluck

import ai.rever.boss.services.supabase.models.CreateSecretRequest
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

private val BossDarkBackground = Color(0xFF1E1F22)
private val BossDarkBorder = Color(0xFF3C3F41)
private val BossDarkTextSecondary = Color(0xFF9E9E9E)

/**
 * Quick create secret dialog for browser integration.
 *
 * This dialog is shown when the user wants to save credentials from a website.
 * It provides a simple interface to capture website, username, and password.
 *
 * @param websitePrefill The website URL to prefill
 * @param onConfirm Called when the user confirms the secret creation
 * @param onDismiss Called when the dialog is dismissed
 * @param isLoading Whether a creation operation is in progress
 */
@Composable
fun QuickCreateSecretDialog(
    websitePrefill: String,
    onConfirm: (CreateSecretRequest) -> Unit,
    onDismiss: () -> Unit,
    isLoading: Boolean
) {
    var website by remember { mutableStateOf(websitePrefill) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.width(380.dp),
            color = Color(0xFF2D2D2D),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Save Credentials",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    "Save login credentials for this website",
                    color = BossDarkTextSecondary,
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Website field
                QuickDialogTextField(
                    value = website,
                    onValueChange = { website = it },
                    label = "Website",
                    placeholder = "e.g., github.com"
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Username field
                QuickDialogTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = "Username / Email",
                    placeholder = "Enter username or email"
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Password field
                QuickDialogTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = "Password",
                    placeholder = "Enter password",
                    isPassword = true,
                    showPassword = showPassword,
                    onTogglePassword = { showPassword = !showPassword }
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss, enabled = !isLoading) {
                        Text("Cancel", color = BossDarkTextSecondary)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (website.isNotBlank() && username.isNotBlank() && password.isNotBlank()) {
                                onConfirm(CreateSecretRequest(
                                    website = website,
                                    username = username,
                                    password = password
                                ))
                            }
                        },
                        enabled = !isLoading && website.isNotBlank() && username.isNotBlank() && password.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF4CAF50))
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Save", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickDialogTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String = "",
    isPassword: Boolean = false,
    showPassword: Boolean = false,
    onTogglePassword: (() -> Unit)? = null
) {
    Column {
        Text(
            label,
            color = BossDarkTextSecondary,
            fontSize = 11.sp,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(BossDarkBackground, RoundedCornerShape(4.dp))
                .border(1.dp, BossDarkBorder, RoundedCornerShape(4.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = MaterialTheme.typography.body2.copy(color = Color.White),
                cursorBrush = SolidColor(Color(0xFF4CAF50)),
                visualTransformation = if (isPassword && !showPassword)
                    PasswordVisualTransformation() else VisualTransformation.None,
                decorationBox = { innerTextField ->
                    Box {
                        if (value.isEmpty() && placeholder.isNotEmpty()) {
                            Text(
                                placeholder,
                                color = BossDarkTextSecondary,
                                fontSize = 13.sp
                            )
                        }
                        innerTextField()
                    }
                }
            )

            if (isPassword && onTogglePassword != null) {
                IconButton(
                    onClick = onTogglePassword,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = "Toggle password visibility",
                        tint = BossDarkTextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
