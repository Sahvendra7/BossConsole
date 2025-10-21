package ai.rever.boss.services.supabase.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A secret entry containing website credentials and metadata
 */
@Serializable
data class SecretEntry(
    val id: String,
    val website: String,
    val username: String,
    val password: String,  // Decrypted on client
    val notes: String? = null,
    @SerialName("expiration_date")
    val expirationDate: String? = null,
    val tags: List<String> = emptyList(),
    val metadata: SecretMetadata? = null,
    @SerialName("created_at")
    val createdAt: String,
    @SerialName("updated_at")
    val updatedAt: String
)

/**
 * Metadata for a secret (2FA information)
 */
@Serializable
data class SecretMetadata(
    @SerialName("twofa_enabled")
    val twofaEnabled: Boolean = false,
    @SerialName("twofa_type")
    val twofaType: String? = null,  // 'app', 'sms', 'email', 'hardware'
    @SerialName("recovery_codes")
    val recoveryCodes: List<String> = emptyList()
)

/**
 * Request to create a new secret
 */
data class CreateSecretRequest(
    val website: String,
    val username: String,
    val password: String,
    val notes: String? = null,
    val expirationDate: String? = null,
    val tags: List<String> = emptyList(),
    val twofaEnabled: Boolean = false,
    val twofaType: String? = null,
    val recoveryCodes: List<String> = emptyList()
) {
    /**
     * Validate the request data
     */
    fun validate(): Result<Unit> {
        if (website.isBlank()) {
            return Result.failure(IllegalArgumentException("Website cannot be empty"))
        }
        if (username.isBlank()) {
            return Result.failure(IllegalArgumentException("Username cannot be empty"))
        }
        if (password.isBlank()) {
            return Result.failure(IllegalArgumentException("Password cannot be empty"))
        }
        if (twofaEnabled && twofaType == null) {
            return Result.failure(IllegalArgumentException("2FA type must be specified when 2FA is enabled"))
        }
        if (twofaType != null && twofaType !in listOf("app", "sms", "email", "hardware")) {
            return Result.failure(IllegalArgumentException("Invalid 2FA type: $twofaType"))
        }
        return Result.success(Unit)
    }
}

/**
 * Request to update an existing secret
 */
data class UpdateSecretRequest(
    val secretId: String,
    val website: String,
    val username: String,
    val password: String,
    val notes: String? = null,
    val expirationDate: String? = null,
    val tags: List<String> = emptyList(),
    val twofaEnabled: Boolean = false,
    val twofaType: String? = null,
    val recoveryCodes: List<String> = emptyList()
) {
    /**
     * Validate the request data
     */
    fun validate(): Result<Unit> {
        if (secretId.isBlank()) {
            return Result.failure(IllegalArgumentException("Secret ID cannot be empty"))
        }
        if (website.isBlank()) {
            return Result.failure(IllegalArgumentException("Website cannot be empty"))
        }
        if (username.isBlank()) {
            return Result.failure(IllegalArgumentException("Username cannot be empty"))
        }
        if (password.isBlank()) {
            return Result.failure(IllegalArgumentException("Password cannot be empty"))
        }
        if (twofaEnabled && twofaType == null) {
            return Result.failure(IllegalArgumentException("2FA type must be specified when 2FA is enabled"))
        }
        if (twofaType != null && twofaType !in listOf("app", "sms", "email", "hardware")) {
            return Result.failure(IllegalArgumentException("Invalid 2FA type: $twofaType"))
        }
        return Result.success(Unit)
    }
}

/**
 * Paginated result for secret queries
 */
data class PaginatedSecrets(
    val data: List<SecretEntry>,
    val hasMore: Boolean,
    val total: Int? = null
)
