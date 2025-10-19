package ai.rever.boss.services.supabase

import ai.rever.boss.services.supabase.models.*
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.rpc
import kotlinx.serialization.json.*

/**
 * Service for managing user roles and permissions
 *
 * ⚠️ CRITICAL SECURITY MODEL ⚠️
 * ================================
 * JWT claims parsed on the client are for UI/UX convenience ONLY.
 *
 * - JWT signature is NOT verified on the client
 * - Client-parsed claims are UNTRUSTED for authorization decisions
 * - All security enforcement happens server-side via:
 *   1. Row Level Security (RLS) policies in PostgreSQL
 *   2. Database functions that verify roles before actions
 *   3. Supabase Auth verifies JWT signatures server-side
 *
 * Client-side role checks (isAdmin, hasRole) are for:
 * - Showing/hiding UI elements
 * - Optimistic UI updates
 * - Reducing unnecessary server calls
 *
 * They are NOT for:
 * - Granting actual access to resources
 * - Bypassing server-side checks
 * - Making security decisions
 *
 * Features:
 * - Assign/remove roles (admin only, server-enforced)
 * - Query user roles (RLS-protected)
 * - Check permissions (informational only)
 * - Parse JWT role claims (for UI convenience)
 *
 * Usage:
 * ```kotlin
 * // Get current user's roles from JWT (UI only!)
 * val claims = RoleService.parseRoleClaimsFromSession(session)
 * if (claims.isAdmin) {
 *     // Show admin UI elements
 *     // Actual admin actions still protected by RLS
 * }
 *
 * // Assign admin role (server verifies you're admin via RLS)
 * val result = RoleService.assignRole(userId, AppRole.ADMIN)
 * ```
 */
object RoleService {

    /**
     * Get the Supabase client
     */
    private val client
        get() = SupabaseConfig.client

    /**
     * Parse role claims from the current session's JWT
     */
    fun parseRoleClaimsFromSession(session: io.github.jan.supabase.auth.user.UserSession?): RoleClaims? {
        if (session == null) return null

        return try {
            // Decode JWT claims from access token
            val accessToken = session.accessToken
            val claims = decodeJWTClaims(accessToken)

            // Debug: print raw claims
            println("🔍 [RBAC DEBUG] JWT Claims parsed:")
            println("  user_role: ${claims["user_role"]}")
            println("  user_roles: ${claims["user_roles"]}")
            println("  is_admin: ${claims["is_admin"]}")

            val roleClaims = RoleClaims.fromJWTClaims(claims)
            println("🔍 [RBAC DEBUG] RoleClaims created: $roleClaims")

            roleClaims
        } catch (e: Exception) {
            println("Failed to parse role claims: ${e.message}")
            null
        }
    }

    /**
     * Decode JWT token to extract claims using proper JSON parsing
     *
     * ⚠️ SECURITY WARNING: This does NOT verify the JWT signature.
     *
     * These claims are for UI/UX convenience only. The JWT signature has already
     * been verified by Supabase when the token was issued, but we don't re-verify
     * it here on the client.
     *
     * All authorization decisions MUST be made server-side via:
     * - RLS policies that check auth.jwt() claims
     * - Database functions that verify roles before mutations
     * - Supabase API endpoints that validate tokens
     *
     * Never trust client-parsed JWT claims for security decisions.
     * Client can be modified, debugged, or have malicious code injected.
     *
     * This parsing is safe for:
     * - Showing/hiding UI elements
     * - Displaying user role badges
     * - Optimistic UI updates
     * - Reducing unnecessary API calls
     *
     * Uses kotlinx.serialization.json for reliable JSON parsing.
     */
    private fun decodeJWTClaims(jwt: String): Map<String, Any?> {
        return try {
            val parts = jwt.split(".")
            if (parts.size != 3) {
                throw IllegalArgumentException("Invalid JWT format")
            }

            // Decode the payload (second part)
            val payload = parts[1]
            val decodedBytes = java.util.Base64.getUrlDecoder().decode(payload)
            val jsonString = decodedBytes.decodeToString()

            println("🔍 [RBAC DEBUG] JWT Payload (first 500 chars): ${jsonString.take(500)}")

            // Parse JSON using kotlinx.serialization (secure and reliable)
            val jsonObject = Json.parseToJsonElement(jsonString).jsonObject

            // Extract RBAC claims
            mapOf(
                "user_role" to jsonObject["user_role"]?.jsonPrimitive?.content,
                "user_roles" to jsonObject["user_roles"]?.jsonArray?.map {
                    it.jsonPrimitive.content
                },
                "is_admin" to jsonObject["is_admin"]?.jsonPrimitive?.content?.toBooleanStrictOrNull()
            )
        } catch (e: Exception) {
            println("Failed to decode JWT: ${e.message}")
            emptyMap()
        }
    }

    /**
     * Get all roles for a specific user
     */
    suspend fun getUserRoles(userId: String): Result<List<UserRole>> {
        return try {
            val roles = client.from("user_roles")
                .select(Columns.ALL) {
                    filter {
                        eq("user_id", userId)
                    }
                }
                .decodeList<UserRole>()

            Result.success(roles)
        } catch (e: Exception) {
            println("Failed to get user roles: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Check if a user has a specific role
     */
    suspend fun userHasRole(userId: String, role: AppRole): Result<Boolean> {
        return try {
            val roles = client.from("user_roles")
                .select(Columns.list("id")) {
                    filter {
                        eq("user_id", userId)
                        eq("role", role.value)
                    }
                }
                .decodeList<UserRole>()

            Result.success(roles.isNotEmpty())
        } catch (e: Exception) {
            println("Failed to check user role: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Check if a user is an admin
     */
    suspend fun isUserAdmin(userId: String): Result<Boolean> {
        return userHasRole(userId, AppRole.ADMIN)
    }

    /**
     * Assign a role to a user (admin only)
     * Calls the assign_role_to_user() PostgreSQL function via RPC
     */
    suspend fun assignRole(targetUserId: String, role: AppRole): Result<Unit> {
        return try {
            // Call the PostgreSQL function via RPC (not Edge Functions!)
            client.postgrest.rpc(
                function = "assign_role_to_user",
                parameters = buildJsonObject {
                    put("target_user_id", targetUserId)
                    put("target_role", role.value)
                }
            )

            Result.success(Unit)
        } catch (e: Exception) {
            println("Failed to assign role: ${e.message}")
            Result.failure(Exception("Failed to assign role: ${e.message}"))
        }
    }

    /**
     * Remove a role from a user (admin only)
     * Calls the remove_role_from_user() PostgreSQL function via RPC
     */
    suspend fun removeRole(targetUserId: String, role: AppRole): Result<Unit> {
        return try {
            // Call the PostgreSQL function via RPC (not Edge Functions!)
            client.postgrest.rpc(
                function = "remove_role_from_user",
                parameters = buildJsonObject {
                    put("target_user_id", targetUserId)
                    put("target_role", role.value)
                }
            )

            Result.success(Unit)
        } catch (e: Exception) {
            println("Failed to remove role: ${e.message}")
            Result.failure(Exception("Failed to remove role: ${e.message}"))
        }
    }

    /**
     * Get all available roles from the database
     */
    suspend fun getAllRoles(): Result<List<AppRole>> {
        // Since roles are defined in an enum, we return the Kotlin enum values
        return Result.success(AppRole.entries)
    }

    /**
     * Get all role permissions
     */
    suspend fun getRolePermissions(role: AppRole): Result<List<RolePermission>> {
        return try {
            val permissions = client.from("role_permissions")
                .select(Columns.ALL) {
                    filter {
                        eq("role", role.value)
                    }
                }
                .decodeList<RolePermission>()

            Result.success(permissions)
        } catch (e: Exception) {
            println("Failed to get role permissions: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Check if current user can perform an action
     * This checks against the role_permissions table
     */
    suspend fun canPerformAction(userId: String, permission: AppPermission): Result<Boolean> {
        return try {
            // Get user's roles
            val userRolesResult = getUserRoles(userId)
            if (userRolesResult.isFailure) {
                return Result.failure(userRolesResult.exceptionOrNull()!!)
            }

            val userRoles = userRolesResult.getOrNull() ?: emptyList()
            val roleValues = userRoles.mapNotNull { it.getRoleEnum() }

            // Check if any of the user's roles have the required permission
            for (role in roleValues) {
                val permissionsResult = getRolePermissions(role)
                if (permissionsResult.isSuccess) {
                    val permissions = permissionsResult.getOrNull() ?: emptyList()
                    if (permissions.any { it.permission == permission.value }) {
                        return Result.success(true)
                    }
                }
            }

            Result.success(false)
        } catch (e: Exception) {
            println("Failed to check permission: ${e.message}")
            Result.failure(e)
        }
    }
}
