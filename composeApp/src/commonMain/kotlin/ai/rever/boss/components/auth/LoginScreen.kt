package ai.rever.boss.components.auth

import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
// BOSS theme and logo imports
import BossDarkBackground
import BossDarkSurface
import BossDarkBorder
import BossDarkTextPrimary
import BossDarkTextSecondary
import BossDarkAccent
import BossDarkError
import BossTheme
import ai.rever.boss.viewmodels.LoginViewModel
import ai.rever.boss.components.dialogs.EmailVerificationDialog
import ai.rever.boss.components.dialogs.TwoFactorVerifyDialog
import ai.rever.boss.services.supabase.AuthService
import ai.rever.boss.services.supabase.AuthState
import ai.rever.boss.utils.PasswordValidator

enum class AuthScreen {
    LOGIN,
    SIGNUP,
    VERIFY_EMAIL,
    TWO_FACTOR,
    FORGOT_PASSWORD
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit
) {
    // Use a stable key to prevent ViewModel recreation during AuthState changes
    val viewModel = remember("login_viewmodel") { LoginViewModel() }
    var currentScreen by remember { mutableStateOf(AuthScreen.LOGIN) }
    var isInSignupFlow by remember { mutableStateOf(false) }
    
    println("LoginScreen: Recomposed - viewModel: ${viewModel.hashCode()}")
    
    // Watch AuthService state directly to handle 2FA
    val authState by AuthService.authState.collectAsState()
    
    // React to AuthState changes (only for certain transitions)
    LaunchedEffect(authState) {
        println("LoginScreen: AuthState changed to: $authState, currentScreen: $currentScreen, isInSignupFlow: $isInSignupFlow")
        when (authState) {
            is AuthState.Requires2FA -> {
                println("LoginScreen: Switching to TWO_FACTOR screen due to AuthState")
                currentScreen = AuthScreen.TWO_FACTOR
            }
            is AuthState.Authenticated -> {
                // User is authenticated but might need 2FA enrollment
                // This will be handled by BossAppWithAuth showing Mandatory2FAEnrollmentScreen
                println("LoginScreen: User authenticated - will be handled by parent component")
                onLoginSuccess()
            }
            is AuthState.NotAuthenticated, is AuthState.Error -> {
                // Only switch to LOGIN if we're currently on TWO_FACTOR screen
                // Don't interrupt user navigation during signup flow
                if (currentScreen == AuthScreen.TWO_FACTOR) {
                    println("LoginScreen: Switching from TWO_FACTOR to LOGIN due to AuthState")  
                    currentScreen = AuthScreen.LOGIN
                }
                // Don't change screen if we're in the middle of signup flow
                // (SIGNUP -> VERIFY_EMAIL transition should not be interrupted)
            }
            else -> {
                // Don't change screen for other states
            }
        }
    }
    
    // Debug current screen changes
    LaunchedEffect(currentScreen) {
        println("LoginScreen: currentScreen changed to: $currentScreen")
    }
    
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    
    BossTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BossDarkBackground)
        ) {
            when (currentScreen) {
                AuthScreen.LOGIN -> LoginForm(
                    viewModel = viewModel,
                    onLoginSuccess = onLoginSuccess,
                    onNavigateToSignup = { 
                        isInSignupFlow = true
                        currentScreen = AuthScreen.SIGNUP 
                    },
                    onNavigateToForgotPassword = { currentScreen = AuthScreen.FORGOT_PASSWORD },
                    onNavigateTo2FA = { 
                        println("LoginScreen: onNavigateTo2FA called, switching to TWO_FACTOR screen")
                        currentScreen = AuthScreen.TWO_FACTOR 
                    },
                    isLoading = isLoading,
                    errorMessage = errorMessage
                )
                
                AuthScreen.SIGNUP -> SignupForm(
                    viewModel = viewModel,
                    onSignupSuccess = { 
                        println("LoginScreen: Sign-up successful, navigating to VERIFY_EMAIL")
                        currentScreen = AuthScreen.VERIFY_EMAIL 
                    },
                    onNavigateToLogin = { 
                        isInSignupFlow = false
                        currentScreen = AuthScreen.LOGIN 
                    },
                    isLoading = isLoading,
                    errorMessage = errorMessage
                )
                
                AuthScreen.VERIFY_EMAIL -> EmailVerificationScreen(
                    viewModel = viewModel,
                    onVerified = { 
                        // After email verification with auto sign-in, the AuthState will change
                        // and LoginScreen will automatically handle the transition based on the new state
                        println("Email verification completed - letting AuthState handle navigation")
                        isInSignupFlow = false // Clear signup flow flag after verification
                    },
                    onBack = { currentScreen = AuthScreen.SIGNUP }
                )
                
                AuthScreen.TWO_FACTOR -> TwoFactorScreen(
                    viewModel = viewModel,
                    onVerified = onLoginSuccess,
                    onBack = { currentScreen = AuthScreen.LOGIN }
                )
                
                AuthScreen.FORGOT_PASSWORD -> ForgotPasswordScreen(
                    viewModel = viewModel,
                    onBack = { currentScreen = AuthScreen.LOGIN },
                    isLoading = isLoading,
                    errorMessage = errorMessage
                )
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun LoginForm(
    viewModel: LoginViewModel,
    onLoginSuccess: () -> Unit,
    onNavigateToSignup: () -> Unit,
    onNavigateToForgotPassword: () -> Unit,
    onNavigateTo2FA: () -> Unit,
    isLoading: Boolean,
    errorMessage: String?
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // BOSS Logo
        Image(
            painter = painterResource("boss_icon.png"),
            contentDescription = "BOSS Logo",
            modifier = Modifier.size(64.dp)
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Login Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 400.dp),
            shape = RoundedCornerShape(8.dp),
            elevation = 2.dp,
            backgroundColor = BossDarkSurface
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Sign In",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = BossDarkTextPrimary,
                    modifier = Modifier.padding(bottom = 24.dp)
                )
                
                // Email Field
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email", color = BossDarkTextSecondary) },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Email,
                            contentDescription = "Email",
                            tint = BossDarkTextSecondary
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) }
                    ),
                    enabled = !isLoading,
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        textColor = BossDarkTextPrimary,
                        backgroundColor = BossDarkBackground,
                        focusedBorderColor = BossDarkAccent,
                        unfocusedBorderColor = BossDarkBorder,
                        cursorColor = BossDarkAccent,
                        focusedLabelColor = BossDarkAccent,
                        unfocusedLabelColor = BossDarkTextSecondary
                    )
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Password Field
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password", color = BossDarkTextSecondary) },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = "Password",
                            tint = BossDarkTextSecondary
                        )
                    },
                    visualTransformation = if (passwordVisible) VisualTransformation.None 
                                          else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                if (passwordVisible) Icons.Default.VisibilityOff 
                                else Icons.Default.Visibility,
                                contentDescription = if (passwordVisible) "Hide password" else "Show password",
                                tint = BossDarkTextSecondary
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            keyboardController?.hide()
                            if (email.isNotBlank() && password.isNotBlank() && !isLoading) {
                                viewModel.signIn(email, password) {
                                    // Success handled by AuthState changes
                                    onLoginSuccess()
                                }
                            }
                        }
                    ),
                    enabled = !isLoading,
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        textColor = BossDarkTextPrimary,
                        backgroundColor = BossDarkBackground,
                        focusedBorderColor = BossDarkAccent,
                        unfocusedBorderColor = BossDarkBorder,
                        cursorColor = BossDarkAccent,
                        focusedLabelColor = BossDarkAccent,
                        unfocusedLabelColor = BossDarkTextSecondary
                    )
                )
                
                // Error Message
                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage,
                        color = BossDarkError,
                        fontSize = 12.sp,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Start
                    )
                }
                
                Spacer(modifier = Modifier.height(20.dp))
                
                // Login Button
                Button(
                    onClick = {
                        viewModel.signIn(email, password) {
                            // Success handled by AuthState changes
                            onLoginSuccess()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    enabled = !isLoading && email.isNotBlank() && password.isNotBlank(),
                    shape = RoundedCornerShape(4.dp),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = BossDarkAccent,
                        contentColor = Color.White
                    )
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = "Sign In",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Create Account Link
                TextButton(
                    onClick = onNavigateToSignup,
                    enabled = !isLoading
                ) {
                    Text(
                        text = "Create new account",
                        fontSize = 14.sp,
                        color = BossDarkAccent,
                        textDecoration = TextDecoration.Underline
                    )
                }
                
                // Forgot Password Link
                TextButton(
                    onClick = onNavigateToForgotPassword,
                    enabled = !isLoading
                ) {
                    Text(
                        text = "Forgot password?",
                        fontSize = 12.sp,
                        color = BossDarkTextSecondary
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun SignupForm(
    viewModel: LoginViewModel,
    onSignupSuccess: () -> Unit,
    onNavigateToLogin: () -> Unit,
    isLoading: Boolean,
    errorMessage: String?
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var passwordValidationErrors by remember { mutableStateOf<List<String>>(emptyList()) }
    
    // Calculate password strength for visual feedback
    val passwordStrength = if (password.isNotBlank()) {
        PasswordValidator.getPasswordStrength(password)
    } else 0
    
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // BOSS Logo
        Image(
            painter = painterResource("boss_icon.png"),
            contentDescription = "BOSS Logo",
            modifier = Modifier.size(64.dp)
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Signup Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 400.dp),
            shape = RoundedCornerShape(8.dp),
            elevation = 2.dp,
            backgroundColor = BossDarkSurface
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Create Account",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = BossDarkTextPrimary,
                    modifier = Modifier.padding(bottom = 24.dp)
                )
                
                // Email Field
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email", color = BossDarkTextSecondary) },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Email,
                            contentDescription = "Email",
                            tint = BossDarkTextSecondary
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) }
                    ),
                    enabled = !isLoading,
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        textColor = BossDarkTextPrimary,
                        backgroundColor = BossDarkBackground,
                        focusedBorderColor = BossDarkAccent,
                        unfocusedBorderColor = BossDarkBorder,
                        cursorColor = BossDarkAccent,
                        focusedLabelColor = BossDarkAccent,
                        unfocusedLabelColor = BossDarkTextSecondary
                    )
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Password Field
                OutlinedTextField(
                    value = password,
                    onValueChange = { 
                        password = it
                        passwordValidationErrors = emptyList() // Clear validation errors when user types
                    },
                    label = { Text("Password", color = BossDarkTextSecondary) },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = "Password",
                            tint = BossDarkTextSecondary
                        )
                    },
                    visualTransformation = if (passwordVisible) VisualTransformation.None 
                                          else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                if (passwordVisible) Icons.Default.VisibilityOff 
                                else Icons.Default.Visibility,
                                contentDescription = if (passwordVisible) "Hide password" else "Show password",
                                tint = BossDarkTextSecondary
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            keyboardController?.hide()
                        }
                    ),
                    enabled = !isLoading,
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        textColor = BossDarkTextPrimary,
                        backgroundColor = BossDarkBackground,
                        focusedBorderColor = BossDarkAccent,
                        unfocusedBorderColor = BossDarkBorder,
                        cursorColor = BossDarkAccent,
                        focusedLabelColor = BossDarkAccent,
                        unfocusedLabelColor = BossDarkTextSecondary
                    )
                )
                
                // Error Message
                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage,
                        color = BossDarkError,
                        fontSize = 12.sp,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Start
                    )
                }
                
                // Password Strength Indicator
                if (password.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Password Strength:",
                                fontSize = 12.sp,
                                color = BossDarkTextSecondary
                            )
                            Text(
                                text = PasswordValidator.getPasswordStrengthDescription(passwordStrength),
                                fontSize = 12.sp,
                                color = PasswordValidator.getPasswordStrengthColor(passwordStrength),
                                fontWeight = FontWeight.Medium
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        // Strength progress bar
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .background(
                                    BossDarkBorder,
                                    RoundedCornerShape(2.dp)
                                )
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(passwordStrength / 100f)
                                    .fillMaxHeight()
                                    .background(
                                        PasswordValidator.getPasswordStrengthColor(passwordStrength),
                                        RoundedCornerShape(2.dp)
                                    )
                            )
                        }
                    }
                }
                
                // Password Requirements (shown when password has validation errors)
                if (passwordValidationErrors.isNotEmpty() && password.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Password Requirements:",
                            fontSize = 12.sp,
                            color = BossDarkTextSecondary,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        passwordValidationErrors.forEach { error ->
                            Text(
                                text = "• $error",
                                fontSize = 11.sp,
                                color = BossDarkError,
                                modifier = Modifier.padding(start = 8.dp, bottom = 2.dp)
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(20.dp))
                
                // Signup Button
                Button(
                    onClick = {
                        // Validate password before attempting signup
                        val validation = PasswordValidator.validatePassword(password)
                        if (validation.isValid) {
                            viewModel.signUp(email, password, onSignupSuccess)
                        } else {
                            passwordValidationErrors = validation.errors
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    enabled = !isLoading && email.isNotBlank() && password.isNotBlank(),
                    shape = RoundedCornerShape(4.dp),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = BossDarkAccent,
                        contentColor = Color.White
                    )
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = "Sign Up",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Have Account Link
                TextButton(
                    onClick = onNavigateToLogin,
                    enabled = !isLoading
                ) {
                    Text(
                        text = "Already have an account? Sign in",
                        fontSize = 14.sp,
                        color = BossDarkAccent,
                        textDecoration = TextDecoration.Underline
                    )
                }
            }
        }
    }
}

@Composable
private fun EmailVerificationScreen(
    viewModel: LoginViewModel,
    onVerified: () -> Unit,
    onBack: () -> Unit
) {
    var verificationCode by remember { mutableStateOf("") }
    var showVerificationDialog by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // BOSS Logo
        Image(
            painter = painterResource("boss_icon.png"),
            contentDescription = "BOSS Logo",
            modifier = Modifier.size(64.dp)
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Verification Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 350.dp),
            shape = RoundedCornerShape(8.dp),
            elevation = 2.dp,
            backgroundColor = BossDarkSurface
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Check Your Email",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = BossDarkTextPrimary
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "We've sent a verification link to your email.\nClick the link to verify your account, or use the button below if the link doesn't work.",
                    fontSize = 14.sp,
                    color = BossDarkTextSecondary,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )
                
                Spacer(modifier = Modifier.height(20.dp))
                
                // Verify Link Button
                Button(
                    onClick = { showVerificationDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(4.dp),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = BossDarkAccent,
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        text = "Verify Email",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Back Button
                TextButton(
                    onClick = onBack
                ) {
                    Text(
                        text = "Back to Sign Up",
                        fontSize = 14.sp,
                        color = BossDarkAccent,
                        textDecoration = TextDecoration.Underline
                    )
                }
            }
        }
    }
    
    // Email Verification Dialog
    if (showVerificationDialog) {
        EmailVerificationDialog(
            onDismiss = { showVerificationDialog = false },
            onVerified = onVerified,
            viewModel = viewModel
        )
    }
}

@Composable
private fun TwoFactorScreen(
    viewModel: LoginViewModel,
    onVerified: () -> Unit,
    onBack: () -> Unit
) {
    var verificationCode by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // BOSS Logo
        Image(
            painter = painterResource("boss_icon.png"),
            contentDescription = "BOSS Logo",
            modifier = Modifier.size(64.dp)
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // 2FA Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 350.dp),
            shape = RoundedCornerShape(8.dp),
            elevation = 2.dp,
            backgroundColor = BossDarkSurface
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Two-Factor Authentication",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = BossDarkTextPrimary
                )
                
                Spacer(modifier = Modifier.height(20.dp))
                
                // Code Input
                OutlinedTextField(
                    value = verificationCode,
                    onValueChange = { 
                        if (it.length <= 6 && it.all { char -> char.isDigit() }) {
                            verificationCode = it
                        }
                    },
                    label = { 
                        Text(
                            "Enter 6-digit code", 
                            color = BossDarkTextSecondary
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        textAlign = TextAlign.Center,
                        fontSize = 20.sp,
                        letterSpacing = 8.sp
                    ),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    enabled = !isLoading,
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        textColor = BossDarkTextPrimary,
                        backgroundColor = BossDarkBackground,
                        focusedBorderColor = BossDarkAccent,
                        unfocusedBorderColor = BossDarkBorder,
                        cursorColor = BossDarkAccent,
                        focusedLabelColor = BossDarkAccent,
                        unfocusedLabelColor = BossDarkTextSecondary
                    )
                )
                
                // Error Message
                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage!!,
                        color = BossDarkError,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }
                
                Spacer(modifier = Modifier.height(20.dp))
                
                // Verify Button
                Button(
                    onClick = {
                        // Verify the 2FA code
                        viewModel.verify2FAChallenge("", "", verificationCode) {
                            onVerified()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    enabled = !isLoading && verificationCode.length == 6,
                    shape = RoundedCornerShape(4.dp),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = BossDarkAccent,
                        contentColor = Color.White
                    )
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = "Verify",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Back Link
                TextButton(
                    onClick = onBack,
                    enabled = !isLoading
                ) {
                    Text(
                        text = "Back to Sign In",
                        fontSize = 14.sp,
                        color = BossDarkAccent,
                        textDecoration = TextDecoration.Underline
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Can't access your 2FA device? Contact your administrator.",
                    fontSize = 12.sp,
                    color = BossDarkTextSecondary,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ForgotPasswordScreen(
    viewModel: LoginViewModel,
    onBack: () -> Unit,
    isLoading: Boolean,
    errorMessage: String?
) {
    var email by remember { mutableStateOf("") }
    var emailSent by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // BOSS Logo
        Image(
            painter = painterResource("boss_icon.png"),
            contentDescription = "BOSS Logo",
            modifier = Modifier.size(64.dp)
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Forgot Password Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 350.dp),
            shape = RoundedCornerShape(8.dp),
            elevation = 2.dp,
            backgroundColor = BossDarkSurface
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Reset Password",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = BossDarkTextPrimary
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                if (!emailSent) {
                    Text(
                        text = "Enter your email address and we'll send you a link to reset your password.",
                        fontSize = 14.sp,
                        color = BossDarkTextSecondary,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )
                    
                    Spacer(modifier = Modifier.height(20.dp))
                    
                    // Email Field
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email", color = BossDarkTextSecondary) },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Email,
                                contentDescription = "Email",
                                tint = BossDarkTextSecondary
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Done
                        ),
                        enabled = !isLoading,
                        colors = TextFieldDefaults.outlinedTextFieldColors(
                            textColor = BossDarkTextPrimary,
                            backgroundColor = BossDarkBackground,
                            focusedBorderColor = BossDarkAccent,
                            unfocusedBorderColor = BossDarkBorder,
                            cursorColor = BossDarkAccent,
                            focusedLabelColor = BossDarkAccent,
                            unfocusedLabelColor = BossDarkTextSecondary
                        )
                    )
                    
                    // Error Message
                    if (errorMessage != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = errorMessage,
                            color = BossDarkError,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(20.dp))
                    
                    // Send Reset Link Button
                    Button(
                        onClick = {
                            viewModel.resetPasswordRequest(email) {
                                emailSent = true
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp),
                        enabled = !isLoading && email.isNotBlank(),
                        shape = RoundedCornerShape(4.dp),
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = BossDarkAccent,
                            contentColor = Color.White
                        )
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = "Send Reset Link",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                } else {
                    // Email Sent Success Message
                    Icon(
                        Icons.Default.Email,
                        contentDescription = "Email sent",
                        tint = BossDarkAccent,
                        modifier = Modifier.size(48.dp)
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "Password Reset Link Sent!",
                        fontSize = 18.sp,
                        color = BossDarkTextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Text(
                        text = "Check your email for instructions to reset your password.",
                        fontSize = 14.sp,
                        color = BossDarkTextSecondary,
                        textAlign = TextAlign.Center
                    )
                }
                
                Spacer(modifier = Modifier.height(20.dp))
                
                // Back to Login Link
                TextButton(
                    onClick = onBack,
                    enabled = !isLoading
                ) {
                    Text(
                        text = "Back to Sign In",
                        fontSize = 14.sp,
                        color = BossDarkAccent,
                        textDecoration = TextDecoration.Underline
                    )
                }
            }
        }
    }
}

// Helper function for consistent text field styling
@Composable
private fun BossTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    trailingIcon: @Composable (() -> Unit)? = null,
    enabled: Boolean = true
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { 
            Text(
                placeholder, 
                color = Color(0xFF6B6B6B),
                fontSize = 14.sp
            ) 
        },
        modifier = modifier
            .fillMaxWidth()
            .height(45.dp),
        singleLine = true,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        trailingIcon = trailingIcon,
        enabled = enabled,
        shape = RoundedCornerShape(6.dp),
        colors = TextFieldDefaults.textFieldColors(
            textColor = Color.White,
            backgroundColor = Color(0xFF2A2A2A),
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            cursorColor = Color(0xFF007AFF),
            placeholderColor = Color(0xFF6B6B6B)
        )
    )
}

// Helper function for consistent button styling
@Composable
private fun BossButton(
    onClick: () -> Unit,
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp),
        enabled = enabled && !isLoading,
        shape = RoundedCornerShape(6.dp),
        colors = ButtonDefaults.buttonColors(
            backgroundColor = Color(0xFF007AFF),
            contentColor = Color.White,
            disabledBackgroundColor = Color(0xFF007AFF).copy(alpha = 0.5f)
        ),
        elevation = ButtonDefaults.elevation(0.dp)
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                color = Color.White,
                strokeWidth = 2.dp
            )
        } else {
            Text(
                text = text,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White
            )
        }
    }
}