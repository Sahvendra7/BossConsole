package ai.rever.boss.components.plugin.panels.right_top

import ai.rever.boss.services.supabase.SecretService
import ai.rever.boss.services.supabase.models.SecretEntryWithSharing
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * ViewModel for User Secret List (Read-Only)
 *
 * Manages the state and business logic for the user-level secret list panel.
 * Provides read-only access to secrets owned by or shared with the current user.
 *
 * Features:
 * - Load secrets with sharing information (owned + shared)
 * - Client-side search/filter by website or username
 * - Pagination support
 * - No CRUD operations (read-only view)
 *
 * Lifecycle management:
 * - Uses SupervisorJob to prevent child coroutine failures from cancelling the entire scope
 * - Call dispose() when the ViewModel is no longer needed to cancel all coroutines
 */
class UserSecretListViewModel {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // State
    var state by mutableStateOf(UserSecretListState())
        private set

    init {
        loadSecrets()
    }

    /**
     * Dispose the ViewModel and cancel all coroutines
     * Should be called when the component is destroyed
     */
    fun dispose() {
        scope.cancel()
    }

    /**
     * Load all accessible secrets (owned + shared)
     */
    fun loadSecrets() {
        state = state.copy(
            isLoading = true,
            errorMessage = null,
            searchQuery = "",
            currentOffset = 0,
            hasMore = true
        )

        scope.launch {
            val result = SecretService.getUserSecretsWithSharingInfo(limit = state.pageSize, offset = 0)

            result.onSuccess { paginatedResult ->
                val secrets = paginatedResult.data
                state = state.copy(
                    allSecrets = secrets,
                    secrets = secrets,
                    isLoading = false,
                    currentOffset = secrets.size,
                    hasMore = paginatedResult.hasMore
                )
                println("✅ Loaded ${secrets.size} secrets successfully (hasMore: ${paginatedResult.hasMore})")
            }.onFailure { exception ->
                val error = exception.message ?: "Unknown error"
                state = state.copy(
                    isLoading = false,
                    errorMessage = error
                )
                println("❌ Failed to load secrets: $error")
            }
        }
    }

    /**
     * Load more secrets (pagination)
     */
    fun loadMoreSecrets() {
        // Don't load if already loading or no more data or in search mode
        if (state.isLoadingMore || !state.hasMore || state.isLoading || state.searchQuery.isNotBlank()) {
            return
        }

        state = state.copy(isLoadingMore = true)

        scope.launch {
            val result = SecretService.getUserSecretsWithSharingInfo(
                limit = state.pageSize,
                offset = state.currentOffset
            )

            result.onSuccess { paginatedResult ->
                val newSecrets = paginatedResult.data
                val allSecrets = state.allSecrets + newSecrets
                state = state.copy(
                    allSecrets = allSecrets,
                    secrets = if (state.searchQuery.isBlank()) allSecrets else state.secrets,
                    isLoadingMore = false,
                    currentOffset = state.currentOffset + newSecrets.size,
                    hasMore = paginatedResult.hasMore
                )
                println("✅ Loaded ${newSecrets.size} more secrets (total: ${allSecrets.size}, hasMore: ${paginatedResult.hasMore})")
            }.onFailure { exception ->
                val error = exception.message ?: "Unknown error"
                state = state.copy(
                    isLoadingMore = false,
                    errorMessage = error
                )
                println("❌ Failed to load more secrets: $error")
            }
        }
    }

    /**
     * Search/filter secrets by website or username (client-side)
     */
    fun searchSecrets(query: String) {
        state = state.copy(searchQuery = query)

        if (query.isBlank()) {
            // Show all secrets
            state = state.copy(secrets = state.allSecrets)
            return
        }

        // Client-side filter
        val filtered = state.allSecrets.filter { secret ->
            secret.website.contains(query, ignoreCase = true) ||
                secret.username.contains(query, ignoreCase = true)
        }

        state = state.copy(secrets = filtered)
        println("🔍 Search: Found ${filtered.size} secrets matching '$query'")
    }

    /**
     * Toggle metadata expansion for a secret
     */
    fun toggleMetadataExpanded(secretId: String) {
        val currentExpanded = state.expandedSecretIds
        state = if (currentExpanded.contains(secretId)) {
            state.copy(expandedSecretIds = currentExpanded - secretId)
        } else {
            state.copy(expandedSecretIds = currentExpanded + secretId)
        }
    }

    /**
     * Clear error message
     */
    fun clearError() {
        state = state.copy(errorMessage = null)
    }
}

/**
 * State for User Secret List
 */
data class UserSecretListState(
    val allSecrets: List<SecretEntryWithSharing> = emptyList(), // All loaded secrets
    val secrets: List<SecretEntryWithSharing> = emptyList(), // Filtered secrets (for display)
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val errorMessage: String? = null,
    val searchQuery: String = "",
    val expandedSecretIds: Set<String> = emptySet(),
    val pageSize: Int = 50,
    val currentOffset: Int = 0,
    val hasMore: Boolean = true
)
