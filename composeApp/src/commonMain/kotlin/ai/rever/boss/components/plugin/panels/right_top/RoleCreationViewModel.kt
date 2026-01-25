package ai.rever.boss.components.plugin.panels.right_top

import ai.rever.boss.services.supabase.RoleCreationService
import ai.rever.boss.services.supabase.models.RoleInfo
import ai.rever.boss.services.supabase.models.PermissionInfo
import ai.rever.boss.services.supabase.models.RoleWithPermissions
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
 * ViewModel for Role Creation
 *
 * Manages the state and business logic for creating roles and permissions dynamically.
 * Provides methods to:
 * - Load all roles and permissions from database
 * - Create new roles
 * - Create new permissions
 * - Assign permissions to roles
 * - Remove permissions from roles
 * - View role permissions
 *
 * Lifecycle management:
 * - Uses SupervisorJob to prevent child coroutine failures from cancelling the entire scope
 * - Call dispose() when the ViewModel is no longer needed to cancel all coroutines
 */
class RoleCreationViewModel {
    private val logger = BossLogger.forComponent("RoleCreationViewModel")

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // State
    var state by mutableStateOf(RoleCreationState())
        private set

    init {
        loadAllRolesAndPermissions()
    }

    /**
     * Delete a role
     * Only non-system roles can be deleted
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
            val result = RoleCreationService.deleteRole(roleName)

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
                    logger.error(LogCategory.AUTH, "Failed to delete role", mapOf("roleName" to roleName, "error" to errorMsg))
                }
            )
        }
    }

    /**
     * Delete a permission
     * Only non-system permissions can be deleted
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
            val result = RoleCreationService.deletePermission(permissionName)

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
                    logger.error(LogCategory.AUTH, "Failed to delete permission", mapOf("permissionName" to permissionName, "error" to errorMsg))
                }
            )
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
     * Load all roles and permissions from database
     */
    fun loadAllRolesAndPermissions() {
        state = state.copy(isLoading = true, errorMessage = null)

        scope.launch {
            // Load roles
            val rolesResult = RoleCreationService.getAllRoles()
            val roles = rolesResult.getOrNull() ?: emptyList()

            // Load permissions
            val permissionsResult = RoleCreationService.getAllPermissions()
            val permissions = permissionsResult.getOrNull() ?: emptyList()

            if (rolesResult.isFailure) {
                val error = rolesResult.exceptionOrNull()?.message ?: "Unknown error"
                state = state.copy(
                    isLoading = false,
                    errorMessage = "Failed to load roles: $error"
                )
                logger.error(LogCategory.AUTH, "Failed to load roles", mapOf("error" to error))
                return@launch
            }

            if (permissionsResult.isFailure) {
                val error = permissionsResult.exceptionOrNull()?.message ?: "Unknown error"
                state = state.copy(
                    isLoading = false,
                    errorMessage = "Failed to load permissions: $error"
                )
                logger.error(LogCategory.AUTH, "Failed to load permissions", mapOf("error" to error))
                return@launch
            }

            state = state.copy(
                allRoles = roles.sortedBy { it.ordinal },
                allPermissions = permissions.sortedBy { it.ordinal },
                isLoading = false
            )
            logger.debug(LogCategory.AUTH, "Loaded roles and permissions", mapOf("rolesCount" to roles.size, "permissionsCount" to permissions.size))
        }
    }

    /**
     * Create a new role
     */
    fun createRole(roleName: String, description: String?) {
        // Client-side validation
        val validationError = RoleCreationService.validateRoleName(roleName)
        if (validationError != null) {
            state = state.copy(errorMessage = validationError)
            return
        }

        state = state.copy(isOperationInProgress = true, errorMessage = null)

        scope.launch {
            val result = RoleCreationService.createRole(roleName, description)

            if (result.isSuccess) {
                logger.info(LogCategory.AUTH, "Successfully created role", mapOf("roleName" to roleName))
                state = state.copy(
                    isOperationInProgress = false,
                    successMessage = "Role '$roleName' created successfully"
                )
                // Reload to reflect changes
                loadAllRolesAndPermissions()
            } else {
                val error = result.exceptionOrNull()?.message ?: "Unknown error"
                state = state.copy(
                    isOperationInProgress = false,
                    errorMessage = "Failed to create role: $error"
                )
                logger.error(LogCategory.AUTH, "Failed to create role", mapOf("error" to error))
            }
        }
    }

    /**
     * Create a new permission
     */
    fun createPermission(permissionName: String, description: String?) {
        // Client-side validation
        val validationError = RoleCreationService.validatePermissionName(permissionName)
        if (validationError != null) {
            state = state.copy(errorMessage = validationError)
            return
        }

        state = state.copy(isOperationInProgress = true, errorMessage = null)

        scope.launch {
            val result = RoleCreationService.createPermission(permissionName, description)

            if (result.isSuccess) {
                logger.info(LogCategory.AUTH, "Successfully created permission", mapOf("permissionName" to permissionName))
                state = state.copy(
                    isOperationInProgress = false,
                    successMessage = "Permission '$permissionName' created successfully"
                )
                // Reload to reflect changes
                loadAllRolesAndPermissions()
            } else {
                val error = result.exceptionOrNull()?.message ?: "Unknown error"
                state = state.copy(
                    isOperationInProgress = false,
                    errorMessage = "Failed to create permission: $error"
                )
                logger.error(LogCategory.AUTH, "Failed to create permission", mapOf("error" to error))
            }
        }
    }

    /**
     * Assign a permission to a role
     */
    fun assignPermission(roleName: String, permissionName: String) {
        state = state.copy(isOperationInProgress = true, errorMessage = null)

        scope.launch {
            val result = RoleCreationService.assignPermissionToRole(roleName, permissionName)

            if (result.isSuccess) {
                logger.info(LogCategory.AUTH, "Successfully assigned permission", mapOf("permissionName" to permissionName, "roleName" to roleName))
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
                logger.error(LogCategory.AUTH, "Failed to assign permission", mapOf("error" to error))
            }
        }
    }

    /**
     * Remove a permission from a role
     */
    fun removePermission(roleName: String, permissionName: String) {
        state = state.copy(isOperationInProgress = true, errorMessage = null)

        scope.launch {
            val result = RoleCreationService.removePermissionFromRole(roleName, permissionName)

            if (result.isSuccess) {
                logger.info(LogCategory.AUTH, "Successfully removed permission", mapOf("permissionName" to permissionName, "roleName" to roleName))
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
                logger.error(LogCategory.AUTH, "Failed to remove permission", mapOf("error" to error))
            }
        }
    }

    /**
     * Load permissions for a specific role
     */
    fun loadRolePermissions(roleName: String) {
        scope.launch {
            val result = RoleCreationService.getRolePermissions(roleName)

            result.onSuccess { roleWithPerms ->
                state = state.copy(
                    selectedRolePermissions = roleWithPerms
                )
                logger.debug(LogCategory.AUTH, "Loaded role permissions", mapOf("roleName" to roleName, "permissionCount" to roleWithPerms.permissions.size))
            }.onFailure { exception ->
                val error = exception.message ?: "Unknown error"
                state = state.copy(errorMessage = "Failed to load role permissions: $error")
                logger.error(LogCategory.AUTH, "Failed to load role permissions", mapOf("error" to error))
            }
        }
    }

    /**
     * Select a role to view/manage its permissions
     */
    fun selectRole(role: RoleInfo) {
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
    fun showAssignPermissionDialog(role: RoleInfo) {
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
    fun getAvailablePermissionsForRole(roleName: String): List<PermissionInfo> {
        val assignedPermissions = state.selectedRolePermissions?.permissions ?: emptyList()
        return state.allPermissions.filter { perm ->
            !assignedPermissions.contains(perm.name)
        }
    }

    /**
     * Show delete role dialog
     */
    fun showDeleteRoleDialog(role: RoleInfo) {
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
    fun showDeletePermissionDialog(permission: PermissionInfo) {
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
}

/**
 * State for Role Creation UI
 */
data class RoleCreationState(
    val allRoles: List<RoleInfo> = emptyList(),
    val allPermissions: List<PermissionInfo> = emptyList(),
    val selectedRole: RoleInfo? = null,
    val selectedRolePermissions: RoleWithPermissions? = null,
    val isLoading: Boolean = false,
    val isOperationInProgress: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val showCreateRoleDialog: Boolean = false,
    val showCreatePermissionDialog: Boolean = false,
    val showAssignPermissionDialog: Boolean = false,
    val showDeleteRoleDialog: Boolean = false,
    val roleToDelete: RoleInfo? = null,
    val showDeletePermissionDialog: Boolean = false,
    val permissionToDelete: PermissionInfo? = null
)
