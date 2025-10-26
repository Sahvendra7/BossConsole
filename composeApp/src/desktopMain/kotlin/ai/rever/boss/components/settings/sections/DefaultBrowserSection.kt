package ai.rever.boss.components.settings.sections

import BossDarkAccent
import BossDarkBackground
import BossDarkBorder
import BossDarkSurface
import ai.rever.boss.components.settings.shared.SettingSection
import ai.rever.boss.utils.DefaultBrowserManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * Default Browser section for Fluck Browser settings
 *
 * Allows users to:
 * - Check if BOSS is the default browser
 * - Set BOSS as the default browser
 * - View platform-specific instructions
 */
@Composable
fun DefaultBrowserSection() {
    var isDefault by remember { mutableStateOf<Boolean?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showSuccessDialog by remember { mutableStateOf(false) }
    var showInstructionsDialog by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()

    // Check status on mount
    LaunchedEffect(Unit) {
        isLoading = true
        errorMessage = null

        val result = DefaultBrowserManager.isDefaultBrowser()
        isLoading = false

        result.fold(
            onSuccess = { isDefault = it },
            onFailure = { error ->
                errorMessage = error.message
                isDefault = null
            }
        )
    }

    SettingSection(
        title = "Default Browser",
        description = "Make BOSS your default web browser"
    ) {
        // Status Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            backgroundColor = BossDarkBackground,
            shape = RoundedCornerShape(8.dp),
            elevation = 2.dp
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                // Status Display
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Status",
                            color = Color.Gray,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))

                        when {
                            isLoading -> {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = Color.Gray
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Checking...",
                                        color = Color.Gray,
                                        fontSize = 16.sp
                                    )
                                }
                            }
                            errorMessage != null -> {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Outlined.Error,
                                        contentDescription = "Error",
                                        tint = Color(0xFFFF6B6B),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Error checking status",
                                        color = Color(0xFFFF6B6B),
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                            isDefault == true -> {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Outlined.CheckCircle,
                                        contentDescription = "Default",
                                        tint = BossDarkAccent,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "BOSS is your default browser",
                                        color = Color.White,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                            isDefault == false -> {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Outlined.Cancel,
                                        contentDescription = "Not Default",
                                        tint = Color.Gray,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "BOSS is not your default browser",
                                        color = Color.Gray,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }

                    // Action Buttons
                    Row {
                        // Refresh button
                        IconButton(
                            onClick = {
                                coroutineScope.launch {
                                    isLoading = true
                                    errorMessage = null

                                    val result = DefaultBrowserManager.isDefaultBrowser()
                                    isLoading = false

                                    result.fold(
                                        onSuccess = { isDefault = it },
                                        onFailure = { error ->
                                            errorMessage = error.message
                                            isDefault = null
                                        }
                                    )
                                }
                            }
                        ) {
                            Icon(
                                Icons.Outlined.Refresh,
                                contentDescription = "Refresh",
                                tint = Color.Gray
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Set as default button
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    isLoading = true
                                    errorMessage = null

                                    val result = DefaultBrowserManager.setAsDefaultBrowser()
                                    isLoading = false

                                    result.fold(
                                        onSuccess = { wasSetProgrammatically ->
                                            if (wasSetProgrammatically) {
                                                // Successfully set programmatically (macOS/Linux)
                                                isDefault = true
                                                showSuccessDialog = true
                                            } else {
                                                // User action required (Windows)
                                                showInstructionsDialog = true
                                            }
                                        },
                                        onFailure = { error ->
                                            errorMessage = error.message
                                        }
                                    )
                                }
                            },
                            enabled = !isLoading && isDefault != true,
                            colors = ButtonDefaults.buttonColors(
                                backgroundColor = BossDarkAccent,
                                contentColor = Color.White,
                                disabledBackgroundColor = Color.Gray.copy(alpha = 0.3f),
                                disabledContentColor = Color.Gray
                            ),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Setting...")
                            } else {
                                Icon(
                                    Icons.Outlined.OpenInNew,
                                    contentDescription = "Set",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Set as Default Browser")
                            }
                        }
                    }
                }

                // Error message
                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Error: $errorMessage",
                        color = Color(0xFFFF6B6B),
                        fontSize = 12.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Platform-specific info card
        Card(
            modifier = Modifier.fillMaxWidth(),
            backgroundColor = BossDarkAccent.copy(alpha = 0.1f),
            shape = RoundedCornerShape(8.dp),
            elevation = 0.dp
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    Icons.Outlined.Info,
                    contentDescription = "Info",
                    tint = BossDarkAccent,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Platform: ${DefaultBrowserManager.getPlatformName()}",
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.9f),
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = when {
                            DefaultBrowserManager.getPlatformName() == "macOS" ->
                                "BOSS will attempt to set itself as default automatically"
                            DefaultBrowserManager.getPlatformName() == "Windows" ->
                                "Windows requires manual selection in Settings"
                            else ->
                                "Uses XDG standards for Linux desktop environments"
                        },
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }

    // Success Dialog
    if (showSuccessDialog) {
        AlertDialog(
            onDismissRequest = { showSuccessDialog = false },
            title = {
                Text(
                    "Success",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    "BOSS has been set as your default web browser. Links will now open in BOSS.",
                    color = Color.Gray,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                TextButton(onClick = { showSuccessDialog = false }) {
                    Text("OK", color = BossDarkAccent)
                }
            },
            backgroundColor = BossDarkSurface,
            contentColor = Color.White
        )
    }

    // Instructions Dialog (Windows)
    if (showInstructionsDialog) {
        AlertDialog(
            onDismissRequest = { showInstructionsDialog = false },
            title = {
                Text(
                    "Complete Setup in Windows Settings",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        "Windows Settings has been opened. Please complete these steps:",
                        color = Color.Gray,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "1. Scroll down to \"Web browser\"\n" +
                        "2. Click on the current browser\n" +
                        "3. Select \"BOSS Console\" from the list\n" +
                        "4. Close Settings",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 14.sp,
                        lineHeight = 22.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "After completing these steps, click \"Refresh\" to verify.",
                        color = Color.Gray,
                        fontSize = 12.sp,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showInstructionsDialog = false }) {
                    Text("Got it", color = BossDarkAccent)
                }
            },
            backgroundColor = BossDarkSurface,
            contentColor = Color.White
        )
    }
}
