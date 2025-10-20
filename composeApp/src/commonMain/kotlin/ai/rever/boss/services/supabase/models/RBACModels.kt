package ai.rever.boss.services.supabase.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.datetime.Instant

/**
 * Application roles matching the database app_role enum
 *
 * To add plugin-specific roles:
 * 1. Add enum value here
 * 2. Run SQL: ALTER TYPE public.app_role ADD VALUE 'new_role';
 */
enum class AppRole(val value: String) {
    USER("user"),
    ADMIN("admin");

    companion object {
        fun fromString(value: String): AppRole? {
            return values().firstOrNull { it.value == value }
        }

        fun fromStringOrDefault(value: String?, default: AppRole = USER): AppRole {
            return value?.let { fromString(it) } ?: default
        }
    }

    override fun toString(): String = value
}

/**
 * Application permissions matching the database app_permission enum
 */
enum class AppPermission(val value: String) {
    USERS_READ("users.read"),
    USERS_WRITE("users.write"),
    WORKSPACES_READ("workspaces.read"),
    WORKSPACES_WRITE("workspaces.write"),
    WORKSPACES_DELETE("workspaces.delete"),
    PLUGINS_INSTALL("plugins.install"),
    PLUGINS_MANAGE("plugins.manage"),
    ADMIN_ACCESS("admin.access");

    companion object {
        fun fromString(value: String): AppPermission? {
            return values().firstOrNull { it.value == value }
        }
    }

    override fun toString(): String = value
}

/**
 * User role assignment from the user_roles table
 */
@Serializable
data class UserRole(
    val id: String,
    @SerialName("user_id")
    val userId: String,
    val role: String,
    @SerialName("assigned_by")
    val assignedBy: String? = null,
    @SerialName("assigned_at")
    val assignedAt: String,
    @SerialName("created_at")
    val createdAt: String
) {
    fun getRoleEnum(): AppRole? = AppRole.fromString(role)
}

/**
 * Role permission mapping from the role_permissions table
 */
@Serializable
data class RolePermission(
    val id: String,
    val role: String,
    val permission: String,
    @SerialName("created_at")
    val createdAt: String
) {
    fun getRoleEnum(): AppRole? = AppRole.fromString(role)
    fun getPermissionEnum(): AppPermission? = AppPermission.fromString(permission)
}

/**
 * JWT claims containing user role information
 * These claims are injected by the custom_access_token_hook
 */
data class RoleClaims(
    val userRole: AppRole,
    val userRoles: List<AppRole>,
    val isAdmin: Boolean
) {
    companion object {
        /**
         * Parse role claims from JWT token claims map
         */
        fun fromJWTClaims(claims: Map<String, Any?>): RoleClaims? {
            val userRoleStr = claims["user_role"] as? String
            val userRolesArray = claims["user_roles"] as? List<*>
            val isAdmin = claims["is_admin"] as? Boolean ?: false

            val primaryRole = userRoleStr?.let { AppRole.fromString(it) } ?: AppRole.USER

            val roles = userRolesArray?.mapNotNull {
                (it as? String)?.let { roleStr -> AppRole.fromString(roleStr) }
            } ?: listOf(primaryRole)

            return RoleClaims(
                userRole = primaryRole,
                userRoles = roles,
                isAdmin = isAdmin
            )
        }
    }

    /**
     * Check if user has a specific role
     */
    fun hasRole(role: AppRole): Boolean = userRoles.contains(role)

    /**
     * Check if user has any of the specified roles
     */
    fun hasAnyRole(vararg roles: AppRole): Boolean = roles.any { hasRole(it) }

    /**
     * Check if user has all of the specified roles
     */
    fun hasAllRoles(vararg roles: AppRole): Boolean = roles.all { hasRole(it) }
}

/**
 * Request/Response DTOs for role management operations
 */
@Serializable
data class AssignRoleRequest(
    val userId: String,
    val role: String
)

@Serializable
data class RemoveRoleRequest(
    val userId: String,
    val role: String
)

@Serializable
data class RoleAssignmentResult(
    val success: Boolean,
    val message: String? = null
)

/**
 * User information with roles
 */
data class UserWithRoles(
    val userId: String,
    val email: String,
    val roles: List<AppRole>,
    val isAdmin: Boolean
) {
    val primaryRole: AppRole
        get() = roles.firstOrNull() ?: AppRole.USER
}

/**
 * Role information from database (includes all roles, even dynamically created ones)
 * Now uses table-based schema with full CRUD support
 */
data class RoleInfo(
    val id: String? = null,              // UUID from roles table (null for backward compatibility)
    val name: String,
    val description: String? = null,
    val isSystem: Boolean = false,       // System roles (user, admin) cannot be deleted
    val createdAt: String? = null,       // Timestamp when role was created
    val updatedAt: String? = null,       // Timestamp when role was last updated
    val ordinal: Int = 0                 // For backward compatibility (deprecated)
) {
    /**
     * Check if this role can be deleted
     */
    fun canDelete(): Boolean = !isSystem

    /**
     * Get display name (for UI)
     */
    fun getDisplayName(): String = name.replaceFirstChar { it.uppercase() }
}

/**
 * Permission information from database (includes all permissions, even dynamically created ones)
 * Now uses table-based schema with full CRUD support
 */
data class PermissionInfo(
    val id: String? = null,              // UUID from permissions table (null for backward compatibility)
    val name: String,
    val description: String? = null,
    val isSystem: Boolean = false,       // System permissions cannot be deleted
    val createdAt: String? = null,       // Timestamp when permission was created
    val updatedAt: String? = null,       // Timestamp when permission was last updated
    val ordinal: Int = 0                 // For backward compatibility (deprecated)
) {
    /**
     * Check if this permission can be deleted
     */
    fun canDelete(): Boolean = !isSystem

    /**
     * Get domain and action parts (e.g., "users.read" -> "users" and "read")
     */
    fun getDomain(): String = name.substringBefore(".")
    fun getAction(): String = name.substringAfter(".")
}

/**
 * Role with its assigned permissions
 */
data class RoleWithPermissions(
    val roleName: String,
    val permissions: List<String> = emptyList()
) {
    fun hasPermission(permission: String): Boolean = permissions.contains(permission)
}
