package ai.rever.boss.services.supabase.models

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
 * User information
 */
data class UserInfo(
    val id: String,
    val email: String,
    val createdAt: String
)
