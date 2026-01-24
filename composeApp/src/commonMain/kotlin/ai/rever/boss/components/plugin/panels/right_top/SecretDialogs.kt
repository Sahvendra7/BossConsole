package ai.rever.boss.components.plugin.panels.right_top

import ai.rever.boss.components.bars.getPanelScrollbarConfig
import ai.rever.boss.components.bars.verticalScrollWithScrollbar
import ai.rever.boss.services.supabase.models.CreateSecretRequest
import ai.rever.boss.services.supabase.models.SecretEntry
import ai.rever.boss.services.supabase.models.UpdateSecretRequest
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import compose.icons.feathericons.Trash2

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
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScrollWithScrollbar(
                        scrollState = rememberScrollState(),
                        scrollbarConfig = getPanelScrollbarConfig()
                    ),
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
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScrollWithScrollbar(
                        scrollState = rememberScrollState(),
                        scrollbarConfig = getPanelScrollbarConfig()
                    ),
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

/**
 * Share secret dialog
 * Allows sharing secrets with users or roles and viewing existing shares
 */
@Composable
fun ShareSecretDialog(
    secret: SecretEntry,
    shares: List<ai.rever.boss.services.supabase.models.SecretShareEntry>,
    availableUsers: List<ai.rever.boss.services.supabase.models.UserWithRoles>,
    availableRoles: List<ai.rever.boss.services.supabase.models.RoleInfo>,
    onShare: (ai.rever.boss.services.supabase.models.ShareSecretRequest) -> Unit,
    onRevoke: (userId: String?, roleId: String?) -> Unit,
    onDismiss: () -> Unit,
    isLoading: Boolean,
    isLoadingShares: Boolean,
    onSearchUsers: (String) -> Unit = {},
    isLoadingUsers: Boolean = false
) {
    var selectedUser by remember { mutableStateOf<ai.rever.boss.services.supabase.models.UserWithRoles?>(null) }
    var selectedRole by remember { mutableStateOf<ai.rever.boss.services.supabase.models.RoleInfo?>(null) }
    var shareNotes by remember { mutableStateOf("") }
    var expiresAt by remember { mutableStateOf("") }
    var shareType by remember { mutableStateOf("user") } // "user" or "role"

    Dialog(onDismissRequest = { if (!isLoading) onDismiss() }) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = Color(0xFF3C3F41),
            modifier = Modifier.width(600.dp).heightIn(max = 700.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScrollWithScrollbar(
                        scrollState = rememberScrollState(),
                        scrollbarConfig = getPanelScrollbarConfig()
                    ),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Title
                Text(
                    "Share Secret",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )

                // Secret info
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF2B2D30), RoundedCornerShape(4.dp))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = secret.website,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = secret.username,
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                    }
                }

                Divider(color = Color(0xFF4E5254), thickness = 1.dp)

                // Existing shares section
                Text(
                    "Current Shares",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )

                if (isLoadingShares) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(100.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = Color(0xFF4CAF50),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                } else if (shares.isEmpty()) {
                    Text(
                        "No one has access to this secret yet",
                        color = Color.Gray,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF2B2D30), RoundedCornerShape(4.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        shares.forEach { share ->
                            ShareItem(
                                share = share,
                                onRevoke = {
                                    onRevoke(share.sharedWithUserId, share.sharedWithRoleId)
                                },
                                isLoading = isLoading
                            )
                        }
                    }
                }

                Divider(color = Color(0xFF4E5254), thickness = 1.dp)

                // Add new share section
                Text(
                    "Grant Access",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )

                // Share type selector (User or Role)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { shareType = "user" },
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = if (shareType == "user") Color(0xFF4CAF50) else Color(0xFF2B2D30)
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("User", color = Color.White)
                    }
                    Button(
                        onClick = { shareType = "role" },
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = if (shareType == "role") Color(0xFF4CAF50) else Color(0xFF2B2D30)
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Role", color = Color.White)
                    }
                }

                // Dropdown selector based on share type
                if (shareType == "user") {
                    Text(
                        "Select User:",
                        color = Color.White,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    UserDropdown(
                        users = availableUsers,
                        selectedUser = selectedUser,
                        onUserSelected = { selectedUser = it },
                        onSearch = onSearchUsers,
                        isLoading = isLoadingUsers,
                        enabled = !isLoading
                    )
                } else {
                    Text(
                        "Select Role:",
                        color = Color.White,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    RoleDropdownForSharing(
                        roles = availableRoles,
                        selectedRole = selectedRole,
                        onRoleSelected = { selectedRole = it },
                        enabled = !isLoading
                    )
                }

                // Notes
                OutlinedTextField(
                    value = shareNotes,
                    onValueChange = { shareNotes = it },
                    label = { Text("Notes (Optional)", color = Color.Gray) },
                    placeholder = { Text("Why are you sharing this?", color = Color.Gray) },
                    modifier = Modifier.fillMaxWidth().height(80.dp),
                    enabled = !isLoading,
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        textColor = Color.White,
                        cursorColor = Color.White,
                        focusedBorderColor = Color(0xFF4CAF50),
                        unfocusedBorderColor = Color.Gray
                    ),
                    maxLines = 3
                )

                // Expiration date
                OutlinedTextField(
                    value = expiresAt,
                    onValueChange = { expiresAt = it },
                    label = { Text("Expires At (Optional)", color = Color.Gray) },
                    placeholder = { Text("YYYY-MM-DD HH:MM:SS", color = Color.Gray) },
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

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        enabled = !isLoading
                    ) {
                        Text("Close", color = Color.Gray)
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val request = ai.rever.boss.services.supabase.models.ShareSecretRequest(
                                secretId = secret.id,
                                targetUserId = if (shareType == "user") selectedUser?.userId else null,
                                targetRoleId = if (shareType == "role") selectedRole?.id else null,
                                notes = shareNotes.ifBlank { null },
                                expiresAt = expiresAt.ifBlank { null }
                            )
                            onShare(request)
                            // Clear form
                            selectedUser = null
                            selectedRole = null
                            shareNotes = ""
                            expiresAt = ""
                        },
                        enabled = !isLoading &&
                            ((shareType == "user" && selectedUser != null) ||
                             (shareType == "role" && selectedRole != null)),
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
                            Text("Share", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Share item component showing a single share entry
 */
@Composable
fun ShareItem(
    share: ai.rever.boss.services.supabase.models.SecretShareEntry,
    onRevoke: () -> Unit,
    isLoading: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF3C3F41), RoundedCornerShape(4.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            // Show user email or role name
            Text(
                text = share.sharedWithUserEmail ?: share.sharedWithRoleName ?: "Unknown",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )

            // Show who shared it
            Text(
                text = "Shared by ${share.sharedByEmail}",
                color = Color.Gray,
                fontSize = 11.sp
            )

            // Show access level
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Access: ${share.accessLevel}",
                    color = Color(0xFF4CAF50),
                    fontSize = 11.sp
                )

                // Show expiration if set
                if (share.expiresAt != null) {
                    Text(
                        text = " • Expires: ${share.expiresAt}",
                        color = Color(0xFFFFB74D),
                        fontSize = 11.sp
                    )
                }
            }
        }

        // Revoke button
        IconButton(
            onClick = onRevoke,
            enabled = !isLoading,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                FeatherIcons.Trash2,
                contentDescription = "Revoke access",
                tint = Color(0xFFE57373),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/**
 * User dropdown for selecting a user to share with (server-side search)
 */
@Composable
fun UserDropdown(
    users: List<ai.rever.boss.services.supabase.models.UserWithRoles>,
    selectedUser: ai.rever.boss.services.supabase.models.UserWithRoles?,
    onUserSelected: (ai.rever.boss.services.supabase.models.UserWithRoles) -> Unit,
    onSearch: (String) -> Unit,
    isLoading: Boolean = false,
    enabled: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    Box {
        // Dropdown button
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled) { expanded = true },
            shape = RoundedCornerShape(4.dp),
            color = if (enabled) Color(0xFF2B2D30) else Color(0xFF1E1E1E)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    selectedUser?.email ?: "Select a user...",
                    color = if (selectedUser != null) Color.White else Color.Gray,
                    fontSize = 14.sp
                )
                Icon(
                    FeatherIcons.ChevronDown,
                    contentDescription = "Expand",
                    tint = Color.Gray,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // Dropdown menu with search
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = {
                expanded = false
                searchQuery = ""
            },
            modifier = Modifier.background(Color(0xFF3C3F41)).heightIn(max = 300.dp)
        ) {
            // Search field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { query ->
                    searchQuery = query
                    // Trigger server-side search
                    onSearch(query)
                },
                placeholder = { Text("Search users...", color = Color.Gray) },
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                enabled = !isLoading,
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    textColor = Color.White,
                    backgroundColor = Color(0xFF2B2D30),
                    focusedBorderColor = Color(0xFF4CAF50),
                    unfocusedBorderColor = Color.Gray
                ),
                singleLine = true,
                trailingIcon = {
                    if (isLoading) {
                        CircularProgressIndicator(
                            color = Color(0xFF4CAF50),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            )

            Divider(color = Color(0xFF4E5254), thickness = 1.dp)

            if (isLoading) {
                // Show loading indicator
                Box(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = Color(0xFF4CAF50),
                        modifier = Modifier.size(24.dp)
                    )
                }
            } else if (users.isEmpty()) {
                DropdownMenuItem(onClick = {}) {
                    Text(
                        "No users found",
                        color = Color.Gray,
                        fontSize = 14.sp
                    )
                }
            } else {
                users.forEach { user ->
                    DropdownMenuItem(
                        onClick = {
                            onUserSelected(user)
                            expanded = false
                            searchQuery = ""
                        }
                    ) {
                        Column {
                            Text(
                                user.email,
                                color = Color.White,
                                fontSize = 14.sp
                            )
                            if (user.roles.isNotEmpty()) {
                                Text(
                                    "Roles: ${user.roles.joinToString(", ")}",
                                    color = Color.Gray,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Role dropdown for selecting a role to share with
 */
@Composable
fun RoleDropdownForSharing(
    roles: List<ai.rever.boss.services.supabase.models.RoleInfo>,
    selectedRole: ai.rever.boss.services.supabase.models.RoleInfo?,
    onRoleSelected: (ai.rever.boss.services.supabase.models.RoleInfo) -> Unit,
    enabled: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        // Dropdown button
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled) { expanded = true },
            shape = RoundedCornerShape(4.dp),
            color = if (enabled) Color(0xFF2B2D30) else Color(0xFF1E1E1E)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    selectedRole?.name ?: "Select a role...",
                    color = if (selectedRole != null) Color.White else Color.Gray,
                    fontSize = 14.sp
                )
                Icon(
                    FeatherIcons.ChevronDown,
                    contentDescription = "Expand",
                    tint = Color.Gray,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // Dropdown menu
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(Color(0xFF3C3F41))
        ) {
            if (roles.isEmpty()) {
                DropdownMenuItem(onClick = {}) {
                    Text(
                        "No roles available",
                        color = Color.Gray,
                        fontSize = 14.sp
                    )
                }
            } else {
                roles.forEach { role ->
                    DropdownMenuItem(
                        onClick = {
                            onRoleSelected(role)
                            expanded = false
                        }
                    ) {
                        Column {
                            Text(
                                role.name,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                            if (role.description != null && role.description.isNotBlank()) {
                                Text(
                                    role.description,
                                    color = Color.Gray,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Quick create secret dialog for browser integration (Issue #56).
 *
 * Streamlined version of CreateSecretDialog optimized for quick credential entry
 * from the browser. Pre-fills website domain and focuses on essential fields.
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
    var tags by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = { if (!isLoading) onDismiss() }) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = Color(0xFF3C3F41),
            modifier = Modifier.width(450.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Title
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Quick Add Secret",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "🔑",
                        fontSize = 24.sp
                    )
                }

                Text(
                    "Save credentials for this website to auto-fill in the future",
                    color = Color.Gray,
                    fontSize = 13.sp
                )

                // Website (pre-filled)
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
                    label = { Text("Username / Email", color = Color.Gray) },
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

                // Tags (optional)
                OutlinedTextField(
                    value = tags,
                    onValueChange = { tags = it },
                    label = { Text("Tags (Optional)", color = Color.Gray) },
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

                // Help text
                Surface(
                    color = Color(0xFF2B2D30),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("💡", fontSize = 16.sp)
                        Text(
                            "You can add notes, expiration dates, and 2FA info later by editing the secret.",
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
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
                            onConfirm(
                                CreateSecretRequest(
                                    website = website,
                                    username = username,
                                    password = password,
                                    notes = null,
                                    expirationDate = null,
                                    tags = tagsList,
                                    twofaEnabled = false,
                                    twofaType = null,
                                    recoveryCodes = emptyList()
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
                            Text("Save Secret", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}
