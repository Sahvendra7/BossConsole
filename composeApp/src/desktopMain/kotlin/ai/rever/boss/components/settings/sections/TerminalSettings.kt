package ai.rever.boss.components.settings.sections

import BossDarkAccent
import BossDarkBackground
import BossDarkBorder
import ai.rever.boss.components.plugin.panels.bottom.terminal.TerminalSettings
import ai.rever.boss.components.plugin.panels.bottom.terminal.TerminalSettingsManager
import ai.rever.boss.components.settings.shared.DropdownSelector
import ai.rever.boss.components.settings.shared.SectionHeader
import ai.rever.boss.components.settings.shared.SettingSection
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
fun TerminalSettings() {
    var selectedFont by remember { mutableStateOf(TerminalSettings.fontFamily) }
    var fontSize by remember { mutableStateOf(TerminalSettings.fontSize.toString()) }
    var colorScheme by remember { mutableStateOf(TerminalSettings.colorScheme) }
    var shell by remember { mutableStateOf(TerminalSettings.shell) }
    var startupCommand by remember { mutableStateOf(TerminalSettings.startupCommand) }
    val coroutineScope = rememberCoroutineScope()
    
    val fonts = listOf("MesloLGS NF", "JetBrains Mono", "Fira Code", "Source Code Pro", "Consolas", "Monaco", "Menlo")
    val fontSizes = listOf("10", "11", "12", "13", "14", "15", "16", "18", "20", "22", "24")
    val colorSchemes = listOf("BOSS Dark", "BOSS Light", "Solarized Dark", "Solarized Light", "Dracula", "Tomorrow Night")
    val shells = listOf("/bin/zsh", "/bin/bash", "/bin/sh", "/usr/bin/fish", "/usr/local/bin/zsh", "/usr/local/bin/bash")
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        SectionHeader(
            title = "Terminal Settings",
            description = "Customize your terminal environment"
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Font Settings
        SettingSection(title = "Typography", description = "Font family and size for terminal text") {
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
        
        // Color Scheme
        SettingSection(title = "Color Scheme", description = "Terminal color theme") {
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
        SettingSection(title = "Shell Configuration", description = "Default shell and startup commands") {
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
        
        // Preview
        SettingSection(title = "Preview", description = "See how your terminal will look") {
            // Compute preview colors based on selected color scheme
            val previewBgColor = when (colorScheme) {
                "BOSS Light" -> Color(0xFF_FFFFFF)
                "Solarized Dark" -> Color(0xFF_002B36)
                "Solarized Light" -> Color(0xFF_FDF6E3)
                "Dracula" -> Color(0xFF_282A36)
                "Tomorrow Night" -> Color(0xFF_1D1F21)
                else -> Color(0xFF_1E1E1E) // BOSS Dark
            }
            val previewTextColor = when (colorScheme) {
                "BOSS Light" -> Color(0xFF_000000)
                "Solarized Dark" -> Color(0xFF_839496)
                "Solarized Light" -> Color(0xFF_657B83)
                "Dracula" -> Color(0xFF_F8F8F2)
                "Tomorrow Night" -> Color(0xFF_C5C8C6)
                else -> Color(0xFF_D4D4D4) // BOSS Dark
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
                            $ ls -la
                            total 24
                            drwxr-xr-x  5 user  staff   160 Jan  1 12:00 .
                            drwxr-xr-x  8 user  staff   256 Jan  1 12:00 ..
                            -rw-r--r--  1 user  staff  1024 Jan  1 12:00 README.md
                            -rwxr-xr-x  1 user  staff  2048 Jan  1 12:00 script.sh
                            drwxr-xr-x  3 user  staff    96 Jan  1 12:00 src
                            
                            $ echo "Font: $selectedFont, Size: ${fontSize}pt"
                            Font: $selectedFont, Size: ${fontSize}pt
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
