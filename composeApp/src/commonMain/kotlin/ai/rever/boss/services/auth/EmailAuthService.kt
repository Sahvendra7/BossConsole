package ai.rever.boss.services.auth

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.OTP
import io.github.jan.supabase.auth.OtpType
import ai.rever.boss.services.supabase.SupabaseConfig

/**
 * Handles email-based authentication operations
 */
internal object EmailAuthService {

    /**
     * Mark email as verified - called when deep link indicates successful verification
     */
    suspend fun verifyEmail(token: String, type: String = "magiclink"): Result<Unit> {
        return try {
            println("Email verification confirmed via deep link with token: $token, type: $type")
            
            // Use our magic link verification method with the correct type
            verifyMagicLinkToken(token, type = type).fold(
                onSuccess = { 
                    println("Magic link verification successful")
                    Result.success(Unit)
                },
                onFailure = { error ->
                    println("Magic link verification failed: ${error.message}")
                    Result.failure(error)
                }
            )
        } catch (e: Exception) {
            println("Email verification processing failed: ${e.message}")
            Result.failure(Exception("Failed to process email verification: ${e.message}"))
        }
    }
    

    /**
     * Send magic link to user's email for passwordless authentication
     * This works for both new signups and existing users (including unconfirmed ones)
     */
    suspend fun sendMagicLink(email: String): Result<Unit> {
        return try {
            println("Sending magic link to email: $email")
            println("Supabase URL: ${SupabaseConfig.client.supabaseUrl}")
            
            // signInWith(OTP) handles multiple cases:
            // 1. New user - creates unconfirmed user and sends signup link
            // 2. Existing confirmed user - sends login link
            // 3. Existing unconfirmed user - resends signup/confirmation link
            SupabaseConfig.client.auth.signInWith(OTP) {
                this.email = email
                // The createUser flag is true by default, which means:
                // - If user doesn't exist, create them (signup)
                // - If user exists (confirmed or not), just send the link
            }
            
            println("Magic link sent successfully!")
            Result.success(Unit)
        } catch (e: Exception) {
            println("Magic link sending failed with exception: ${e.javaClass.simpleName}")
            println("Error message: ${e.message}")
            
            val errorMessage = when {
                e.message?.contains("User not found") == true -> 
                    "No account found with this email address"
                e.message?.contains("cancelled") == true ->
                    "Network request cancelled. Please check your internet connection."
                e.message?.contains("Email rate limit exceeded") == true ->
                    "Too many attempts. Please wait a few minutes before trying again."
                else -> e.message ?: "Failed to send magic link"
            }
            Result.failure(Exception(errorMessage))
        }
    }

    /**
     * Verify magic link token using SDK's verifyEmailOtp method
     * For magic links, we need to use token_hash verification
     */
    suspend fun verifyMagicLinkToken(token: String, email: String? = null, type: String = "magiclink"): Result<Boolean> {
        return try {
            println("========== MAGIC LINK VERIFICATION DEBUG ==========")
            println("Token: $token")
            println("Token length: ${token.length}")
            println("Email provided: ${email ?: "None"}")
            println("Type: $type")
            
            // Try using the SDK's verifyEmailOtp with tokenHash
            // Magic links use token_hash verification
            val otpType = when(type) {
                "signup" -> OtpType.Email.SIGNUP
                "magiclink" -> OtpType.Email.MAGIC_LINK
                "recovery" -> OtpType.Email.RECOVERY
                "invite" -> OtpType.Email.INVITE
                else -> OtpType.Email.EMAIL
            }
            
            println("Mapped to OtpType: $otpType")
            
            // The SDK should handle the session properly
            // For magic links, we need the email address
            if (email != null) {
                println("Using verifyEmailOtp with email='$email' and token")
                // Use the version with email and token
                SupabaseConfig.client.auth.verifyEmailOtp(
                    type = otpType,
                    email = email,
                    token = token
                )
            } else {
                println("Using verifyEmailOtp with tokenHash (no email provided)")
                // Fallback to tokenHash version if no email provided
                SupabaseConfig.client.auth.verifyEmailOtp(
                    type = otpType,
                    tokenHash = token
                )
            }
            
            println("SDK verifyEmailOtp completed successfully")
            
            // Check if we have a session now
            val currentSession = SupabaseConfig.client.auth.currentSessionOrNull()
            println("Current session after verification: ${if (currentSession != null) "EXISTS" else "NULL"}")
            if (currentSession != null) {
                println("Session user: ${currentSession.user?.email}")
                println("Session access token: ${currentSession.accessToken.take(20)}...")
            }
            
            // Mark that user authenticated via magic link
            AuthStateManager.setAuthenticatedViaMagicLink(true)
            println("Marked user as authenticated via magic link")
            println("========== VERIFICATION COMPLETE ==========")
            
            Result.success(true)
        } catch (e: Exception) {
            println("========== VERIFICATION FAILED ==========")
            println("Exception type: ${e::class.simpleName}")
            println("Error message: ${e.message}")
            
            val errorMessage = when {
                e.message?.contains("Invalid token") == true -> 
                    "This magic link has expired. Magic links are valid for 15 minutes. Please request a new one."
                e.message?.contains("already_used") == true -> 
                    "This magic link has already been used. Please request a new one if you need to sign in again."
                e.message?.contains("expired") == true ->
                    "This magic link has expired. Magic links are valid for 15 minutes. Please request a new one."
                e.message?.contains("JsonLiteral") == true ||
                e.message?.contains("JsonObject") == true -> {
                    "Server response format issue - please try again"
                }
                e.message?.contains("404") == true -> {
                    "Magic link verification endpoint not found"
                }
                e.message?.contains("cancelled") == true ->
                    "Network request cancelled. Please check your internet connection."
                else -> e.message ?: "Magic link verification failed"
            }
            
            Result.failure(Exception(errorMessage))
        }
    }
}
