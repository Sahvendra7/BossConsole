package ai.rever.boss.components.plugin.panels.right_top

import ai.rever.boss.services.supabase.models.SecretEntry
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Secret list view with pagination
 */
@Composable
fun SecretList(
    secrets: List<SecretEntry>,
    visiblePasswordIds: Set<String>,
    expandedSecretIds: Set<String>,
    onTogglePassword: (String) -> Unit,
    onToggleMetadata: (String) -> Unit,
    onEdit: (SecretEntry) -> Unit,
    onDelete: (SecretEntry) -> Unit,
    onShare: (SecretEntry) -> Unit = {},
    onLoadMore: () -> Unit,
    isLoadingMore: Boolean,
    hasMore: Boolean,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    // Trigger load more when scrolled to bottom
    // Key on hasMore and isLoadingMore to properly react to state changes
    LaunchedEffect(listState, hasMore, isLoadingMore) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { lastVisibleIndex ->
                if (lastVisibleIndex != null &&
                    lastVisibleIndex >= secrets.size - 3 &&
                    hasMore &&
                    !isLoadingMore
                ) {
                    onLoadMore()
                }
            }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        items(
            items = secrets,
            key = { it.id }
        ) { secret ->
            SecretCard(
                secret = secret,
                isPasswordVisible = visiblePasswordIds.contains(secret.id),
                isMetadataExpanded = expandedSecretIds.contains(secret.id),
                onTogglePassword = { onTogglePassword(secret.id) },
                onToggleMetadata = { onToggleMetadata(secret.id) },
                onEdit = { onEdit(secret) },
                onDelete = { onDelete(secret) },
                onShare = { onShare(secret) }
            )
        }

        // Loading more indicator
        if (isLoadingMore) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = Color(0xFF4CAF50),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        // End of list indicator
        if (!hasMore && secrets.isNotEmpty()) {
            item {
                Text(
                    "— End of list —",
                    color = Color.Gray,
                    fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth().padding(16.dp).wrapContentWidth(Alignment.CenterHorizontally)
                )
            }
        }
    }
}
