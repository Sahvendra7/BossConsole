package ai.rever.boss.components.settings.sections

import ai.rever.boss.components.plugin.tab_types.CodeEditorSettings
import ai.rever.boss.components.plugin.tab_types.CodeEditorSettingsManager
import ai.rever.boss.components.settings.shared.SettingsSection
import ai.rever.boss.components.settings.shared.SettingsToggle
import ai.rever.boss.components.settings.shared.SettingsSlider
import ai.rever.boss.components.settings.shared.SettingsDropdown
import ai.rever.boss.components.settings.shared.SettingsSectionedDropdown
import ai.rever.boss.components.settings.shared.SettingsTheme.AccentColor
import ai.rever.boss.components.settings.shared.SettingsTheme.BackgroundColor
import ai.rever.boss.components.settings.shared.SettingsTheme.BorderColor
import ai.rever.boss.components.settings.shared.SettingsTheme.TextPrimary
import ai.rever.boss.components.settings.shared.SettingsTheme.TextSecondary
import ai.rever.boss.font.FontManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
fun CodeEditorSettings() {
    var selectedFont by remember { mutableStateOf(CodeEditorSettings.fontFamily) }
    var fontSize by remember { mutableStateOf(CodeEditorSettings.fontSize.toFloat()) }
    var theme by remember { mutableStateOf(CodeEditorSettings.theme) }
    var useLigatures by remember { mutableStateOf(CodeEditorSettings.useLigatures) }
    var useAntialiasing by remember { mutableStateOf(CodeEditorSettings.useAntialiasing) }
    var lineSpacing by remember { mutableStateOf(CodeEditorSettings.lineSpacing) }
    val coroutineScope = rememberCoroutineScope()

    val categorizedFonts = remember { CodeEditorSettings.getCategorizedFonts() }
    val themes = listOf("Dark", "Light", "Dracula", "Monokai", "Solarized Dark", "Solarized Light")

    // Helper to save settings
    fun saveSettings() {
        CodeEditorSettings.fontFamily = selectedFont
        CodeEditorSettings.fontSize = fontSize.toInt()
        CodeEditorSettings.theme = theme
        CodeEditorSettings.useLigatures = useLigatures
        CodeEditorSettings.useAntialiasing = useAntialiasing
        CodeEditorSettings.lineSpacing = lineSpacing
        coroutineScope.launch {
            CodeEditorSettingsManager.saveSettings()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Typography
        SettingsSection(title = "Typography") {
            SettingsSectionedDropdown(
                label = "Font Family",
                sections = categorizedFonts,
                selectedOption = selectedFont,
                onOptionSelected = {
                    selectedFont = it
                    saveSettings()
                },
                description = "Choose a monospace font for code editing"
            )

            SettingsSlider(
                label = "Font Size",
                value = fontSize,
                onValueChange = { fontSize = it },
                onValueChangeFinished = { saveSettings() },
                valueRange = 10f..24f,
                steps = 13,
                valueDisplay = { "${it.toInt()} px" },
                description = "Text size in the code editor"
            )

            SettingsSlider(
                label = "Line Spacing",
                value = lineSpacing,
                onValueChange = { lineSpacing = it },
                onValueChangeFinished = { saveSettings() },
                valueRange = 1.0f..2.0f,
                steps = 9,
                valueDisplay = { "%.1f".format(it) },
                description = "Space between lines of code"
            )
        }

        // Font Rendering
        SettingsSection(title = "Font Rendering") {
            SettingsToggle(
                label = "Enable Ligatures",
                checked = useLigatures,
                onCheckedChange = {
                    useLigatures = it
                    saveSettings()
                },
                description = "Display combined characters (e.g., -> becomes →)"
            )

            SettingsToggle(
                label = "Font Smoothing",
                checked = useAntialiasing,
                onCheckedChange = {
                    useAntialiasing = it
                    saveSettings()
                },
                description = "Apply antialiasing for smoother text"
            )
        }

        // Theme Selection
        SettingsSection(title = "Theme") {
            SettingsDropdown(
                label = "Editor Theme",
                options = themes,
                selectedOption = theme,
                onOptionSelected = {
                    theme = it
                    saveSettings()
                },
                description = "Color scheme for syntax highlighting"
            )
        }

        // Preview
        SettingsSection(title = "Preview") {
            val previewBgColor = when (theme) {
                "Light" -> androidx.compose.ui.graphics.Color(0xFF_FFFFFF)
                "Dracula" -> androidx.compose.ui.graphics.Color(0xFF_282A36)
                "Monokai" -> androidx.compose.ui.graphics.Color(0xFF_272822)
                "Solarized Dark" -> androidx.compose.ui.graphics.Color(0xFF_002B36)
                "Solarized Light" -> androidx.compose.ui.graphics.Color(0xFF_FDF6E3)
                else -> androidx.compose.ui.graphics.Color(0xFF_1E1E1E)
            }
            val previewTextColor = when (theme) {
                "Light" -> androidx.compose.ui.graphics.Color(0xFF_000000)
                "Dracula" -> androidx.compose.ui.graphics.Color(0xFF_F8F8F2)
                "Monokai" -> androidx.compose.ui.graphics.Color(0xFF_F8F8F2)
                "Solarized Dark" -> androidx.compose.ui.graphics.Color(0xFF_839496)
                "Solarized Light" -> androidx.compose.ui.graphics.Color(0xFF_657B83)
                else -> androidx.compose.ui.graphics.Color(0xFF_D4D4D4)
            }

            val previewFontFamily = remember(selectedFont) {
                FontManager.loadComposeFontFamily(selectedFont)
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = previewBgColor,
                shape = RoundedCornerShape(6.dp),
                elevation = 2.dp
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .padding(16.dp)
                ) {
                    Text(
                        text = """
                            fun main() {
                                val message = "Hello, BOSS!"
                                println(message)

                                // Configure your editor
                                val settings = EditorSettings(
                                    font = "$selectedFont",
                                    size = ${fontSize.toInt()}
                                )
                            }
                        """.trimIndent(),
                        fontFamily = previewFontFamily,
                        fontSize = fontSize.toInt().sp,
                        color = previewTextColor,
                        lineHeight = (fontSize.toInt() * lineSpacing).sp
                    )
                }
            }
        }
    }
}
