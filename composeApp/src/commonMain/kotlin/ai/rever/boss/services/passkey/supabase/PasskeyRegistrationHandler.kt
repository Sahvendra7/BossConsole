package ai.rever.boss.services.passkey.supabase

import ai.rever.boss.services.passkey.*
import io.ktor.client.statement.*

/**
 * Handles passkey registration flow operations
 */
internal object PasskeyRegistrationHandler {
    
    /**
     * Request passkey registration challenge
     */
    suspend fun requestChallenge(
        userId: String,
        displayName: String,
        authenticatorSelection: AuthenticatorSelectionCriteria? = null
    ): Result<PasskeyChallenge> {
        return try {
            println("Requesting passkey registration challenge for user: $userId")
            println("Debug - userId: '$userId', displayName: '$displayName'")
            
            val challenge = PasskeyDataMapper.generateChallenge()
            val requestData = PasskeyDataMapper.createRegistrationRequest(
                userId = userId,
                displayName = displayName,
                challenge = challenge,
                authenticatorSelection = authenticatorSelection
            )
            
            println("Debug - Request data: userId='${requestData.userId}', displayName='${requestData.displayName}', challenge='${requestData.challenge.take(10)}...')")
            
            // Call Edge Function for registration challenge
            val response = SupabaseApiClient.invokeRegistrationChallenge(requestData)
            
            val responseText = response.bodyAsText()
            val parsedChallenge = PasskeyDataMapper.parsePasskeyChallenge(
                responseText = responseText,
                userId = userId,
                displayName = displayName,
                authenticatorSelection = authenticatorSelection
            )
            
            Result.success(parsedChallenge)
        } catch (e: Exception) {
            println("Failed to request registration challenge: ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        }
    }
    
    /**
     * Complete passkey registration
     */
    suspend fun completeRegistration(
        userId: String,
        registration: PasskeyRegistration,
        challenge: String
    ): Result<PasskeyCredential> {
        return try {
            println("Completing passkey registration for user: $userId")
            
            val registrationData = PasskeyDataMapper.createRegistrationData(
                userId = userId,
                registration = registration,
                challenge = challenge
            )
            
            // Call Edge Function for registration completion
            val response = SupabaseApiClient.completeRegistration(registrationData)
            
            val responseText = response.bodyAsText()
            println("Registration completion response: $responseText")
            println("Response type: ${response::class.qualifiedName}")
            println("Response status: ${response.status}")
            
            val result = PasskeyDataMapper.parseRegistrationResponse(responseText)
            
            when {
                result.isSuccess -> {
                    println("Passkey registration completed successfully for user: $userId, credential: ${registration.credentialId}")
                    result
                }
                else -> {
                    println("Passkey registration failed: ${result.exceptionOrNull()?.message}")
                    result
                }
            }
        } catch (e: Exception) {
            println("Failed to complete registration: ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        }
    }
    
    /**
     * Validate registration request parameters
     */
    fun validateRegistrationRequest(
        userId: String,
        displayName: String
    ): Result<Unit> {
        return when {
            userId.isBlank() -> Result.failure(
                IllegalArgumentException("User ID cannot be blank")
            )
            displayName.isBlank() -> Result.failure(
                IllegalArgumentException("Display name cannot be blank")
            )
            userId.length > 64 -> Result.failure(
                IllegalArgumentException("User ID cannot exceed 64 characters")
            )
            displayName.length > 64 -> Result.failure(
                IllegalArgumentException("Display name cannot exceed 64 characters")
            )
            else -> Result.success(Unit)
        }
    }
    
    /**
     * Validate registration completion data
     */
    fun validateRegistrationCompletion(
        userId: String,
        registration: PasskeyRegistration,
        challenge: String
    ): Result<Unit> {
        return when {
            userId.isBlank() -> Result.failure(
                IllegalArgumentException("User ID cannot be blank")
            )
            challenge.isBlank() -> Result.failure(
                IllegalArgumentException("Challenge cannot be blank")
            )
            registration.credentialId.isBlank() -> Result.failure(
                IllegalArgumentException("Credential ID cannot be blank")
            )
            registration.attestationObject.isBlank() -> Result.failure(
                IllegalArgumentException("Attestation object cannot be blank")
            )
            registration.clientDataJSON.isBlank() -> Result.failure(
                IllegalArgumentException("Client data JSON cannot be blank")
            )
            registration.transports.isEmpty() -> Result.failure(
                IllegalArgumentException("Transport methods cannot be empty")
            )
            else -> Result.success(Unit)
        }
    }
    
    /**
     * Check if authenticator selection is valid
     */
    fun validateAuthenticatorSelection(
        authenticatorSelection: AuthenticatorSelectionCriteria?
    ): Result<Unit> {
        if (authenticatorSelection == null) {
            return Result.success(Unit)
        }
        
        return when {
            authenticatorSelection.authenticatorAttachment != null && 
            authenticatorSelection.authenticatorAttachment !in listOf("platform", "cross-platform") -> {
                Result.failure(
                    IllegalArgumentException("Invalid authenticator attachment: ${authenticatorSelection.authenticatorAttachment}")
                )
            }
            authenticatorSelection.userVerification != null &&
            authenticatorSelection.userVerification !in listOf("required", "preferred", "discouraged") -> {
                Result.failure(
                    IllegalArgumentException("Invalid user verification requirement: ${authenticatorSelection.userVerification}")
                )
            }
            authenticatorSelection.requireResidentKey != null &&
            authenticatorSelection.residentKey != null -> {
                // Both cannot be specified simultaneously
                Result.failure(
                    IllegalArgumentException("Cannot specify both requireResidentKey and residentKey")
                )
            }
            authenticatorSelection.residentKey != null &&
            authenticatorSelection.residentKey !in listOf("required", "preferred", "discouraged") -> {
                Result.failure(
                    IllegalArgumentException("Invalid resident key requirement: ${authenticatorSelection.residentKey}")
                )
            }
            else -> Result.success(Unit)
        }
    }
    
    /**
     * Generate registration URL for cross-device flows
     */
    fun generateCrossDeviceRegistrationUrl(
        challenge: String,
        userId: String,
        displayName: String,
        sessionId: String? = null
    ): String {
        return CrossDeviceUrlGenerator.generateRegistrationUrl(
            challenge = challenge,
            userId = userId,
            displayName = displayName,
            sessionId = sessionId
        )
    }
}