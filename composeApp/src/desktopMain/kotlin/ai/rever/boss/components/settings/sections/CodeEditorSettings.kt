package ai.rever.boss.components.settings.sections

import BossDarkAccent
import BossDarkBackground
import BossDarkBorder
import ai.rever.boss.components.plugin.tab_types.CodeEditorSettings
import ai.rever.boss.components.plugin.tab_types.CodeEditorSettingsManager
import ai.rever.boss.components.settings.shared.DropdownSelector
import ai.rever.boss.components.settings.shared.SectionHeader
import ai.rever.boss.components.settings.shared.SettingSection
import ai.rever.boss.font.FontManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    var useLigatures by remember { mutableStateOf(CodeEditorSettings.useLigatures) }
    var useAntialiasing by remember { mutableStateOf(CodeEditorSettings.useAntialiasing) }
    var lineSpacing by remember { mutableStateOf(CodeEditorSettings.lineSpacing) }
    val coroutineScope = rememberCoroutineScope()

    // Get categorized fonts from FontManager
    val categorizedFonts = remember { CodeEditorSettings.getCategorizedFonts() }

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

        // Font Selection with categorized dropdown
        SettingSection(title = "Typography", description = "Font family and size for code editing") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategorizedFontDropdown(
                    label = "Font Family",
                    value = selectedFont,
                    categorizedFonts = categorizedFonts,
                    onValueChange = { selectedFont = it },
                    modifier = Modifier.width(320.dp)
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

            Spacer(modifier = Modifier.height(16.dp))

            // Line Spacing Slider
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Line Spacing",
                    color = Color.White,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.width(16.dp))
                Slider(
                    value = lineSpacing,
                    onValueChange = { lineSpacing = it },
                    valueRange = 1.0f..2.0f,
                    steps = 9, // 1.0, 1.1, 1.2, ... 2.0
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = BossDarkAccent,
                        activeTrackColor = BossDarkAccent
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "%.1f".format(lineSpacing),
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 14.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Font Rendering Options
        SettingSection(title = "Font Rendering", description = "Ligatures and antialiasing settings") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(32.dp)
            ) {
                // Ligatures toggle
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = useLigatures,
                        onCheckedChange = { useLigatures = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = BossDarkAccent,
                            uncheckedColor = Color.White.copy(alpha = 0.6f)
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Enable Ligatures",
                            color = Color.White,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Display combined characters (e.g., -> becomes →)",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 12.sp
                        )
                    }
                }

                // Antialiasing toggle
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = useAntialiasing,
                        onCheckedChange = { useAntialiasing = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = BossDarkAccent,
                            uncheckedColor = Color.White.copy(alpha = 0.6f)
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Font Smoothing",
                            color = Color.White,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Apply antialiasing for smoother text",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 12.sp
                        )
                    }
                }
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

            // Get actual FontFamily from FontManager for preview
            val previewFontFamily = remember(selectedFont) {
                FontManager.loadComposeFontFamily(selectedFont)
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
                        fontFamily = previewFontFamily,
                        fontSize = fontSize.toIntOrNull()?.sp ?: 14.sp,
                        color = previewTextColor,
                        lineHeight = ((fontSize.toIntOrNull() ?: 14) * lineSpacing).sp
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
                    CodeEditorSettings.useLigatures = useLigatures
                    CodeEditorSettings.useAntialiasing = useAntialiasing
                    CodeEditorSettings.lineSpacing = lineSpacing

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

/**
 * A dropdown selector that shows fonts organized by category (like iTerm2/BossTerm).
 * Categories: Bundled, Fixed Pitch, Variable Pitch
 */
@Composable
private fun CategorizedFontDropdown(
    label: String,
    value: String,
    categorizedFonts: Map<String, List<String>>,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        Box {
            // Current selection button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(BossDarkBackground)
                    .border(1.dp, BossDarkBorder, RoundedCornerShape(6.dp))
                    .clickable { expanded = true }
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = value,
                        color = Color.White,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "▼",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 10.sp
                    )
                }
            }

            // Dropdown menu
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier
                    .width(320.dp)
                    .background(BossDarkBackground)
            ) {
                // Iterate through categories in order
                listOf(
                    FontManager.SECTION_BUNDLED,
                    FontManager.SECTION_FIXED_PITCH,
                    FontManager.SECTION_VARIABLE_PITCH
                ).forEach { sectionName ->
                    val fonts = categorizedFonts[sectionName] ?: emptyList()
                    if (fonts.isNotEmpty()) {
                        // Section header
                        DropdownMenuItem(
                            onClick = {},
                            enabled = false
                        ) {
                            Text(
                                text = sectionName,
                                color = BossDarkAccent,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Font items in this section
                        fonts.forEach { font ->
                            val isSelected = font == value
                            DropdownMenuItem(
                                onClick = {
                                    onValueChange(font)
                                    expanded = false
                                },
                                modifier = Modifier
                                    .background(if (isSelected) BossDarkAccent.copy(alpha = 0.2f) else Color.Transparent)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = font,
                                        color = if (isSelected) BossDarkAccent else Color.White,
                                        fontSize = 14.sp
                                    )
                                    if (isSelected) {
                                        Spacer(modifier = Modifier.weight(1f))
                                        Text(
                                            text = "✓",
                                            color = BossDarkAccent,
                                            fontSize = 14.sp
                                        )
                                    }
                                }
                            }
                        }

                        // Divider between sections (except after last)
                        if (sectionName != FontManager.SECTION_VARIABLE_PITCH) {
                            Divider(color = BossDarkBorder, thickness = 1.dp)
                        }
                    }
                }
            }
        }
    }
}
