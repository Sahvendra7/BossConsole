package ai.rever.boss.plugin.panel.secretmanager

import ai.rever.boss.plugin.api.CreateSecretRequestData
import ai.rever.boss.plugin.api.RoleInfoData
import ai.rever.boss.plugin.api.SecretEntryData
import ai.rever.boss.plugin.api.SecretShareData
import ai.rever.boss.plugin.api.ShareSecretRequestData
import ai.rever.boss.plugin.api.UpdateSecretRequestData
import ai.rever.boss.plugin.api.UserWithRolesData
import ai.rever.boss.plugin.ui.BossDarkBackground
import ai.rever.boss.plugin.ui.BossDarkBorder
import ai.rever.boss.plugin.ui.BossDarkTextSecondary
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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

@Composable
fun CreateSecretDialog(
    onConfirm: (CreateSecretRequestData) -> Unit,
    onDismiss: () -> Unit,
    isLoading: Boolean
) {
    var website by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.width(400.dp),
            color = Color(0xFF2D2D2D),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Add New Secret",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(16.dp))

                DialogTextField(
                    value = website,
                    onValueChange = { website = it },
                    label = "Website",
                    placeholder = "e.g., github.com"
                )

                Spacer(modifier = Modifier.height(12.dp))

                DialogTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = "Username / Email",
                    placeholder = "e.g., user@example.com"
                )

                Spacer(modifier = Modifier.height(12.dp))

                DialogTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = "Password",
                    placeholder = "Enter password",
                    isPassword = true,
                    showPassword = showPassword,
                    onTogglePassword = { showPassword = !showPassword }
                )

                Spacer(modifier = Modifier.height(12.dp))

                DialogTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = "Notes (optional)",
                    placeholder = "Additional notes",
                    singleLine = false
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
                                onConfirm(CreateSecretRequestData(
                                    website = website,
                                    username = username,
                                    password = password,
                                    notes = notes.takeIf { it.isNotBlank() }
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
                            Text("Create", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EditSecretDialog(
    secret: SecretEntryData,
    onConfirm: (UpdateSecretRequestData) -> Unit,
    onDismiss: () -> Unit,
    isLoading: Boolean
) {
    var website by remember { mutableStateOf(secret.website) }
    var username by remember { mutableStateOf(secret.username) }
    var password by remember { mutableStateOf(secret.password) }
    var notes by remember { mutableStateOf(secret.notes ?: "") }
    var showPassword by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.width(400.dp),
            color = Color(0xFF2D2D2D),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Edit Secret",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(16.dp))

                DialogTextField(
                    value = website,
                    onValueChange = { website = it },
                    label = "Website"
                )

                Spacer(modifier = Modifier.height(12.dp))

                DialogTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = "Username / Email"
                )

                Spacer(modifier = Modifier.height(12.dp))

                DialogTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = "Password",
                    isPassword = true,
                    showPassword = showPassword,
                    onTogglePassword = { showPassword = !showPassword }
                )

                Spacer(modifier = Modifier.height(12.dp))

                DialogTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = "Notes (optional)",
                    singleLine = false
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
                                onConfirm(UpdateSecretRequestData(
                                    secretId = secret.id,
                                    website = website,
                                    username = username,
                                    password = password,
                                    notes = notes.takeIf { it.isNotBlank() }
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
fun DeleteConfirmationDialog(
    secret: SecretEntryData,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    isLoading: Boolean
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Delete Secret?", color = Color.White, fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                Text(
                    "Are you sure you want to delete this secret?",
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "${secret.website} - ${secret.username}",
                    color = Color(0xFF90CAF9),
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "This action cannot be undone.",
                    color = Color(0xFFF44336),
                    fontSize = 11.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isLoading,
                colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFFF44336))
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Delete", color = Color.White)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isLoading) {
                Text("Cancel", color = BossDarkTextSecondary)
            }
        },
        backgroundColor = Color(0xFF2D2D2D)
    )
}

@Composable
fun ShareSecretDialog(
    secret: SecretEntryData,
    shares: List<SecretShareData>,
    availableUsers: List<UserWithRolesData>,
    availableRoles: List<RoleInfoData>,
    onShare: (ShareSecretRequestData) -> Unit,
    onRevoke: (userId: String?, roleId: String?) -> Unit,
    onDismiss: () -> Unit,
    onSearchUsers: (String) -> Unit,
    isLoading: Boolean,
    isLoadingShares: Boolean,
    isLoadingUsers: Boolean
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(0) } // 0 = Users, 1 = Roles

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.width(450.dp).heightIn(max = 500.dp),
            color = Color(0xFF2D2D2D),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Share Secret",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    "${secret.website} - ${secret.username}",
                    color = BossDarkTextSecondary,
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Current shares
                if (shares.isNotEmpty() || isLoadingShares) {
                    Text(
                        "Currently shared with:",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    if (isLoadingShares) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color(0xFF4CAF50),
                            strokeWidth = 2.dp
                        )
                    } else {
                        shares.forEach { share ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF1E1F22), RoundedCornerShape(4.dp))
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        share.sharedWithUserEmail ?: share.sharedWithRoleName ?: "Unknown",
                                        color = Color.White,
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        if (share.sharedWithUserId != null) "User" else "Role",
                                        color = BossDarkTextSecondary,
                                        fontSize = 10.sp
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        onRevoke(share.sharedWithUserId, share.sharedWithRoleId)
                                    },
                                    modifier = Modifier.size(24.dp),
                                    enabled = !isLoading
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Revoke",
                                        tint = Color(0xFFF44336),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Tabs
                TabRow(
                    selectedTabIndex = selectedTab,
                    backgroundColor = Color(0xFF1E1F22),
                    contentColor = Color(0xFF4CAF50)
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Users", fontSize = 12.sp) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Roles", fontSize = 12.sp) }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (selectedTab == 0) {
                    // User search
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = {
                            searchQuery = it
                            onSearchUsers(it)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(32.dp)
                            .background(Color(0xFF1E1F22), RoundedCornerShape(4.dp))
                            .border(1.dp, BossDarkBorder, RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.body2.copy(color = Color.White),
                        cursorBrush = SolidColor(Color(0xFF4CAF50)),
                        decorationBox = { innerTextField ->
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = BossDarkTextSecondary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(modifier = Modifier.weight(1f)) {
                                    if (searchQuery.isEmpty()) {
                                        Text(
                                            "Search users by email...",
                                            color = BossDarkTextSecondary,
                                            fontSize = 12.sp
                                        )
                                    }
                                    innerTextField()
                                }
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // User list
                    if (isLoadingUsers) {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(100.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = Color(0xFF4CAF50),
                                strokeWidth = 2.dp
                            )
                        }
                    } else {
                        LazyColumn(modifier = Modifier.height(150.dp)) {
                            items(availableUsers) { user ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onShare(ShareSecretRequestData(
                                                secretId = secret.id,
                                                targetUserId = user.id
                                            ))
                                        }
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Person,
                                        contentDescription = null,
                                        tint = BossDarkTextSecondary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        user.email,
                                        color = Color.White,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // Roles list
                    LazyColumn(modifier = Modifier.height(150.dp)) {
                        items(availableRoles) { role ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onShare(ShareSecretRequestData(
                                            secretId = secret.id,
                                            targetRoleId = role.id
                                        ))
                                    }
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Group,
                                    contentDescription = null,
                                    tint = BossDarkTextSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        role.name,
                                        color = Color.White,
                                        fontSize = 12.sp
                                    )
                                    val description = role.description
                                    if (description != null) {
                                        Text(
                                            description,
                                            color = BossDarkTextSecondary,
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF424242))
                    ) {
                        Text("Done", color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun DialogTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String = "",
    isPassword: Boolean = false,
    showPassword: Boolean = false,
    onTogglePassword: (() -> Unit)? = null,
    singleLine: Boolean = true
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
                .background(Color(0xFF1E1F22), RoundedCornerShape(4.dp))
                .border(1.dp, BossDarkBorder, RoundedCornerShape(4.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                singleLine = singleLine,
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
