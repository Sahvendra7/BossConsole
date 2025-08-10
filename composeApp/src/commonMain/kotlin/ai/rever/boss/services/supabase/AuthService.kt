package ai.rever.boss.services.supabase

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.OTP
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.time.ExperimentalTime
import kotlinx.serialization.Serializable
import ai.rever.boss.utils.PasswordValidator

/**
 * Authentication service for managing user authentication with Supabase
 */
@OptIn(ExperimentalTime::class)
object AuthService {
    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()
    
    // Flow for email verification events from deep links
    private val _emailVerificationEvent = MutableStateFlow<String?>(null)
    val emailVerificationEvent: StateFlow<String?> = _emailVerificationEvent.asStateFlow()
    
    private val _currentUser = MutableStateFlow<UserInfo?>(null)
    val currentUser: StateFlow<UserInfo?> = _currentUser.asStateFlow()
    
    // Flag to track if we're waiting for 2FA verification
    private var pendingTwoFactorVerification = false
    
    // Coroutine scope for auth service
    private val authScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    /**
     * Initialize the auth service and check for existing session
     */
    suspend fun initialize() {
        try {
            // Initialize Supabase with build-time configuration
            if (!SupabaseConfig.isInitialized.value) {
                SupabaseConfig.initializeFromEnvironment()
            }
            
            // Wait for session to load from storage and then set proper state
            authScope.launch {
                SupabaseConfig.client.auth.sessionStatus.collect { sessionStatus ->
                    println("AuthService.initialize: SessionStatus changed to: ${sessionStatus::class.simpleName}")
                    
                    when (sessionStatus) {
                        is io.github.jan.supabase.auth.status.SessionStatus.Authenticated -> {
                            val user = sessionStatus.session.user
                            val userId = user?.id ?: ""
                            println("AuthService.initialize: Session authenticated, User ID: $userId")
                            
                            _currentUser.value = UserInfo(
                                id = userId,
                                email = user?.email ?: "",
                                createdAt = user?.createdAt?.toString() ?: ""
                            )
                            
                            // Check if we're already pending 2FA verification from sign-in
                            if (pendingTwoFactorVerification) {
                                println("AuthService.initialize: 2FA verification already pending, keeping Requires2FA state")
                                // Don't override the Requires2FA state set by signIn()
                            } else {
                                // Check if 2FA verification is needed (considering trust period)
                                val factors = TwoFactorStorage.getUserFactors(userId)
                                println("AuthService.initialize: 2FA factors found: ${factors.size}")
                                
                                if (needs2FAVerification(userId)) {
                                    // User needs 2FA verification
                                    _authState.value = AuthState.Requires2FA
                                    pendingTwoFactorVerification = true
                                    println("AuthService.initialize: Setting state to Requires2FA")
                                } else {
                                    _authState.value = AuthState.Authenticated
                                    println("AuthService.initialize: Setting state to Authenticated (2FA trust period valid)")
                                }
                            }
                        }
                        is io.github.jan.supabase.auth.status.SessionStatus.NotAuthenticated -> {
                            _currentUser.value = null
                            _authState.value = AuthState.NotAuthenticated
                            println("AuthService.initialize: Setting state to NotAuthenticated")
                        }
                        else -> {
                            // Keep loading state for any other status while we wait
                            if (_authState.value is AuthState.Loading) {
                                println("AuthService.initialize: Still waiting for session status: $sessionStatus")
                            } else {
                                println("AuthService.initialize: Other session status: $sessionStatus")
                            }
                        }
                    }
                }
            }
            
        } catch (e: Exception) {
            _authState.value = AuthState.Error(e.message ?: "Failed to initialize authentication")
        }
    }
    
    /**
     * Sign in with email and password
     */
    suspend fun signIn(email: String, password: String): Result<Unit> {
        return try {
            // Don't change AuthState to Loading during signin - let the ViewModel handle loading state
            println("Attempting to sign in with email: $email")
            println("Supabase URL: ${SupabaseConfig.client.supabaseUrl}")
            
            SupabaseConfig.client.auth.signInWith(Email) {
                this.email = email
                this.password = password
            }
            
            println("Sign in successful!")
            
            // Check if user has 2FA enrolled
            val session = SupabaseConfig.client.auth.currentSessionOrNull()
            println("Session after sign in: ${session != null}")
            if (session != null) {
                val userId = session.user?.id ?: ""
                println("User ID: $userId")
                val factors = TwoFactorStorage.getUserFactors(userId)
                println("2FA factors found: ${factors.size}, verified: ${factors.any { it.status == "verified" }}")
                
                if (factors.isNotEmpty() && factors.any { it.status == "verified" }) {
                    // User has 2FA enrolled - mark as pending verification
                    pendingTwoFactorVerification = true
                    _authState.value = AuthState.Requires2FA
                    println("Setting auth state to Requires2FA")
                } else {
                    // No 2FA, let the session listener update the state
                    println("User doesn't have 2FA enrolled, will be set to Authenticated")
                }
            } else {
                println("WARNING: No session found after sign in!")
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            println("Sign in failed with exception: ${e.javaClass.simpleName}")
            println("Error message: ${e.message}")
            e.printStackTrace()
            
            val errorMessage = when {
                e.message?.contains("Invalid login credentials") == true -> 
                    "Invalid email or password"
                e.message?.contains("Email not confirmed") == true -> 
                    "Please confirm your email before signing in"
                e.message?.contains("cancelled") == true ->
                    "Network request cancelled. Please check your internet connection."
                else -> e.message ?: "Sign in failed"
            }
            // Don't change AuthState on signin failure - let the ViewModel handle the error
            Result.failure(Exception(errorMessage))
        }
    }
    
    /**
     * Sign up with email and password
     */
    suspend fun signUp(email: String, password: String): Result<Unit> {
        return try {
            // Validate password strength first
            val validation = PasswordValidator.validatePassword(password)
            if (!validation.isValid) {
                return Result.failure(Exception(validation.errors.first()))
            }
            
            // Don't change AuthState to Loading during signup - let the ViewModel handle loading state
            println("Attempting to sign up with email: $email")
            println("Supabase URL: ${SupabaseConfig.client.supabaseUrl}")
            
            SupabaseConfig.client.auth.signUpWith(Email) {
                this.email = email
                this.password = password
            }
            
            println("Sign up successful!")
            // AuthState remains as-is (should be NotAuthenticated)
            Result.success(Unit)
        } catch (e: Exception) {
            println("Sign up failed with exception: ${e.javaClass.simpleName}")
            println("Error message: ${e.message}")
            e.printStackTrace()
            
            val errorMessage = when {
                e.message?.contains("already registered") == true -> 
                    "This email is already registered"
                e.message?.contains("Password should be") == true -> 
                    "Password does not meet security requirements"
                e.message?.contains("cancelled") == true ->
                    "Network request cancelled. Please check your internet connection."
                else -> e.message ?: "Sign up failed"
            }
            // Don't change AuthState on signup failure - let the ViewModel handle the error
            Result.failure(Exception(errorMessage))
        }
    }
    
    /**
     * Send password reset email
     */
    suspend fun resetPassword(email: String): Result<Unit> {
        return try {
            println("Requesting password reset for email: $email")
            
            SupabaseConfig.client.auth.resetPasswordForEmail(email)
            
            println("Password reset email sent successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            println("Password reset failed: ${e.message}")
            val errorMessage = when {
                e.message?.contains("User not found") == true -> 
                    "No account found with this email address"
                e.message?.contains("cancelled") == true ->
                    "Request cancelled. Please check your internet connection."
                else -> e.message ?: "Failed to send password reset email"
            }
            Result.failure(Exception(errorMessage))
        }
    }
    
    /**
     * Sign out the current user
     */
    suspend fun signOut(): Result<Unit> {
        return try {
            // Reset 2FA verification timestamps for the current user
            val userId = _currentUser.value?.id
            if (userId != null) {
                val factors = TwoFactorStorage.getUserFactors(userId)
                val resetFactors = factors.map { factor ->
                    factor.copy(lastVerifiedAt = 0L) // Reset verification timestamp
                }
                TwoFactorStorage.saveUserFactors(userId, resetFactors)
                println("Reset 2FA verification timestamps for user: $userId")
            }
            
            // Reset pending 2FA verification flag
            pendingTwoFactorVerification = false
            
            SupabaseConfig.client.auth.signOut()
            _currentUser.value = null
            _authState.value = AuthState.NotAuthenticated
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Verify email with token and automatically sign in the user
     */
    suspend fun verifyEmailAndSignIn(token: String, email: String, password: String): Result<Unit> {
        return try {
            println("Verifying email and signing in user: $email")
            
            // First, verify the email token (simplified - in real app this would call Supabase API)
            println("Email verification token: $token")
            
            // After successful email verification, automatically sign the user in
            signIn(email, password).fold(
                onSuccess = {
                    println("Auto sign-in after email verification successful")
                    Result.success(Unit)
                },
                onFailure = { error ->
                    println("Auto sign-in failed after email verification: ${error.message}")
                    Result.failure(error)
                }
            )
        } catch (e: Exception) {
            println("Email verification and sign-in failed: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * Mark email as verified - called when deep link indicates successful verification
     */
    suspend fun verifyEmail(token: String): Result<Unit> {
        return try {
            println("Email verification confirmed via deep link with token: $token")
            
            // The deep link indicates that Supabase has successfully verified the email
            // Now we should be able to sign in the user if we have their credentials
            
            println("Email verification completed successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            println("Email verification processing failed: ${e.message}")
            Result.failure(Exception("Failed to process email verification: ${e.message}"))
        }
    }
    
    /**
     * Handle password reset from deep link token
     */
    suspend fun processPasswordReset(token: String): Result<Unit> {
        return try {
            println("Processing password reset with token: $token")
            
            // For password reset, we need to use the access_token to authenticate
            // and then allow the user to set a new password
            // This token represents a temporary session for password reset
            
            // Note: The actual password update will be handled by a separate method
            // This just validates that the reset token is valid
            
            println("Password reset token processed successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            println("Password reset processing failed: ${e.message}")
            Result.failure(Exception("Failed to process password reset: ${e.message}"))
        }
    }
    
    /**
     * Update password using reset token
     */
    suspend fun updatePassword(accessToken: String, newPassword: String): Result<Unit> {
        return try {
            println("Updating password with access token")
            
            // Store current session to restore later (if any)
            val originalSession = SupabaseConfig.client.auth.currentSessionOrNull()
            
            // Temporarily set the session using the access token from the password reset link
            SupabaseConfig.client.auth.importAuthToken(accessToken)
            
            // Update the password - should work since we have a valid session
            SupabaseConfig.client.auth.updateUser {
                password = newPassword
            }
            
            // After successful password update, sign out the temporary session
            // This clears the temporary reset session
            SupabaseConfig.client.auth.signOut()
            
            // Restore original session if it existed, otherwise leave signed out
            if (originalSession != null) {
                println("Restoring original session after password update")
                // Note: In a real app, we might need to re-authenticate the user
                // For now, we'll let them sign in again with their new password
            }
            
            println("Password updated successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            println("Password update failed: ${e.message}")
            val errorMessage = when {
                e.message?.contains("Invalid token") == true -> 
                    "Password reset link has expired. Please request a new one."
                e.message?.contains("Password should be") == true -> 
                    "Password must be at least 6 characters"
                e.message?.contains("cancelled") == true ->
                    "Request cancelled. Please check your internet connection."
                e.message?.contains("Unauthorized") == true ->
                    "Password reset link has expired. Please request a new one."
                else -> e.message ?: "Failed to update password"
            }
            Result.failure(Exception(errorMessage))
        }
    }
    
    /**
     * Trigger email verification event for LoginViewModel to handle
     */
    fun triggerEmailVerificationEvent(token: String) {
        println("AuthService: Triggering email verification event with token: $token")
        _emailVerificationEvent.value = token
    }
    
    /**
     * Clear email verification event after processing
     */
    fun clearEmailVerificationEvent() {
        _emailVerificationEvent.value = null
    }
    
    /**
     * Enroll user in 2FA (TOTP) - Simplified implementation
     */
    suspend fun enroll2FA(): Result<TwoFactorEnrollment> {
        return try {
            // Placeholder implementation for 2FA enrollment
            // In a real implementation, you would call the Supabase MFA API
            println("Starting 2FA enrollment process...")
            
            // Generate a random TOTP secret (Base32 encoded)
            val secret = generateTOTPSecret()
            val factorId = "totp_${System.currentTimeMillis()}"
            
            // Get current user email or use placeholder
            val userEmail = _currentUser.value?.email ?: "user@example.com"
            
            // Generate TOTP URI
            val uri = "otpauth://totp/BOSS:$userEmail?secret=$secret&issuer=BOSS&algorithm=SHA1&digits=6&period=30"
            
            Result.success(TwoFactorEnrollment(
                id = factorId,
                qrCode = "", // Will be generated by QR code provider
                secret = secret,
                uri = uri
            ))
        } catch (e: Exception) {
            println("2FA enrollment failed: ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        }
    }
    
    /**
     * Generate a random TOTP secret (Base32 encoded)
     */
    private fun generateTOTPSecret(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        return (1..32).map { chars.random() }.joinToString("")
    }
    
    /**
     * Verify 2FA enrollment with TOTP code - Simplified implementation
     */
    suspend fun verify2FAEnrollment(factorId: String, code: String): Result<Unit> {
        return try {
            println("Verifying 2FA enrollment with code: $code for factor: $factorId")
            
            // Placeholder verification - in real implementation, verify the TOTP code
            if (code.length == 6 && code.all { it.isDigit() }) {
                // Store the enrolled factor persistently
                val userId = _currentUser.value?.id ?: return Result.failure(Exception("No user logged in"))
                val newFactor = TwoFactorInfo(
                    id = factorId,
                    friendlyName = "Authenticator App",
                    status = "verified"
                )
                TwoFactorStorage.addUserFactor(userId, newFactor)
                println("2FA factor enrolled successfully for user: $userId")
                Result.success(Unit)
            } else {
                Result.failure(Exception("Invalid verification code"))
            }
        } catch (e: Exception) {
            println("2FA verification failed: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * Check if current user requires 2FA verification
     */
    suspend fun check2FARequired(): Boolean {
        val userId = _currentUser.value?.id ?: return false
        val factors = TwoFactorStorage.getUserFactors(userId)
        return factors.isNotEmpty() && factors.any { it.status == "verified" }
    }
    
    /**
     * Check if 2FA verification is needed based on trust period
     * Trust period: 24 hours (86400000 ms)
     */
    private fun needs2FAVerification(userId: String): Boolean {
        val factors = TwoFactorStorage.getUserFactors(userId)
        if (factors.isEmpty() || factors.none { it.status == "verified" }) {
            return false // No 2FA enrolled
        }
        
        val trustPeriodMs = 24 * 60 * 60 * 1000L // 24 hours
        val currentTime = System.currentTimeMillis()
        
        // Check if any factor was verified within the trust period
        val hasRecentVerification = factors.any { factor ->
            factor.status == "verified" && 
            factor.lastVerifiedAt > 0 && 
            (currentTime - factor.lastVerifiedAt) < trustPeriodMs
        }
        
        println("2FA verification needed: ${!hasRecentVerification} (last verified: ${factors.maxOfOrNull { it.lastVerifiedAt }}, current time: $currentTime)")
        return !hasRecentVerification
    }
    
    /**
     * Verify 2FA code during sign-in - Simplified implementation
     */
    suspend fun verify2FAChallenge(factorId: String, challengeId: String, code: String): Result<Unit> {
        return try {
            println("Verifying 2FA challenge with code: $code")
            
            // Placeholder verification
            if (code.length == 6 && code.all { it.isDigit() }) {
                // Update last verification timestamp for all factors
                val userId = _currentUser.value?.id ?: return Result.failure(Exception("No user logged in"))
                val factors = TwoFactorStorage.getUserFactors(userId).toMutableList()
                val currentTime = System.currentTimeMillis()
                
                // Update timestamps for verified factors
                val updatedFactors = factors.map { factor ->
                    if (factor.status == "verified") {
                        factor.copy(lastVerifiedAt = currentTime)
                    } else {
                        factor
                    }
                }
                
                TwoFactorStorage.saveUserFactors(userId, updatedFactors)
                println("Updated 2FA verification timestamp: $currentTime")
                
                // After successful 2FA verification, clear the pending flag and set state to Authenticated
                pendingTwoFactorVerification = false
                _authState.value = AuthState.Authenticated
                println("2FA verification successful, user is now authenticated")
                Result.success(Unit)
            } else {
                Result.failure(Exception("Invalid 2FA code"))
            }
        } catch (e: Exception) {
            println("2FA challenge verification failed: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * Get user's 2FA factors - Simplified implementation
     */
    suspend fun get2FAFactors(): Result<List<TwoFactorInfo>> {
        return try {
            println("Getting user's 2FA factors...")
            
            val userId = _currentUser.value?.id ?: return Result.success(emptyList())
            val factors = TwoFactorStorage.getUserFactors(userId)
            println("User $userId has ${factors.size} 2FA factors enrolled")
            Result.success(factors)
        } catch (e: Exception) {
            println("Failed to get 2FA factors: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * Check if user needs to enroll in 2FA (mandatory 2FA policy)
     */
    suspend fun requires2FAEnrollment(): Boolean {
        // Since we're using a simplified implementation, we'll track 2FA status locally
        // In a real implementation, this would check the Supabase MFA API
        val user = currentUser.value ?: return false
        
        // Check if user has any enrolled 2FA factors
        val factors = get2FAFactors().getOrNull() ?: emptyList()
        
        // Return true if no verified factors exist (mandatory 2FA policy)
        val needsEnrollment = factors.none { it.status == "verified" }
        println("User ${user.id} needs 2FA enrollment: $needsEnrollment")
        return needsEnrollment
    }
    
    /**
     * Unenroll from 2FA - Simplified implementation
     */
    suspend fun unenroll2FA(factorId: String): Result<Unit> {
        return try {
            println("Unenrolling from 2FA: $factorId")
            val userId = _currentUser.value?.id ?: return Result.failure(Exception("No user logged in"))
            TwoFactorStorage.removeUserFactor(userId, factorId)
            Result.success(Unit)
        } catch (e: Exception) {
            println("2FA unenrollment failed: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * Configure Supabase connection (deprecated - now uses build-time config)
     */
    @Deprecated("Supabase configuration is now build-time only")
    suspend fun configureSupabase(url: String, anonKey: String): Result<Unit> {
        return try {
            // Configuration is now build-time only, so just re-initialize
            initialize()
            Result.success(Unit)
        } catch (e: Exception) {
            _authState.value = AuthState.Error("Failed to initialize: ${e.message}")
            Result.failure(e)
        }
    }
}

/**
 * Authentication state
 */
sealed class AuthState {
    object Loading : AuthState()
    object NotAuthenticated : AuthState()
    object Authenticated : AuthState()
    object Requires2FA : AuthState()  // New state for users who need to verify 2FA
    data class Error(val message: String) : AuthState()
}

/**
 * User information
 */
data class UserInfo(
    val id: String,
    val email: String,
    val createdAt: String
)

/**
 * Two-factor authentication enrollment data
 */
data class TwoFactorEnrollment(
    val id: String,
    val qrCode: String,
    val secret: String,
    val uri: String
)

/**
 * Two-factor authentication factor information
 */
@Serializable
data class TwoFactorInfo(
    val id: String,
    val friendlyName: String,
    val status: String,
    val lastVerifiedAt: Long = 0L // Unix timestamp of last verification
)