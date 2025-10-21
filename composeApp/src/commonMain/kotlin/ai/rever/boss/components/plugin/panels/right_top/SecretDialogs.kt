package ai.rever.boss.components.plugin.panels.right_top

import ai.rever.boss.services.supabase.models.CreateSecretRequest
import ai.rever.boss.services.supabase.models.SecretEntry
import ai.rever.boss.services.supabase.models.UpdateSecretRequest
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import compose.icons.FeatherIcons
import compose.icons.feathericons.ChevronDown
import compose.icons.feathericons.ChevronUp
import compose.icons.feathericons.Eye
import compose.icons.feathericons.EyeOff

/**
 * Create secret dialog
 */
@Composable
fun CreateSecretDialog(
    onConfirm: (CreateSecretRequest) -> Unit,
    onDismiss: () -> Unit,
    isLoading: Boolean
) {
    var website by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf("") }
    var expirationDate by remember { mutableStateOf("") }
    var twofaEnabled by remember { mutableStateOf(false) }
    var twofaType by remember { mutableStateOf("") }
    var recoveryCodes by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var show2FASection by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = { if (!isLoading) onDismiss() }) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = Color(0xFF3C3F41),
            modifier = Modifier.width(500.dp).heightIn(max = 600.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Title
                Text(
                    "Add New Secret",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )

                // Website
                OutlinedTextField(
                    value = website,
                    onValueChange = { website = it },
                    label = { Text("Website", color = Color.Gray) },
                    placeholder = { Text("example.com", color = Color.Gray) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading,
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        textColor = Color.White,
                        cursorColor = Color.White,
                        focusedBorderColor = Color(0xFF4CAF50),
                        unfocusedBorderColor = Color.Gray
                    ),
                    singleLine = true
                )

                // Username
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username", color = Color.Gray) },
                    placeholder = { Text("user@example.com", color = Color.Gray) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading,
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        textColor = Color.White,
                        cursorColor = Color.White,
                        focusedBorderColor = Color(0xFF4CAF50),
                        unfocusedBorderColor = Color.Gray
                    ),
                    singleLine = true
                )

                // Password
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password", color = Color.Gray) },
                    placeholder = { Text("••••••••", color = Color.Gray) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading,
                    visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                            Icon(
                                if (isPasswordVisible) FeatherIcons.EyeOff else FeatherIcons.Eye,
                                contentDescription = if (isPasswordVisible) "Hide password" else "Show password",
                                tint = Color.Gray
                            )
                        }
                    },
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        textColor = Color.White,
                        cursorColor = Color.White,
                        focusedBorderColor = Color(0xFF4CAF50),
                        unfocusedBorderColor = Color.Gray
                    ),
                    singleLine = true
                )

                // Tags
                OutlinedTextField(
                    value = tags,
                    onValueChange = { tags = it },
                    label = { Text("Tags", color = Color.Gray) },
                    placeholder = { Text("work, personal (comma-separated)", color = Color.Gray) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading,
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        textColor = Color.White,
                        cursorColor = Color.White,
                        focusedBorderColor = Color(0xFF4CAF50),
                        unfocusedBorderColor = Color.Gray
                    ),
                    singleLine = true
                )

                // Expiration Date
                OutlinedTextField(
                    value = expirationDate,
                    onValueChange = { expirationDate = it },
                    label = { Text("Expiration Date (Optional)", color = Color.Gray) },
                    placeholder = { Text("YYYY-MM-DD", color = Color.Gray) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading,
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        textColor = Color.White,
                        cursorColor = Color.White,
                        focusedBorderColor = Color(0xFF4CAF50),
                        unfocusedBorderColor = Color.Gray
                    ),
                    singleLine = true
                )

                // Notes
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (Optional)", color = Color.Gray) },
                    placeholder = { Text("Additional notes...", color = Color.Gray) },
                    modifier = Modifier.fillMaxWidth().height(100.dp),
                    enabled = !isLoading,
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        textColor = Color.White,
                        cursorColor = Color.White,
                        focusedBorderColor = Color(0xFF4CAF50),
                        unfocusedBorderColor = Color.Gray
                    ),
                    maxLines = 4
                )

                // 2FA Section Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "2FA Information",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    IconButton(onClick = { show2FASection = !show2FASection }) {
                        Icon(
                            if (show2FASection) FeatherIcons.ChevronUp else FeatherIcons.ChevronDown,
                            contentDescription = if (show2FASection) "Hide" else "Show",
                            tint = Color.Gray
                        )
                    }
                }

                // 2FA Fields
                if (show2FASection) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF2B2D30), RoundedCornerShape(4.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // 2FA Enabled Checkbox
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Checkbox(
                                checked = twofaEnabled,
                                onCheckedChange = { twofaEnabled = it },
                                enabled = !isLoading,
                                colors = CheckboxDefaults.colors(
                                    checkedColor = Color(0xFF4CAF50),
                                    uncheckedColor = Color.Gray
                                )
                            )
                            Text("2FA Enabled", color = Color.White, fontSize = 14.sp)
                        }

                        if (twofaEnabled) {
                            // 2FA Type Dropdown
                            OutlinedTextField(
                                value = twofaType,
                                onValueChange = { twofaType = it },
                                label = { Text("2FA Type", color = Color.Gray) },
                                placeholder = { Text("app, sms, email, hardware", color = Color.Gray) },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !isLoading,
                                colors = TextFieldDefaults.outlinedTextFieldColors(
                                    textColor = Color.White,
                                    cursorColor = Color.White,
                                    focusedBorderColor = Color(0xFF4CAF50),
                                    unfocusedBorderColor = Color.Gray
                                ),
                                singleLine = true
                            )

                            // Recovery Codes
                            OutlinedTextField(
                                value = recoveryCodes,
                                onValueChange = { recoveryCodes = it },
                                label = { Text("Recovery Codes (Optional)", color = Color.Gray) },
                                placeholder = { Text("One per line", color = Color.Gray) },
                                modifier = Modifier.fillMaxWidth().height(100.dp),
                                enabled = !isLoading,
                                colors = TextFieldDefaults.outlinedTextFieldColors(
                                    textColor = Color.White,
                                    cursorColor = Color.White,
                                    focusedBorderColor = Color(0xFF4CAF50),
                                    unfocusedBorderColor = Color.Gray
                                ),
                                maxLines = 5
                            )
                        }
                    }
                }

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        enabled = !isLoading
                    ) {
                        Text("Cancel", color = Color.Gray)
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val tagsList = tags.split(",").map { it.trim() }.filter { it.isNotBlank() }
                            val codesList = recoveryCodes.split("\n").map { it.trim() }.filter { it.isNotBlank() }
                            onConfirm(
                                CreateSecretRequest(
                                    website = website,
                                    username = username,
                                    password = password,
                                    notes = notes.ifBlank { null },
                                    expirationDate = expirationDate.ifBlank { null },
                                    tags = tagsList,
                                    twofaEnabled = twofaEnabled,
                                    twofaType = if (twofaEnabled) twofaType.ifBlank { null } else null,
                                    recoveryCodes = if (twofaEnabled) codesList else emptyList()
                                )
                            )
                        },
                        enabled = !isLoading && website.isNotBlank() && username.isNotBlank() && password.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = Color(0xFF4CAF50),
                            disabledBackgroundColor = Color.Gray
                        )
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        } else {
                            Text("Add Secret", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Edit secret dialog (similar to create but pre-filled)
 */
@Composable
fun EditSecretDialog(
    secret: SecretEntry,
    onConfirm: (UpdateSecretRequest) -> Unit,
    onDismiss: () -> Unit,
    isLoading: Boolean
) {
    var website by remember { mutableStateOf(secret.website) }
    var username by remember { mutableStateOf(secret.username) }
    var password by remember { mutableStateOf(secret.password) }
    var notes by remember { mutableStateOf(secret.notes ?: "") }
    var tags by remember { mutableStateOf(secret.tags.joinToString(", ")) }
    var expirationDate by remember { mutableStateOf(secret.expirationDate ?: "") }
    var twofaEnabled by remember { mutableStateOf(secret.metadata?.twofaEnabled ?: false) }
    var twofaType by remember { mutableStateOf(secret.metadata?.twofaType ?: "") }
    var recoveryCodes by remember { mutableStateOf(secret.metadata?.recoveryCodes?.joinToString("\n") ?: "") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var show2FASection by remember { mutableStateOf(secret.metadata?.twofaEnabled == true) }

    Dialog(onDismissRequest = { if (!isLoading) onDismiss() }) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = Color(0xFF3C3F41),
            modifier = Modifier.width(500.dp).heightIn(max = 600.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Title
                Text(
                    "Edit Secret",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )

                // Website
                OutlinedTextField(
                    value = website,
                    onValueChange = { website = it },
                    label = { Text("Website", color = Color.Gray) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading,
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        textColor = Color.White,
                        cursorColor = Color.White,
                        focusedBorderColor = Color(0xFF4CAF50),
                        unfocusedBorderColor = Color.Gray
                    ),
                    singleLine = true
                )

                // Username
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username", color = Color.Gray) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading,
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        textColor = Color.White,
                        cursorColor = Color.White,
                        focusedBorderColor = Color(0xFF4CAF50),
                        unfocusedBorderColor = Color.Gray
                    ),
                    singleLine = true
                )

                // Password
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password", color = Color.Gray) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading,
                    visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                            Icon(
                                if (isPasswordVisible) FeatherIcons.EyeOff else FeatherIcons.Eye,
                                contentDescription = null,
                                tint = Color.Gray
                            )
                        }
                    },
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        textColor = Color.White,
                        cursorColor = Color.White,
                        focusedBorderColor = Color(0xFF4CAF50),
                        unfocusedBorderColor = Color.Gray
                    ),
                    singleLine = true
                )

                // Tags
                OutlinedTextField(
                    value = tags,
                    onValueChange = { tags = it },
                    label = { Text("Tags", color = Color.Gray) },
                    placeholder = { Text("work, personal", color = Color.Gray) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading,
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        textColor = Color.White,
                        cursorColor = Color.White,
                        focusedBorderColor = Color(0xFF4CAF50),
                        unfocusedBorderColor = Color.Gray
                    ),
                    singleLine = true
                )

                // Expiration Date
                OutlinedTextField(
                    value = expirationDate,
                    onValueChange = { expirationDate = it },
                    label = { Text("Expiration Date", color = Color.Gray) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading,
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        textColor = Color.White,
                        cursorColor = Color.White,
                        focusedBorderColor = Color(0xFF4CAF50),
                        unfocusedBorderColor = Color.Gray
                    ),
                    singleLine = true
                )

                // Notes
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes", color = Color.Gray) },
                    modifier = Modifier.fillMaxWidth().height(100.dp),
                    enabled = !isLoading,
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        textColor = Color.White,
                        cursorColor = Color.White,
                        focusedBorderColor = Color(0xFF4CAF50),
                        unfocusedBorderColor = Color.Gray
                    ),
                    maxLines = 4
                )

                // 2FA Section (similar to create dialog)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "2FA Information",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    IconButton(onClick = { show2FASection = !show2FASection }) {
                        Icon(
                            if (show2FASection) FeatherIcons.ChevronUp else FeatherIcons.ChevronDown,
                            contentDescription = null,
                            tint = Color.Gray
                        )
                    }
                }

                if (show2FASection) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF2B2D30), RoundedCornerShape(4.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Checkbox(
                                checked = twofaEnabled,
                                onCheckedChange = { twofaEnabled = it },
                                enabled = !isLoading,
                                colors = CheckboxDefaults.colors(
                                    checkedColor = Color(0xFF4CAF50),
                                    uncheckedColor = Color.Gray
                                )
                            )
                            Text("2FA Enabled", color = Color.White, fontSize = 14.sp)
                        }

                        if (twofaEnabled) {
                            OutlinedTextField(
                                value = twofaType,
                                onValueChange = { twofaType = it },
                                label = { Text("2FA Type", color = Color.Gray) },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !isLoading,
                                colors = TextFieldDefaults.outlinedTextFieldColors(
                                    textColor = Color.White,
                                    cursorColor = Color.White,
                                    focusedBorderColor = Color(0xFF4CAF50),
                                    unfocusedBorderColor = Color.Gray
                                ),
                                singleLine = true
                            )

                            OutlinedTextField(
                                value = recoveryCodes,
                                onValueChange = { recoveryCodes = it },
                                label = { Text("Recovery Codes", color = Color.Gray) },
                                modifier = Modifier.fillMaxWidth().height(100.dp),
                                enabled = !isLoading,
                                colors = TextFieldDefaults.outlinedTextFieldColors(
                                    textColor = Color.White,
                                    cursorColor = Color.White,
                                    focusedBorderColor = Color(0xFF4CAF50),
                                    unfocusedBorderColor = Color.Gray
                                ),
                                maxLines = 5
                            )
                        }
                    }
                }

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onDismiss,
                        enabled = !isLoading
                    ) {
                        Text("Cancel", color = Color.Gray)
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val tagsList = tags.split(",").map { it.trim() }.filter { it.isNotBlank() }
                            val codesList = recoveryCodes.split("\n").map { it.trim() }.filter { it.isNotBlank() }
                            onConfirm(
                                UpdateSecretRequest(
                                    secretId = secret.id,
                                    website = website,
                                    username = username,
                                    password = password,
                                    notes = notes.ifBlank { null },
                                    expirationDate = expirationDate.ifBlank { null },
                                    tags = tagsList,
                                    twofaEnabled = twofaEnabled,
                                    twofaType = if (twofaEnabled) twofaType.ifBlank { null } else null,
                                    recoveryCodes = if (twofaEnabled) codesList else emptyList()
                                )
                            )
                        },
                        enabled = !isLoading && website.isNotBlank() && username.isNotBlank() && password.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = Color(0xFF4CAF50)
                        )
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        } else {
                            Text("Update Secret", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Delete confirmation dialog
 */
@Composable
fun DeleteSecretConfirmationDialog(
    secret: SecretEntry,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    isLoading: Boolean
) {
    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        title = {
            Text(
                "Delete Secret?",
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Are you sure you want to delete this secret?",
                    color = Color.Gray
                )
                Text(
                    "${secret.website} : ${secret.username}",
                    color = Color.White,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    "This action cannot be undone.",
                    color = Color(0xFFE57373),
                    fontSize = 12.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isLoading,
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = Color(0xFFE57373)
                )
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                } else {
                    Text("Delete", color = Color.White)
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isLoading
            ) {
                Text("Cancel", color = Color.Gray)
            }
        },
        backgroundColor = Color(0xFF3C3F41)
    )
}
