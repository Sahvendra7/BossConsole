package ai.rever.boss.services.auth

import ai.rever.boss.services.passkey.PasskeyService
import ai.rever.boss.services.passkey.SupabasePasskeyService
import kotlin.time.ExperimentalTime

/**
 * PasskeyCredentialManager - Manages passkey credentials (CRUD operations)
 *
 * This service handles credential management operations:
 * - Listing user's registered passkeys
 * - Deleting passkeys
 * - Cleaning up orphaned local credentials
 *
 * Separated from PasskeyAuthService to follow Single Responsibility Principle.
 * Authentication logic (register/authenticate) remains in PasskeyAuthService.
 */
@OptIn(ExperimentalTime::class)
object PasskeyCredentialManager {
    private var passkeyService: PasskeyService? = null

    /**
     * Set the platform-specific passkey service implementation
     */
    fun setPasskeyService(service: PasskeyService) {
        passkeyService = service
        println("PasskeyCredentialManager: Platform passkey service initialized")
    }

    /**
     * Get user's registered passkeys (from both local storage and Supabase backend)
     *
     * This method:
     * 1. Fetches passkeys from Supabase backend (source of truth)
     * 2. Fetches local passkeys (Touch ID credentials in keychain)
     * 3. Cleans up any orphaned local credentials that don't exist on server
     * 4. Returns the list of server passkeys
     *
     * @return Result containing list of PasskeyInfo or error
     */
    suspend fun getUserPasskeys(): Result<List<ai.rever.boss.services.passkey.PasskeyInfo>> {
        return try {
            val currentUser = AuthStateManager.currentUser.value
                ?: return Result.failure(Exception("No user logged in"))

            val allPasskeys = mutableListOf<ai.rever.boss.services.passkey.PasskeyInfo>()

            // Get passkeys from Supabase backend (source of truth)
            val credentialsResult = SupabasePasskeyService.getUserPasskeys(currentUser.id)
            if (credentialsResult.isSuccess) {
                val credentials = credentialsResult.getOrThrow()
                val supabasePasskeyInfos = credentials.map { credential ->
                    ai.rever.boss.services.passkey.PasskeyInfo(
                        id = credential.id, // Database ID for deletion
                        credentialId = credential.credential_id,
                        displayName = credential.display_name,
                        createdAt = credential.created_at,
                        lastUsed = credential.last_used_at,
                        rpId = "api.risaboss.com",
                        transports = credential.transports
                    )
                }
                allPasskeys.addAll(supabasePasskeyInfos)
            }

            // Also get local passkeys (Touch ID credentials stored in keychain)
            // and clean up any orphaned credentials that don't exist on the server
            passkeyService?.let { service ->
                try {
                    val localPasskeysResult = service.getAvailablePasskeys()
                    if (localPasskeysResult.isSuccess) {
                        val localPasskeys = localPasskeysResult.getOrThrow()

                        // Get server credential IDs for comparison
                        val serverCredentialIds = if (credentialsResult.isSuccess) {
                            credentialsResult.getOrThrow().map { it.credential_id }.toSet()
                        } else {
                            emptySet()
                        }

                        // Clean up orphaned local credentials
                        val orphanedCredentials = localPasskeys.filter { localPasskey ->
                            !serverCredentialIds.contains(localPasskey.credentialId)
                        }

                        if (orphanedCredentials.isNotEmpty()) {
                            println("PasskeyCredentialManager: Found ${orphanedCredentials.size} orphaned local credentials, cleaning up...")
                            orphanedCredentials.forEach { orphan ->
                                try {
                                    val deleteResult = service.deletePasskey(orphan.credentialId)
                                    if (deleteResult.isSuccess) {
                                        println("PasskeyCredentialManager: Cleaned up orphaned credential: ${orphan.credentialId}")
                                    } else {
                                        println("PasskeyCredentialManager: Failed to clean up credential ${orphan.credentialId}: ${deleteResult.exceptionOrNull()?.message}")
                                    }
                                } catch (e: Exception) {
                                    println("PasskeyCredentialManager: Error cleaning up credential ${orphan.credentialId}: ${e.message}")
                                }
                            }
                        }

                        // Don't add local passkeys to the display list since server passkeys are already added
                        // Local passkeys are only used for cleanup validation
                        val validLocalPasskeys = localPasskeys.filter { localPasskey ->
                            serverCredentialIds.contains(localPasskey.credentialId)
                        }
                        println("PasskeyCredentialManager: Found ${validLocalPasskeys.size} valid local passkeys (${localPasskeys.size - validLocalPasskeys.size} orphaned credentials cleaned up)")
                    }
                } catch (e: Exception) {
                    // Ignore cancellation exceptions (when composable is disposed)
                    if (e !is java.util.concurrent.CancellationException) {
                        println("PasskeyCredentialManager: Failed to load local passkeys: ${e.message}")
                    }
                    // Don't fail the entire operation if local passkeys can't be loaded
                }
            }

            Result.success(allPasskeys)
        } catch (e: Exception) {
            // Ignore cancellation exceptions (when composable is disposed)
            if (e is java.util.concurrent.CancellationException) {
                return Result.failure(e)
            }
            println("PasskeyCredentialManager: Failed to load passkeys: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Delete a passkey
     *
     * This method:
     * 1. Deletes the passkey from Supabase backend
     * 2. Deletes the passkey from platform service (if supported)
     *
     * @param credentialId The credential ID to delete
     * @return Result indicating success or failure
     */
    suspend fun deletePasskey(credentialId: String): Result<Unit> {
        return try {
            val currentUser = AuthStateManager.currentUser.value
                ?: return Result.failure(Exception("No user logged in"))

            // Delete from Supabase backend
            val deleteResult = SupabasePasskeyService.deletePasskey(currentUser.id, credentialId)
            if (deleteResult.isFailure) {
                return Result.failure(deleteResult.exceptionOrNull() ?: Exception("Failed to delete passkey"))
            }

            // Delete from platform service if supported
            passkeyService?.deletePasskey(credentialId)

            println("PasskeyCredentialManager: Successfully deleted passkey: $credentialId")
            Result.success(Unit)
        } catch (e: Exception) {
            println("PasskeyCredentialManager: Failed to delete passkey: ${e.message}")
            Result.failure(e)
        }
    }
}
