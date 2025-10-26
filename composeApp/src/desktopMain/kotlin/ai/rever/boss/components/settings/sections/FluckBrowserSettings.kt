package ai.rever.boss.components.settings.sections

import BossDarkAccent
import BossDarkBackground
import BossDarkBorder
import BossDarkSurface
import androidx.compose.foundation.background
import ai.rever.boss.components.plugin.tab_types.fluck.BrowserSettings
import ai.rever.boss.components.plugin.tab_types.fluck.BrowserSettingsManager
import ai.rever.boss.components.settings.shared.DropdownSelector
import ai.rever.boss.components.settings.shared.SectionHeader
import ai.rever.boss.components.settings.shared.SettingSection
import ai.rever.boss.utils.ApplicationRestarter
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
fun FluckBrowserSettings() {
    var userAgent by remember { mutableStateOf(BrowserSettings.userAgent ?: "Default") }
    var currentProfile by remember { mutableStateOf(BrowserSettings.currentProfile) }
    var customUserAgent by remember { mutableStateOf(BrowserSettings.customUserAgent ?: "") }

    val userAgents = listOf("Default", "Chrome", "Firefox", "Safari", "Edge", "Custom")
    var showRestartDialog by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        SectionHeader(
            title = "Fluck Browser Settings",
            description = "Configure browser behavior and profiles"
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // User Agent Selection
        SettingSection(title = "User Agent", description = "Change how websites see your browser") {
            DropdownSelector(
                label = "User Agent",
                value = userAgent,
                options = userAgents,
                onValueChange = { 
                    userAgent = it
                    BrowserSettings.userAgent = if (it == "Default") null else it
                },
                modifier = Modifier.width(400.dp)
            )
            
            if (userAgent == "Custom") {
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = customUserAgent,
                    onValueChange = { 
                        customUserAgent = it
                        BrowserSettings.customUserAgent = it
                    },
                    label = { Text("Custom User Agent String") },
                    placeholder = { Text("Mozilla/5.0 (Windows NT 10.0; Win64; x64)...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                    maxLines = 3,
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        textColor = Color.White,
                        focusedBorderColor = BossDarkAccent,
                        unfocusedBorderColor = BossDarkBorder,
                        focusedLabelColor = BossDarkAccent,
                        unfocusedLabelColor = Color.Gray,
                        placeholderColor = Color.Gray.copy(alpha = 0.5f)
                    )
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Note about restart requirement
        Card(
            modifier = Modifier.fillMaxWidth(),
            backgroundColor = BossDarkAccent.copy(alpha = 0.1f),
            shape = RoundedCornerShape(8.dp),
            elevation = 0.dp
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Outlined.Info,
                    contentDescription = "Info",
                    tint = BossDarkAccent,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Note: Application restart required for browser settings changes to take effect",
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))

        // Default Browser Section
        DefaultBrowserSection()

        Spacer(modifier = Modifier.height(32.dp))

        // Profile Management Section
        ProfileManagementSection(
            currentProfile = currentProfile,
            onProfileChange = { currentProfile = it }
        )
    }
    
    // Apply settings button
    Spacer(modifier = Modifier.height(32.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        Button(
            onClick = {
                // Check what changed
                val profileChanged = BrowserSettings.currentProfile != currentProfile
                val userAgentChanged = BrowserSettings.userAgent != (if (userAgent == "Default") null else userAgent) ||
                    (userAgent == "Custom" && BrowserSettings.customUserAgent != customUserAgent)
                
                // Apply settings
                BrowserSettings.currentProfile = currentProfile
                BrowserSettings.userAgent = if (userAgent == "Default") null else userAgent
                if (userAgent == "Custom") {
                    BrowserSettings.customUserAgent = customUserAgent
                }
                
                // Save settings
                coroutineScope.launch {
                    BrowserSettingsManager.saveSettings()
                }
                
                // Show restart dialog if significant changes were made
                if (profileChanged || userAgentChanged) {
                    showRestartDialog = true
                }
            },
            colors = ButtonDefaults.buttonColors(
                backgroundColor = BossDarkAccent,
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(6.dp)
        ) {
            Text("Apply Settings")
        }
    }
    
    // Restart dialog
    if (showRestartDialog) {
        AlertDialog(
            onDismissRequest = { showRestartDialog = false },
            title = {
                Text(
                    "Restart Required",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        "Browser settings have been changed and require an application restart to take effect.",
                        color = Color.Gray,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Would you like to restart the application now?",
                        color = Color.Gray,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Make sure to save any unsaved work before restarting.",
                        color = Color.Gray.copy(alpha = 0.7f),
                        fontSize = 12.sp
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRestartDialog = false
                        // Restart the application
                        ApplicationRestarter.scheduleRestart(delayMillis = 500)
                    }
                ) {
                    Text("Restart Now", color = BossDarkAccent)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showRestartDialog = false }
                ) {
                    Text("Later", color = Color.Gray)
                }
            },
            backgroundColor = BossDarkSurface,
            contentColor = Color.White
        )
    }
}
