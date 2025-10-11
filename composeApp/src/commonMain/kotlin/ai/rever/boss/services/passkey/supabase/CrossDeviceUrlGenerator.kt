package ai.rever.boss.services.passkey.supabase

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Handles generation of QR code URLs for cross-device authentication flows
 */
internal object CrossDeviceUrlGenerator {
    
    private const val BASE_URL = "https://api.risaboss.com"
    private const val MOBILE_AUTH_PATH = "/mobile-auth"
    
    /**
     * Generates QR code URL for cross-device registration
     */
    fun generateRegistrationUrl(
        challenge: String,
        userId: String,
        displayName: String,
        sessionId: String? = null
    ): String {
        val params = mutableMapOf(
            "action" to "register",
            "challenge" to challenge,
            "userId" to userId,
            "displayName" to displayName
        )
        
        sessionId?.let { params["sessionId"] = it }
        
        return buildUrl(MOBILE_AUTH_PATH, params)
    }
    
    /**
     * Generates QR code URL for cross-device authentication
     */
    fun generateAuthenticationUrl(
        challenge: String,
        email: String? = null,
        sessionId: String? = null
    ): String {
        val params = mutableMapOf(
            "action" to "authenticate",
            "challenge" to challenge
        )
        
        email?.let { params["email"] = it }
        sessionId?.let { params["sessionId"] = it }
        
        return buildUrl(MOBILE_AUTH_PATH, params)
    }
    
    /**
     * Generates deep link URL for mobile app authentication
     */
    fun generateDeepLinkUrl(
        action: String,
        challenge: String,
        additionalParams: Map<String, String> = emptyMap()
    ): String {
        val params = mutableMapOf(
            "action" to action,
            "challenge" to challenge
        ) + additionalParams
        
        return buildUrl("boss://auth", params, useCustomScheme = true)
    }
    
    /**
     * Generates URL for WebAuthn credential request options
     */
    fun generateCredentialRequestUrl(
        challenge: String,
        rpId: String = "api.risaboss.com",
        allowCredentials: List<String> = emptyList()
    ): String {
        val params = mutableMapOf(
            "challenge" to challenge,
            "rpId" to rpId
        )
        
        if (allowCredentials.isNotEmpty()) {
            params["allowCredentials"] = allowCredentials.joinToString(",")
        }
        
        return buildUrl("/webauthn/options", params)
    }
    
    /**
     * Generates URL for cross-device status polling
     */
    fun generateStatusPollingUrl(
        challenge: String,
        sessionId: String? = null
    ): String {
        val params = mutableMapOf(
            "challenge" to challenge,
            "operation" to "poll_status"
        )
        
        sessionId?.let { params["sessionId"] = it }
        
        return buildUrl("/auth-status", params)
    }
    
    /**
     * Builds URL with query parameters
     */
    private fun buildUrl(
        path: String,
        params: Map<String, String>,
        useCustomScheme: Boolean = false
    ): String {
        val baseUrl = if (useCustomScheme) {
            path // For custom schemes like boss://auth, path contains the full scheme
        } else {
            "$BASE_URL$path"
        }
        
        if (params.isEmpty()) {
            return baseUrl
        }
        
        val queryString = params.entries.joinToString("&") { (key, value) ->
            "$key=${URLEncoder.encode(value, StandardCharsets.UTF_8)}"
        }
        
        return "$baseUrl?$queryString"
    }
    
    /**
     * Extracts challenge from URL parameters
     */
    fun extractChallengeFromUrl(url: String): String? {
        val queryStart = url.indexOf('?')
        if (queryStart == -1) return null
        
        val queryParams = url.substring(queryStart + 1)
            .split('&')
            .associate { param ->
                val parts = param.split('=', limit = 2)
                if (parts.size == 2) {
                    parts[0] to java.net.URLDecoder.decode(parts[1], StandardCharsets.UTF_8)
                } else {
                    parts[0] to ""
                }
            }
        
        return queryParams["challenge"]
    }
    
    /**
     * Extracts session ID from URL parameters
     */
    fun extractSessionIdFromUrl(url: String): String? {
        val queryStart = url.indexOf('?')
        if (queryStart == -1) return null
        
        val queryParams = url.substring(queryStart + 1)
            .split('&')
            .associate { param ->
                val parts = param.split('=', limit = 2)
                if (parts.size == 2) {
                    parts[0] to java.net.URLDecoder.decode(parts[1], StandardCharsets.UTF_8)
                } else {
                    parts[0] to ""
                }
            }
        
        return queryParams["sessionId"]
    }
    
    /**
     * Validates if URL is a valid cross-device authentication URL
     */
    fun isValidAuthenticationUrl(url: String): Boolean {
        return when {
            url.startsWith("$BASE_URL$MOBILE_AUTH_PATH") -> true
            url.startsWith("boss://auth") -> true
            url.contains("challenge=") -> true
            else -> false
        }
    }
}