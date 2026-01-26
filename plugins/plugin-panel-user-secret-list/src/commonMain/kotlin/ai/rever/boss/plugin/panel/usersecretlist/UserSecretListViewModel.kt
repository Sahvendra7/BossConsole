package ai.rever.boss.plugin.panel.usersecretlist

import ai.rever.boss.plugin.api.SecretDataProvider
import ai.rever.boss.plugin.api.SecretEntryWithSharingData
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for User Secret List (Read-Only)
 *
 * Uses SecretDataProvider interface for data operations,
 * allowing this panel to be in a separate plugin module.
 *
 * Features:
 * - Load secrets with sharing information (owned + shared)
 * - Client-side search/filter by website or username
 * - Pagination support
 * - No CRUD operations (read-only view)
 * - Automatic refresh when secrets change (via event flow)
 */
class UserSecretListViewModel(
    private val secretDataProvider: SecretDataProvider,
    secretChangeEvents: SharedFlow<Any>? = null
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Job tracking to prevent race conditions
    private var loadJob: Job? = null
    private var loadMoreJob: Job? = null

    // State
    var state by mutableStateOf(UserSecretListState())
        private set

    init {
        loadSecrets()

        // Observe secret change events for automatic synchronization
        secretChangeEvents?.let { events ->
            scope.launch {
                events.collect {
                    // Reload secrets whenever they change in other components
                    loadSecrets()
                }
            }
        }
    }

    /**
     * Dispose the ViewModel and cancel all coroutines
     */
    fun dispose() {
        scope.cancel()
    }

    /**
     * Load all accessible secrets (owned + shared)
     */
    fun loadSecrets() {
        // Cancel any in-flight load or pagination requests
        loadJob?.cancel()
        loadMoreJob?.cancel()

        state = state.copy(
            isLoading = true,
            errorMessage = null,
            searchQuery = "",
            currentOffset = 0,
            hasMore = true
        )

        loadJob = scope.launch {
            val result = secretDataProvider.getUserSecretsWithSharingInfo(
                limit = state.pageSize,
                offset = 0
            )

            result.onSuccess { paginatedResult ->
                val secrets = paginatedResult.data
                state = state.copy(
                    allSecrets = secrets,
                    secrets = secrets,
                    isLoading = false,
                    currentOffset = secrets.size,
                    hasMore = paginatedResult.hasMore
                )
            }.onFailure { exception ->
                if (exception is CancellationException) return@onFailure

                val error = exception.message ?: "Unknown error"
                state = state.copy(
                    isLoading = false,
                    errorMessage = error
                )
            }
        }
    }

    /**
     * Load more secrets (pagination)
     */
    fun loadMoreSecrets() {
        if (state.isLoadingMore || !state.hasMore || state.isLoading || state.searchQuery.isNotBlank()) {
            return
        }

        loadMoreJob?.cancel()
        state = state.copy(isLoadingMore = true)

        loadMoreJob = scope.launch {
            val result = secretDataProvider.getUserSecretsWithSharingInfo(
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
            }.onFailure { exception ->
                if (exception is CancellationException) return@onFailure

                state = state.copy(
                    isLoadingMore = false,
                    errorMessage = exception.message ?: "Unknown error"
                )
            }
        }
    }

    /**
     * Search/filter secrets by website or username (client-side)
     */
    fun searchSecrets(query: String) {
        state = state.copy(searchQuery = query)

        if (query.isBlank()) {
            state = state.copy(secrets = state.allSecrets)
            return
        }

        // Client-side filter
        val filtered = state.allSecrets.filter { secret ->
            secret.website.contains(query, ignoreCase = true) ||
                secret.username.contains(query, ignoreCase = true)
        }

        state = state.copy(secrets = filtered)
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
    val allSecrets: List<SecretEntryWithSharingData> = emptyList(),
    val secrets: List<SecretEntryWithSharingData> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val errorMessage: String? = null,
    val searchQuery: String = "",
    val expandedSecretIds: Set<String> = emptySet(),
    val pageSize: Int = 50,
    val currentOffset: Int = 0,
    val hasMore: Boolean = true
)
