package ai.rever.boss.components.plugin.panels.right_top

import ai.rever.boss.components.bars.PanelScrollbarConfig
import ai.rever.boss.components.bars.lazyListScrollbar
import ai.rever.boss.services.supabase.models.RoleInfo
import ai.rever.boss.services.supabase.models.PermissionInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import compose.icons.FeatherIcons
import compose.icons.feathericons.AlertTriangle
import compose.icons.feathericons.Key
import compose.icons.feathericons.Shield
import compose.icons.feathericons.Trash2

/**
 * Dialog for creating a new role
 */
@Composable
fun CreateRoleDialog(
    onDismiss: () -> Unit,
    onConfirm: (roleName: String, description: String?) -> Unit
) {
    var roleName by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .width(400.dp)
                .padding(16.dp),
            shape = RoundedCornerShape(12.dp),
            elevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .background(MaterialTheme.colors.surface)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Title
                Text(
                    text = "Create New Role",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colors.onSurface
                )

                // Role name input
                OutlinedTextField(
                    value = roleName,
                    onValueChange = {
                        roleName = it
                        error = null
                    },
                    label = { Text("Role Name") },
                    placeholder = { Text("e.g., developer") },
                    modifier = Modifier.fillMaxWidth(),
                    isError = error != null,
                    singleLine = true
                )

                // Description input (optional)
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (optional)") },
                    placeholder = { Text("e.g., Developer role with code access") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )

                // Validation rules
                Text(
                    text = "Rules: lowercase, start with letter, 3-50 chars, alphanumeric + underscore",
                    fontSize = 12.sp,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                )

                // Error message
                error?.let { errorMsg ->
                    Text(
                        text = errorMsg,
                        color = MaterialTheme.colors.error,
                        fontSize = 13.sp
                    )
                }

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            if (roleName.isBlank()) {
                                error = "Role name cannot be empty"
                                return@Button
                            }

                            val validationError = ai.rever.boss.services.supabase.RoleCreationService.validateRoleName(roleName)
                            if (validationError != null) {
                                error = validationError
                                return@Button
                            }

                            onConfirm(roleName.trim(), description.trim().takeIf { it.isNotBlank() })
                        },
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = MaterialTheme.colors.primary
                        )
                    ) {
                        Text("Create")
                    }
                }
            }
        }
    }
}

/**
 * Dialog for creating a new permission
 */
@Composable
fun CreatePermissionDialog(
    onDismiss: () -> Unit,
    onConfirm: (permissionName: String, description: String?) -> Unit,
    isOperationInProgress: Boolean = false,
    errorMessage: String? = null
) {
    var permissionName by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }

    // Auto-close on success (when not in progress and no error after confirming)
    var hasConfirmed by remember { mutableStateOf(false) }
    LaunchedEffect(isOperationInProgress, errorMessage) {
        if (hasConfirmed && !isOperationInProgress && errorMessage == null) {
            onDismiss()
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .width(400.dp)
                .padding(16.dp),
            shape = RoundedCornerShape(12.dp),
            elevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .background(MaterialTheme.colors.surface)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Title
                Text(
                    text = "Create New Permission",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colors.onSurface
                )

                // Permission name input
                OutlinedTextField(
                    value = permissionName,
                    onValueChange = {
                        permissionName = it
                        localError = null
                    },
                    label = { Text("Permission Name") },
                    placeholder = { Text("e.g., code.review") },
                    modifier = Modifier.fillMaxWidth(),
                    isError = localError != null || errorMessage != null,
                    singleLine = true,
                    enabled = !isOperationInProgress
                )

                // Description input (optional)
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (optional)") },
                    placeholder = { Text("e.g., Review code changes") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3,
                    enabled = !isOperationInProgress
                )

                // Validation rules
                Text(
                    text = "Rules: domain.action format (e.g., code.review), lowercase, alphanumeric + underscore",
                    fontSize = 12.sp,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                )

                // Error message (show ViewModel error or local error)
                val displayError = errorMessage ?: localError
                displayError?.let { errorMsg ->
                    Text(
                        text = errorMsg,
                        color = MaterialTheme.colors.error,
                        fontSize = 13.sp
                    )
                }

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            if (permissionName.isBlank()) {
                                localError = "Permission name cannot be empty"
                                return@Button
                            }

                            val validationError = ai.rever.boss.services.supabase.RoleCreationService.validatePermissionName(permissionName)
                            if (validationError != null) {
                                localError = validationError
                                return@Button
                            }

                            hasConfirmed = true
                            onConfirm(permissionName.trim(), description.trim().takeIf { it.isNotBlank() })
                        },
                        enabled = !isOperationInProgress,
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = MaterialTheme.colors.secondary
                        )
                    ) {
                        if (isOperationInProgress) {
                            androidx.compose.material.CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = MaterialTheme.colors.onSecondary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text("Create")
                    }
                }
            }
        }
    }
}

/**
 * Dialog for assigning a permission to a role
 */
@Composable
fun AssignPermissionDialog(
    role: RoleInfo,
    availablePermissions: List<PermissionInfo>,
    onDismiss: () -> Unit,
    onConfirm: (permissionName: String) -> Unit
) {
    var selectedPermission by remember { mutableStateOf<PermissionInfo?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    val filteredPermissions = remember(availablePermissions, searchQuery) {
        if (searchQuery.isBlank()) {
            availablePermissions
        } else {
            availablePermissions.filter { permission ->
                permission.name.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .width(450.dp)
                .height(500.dp)
                .padding(16.dp),
            shape = RoundedCornerShape(12.dp),
            elevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .background(MaterialTheme.colors.surface)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Title
                Text(
                    text = "Assign Permission to \"${role.name}\"",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colors.onSurface
                )

                // Search field
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Search permissions") },
                    placeholder = { Text("Type to filter...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Available permissions count
                Text(
                    text = "Available permissions: ${filteredPermissions.size}",
                    fontSize = 13.sp,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                )

                // Permissions list
                if (filteredPermissions.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (searchQuery.isBlank())
                                "All permissions are already assigned"
                            else
                                "No permissions found matching \"$searchQuery\"",
                            fontSize = 14.sp,
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f)
                        )
                    }
                } else {
                    val permListState = rememberLazyListState()
                    LazyColumn(
                        state = permListState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .lazyListScrollbar(
                                listState = permListState,
                                direction = Orientation.Vertical,
                                config = PanelScrollbarConfig
                            ),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(filteredPermissions) { permission ->
                            PermissionSelectionItem(
                                permission = permission,
                                isSelected = selectedPermission == permission,
                                onClick = { selectedPermission = permission }
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
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            selectedPermission?.let { permission ->
                                onConfirm(permission.name)
                            }
                        },
                        enabled = selectedPermission != null,
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = MaterialTheme.colors.secondary
                        )
                    ) {
                        Text("Assign")
                    }
                }
            }
        }
    }
}

/**
 * Permission selection item
 */
@Composable
private fun PermissionSelectionItem(
    permission: PermissionInfo,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = if (isSelected) 4.dp else 1.dp,
        backgroundColor = if (isSelected)
            MaterialTheme.colors.secondary.copy(alpha = 0.2f)
        else
            MaterialTheme.colors.surface,
        shape = RoundedCornerShape(6.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = isSelected,
                onClick = onClick,
                colors = RadioButtonDefaults.colors(
                    selectedColor = MaterialTheme.colors.secondary
                )
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = permission.name,
                fontSize = 14.sp,
                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                color = MaterialTheme.colors.onSurface
            )
        }
    }
}

/**
 * Delete Role Confirmation Dialog
 * Warns user about consequences of deleting a role
 */
@Composable
fun DeleteRoleDialog(
    role: RoleInfo,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = FeatherIcons.AlertTriangle,
                    contentDescription = "Warning",
                    tint = MaterialTheme.colors.error,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "Delete Role?",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "You are about to delete the role:",
                    fontSize = 14.sp,
                    color = MaterialTheme.colors.onSurface
                )

                Surface(
                    color = MaterialTheme.colors.error.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = FeatherIcons.Shield,
                            contentDescription = null,
                            tint = MaterialTheme.colors.error
                        )
                        Column {
                            Text(
                                text = role.name,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colors.onSurface
                            )
                            if (role.description != null) {
                                Text(
                                    text = role.description,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }

                Text(
                    text = "⚠️ Warning:",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colors.error
                )

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "• This role will be removed from all users",
                        fontSize = 13.sp,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.8f)
                    )
                    Text(
                        text = "• All permission assignments will be deleted",
                        fontSize = 13.sp,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.8f)
                    )
                    Text(
                        text = "• This action cannot be undone",
                        fontSize = 13.sp,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.8f),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = MaterialTheme.colors.error
                )
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = FeatherIcons.Trash2,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Text("Delete Role")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Delete Permission Confirmation Dialog
 * Warns user about consequences of deleting a permission
 */
@Composable
fun DeletePermissionDialog(
    permission: PermissionInfo,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = FeatherIcons.AlertTriangle,
                    contentDescription = "Warning",
                    tint = MaterialTheme.colors.error,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "Delete Permission?",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "You are about to delete the permission:",
                    fontSize = 14.sp,
                    color = MaterialTheme.colors.onSurface
                )

                Surface(
                    color = MaterialTheme.colors.error.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = FeatherIcons.Key,
                            contentDescription = null,
                            tint = MaterialTheme.colors.error
                        )
                        Column {
                            Text(
                                text = permission.name,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colors.onSurface
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = permission.getDomain(),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                                )
                                Text(
                                    text = "→",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.4f)
                                )
                                Text(
                                    text = permission.getAction(),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                                )
                            }
                            if (permission.description != null) {
                                Text(
                                    text = permission.description,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                }

                Text(
                    text = "⚠️ Warning:",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colors.error
                )

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "• This permission will be removed from all roles",
                        fontSize = 13.sp,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.8f)
                    )
                    Text(
                        text = "• Users with roles that had this permission will lose access",
                        fontSize = 13.sp,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.8f)
                    )
                    Text(
                        text = "• This action cannot be undone",
                        fontSize = 13.sp,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.8f),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = MaterialTheme.colors.error
                )
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = FeatherIcons.Trash2,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Text("Delete Permission")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
