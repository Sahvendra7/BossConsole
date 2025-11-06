package ai.rever.boss.components.plugin.panels.right_top

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.rever.boss.components.common.BossSearchBar
import compose.icons.FeatherIcons
import compose.icons.feathericons.Key
import compose.icons.feathericons.RefreshCw
import compose.icons.feathericons.Search

/**
 * Main content composable for User Secret List (Read-Only)
 */
@Composable
fun UserSecretListContent(viewModel: UserSecretListViewModel) {
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
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        FeatherIcons.Key,
                        contentDescription = "My Secrets",
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        "My Secrets",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

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
            }

            // Search bar
            UserSecretSearchBar(
                query = state.searchQuery,
                onQueryChange = { viewModel.searchSecrets(it) },
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            )

            // Secret count
            Text(
                if (state.searchQuery.isBlank()) {
                    "${state.secrets.size} secret${if (state.secrets.size != 1) "s" else ""}"
                } else {
                    "${state.secrets.size} result${if (state.secrets.size != 1) "s" else ""} for '${state.searchQuery}'"
                },
                color = Color.Gray,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Content based on state
            when {
                state.isLoading -> {
                    UserSecretLoadingView()
                }
                state.errorMessage != null -> {
                    UserSecretErrorView(
                        message = state.errorMessage,
                        onRetry = { viewModel.loadSecrets() },
                        onDismiss = { viewModel.clearError() }
                    )
                }
                state.secrets.isEmpty() -> {
                    UserSecretEmptyView(searchQuery = state.searchQuery)
                }
                else -> {
                    UserSecretList(
                        secrets = state.secrets,
                        expandedSecretIds = state.expandedSecretIds,
                        onToggleMetadata = { viewModel.toggleMetadataExpanded(it) },
                        onLoadMore = { viewModel.loadMoreSecrets() },
                        isLoadingMore = state.isLoadingMore,
                        hasMore = state.hasMore,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

/**
 * Search bar composable
 */
@Composable
fun UserSecretSearchBar(
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
fun UserSecretLoadingView() {
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
                "Loading your secrets...",
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
fun UserSecretErrorView(
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
fun UserSecretEmptyView(searchQuery: String) {
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
                Icon(
                    FeatherIcons.Key,
                    contentDescription = "No secrets",
                    tint = Color.Gray,
                    modifier = Modifier.size(64.dp)
                )
                Text(
                    "No secrets found",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "You don't have any secrets yet, or none have been shared with you.",
                    color = Color.Gray,
                    fontSize = 14.sp
                )
            } else {
                Icon(
                    FeatherIcons.Search,
                    contentDescription = "No results",
                    tint = Color.Gray,
                    modifier = Modifier.size(64.dp)
                )
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
