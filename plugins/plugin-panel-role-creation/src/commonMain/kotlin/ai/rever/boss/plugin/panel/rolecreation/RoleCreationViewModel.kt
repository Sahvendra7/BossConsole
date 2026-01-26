package ai.rever.boss.plugin.panel.rolecreation

import ai.rever.boss.plugin.api.PermissionInfoData
import ai.rever.boss.plugin.api.RoleInfoData
import ai.rever.boss.plugin.api.RoleManagementProvider
import ai.rever.boss.plugin.api.RoleWithPermissionsData
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * ViewModel for Role Creation
 *
 * Uses RoleManagementProvider interface for data operations,
 * allowing this panel to be in a separate plugin module.
 */
class RoleCreationViewModel(
    private val roleManagementProvider: RoleManagementProvider
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // State
    var state by mutableStateOf(RoleCreationState())
        private set

    init {
        loadAllRolesAndPermissions()
    }

    /**
     * Dispose the ViewModel and cancel all coroutines
     */
    fun dispose() {
        scope.cancel()
    }

    /**
     * Load all roles and permissions from database
     */
    fun loadAllRolesAndPermissions() {
        state = state.copy(isLoading = true, errorMessage = null)

        scope.launch {
            // Load roles
            val rolesResult = roleManagementProvider.getAllRoles()
            val roles = rolesResult.getOrNull() ?: emptyList()

            // Load permissions
            val permissionsResult = roleManagementProvider.getAllPermissions()
            val permissions = permissionsResult.getOrNull() ?: emptyList()

            if (rolesResult.isFailure) {
                val error = rolesResult.exceptionOrNull()?.message ?: "Unknown error"
                state = state.copy(
                    isLoading = false,
                    errorMessage = "Failed to load roles: $error"
                )
                return@launch
            }

            if (permissionsResult.isFailure) {
                val error = permissionsResult.exceptionOrNull()?.message ?: "Unknown error"
                state = state.copy(
                    isLoading = false,
                    errorMessage = "Failed to load permissions: $error"
                )
                return@launch
            }

            state = state.copy(
                allRoles = roles,
                allPermissions = permissions,
                isLoading = false
            )
        }
    }

    /**
     * Create a new role
     */
    fun createRole(roleName: String, description: String?) {
        // Client-side validation
        val validationError = roleManagementProvider.validateRoleName(roleName)
        if (validationError != null) {
            state = state.copy(errorMessage = validationError)
            return
        }

        state = state.copy(isOperationInProgress = true, errorMessage = null)

        scope.launch {
            val result = roleManagementProvider.createRole(roleName, description)

            if (result.isSuccess) {
                state = state.copy(
                    isOperationInProgress = false,
                    successMessage = "Role '$roleName' created successfully"
                )
                loadAllRolesAndPermissions()
            } else {
                val error = result.exceptionOrNull()?.message ?: "Unknown error"
                state = state.copy(
                    isOperationInProgress = false,
                    errorMessage = "Failed to create role: $error"
                )
            }
        }
    }

    /**
     * Create a new permission
     */
    fun createPermission(permissionName: String, description: String?) {
        // Client-side validation
        val validationError = roleManagementProvider.validatePermissionName(permissionName)
        if (validationError != null) {
            state = state.copy(errorMessage = validationError)
            return
        }

        state = state.copy(isOperationInProgress = true, errorMessage = null)

        scope.launch {
            val result = roleManagementProvider.createPermission(permissionName, description)

            if (result.isSuccess) {
                state = state.copy(
                    isOperationInProgress = false,
                    successMessage = "Permission '$permissionName' created successfully"
                )
                loadAllRolesAndPermissions()
            } else {
                val error = result.exceptionOrNull()?.message ?: "Unknown error"
                state = state.copy(
                    isOperationInProgress = false,
                    errorMessage = "Failed to create permission: $error"
                )
            }
        }
    }

    /**
     * Delete a role
     */
    fun deleteRole(roleName: String) {
        // Check if role is system role
        val role = state.allRoles.find { it.name == roleName }
        if (role == null) {
            state = state.copy(errorMessage = "Role not found")
            return
        }

        if (role.isSystem) {
            state = state.copy(errorMessage = "Cannot delete system role: $roleName")
            return
        }

        state = state.copy(isOperationInProgress = true, errorMessage = null)

        scope.launch {
            val result = roleManagementProvider.deleteRole(roleName)

            result.fold(
                onSuccess = {
                    state = state.copy(
                        isOperationInProgress = false,
                        successMessage = "Role \"$roleName\" deleted successfully",
                        showDeleteRoleDialog = false,
                        roleToDelete = null
                    )
                    loadAllRolesAndPermissions()
                },
                onFailure = { error ->
                    val errorMsg = error.message ?: "Unknown error"
                    state = state.copy(
                        isOperationInProgress = false,
                        errorMessage = "Failed to delete role: $errorMsg",
                        showDeleteRoleDialog = false,
                        roleToDelete = null
                    )
                }
            )
        }
    }

    /**
     * Delete a permission
     */
    fun deletePermission(permissionName: String) {
        // Check if permission is system permission
        val permission = state.allPermissions.find { it.name == permissionName }
        if (permission == null) {
            state = state.copy(errorMessage = "Permission not found")
            return
        }

        if (permission.isSystem) {
            state = state.copy(errorMessage = "Cannot delete system permission: $permissionName")
            return
        }

        state = state.copy(isOperationInProgress = true, errorMessage = null)

        scope.launch {
            val result = roleManagementProvider.deletePermission(permissionName)

            result.fold(
                onSuccess = {
                    state = state.copy(
                        isOperationInProgress = false,
                        successMessage = "Permission \"$permissionName\" deleted successfully",
                        showDeletePermissionDialog = false,
                        permissionToDelete = null
                    )
                    loadAllRolesAndPermissions()
                },
                onFailure = { error ->
                    val errorMsg = error.message ?: "Unknown error"
                    state = state.copy(
                        isOperationInProgress = false,
                        errorMessage = "Failed to delete permission: $errorMsg",
                        showDeletePermissionDialog = false,
                        permissionToDelete = null
                    )
                }
            )
        }
    }

    /**
     * Assign a permission to a role
     */
    fun assignPermission(roleName: String, permissionName: String) {
        state = state.copy(isOperationInProgress = true, errorMessage = null)

        scope.launch {
            val result = roleManagementProvider.assignPermissionToRole(roleName, permissionName)

            if (result.isSuccess) {
                state = state.copy(
                    isOperationInProgress = false,
                    successMessage = "Permission '$permissionName' assigned to role '$roleName'"
                )
                // Reload role permissions if viewing
                if (state.selectedRole?.name == roleName) {
                    loadRolePermissions(roleName)
                }
            } else {
                val error = result.exceptionOrNull()?.message ?: "Unknown error"
                state = state.copy(
                    isOperationInProgress = false,
                    errorMessage = "Failed to assign permission: $error"
                )
            }
        }
    }

    /**
     * Remove a permission from a role
     */
    fun removePermission(roleName: String, permissionName: String) {
        state = state.copy(isOperationInProgress = true, errorMessage = null)

        scope.launch {
            val result = roleManagementProvider.removePermissionFromRole(roleName, permissionName)

            if (result.isSuccess) {
                state = state.copy(
                    isOperationInProgress = false,
                    successMessage = "Permission '$permissionName' removed from role '$roleName'"
                )
                // Reload role permissions if viewing
                if (state.selectedRole?.name == roleName) {
                    loadRolePermissions(roleName)
                }
            } else {
                val error = result.exceptionOrNull()?.message ?: "Unknown error"
                state = state.copy(
                    isOperationInProgress = false,
                    errorMessage = "Failed to remove permission: $error"
                )
            }
        }
    }

    /**
     * Load permissions for a specific role
     */
    fun loadRolePermissions(roleName: String) {
        scope.launch {
            val result = roleManagementProvider.getRolePermissions(roleName)

            result.onSuccess { roleWithPerms ->
                state = state.copy(selectedRolePermissions = roleWithPerms)
            }.onFailure { exception ->
                val error = exception.message ?: "Unknown error"
                state = state.copy(errorMessage = "Failed to load role permissions: $error")
            }
        }
    }

    /**
     * Select a role to view/manage its permissions
     */
    fun selectRole(role: RoleInfoData) {
        state = state.copy(selectedRole = role)
        loadRolePermissions(role.name)
    }

    /**
     * Clear selected role
     */
    fun clearSelectedRole() {
        state = state.copy(
            selectedRole = null,
            selectedRolePermissions = null
        )
    }

    /**
     * Show create role dialog
     */
    fun showCreateRoleDialog() {
        state = state.copy(showCreateRoleDialog = true)
    }

    /**
     * Hide create role dialog
     */
    fun hideCreateRoleDialog() {
        state = state.copy(showCreateRoleDialog = false)
    }

    /**
     * Show create permission dialog
     */
    fun showCreatePermissionDialog() {
        state = state.copy(showCreatePermissionDialog = true)
    }

    /**
     * Hide create permission dialog
     */
    fun hideCreatePermissionDialog() {
        state = state.copy(showCreatePermissionDialog = false)
    }

    /**
     * Show assign permission dialog
     */
    fun showAssignPermissionDialog(role: RoleInfoData) {
        state = state.copy(
            selectedRole = role,
            showAssignPermissionDialog = true
        )
        loadRolePermissions(role.name)
    }

    /**
     * Hide assign permission dialog
     */
    fun hideAssignPermissionDialog() {
        state = state.copy(showAssignPermissionDialog = false)
    }

    /**
     * Get available permissions for a role (permissions not yet assigned)
     */
    fun getAvailablePermissionsForRole(roleName: String): List<PermissionInfoData> {
        val assignedPermissions = state.selectedRolePermissions?.permissions ?: emptyList()
        return state.allPermissions.filter { perm ->
            !assignedPermissions.contains(perm.name)
        }
    }

    /**
     * Show delete role dialog
     */
    fun showDeleteRoleDialog(role: RoleInfoData) {
        state = state.copy(
            roleToDelete = role,
            showDeleteRoleDialog = true
        )
    }

    /**
     * Hide delete role dialog
     */
    fun hideDeleteRoleDialog() {
        state = state.copy(
            roleToDelete = null,
            showDeleteRoleDialog = false
        )
    }

    /**
     * Show delete permission dialog
     */
    fun showDeletePermissionDialog(permission: PermissionInfoData) {
        state = state.copy(
            permissionToDelete = permission,
            showDeletePermissionDialog = true
        )
    }

    /**
     * Hide delete permission dialog
     */
    fun hideDeletePermissionDialog() {
        state = state.copy(
            permissionToDelete = null,
            showDeletePermissionDialog = false
        )
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

    /**
     * Clear both success and error messages
     */
    fun clearMessages() {
        state = state.copy(
            successMessage = null,
            errorMessage = null
        )
    }

    /**
     * Validate role name (for dialog validation)
     */
    fun validateRoleName(roleName: String): String? {
        return roleManagementProvider.validateRoleName(roleName)
    }

    /**
     * Validate permission name (for dialog validation)
     */
    fun validatePermissionName(permissionName: String): String? {
        return roleManagementProvider.validatePermissionName(permissionName)
    }
}

/**
 * State for Role Creation UI
 */
data class RoleCreationState(
    val allRoles: List<RoleInfoData> = emptyList(),
    val allPermissions: List<PermissionInfoData> = emptyList(),
    val selectedRole: RoleInfoData? = null,
    val selectedRolePermissions: RoleWithPermissionsData? = null,
    val isLoading: Boolean = false,
    val isOperationInProgress: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val showCreateRoleDialog: Boolean = false,
    val showCreatePermissionDialog: Boolean = false,
    val showAssignPermissionDialog: Boolean = false,
    val showDeleteRoleDialog: Boolean = false,
    val roleToDelete: RoleInfoData? = null,
    val showDeletePermissionDialog: Boolean = false,
    val permissionToDelete: PermissionInfoData? = null
)
