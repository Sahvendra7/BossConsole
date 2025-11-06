package ai.rever.boss.components.plugin.panels.right_top

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.rever.boss.components.common.BossSearchBar
import compose.icons.FeatherIcons
import compose.icons.feathericons.Plus
import compose.icons.feathericons.RefreshCw

/**
 * Main content composable for Secret Manager
 */
@Composable
fun SecretManagerContent(viewModel: SecretManagerViewModel) {
    val state = viewModel.state

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF2B2D30))
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header with title and refresh button
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Secret Manager",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Refresh button
                    IconButton(
                        onClick = { viewModel.loadSecrets() },
                        enabled = !state.isLoading
                    ) {
                        Icon(
                            FeatherIcons.RefreshCw,
                            contentDescription = "Refresh",
                            tint = Color.White
                        )
                    }

                    // Add button
                    IconButton(
                        onClick = { viewModel.showCreateDialog() },
                        enabled = !state.isLoading
                    ) {
                        Icon(
                            FeatherIcons.Plus,
                            contentDescription = "Add Secret",
                            tint = Color(0xFF4CAF50)
                        )
                    }
                }
            }

            // Search bar
            SecretSearchBar(
                query = state.searchQuery,
                onQueryChange = { viewModel.searchSecrets(it) },
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            )

            // Secret count
            Text(
                "${state.secrets.size} secret${if (state.secrets.size != 1) "s" else ""}",
                color = Color.Gray,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Content based on state
            when {
                state.isLoading -> {
                    SecretLoadingView()
                }
                state.errorMessage != null -> {
                    SecretErrorView(
                        message = state.errorMessage,
                        onRetry = { viewModel.loadSecrets() },
                        onDismiss = { viewModel.clearError() }
                    )
                }
                state.secrets.isEmpty() -> {
                    SecretEmptyView(
                        searchQuery = state.searchQuery,
                        onAddSecret = { viewModel.showCreateDialog() }
                    )
                }
                else -> {
                    SecretList(
                        secrets = state.secrets,
                        visiblePasswordIds = state.visiblePasswordIds,
                        expandedSecretIds = state.expandedSecretIds,
                        onTogglePassword = { viewModel.togglePasswordVisibility(it) },
                        onToggleMetadata = { viewModel.toggleMetadataExpanded(it) },
                        onEdit = { viewModel.showEditDialog(it) },
                        onDelete = { viewModel.showDeleteDialog(it) },
                        onShare = { viewModel.showShareDialog(it) },
                        onLoadMore = { viewModel.loadMoreSecrets() },
                        isLoadingMore = state.isLoadingMore,
                        hasMore = state.hasMore,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Dialogs
        if (state.showCreateDialog) {
            CreateSecretDialog(
                onConfirm = { viewModel.createSecret(it) },
                onDismiss = { viewModel.hideCreateDialog() },
                isLoading = state.isOperationInProgress
            )
        }

        if (state.showEditDialog && state.selectedSecret != null) {
            EditSecretDialog(
                secret = state.selectedSecret,
                onConfirm = { viewModel.updateSecret(it) },
                onDismiss = { viewModel.hideEditDialog() },
                isLoading = state.isOperationInProgress
            )
        }

        if (state.showDeleteDialog && state.selectedSecret != null) {
            DeleteSecretConfirmationDialog(
                secret = state.selectedSecret,
                onConfirm = {
                    viewModel.deleteSecret(state.selectedSecret.id)
                },
                onDismiss = { viewModel.hideDeleteDialog() },
                isLoading = state.isOperationInProgress
            )
        }

        if (state.showShareDialog && state.selectedSecret != null) {
            ShareSecretDialog(
                secret = state.selectedSecret,
                shares = state.secretShares,
                availableUsers = state.availableUsers,
                availableRoles = state.availableRoles,
                onShare = { request ->
                    viewModel.shareSecret(request)
                },
                onRevoke = { userId, roleId ->
                    viewModel.unshareSecret(
                        secretId = state.selectedSecret.id,
                        userId = userId,
                        roleId = roleId
                    )
                },
                onDismiss = { viewModel.hideShareDialog() },
                isLoading = state.isOperationInProgress,
                isLoadingShares = state.isLoadingShares,
                onSearchUsers = { query ->
                    if (query.isBlank()) {
                        viewModel.loadAvailableUsers()
                    } else {
                        viewModel.searchUsersForSharing(query)
                    }
                },
                isLoadingUsers = state.isLoadingUsers
            )
        }
    }
}

/**
 * Search bar composable
 */
@Composable
fun SecretSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    BossSearchBar(
        query = query,
        onQueryChange = onQueryChange,
        placeholder = "Search by website or username...",
        modifier = modifier
    )
}

/**
 * Loading view
 */
@Composable
fun SecretLoadingView() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator(color = Color(0xFF4CAF50))
            Text(
                "Loading secrets...",
                color = Color.Gray,
                fontSize = 14.sp
            )
        }
    }
}

/**
 * Error view
 */
@Composable
fun SecretErrorView(
    message: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                "Error",
                color = Color(0xFFE57373),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                message,
                color = Color.Gray,
                fontSize = 14.sp
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = Color(0xFF4CAF50)
                    )
                ) {
                    Text("Retry", color = Color.White)
                }
                TextButton(onClick = onDismiss) {
                    Text("Dismiss", color = Color.Gray)
                }
            }
        }
    }
}

/**
 * Empty view (no secrets)
 */
@Composable
fun SecretEmptyView(
    searchQuery: String,
    onAddSecret: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            if (searchQuery.isBlank()) {
                Text(
                    "No secrets yet",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Add your first secret to get started",
                    color = Color.Gray,
                    fontSize = 14.sp
                )
                Button(
                    onClick = onAddSecret,
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = Color(0xFF4CAF50)
                    )
                ) {
                    Icon(
                        FeatherIcons.Plus,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Add Secret", color = Color.White)
                }
            } else {
                Text(
                    "No results found",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Try a different search term",
                    color = Color.Gray,
                    fontSize = 14.sp
                )
            }
        }
    }
}
