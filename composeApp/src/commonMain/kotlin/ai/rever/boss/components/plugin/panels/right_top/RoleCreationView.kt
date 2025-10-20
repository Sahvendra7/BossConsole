package ai.rever.boss.components.plugin.panels.right_top

import ai.rever.boss.services.supabase.models.RoleInfo
import ai.rever.boss.services.supabase.models.PermissionInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import compose.icons.FeatherIcons
import compose.icons.feathericons.AlertCircle
import compose.icons.feathericons.CheckCircle
import compose.icons.feathericons.Key
import compose.icons.feathericons.Plus
import compose.icons.feathericons.Shield
import compose.icons.feathericons.Trash2
import compose.icons.feathericons.X

/**
 * Main content for Role Creation panel
 */
@Composable
fun RoleCreationContent(viewModel: RoleCreationViewModel) {
    val state = viewModel.state

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colors.background)
                .padding(16.dp)
        ) {
            // Header
            RoleCreationHeader(
                onCreateRole = { viewModel.showCreateRoleDialog() },
                onCreatePermission = { viewModel.showCreatePermissionDialog() },
                isLoading = state.isLoading
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Content
            when {
                state.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                else -> {
                    RoleCreationMainContent(
                        state = state,
                        viewModel = viewModel
                    )
                }
            }
        }

        // Success/Error Messages
        state.successMessage?.let { message ->
            SuccessMessage(
                message = message,
                onDismiss = { viewModel.clearSuccessMessage() }
            )
        }

        state.errorMessage?.let { message ->
            ErrorMessage(
                message = message,
                onDismiss = { viewModel.clearErrorMessage() }
            )
        }
    }

    // Dialogs
    if (state.showCreateRoleDialog) {
        CreateRoleDialog(
            onDismiss = { viewModel.hideCreateRoleDialog() },
            onConfirm = { roleName, description ->
                viewModel.createRole(roleName, description)
                viewModel.hideCreateRoleDialog()
            }
        )
    }

    if (state.showCreatePermissionDialog) {
        CreatePermissionDialog(
            onDismiss = { viewModel.hideCreatePermissionDialog() },
            onConfirm = { permissionName, description ->
                viewModel.createPermission(permissionName, description)
                viewModel.hideCreatePermissionDialog()
            }
        )
    }

    if (state.showAssignPermissionDialog && state.selectedRole != null) {
        AssignPermissionDialog(
            role = state.selectedRole,
            availablePermissions = viewModel.getAvailablePermissionsForRole(state.selectedRole.name),
            onDismiss = { viewModel.hideAssignPermissionDialog() },
            onConfirm = { permissionName ->
                viewModel.assignPermission(state.selectedRole.name, permissionName)
                viewModel.hideAssignPermissionDialog()
            }
        )
    }

    // Delete Role Dialog
    if (state.showDeleteRoleDialog && state.roleToDelete != null) {
        DeleteRoleDialog(
            role = state.roleToDelete,
            onDismiss = { viewModel.hideDeleteRoleDialog() },
            onConfirm = {
                viewModel.deleteRole(state.roleToDelete.name)
            }
        )
    }

    // Delete Permission Dialog
    if (state.showDeletePermissionDialog && state.permissionToDelete != null) {
        DeletePermissionDialog(
            permission = state.permissionToDelete,
            onDismiss = { viewModel.hideDeletePermissionDialog() },
            onConfirm = {
                viewModel.deletePermission(state.permissionToDelete.name)
            }
        )
    }
}

/**
 * Header with create buttons
 */
@Composable
private fun RoleCreationHeader(
    onCreateRole: () -> Unit,
    onCreatePermission: () -> Unit,
    isLoading: Boolean
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Role & Permission Management",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colors.onBackground
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onCreateRole,
                enabled = !isLoading,
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = MaterialTheme.colors.primary
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = FeatherIcons.Plus,
                        contentDescription = "Create Role",
                        modifier = Modifier.size(16.dp)
                    )
                    Text("Create Role")
                }
            }

            Button(
                onClick = onCreatePermission,
                enabled = !isLoading,
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = MaterialTheme.colors.secondary
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = FeatherIcons.Plus,
                        contentDescription = "Create Permission",
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Create Permission",
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/**
 * Main content showing roles and permissions
 */
@Composable
private fun RoleCreationMainContent(
    state: RoleCreationState,
    viewModel: RoleCreationViewModel
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Role selector dropdown
        RoleDropdownSelector(
            roles = state.allRoles,
            selectedRole = state.selectedRole,
            onRoleSelected = { viewModel.selectRole(it) },
            onClearSelection = { viewModel.clearSelectedRole() },
            onDeleteRole = { viewModel.showDeleteRoleDialog(it) }
        )

        // Show permissions for selected role or all permissions
        if (state.selectedRole != null) {
            // Show permissions for the selected role
            SelectedRolePermissionsSection(
                role = state.selectedRole,
                rolePermissions = state.selectedRolePermissions,
                allPermissions = state.allPermissions,
                onAssignPermission = { viewModel.showAssignPermissionDialog(state.selectedRole) },
                onRemovePermission = { permission ->
                    viewModel.removePermission(state.selectedRole.name, permission)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        } else {
            // Show all permissions
            AllPermissionsSection(
                permissions = state.allPermissions,
                onDeletePermission = { viewModel.showDeletePermissionDialog(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        }
    }
}

/**
 * Role dropdown selector
 */
@Composable
private fun RoleDropdownSelector(
    roles: List<RoleInfo>,
    selectedRole: RoleInfo?,
    onRoleSelected: (RoleInfo) -> Unit,
    onClearSelection: () -> Unit,
    onDeleteRole: (RoleInfo) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = FeatherIcons.Shield,
                contentDescription = "Select Role",
                tint = MaterialTheme.colors.primary,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = "Select Role",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colors.onSurface
            )
        }

        Box(modifier = Modifier.weight(1f)) {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    backgroundColor = if (selectedRole != null)
                        MaterialTheme.colors.primary.copy(alpha = 0.1f)
                    else
                        MaterialTheme.colors.surface
                )
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = selectedRole?.name ?: "Choose a role...",
                        fontSize = 14.sp,
                        fontWeight = if (selectedRole != null) FontWeight.Medium else FontWeight.Normal,
                        modifier = Modifier.weight(1f)
                    )
                    if (selectedRole != null) {
                        IconButton(
                            onClick = {
                                onClearSelection()
                                expanded = false
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = FeatherIcons.X,
                                contentDescription = "Clear selection",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                roles.forEach { role ->
                    DropdownMenuItem(
                        onClick = {
                            onRoleSelected(role)
                            expanded = false
                        }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Role name with system badge
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = role.name,
                                    fontSize = 14.sp,
                                    fontWeight = if (role == selectedRole) FontWeight.Bold else FontWeight.Normal
                                )

                                // System badge for protected roles
                                if (role.isSystem) {
                                    Surface(
                                        color = MaterialTheme.colors.primary.copy(alpha = 0.1f),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = "SYSTEM",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colors.primary,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }

                            // Icons row
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Delete button (only for non-system roles)
                                if (!role.isSystem) {
                                    IconButton(
                                        onClick = {
                                            expanded = false
                                            onDeleteRole(role)
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = FeatherIcons.Trash2,
                                            contentDescription = "Delete role",
                                            tint = MaterialTheme.colors.error,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }

                                // Selected checkmark
                                if (role == selectedRole) {
                                    Icon(
                                        imageVector = FeatherIcons.CheckCircle,
                                        contentDescription = "Selected",
                                        tint = MaterialTheme.colors.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Read-only permission item (for all permissions view)
 */
@Composable
private fun PermissionItemReadOnly(
    permission: PermissionInfo,
    onDelete: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        backgroundColor = MaterialTheme.colors.surface,
        shape = RoundedCornerShape(4.dp),
        elevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Permission name and domain/action breakdown
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = permission.name,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colors.onSurface
                )

                // Show domain.action breakdown
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = permission.getDomain(),
                        fontSize = 11.sp,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f)
                    )
                    Text(
                        text = "→",
                        fontSize = 11.sp,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.3f)
                    )
                    Text(
                        text = permission.getAction(),
                        fontSize = 11.sp,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f)
                    )
                }
            }

            // System badge or delete button
            if (permission.isSystem) {
                Surface(
                    color = MaterialTheme.colors.secondary.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "SYSTEM",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colors.secondary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            } else if (onDelete != null) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = FeatherIcons.Trash2,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colors.error,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

/**
 * All permissions section (when no role selected)
 */
@Composable
private fun AllPermissionsSection(
    permissions: List<ai.rever.boss.services.supabase.models.PermissionInfo>,
    onDeletePermission: (PermissionInfo) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxHeight(),
        elevation = 4.dp,
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = FeatherIcons.Key,
                    contentDescription = "All Permissions",
                    tint = MaterialTheme.colors.secondary
                )
                Text(
                    text = "All Permissions (${permissions.size})",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colors.onSurface
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(permissions) { permission ->
                    PermissionItemReadOnly(
                        permission = permission,
                        onDelete = if (!permission.isSystem) {
                            { onDeletePermission(permission) }
                        } else null
                    )
                }
            }
        }
    }
}

/**
 * Selected role permissions section
 */
@Composable
private fun SelectedRolePermissionsSection(
    role: RoleInfo,
    rolePermissions: ai.rever.boss.services.supabase.models.RoleWithPermissions?,
    allPermissions: List<ai.rever.boss.services.supabase.models.PermissionInfo>,
    onAssignPermission: () -> Unit,
    onRemovePermission: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxHeight(),
        elevation = 4.dp,
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = FeatherIcons.Shield,
                    contentDescription = "Role",
                    tint = MaterialTheme.colors.primary
                )
                Text(
                    text = "${role.name} - Permissions",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colors.onSurface
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Assign permission button
            Button(
                onClick = onAssignPermission,
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = MaterialTheme.colors.secondary
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = FeatherIcons.Plus,
                    contentDescription = "Assign Permission",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Assign Permission")
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Permissions list
            Text(
                text = "Permissions (${rolePermissions?.permissions?.size ?: 0})",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colors.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (rolePermissions?.permissions.isNullOrEmpty()) {
                Text(
                    text = "No permissions assigned",
                    fontSize = 13.sp,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(rolePermissions?.permissions ?: emptyList()) { permission ->
                        PermissionItem(
                            permission = permission,
                            allPermissions = allPermissions,
                            onRemove = { onRemovePermission(permission) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Individual permission item with remove button
 */
@Composable
private fun PermissionItem(
    permission: String,
    allPermissions: List<PermissionInfo>,
    onRemove: () -> Unit
) {
    val permissionInfo = allPermissions.find { it.name == permission }
    val canDelete = permissionInfo?.canDelete() ?: true  // Default to deletable if not found

    Card(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = MaterialTheme.colors.surface,
        shape = RoundedCornerShape(4.dp),
        elevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = permission,
                    fontSize = 13.sp,
                    color = MaterialTheme.colors.onSurface
                )

                // System badge
                if (permissionInfo?.isSystem == true) {
                    Surface(
                        color = MaterialTheme.colors.secondary.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "SYSTEM",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colors.secondary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // Delete button (only if not system permission)
            if (canDelete) {
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = FeatherIcons.X,
                        contentDescription = "Remove",
                        tint = MaterialTheme.colors.error,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

/**
 * Success message overlay
 */
@Composable
private fun BoxScope.SuccessMessage(
    message: String,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(16.dp)
            .clickable(onClick = onDismiss),
        backgroundColor = Color(0xFF4CAF50),
        elevation = 8.dp
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = FeatherIcons.CheckCircle,
                contentDescription = "Success",
                tint = Color.White
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = message,
                color = Color.White,
                fontSize = 14.sp
            )
        }
    }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(3000)
        onDismiss()
    }
}

/**
 * Error message overlay
 */
@Composable
private fun BoxScope.ErrorMessage(
    message: String,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(16.dp)
            .clickable(onClick = onDismiss),
        backgroundColor = Color(0xFFF44336),
        elevation = 8.dp
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = FeatherIcons.AlertCircle,
                contentDescription = "Error",
                tint = Color.White
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = message,
                color = Color.White,
                fontSize = 14.sp
            )
        }
    }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(5000)
        onDismiss()
    }
}
