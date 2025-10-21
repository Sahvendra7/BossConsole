package ai.rever.boss.components.plugin.panels.right_top

import ai.rever.boss.services.supabase.SecretService
import ai.rever.boss.services.supabase.models.CreateSecretRequest
import ai.rever.boss.services.supabase.models.SecretEntry
import ai.rever.boss.services.supabase.models.UpdateSecretRequest
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * ViewModel for Secret Manager
 *
 * Manages the state and business logic for the secret manager panel.
 * Provides methods to:
 * - Load all user secrets
 * - Search secrets
 * - Create new secrets
 * - Update existing secrets
 * - Delete secrets
 * - Toggle password visibility
 * - Expand/collapse metadata
 * - Handle loading and error states
 *
 * Lifecycle management:
 * - Uses SupervisorJob to prevent child coroutine failures from cancelling the entire scope
 * - Call dispose() when the ViewModel is no longer needed to cancel all coroutines
 */
class SecretManagerViewModel {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // State
    var state by mutableStateOf(SecretManagerState())
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
     * Load all secrets for the current user
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
            val result = SecretService.getUserSecrets(limit = state.pageSize, offset = 0)

            result.onSuccess { paginatedResult ->
                val secrets = paginatedResult.data
                state = state.copy(
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
            val result = SecretService.getUserSecrets(
                limit = state.pageSize,
                offset = state.currentOffset
            )

            result.onSuccess { paginatedResult ->
                val newSecrets = paginatedResult.data
                state = state.copy(
                    secrets = state.secrets + newSecrets,
                    isLoadingMore = false,
                    currentOffset = state.currentOffset + newSecrets.size,
                    hasMore = paginatedResult.hasMore
                )
                println("✅ Loaded ${newSecrets.size} more secrets (total: ${state.secrets.size}, hasMore: ${paginatedResult.hasMore})")
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
     * Search secrets by website or username
     */
    fun searchSecrets(query: String) {
        state = state.copy(
            searchQuery = query,
            isLoading = true,
            errorMessage = null,
            currentOffset = 0,
            hasMore = false
        )

        if (query.isBlank()) {
            // Reset to full list
            loadSecrets()
            return
        }

        scope.launch {
            val result = SecretService.searchSecrets(
                query = query,
                limit = 100,  // Show more results for search
                offset = 0
            )

            result.onSuccess { paginatedResult ->
                val secrets = paginatedResult.data
                state = state.copy(
                    secrets = secrets,
                    isLoading = false
                )
                println("✅ Found ${secrets.size} secrets matching '$query'")
            }.onFailure { exception ->
                val error = exception.message ?: "Unknown error"
                state = state.copy(
                    isLoading = false,
                    errorMessage = error
                )
                println("❌ Failed to search secrets: $error")
            }
        }
    }

    /**
     * Show create secret dialog
     */
    fun showCreateDialog() {
        state = state.copy(
            showCreateDialog = true,
            selectedSecret = null
        )
    }

    /**
     * Hide create secret dialog
     */
    fun hideCreateDialog() {
        state = state.copy(showCreateDialog = false)
    }

    /**
     * Show edit secret dialog
     */
    fun showEditDialog(secret: SecretEntry) {
        state = state.copy(
            showEditDialog = true,
            selectedSecret = secret
        )
    }

    /**
     * Hide edit secret dialog
     */
    fun hideEditDialog() {
        state = state.copy(
            showEditDialog = false,
            selectedSecret = null
        )
    }

    /**
     * Show delete confirmation dialog
     */
    fun showDeleteDialog(secret: SecretEntry) {
        state = state.copy(
            showDeleteDialog = true,
            selectedSecret = secret
        )
    }

    /**
     * Hide delete confirmation dialog
     */
    fun hideDeleteDialog() {
        state = state.copy(
            showDeleteDialog = false,
            selectedSecret = null
        )
    }

    /**
     * Create a new secret
     */
    fun createSecret(request: CreateSecretRequest) {
        state = state.copy(isOperationInProgress = true)

        scope.launch {
            val result = SecretService.createSecret(request)

            result.onSuccess {
                println("✅ Successfully created secret: ${request.website}:${request.username}")
                state = state.copy(isOperationInProgress = false)
                hideCreateDialog()
                // Reload secrets to show the new one
                loadSecrets()
            }.onFailure { exception ->
                val error = exception.message ?: "Unknown error"
                state = state.copy(
                    isOperationInProgress = false,
                    errorMessage = error
                )
                println("❌ Failed to create secret: $error")
            }
        }
    }

    /**
     * Update an existing secret
     */
    fun updateSecret(request: UpdateSecretRequest) {
        state = state.copy(isOperationInProgress = true)

        scope.launch {
            val result = SecretService.updateSecret(request)

            result.onSuccess {
                println("✅ Successfully updated secret: ${request.secretId}")
                state = state.copy(isOperationInProgress = false)
                hideEditDialog()
                // Reload secrets to show the updated one
                loadSecrets()
            }.onFailure { exception ->
                val error = exception.message ?: "Unknown error"
                state = state.copy(
                    isOperationInProgress = false,
                    errorMessage = error
                )
                println("❌ Failed to update secret: $error")
            }
        }
    }

    /**
     * Delete a secret
     */
    fun deleteSecret(secretId: String) {
        state = state.copy(isOperationInProgress = true)

        scope.launch {
            val result = SecretService.deleteSecret(secretId)

            result.onSuccess {
                println("✅ Successfully deleted secret: $secretId")
                state = state.copy(isOperationInProgress = false)
                hideDeleteDialog()
                // Remove from local state
                state = state.copy(
                    secrets = state.secrets.filter { it.id != secretId }
                )
            }.onFailure { exception ->
                val error = exception.message ?: "Unknown error"
                state = state.copy(
                    isOperationInProgress = false,
                    errorMessage = error
                )
                println("❌ Failed to delete secret: $error")
            }
        }
    }

    /**
     * Toggle password visibility for a secret
     */
    fun togglePasswordVisibility(secretId: String) {
        val currentVisible = state.visiblePasswordIds
        state = if (currentVisible.contains(secretId)) {
            state.copy(visiblePasswordIds = currentVisible - secretId)
        } else {
            state.copy(visiblePasswordIds = currentVisible + secretId)
        }
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
 * State for Secret Manager
 */
data class SecretManagerState(
    val secrets: List<SecretEntry> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isOperationInProgress: Boolean = false,
    val errorMessage: String? = null,
    val searchQuery: String = "",
    val showCreateDialog: Boolean = false,
    val showEditDialog: Boolean = false,
    val showDeleteDialog: Boolean = false,
    val selectedSecret: SecretEntry? = null,
    val expandedSecretIds: Set<String> = emptySet(),
    val visiblePasswordIds: Set<String> = emptySet(),
    val pageSize: Int = 50,
    val currentOffset: Int = 0,
    val hasMore: Boolean = true
)
