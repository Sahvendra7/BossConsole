package ai.rever.boss.plugin.panel.adminrolemanagement

import ai.rever.boss.plugin.api.RoleInfoData
import ai.rever.boss.plugin.api.UserManagementProvider
import ai.rever.boss.plugin.api.UserWithRolesData
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
 * Uses UserManagementProvider interface for data operations,
 * allowing this panel to be in a separate plugin module.
 */
class AdminRoleManagementViewModel(
    private val userManagementProvider: UserManagementProvider
) {
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
            searchQuery = "",
            currentOffset = 0,
            hasMore = true
        )

        scope.launch {
            val result = userManagementProvider.getAllUsersWithRoles(limit = state.pageSize, offset = 0)

            result.onSuccess { paginatedResult ->
                val users = paginatedResult.data
                state = state.copy(
                    allUsers = users,
                    filteredUsers = users,
                    isLoading = false,
                    currentOffset = users.size,
                    hasMore = paginatedResult.hasMore
                )
            }.onFailure { exception ->
                val error = exception.message ?: "Unknown error"
                state = state.copy(
                    isLoading = false,
                    errorMessage = error
                )
            }
        }
    }

    /**
     * Load all available roles from database
     */
    fun loadAvailableRoles() {
        scope.launch {
            val result = userManagementProvider.getAllRoles()

            result.onSuccess { roles ->
                state = state.copy(availableRoles = roles)
            }.onFailure {
                state = state.copy(availableRoles = emptyList())
            }
        }
    }

    /**
     * Load more users (pagination)
     */
    fun loadMoreUsers() {
        if (state.searchQuery.isNotBlank()) {
            loadMoreSearchResults()
            return
        }

        if (state.isLoadingMore || !state.hasMore || state.isLoading) {
            return
        }

        state = state.copy(isLoadingMore = true, errorMessage = null)

        scope.launch {
            val result = userManagementProvider.getAllUsersWithRoles(
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
            }.onFailure { exception ->
                state = state.copy(
                    isLoadingMore = false,
                    errorMessage = exception.message ?: "Unknown error"
                )
            }
        }
    }

    /**
     * Search users by email (server-side)
     */
    fun searchUsers(query: String) {
        state = state.copy(
            searchQuery = query,
            isLoading = true,
            errorMessage = null,
            currentOffset = 0,
            hasMore = true
        )

        if (query.isBlank()) {
            loadAllUsers()
            return
        }

        scope.launch {
            val result = userManagementProvider.searchUsersByEmail(
                query = query,
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
            }.onFailure { exception ->
                state = state.copy(
                    isLoading = false,
                    errorMessage = exception.message ?: "Unknown error"
                )
            }
        }
    }

    private fun loadMoreSearchResults() {
        if (state.isLoadingMore || !state.hasMore || state.isLoading) {
            return
        }

        state = state.copy(isLoadingMore = true, errorMessage = null)

        scope.launch {
            val result = userManagementProvider.searchUsersByEmail(
                query = state.searchQuery,
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
            }.onFailure { exception ->
                state = state.copy(
                    isLoadingMore = false,
                    errorMessage = exception.message ?: "Unknown error"
                )
            }
        }
    }

    /**
     * Assign a role to a user
     */
    fun assignRole(userId: String, roleName: String) {
        state = state.copy(isOperationInProgress = true, errorMessage = null)

        scope.launch {
            val result = userManagementProvider.assignRole(userId, roleName)

            if (result.isSuccess) {
                state = state.copy(
                    isOperationInProgress = false,
                    successMessage = "Role $roleName assigned successfully"
                )
                loadAllUsers()
            } else {
                val error = result.exceptionOrNull()?.message ?: "Unknown error"
                state = state.copy(
                    isOperationInProgress = false,
                    errorMessage = "Failed to assign role: $error"
                )
            }
        }
    }

    /**
     * Remove a role from a user
     */
    fun removeRole(userId: String, roleName: String) {
        state = state.copy(isOperationInProgress = true, errorMessage = null)

        scope.launch {
            val result = userManagementProvider.removeRole(userId, roleName)

            if (result.isSuccess) {
                state = state.copy(
                    isOperationInProgress = false,
                    successMessage = "Role $roleName removed successfully"
                )
                loadAllUsers()
            } else {
                val error = result.exceptionOrNull()?.message ?: "Unknown error"
                state = state.copy(
                    isOperationInProgress = false,
                    errorMessage = "Failed to remove role: $error"
                )
            }
        }
    }

    /**
     * Delete a user
     */
    fun deleteUser(userId: String) {
        state = state.copy(isOperationInProgress = true, errorMessage = null)

        scope.launch {
            val result = userManagementProvider.deleteUser(userId)

            if (result.isSuccess) {
                state = state.copy(
                    isOperationInProgress = false,
                    successMessage = "User deleted successfully"
                )
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
            }
        }
    }

    fun selectUser(user: UserWithRolesData) {
        state = state.copy(selectedUser = user)
    }

    fun clearSelectedUser() {
        state = state.copy(selectedUser = null)
    }

    fun showAssignRoleDialog(user: UserWithRolesData) {
        state = state.copy(
            selectedUser = user,
            showAssignRoleDialog = true
        )
    }

    fun hideAssignRoleDialog() {
        state = state.copy(
            showAssignRoleDialog = false,
            selectedRoleToAssign = null
        )
    }

    fun showRemoveRoleDialog(user: UserWithRolesData, roleName: String) {
        state = state.copy(
            selectedUser = user,
            selectedRoleToRemove = roleName,
            showRemoveRoleDialog = true
        )
    }

    fun hideRemoveRoleDialog() {
        state = state.copy(
            showRemoveRoleDialog = false,
            selectedRoleToRemove = null
        )
    }

    fun showDeleteUserDialog(user: UserWithRolesData) {
        state = state.copy(
            selectedUser = user,
            showDeleteUserDialog = true
        )
    }

    fun hideDeleteUserDialog() {
        state = state.copy(showDeleteUserDialog = false)
    }

    fun setRoleToAssign(roleName: String) {
        state = state.copy(selectedRoleToAssign = roleName)
    }

    fun getAvailableRolesForUser(user: UserWithRolesData): List<String> {
        val userRoleNames = user.roles
        return state.availableRoles
            .map { it.name }
            .filter { roleName -> !userRoleNames.contains(roleName) }
    }

    fun clearSuccessMessage() {
        state = state.copy(successMessage = null)
    }

    fun clearErrorMessage() {
        state = state.copy(errorMessage = null)
    }
}

/**
 * State for Admin Role Management UI
 */
data class AdminRoleState(
    val allUsers: List<UserWithRolesData> = emptyList(),
    val filteredUsers: List<UserWithRolesData> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isOperationInProgress: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val selectedUser: UserWithRolesData? = null,
    val showAssignRoleDialog: Boolean = false,
    val showRemoveRoleDialog: Boolean = false,
    val showDeleteUserDialog: Boolean = false,
    val selectedRoleToAssign: String? = null,
    val selectedRoleToRemove: String? = null,
    val availableRoles: List<RoleInfoData> = emptyList(),
    val currentOffset: Int = 0,
    val pageSize: Int = 50,
    val hasMore: Boolean = true
)
