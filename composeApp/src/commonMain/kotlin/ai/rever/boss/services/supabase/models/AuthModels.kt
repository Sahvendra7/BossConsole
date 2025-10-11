package ai.rever.boss.services.supabase.models

import kotlinx.serialization.Serializable

/**
 * User existence information for progressive login flow
 */
data class UserExistence(
    val exists: Boolean,
    val hasPasskeys: Boolean,
    val email: String,
    val availableCredentials: List<AvailableWebAuthnCredential> = emptyList()
)

data class AvailableWebAuthnCredential(
    val credentialId: String,
    val displayName: String,
    val transports: List<String>,
    val credentialType: WebAuthnCredentialType
)

enum class WebAuthnCredentialType(val displayName: String, val icon: String) {
    PLATFORM("Touch ID / Face ID", "fingerprint"),
    CROSS_DEVICE("Authenticator App", "smartphone"),
    USB_KEY("USB Security Key", "usb"),
    NFC_KEY("NFC Security Key", "nfc"),
    UNKNOWN("Security Credential", "security")
}

/**
 * Two-factor authentication factor information
 */
@Serializable
data class TwoFactorInfo(
    val id: String,
    val friendlyName: String,
    val status: String,
    val type: String = "webauthn", // Default to webauthn for new 2FA system
    val lastVerifiedAt: Long = 0L, // Unix timestamp of last verification
    val secret: String? = null // Not used for WebAuthn-based 2FA
)

/**
 * User information
 */
data class UserInfo(
    val id: String,
    val email: String,
    val createdAt: String
)