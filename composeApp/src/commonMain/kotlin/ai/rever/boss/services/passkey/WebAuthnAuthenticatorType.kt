package ai.rever.boss.services.passkey

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * WebAuthn authenticator types supported by BOSS
 */
enum class WebAuthnAuthenticatorType(
    val displayName: String,
    val description: String,
    val icon: ImageVector,
    val securityLevel: SecurityLevel,
    val benefits: List<String>,
    val requirements: List<String>,
    val authenticatorAttachment: String? = null,
    val userVerification: String = "preferred",
    val residentKey: String = "preferred"
) {
    /**
     * Platform authenticators (Touch ID, Windows Hello, built-in biometrics)
     */
    PLATFORM(
        displayName = "Touch ID / Windows Hello",
        description = "Use your device's built-in biometric authentication",
        icon = Icons.Default.Fingerprint,
        securityLevel = SecurityLevel.HIGHEST,
        benefits = listOf(
            "Fastest authentication",
            "Most convenient",
            "Hardware-backed security",
            "No additional hardware needed"
        ),
        requirements = listOf(
            "Device with Touch ID, Face ID, or Windows Hello",
            "Biometric enrollment on your device"
        ),
        authenticatorAttachment = "platform",
        userVerification = "required"
    ),

    /**
     * Authenticator apps (1Password, Bitwarden, etc.) via WebAuthn cross-device flow
     */
    AUTHENTICATOR_APP(
        displayName = "Authenticator Apps",
        description = "Use mobile authenticator apps via QR code scanning",
        icon = Icons.Default.PhoneAndroid,
        securityLevel = SecurityLevel.HIGH,
        benefits = listOf(
            "Works with mobile password managers",
            "Cross-device authentication",
            "App-based passkey storage",
            "Backup and sync capabilities"
        ),
        requirements = listOf(
            "Mobile device with compatible app",
            "QR code scanning capability",
            "Examples: 1Password, Bitwarden, Chrome mobile"
        ),
        authenticatorAttachment = "cross-platform",
        userVerification = "required",
        residentKey = "required"
    ),

    /**
     * USB Security Keys (YubiKey, etc.)
     */
    USB_SECURITY_KEY(
        displayName = "USB Security Keys",
        description = "Physical security keys connected via USB",
        icon = Icons.Default.Usb,
        securityLevel = SecurityLevel.HIGHEST,
        benefits = listOf(
            "Highest security level",
            "Phishing resistant", 
            "Works offline",
            "Portable across devices"
        ),
        requirements = listOf(
            "FIDO2/WebAuthn compatible security key",
            "Examples: YubiKey 5 series, SoloKeys",
            "USB port available"
        ),
        authenticatorAttachment = "cross-platform",
        userVerification = "discouraged"
    ),

    /**
     * NFC Security Keys and NFC-enabled devices
     */
    NFC_SECURITY_KEY(
        displayName = "NFC Authentication",
        description = "NFC-enabled security keys or smartphone tap authentication",
        icon = Icons.Default.Nfc,
        securityLevel = SecurityLevel.HIGH,
        benefits = listOf(
            "Contactless authentication",
            "Works with smartphones",
            "No cables needed",
            "Fast and convenient"
        ),
        requirements = listOf(
            "NFC-enabled device",
            "NFC security key or compatible smartphone",
            "NFC functionality enabled"
        ),
        authenticatorAttachment = "cross-platform",
        userVerification = "preferred"
    ),

    /**
     * Cross-device authentication (QR code → smartphone)
     */
    CROSS_DEVICE(
        displayName = "Cross-device Authentication",
        description = "Use another device (like your smartphone) to authenticate",
        icon = Icons.Default.QrCode,
        securityLevel = SecurityLevel.MEDIUM,
        benefits = listOf(
            "Use existing device as authenticator",
            "No additional hardware needed",
            "Leverage smartphone biometrics",
            "Backup authentication method"
        ),
        requirements = listOf(
            "Secondary device with WebAuthn support",
            "QR code scanning capability",
            "Both devices connected to internet"
        ),
        authenticatorAttachment = "cross-platform",
        userVerification = "required"
    );

    /**
     * Check if this authenticator type is likely available on the current platform
     */
    fun isLikelyAvailable(): Boolean {
        return when (this) {
            PLATFORM -> true // Most modern devices have some form of biometric auth
            AUTHENTICATOR_APP -> true // Software-based, widely available
            USB_SECURITY_KEY -> true // Hardware-dependent but commonly supported
            NFC_SECURITY_KEY -> true // Many devices have NFC
            CROSS_DEVICE -> true // Relies on external devices
        }
    }

    /**
     * Get the AuthenticatorSelectionCriteria for this type
     */
    fun getSelectionCriteria(): AuthenticatorSelectionCriteria {
        return AuthenticatorSelectionCriteria(
            authenticatorAttachment = authenticatorAttachment ?: "cross-platform",
            residentKey = residentKey,
            requireResidentKey = residentKey == "required",
            userVerification = userVerification
        )
    }
    
    /**
     * Determines if this authenticator type should use cross-device flow (QR code)
     */
    fun requiresCrossDeviceFlow(): Boolean {
        return when (this) {
            PLATFORM -> false // Use platform authenticator directly
            AUTHENTICATOR_APP -> true // Show QR code for mobile apps
            USB_SECURITY_KEY -> false // Direct USB interaction
            NFC_SECURITY_KEY -> false // Direct NFC interaction  
            CROSS_DEVICE -> true // QR code by design
        }
    }
    
    /**
     * Get the flow type for this authenticator
     */
    fun getFlowType(): AuthenticatorFlowType {
        return when (this) {
            PLATFORM -> AuthenticatorFlowType.PLATFORM_DIRECT
            AUTHENTICATOR_APP -> AuthenticatorFlowType.CROSS_DEVICE_QR
            USB_SECURITY_KEY -> AuthenticatorFlowType.HARDWARE_DIRECT
            NFC_SECURITY_KEY -> AuthenticatorFlowType.HARDWARE_DIRECT
            CROSS_DEVICE -> AuthenticatorFlowType.CROSS_DEVICE_QR
        }
    }
}

/**
 * Authentication flow types
 */
enum class AuthenticatorFlowType {
    PLATFORM_DIRECT,    // Touch ID, Windows Hello - direct platform call
    CROSS_DEVICE_QR,    // Show QR code for mobile scanning
    HARDWARE_DIRECT     // USB/NFC keys - direct hardware interaction
}

/**
 * Security levels for different authenticator types
 */
enum class SecurityLevel(
    val displayName: String,
    val color: String
) {
    MEDIUM("Medium", "#FFA726"),
    HIGH("High", "#66BB6A"), 
    HIGHEST("Highest", "#42A5F5")
}