package ai.rever.boss.services.passkey

import ai.rever.boss.services.passkey.desktop.*
import ai.rever.boss.services.supabase.CrossDeviceAuthenticationRequired
import ai.rever.boss.services.supabase.getSupabaseFunctionUrl
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.URLEncoder
import java.util.*

/**
 * Desktop implementation of PasskeyService using component-based architecture
 * Coordinates biometric authentication, WebAuthn operations, and cross-device flows
 * Supports macOS Touch ID, Windows Hello, and cross-device browser authentication
 */
class DesktopPasskeyService : PasskeyService {
    
    private val _passkeyState = MutableStateFlow<PasskeyState>(PasskeyState.Idle)
    override val passkeyState: StateFlow<PasskeyState> = _passkeyState.asStateFlow()
    
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    // Component dependencies
    private val biometricAuthProvider = BiometricAuthProvider()
    private val credentialManager = PasskeyCredentialManager(biometricAuthProvider)
    private val browserManager = CrossDeviceBrowserManager()
    private val dataMapper = DesktopPasskeyDataMapper()
    
    init {
        // Show enhanced capabilities after a short delay
        scope.launch {
            delay(1000)
            browserManager.showEnhancedCapabilities()
        }
    }

    override suspend fun isPasskeySupported(): Boolean {
        return try {
            val isSupported = biometricAuthProvider.isBiometricSupported()
            println("DesktopPasskeyService: Passkey support check result: $isSupported (platform: ${biometricAuthProvider.getCurrentPlatform()})")
            isSupported
        } catch (e: Exception) {
            println("DesktopPasskeyService: Error checking passkey support: ${e.message}")
            false
        }
    }
    
    override suspend fun hasPasskeys(): Boolean {
        return credentialManager.hasPasskeys()
    }
    
    override suspend fun registerPasskey(
        userId: String,
        displayName: String,
        challenge: ByteArray,
        rpId: String
    ): Result<PasskeyRegistration> = withContext(Dispatchers.Main) {
        try {
            _passkeyState.value = PasskeyState.Loading
            
            val challengeB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(challenge)
            println("DesktopPasskeyService: Starting WebAuthn registration via browser for user: $userId")
            
            // Build server WebAuthn registration URL using RESTful endpoint
            val sessionId = UUID.randomUUID().toString()
            val baseUrl = getSupabaseFunctionUrl()
            val registrationUrl = "$baseUrl/passkey/register/mobile?" +
                "challenge=${URLEncoder.encode(challengeB64, "UTF-8")}&" +
                "email=${URLEncoder.encode(displayName, "UTF-8")}&" +
                "sessionId=${URLEncoder.encode(sessionId, "UTF-8")}&" +
                "rpId=${URLEncoder.encode(rpId, "UTF-8")}&" +
                "rpName=${URLEncoder.encode("BOSS", "UTF-8")}"
            
            // Open the server WebAuthn page in system browser
            println("DesktopPasskeyService: Opening WebAuthn registration page: $registrationUrl")
            _passkeyState.value = PasskeyState.UserGestureRequired
            
            // Use browser manager to open URL with fallbacks
            val browserResult = browserManager.openInSystemBrowser(registrationUrl)
            if (browserResult.isFailure) {
                throw browserResult.exceptionOrNull() ?: Exception("Failed to open browser")
            }
            
            println("DesktopPasskeyService: Browser opened for WebAuthn registration")
            
            // Wait for the browser WebAuthn registration to complete
            // The server will handle the complete registration flow
            _passkeyState.value = PasskeyState.Success("browser-registration-initiated")
            
            // Return a special result that tells AuthService not to call completeRegistration
            Result.success(dataMapper.createBrowserPasskeyRegistration(sessionId))
            
        } catch (e: Exception) {
            println("DesktopPasskeyService: Browser registration error: ${e.message}")
            val errorCode = dataMapper.mapPlatformError(e)
            _passkeyState.value = PasskeyState.Error(e.message ?: "Registration failed", errorCode)
            Result.failure(e)
        }
    }
    
    
    override suspend fun authenticateWithPasskey(
        challenge: ByteArray,
        allowedCredentials: List<String>?,
        rpId: String,
        userEmail: String,
        sessionId: String?,
        allowedCredentialTransports: Map<String, List<String>>?
    ): Result<PasskeyAssertion> = withContext(Dispatchers.Main) {
        try {
            _passkeyState.value = PasskeyState.Loading
            
            println("DesktopPasskeyService: Starting biometric authentication on ${biometricAuthProvider.getCurrentPlatform()}...")
            _passkeyState.value = PasskeyState.UserGestureRequired
            
            // Determine authentication method based on credential transports instead of ID patterns
            val actualCredentialId = if (!allowedCredentials.isNullOrEmpty()) {
                allowedCredentials.first()
            } else {
                "unknown-credential"
            }
            
            val currentPlatform = biometricAuthProvider.getCurrentPlatform()
            val transports = allowedCredentialTransports?.get(actualCredentialId) ?: emptyList()
            
            println("DesktopPasskeyService: CredentialId: $actualCredentialId, Platform: $currentPlatform, Transports: $transports")
            
            println("DesktopPasskeyService: Using browser WebAuthn for all passkey authentication")

            // Always use browser WebAuthn for passkey authentication - this is the correct approach
            // The browser handles the choice between Touch ID, security keys, or cross-device flow
            val crossDeviceSessionId = sessionId ?: UUID.randomUUID().toString()
            val challengeB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(challenge)
            val baseUrl = getSupabaseFunctionUrl()
            val authUrl = "$baseUrl/passkey/auth/mobile?" +
                "challenge=${URLEncoder.encode(challengeB64, "UTF-8")}&" +
                "email=${URLEncoder.encode(userEmail, "UTF-8")}&" +
                "sessionId=${URLEncoder.encode(crossDeviceSessionId, "UTF-8")}&" +
                "credentialId=${URLEncoder.encode(actualCredentialId, "UTF-8")}&" +
                "rpId=${URLEncoder.encode(rpId, "UTF-8")}"

            // Auto-open browser for authentication (same as registration flow)
            // Use browser manager to open URL with fallbacks
            val browserResult = browserManager.openInSystemBrowser(authUrl)
            if (browserResult.isFailure) {
                // If browser opening fails, fall back to QR code flow
                throw CrossDeviceAuthenticationRequired(
                    qrCodeUrl = authUrl,
                    challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(challenge),
                    sessionId = crossDeviceSessionId,
                    message = "Complete authentication in browser - Touch ID will be available there"
                )
            }

            // Throw exception to trigger polling flow
            throw CrossDeviceAuthenticationRequired(
                qrCodeUrl = authUrl,
                challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(challenge),
                sessionId = crossDeviceSessionId,
                message = "Complete authentication in browser - Touch ID will be available there"
            )
            
        } catch (e: Exception) {
            println("DesktopPasskeyService: Biometric authentication error: ${e.message}")
            val errorCode = dataMapper.mapPlatformError(e)
            _passkeyState.value = PasskeyState.Error(e.message ?: "Authentication failed", errorCode)
            Result.failure(e)
        }
    }
    
    
    override suspend fun getAvailablePasskeys(): Result<List<PasskeyInfo>> {
        return credentialManager.getAvailablePasskeys()
    }
    
    
    override suspend fun deletePasskey(credentialId: String): Result<Unit> {
        return credentialManager.deletePasskey(credentialId)
    }
    
    override suspend fun isUserPresent(): Boolean {
        // Check if user gesture is available (simplified implementation)
        return true
    }

}
