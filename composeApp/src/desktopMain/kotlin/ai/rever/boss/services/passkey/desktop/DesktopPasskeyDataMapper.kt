package ai.rever.boss.services.passkey.desktop

import ai.rever.boss.services.passkey.*
import kotlinx.serialization.json.Json
import java.net.URLEncoder
import java.security.interfaces.ECPublicKey
import java.util.*

/**
 * Handles desktop-specific data transformation and mapping operations
 * Converts between different data formats and creates platform-specific URLs and HTML
 */
class DesktopPasskeyDataMapper(
    private val webAuthnProtocolHandler: WebAuthnProtocolHandler,
    private val biometricAuthProvider: BiometricAuthProvider
) {
    
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    
    /**
     * Create platform-specific PasskeyRegistration for browser flows
     */
    fun createBrowserPasskeyRegistration(sessionId: String): PasskeyRegistration {
        return PasskeyRegistration(
            credentialId = "browser-registration-${sessionId}",
            publicKey = "",
            attestationObject = "",
            clientDataJSON = "",
            transports = listOf("internal", "hybrid")
        )
    }
    
    /**
     * Create platform-specific PasskeyInfo from credential ID
     */
    fun createPasskeyInfo(
        credentialId: String,
        rpId: String = "api.risaboss.com"
    ): PasskeyInfo {
        val displayName = when {
            credentialId.startsWith("touchid-credential-") && biometricAuthProvider.isMacOS() -> "Touch ID (macOS)"
            credentialId.startsWith("windowshello-credential-") && biometricAuthProvider.isWindows() -> "Windows Hello"
            biometricAuthProvider.isMacOS() -> "Touch ID (macOS)"
            biometricAuthProvider.isWindows() -> "Windows Hello"
            else -> "Desktop Credential"
        }
        
        return PasskeyInfo(
            credentialId = credentialId,
            displayName = displayName,
            createdAt = System.currentTimeMillis(),
            lastUsed = null,
            rpId = rpId,
            transports = listOf("internal")
        )
    }
    
    /**
     * Create HTML page with WebAuthn JavaScript for registration
     */
    fun createWebAuthnRegistrationPage(
        userId: String,
        displayName: String,
        challenge: ByteArray,
        rpId: String
    ): String {
        val challengeB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(challenge)
        val userIdB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(userId.toByteArray())
        
        return """
        <!DOCTYPE html>
        <html>
        <head>
            <title>BOSS WebAuthn Registration</title>
            <meta charset="UTF-8">
        </head>
        <body>
            <h1>BOSS Passkey Registration</h1>
            <p>Creating your passkey for $displayName...</p>
            <p>Please use your biometric authentication when prompted.</p>
            
            <script>
            async function registerWithWebAuthn() {
                try {
                    console.log('Starting WebAuthn registration...');
                    
                    const options = {
                        challenge: Uint8Array.from(atob('$challengeB64'), c => c.charCodeAt(0)),
                        rp: {
                            id: '$rpId',
                            name: 'BOSS'
                        },
                        user: {
                            id: Uint8Array.from(atob('$userIdB64'), c => c.charCodeAt(0)),
                            name: '$userId',
                            displayName: '$displayName'
                        },
                        pubKeyCredParams: [
                            { alg: -7, type: 'public-key' },  // ES256
                            { alg: -257, type: 'public-key' } // RS256
                        ],
                        authenticatorSelection: {
                            authenticatorAttachment: 'platform',
                            userVerification: 'preferred',
                            requireResidentKey: false
                        },
                        attestation: 'none',
                        timeout: 60000
                    };
                    
                    console.log('WebAuthn registration options:', options);
                    
                    const credential = await navigator.credentials.create({
                        publicKey: options
                    });
                    
                    if (!credential) {
                        throw new Error('No credential created');
                    }
                    
                    console.log('WebAuthn registration successful:', credential);
                    
                    // Convert ArrayBuffers to base64 for transport
                    const result = {
                        credentialId: btoa(String.fromCharCode(...new Uint8Array(credential.rawId))),
                        publicKey: btoa(String.fromCharCode(...new Uint8Array(credential.response.getPublicKey()))),
                        attestationObject: btoa(String.fromCharCode(...new Uint8Array(credential.response.attestationObject))),
                        clientDataJSON: btoa(String.fromCharCode(...new Uint8Array(credential.response.clientDataJSON))),
                        transports: credential.response.getTransports ? credential.response.getTransports() : ['internal']
                    };
                    
                    return result;
                    
                } catch (error) {
                    console.error('WebAuthn registration failed:', error);
                    throw error;
                }
            }
            
            // Auto-start registration when page loads
            window.addEventListener('load', function() {
                console.log('WebAuthn registration page loaded, starting registration...');
                registerWithWebAuthn()
                    .then(result => {
                        console.log('Registration completed:', result);
                        window.webAuthnResult = result;
                    })
                    .catch(error => {
                        console.error('Registration failed:', error);
                        window.webAuthnError = error;
                    });
            });
            </script>
        </body>
        </html>
        """.trimIndent()
    }
    
    /**
     * Create HTML page with WebAuthn JavaScript for authentication
     */
    fun createWebAuthnAuthenticationPage(
        challenge: ByteArray,
        allowedCredentials: List<String>?,
        rpId: String
    ): String {
        val challengeB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(challenge)
        val allowedCreds = allowedCredentials?.joinToString(",") { "\"$it\"" } ?: ""
        
        return """
        <!DOCTYPE html>
        <html>
        <head>
            <title>BOSS WebAuthn Authentication</title>
            <meta charset="UTF-8">
        </head>
        <body>
            <h1>BOSS Authentication</h1>
            <p>Please use your biometric authentication...</p>
            
            <script>
            async function authenticateWithWebAuthn() {
                try {
                    console.log('Starting WebAuthn authentication...');
                    
                    const options = {
                        challenge: Uint8Array.from(atob('$challengeB64'), c => c.charCodeAt(0)),
                        timeout: 60000,
                        rpId: '$rpId',
                        allowCredentials: ${if (allowedCreds.isNotEmpty()) "[$allowedCreds].map(id => ({id: Uint8Array.from(atob(id), c => c.charCodeAt(0)), type: 'public-key'}))" else "[]"},
                        userVerification: 'preferred'
                    };
                    
                    console.log('WebAuthn options:', options);
                    
                    const credential = await navigator.credentials.get({
                        publicKey: options
                    });
                    
                    if (!credential) {
                        throw new Error('No credential returned');
                    }
                    
                    console.log('WebAuthn successful:', credential);
                    
                    // Convert ArrayBuffers to base64 for transport
                    const result = {
                        credentialId: btoa(String.fromCharCode(...new Uint8Array(credential.rawId))),
                        authenticatorData: btoa(String.fromCharCode(...new Uint8Array(credential.response.authenticatorData))),
                        signature: btoa(String.fromCharCode(...new Uint8Array(credential.response.signature))),
                        clientDataJSON: btoa(String.fromCharCode(...new Uint8Array(credential.response.clientDataJSON))),
                        userHandle: credential.response.userHandle ? btoa(String.fromCharCode(...new Uint8Array(credential.response.userHandle))) : null
                    };
                    
                    return result;
                    
                } catch (error) {
                    console.error('WebAuthn authentication failed:', error);
                    throw error;
                }
            }
            
            // Auto-start authentication when page loads
            window.addEventListener('load', function() {
                console.log('WebAuthn page loaded, starting authentication...');
                authenticateWithWebAuthn()
                    .then(result => {
                        console.log('Authentication completed:', result);
                        window.webAuthnResult = result;
                    })
                    .catch(error => {
                        console.error('Authentication failed:', error);
                        window.webAuthnError = error;
                    });
            });
            </script>
        </body>
        </html>
        """.trimIndent()
    }
    
    /**
     * Create URL-encoded registration parameters
     */
    fun createRegistrationUrlParams(
        userId: String,
        displayName: String,
        challenge: String,
        rpId: String,
        sessionId: String
    ): Map<String, String> {
        return mapOf(
            "challenge" to URLEncoder.encode(challenge, "UTF-8"),
            "userId" to URLEncoder.encode(userId, "UTF-8"),
            "email" to URLEncoder.encode(displayName, "UTF-8"),
            "displayName" to URLEncoder.encode(displayName, "UTF-8"),
            "sessionId" to URLEncoder.encode(sessionId, "UTF-8"),
            "rpId" to URLEncoder.encode(rpId, "UTF-8")
        )
    }
    
    /**
     * Create URL-encoded authentication parameters
     */
    fun createAuthenticationUrlParams(
        challenge: String,
        credentialId: String,
        rpId: String,
        userEmail: String,
        sessionId: String
    ): Map<String, String> {
        return mapOf(
            "challenge" to URLEncoder.encode(challenge, "UTF-8"),
            "sessionId" to URLEncoder.encode(sessionId, "UTF-8"),
            "rpId" to URLEncoder.encode(rpId, "UTF-8"),
            "rpName" to "BOSS",
            "email" to URLEncoder.encode(userEmail, "UTF-8"),
            "credentialId" to URLEncoder.encode(credentialId, "UTF-8")
        )
    }
    
    /**
     * Convert URL parameters to query string
     */
    fun paramsToQueryString(params: Map<String, String>, baseUrl: String): String {
        val queryString = params.entries.joinToString("&") { "${it.key}=${it.value}" }
        return "$baseUrl?$queryString"
    }
    
    /**
     * Extract platform-specific error information
     */
    fun mapPlatformError(error: Throwable): PasskeyErrorCode {
        val message = error.message?.lowercase() ?: ""
        return when {
            message.contains("cancelled") || message.contains("user") -> PasskeyErrorCode.USER_CANCELLED
            message.contains("unavailable") || message.contains("not supported") -> PasskeyErrorCode.NOT_SUPPORTED
            message.contains("timeout") -> PasskeyErrorCode.TIMEOUT_ERROR
            message.contains("invalid") -> PasskeyErrorCode.INVALID_STATE
            else -> PasskeyErrorCode.UNKNOWN_ERROR
        }
    }
    
    /**
     * Create platform-specific error message
     */
    fun createPlatformErrorMessage(error: Throwable): String {
        val platformName = biometricAuthProvider.getPlatformBiometricName()
        val baseMessage = error.message ?: "Authentication failed"
        
        return when {
            baseMessage.contains("unavailable") -> "$platformName not available on this device"
            baseMessage.contains("cancelled") -> "$platformName authentication was cancelled"
            baseMessage.contains("timeout") -> "$platformName authentication timed out"
            else -> "$platformName authentication failed: $baseMessage"
        }
    }
}