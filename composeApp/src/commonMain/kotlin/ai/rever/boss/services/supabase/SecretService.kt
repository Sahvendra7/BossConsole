package ai.rever.boss.services.supabase

import ai.rever.boss.services.supabase.models.CreateSecretRequest
import ai.rever.boss.services.supabase.models.PaginatedSecrets
import ai.rever.boss.services.supabase.models.SecretEntry
import ai.rever.boss.services.supabase.models.UpdateSecretRequest
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

/**
 * Service for managing secrets (website credentials)
 *
 * This service provides methods to:
 * - Get user secrets (with decryption on server)
 * - Search secrets by website or username
 * - Create new secrets with encryption
 * - Update existing secrets
 * - Delete secrets
 *
 * Security:
 * - All operations require authenticated user
 * - RLS policies ensure users only access their own secrets
 * - Passwords are encrypted server-side using pgcrypto
 * - Decryption happens on server, decrypted password sent over HTTPS
 *
 * Usage:
 * ```kotlin
 * // Get all secrets for current user
 * val result = SecretService.getUserSecrets(limit = 50, offset = 0)
 *
 * // Create a new secret
 * val createResult = SecretService.createSecret(
 *     CreateSecretRequest(
 *         website = "github.com",
 *         username = "user@example.com",
 *         password = "securepassword123",
 *         tags = listOf("work", "development")
 *     )
 * )
 * ```
 */
object SecretService {

    private val client
        get() = SupabaseConfig.client

    /**
     * Get user secrets with pagination
     *
     * @param limit Maximum number of secrets to return
     * @param offset Number of secrets to skip
     * @return Paginated result with decrypted secrets
     */
    suspend fun getUserSecrets(limit: Int = 50, offset: Int = 0): Result<PaginatedSecrets> {
        return try {
            println("🔍 [SecretService.getUserSecrets] Starting with limit=$limit, offset=$offset")

            val params = buildJsonObject {
                put("p_limit", limit)
                put("p_offset", offset)
            }

            println("🔍 [SecretService.getUserSecrets] Parameters: $params")
            println("🔍 [SecretService.getUserSecrets] Calling RPC function: get_user_secrets")

            val postgrestResult = client.postgrest.rpc(
                function = "get_user_secrets",
                parameters = params
            )

            println("✅ [SecretService.getUserSecrets] RPC call completed")
            println("🔍 [SecretService.getUserSecrets] Response data length: ${postgrestResult.data.length} chars")
            println("🔍 [SecretService.getUserSecrets] Response preview: ${postgrestResult.data.take(200)}")

            val jsonElement = Json.parseToJsonElement(postgrestResult.data)
            val secrets = Json.decodeFromJsonElement<List<SecretEntry>>(jsonElement)

            // Check if there might be more results
            val hasMore = secrets.size >= limit

            println("✅ [SecretService.getUserSecrets] Successfully parsed ${secrets.size} secrets, hasMore=$hasMore")

            Result.success(
                PaginatedSecrets(
                    data = secrets,
                    hasMore = hasMore
                )
            )
        } catch (e: Exception) {
            println("❌ [SecretService.getUserSecrets] Exception caught:")
            println("   Type: ${e::class.simpleName}")
            println("   Message: ${e.message}")
            println("   Cause: ${e.cause?.message}")
            println("   Full error: ${e.toString()}")
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * Search user secrets by website or username
     *
     * @param query Search query (matches website or username)
     * @param limit Maximum number of secrets to return
     * @param offset Number of secrets to skip
     * @return Paginated result with matching secrets
     */
    suspend fun searchSecrets(
        query: String,
        limit: Int = 50,
        offset: Int = 0
    ): Result<PaginatedSecrets> {
        return try {
            val params = buildJsonObject {
                put("p_query", query)
                put("p_limit", limit)
                put("p_offset", offset)
            }

            val postgrestResult = client.postgrest.rpc(
                function = "search_user_secrets",
                parameters = params
            )

            val jsonElement = Json.parseToJsonElement(postgrestResult.data)
            val secrets = Json.decodeFromJsonElement<List<SecretEntry>>(jsonElement)

            // Check if there might be more results
            val hasMore = secrets.size >= limit

            Result.success(
                PaginatedSecrets(
                    data = secrets,
                    hasMore = hasMore
                )
            )
        } catch (e: Exception) {
            println("❌ SecretService.searchSecrets failed: ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * Create a new secret
     *
     * @param request Secret creation request with website, username, password, etc.
     * @return Result with success/failure
     */
    suspend fun createSecret(request: CreateSecretRequest): Result<Unit> {
        return try {
            println("🔍 [SecretService.createSecret] Starting...")
            println("🔍 [SecretService.createSecret] Request: website=${request.website}, username=${request.username}")

            // Validate request
            request.validate().getOrElse {
                println("❌ [SecretService.createSecret] Validation failed: ${it.message}")
                return Result.failure(it)
            }
            println("✅ [SecretService.createSecret] Validation passed")

            val params = buildJsonObject {
                put("p_website", request.website)
                put("p_username", request.username)
                put("p_password", request.password)
                if (request.notes != null) {
                    put("p_notes", request.notes)
                }
                if (request.expirationDate != null) {
                    put("p_expiration_date", request.expirationDate)
                }
                if (request.tags.isNotEmpty()) {
                    put("p_tags", JsonArray(request.tags.map { JsonPrimitive(it) }))
                }
                put("p_twofa_enabled", request.twofaEnabled)
                if (request.twofaType != null) {
                    put("p_twofa_type", request.twofaType)
                }
                if (request.recoveryCodes.isNotEmpty()) {
                    put("p_recovery_codes", JsonArray(request.recoveryCodes.map { JsonPrimitive(it) }))
                }
            }

            println("🔍 [SecretService.createSecret] Parameters built: ${params.toString().take(200)}...")
            println("🔍 [SecretService.createSecret] Calling RPC function: create_secret")

            val postgrestResult = client.postgrest.rpc(
                function = "create_secret",
                parameters = params
            )

            println("✅ [SecretService.createSecret] RPC call completed")
            println("🔍 [SecretService.createSecret] Response data: ${postgrestResult.data.take(500)}")

            val jsonElement = Json.parseToJsonElement(postgrestResult.data)
            val result = Json.decodeFromJsonElement<RpcResponse>(jsonElement)

            println("🔍 [SecretService.createSecret] Parsed response: success=${result.success}, message=${result.message}, error=${result.error}")

            if (result.success) {
                println("✅ [SecretService.createSecret] Secret created successfully")
                Result.success(Unit)
            } else {
                println("❌ [SecretService.createSecret] Server returned error: ${result.error}")
                Result.failure(Exception(result.error ?: "Failed to create secret"))
            }
        } catch (e: Exception) {
            println("❌ [SecretService.createSecret] Exception caught:")
            println("   Type: ${e::class.simpleName}")
            println("   Message: ${e.message}")
            println("   Cause: ${e.cause?.message}")

            // Try to extract more details from PostgrestException
            if (e::class.simpleName?.contains("Postgrest") == true) {
                println("   🔍 This is a Postgrest exception - checking for more details...")
                println("   Stack trace:")
                e.printStackTrace()
            }

            // Print full error details
            println("   Full error toString: ${e.toString()}")

            Result.failure(e)
        }
    }

    /**
     * Update an existing secret
     *
     * @param request Secret update request with all fields
     * @return Result with success/failure
     */
    suspend fun updateSecret(request: UpdateSecretRequest): Result<Unit> {
        return try {
            // Validate request
            request.validate().getOrElse { return Result.failure(it) }

            val params = buildJsonObject {
                put("p_secret_id", request.secretId)
                put("p_website", request.website)
                put("p_username", request.username)
                put("p_password", request.password)
                if (request.notes != null) {
                    put("p_notes", request.notes)
                }
                if (request.expirationDate != null) {
                    put("p_expiration_date", request.expirationDate)
                }
                if (request.tags.isNotEmpty()) {
                    put("p_tags", JsonArray(request.tags.map { JsonPrimitive(it) }))
                }
                put("p_twofa_enabled", request.twofaEnabled)
                if (request.twofaType != null) {
                    put("p_twofa_type", request.twofaType)
                }
                if (request.recoveryCodes.isNotEmpty()) {
                    put("p_recovery_codes", JsonArray(request.recoveryCodes.map { JsonPrimitive(it) }))
                }
            }

            val postgrestResult = client.postgrest.rpc(
                function = "update_secret",
                parameters = params
            )

            val jsonElement = Json.parseToJsonElement(postgrestResult.data)
            val result = Json.decodeFromJsonElement<RpcResponse>(jsonElement)

            if (result.success) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(result.error ?: "Failed to update secret"))
            }
        } catch (e: Exception) {
            println("❌ SecretService.updateSecret failed: ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * Delete a secret
     *
     * @param secretId ID of the secret to delete
     * @return Result with success/failure
     */
    suspend fun deleteSecret(secretId: String): Result<Unit> {
        return try {
            val params = buildJsonObject {
                put("p_secret_id", secretId)
            }

            val postgrestResult = client.postgrest.rpc(
                function = "delete_secret",
                parameters = params
            )

            val jsonElement = Json.parseToJsonElement(postgrestResult.data)
            val result = Json.decodeFromJsonElement<RpcResponse>(jsonElement)

            if (result.success) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(result.error ?: "Failed to delete secret"))
            }
        } catch (e: Exception) {
            println("❌ SecretService.deleteSecret failed: ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * RPC response structure for create/update/delete operations
     */
    @Serializable
    private data class RpcResponse(
        val success: Boolean,
        val error: String? = null,
        val message: String? = null,
        val secret_id: String? = null  // ID of created/updated secret
    )
}
