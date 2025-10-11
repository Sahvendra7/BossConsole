package ai.rever.boss.utils

import java.net.URLEncoder
import java.util.UUID

/**
 * Generates WebAuthn registration URLs for cross-device QR code flows
 * These URLs point to mobile-friendly WebAuthn registration pages
 */
object WebAuthnQRGenerator {
    
    private const val SUPABASE_FUNCTION_URL = "https://api.risaboss.com/functions/v1/passkey"
    
    /**
     * Generate WebAuthn registration URL for QR code scanning
     * @param challenge WebAuthn challenge string
     * @param userEmail User email (used for lookup, not internal user ID)
     * @param rpId Relying party ID (default: api.risaboss.com)
     * @param rpName Relying party name (default: BOSS)
     * @return URL string that mobile devices can open for WebAuthn registration
     */
    fun generateRegistrationQR(
        challenge: String,
        userEmail: String,
        rpId: String = "api.risaboss.com",
        rpName: String = "BOSS"
    ): String {
        // Generate unique session ID for tracking this registration flow
        val sessionId = UUID.randomUUID().toString()

        // Build WebAuthn mobile registration URL with query parameters
        // This should point to an HTML page that performs the WebAuthn ceremony
        // For now, using query parameters that a future HTML page can parse
        val params = mapOf(
            "challenge" to challenge,
            "email" to userEmail,
            "sessionId" to sessionId,
            "rpId" to rpId,
            "rpName" to rpName
        )

        val queryString = params.entries.joinToString("&") { (key, value) ->
            "$key=${URLEncoder.encode(value, "UTF-8")}"
        }

        // TODO: This should point to an HTML page at /register/mobile or similar
        // that performs the WebAuthn ceremony and calls POST /register/complete
        return "$SUPABASE_FUNCTION_URL/register/mobile?$queryString"
    }
    
    /**
     * Generate session ID for tracking cross-device flows
     * @return Unique session identifier
     */
    fun generateSessionId(): String {
        return UUID.randomUUID().toString()
    }
    
    /**
     * Generate WebAuthn authentication URL for QR code scanning  
     * For cross-device authentication via mobile device
     */
    fun generateAuthenticationQR(
        challenge: String,
        rpId: String = "api.risaboss.com",
        allowCredentials: List<String> = emptyList(),
        userEmail: String? = null,
        rpName: String = "BOSS",
        sessionId: String? = null
    ): String {
        // Use provided sessionId or generate new one for tracking this authentication flow
        val actualSessionId = sessionId ?: UUID.randomUUID().toString()

        // Build WebAuthn mobile authentication URL
        val params = mutableMapOf(
            "challenge" to challenge,
            "sessionId" to actualSessionId,
            "rpId" to rpId,
            "rpName" to rpName
        )

        // Add email if provided (for user-specific authentication)
        userEmail?.let { params["email"] = it }

        // Add credentialId if available (use first credential for cross-device auth)
        if (allowCredentials.isNotEmpty()) {
            params["credentialId"] = allowCredentials[0]
        }

        val queryString = params.entries.joinToString("&") { (key, value) ->
            "$key=${URLEncoder.encode(value, "UTF-8")}"
        }

        // TODO: This should point to an HTML page at /auth/mobile or similar
        // that performs the WebAuthn ceremony and calls POST /auth/complete
        return "$SUPABASE_FUNCTION_URL/auth/mobile?$queryString"
    }
    
    /**
     * Data class for cross-device flow tracking
     */
    data class CrossDeviceSession(
        val sessionId: String,
        val challenge: String,
        val userEmail: String,
        val rpId: String,
        val rpName: String,
        val flowType: String // "registration" or "authentication"
    )
}