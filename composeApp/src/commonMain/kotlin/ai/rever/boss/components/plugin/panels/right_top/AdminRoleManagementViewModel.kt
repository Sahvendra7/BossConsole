package ai.rever.boss.components.plugin.panels.right_top

import ai.rever.boss.services.supabase.RoleCreationService
import ai.rever.boss.services.supabase.RoleService
import ai.rever.boss.services.supabase.UserService
import ai.rever.boss.services.supabase.models.RoleInfo
import ai.rever.boss.services.supabase.models.UserWithRoles
import ai.rever.boss.utils.logging.BossLogger
import ai.rever.boss.utils.logging.LogCategory
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * ViewModel for Admin Role Management
 *
 * Manages the state and business logic for the admin role management panel.
 * Provides methods to:
 * - Load all users with their roles
 * - Assign roles to users
 * - Remove roles from users
 * - Filter/search users
 * - Handle loading and error states
 *
 * Lifecycle management:
 * - Uses SupervisorJob to prevent child coroutine failures from cancelling the entire scope
 * - Call dispose() when the ViewModel is no longer needed to cancel all coroutines
 */
class AdminRoleManagementViewModel {
    private val logger = BossLogger.forComponent("AdminRoleManagement")

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // State
    var state by mutableStateOf(AdminRoleState())
        private set

    init {
        loadAllUsers()
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
     * Load initial batch of users with their roles (resets pagination)
     */
    fun loadAllUsers() {
        state = state.copy(
            isLoading = true,
            errorMessage = null,
            currentOffset = 0,
            hasMore = true
        )

        scope.launch {
            val result = UserService.getAllUsersWithRoles(limit = state.pageSize, offset = 0)

            result.onSuccess { paginatedResult ->
                val users = paginatedResult.data
                state = state.copy(
                    allUsers = users,
                    filteredUsers = users,
                    isLoading = false,
                    currentOffset = users.size,
                    hasMore = paginatedResult.hasMore
                )
                logger.debug(LogCategory.AUTH, "Loaded users successfully", mapOf("count" to users.size, "hasMore" to paginatedResult.hasMore))
            }.onFailure { exception ->
                val error = exception.message ?: "Unknown error"
                state = state.copy(
                    isLoading = false,
                    errorMessage = error
                )
                logger.error(LogCategory.AUTH, "Failed to load users", error = exception)
            }
        }
    }

    /**
     * Load all available roles from database (includes dynamically created roles)
     */
    fun loadAvailableRoles() {
        scope.launch {
            val result = RoleCreationService.getAllRoles()

            result.onSuccess { roles ->
                state = state.copy(availableRoles = roles)
                logger.debug(LogCategory.AUTH, "Loaded roles", mapOf("count" to roles.size))
            }.onFailure { exception ->
                logger.error(LogCategory.AUTH, "Failed to load roles", error = exception)
                // Fallback to empty list - UI will still work
                state = state.copy(availableRoles = emptyList())
            }
        }
    }

    /**
     * Load more users (pagination) - Instagram style!
     *
     * Automatically detects if in search mode and loads appropriate results.
     */
    fun loadMoreUsers() {
        // If we're in search mode, load more search results
        if (state.searchQuery.isNotBlank()) {
            loadMoreSearchResults()
            return
        }

        // Don't load if already loading or no more data
        if (state.isLoadingMore || !state.hasMore || state.isLoading) {
            return
        }

        state = state.copy(isLoadingMore = true, errorMessage = null)

        scope.launch {
            val result = UserService.getAllUsersWithRoles(
                limit = state.pageSize,
                offset = state.currentOffset
            )

            result.onSuccess { paginatedResult ->
                val newUsers = paginatedResult.data
                val allUsers = state.allUsers + newUsers

                state = state.copy(
                    allUsers = allUsers,
                    filteredUsers = allUsers,
                    isLoadingMore = false,
                    currentOffset = state.currentOffset + newUsers.size,
                    hasMore = paginatedResult.hasMore
                )
                logger.debug(LogCategory.AUTH, "Loaded more users", mapOf("newCount" to newUsers.size, "totalCount" to allUsers.size, "hasMore" to paginatedResult.hasMore))
            }.onFailure { exception ->
                val error = exception.message ?: "Unknown error"
                state = state.copy(
                    isLoadingMore = false,
                    errorMessage = error
                )
                logger.error(LogCategory.AUTH, "Failed to load more users", error = exception)
            }
        }
    }

    /**
     * Assign a role to a user (supports dynamic roles)
     */
    fun assignRole(userId: String, roleName: String) {
        state = state.copy(isOperationInProgress = true, errorMessage = null)

        scope.launch {
            val result = RoleService.assignRoleByName(userId, roleName)

            if (result.isSuccess) {
                logger.info(LogCategory.AUTH, "Successfully assigned role", mapOf("role" to roleName, "userId" to userId))
                state = state.copy(
                    isOperationInProgress = false,
                    successMessage = "Role $roleName assigned successfully"
                )
                // Reload users to reflect changes
                loadAllUsers()
            } else {
                val error = result.exceptionOrNull()?.message ?: "Unknown error"
                state = state.copy(
                    isOperationInProgress = false,
                    errorMessage = "Failed to assign role: $error"
                )
                logger.error(LogCategory.AUTH, "Failed to assign role", mapOf("role" to roleName, "userId" to userId, "error" to error))
            }
        }
    }

    /**
     * Remove a role from a user (supports dynamic roles)
     */
    fun removeRole(userId: String, roleName: String) {
        state = state.copy(isOperationInProgress = true, errorMessage = null)

        scope.launch {
            val result = RoleService.removeRoleByName(userId, roleName)

            if (result.isSuccess) {
                logger.info(LogCategory.AUTH, "Successfully removed role", mapOf("role" to roleName, "userId" to userId))
                state = state.copy(
                    isOperationInProgress = false,
                    successMessage = "Role $roleName removed successfully"
                )
                // Reload users to reflect changes
                loadAllUsers()
            } else {
                val error = result.exceptionOrNull()?.message ?: "Unknown error"
                state = state.copy(
                    isOperationInProgress = false,
                    errorMessage = "Failed to remove role: $error"
                )
                logger.error(LogCategory.AUTH, "Failed to remove role", mapOf("role" to roleName, "userId" to userId, "error" to error))
            }
        }
    }

    /**
     * Search users by email (server-side database query)
     *
     * This performs a real-time database search across ALL users,
     * not just the ones loaded in memory.
     */
    fun searchUsers(query: String) {
        // Update search query immediately for UI responsiveness
        state = state.copy(
            searchQuery = query,
            isLoading = true,
            errorMessage = null,
            currentOffset = 0,
            hasMore = true
        )

        scope.launch {
            val result = UserService.searchUsersByEmail(
                searchQuery = query,
                limit = state.pageSize,
                offset = 0
            )

            result.onSuccess { paginatedResult ->
                val users = paginatedResult.data
                state = state.copy(
                    allUsers = users,
                    filteredUsers = users,
                    isLoading = false,
                    currentOffset = users.size,
                    hasMore = paginatedResult.hasMore
                )
                logger.debug(LogCategory.AUTH, "Search completed", mapOf("count" to users.size, "query" to query))
            }.onFailure { exception ->
                val error = exception.message ?: "Unknown error"
                state = state.copy(
                    isLoading = false,
                    errorMessage = error
                )
                logger.error(LogCategory.AUTH, "Search failed", error = exception)
            }
        }
    }

    /**
     * Load more search results (pagination for search)
     */
    private fun loadMoreSearchResults() {
        if (state.isLoadingMore || !state.hasMore || state.isLoading) {
            return
        }

        state = state.copy(isLoadingMore = true, errorMessage = null)

        scope.launch {
            val result = UserService.searchUsersByEmail(
                searchQuery = state.searchQuery,
                limit = state.pageSize,
                offset = state.currentOffset
            )

            result.onSuccess { paginatedResult ->
                val newUsers = paginatedResult.data
                val allUsers = state.allUsers + newUsers

                state = state.copy(
                    allUsers = allUsers,
                    filteredUsers = allUsers,
                    isLoadingMore = false,
                    currentOffset = state.currentOffset + newUsers.size,
                    hasMore = paginatedResult.hasMore
                )
                logger.debug(LogCategory.AUTH, "Loaded more search results", mapOf("newCount" to newUsers.size, "totalCount" to allUsers.size))
            }.onFailure { exception ->
                val error = exception.message ?: "Unknown error"
                state = state.copy(
                    isLoadingMore = false,
                    errorMessage = error
                )
                logger.error(LogCategory.AUTH, "Failed to load more search results", error = exception)
            }
        }
    }

    /**
     * Select a user for role operations
     */
    fun selectUser(user: UserWithRoles) {
        state = state.copy(selectedUser = user)
    }

    /**
     * Clear selected user
     */
    fun clearSelectedUser() {
        state = state.copy(selectedUser = null)
    }

    /**
     * Show assign role dialog
     */
    fun showAssignRoleDialog(user: UserWithRoles) {
        state = state.copy(
            selectedUser = user,
            showAssignRoleDialog = true
        )
    }

    /**
     * Hide assign role dialog
     */
    fun hideAssignRoleDialog() {
        state = state.copy(
            showAssignRoleDialog = false,
            selectedRoleToAssign = null
        )
    }

    /**
     * Show remove role dialog (supports dynamic roles)
     */
    fun showRemoveRoleDialog(user: UserWithRoles, roleName: String) {
        state = state.copy(
            selectedUser = user,
            selectedRoleToRemove = roleName,
            showRemoveRoleDialog = true
        )
    }

    /**
     * Hide remove role dialog
     */
    fun hideRemoveRoleDialog() {
        state = state.copy(
            showRemoveRoleDialog = false,
            selectedRoleToRemove = null
        )
    }

    /**
     * Show delete user confirmation dialog
     */
    fun showDeleteUserDialog(user: UserWithRoles) {
        state = state.copy(
            selectedUser = user,
            showDeleteUserDialog = true
        )
    }

    /**
     * Hide delete user dialog
     */
    fun hideDeleteUserDialog() {
        state = state.copy(
            showDeleteUserDialog = false
        )
    }

    /**
     * Delete a user (admin only, cannot delete admins)
     */
    fun deleteUser(userId: String) {
        state = state.copy(isOperationInProgress = true, errorMessage = null)

        scope.launch {
            val result = UserService.deleteUser(userId)

            if (result.isSuccess) {
                logger.info(LogCategory.AUTH, "Successfully deleted user", mapOf("userId" to userId))
                state = state.copy(
                    isOperationInProgress = false,
                    successMessage = "User deleted successfully"
                )
                // Reload users to reflect changes
                if (state.searchQuery.isBlank()) {
                    loadAllUsers()
                } else {
                    searchUsers(state.searchQuery)
                }
            } else {
                val error = result.exceptionOrNull()?.message ?: "Unknown error"
                state = state.copy(
                    isOperationInProgress = false,
                    errorMessage = "Failed to delete user: $error"
                )
                logger.error(LogCategory.AUTH, "Failed to delete user", mapOf("userId" to userId, "error" to error))
            }
        }
    }

    /**
     * Set the role to assign (supports dynamic roles)
     */
    fun setRoleToAssign(roleName: String) {
        state = state.copy(selectedRoleToAssign = roleName)
    }

    /**
     * Get available roles for a user (roles they don't have yet)
     * Returns role names from database, filtered by what user already has
     */
    fun getAvailableRolesForUser(user: UserWithRoles): List<String> {
        val userRoleNames = user.roles  // Already List<String>, no need to map
        return state.availableRoles
            .map { it.name }
            .filter { roleName -> !userRoleNames.contains(roleName) }
    }

    /**
     * Clear success message
     */
    fun clearSuccessMessage() {
        state = state.copy(successMessage = null)
    }

    /**
     * Clear error message
     */
    fun clearErrorMessage() {
        state = state.copy(errorMessage = null)
    }
}

/**
 * State for Admin Role Management UI
 */
data class AdminRoleState(
    val allUsers: List<UserWithRoles> = emptyList(),
    val filteredUsers: List<UserWithRoles> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isOperationInProgress: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val selectedUser: UserWithRoles? = null,
    val showAssignRoleDialog: Boolean = false,
    val showRemoveRoleDialog: Boolean = false,
    val showDeleteUserDialog: Boolean = false,
    val selectedRoleToAssign: String? = null,  // Changed from AppRole to String for dynamic roles
    val selectedRoleToRemove: String? = null,  // Changed from AppRole to String for dynamic roles
    val availableRoles: List<RoleInfo> = emptyList(),  // All roles from database
    // Pagination state
    val currentOffset: Int = 0,
    val pageSize: Int = 50,
    val hasMore: Boolean = true
)
