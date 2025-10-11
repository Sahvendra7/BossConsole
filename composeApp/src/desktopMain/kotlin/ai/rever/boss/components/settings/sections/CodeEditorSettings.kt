package ai.rever.boss.components.settings.sections

import BossDarkAccent
import BossDarkBackground
import BossDarkBorder
import ai.rever.boss.components.plugin.tab_types.CodeEditorSettings
import ai.rever.boss.components.plugin.tab_types.CodeEditorSettingsManager
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
fun CodeEditorSettings() {
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