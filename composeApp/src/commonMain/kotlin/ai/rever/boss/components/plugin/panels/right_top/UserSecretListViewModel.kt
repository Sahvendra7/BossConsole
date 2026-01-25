package ai.rever.boss.components.plugin.panels.right_top

import ai.rever.boss.utils.logging.BossLogger
import ai.rever.boss.utils.logging.LogCategory
import ai.rever.boss.components.plugin.tab_types.fluck.SecretChangeNotifier
import ai.rever.boss.services.supabase.SecretService
import ai.rever.boss.services.supabase.models.SecretEntryWithSharing
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
    private val logger = BossLogger.forComponent("UserSecretListViewModel")

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Job tracking to prevent race conditions and request cancellations (Issue #352)
    private var loadJob: Job? = null
    private var loadMoreJob: Job? = null

    // State
    var state by mutableStateOf(UserSecretListState())
        private set

    init {
        loadSecrets()

        // Observe secret change events for automatic synchronization
        scope.launch {
            SecretChangeNotifier.secretChangeEvents.collect { event ->
                logger.debug(LogCategory.GENERAL, "Received secret change event", mapOf("event" to event.toString()))
                // Reload secrets whenever they change in other components
                loadSecrets()
            }
        }
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
     *
     * Race condition fix (Issue #352): Cancels in-flight requests before starting new one
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
                logger.debug(LogCategory.GENERAL, "Loaded secrets successfully", mapOf("count" to secrets.size, "hasMore" to paginatedResult.hasMore))
            }.onFailure { exception ->
                // Silently ignore cancellation - it's expected when a new load starts (Issue #352)
                if (exception is CancellationException) {
                    logger.debug(LogCategory.GENERAL, "Load cancelled (new request started)")
                    return@onFailure
                }

                val error = exception.message ?: "Unknown error"
                state = state.copy(
                    isLoading = false,
                    errorMessage = error
                )
                logger.warn(LogCategory.GENERAL, "Failed to load secrets", error = exception)
            }
        }
    }

    /**
     * Load more secrets (pagination)
     *
     * Race condition fix (Issue #352): Tracks job to prevent overlapping requests
     */
    fun loadMoreSecrets() {
        // Don't load if already loading or no more data or in search mode
        if (state.isLoadingMore || !state.hasMore || state.isLoading || state.searchQuery.isNotBlank()) {
            return
        }

        // Cancel previous pagination request if still in flight
        loadMoreJob?.cancel()

        state = state.copy(isLoadingMore = true)

        loadMoreJob = scope.launch {
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
                logger.debug(LogCategory.GENERAL, "Loaded more secrets", mapOf("newCount" to newSecrets.size, "total" to allSecrets.size, "hasMore" to paginatedResult.hasMore))
            }.onFailure { exception ->
                // Silently ignore cancellation - it's expected when a new load starts (Issue #352)
                if (exception is CancellationException) {
                    logger.debug(LogCategory.GENERAL, "Pagination cancelled (new request started)")
                    return@onFailure
                }

                val error = exception.message ?: "Unknown error"
                state = state.copy(
                    isLoadingMore = false,
                    errorMessage = error
                )
                logger.warn(LogCategory.GENERAL, "Failed to load more secrets", error = exception)
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
        logger.debug(LogCategory.GENERAL, "Search completed", mapOf("matchCount" to filtered.size, "query" to query))
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
