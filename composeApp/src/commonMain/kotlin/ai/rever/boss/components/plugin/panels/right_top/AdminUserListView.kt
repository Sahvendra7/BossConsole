package ai.rever.boss.components.plugin.panels.right_top

import ai.rever.boss.services.supabase.models.AppRole
import ai.rever.boss.services.supabase.models.UserWithRoles
import androidx.compose.foundation.background
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
import compose.icons.feathericons.Plus
import compose.icons.feathericons.Minus
import compose.icons.feathericons.RefreshCw
import compose.icons.feathericons.Search
import compose.icons.feathericons.Shield
import compose.icons.feathericons.Trash2
import compose.icons.feathericons.X

/**
 * Main content composable for Admin Role Management
 */
@Composable
fun AdminRoleManagementContent(viewModel: AdminRoleManagementViewModel) {
    val state = viewModel.state

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF2B2D30))
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header with refresh button
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "User Role Management",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )

                IconButton(
                    onClick = { viewModel.loadAllUsers() },
                    enabled = !state.isLoading
                ) {
                    Icon(
                        FeatherIcons.RefreshCw,
                        contentDescription = "Refresh",
                        tint = Color.White
                    )
                }
            }

            // Search bar (server-side database search)
            SearchBar(
                query = state.searchQuery,
                onQueryChange = { viewModel.searchUsers(it) },
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            )

            // User count
            Text(
                "${state.filteredUsers.size} users",
                color = Color.Gray,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // User list or loading/error states
            when {
                state.isLoading -> {
                    LoadingView()
                }
                state.errorMessage != null -> {
                    ErrorView(
                        message = state.errorMessage,
                        onRetry = { viewModel.loadAllUsers() }
                    )
                }
                state.filteredUsers.isEmpty() -> {
                    EmptyView(searchQuery = state.searchQuery)
                }
                else -> {
                    UserList(
                        users = state.filteredUsers,
                        onAssignRole = { user -> viewModel.showAssignRoleDialog(user) },
                        onRemoveRole = { user, role -> viewModel.showRemoveRoleDialog(user, role) },
                        onDeleteUser = { user -> viewModel.showDeleteUserDialog(user) },
                        onLoadMore = { viewModel.loadMoreUsers() },
                        isLoadingMore = state.isLoadingMore,
                        hasMore = state.hasMore,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Dialogs
        if (state.showAssignRoleDialog && state.selectedUser != null) {
            AssignRoleDialog(
                user = state.selectedUser,
                availableRoles = viewModel.getAvailableRolesForUser(state.selectedUser),
                selectedRole = state.selectedRoleToAssign,
                onRoleSelected = { viewModel.setRoleToAssign(it) },
                onConfirm = {
                    state.selectedRoleToAssign?.let { role ->
                        viewModel.assignRole(state.selectedUser.userId, role)
                    }
                    viewModel.hideAssignRoleDialog()
                },
                onDismiss = { viewModel.hideAssignRoleDialog() },
                isLoading = state.isOperationInProgress
            )
        }

        if (state.showRemoveRoleDialog && state.selectedUser != null && state.selectedRoleToRemove != null) {
            RemoveRoleConfirmationDialog(
                user = state.selectedUser,
                role = state.selectedRoleToRemove,
                onConfirm = {
                    viewModel.removeRole(state.selectedUser.userId, state.selectedRoleToRemove)
                    viewModel.hideRemoveRoleDialog()
                },
                onDismiss = { viewModel.hideRemoveRoleDialog() },
                isLoading = state.isOperationInProgress
            )
        }

        if (state.showDeleteUserDialog && state.selectedUser != null) {
            DeleteUserConfirmationDialog(
                user = state.selectedUser,
                onConfirm = {
                    viewModel.deleteUser(state.selectedUser.userId)
                    viewModel.hideDeleteUserDialog()
                },
                onDismiss = { viewModel.hideDeleteUserDialog() },
                isLoading = state.isOperationInProgress
            )
        }

        // Success/Error snackbars
        state.successMessage?.let { message ->
            LaunchedEffect(message) {
                kotlinx.coroutines.delay(3000)
                viewModel.clearSuccessMessage()
            }
            Snackbar(
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                backgroundColor = Color(0xFF4CAF50)
            ) {
                Text(message, color = Color.White)
            }
        }
    }
}

/**
 * Search bar composable
 */
@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier,
        placeholder = { Text("Search users by email...", color = Color.Gray) },
        leadingIcon = {
            Icon(FeatherIcons.Search, contentDescription = "Search", tint = Color.Gray)
        },
        colors = TextFieldDefaults.outlinedTextFieldColors(
            textColor = Color.White,
            backgroundColor = Color(0xFF3C3F41),
            focusedBorderColor = Color(0xFF4A90E2),
            unfocusedBorderColor = Color.Gray
        ),
        singleLine = true
    )
}

/**
 * User list composable with infinite scroll
 */
@Composable
fun UserList(
    users: List<UserWithRoles>,
    onAssignRole: (UserWithRoles) -> Unit,
    onRemoveRole: (UserWithRoles, AppRole) -> Unit,
    onDeleteUser: (UserWithRoles) -> Unit,
    onLoadMore: () -> Unit,
    isLoadingMore: Boolean,
    hasMore: Boolean,
    modifier: Modifier = Modifier
) {
    LazyColumn(modifier = modifier) {
        items(users) { user ->
            UserCard(
                user = user,
                onAssignRole = { onAssignRole(user) },
                onRemoveRole = { role -> onRemoveRole(user, role) },
                onDeleteUser = { onDeleteUser(user) },
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            )
        }

        // Instagram-style loading indicator at the bottom
        if (hasMore) {
            item {
                LoadingMoreIndicator(
                    isLoading = isLoadingMore,
                    onLoadMore = onLoadMore
                )
            }
        }

        // End of list message
        if (!hasMore && users.isNotEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No more users to load",
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

/**
 * Loading more indicator - triggers load when visible
 */
@Composable
fun LoadingMoreIndicator(
    isLoading: Boolean,
    onLoadMore: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    color = Color(0xFF4A90E2),
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    "Loading more users...",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }
        } else {
            // Trigger load when this composable becomes visible
            LaunchedEffect(Unit) {
                onLoadMore()
            }

            // Show a small progress indicator while waiting
            CircularProgressIndicator(
                color = Color(0xFF4A90E2),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * User card composable
 */
@Composable
fun UserCard(
    user: UserWithRoles,
    onAssignRole: () -> Unit,
    onRemoveRole: (AppRole) -> Unit,
    onDeleteUser: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        backgroundColor = Color(0xFF3C3F41),
        elevation = 2.dp,
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Email
            Text(
                user.email,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Role badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (user.roles.isEmpty()) {
                    Text(
                        "No roles assigned",
                        color = Color.Gray,
                        fontSize = 12.sp,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    )
                } else {
                    user.roles.forEach { role ->
                        RoleBadge(
                            role = role,
                            userId = user.userId,
                            onRemove = { onRemoveRole(role) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Action buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onAssignRole,
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = Color(0xFF4A90E2)
                    ),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(
                        FeatherIcons.Plus,
                        contentDescription = "Assign Role",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Assign Role", color = Color.White, fontSize = 12.sp)
                }

                // Delete button - only show for non-admin users
                if (!user.isAdmin) {
                    Button(
                        onClick = onDeleteUser,
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = Color(0xFFE91E63)
                        ),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(
                            FeatherIcons.Trash2,
                            contentDescription = "Delete User",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Delete", color = Color.White, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

/**
 * Role badge with optional remove button
 *
 * Uses a single color for all roles to support extensibility.
 * Admin roles get a small shield icon for visual distinction.
 *
 * Removal restrictions:
 * - USER role: Never removable (baseline role for all users)
 * - ADMIN role: Not removable from own account (prevents self-lockout)
 * - Other roles: Removable by admins
 */
@Composable
fun RoleBadge(
    role: AppRole,
    userId: String,
    onRemove: () -> Unit
) {
    // Get current user to check if this is their own account
    val currentUser by ai.rever.boss.services.auth.AuthStateManager.currentUser.collectAsState()
    val isOwnAccount = currentUser?.id == userId

    // Single consistent color for all roles (BOSS theme blue)
    val badgeColor = Color(0xFF4A90E2)

    // Determine if role is removable
    val isRemovable = when {
        role == AppRole.USER -> false  // USER role is never removable
        role == AppRole.ADMIN && isOwnAccount -> false  // Can't remove own admin role
        else -> true  // All other roles are removable
    }

    Surface(
        color = badgeColor.copy(alpha = 0.2f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Show shield icon for admin role
            if (role == AppRole.ADMIN) {
                Icon(
                    FeatherIcons.Shield,
                    contentDescription = "Admin",
                    tint = badgeColor,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
            }

            Text(
                role.value,
                color = badgeColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )

            // Only show remove button for non-USER roles
            if (isRemovable) {
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.size(16.dp)
                ) {
                    Icon(
                        FeatherIcons.X,
                        contentDescription = "Remove ${role.value} role",
                        tint = badgeColor,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }
    }
}

/**
 * Loading view
 */
@Composable
fun LoadingView() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(color = Color(0xFF4A90E2))
    }
}

/**
 * Error view
 */
@Composable
fun ErrorView(message: String, onRetry: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "Error",
                color = Color(0xFFE91E63),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                message,
                color = Color.Gray,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onRetry) {
                Text("Retry")
            }
        }
    }
}

/**
 * Empty view
 */
@Composable
fun EmptyView(searchQuery: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            if (searchQuery.isBlank()) "No users found" else "No users match \"$searchQuery\"",
            color = Color.Gray,
            fontSize = 14.sp
        )
    }
}
