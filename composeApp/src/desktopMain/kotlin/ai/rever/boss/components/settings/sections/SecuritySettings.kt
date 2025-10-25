package ai.rever.boss.components.settings.sections

import BossDarkAccent
import BossDarkBackground
import BossDarkBorder
import BossDarkSurface
import ai.rever.boss.components.settings.shared.SectionHeader
import ai.rever.boss.components.settings.shared.SettingSection
import ai.rever.boss.services.supabase.AuthService
import ai.rever.boss.services.passkey.PasskeyInfo
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * WebAuthn capabilities information
 */
data class WebAuthnCapabilities(
    val hasJxBrowserEngine: Boolean,
    val hasTouchId: Boolean,
    val hasSecurityKeySupport: Boolean,
    val hasNfcSupport: Boolean,
    val hasHybridTransport: Boolean,
    val supportedTransports: List<String>,
    val platformName: String
)

/**
 * Detect WebAuthn capabilities on the current platform
 */
private suspend fun detectWebAuthnCapabilities(): WebAuthnCapabilities {
    return try {
        val os = System.getProperty("os.name").lowercase()
        val platformName = when {
            os.contains("mac") -> "macOS"
            os.contains("windows") -> "Windows"  
            os.contains("linux") -> "Linux"
            else -> "Unknown"
        }
        
        // Try to check if JxBrowser is available by checking if passkey is supported
        val hasJxBrowser = try {
            // This is a simplified check - we assume JxBrowser is available if passkeys are supported
            AuthService.isPasskeySupported()
        } catch (e: Exception) {
            false
        }
        
        val hasTouchId = when {
            os.contains("mac") -> {
                try {
                    AuthService.isPasskeySupported()
                } catch (e: Exception) { false }
            }
            else -> false
        }
        
        // Enhanced capabilities when JxBrowser is available
        val supportedTransports = mutableListOf<String>()
        supportedTransports.add("internal")
        
        var hasSecurityKey = false
        var hasNfc = false
        var hasHybrid = false
        
        if (hasJxBrowser) {
            // Enhanced capabilities with JxBrowser
            hasSecurityKey = true
            hasNfc = true
            supportedTransports.addAll(listOf("usb", "nfc"))
            
            if (os.contains("mac")) {
                hasHybrid = true
                supportedTransports.add("hybrid")
            }
        } else {
            // Basic capabilities without JxBrowser
            hasSecurityKey = os.contains("mac") || os.contains("windows")
        }
        
        WebAuthnCapabilities(
            hasJxBrowserEngine = hasJxBrowser,
            hasTouchId = hasTouchId,
            hasSecurityKeySupport = hasSecurityKey,
            hasNfcSupport = hasNfc,
            hasHybridTransport = hasHybrid,
            supportedTransports = supportedTransports,
            platformName = platformName
        )
        
    } catch (e: Exception) {
        // Fallback capabilities
        val os = System.getProperty("os.name").lowercase()
        WebAuthnCapabilities(
            hasJxBrowserEngine = false,
            hasTouchId = false,
            hasSecurityKeySupport = false,
            hasNfcSupport = false,
            hasHybridTransport = false,
            supportedTransports = listOf("internal"),
            platformName = if (os.contains("mac")) "macOS" else if (os.contains("windows")) "Windows" else "Linux"
        )
    }
}

@Composable
fun SecuritySettings() {
    val authState by AuthService.authState.collectAsState()
    val currentUser by AuthService.currentUser.collectAsState()

    // Observe passkey state for embedded browser trigger
    val passkeyStateFlow = AuthService.getPasskeyState()
    val passkeyState by passkeyStateFlow?.collectAsState() ?: remember { mutableStateOf(null) }
    var showEmbeddedBrowser by remember { mutableStateOf(false) }
    var passkeyBrowserUrl by remember { mutableStateOf("") }
    var passkeyBrowserSessionId by remember { mutableStateOf("") }
    var initialPasskeyCount by remember { mutableStateOf(0) }  // Track count when browser opens for polling fallback

    var passkeyFactors by remember { mutableStateOf<List<PasskeyInfo>>(emptyList()) }
    var isLoadingPasskeys by remember { mutableStateOf(false) }
    var touchIDSupported by remember { mutableStateOf(false) }
    var webAuthnCapabilities by remember { mutableStateOf<WebAuthnCapabilities?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showRemovePasskeyDialog by remember { mutableStateOf<PasskeyInfo?>(null) }
    var showEnhancedEnrollDialog by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableStateOf(0) } // Add refresh trigger
    val coroutineScope = rememberCoroutineScope()
    
    // Function to refresh passkey list
    val refreshPasskeyList = suspend {
        isLoadingPasskeys = true
        AuthService.getUserPasskeys().fold(
            onSuccess = { passkeys ->
                passkeyFactors = passkeys
                isLoadingPasskeys = false
                errorMessage = null
            },
            onFailure = { error ->
                // Don't show error for passkeys if Touch ID not supported
                if (touchIDSupported) {
                    errorMessage = "Failed to load WebAuthn credentials: ${error.message}"
                }
                isLoadingPasskeys = false
            }
        )
    }
    
    // Load passkeys when component mounts or refreshKey changes
    LaunchedEffect(refreshKey) {
        if (authState is AuthService.AuthState.Authenticated) {
            // Check Touch ID support and detect WebAuthn capabilities (only on first load)
            if (refreshKey == 0) {
                try {
                    touchIDSupported = AuthService.isPasskeySupported()
                    webAuthnCapabilities = detectWebAuthnCapabilities()
                } catch (e: Exception) {
                    touchIDSupported = false
                    webAuthnCapabilities = null
                }
            }
            
            // Refresh passkey list
            refreshPasskeyList()
        }
    }
    
    // Add periodic refresh to catch passkeys added outside of settings
    LaunchedEffect(authState) {
        if (authState is AuthService.AuthState.Authenticated) {
            while (true) {
                kotlinx.coroutines.delay(5000) // Check every 5 seconds
                if (!isLoadingPasskeys) { // Only refresh if not currently loading
                    val currentCount = passkeyFactors.size
                    AuthService.getUserPasskeys().fold(
                        onSuccess = { passkeys ->
                            // Only update if the count changed (new passkey added/removed)
                            if (passkeys.size != currentCount) {
                                passkeyFactors = passkeys
                                errorMessage = null

                                // Fallback: Close embedded browser if new passkey detected during registration
                                if (showEmbeddedBrowser && passkeys.size > initialPasskeyCount) {
                                    println("SecuritySettings: New passkey detected via polling (count: $currentCount -> ${passkeys.size}), closing embedded browser")
                                    showEmbeddedBrowser = false
                                }
                            }
                        },
                        onFailure = { /* Ignore periodic refresh errors */ }
                    )
                }
            }
        }
    }

    // Monitor passkey state for embedded browser trigger
    LaunchedEffect(passkeyState) {
        if (passkeyState is ai.rever.boss.services.passkey.PasskeyState.ShowEmbeddedBrowser) {
            val browserState = passkeyState as ai.rever.boss.services.passkey.PasskeyState.ShowEmbeddedBrowser
            println("SecuritySettings: Passkey state changed to ShowEmbeddedBrowser, showing browser screen")
            passkeyBrowserUrl = browserState.url
            passkeyBrowserSessionId = browserState.sessionId
            initialPasskeyCount = passkeyFactors.size  // Track initial count for polling fallback detection
            showEmbeddedBrowser = true
            showEnhancedEnrollDialog = false  // Close the enrollment dialog if it's open
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        SectionHeader(
            title = "Security",
            description = "Manage WebAuthn credentials for secure authentication"
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Authentication status check
        if (authState !is AuthService.AuthState.Authenticated) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = Color(0xFFFF5252).copy(alpha = 0.1f),
                shape = RoundedCornerShape(8.dp),
                elevation = 0.dp
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Outlined.Warning,
                        contentDescription = "Warning",
                        tint = Color(0xFFFF5252),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "You must be logged in to manage security settings",
                        fontSize = 14.sp,
                        color = Color.White
                    )
                }
            }
            return@Column
        }
        
        // WebAuthn / Touch ID Authentication
        SettingSection(
            title = "WebAuthn Authentication",
            description = if (touchIDSupported) 
                "Manage WebAuthn credentials for secure, passwordless authentication"
            else 
                "WebAuthn is not available on this device"
        ) {
            if (!touchIDSupported) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = Color(0xFFFF9800).copy(alpha = 0.1f),
                    shape = RoundedCornerShape(8.dp),
                    elevation = 0.dp
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Outlined.Warning,
                            contentDescription = "Warning",
                            tint = Color(0xFFFF9800),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "WebAuthn is not supported on this device. Please ensure you have biometric authentication enabled in System Preferences.",
                            fontSize = 14.sp,
                            color = Color.White
                        )
                    }
                }
            } else {
                if (isLoadingPasskeys) {
                    // Loading state
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        backgroundColor = BossDarkBackground,
                        shape = RoundedCornerShape(8.dp),
                        elevation = 2.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = BossDarkAccent,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = "Loading WebAuthn credentials...",
                                fontSize = 14.sp,
                                color = Color.White
                            )
                        }
                    }
                } else {
                    // Show WebAuthn capabilities if available
                    webAuthnCapabilities?.let { capabilities ->
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                            backgroundColor = BossDarkSurface,
                            shape = RoundedCornerShape(12.dp),
                            elevation = 2.dp,
                            border = BorderStroke(1.dp, BossDarkBorder.copy(alpha = 0.3f))
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp)
                            ) {
                                Text(
                                    text = "WebAuthn Capabilities",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White
                                )
                                
                                Spacer(modifier = Modifier.height(16.dp))
                                
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    WebAuthnCapabilityRow(
                                        icon = if (capabilities.hasJxBrowserEngine) Icons.Outlined.CheckCircle else Icons.Outlined.Error,
                                        label = "WebAuthn Support",
                                        status = if (capabilities.hasJxBrowserEngine) "Available" else "Not supported",
                                        enabled = capabilities.hasJxBrowserEngine
                                    )
                                    
                                    WebAuthnCapabilityRow(
                                        icon = if (capabilities.hasTouchId) Icons.Outlined.Fingerprint else Icons.Outlined.Error,
                                        label = "Platform Authenticator",
                                        status = if (capabilities.hasTouchId) "Touch ID/Windows Hello" else "Not available",
                                        enabled = capabilities.hasTouchId
                                    )
                                    
                                    WebAuthnCapabilityRow(
                                        icon = if (capabilities.hasSecurityKeySupport) Icons.Outlined.Usb else Icons.Outlined.Error,
                                        label = "Security Key Support",
                                        status = if (capabilities.hasSecurityKeySupport) "USB/NFC keys supported" else "Not supported",
                                        enabled = capabilities.hasSecurityKeySupport
                                    )
                                    
                                    WebAuthnCapabilityRow(
                                        icon = if (capabilities.hasHybridTransport) Icons.Outlined.Smartphone else Icons.Outlined.Error,
                                        label = "Cross-Device Authentication",
                                        status = if (capabilities.hasHybridTransport) "QR Code/Bluetooth available" else "Not supported",
                                        enabled = capabilities.hasHybridTransport
                                    )
                                    
                                    WebAuthnCapabilityRow(
                                        icon = if (capabilities.hasNfcSupport) Icons.Outlined.Nfc else Icons.Outlined.Error,
                                        label = "NFC Support",
                                        status = if (capabilities.hasNfcSupport) "NFC authenticators supported" else "Not supported",
                                        enabled = capabilities.hasNfcSupport
                                    )
                                }
                            }
                        }
                    }
                    
                    // Current WebAuthn status
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        backgroundColor = BossDarkBackground,
                        shape = RoundedCornerShape(8.dp),
                        elevation = 2.dp
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Outlined.Security,
                                        contentDescription = "WebAuthn",
                                        tint = BossDarkAccent,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column {
                                        Text(
                                            text = "WebAuthn Credentials",
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = Color.White
                                        )
                                        Text(
                                            text = if (passkeyFactors.isEmpty()) "No credentials enrolled" else "${passkeyFactors.size} credential(s) enrolled",
                                            fontSize = 14.sp,
                                            color = Color.Gray
                                        )
                                    }
                                }
                                
                                Spacer(modifier = Modifier.weight(1f))
                                
                                if (passkeyFactors.isEmpty()) {
                                    Button(
                                        onClick = {
                                            showEnhancedEnrollDialog = true
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            backgroundColor = BossDarkAccent,
                                            contentColor = Color.White
                                        )
                                    ) {
                                        Icon(
                                            Icons.Outlined.Add,
                                            contentDescription = "Setup",
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Set Up Passkey")
                                    }
                                }
                            }
                        }
                    }
                    
                    // List enrolled WebAuthn credentials
                    if (passkeyFactors.isNotEmpty()) {
                        passkeyFactors.forEach { passkey ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                backgroundColor = BossDarkBackground,
                                shape = RoundedCornerShape(8.dp),
                                elevation = 1.dp
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        when (getAuthenticatorTypeDescription(passkey)) {
                                            "Touch ID" -> Icons.Outlined.Fingerprint
                                            "Cross-device" -> Icons.Outlined.Smartphone
                                            "Security Key" -> Icons.Outlined.Usb
                                            else -> Icons.Outlined.Security
                                        },
                                        contentDescription = passkey.displayName,
                                        tint = BossDarkAccent,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = passkey.displayName,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = Color.White
                                        )
                                        Text(
                                            text = "Added ${formatTimestamp(passkey.createdAt)}",
                                            fontSize = 12.sp,
                                            color = Color.Gray
                                        )
                                        
                                        // Show additional details
                                        Text(
                                            text = formatPasskeyDetails(passkey),
                                            fontSize = 11.sp,
                                            color = Color.Gray.copy(alpha = 0.7f),
                                            maxLines = 1
                                        )
                                    }
                                    
                                    IconButton(
                                        onClick = { showRemovePasskeyDialog = passkey },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Outlined.Delete,
                                            contentDescription = "Remove",
                                            tint = Color(0xFFFF5252),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                        
                        // Add another WebAuthn credential button
                        Button(
                            onClick = {
                                showEnhancedEnrollDialog = true
                            },
                            colors = ButtonDefaults.buttonColors(
                                backgroundColor = BossDarkSurface,
                                contentColor = BossDarkAccent
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                Icons.Outlined.Add,
                                contentDescription = "Add",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Add Another Passkey")
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Security Tips
        SettingSection(
            title = "Security Best Practices",
            description = "Tips for secure authentication"
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = BossDarkAccent.copy(alpha = 0.05f),
                shape = RoundedCornerShape(8.dp),
                elevation = 0.dp
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    SecurityTip(
                        icon = Icons.Outlined.Key,
                        text = "Use WebAuthn for passwordless, secure authentication"
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    SecurityTip(
                        icon = Icons.Outlined.DeviceHub,
                        text = "Register multiple devices for redundancy"
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    SecurityTip(
                        icon = Icons.Outlined.Security,
                        text = "WebAuthn provides superior security over traditional passwords"
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    SecurityTip(
                        icon = Icons.Outlined.Fingerprint,
                        text = "Biometric authentication keeps your credentials secure on-device"
                    )
                }
            }
        }
        
        // Error message
        errorMessage?.let { message ->
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = Color(0xFFFF5252).copy(alpha = 0.1f),
                shape = RoundedCornerShape(8.dp),
                elevation = 0.dp
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Outlined.Error,
                        contentDescription = "Error",
                        tint = Color(0xFFFF5252),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = message,
                        fontSize = 14.sp,
                        color = Color.White
                    )
                }
            }
        }
    }
    
    // Show remove passkey confirmation dialog
    showRemovePasskeyDialog?.let { passkey ->
        AlertDialog(
            onDismissRequest = { showRemovePasskeyDialog = null },
            title = {
                Text(
                    "Remove WebAuthn Credential",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        "Are you sure you want to remove this WebAuthn credential?",
                        color = Color.Gray,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Card(
                        backgroundColor = BossDarkSurface,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                when (getAuthenticatorTypeDescription(passkey)) {
                                    "Touch ID" -> Icons.Outlined.Fingerprint
                                    "Cross-device" -> Icons.Outlined.Smartphone
                                    "Security Key" -> Icons.Outlined.Usb
                                    else -> Icons.Outlined.Security
                                },
                                contentDescription = passkey.displayName,
                                tint = BossDarkAccent,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = passkey.displayName,
                                color = Color.White,
                                fontSize = 14.sp
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Warning: You will no longer be able to use this credential to sign in.",
                        color = Color(0xFFFF5252),
                        fontSize = 12.sp
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            // Use database ID for deletion, fallback to credential ID if not available
                            val idToDelete = passkey.id ?: passkey.credentialId
                            AuthService.deletePasskey(idToDelete).fold(
                                onSuccess = {
                                    // Trigger refresh of passkey list
                                    refreshKey++
                                    showRemovePasskeyDialog = null
                                },
                                onFailure = { error ->
                                    errorMessage = "Failed to remove WebAuthn credential: ${error.message}"
                                    showRemovePasskeyDialog = null
                                }
                            )
                        }
                    }
                ) {
                    Text("Remove", color = Color(0xFFFF5252))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemovePasskeyDialog = null }) {
                    Text("Cancel", color = Color.Gray)
                }
            },
            backgroundColor = BossDarkSurface,
            contentColor = Color.White
        )
    }
    
    // Show enhanced enroll dialog
    if (showEnhancedEnrollDialog) {
        PasskeyEnrollmentDialog(
            onDismiss = { showEnhancedEnrollDialog = false },
            onSuccess = {
                showEnhancedEnrollDialog = false
                // Trigger refresh of passkey list after successful enrollment
                refreshKey++
            },
            onError = { error ->
                errorMessage = error
                showEnhancedEnrollDialog = false
            }
        )
    }

    // Show embedded browser for passkey registration
    if (showEmbeddedBrowser) {
        ai.rever.boss.components.auth.screens.PasskeyBrowserScreen(
            url = passkeyBrowserUrl,
            sessionId = passkeyBrowserSessionId,
            onSuccess = {
                println("SecuritySettings: Passkey browser registration successful")
                showEmbeddedBrowser = false
                // Trigger refresh of passkey list after successful registration
                refreshKey++
            },
            onBack = {
                println("SecuritySettings: User cancelled passkey registration from browser")
                showEmbeddedBrowser = false
            }
        )
    }
}

@Composable
private fun PasskeyEnrollmentDialog(
    onDismiss: () -> Unit,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
) {
    var isEnrolling by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Set Up Passkey",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                if (isEnrolling) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = BossDarkAccent,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            "Setting up your passkey...",
                            color = Color.Gray,
                            fontSize = 14.sp
                        )
                    }
                } else {
                    Text(
                        "Set up a passkey for secure, passwordless authentication using Touch ID or Windows Hello.",
                        color = Color.Gray,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "• No passwords to remember",
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "• More secure than traditional passwords",
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "• Works across all your devices",
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                }
            }
        },
        confirmButton = {
            if (!isEnrolling) {
                Button(
                    onClick = {
                        isEnrolling = true
                        coroutineScope.launch {
                            try {
                                // Attempt passkey registration
                                AuthService.registerPasskey().fold(
                                    onSuccess = { 
                                        isEnrolling = false
                                        onSuccess() 
                                    },
                                    onFailure = { error ->
                                        isEnrolling = false
                                        onError("Failed to enroll passkey: ${error.message}")
                                    }
                                )
                            } catch (e: Exception) {
                                isEnrolling = false
                                onError("Passkey enrollment failed: ${e.message}")
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = BossDarkAccent,
                        contentColor = Color.White
                    )
                ) {
                    Text("Set Up Passkey")
                }
            }
        },
        dismissButton = {
            if (!isEnrolling) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = Color.Gray)
                }
            }
        },
        backgroundColor = BossDarkSurface,
        contentColor = Color.White
    )
}

@Composable
private fun SecurityTip(
    icon: ImageVector,
    text: String
) {
    Row(
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = BossDarkAccent,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            fontSize = 13.sp,
            color = Color.White.copy(alpha = 0.9f),
            lineHeight = 18.sp
        )
    }
}

/**
 * Composable for displaying WebAuthn capability rows
 */
@Composable
private fun WebAuthnCapabilityRow(
    icon: ImageVector,
    label: String,
    status: String,
    enabled: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = if (enabled) Color(0xFF4CAF50) else Color(0xFFFF5252),
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = label,
                fontSize = 14.sp,
                color = Color.White
            )
        }
        Text(
            text = status,
            fontSize = 12.sp,
            color = if (enabled) Color(0xFF4CAF50) else Color.Gray
        )
    }
}

/**
 * Helper functions for enhanced passkey display
 */
private fun getAuthenticatorTypeDescription(passkey: PasskeyInfo): String {
    return when {
        passkey.transports.contains("usb") -> "USB Security Key"
        passkey.transports.contains("nfc") -> "NFC Authenticator"
        passkey.transports.contains("hybrid") -> "Cross-device Authentication"
        else -> "Touch ID"
    }
}

private fun formatPasskeyDetails(passkey: PasskeyInfo): String {
    val status = "Verified"
    val createdDate = try {
        val date = java.util.Date(passkey.createdAt)
        java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault()).format(date)
    } catch (e: Exception) {
        "Unknown date"
    }
    
    val lastUsed = if (passkey.lastUsed != null) {
        try {
            val date = java.util.Date(passkey.lastUsed)
            val now = System.currentTimeMillis()
            val diffDays = (now - passkey.lastUsed) / (24 * 60 * 60 * 1000)
            when {
                diffDays == 0L -> "today"
                diffDays == 1L -> "yesterday"  
                diffDays < 30 -> "${diffDays} days ago"
                else -> java.text.SimpleDateFormat("MMM dd", java.util.Locale.getDefault()).format(date)
            }
        } catch (e: Exception) {
            "recently"
        }
    } else {
        "never used"
    }
    
    return "$status • Created $createdDate • Last used $lastUsed"
}

/**
 * Helper function to format timestamps for display in the Touch ID credentials list
 */
private fun formatTimestamp(timestamp: Long): String {
    return try {
        val date = java.util.Date(timestamp)
        val format = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault())
        format.format(date)
    } catch (e: Exception) {
        "Unknown"
    }
}
