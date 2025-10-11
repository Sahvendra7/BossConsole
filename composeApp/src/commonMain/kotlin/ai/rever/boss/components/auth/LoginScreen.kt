package ai.rever.boss.components.auth

import androidx.compose.runtime.Composable
import ai.rever.boss.components.auth.AuthScreenContainer

/**
 * Main entry point for authentication flow.
 * This component has been refactored to use the extracted AuthScreenContainer
 * for better separation of concerns and maintainability.
 *
 * @param onLoginSuccess Callback invoked when login/authentication is successful
 */
@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit
) {
    AuthScreenContainer(
        onLoginSuccess = onLoginSuccess
    )
}

/* 
 * The LoginForm component has been extracted to LoginFormScreen.kt
 * for better separation of concerns and maintainability.
 */

/* 
 * The SignupForm component has been extracted to SignupFormScreen.kt
 * for better separation of concerns and maintainability.
 */

/* 
 * The EmailVerificationScreen component has been extracted to screens/EmailVerificationScreen.kt
 * for better separation of concerns and maintainability.
 */

/* 
 * The TwoFactorScreen component has been extracted to screens/TwoFactorScreen.kt
 * for better separation of concerns and maintainability.
 */

/* 
 * The ForgotPasswordScreen component has been extracted to screens/ForgotPasswordScreen.kt
 * for better separation of concerns and maintainability.
 */

/* 
 * Helper functions for consistent styling have been extracted to forms/AuthFormComponents.kt
 * for better reusability and maintainability.
 */


