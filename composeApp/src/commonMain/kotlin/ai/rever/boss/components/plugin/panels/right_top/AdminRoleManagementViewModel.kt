package ai.rever.boss.components.plugin.panels.right_top

import ai.rever.boss.services.supabase.RoleService
import ai.rever.boss.services.supabase.UserService
import ai.rever.boss.services.supabase.models.AppRole
import ai.rever.boss.services.supabase.models.UserWithRoles
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

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // State
    var state by mutableStateOf(AdminRoleState())
        private set

    init {
        loadAllUsers()
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

            if (result.isSuccess) {
                val paginatedResult = result.getOrNull()!!
                val users = paginatedResult.data
                state = state.copy(
                    allUsers = users,
                    filteredUsers = users,
                    isLoading = false,
                    currentOffset = users.size,
                    hasMore = paginatedResult.hasMore
                )
                println("✅ Loaded ${users.size} users successfully (hasMore: ${paginatedResult.hasMore})")
            } else {
                val error = result.exceptionOrNull()?.message ?: "Unknown error"
                state = state.copy(
                    isLoading = false,
                    errorMessage = error
                )
                println("❌ Failed to load users: $error")
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

            if (result.isSuccess) {
                val paginatedResult = result.getOrNull()!!
                val newUsers = paginatedResult.data
                val allUsers = state.allUsers + newUsers

                state = state.copy(
                    allUsers = allUsers,
                    filteredUsers = allUsers,
                    isLoadingMore = false,
                    currentOffset = state.currentOffset + newUsers.size,
                    hasMore = paginatedResult.hasMore
                )
                println("✅ Loaded ${newUsers.size} more users (total: ${allUsers.size}, hasMore: ${paginatedResult.hasMore})")
            } else {
                val error = result.exceptionOrNull()?.message ?: "Unknown error"
                state = state.copy(
                    isLoadingMore = false,
                    errorMessage = error
                )
                println("❌ Failed to load more users: $error")
            }
        }
    }

    /**
     * Assign a role to a user
     */
    fun assignRole(userId: String, role: AppRole) {
        state = state.copy(isOperationInProgress = true, errorMessage = null)

        scope.launch {
            val result = RoleService.assignRole(userId, role)

            if (result.isSuccess) {
                println("✅ Successfully assigned role ${role.value} to user $userId")
                state = state.copy(
                    isOperationInProgress = false,
                    successMessage = "Role ${role.value} assigned successfully"
                )
                // Reload users to reflect changes
                loadAllUsers()
            } else {
                val error = result.exceptionOrNull()?.message ?: "Unknown error"
                state = state.copy(
                    isOperationInProgress = false,
                    errorMessage = "Failed to assign role: $error"
                )
                println("❌ Failed to assign role: $error")
            }
        }
    }

    /**
     * Remove a role from a user
     */
    fun removeRole(userId: String, role: AppRole) {
        state = state.copy(isOperationInProgress = true, errorMessage = null)

        scope.launch {
            val result = RoleService.removeRole(userId, role)

            if (result.isSuccess) {
                println("✅ Successfully removed role ${role.value} from user $userId")
                state = state.copy(
                    isOperationInProgress = false,
                    successMessage = "Role ${role.value} removed successfully"
                )
                // Reload users to reflect changes
                loadAllUsers()
            } else {
                val error = result.exceptionOrNull()?.message ?: "Unknown error"
                state = state.copy(
                    isOperationInProgress = false,
                    errorMessage = "Failed to remove role: $error"
                )
                println("❌ Failed to remove role: $error")
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

            if (result.isSuccess) {
                val paginatedResult = result.getOrNull()!!
                val users = paginatedResult.data
                state = state.copy(
                    allUsers = users,
                    filteredUsers = users,
                    isLoading = false,
                    currentOffset = users.size,
                    hasMore = paginatedResult.hasMore
                )
                println("✅ Search completed: ${users.size} users found for '$query'")
            } else {
                val error = result.exceptionOrNull()?.message ?: "Unknown error"
                state = state.copy(
                    isLoading = false,
                    errorMessage = error
                )
                println("❌ Search failed: $error")
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

            if (result.isSuccess) {
                val paginatedResult = result.getOrNull()!!
                val newUsers = paginatedResult.data
                val allUsers = state.allUsers + newUsers

                state = state.copy(
                    allUsers = allUsers,
                    filteredUsers = allUsers,
                    isLoadingMore = false,
                    currentOffset = state.currentOffset + newUsers.size,
                    hasMore = paginatedResult.hasMore
                )
                println("✅ Loaded ${newUsers.size} more search results (total: ${allUsers.size})")
            } else {
                val error = result.exceptionOrNull()?.message ?: "Unknown error"
                state = state.copy(
                    isLoadingMore = false,
                    errorMessage = error
                )
                println("❌ Failed to load more search results: $error")
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
     * Show remove role dialog
     */
    fun showRemoveRoleDialog(user: UserWithRoles, role: AppRole) {
        state = state.copy(
            selectedUser = user,
            selectedRoleToRemove = role,
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
                println("✅ Successfully deleted user $userId")
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
                println("❌ Failed to delete user: $error")
            }
        }
    }

    /**
     * Set the role to assign
     */
    fun setRoleToAssign(role: AppRole) {
        state = state.copy(selectedRoleToAssign = role)
    }

    /**
     * Get available roles for a user (roles they don't have yet)
     */
    fun getAvailableRolesForUser(user: UserWithRoles): List<AppRole> {
        return AppRole.entries.filter { role ->
            !user.roles.contains(role)
        }
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
    val selectedRoleToAssign: AppRole? = null,
    val selectedRoleToRemove: AppRole? = null,
    // Pagination state
    val currentOffset: Int = 0,
    val pageSize: Int = 50,
    val hasMore: Boolean = true
)
