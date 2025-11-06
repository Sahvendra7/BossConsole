package ai.rever.boss.components.plugin.panels.right_top

import ai.rever.boss.components.plugin.tab_types.fluck.SecretChangeNotifier
import ai.rever.boss.services.supabase.SecretService
import ai.rever.boss.services.supabase.models.CreateSecretRequest
import ai.rever.boss.services.supabase.models.SecretEntry
import ai.rever.boss.services.supabase.models.UpdateSecretRequest
import ai.rever.boss.services.supabase.models.SecretShareEntry
import ai.rever.boss.services.supabase.models.ShareSecretRequest
import ai.rever.boss.services.supabase.models.UnshareSecretRequest
import ai.rever.boss.services.supabase.UserService
import ai.rever.boss.services.supabase.RoleCreationService
import ai.rever.boss.services.supabase.models.UserWithRoles
import ai.rever.boss.services.supabase.models.RoleInfo
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

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

    // Job tracking to prevent race conditions
    private var loadJob: Job? = null
    private var searchJob: Job? = null

    // State
    var state by mutableStateOf(SecretManagerState())
        private set

    init {
        loadSecrets()
        loadAvailableUsers()
        loadAvailableRoles()
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
     *
     * Race condition fix: Checks for active search jobs and prevents pagination during search
     */
    fun loadMoreSecrets() {
        // Enhanced guards to prevent race condition
        if (state.isLoadingMore) {
            println("⏸️  Pagination blocked: Already loading more")
            return
        }
        if (!state.hasMore) {
            println("⏸️  Pagination blocked: No more data")
            return
        }
        if (state.isLoading) {
            println("⏸️  Pagination blocked: Initial load in progress")
            return
        }
        if (state.searchQuery.isNotBlank()) {
            println("⏸️  Pagination blocked: Search mode active")
            return
        }
        if (searchJob?.isActive == true) {
            println("⏸️  Pagination blocked: Search job still running")
            return
        }

        // Cancel any previous load job
        loadJob?.cancel()

        state = state.copy(isLoadingMore = true)

        loadJob = scope.launch {
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
                // Silently ignore cancellation - it's expected behavior
                if (exception is CancellationException) {
                    println("⏸️  Pagination cancelled (search started)")
                    return@onFailure
                }

                // Log actual errors
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
     *
     * Race condition fix: Cancels in-flight pagination jobs to prevent duplicate entries
     */
    fun searchSecrets(query: String) {
        // Cancel any in-flight pagination to prevent race condition
        loadJob?.cancel()
        loadJob = null

        // Cancel previous search
        searchJob?.cancel()

        state = state.copy(
            searchQuery = query,
            isLoading = true,
            isLoadingMore = false,  // Prevent pagination during search
            errorMessage = null,
            currentOffset = 0,
            hasMore = false
        )

        if (query.isBlank()) {
            // Reset to full list
            searchJob = null
            loadSecrets()
            return
        }

        searchJob = scope.launch {
            val result = SecretService.searchSecrets(
                query = query,
                limit = 100,  // Show more results for search
                offset = 0
            )

            result.onSuccess { paginatedResult ->
                val secrets = paginatedResult.data
                state = state.copy(
                    secrets = secrets,
                    isLoading = false,
                    isLoadingMore = false,  // Ensure pagination is disabled
                    currentOffset = 0,  // Reset offset for search results
                    hasMore = false  // No pagination for search results
                )
                println("✅ Found ${secrets.size} secrets matching '$query'")
            }.onFailure { exception ->
                // Silently ignore cancellation - it's expected behavior
                if (exception is CancellationException) {
                    println("⏸️  Search cancelled (typing in progress)")
                    return@onFailure
                }

                // Log actual errors
                val error = exception.message ?: "Unknown error"
                state = state.copy(
                    isLoading = false,
                    isLoadingMore = false,
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
                // Notify other components about the change
                SecretChangeNotifier.notifyRefresh()
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
                // Notify other components about the change
                SecretChangeNotifier.notifySecretUpdated(request.secretId)
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
                // Notify other components about the deletion
                SecretChangeNotifier.notifySecretDeleted(secretId)
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

    /**
     * Show share secret dialog
     */
    fun showShareDialog(secret: SecretEntry) {
        state = state.copy(
            showShareDialog = true,
            selectedSecret = secret,
            secretShares = emptyList(),
            isLoadingShares = false
        )
        // Automatically load existing shares when dialog opens
        loadSecretShares(secret.id)
    }

    /**
     * Hide share secret dialog
     */
    fun hideShareDialog() {
        state = state.copy(
            showShareDialog = false,
            selectedSecret = null,
            secretShares = emptyList(),
            isLoadingShares = false
        )
    }

    /**
     * Load all shares for a specific secret
     */
    fun loadSecretShares(secretId: String) {
        state = state.copy(isLoadingShares = true)

        scope.launch {
            val result = SecretService.getSecretShares(secretId)

            result.onSuccess { shares ->
                state = state.copy(
                    secretShares = shares,
                    isLoadingShares = false
                )
                println("✅ Loaded ${shares.size} shares for secret $secretId")
            }.onFailure { exception ->
                val error = exception.message ?: "Unknown error"
                state = state.copy(
                    isLoadingShares = false,
                    errorMessage = error
                )
                println("❌ Failed to load secret shares: $error")
            }
        }
    }

    /**
     * Share a secret with a user or role
     */
    fun shareSecret(request: ShareSecretRequest) {
        state = state.copy(isOperationInProgress = true)

        scope.launch {
            val result = SecretService.shareSecret(request)

            result.onSuccess {
                println("✅ Successfully shared secret ${request.secretId}")
                state = state.copy(isOperationInProgress = false)
                // Reload shares to show the new share
                loadSecretShares(request.secretId)
            }.onFailure { exception ->
                val error = exception.message ?: "Unknown error"
                state = state.copy(
                    isOperationInProgress = false,
                    errorMessage = error
                )
                println("❌ Failed to share secret: $error")
            }
        }
    }

    /**
     * Revoke access to a secret from a user or role
     */
    fun unshareSecret(secretId: String, userId: String? = null, roleId: String? = null) {
        state = state.copy(isOperationInProgress = true)

        scope.launch {
            val request = UnshareSecretRequest(
                secretId = secretId,
                targetUserId = userId,
                targetRoleId = roleId
            )

            val result = SecretService.unshareSecret(request)

            result.onSuccess {
                println("✅ Successfully revoked access to secret $secretId")
                state = state.copy(isOperationInProgress = false)
                // Reload shares to reflect the change
                loadSecretShares(secretId)
            }.onFailure { exception ->
                val error = exception.message ?: "Unknown error"
                state = state.copy(
                    isOperationInProgress = false,
                    errorMessage = error
                )
                println("❌ Failed to revoke secret access: $error")
            }
        }
    }

    /**
     * Load available users for sharing (initial load)
     */
    fun loadAvailableUsers() {
        state = state.copy(isLoadingUsers = true)

        scope.launch {
            val result = UserService.getAllUsersWithRoles(limit = 10, offset = 0)

            result.onSuccess { paginatedResult ->
                state = state.copy(
                    availableUsers = paginatedResult.data,
                    isLoadingUsers = false
                )
                println("✅ Loaded ${paginatedResult.data.size} available users for sharing")
            }.onFailure { exception ->
                val error = exception.message ?: "Unknown error"
                state = state.copy(
                    isLoadingUsers = false,
                    errorMessage = error
                )
                println("❌ Failed to load available users: $error")
            }
        }
    }

    /**
     * Search users for sharing by email
     */
    fun searchUsersForSharing(query: String) {
        state = state.copy(isLoadingUsers = true)

        scope.launch {
            val result = UserService.searchUsersByEmail(
                searchQuery = query,
                limit = 10,
                offset = 0
            )

            result.onSuccess { paginatedResult ->
                state = state.copy(
                    availableUsers = paginatedResult.data,
                    isLoadingUsers = false
                )
                println("✅ Search completed: ${paginatedResult.data.size} users found for '$query'")
            }.onFailure { exception ->
                val error = exception.message ?: "Unknown error"
                state = state.copy(
                    isLoadingUsers = false,
                    errorMessage = error
                )
                println("❌ User search failed: $error")
            }
        }
    }

    /**
     * Load available roles for sharing
     */
    fun loadAvailableRoles() {
        state = state.copy(isLoadingRoles = true)

        scope.launch {
            val result = RoleCreationService.getAllRoles()

            result.onSuccess { roles ->
                state = state.copy(
                    availableRoles = roles,
                    isLoadingRoles = false
                )
                println("✅ Loaded ${roles.size} available roles for sharing")
            }.onFailure { exception ->
                val error = exception.message ?: "Unknown error"
                state = state.copy(
                    isLoadingRoles = false,
                    errorMessage = error
                )
                println("❌ Failed to load available roles: $error")
            }
        }
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
    val hasMore: Boolean = true,
    // Sharing-related state
    val showShareDialog: Boolean = false,
    val secretShares: List<SecretShareEntry> = emptyList(),
    val isLoadingShares: Boolean = false,
    // Available users and roles for sharing
    val availableUsers: List<UserWithRoles> = emptyList(),
    val availableRoles: List<RoleInfo> = emptyList(),
    val isLoadingUsers: Boolean = false,
    val isLoadingRoles: Boolean = false
)
