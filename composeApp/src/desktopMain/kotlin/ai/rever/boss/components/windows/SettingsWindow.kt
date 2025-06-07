package ai.rever.boss.components.windows

import BossDarkBackground
import BossDarkSurface
import BossDarkBorder
import BossDarkAccent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.outlined.Check
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
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
import ai.rever.boss.utils.ApplicationRestarter

enum class SettingsSection {
    FLUCK, CODE_EDITOR, TERMINAL
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