package ai.rever.boss.components.windows

import BossDarkBackground
import BossDarkSurface
import BossDarkBorder
import BossDarkAccent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Error
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeviceHub
import androidx.compose.material.icons.outlined.Update
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import ai.rever.boss.components.plugin.tab_types.fluck.BrowserSettings
import ai.rever.boss.components.plugin.tab_types.fluck.BrowserSettingsManager
import ai.rever.boss.components.plugin.tab_types.CodeEditorSettings
import ai.rever.boss.components.plugin.tab_types.CodeEditorSettingsManager
import ai.rever.boss.components.plugin.panels.bottom.terminal.TerminalSettings
import ai.rever.boss.components.plugin.panels.bottom.terminal.TerminalSettingsManager
import ai.rever.boss.components.plugin.panels.right_top.*
import ai.rever.boss.utils.ApplicationRestarter
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.material.CircularProgressIndicator
import ai.rever.boss.updater.UpdateSettingsSection
import ai.rever.boss.services.supabase.AuthService
import ai.rever.boss.services.supabase.AuthState
import ai.rever.boss.services.supabase.TwoFactorInfo
import ai.rever.boss.components.dialogs.TwoFactorEnrollDialog
import ai.rever.boss.components.dialogs.TwoFactorVerifyDialog

enum class SettingsSection {
    FLUCK, CODE_EDITOR, TERMINAL, LLM_PROVIDERS, UPDATES, TWO_FACTOR_AUTH
}

@Composable
actual fun SettingsWindow(
    onClose: () -> Unit
) {
    var isOpen by remember { mutableStateOf(true) }
    
    if (isOpen) {
        Window(
            onCloseRequest = {
                isOpen = false
                onClose()
            },
            title = "BOSS Settings",
            state = rememberWindowState(
                size = DpSize(1200.dp, 800.dp),
                position = WindowPosition.Aligned(Alignment.Center)
            )
        ) {
            SettingsContent()
        }
    }
}

@Composable
private fun SettingsContent() {
    var selectedSection by remember { mutableStateOf(SettingsSection.FLUCK) }
    
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = BossDarkBackground
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            SettingsHeader()
            
            Divider(color = BossDarkBorder, thickness = 1.dp)
            
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(BossDarkSurface)
            ) {
                // Sidebar with sections
                SettingsSidebar(
                    selectedSection = selectedSection,
                    onSectionChange = { selectedSection = it }
                )
                
                // Vertical divider
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(1.dp)
                        .background(BossDarkBorder)
                )
                
                // Content area
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp)
                ) {
                    when (selectedSection) {
                        SettingsSection.FLUCK -> FluckSettings()
                        SettingsSection.CODE_EDITOR -> CodeEditorSettings()
                        SettingsSection.TERMINAL -> TerminalSettings()
                        SettingsSection.LLM_PROVIDERS -> LLMProvidersSettings()
                        SettingsSection.UPDATES -> UpdatesSettings()
                        SettingsSection.TWO_FACTOR_AUTH -> TwoFactorAuthSettings()
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsHeader() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = BossDarkBackground,
        elevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Settings",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

@Composable
private fun SettingsSidebar(
    selectedSection: SettingsSection,
    onSectionChange: (SettingsSection) -> Unit
) {
    Column(
        modifier = Modifier
            .width(280.dp)
            .fillMaxHeight()
            .background(BossDarkBackground)
            .padding(16.dp)
    ) {
        SidebarItem(
            icon = Icons.Outlined.Language,
            title = "Fluck Browser",
            subtitle = "User agent, profiles",
            isSelected = selectedSection == SettingsSection.FLUCK,
            onClick = { onSectionChange(SettingsSection.FLUCK) }
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        SidebarItem(
            icon = Icons.Outlined.Code,
            title = "Code Editor",
            subtitle = "Font, size, theme",
            isSelected = selectedSection == SettingsSection.CODE_EDITOR,
            onClick = { onSectionChange(SettingsSection.CODE_EDITOR) }
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        SidebarItem(
            icon = Icons.Outlined.Terminal,
            title = "Terminal",
            subtitle = "Shell, colors, startup",
            isSelected = selectedSection == SettingsSection.TERMINAL,
            onClick = { onSectionChange(SettingsSection.TERMINAL) }
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        SidebarItem(
            icon = Icons.Outlined.AutoAwesome,
            title = "LLM Providers",
            subtitle = "API keys, models, settings",
            isSelected = selectedSection == SettingsSection.LLM_PROVIDERS,
            onClick = { onSectionChange(SettingsSection.LLM_PROVIDERS) }
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        SidebarItem(
            icon = Icons.Outlined.SystemUpdate,
            title = "Updates",
            subtitle = "Auto-update, version info",
            isSelected = selectedSection == SettingsSection.UPDATES,
            onClick = { onSectionChange(SettingsSection.UPDATES) }
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        SidebarItem(
            icon = Icons.Outlined.Security,
            title = "Two-Factor Auth",
            subtitle = "Manage 2FA settings",
            isSelected = selectedSection == SettingsSection.TWO_FACTOR_AUTH,
            onClick = { onSectionChange(SettingsSection.TWO_FACTOR_AUTH) }
        )
    }
}

@Composable
private fun SidebarItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (isSelected) BossDarkAccent.copy(alpha = 0.1f) else Color.Transparent
    val borderColor = if (isSelected) BossDarkAccent else Color.Transparent
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable { onClick() },
        color = backgroundColor,
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (isSelected) BossDarkAccent else Color.Gray,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = title,
                    color = if (isSelected) Color.White else Color.Gray,
                    fontSize = 15.sp,
                    fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
                )
                Text(
                    text = subtitle,
                    color = Color.Gray.copy(alpha = 0.7f),
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun FluckSettings() {
    var userAgent by remember { mutableStateOf(BrowserSettings.userAgent ?: "Default") }
    var currentProfile by remember { mutableStateOf(BrowserSettings.currentProfile) }
    var customUserAgent by remember { mutableStateOf(BrowserSettings.customUserAgent ?: "") }
    var showNewProfileDialog by remember { mutableStateOf(false) }
    var showSwitchProfileMenu by remember { mutableStateOf(false) }
    var newProfileName by remember { mutableStateOf("") }
    
    val userAgents = listOf("Default", "Chrome", "Firefox", "Safari", "Edge", "Custom")
    val availableProfiles = remember { 
        mutableStateListOf<String>().also { 
            it.addAll(BrowserSettings.availableProfiles)
        }
    }
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
        
        // Profile Management
        SettingSection(title = "Browser Profiles", description = "Manage browser data and sessions") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = BossDarkBackground,
                shape = RoundedCornerShape(8.dp),
                elevation = 2.dp
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Current Profile",
                                color = Color.Gray,
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = currentProfile,
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        
                        Row {
                            Box {
                                Button(
                                    onClick = { showSwitchProfileMenu = true },
                                    colors = ButtonDefaults.buttonColors(
                                        backgroundColor = BossDarkAccent,
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Icon(
                                        Icons.Outlined.SwapHoriz,
                                        contentDescription = "Switch",
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Switch Profile")
                                }
                                
                                DropdownMenu(
                                    expanded = showSwitchProfileMenu,
                                    onDismissRequest = { showSwitchProfileMenu = false },
                                    modifier = Modifier.background(BossDarkSurface)
                                ) {
                                    availableProfiles.forEach { profile ->
                                        DropdownMenuItem(
                                            onClick = {
                                                currentProfile = profile
                                                BrowserSettings.currentProfile = profile
                                                showSwitchProfileMenu = false
                                                // Note: Browser engine will need to be restarted for profile change to take effect
                                            },
                                            modifier = Modifier.background(
                                                if (profile == currentProfile) 
                                                    BossDarkAccent.copy(alpha = 0.1f) 
                                                else BossDarkSurface
                                            )
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = profile,
                                                    color = Color.White
                                                )
                                                if (profile == currentProfile) {
                                                    Icon(
                                                        Icons.Outlined.Check,
                                                        contentDescription = "Selected",
                                                        tint = BossDarkAccent,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            
                            Spacer(modifier = Modifier.width(12.dp))
                            
                            Button(
                                onClick = { showNewProfileDialog = true },
                                colors = ButtonDefaults.buttonColors(
                                    backgroundColor = BossDarkAccent,
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.Add,
                                    contentDescription = "Add",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("New Profile")
                            }
                        }
                    }
                }
            }
        }
    }
    
    // New Profile Dialog
    if (showNewProfileDialog) {
        AlertDialog(
            onDismissRequest = { 
                showNewProfileDialog = false
                newProfileName = ""
            },
            title = {
                Text(
                    "Create New Profile",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        "Enter a name for the new browser profile:",
                        color = Color.Gray,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = newProfileName,
                        onValueChange = { newProfileName = it },
                        label = { Text("Profile Name") },
                        placeholder = { Text("e.g., Work, Personal, Development") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
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
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newProfileName.isNotBlank()) {
                            val profileName = "browser-profile-${newProfileName.replace(" ", "-").lowercase()}"
                            availableProfiles.add(profileName)
                            BrowserSettings.availableProfiles.add(profileName)
                            currentProfile = profileName
                            showNewProfileDialog = false
                            newProfileName = ""
                            
                            // Save settings
                            coroutineScope.launch {
                                BrowserSettingsManager.saveSettings()
                            }
                        }
                    },
                    enabled = newProfileName.isNotBlank()
                ) {
                    Text(
                        "Create",
                        color = if (newProfileName.isNotBlank()) BossDarkAccent else Color.Gray
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showNewProfileDialog = false
                        newProfileName = ""
                    }
                ) {
                    Text("Cancel", color = Color.Gray)
                }
            },
            backgroundColor = BossDarkSurface,
            contentColor = Color.White
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

@Composable
private fun CodeEditorSettings() {
    var selectedFont by remember { mutableStateOf(CodeEditorSettings.fontFamily) }
    var fontSize by remember { mutableStateOf(CodeEditorSettings.fontSize.toString()) }
    var theme by remember { mutableStateOf(CodeEditorSettings.theme) }
    val coroutineScope = rememberCoroutineScope()
    
    val fonts = listOf("JetBrains Mono", "Fira Code", "Source Code Pro", "Consolas", "Monaco", "Menlo")
    val fontSizes = listOf("10", "11", "12", "13", "14", "15", "16", "18", "20", "22", "24")
    val themes = listOf("Dark", "Light", "Dracula", "Monokai", "Solarized Dark", "Solarized Light")
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        SectionHeader(
            title = "Code Editor Settings",
            description = "Customize your coding environment"
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Font Selection
        SettingSection(title = "Typography", description = "Font family and size for code editing") {
            Row {
                DropdownSelector(
                    label = "Font Family",
                    value = selectedFont,
                    options = fonts,
                    onValueChange = { selectedFont = it },
                    modifier = Modifier.width(300.dp)
                )
                
                Spacer(modifier = Modifier.width(16.dp))
                
                DropdownSelector(
                    label = "Font Size",
                    value = fontSize,
                    options = fontSizes,
                    onValueChange = { fontSize = it },
                    modifier = Modifier.width(150.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Theme Selection
        SettingSection(title = "Theme", description = "Color scheme for syntax highlighting") {
            DropdownSelector(
                label = "Editor Theme",
                value = theme,
                options = themes,
                onValueChange = { theme = it },
                modifier = Modifier.width(300.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Preview
        SettingSection(title = "Preview", description = "See how your code will look") {
            // Compute preview colors based on selected theme
            val previewBgColor = when (theme) {
                "Light" -> Color(0xFF_FFFFFF)
                "Dracula" -> Color(0xFF_282A36)
                "Monokai" -> Color(0xFF_272822)
                "Solarized Dark" -> Color(0xFF_002B36)
                "Solarized Light" -> Color(0xFF_FDF6E3)
                else -> Color(0xFF_1E1E1E)
            }
            val previewTextColor = when (theme) {
                "Light" -> Color(0xFF_000000)
                "Dracula" -> Color(0xFF_F8F8F2)
                "Monokai" -> Color(0xFF_F8F8F2)
                "Solarized Dark" -> Color(0xFF_839496)
                "Solarized Light" -> Color(0xFF_657B83)
                else -> Color(0xFF_D4D4D4)
            }
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = previewBgColor,
                shape = RoundedCornerShape(8.dp),
                elevation = 2.dp
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .padding(20.dp)
                ) {
                    Text(
                        text = """
                            fun main() {
                                val message = "Hello, BOSS!"
                                println(message)
                                
                                // Configure your editor
                                val settings = EditorSettings(
                                    font = "$selectedFont",
                                    size = $fontSize
                                )
                            }
                        """.trimIndent(),
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        fontSize = fontSize.toIntOrNull()?.sp ?: 14.sp,
                        color = previewTextColor,
                        lineHeight = (fontSize.toIntOrNull()?.times(1.4))?.sp ?: 19.6.sp
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Apply settings button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Button(
                onClick = {
                    // Apply settings
                    CodeEditorSettings.fontFamily = selectedFont
                    CodeEditorSettings.fontSize = fontSize.toIntOrNull() ?: 14
                    CodeEditorSettings.theme = theme
                    
                    // Save settings
                    coroutineScope.launch {
                        CodeEditorSettingsManager.saveSettings()
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
    }
}

@Composable
private fun TerminalSettings() {
    var selectedFont by remember { mutableStateOf(TerminalSettings.fontFamily) }
    var fontSize by remember { mutableStateOf(TerminalSettings.fontSize.toString()) }
    var colorScheme by remember { mutableStateOf(TerminalSettings.colorScheme) }
    var shell by remember { mutableStateOf(TerminalSettings.shell) }
    var startupCommand by remember { mutableStateOf(TerminalSettings.startupCommand) }
    val coroutineScope = rememberCoroutineScope()
    
    val fonts = listOf("MesloLGS NF", "JetBrains Mono", "Fira Code", "Source Code Pro", "Consolas", "Menlo")
    val fontSizes = listOf("10", "11", "12", "13", "14", "15", "16", "18", "20", "22", "24")
    val colorSchemes = listOf("BOSS Dark", "BOSS Light", "Solarized Dark", "Solarized Light", "Dracula", "Tomorrow Night")
    val shells = listOf("/bin/zsh", "/bin/bash", "/bin/sh", "/usr/local/bin/fish")
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        SectionHeader(
            title = "Terminal Settings",
            description = "Configure your terminal environment"
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Font Selection
        SettingSection(title = "Typography", description = "Font settings for terminal display") {
            Row {
                DropdownSelector(
                    label = "Font Family",
                    value = selectedFont,
                    options = fonts,
                    onValueChange = { selectedFont = it },
                    modifier = Modifier.width(300.dp)
                )
                
                Spacer(modifier = Modifier.width(16.dp))
                
                DropdownSelector(
                    label = "Font Size",
                    value = fontSize,
                    options = fontSizes,
                    onValueChange = { fontSize = it },
                    modifier = Modifier.width(150.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Appearance
        SettingSection(title = "Appearance", description = "Visual settings") {
            DropdownSelector(
                label = "Color Scheme",
                value = colorScheme,
                options = colorSchemes,
                onValueChange = { colorScheme = it },
                modifier = Modifier.width(300.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Shell Configuration
        SettingSection(title = "Shell", description = "Default shell and startup behavior") {
            Column {
                DropdownSelector(
                    label = "Default Shell",
                    value = shell,
                    options = shells,
                    onValueChange = { shell = it },
                    modifier = Modifier.width(400.dp)
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = startupCommand,
                    onValueChange = { startupCommand = it },
                    label = { Text("Startup Command") },
                    placeholder = { Text("Commands to run when terminal starts (optional)") },
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
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Apply settings button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Button(
                onClick = {
                    // Apply settings
                    TerminalSettings.fontFamily = selectedFont
                    TerminalSettings.fontSize = fontSize.toIntOrNull() ?: 14
                    TerminalSettings.colorScheme = colorScheme
                    TerminalSettings.shell = shell
                    TerminalSettings.startupCommand = startupCommand
                    
                    // Save settings
                    coroutineScope.launch {
                        TerminalSettingsManager.saveSettings()
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
    }
}

@Composable
private fun LLMProvidersSettings() {
    var selectedProvider by remember { mutableStateOf(LLMSettings.selectedProvider) }
    var selectedModelId by remember { mutableStateOf(LLMSettings.selectedModel.modelId) }
    val apiKeys = remember { mutableStateMapOf<LLMProvider, String>().apply {
        LLMProvider.values().forEach { provider ->
            LLMSettings.getApiKey(provider)?.let { put(provider, it) }
        }
    }}
    var customEndpoint by remember { mutableStateOf(LLMSettings.customEndpoint ?: "") }
    var temperature by remember { mutableStateOf(LLMSettings.temperature) }
    var maxTokens by remember { mutableStateOf(LLMSettings.maxTokens.toString()) }
    var enableStreaming by remember { mutableStateOf(LLMSettings.enableStreaming) }
    var enableCaching by remember { mutableStateOf(LLMSettings.enableCaching) }
    var showApiKey by remember { mutableStateOf(false) }
    var showOAuthDialog by remember { mutableStateOf<LLMProvider?>(null) }
    var settingsFeedbackMessage by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    
    // Dynamic models
    val availableModels by LLMModelFetcher.availableModels.collectAsState()
    val isLoadingModels by LLMModelFetcher.isLoading.collectAsState()
    val modelError by LLMModelFetcher.lastError.collectAsState()
    
    // Load models on first launch
    LaunchedEffect(Unit) {
        LLMModelFetcher.fetchLatestModels()
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        SectionHeader(
            title = "LLM Provider Settings",
            description = "Configure AI models and API keys for automation"
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Provider Selection
        SettingSection(title = "Provider Selection", description = "Choose your preferred AI provider") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = BossDarkBackground,
                shape = RoundedCornerShape(8.dp),
                elevation = 2.dp
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    // Provider tabs
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LLMProvider.values().forEach { provider ->
                            ProviderTab(
                                provider = provider,
                                isSelected = selectedProvider == provider,
                                hasApiKey = apiKeys[provider]?.isNotBlank() == true,
                                onClick = { 
                                    selectedProvider = provider
                                    // Update selected model to first available for this provider
                                    val providerModels = availableModels[provider.name] ?: LLMModelFetcher.getModelsForProvider(provider)
                                    if (providerModels.isNotEmpty()) {
                                        selectedModelId = providerModels.first().id
                                    }
                                }
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(20.dp))
                    
                    // API Key input for selected provider
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "${selectedProvider.displayName} API Key",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White
                                )
                                
                                // Show if key is from environment
                                val envKey = when (selectedProvider) {
                                    LLMProvider.ANTHROPIC -> getEnvironmentVariable("ANTHROPIC_API_KEY")
                                    LLMProvider.OPENAI -> getEnvironmentVariable("OPENAI_API_KEY")
                                    LLMProvider.TOGETHER -> getEnvironmentVariable("TOGETHER_API_KEY")
                                    LLMProvider.CUSTOM -> getEnvironmentVariable("CUSTOM_LLM_API_KEY")
                                }
                                
                                if (!envKey.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Card(
                                        backgroundColor = Color(0xFF4CAF50).copy(alpha = 0.1f),
                                        contentColor = Color(0xFF4CAF50),
                                        shape = RoundedCornerShape(4.dp),
                                        elevation = 0.dp
                                    ) {
                                        Text(
                                            text = "From Environment",
                                            fontSize = 11.sp,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            
                            if (selectedProvider != LLMProvider.CUSTOM) {
                                TextButton(
                                    onClick = { showOAuthDialog = selectedProvider }
                                ) {
                                    Icon(
                                        Icons.Outlined.Login,
                                        contentDescription = "Sign In",
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Sign In", fontSize = 12.sp)
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        val envKey = when (selectedProvider) {
                            LLMProvider.ANTHROPIC -> getEnvironmentVariable("ANTHROPIC_API_KEY")
                            LLMProvider.OPENAI -> getEnvironmentVariable("OPENAI_API_KEY")
                            LLMProvider.TOGETHER -> getEnvironmentVariable("TOGETHER_API_KEY")
                            LLMProvider.CUSTOM -> getEnvironmentVariable("CUSTOM_LLM_API_KEY")
                        }
                        
                        OutlinedTextField(
                            value = apiKeys[selectedProvider] ?: "",
                            onValueChange = { apiKeys[selectedProvider] = it },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = envKey.isNullOrBlank(), // Disable if env key exists
                            placeholder = { 
                                Text(
                                    if (!envKey.isNullOrBlank()) {
                                        "Using environment variable"
                                    } else {
                                        when (selectedProvider) {
                                            LLMProvider.ANTHROPIC -> "sk-ant-..."
                                            LLMProvider.OPENAI -> "sk-..."
                                            LLMProvider.TOGETHER -> "together-..."
                                            LLMProvider.CUSTOM -> "Your API key"
                                        }
                                    },
                                    color = Color.Gray.copy(alpha = 0.5f)
                                ) 
                            },
                            visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(
                                    onClick = { showApiKey = !showApiKey }
                                ) {
                                    Icon(
                                        if (showApiKey) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                        contentDescription = if (showApiKey) "Hide" else "Show",
                                        modifier = Modifier.size(20.dp),
                                        tint = Color.Gray
                                    )
                                }
                            },
                            singleLine = true,
                            colors = TextFieldDefaults.outlinedTextFieldColors(
                                textColor = Color.White,
                                focusedBorderColor = BossDarkAccent,
                                unfocusedBorderColor = BossDarkBorder,
                                focusedLabelColor = BossDarkAccent,
                                unfocusedLabelColor = Color.Gray,
                                placeholderColor = Color.Gray.copy(alpha = 0.5f)
                            )
                        )
                        
                        // Help text about environment variables
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (!envKey.isNullOrBlank()) {
                                "API key is set via environment variable"
                            } else {
                                val envVarName = when (selectedProvider) {
                                    LLMProvider.ANTHROPIC -> "ANTHROPIC_API_KEY"
                                    LLMProvider.OPENAI -> "OPENAI_API_KEY"
                                    LLMProvider.TOGETHER -> "TOGETHER_API_KEY"
                                    LLMProvider.CUSTOM -> "CUSTOM_LLM_API_KEY"
                                }
                                "You can also set the $envVarName environment variable"
                            },
                            fontSize = 12.sp,
                            color = Color.Gray.copy(alpha = 0.7f),
                            modifier = Modifier.padding(start = 4.dp)
                        )
                        
                        if (selectedProvider == LLMProvider.CUSTOM) {
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedTextField(
                                value = customEndpoint,
                                onValueChange = { customEndpoint = it },
                                label = { Text("API Endpoint") },
                                placeholder = { Text("https://api.example.com/v1/chat", color = Color.Gray.copy(alpha = 0.5f)) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                colors = TextFieldDefaults.outlinedTextFieldColors(
                                    textColor = Color.White,
                                    focusedBorderColor = BossDarkAccent,
                                    unfocusedBorderColor = BossDarkBorder,
                                    focusedLabelColor = BossDarkAccent,
                                    unfocusedLabelColor = Color.Gray
                                )
                            )
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Model Selection
        if (selectedProvider != LLMProvider.CUSTOM) {
            SettingSection(title = "Model Selection", description = "Choose the AI model to use") {
                val providerModels = availableModels[selectedProvider.name] ?: LLMModelFetcher.getModelsForProvider(selectedProvider)
                
                if (isLoadingModels && providerModels.isEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = BossDarkAccent
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Loading models...", color = Color.Gray)
                    }
                } else {
                    val currentModel = providerModels.find { it.id == selectedModelId }
                    DropdownSelector(
                        label = "Model",
                        value = currentModel?.name ?: "Select a model",
                        options = providerModels.map { it.name },
                        onValueChange = { displayName ->
                            providerModels.find { it.name == displayName }?.let {
                                selectedModelId = it.id
                            }
                        },
                        modifier = Modifier.width(400.dp)
                    )
                    
                    // Show model details if available
                    currentModel?.let { model ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            backgroundColor = BossDarkBackground.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(4.dp),
                            elevation = 0.dp
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                model.description?.let {
                                    Text(
                                        text = it,
                                        fontSize = 12.sp,
                                        color = Color.Gray
                                    )
                                }
                                model.contextLength?.let {
                                    Text(
                                        text = "Context: ${it.toString().reversed().chunked(3).reversed().joinToString(",")} tokens",
                                        fontSize = 11.sp,
                                        color = Color.Gray.copy(alpha = 0.7f)
                                    )
                                }
                                if (model.capabilities.isNotEmpty()) {
                                    Text(
                                        text = "Capabilities: ${model.capabilities.joinToString(", ")}",
                                        fontSize = 11.sp,
                                        color = Color.Gray.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    }
                }
                
                // Refresh button
                modelError?.let { error ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Row {
                        Text(
                            text = error,
                            fontSize = 12.sp,
                            color = Color(0xFFFF5252)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(
                            onClick = {
                                coroutineScope.launch {
                                    LLMModelFetcher.fetchLatestModels(forceRefresh = true)
                                }
                            }
                        ) {
                            Text("Retry", fontSize = 12.sp)
                        }
                    }
                }
                
                // Info about custom models via environment variables
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Custom models can be set via BOSS_LLM_MODELS_${selectedProvider.name} environment variable",
                    fontSize = 11.sp,
                    color = Color.Gray.copy(alpha = 0.5f),
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                Text(
                    text = "Format: model1:name1:context1;model2:name2:context2",
                    fontSize = 10.sp,
                    color = Color.Gray.copy(alpha = 0.4f),
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Advanced Settings
        SettingSection(title = "Advanced Settings", description = "Fine-tune model behavior") {
            Column {
                // Temperature
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Temperature",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White
                        )
                        Text(
                            text = "Controls randomness (0 = focused, 2 = creative)",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                    
                    Text(
                        text = String.format("%.1f", temperature),
                        fontSize = 14.sp,
                        color = Color.White,
                        modifier = Modifier.width(40.dp)
                    )
                    
                    Slider(
                        value = temperature,
                        onValueChange = { temperature = it },
                        valueRange = 0f..2f,
                        modifier = Modifier.width(200.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = BossDarkAccent,
                            activeTrackColor = BossDarkAccent,
                            inactiveTrackColor = BossDarkBorder
                        )
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Max Tokens
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Max Tokens",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White
                        )
                        Text(
                            text = "Maximum response length",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                    
                    OutlinedTextField(
                        value = maxTokens,
                        onValueChange = { 
                            if (it.all { char -> char.isDigit() }) {
                                maxTokens = it
                            }
                        },
                        modifier = Modifier.width(150.dp),
                        singleLine = true,
                        colors = TextFieldDefaults.outlinedTextFieldColors(
                            textColor = Color.White,
                            focusedBorderColor = BossDarkAccent,
                            unfocusedBorderColor = BossDarkBorder
                        )
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Options
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(32.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = enableStreaming,
                            onCheckedChange = { enableStreaming = it },
                            colors = CheckboxDefaults.colors(
                                checkedColor = BossDarkAccent,
                                uncheckedColor = BossDarkBorder
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Enable Streaming",
                            fontSize = 14.sp,
                            color = Color.White
                        )
                    }
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = enableCaching,
                            onCheckedChange = { enableCaching = it },
                            colors = CheckboxDefaults.colors(
                                checkedColor = BossDarkAccent,
                                uncheckedColor = BossDarkBorder
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Enable Response Caching",
                            fontSize = 14.sp,
                            color = Color.White
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Apply settings button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Button(
                onClick = {
                    // Apply all settings
                    LLMSettings.selectedProvider = selectedProvider
                    LLMSettings.selectedModelId = selectedModelId
                    apiKeys.forEach { (provider, key) ->
                        LLMSettings.setApiKey(provider, key.takeIf { it.isNotBlank() })
                    }
                    if (selectedProvider == LLMProvider.CUSTOM) {
                        LLMSettings.customEndpoint = customEndpoint.takeIf { it.isNotBlank() }
                    }
                    LLMSettings.temperature = temperature
                    LLMSettings.maxTokens = maxTokens.toIntOrNull() ?: 2000
                    LLMSettings.enableStreaming = enableStreaming
                    LLMSettings.enableCaching = enableCaching
                    
                    // Save settings
                    coroutineScope.launch {
                        try {
                            LLMSettingsManager.saveSettings()
                            settingsFeedbackMessage = "LLM settings applied successfully!"
                            delay(3000) // Show message for 3 seconds
                            settingsFeedbackMessage = null
                        } catch (e: Exception) {
                            settingsFeedbackMessage = "Error saving settings: ${e.message}"
                            delay(5000) // Show error message longer
                            settingsFeedbackMessage = null
                        }
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
    }
    
    // OAuth Dialog
    showOAuthDialog?.let { provider ->
        AlertDialog(
            onDismissRequest = { showOAuthDialog = null },
            title = {
                Text(
                    "Sign in to ${provider.displayName}",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        "OAuth authentication for ${provider.displayName} is not yet implemented.",
                        color = Color.Gray,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Please manually enter your API key for now. You can obtain it from:",
                        color = Color.Gray,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        when (provider) {
                            LLMProvider.ANTHROPIC -> "https://console.anthropic.com/account/keys"
                            LLMProvider.OPENAI -> "https://platform.openai.com/api-keys"
                            LLMProvider.TOGETHER -> "https://api.together.xyz/settings/api-keys"
                            else -> ""
                        },
                        color = BossDarkAccent,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showOAuthDialog = null }
                ) {
                    Text("OK", color = BossDarkAccent)
                }
            },
            backgroundColor = BossDarkSurface,
            contentColor = Color.White
        )
    }
    
    // Settings feedback message
    settingsFeedbackMessage?.let { message ->
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.CenterEnd
        ) {
            Surface(
                modifier = Modifier.padding(top = 16.dp),
                color = if (message.contains("Error")) Color.Red else BossDarkAccent,
                shape = RoundedCornerShape(6.dp),
                elevation = 4.dp
            ) {
                Text(
                    text = message,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    color = Color.White,
                    style = MaterialTheme.typography.body2
                )
            }
        }
    }
}

@Composable
private fun ProviderTab(
    provider: LLMProvider,
    isSelected: Boolean,
    hasApiKey: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .clickable { onClick() }
            .padding(4.dp),
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) BossDarkAccent.copy(alpha = 0.2f) else Color.Transparent,
        border = BorderStroke(
            1.dp, 
            if (isSelected) BossDarkAccent else BossDarkBorder
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Provider icon placeholder
            Icon(
                when (provider) {
                    LLMProvider.ANTHROPIC -> Icons.Outlined.AutoAwesome
                    LLMProvider.OPENAI -> Icons.Outlined.Psychology
                    LLMProvider.TOGETHER -> Icons.Outlined.Groups
                    LLMProvider.CUSTOM -> Icons.Outlined.Settings
                },
                contentDescription = provider.displayName,
                modifier = Modifier.size(20.dp),
                tint = if (isSelected) BossDarkAccent else Color.Gray
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            Text(
                text = provider.displayName,
                fontSize = 14.sp,
                color = if (isSelected) Color.White else Color.Gray,
                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
            )
            
            if (hasApiKey) {
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    Icons.Outlined.CheckCircle,
                    contentDescription = "API Key Set",
                    modifier = Modifier.size(16.dp),
                    tint = Color(0xFF4CAF50)
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    description: String
) {
    Column {
        Text(
            text = title,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = description,
            fontSize = 16.sp,
            color = Color.Gray
        )
    }
}

@Composable
private fun SettingSection(
    title: String,
    description: String? = null,
    content: @Composable () -> Unit
) {
    Column {
        Text(
            text = title,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White
        )
        description?.let {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = it,
                fontSize = 14.sp,
                color = Color.Gray
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        content()
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun DropdownSelector(
    label: String,
    value: String,
    options: List<String>,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = { },
            readOnly = true,
            label = { Text(label) },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            modifier = Modifier.fillMaxWidth(),
            colors = TextFieldDefaults.outlinedTextFieldColors(
                textColor = Color.White,
                focusedBorderColor = BossDarkAccent,
                unfocusedBorderColor = BossDarkBorder,
                focusedLabelColor = BossDarkAccent,
                unfocusedLabelColor = Color.Gray,
                trailingIconColor = Color.Gray
            )
        )
        
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(BossDarkSurface)
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    onClick = {
                        onValueChange(option)
                        expanded = false
                    },
                    modifier = Modifier.background(BossDarkSurface)
                ) {
                    Text(
                        text = option,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun UpdatesSettings() {
    UpdateSettingsSection()
}

@Composable
private fun TwoFactorAuthSettings() {
    val authState by AuthService.authState.collectAsState()
    val currentUser by AuthService.currentUser.collectAsState()
    var twoFactorFactors by remember { mutableStateOf<List<TwoFactorInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showEnrollDialog by remember { mutableStateOf(false) }
    var showRemoveDialog by remember { mutableStateOf<TwoFactorInfo?>(null) }
    val coroutineScope = rememberCoroutineScope()
    
    // Load 2FA factors when component mounts
    LaunchedEffect(Unit) {
        if (authState is AuthState.Authenticated) {
            isLoading = true
            AuthService.get2FAFactors().fold(
                onSuccess = { factors ->
                    twoFactorFactors = factors
                    isLoading = false
                },
                onFailure = { error ->
                    errorMessage = error.message
                    isLoading = false
                }
            )
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        SectionHeader(
            title = "Two-Factor Authentication",
            description = "Manage your 2FA settings for enhanced security"
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Authentication status check
        if (authState !is AuthState.Authenticated) {
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
                        text = "You must be logged in to manage 2FA settings",
                        fontSize = 14.sp,
                        color = Color.White
                    )
                }
            }
            return@Column
        }
        
        // Current 2FA Status
        SettingSection(
            title = "Current Status",
            description = "Your two-factor authentication status"
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = BossDarkBackground,
                shape = RoundedCornerShape(8.dp),
                elevation = 2.dp
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Two-Factor Authentication",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (twoFactorFactors.isNotEmpty()) 
                                    "Enabled (${twoFactorFactors.size} method${if (twoFactorFactors.size > 1) "s" else ""})"
                                else 
                                    "Not enabled",
                                fontSize = 14.sp,
                                color = if (twoFactorFactors.isNotEmpty()) Color(0xFF4CAF50) else Color.Gray
                            )
                        }
                        
                        if (twoFactorFactors.isEmpty()) {
                            Button(
                                onClick = { showEnrollDialog = true },
                                colors = ButtonDefaults.buttonColors(
                                    backgroundColor = BossDarkAccent,
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.Add,
                                    contentDescription = "Enable",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Enable 2FA")
                            }
                        }
                    }
                    
                    if (currentUser != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Account: ${currentUser!!.email}",
                            fontSize = 13.sp,
                            color = Color.Gray
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Enrolled Methods
        if (twoFactorFactors.isNotEmpty()) {
            SettingSection(
                title = "Enrolled Methods",
                description = "Your active 2FA methods"
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    twoFactorFactors.forEach { factor ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            backgroundColor = BossDarkBackground,
                            shape = RoundedCornerShape(8.dp),
                            elevation = 1.dp
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Outlined.Smartphone,
                                        contentDescription = "Authenticator",
                                        tint = BossDarkAccent,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column {
                                        Text(
                                            text = factor.friendlyName,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = Color.White
                                        )
                                        Text(
                                            text = "Status: ${factor.status}",
                                            fontSize = 13.sp,
                                            color = if (factor.status == "verified") Color(0xFF4CAF50) else Color.Gray
                                        )
                                    }
                                }
                                
                                TextButton(
                                    onClick = { showRemoveDialog = factor },
                                    colors = ButtonDefaults.textButtonColors(
                                        contentColor = Color(0xFFFF5252)
                                    )
                                ) {
                                    Icon(
                                        Icons.Outlined.Delete,
                                        contentDescription = "Remove",
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Remove", fontSize = 13.sp)
                                }
                            }
                        }
                    }
                    
                    // Add another method button
                    Button(
                        onClick = { showEnrollDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = BossDarkAccent.copy(alpha = 0.1f),
                            contentColor = BossDarkAccent
                        ),
                        shape = RoundedCornerShape(6.dp),
                        elevation = ButtonDefaults.elevation(0.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Add,
                            contentDescription = "Add",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Add Another Method")
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Security Tips
        SettingSection(
            title = "Security Tips",
            description = "Best practices for 2FA"
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = BossDarkAccent.copy(alpha = 0.05f),
                shape = RoundedCornerShape(8.dp),
                elevation = 0.dp,
                border = BorderStroke(1.dp, BossDarkAccent.copy(alpha = 0.2f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    SecurityTip(
                        icon = Icons.Outlined.PhoneAndroid,
                        text = "Use an authenticator app like Google Authenticator, Authy, or 1Password"
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    SecurityTip(
                        icon = Icons.Outlined.ContentCopy,
                        text = "Save your backup codes in a secure location"
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    SecurityTip(
                        icon = Icons.Outlined.DeviceHub,
                        text = "Enable 2FA on multiple devices for redundancy"
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    SecurityTip(
                        icon = Icons.Outlined.Update,
                        text = "Keep your authenticator app updated"
                    )
                }
            }
        }
        
        // Loading state
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = BossDarkAccent,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
        
        // Error message
        errorMessage?.let { error ->
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = Color(0xFFFF5252).copy(alpha = 0.1f),
                shape = RoundedCornerShape(8.dp),
                elevation = 0.dp
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Outlined.Error,
                        contentDescription = "Error",
                        tint = Color(0xFFFF5252),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = error,
                        fontSize = 13.sp,
                        color = Color.White
                    )
                }
            }
        }
    }
    
    // Enroll Dialog
    if (showEnrollDialog) {
        Simple2FAEnrollDialog(
            onDismiss = { showEnrollDialog = false },
            onEnrolled = {
                showEnrollDialog = false
                // Refresh the factors list
                coroutineScope.launch {
                    isLoading = true
                    AuthService.get2FAFactors().fold(
                        onSuccess = { factors ->
                            twoFactorFactors = factors
                            isLoading = false
                        },
                        onFailure = { error ->
                            errorMessage = error.message
                            isLoading = false
                        }
                    )
                }
            }
        )
    }
    
    // Remove confirmation dialog
    showRemoveDialog?.let { factor ->
        AlertDialog(
            onDismissRequest = { showRemoveDialog = null },
            title = {
                Text(
                    "Remove 2FA Method",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        "Are you sure you want to remove this 2FA method?",
                        color = Color.Gray,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Card(
                        backgroundColor = BossDarkBackground,
                        shape = RoundedCornerShape(4.dp),
                        elevation = 0.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Outlined.Smartphone,
                                contentDescription = "Method",
                                tint = BossDarkAccent,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = factor.friendlyName,
                                fontSize = 14.sp,
                                color = Color.White
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Warning: Make sure you have another way to access your account before removing this method.",
                        color = Color(0xFFFF5252),
                        fontSize = 12.sp
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            isLoading = true
                            AuthService.unenroll2FA(factor.id).fold(
                                onSuccess = {
                                    // Refresh the list
                                    AuthService.get2FAFactors().fold(
                                        onSuccess = { factors ->
                                            twoFactorFactors = factors
                                            isLoading = false
                                            showRemoveDialog = null
                                        },
                                        onFailure = { error ->
                                            errorMessage = error.message
                                            isLoading = false
                                        }
                                    )
                                },
                                onFailure = { error ->
                                    errorMessage = error.message
                                    isLoading = false
                                }
                            )
                        }
                    }
                ) {
                    Text("Remove", color = Color(0xFFFF5252))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showRemoveDialog = null }
                ) {
                    Text("Cancel", color = Color.Gray)
                }
            },
            backgroundColor = BossDarkSurface,
            contentColor = Color.White
        )
    }
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

@Composable
private fun Simple2FAEnrollDialog(
    onDismiss: () -> Unit,
    onEnrolled: () -> Unit
) {
    var enrollmentData by remember { mutableStateOf<ai.rever.boss.services.supabase.TwoFactorEnrollment?>(null) }
    var verificationCode by remember { mutableStateOf("") }
    var currentStep by remember { mutableStateOf(1) } // 1: Setup, 2: Verify
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    
    // Start enrollment process
    LaunchedEffect(Unit) {
        isLoading = true
        AuthService.enroll2FA().fold(
            onSuccess = { enrollment ->
                enrollmentData = enrollment
                isLoading = false
            },
            onFailure = { error ->
                errorMessage = error.message
                isLoading = false
            }
        )
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Security,
                    contentDescription = "2FA",
                    tint = BossDarkAccent,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = if (currentStep == 1) "Set Up 2FA" else "Verify 2FA",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = BossDarkAccent,
                        modifier = Modifier.size(32.dp)
                    )
                } else if (currentStep == 1 && enrollmentData != null) {
                    // Step 1: Show QR Code and Secret
                    Text(
                        text = "Scan this QR code with your authenticator app",
                        fontSize = 14.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // QR Code
                    val qrCodeImage = ai.rever.boss.utils.QRCodeProvider.generateQRCode(enrollmentData!!.uri)
                    qrCodeImage?.let { image ->
                        Image(
                            bitmap = image,
                            contentDescription = "QR Code",
                            modifier = Modifier.size(200.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Manual entry option
                    Card(
                        backgroundColor = BossDarkBackground,
                        shape = RoundedCornerShape(4.dp),
                        elevation = 0.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Or enter this code manually:",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = enrollmentData!!.secret.chunked(4).joinToString(" "),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    color = Color.White
                                )
                                val clipboardManager = LocalClipboardManager.current
                                IconButton(
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString(enrollmentData!!.secret))
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.ContentCopy,
                                        contentDescription = "Copy",
                                        tint = BossDarkAccent,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                } else if (currentStep == 2) {
                    // Step 2: Verify
                    Text(
                        text = "Enter the 6-digit code from your authenticator app",
                        fontSize = 14.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    OutlinedTextField(
                        value = verificationCode,
                        onValueChange = { if (it.length <= 6 && it.all { char -> char.isDigit() }) verificationCode = it },
                        label = { Text("Verification Code") },
                        placeholder = { Text("000000") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = TextFieldDefaults.outlinedTextFieldColors(
                            textColor = Color.White,
                            focusedBorderColor = BossDarkAccent,
                            unfocusedBorderColor = BossDarkBorder,
                            focusedLabelColor = BossDarkAccent,
                            unfocusedLabelColor = Color.Gray
                        )
                    )
                }
                
                // Error message
                errorMessage?.let { error ->
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = error,
                        fontSize = 12.sp,
                        color = Color(0xFFFF5252)
                    )
                }
            }
        },
        confirmButton = {
            if (!isLoading) {
                TextButton(
                    onClick = {
                        if (currentStep == 1) {
                            currentStep = 2
                        } else {
                            enrollmentData?.let { enrollment ->
                                coroutineScope.launch {
                                    isLoading = true
                                    AuthService.verify2FAEnrollment(enrollment.id, verificationCode).fold(
                                        onSuccess = {
                                            isLoading = false
                                            onEnrolled()
                                        },
                                        onFailure = { error ->
                                            errorMessage = error.message
                                            isLoading = false
                                        }
                                    )
                                }
                            }
                        }
                    },
                    enabled = if (currentStep == 2) verificationCode.length == 6 else true
                ) {
                    Text(
                        if (currentStep == 1) "Next" else "Verify",
                        color = BossDarkAccent
                    )
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isLoading
            ) {
                Text("Cancel", color = Color.Gray)
            }
        },
        backgroundColor = BossDarkSurface,
        contentColor = Color.White
    )
}
