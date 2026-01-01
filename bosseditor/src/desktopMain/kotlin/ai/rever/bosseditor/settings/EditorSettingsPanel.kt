package ai.rever.bosseditor.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.rever.bosseditor.settings.SettingsTheme.AccentColor
import ai.rever.bosseditor.settings.SettingsTheme.BackgroundColor
import ai.rever.bosseditor.settings.SettingsTheme.BorderColor
import ai.rever.bosseditor.settings.SettingsTheme.SurfaceColor
import ai.rever.bosseditor.settings.SettingsTheme.TextMuted
import ai.rever.bosseditor.settings.SettingsTheme.TextPrimary
import ai.rever.bosseditor.settings.SettingsTheme.TextSecondary
import ai.rever.bosseditor.settings.components.ColorPickerDialog

// ========== Settings Categories ==========

/**
 * Categories for organizing editor settings in the settings panel.
 * Following BossTerm's pattern with Material icons.
 */
enum class EditorSettingsCategory(
    val displayName: String,
    val icon: ImageVector,
    val description: String
) {
    VISUAL(
        displayName = "Visual",
        icon = Icons.Default.Palette,
        description = "Font, theme, and text rendering"
    ),
    COLORS(
        displayName = "Colors",
        icon = Icons.Default.ColorLens,
        description = "Theme colors and syntax highlighting"
    ),
    BEHAVIOR(
        displayName = "Behavior",
        icon = Icons.Default.Settings,
        description = "Scrolling, tabs, and text input"
    ),
    FEATURES(
        displayName = "Features",
        icon = Icons.Default.Star,
        description = "Code folding, brackets, and guides"
    ),
    CARET(
        displayName = "Caret",
        icon = Icons.Default.Edit,
        description = "Cursor style and blink rate"
    ),
    MINIMAP(
        displayName = "Minimap",
        icon = Icons.Default.ViewCompact,
        description = "Code overview panel"
    );

    companion object {
        val default: EditorSettingsCategory = VISUAL
    }
}

private val NavRailWidth = 180.dp

/**
 * Main settings panel for BossEditor with navigation sidebar.
 *
 * Displays all editor settings organized by category with immediate save.
 */
@Composable
fun EditorSettingsPanel(
    settings: EditorSettings,
    onSettingsChange: (EditorSettings) -> Unit,
    onResetToDefaults: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedCategory by remember { mutableStateOf(EditorSettingsCategory.default) }
    var showResetConfirmation by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundColor)
    ) {
        // Left navigation rail
        NavigationRail(
            selectedCategory = selectedCategory,
            onCategorySelected = { selectedCategory = it },
            modifier = Modifier
                .width(NavRailWidth)
                .fillMaxHeight()
        )

        // Divider
        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(BorderColor)
        )

        // Right content area
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            // Content
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                SettingsContent(
                    category = selectedCategory,
                    settings = settings,
                    onSettingsChange = onSettingsChange,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Footer with reset button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceColor)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Changes are saved automatically",
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                    TextButton(
                        onClick = { showResetConfirmation = true },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = TextSecondary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Reset to Defaults", fontSize = 13.sp)
                    }
                }
            }
        }
    }

    // Reset confirmation dialog
    if (showResetConfirmation) {
        AlertDialog(
            onDismissRequest = { showResetConfirmation = false },
            title = {
                Text(
                    text = "Reset Settings?",
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                Text(
                    text = "This will reset all settings to their default values. This action cannot be undone.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onResetToDefaults()
                        showResetConfirmation = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = Color(0xFFE04040)
                    )
                ) {
                    Text("Reset", color = TextPrimary)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showResetConfirmation = false }
                ) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            backgroundColor = SurfaceColor,
            contentColor = TextPrimary
        )
    }
}

// ========== Navigation Rail ==========

@Composable
private fun NavigationRail(
    selectedCategory: EditorSettingsCategory,
    onCategorySelected: (EditorSettingsCategory) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .background(SurfaceColor)
            .verticalScroll(scrollState)
            .padding(vertical = 8.dp)
    ) {
        EditorSettingsCategory.entries.forEach { category ->
            val isSelected = category == selectedCategory
            NavigationRailItem(
                category = category,
                isSelected = isSelected,
                onClick = { onCategorySelected(category) }
            )
        }
    }
}

@Composable
private fun NavigationRailItem(
    category: EditorSettingsCategory,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(if (isSelected) AccentColor.copy(alpha = 0.15f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Selection indicator
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(20.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (isSelected) AccentColor else Color.Transparent)
        )

        Icon(
            imageVector = category.icon,
            contentDescription = category.displayName,
            tint = if (isSelected) AccentColor else TextSecondary,
            modifier = Modifier.size(18.dp)
        )

        Text(
            text = category.displayName,
            color = if (isSelected) AccentColor else TextPrimary,
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
        )
    }
}

// ========== Settings Content ==========

@Composable
private fun SettingsContent(
    category: EditorSettingsCategory,
    settings: EditorSettings,
    onSettingsChange: (EditorSettings) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .verticalScroll(scrollState)
            .padding(20.dp)
    ) {
        // Category header
        Text(
            text = category.displayName,
            color = TextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Text(
            text = category.description,
            color = TextMuted,
            fontSize = 13.sp,
            modifier = Modifier.padding(bottom = 20.dp)
        )

        // Category-specific content
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (category) {
                EditorSettingsCategory.VISUAL -> VisualSettings(settings, onSettingsChange)
                EditorSettingsCategory.COLORS -> ColorsSettings(settings, onSettingsChange)
                EditorSettingsCategory.BEHAVIOR -> BehaviorSettings(settings, onSettingsChange)
                EditorSettingsCategory.FEATURES -> FeaturesSettings(settings, onSettingsChange)
                EditorSettingsCategory.CARET -> CaretSettings(settings, onSettingsChange)
                EditorSettingsCategory.MINIMAP -> MinimapSettings(settings, onSettingsChange)
            }
        }
    }
}

// ========== Category-Specific Sections ==========

@Composable
private fun VisualSettings(
    settings: EditorSettings,
    onSettingsChange: (EditorSettings) -> Unit
) {
    // Get categorized fonts (cached)
    val categorizedFonts = remember { getEditorCategorizedFonts() }

    // Font Section
    SettingsSection(title = "Font") {
        // Font Family - Sectioned dropdown like JetBrains
        SettingsSectionedDropdown(
            label = "Font Family",
            sections = linkedMapOf(
                FONT_SECTION_RECOMMENDED to (categorizedFonts[FONT_SECTION_RECOMMENDED] ?: emptyList()),
                FONT_SECTION_FIXED_PITCH to (categorizedFonts[FONT_SECTION_FIXED_PITCH] ?: emptyList()),
                FONT_SECTION_VARIABLE_PITCH to (categorizedFonts[FONT_SECTION_VARIABLE_PITCH] ?: emptyList())
            ),
            selectedOption = settings.fontFamily ?: DEFAULT_EDITOR_FONT_NAME,
            onOptionSelected = { selected ->
                val fontName = if (selected == DEFAULT_EDITOR_FONT_NAME) null else selected
                onSettingsChange(settings.copy(fontFamily = fontName))
            },
            description = "Monospace fonts recommended for code editing"
        )

        // Font Size
        SettingsSlider(
            label = "Font Size",
            value = settings.fontSize,
            onValueChange = { onSettingsChange(settings.copy(fontSize = it)) },
            valueRange = EditorSettings.MIN_FONT_SIZE..EditorSettings.MAX_FONT_SIZE,
            steps = (EditorSettings.MAX_FONT_SIZE - EditorSettings.MIN_FONT_SIZE).toInt() - 1,
            valueDisplay = { "${it.toInt()} px" },
            description = "Editor font size in pixels"
        )

        // Line Spacing
        SettingsSlider(
            label = "Line Spacing",
            value = settings.lineSpacing,
            onValueChange = { onSettingsChange(settings.copy(lineSpacing = it)) },
            valueRange = EditorSettings.MIN_LINE_SPACING..EditorSettings.MAX_LINE_SPACING,
            valueDisplay = { "%.2fx".format(it) },
            description = "Line height multiplier (1.0 = tight, 1.2 = normal)"
        )
    }

    Spacer(modifier = Modifier.height(16.dp))

    // Appearance Section
    SettingsSection(title = "Appearance") {
        // Theme
        SettingsDropdown(
            label = "Color Theme",
            selectedValue = settings.themeName,
            options = EditorSettings.availableThemes,
            onValueChange = { onSettingsChange(settings.copy(themeName = it)) },
            description = "Color theme for syntax highlighting"
        )

        // Show Line Numbers
        SettingsToggle(
            label = "Show Line Numbers",
            checked = settings.showLineNumbers,
            onCheckedChange = { onSettingsChange(settings.copy(showLineNumbers = it)) },
            description = "Display line numbers in the gutter"
        )

        // Highlight Current Line
        SettingsToggle(
            label = "Highlight Current Line",
            checked = settings.highlightCurrentLine,
            onCheckedChange = { onSettingsChange(settings.copy(highlightCurrentLine = it)) },
            description = "Highlight the line where the caret is"
        )
    }
}

@Composable
private fun ColorsSettings(
    settings: EditorSettings,
    onSettingsChange: (EditorSettings) -> Unit
) {
    // Import EditorTheme for color access
    val currentTheme = remember(settings.themeName) {
        ai.rever.bosseditor.theme.EditorTheme.forName(settings.themeName)
    }

    // Theme Selection Section
    SettingsSection(title = "Color Theme") {
        // Theme selector with preview
        SettingsDropdown(
            label = "Theme",
            selectedValue = settings.themeName,
            options = EditorSettings.availableThemes,
            onValueChange = { onSettingsChange(settings.copy(themeName = it)) },
            description = "Select a color theme for the editor"
        )
    }

    Spacer(modifier = Modifier.height(16.dp))

    // Theme Preview Section
    SettingsSection(title = "Theme Preview") {
        ThemePreviewCard(theme = currentTheme)
    }

    Spacer(modifier = Modifier.height(16.dp))

    // Editor Colors Section
    SettingsSection(title = "Editor Colors") {
        ColorSwatchRow(
            label = "Background",
            color = currentTheme.colors.background,
            description = "Editor and minimap background"
        )
        ColorSwatchRow(
            label = "Text",
            color = currentTheme.colors.text,
            description = "Default text and minimap foreground"
        )
        ColorSwatchRow(
            label = "Selection",
            color = currentTheme.colors.selectionBackground,
            description = "Selected text background"
        )
        ColorSwatchRow(
            label = "Current Line",
            color = currentTheme.colors.currentLineHighlight,
            description = "Current line highlight"
        )
        ColorSwatchRow(
            label = "Caret",
            color = currentTheme.colors.caret,
            description = "Cursor color"
        )
    }

    Spacer(modifier = Modifier.height(16.dp))

    // Syntax Colors Section
    SettingsSection(title = "Syntax Highlighting") {
        ColorSwatchRow(
            label = "Keywords",
            color = currentTheme.colors.keyword,
            description = "fun, val, var, class, etc."
        )
        ColorSwatchRow(
            label = "Strings",
            color = currentTheme.colors.string,
            description = "String literals"
        )
        ColorSwatchRow(
            label = "Numbers",
            color = currentTheme.colors.number,
            description = "Numeric literals"
        )
        ColorSwatchRow(
            label = "Comments",
            color = currentTheme.colors.comment,
            description = "Code comments"
        )
        ColorSwatchRow(
            label = "Functions",
            color = currentTheme.colors.function,
            description = "Function names"
        )
        ColorSwatchRow(
            label = "Annotations",
            color = currentTheme.colors.annotation,
            description = "@Composable, @Override, etc."
        )
    }

    Spacer(modifier = Modifier.height(16.dp))

    // Gutter Colors Section
    SettingsSection(title = "Gutter") {
        ColorSwatchRow(
            label = "Background",
            color = currentTheme.colors.gutterBackground,
            description = "Line number gutter background"
        )
        ColorSwatchRow(
            label = "Line Numbers",
            color = currentTheme.colors.lineNumber,
            description = "Inactive line numbers"
        )
        ColorSwatchRow(
            label = "Active Line",
            color = currentTheme.colors.lineNumberActive,
            description = "Current line number"
        )
    }

    Spacer(modifier = Modifier.height(16.dp))

    // Minimap Colors Section - uses editor colors
    SettingsSection(title = "Minimap (Uses Editor Colors)") {
        Text(
            text = "Minimap inherits colors from the editor theme for seamless integration",
            color = TextMuted,
            fontSize = 11.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        ColorSwatchRow(
            label = "Background",
            color = currentTheme.colors.background,  // Uses editor background
            description = "Same as editor background"
        )
        ColorSwatchRow(
            label = "Text",
            color = currentTheme.colors.text,  // Uses editor text
            description = "Same as editor text color"
        )
        ColorSwatchRow(
            label = "Current Line",
            color = currentTheme.colors.currentLineHighlight,  // Uses editor current line
            description = "Same as editor current line highlight"
        )
        ColorSwatchRow(
            label = "Viewport Indicator",
            color = currentTheme.colors.minimapViewport,
            description = "Visible area overlay"
        )
        ColorSwatchRow(
            label = "Border",
            color = currentTheme.colors.gutterBorder,  // Uses gutter border
            description = "Same as gutter border"
        )
    }
}

/**
 * Theme preview card showing a mini code snippet with syntax highlighting.
 */
@Composable
private fun ThemePreviewCard(
    theme: ai.rever.bosseditor.theme.EditorTheme
) {
    val colors = theme.colors

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(colors.background)
            .border(1.dp, BorderColor, RoundedCornerShape(8.dp))
            .padding(12.dp)
    ) {
        // Simulated code preview
        Row {
            // Line numbers
            Column(
                modifier = Modifier.padding(end = 12.dp),
                horizontalAlignment = Alignment.End
            ) {
                for (i in 1..5) {
                    Text(
                        text = "$i",
                        color = if (i == 2) colors.lineNumberActive else colors.lineNumber,
                        fontSize = 12.sp
                    )
                }
            }

            // Code content
            Column {
                // Line 1: annotation
                Text(
                    text = "@Composable",
                    color = colors.annotation,
                    fontSize = 12.sp
                )
                // Line 2: function declaration (highlighted as current line)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.currentLineHighlight)
                        .padding(horizontal = 4.dp)
                ) {
                    Text("fun ", color = colors.keyword, fontSize = 12.sp)
                    Text("greeting", color = colors.function, fontSize = 12.sp)
                    Text("(", color = colors.text, fontSize = 12.sp)
                    Text("name", color = colors.parameter, fontSize = 12.sp)
                    Text(": ", color = colors.text, fontSize = 12.sp)
                    Text("String", color = colors.dataType, fontSize = 12.sp)
                    Text(") {", color = colors.text, fontSize = 12.sp)
                }
                // Line 3: val declaration
                Row {
                    Text("    ", color = colors.text, fontSize = 12.sp)
                    Text("val ", color = colors.keyword, fontSize = 12.sp)
                    Text("msg ", color = colors.variable, fontSize = 12.sp)
                    Text("= ", color = colors.operator, fontSize = 12.sp)
                    Text("\"Hello, \"", color = colors.string, fontSize = 12.sp)
                }
                // Line 4: println
                Row {
                    Text("    ", color = colors.text, fontSize = 12.sp)
                    Text("println", color = colors.function, fontSize = 12.sp)
                    Text("(msg + name)", color = colors.text, fontSize = 12.sp)
                    Text(" // ", color = colors.comment, fontSize = 12.sp)
                    Text("Output", color = colors.comment, fontSize = 12.sp)
                }
                // Line 5: closing brace
                Text("}", color = colors.text, fontSize = 12.sp)
            }
        }
    }
}

/**
 * Color swatch row showing a color sample with label and description.
 * Clicking opens the color picker for preview (custom themes coming soon).
 */
@Composable
private fun ColorSwatchRow(
    label: String,
    color: Color,
    description: String,
    onColorClick: ((Color) -> Unit)? = null
) {
    var showColorPicker by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(SurfaceColor)
            .clickable { showColorPicker = true }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = TextPrimary,
                fontSize = 13.sp
            )
            Text(
                text = description,
                color = TextMuted,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }

        // Color swatch with hex code
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Hex code
            Text(
                text = color.toHexString(),
                color = TextSecondary,
                fontSize = 11.sp
            )
            // Color swatch
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(color)
                    .border(1.dp, BorderColor, RoundedCornerShape(4.dp))
            )
        }
    }

    // Color picker dialog
    if (showColorPicker) {
        ai.rever.bosseditor.settings.components.ColorPickerDialog(
            initialColor = color,
            onColorSelected = { selectedColor ->
                onColorClick?.invoke(selectedColor)
                showColorPicker = false
            },
            onDismiss = { showColorPicker = false },
            title = "Color: $label"
        )
    }
}

/**
 * Converts a Compose Color to a hex string.
 */
private fun Color.toHexString(): String {
    val r = (red * 255).toInt()
    val g = (green * 255).toInt()
    val b = (blue * 255).toInt()
    return "#%02X%02X%02X".format(r, g, b)
}

@Composable
private fun BehaviorSettings(
    settings: EditorSettings,
    onSettingsChange: (EditorSettings) -> Unit
) {
    // Scrolling Section
    SettingsSection(title = "Scrolling") {
        SettingsSlider(
            label = "Scroll Speed",
            value = settings.scrollSpeed,
            onValueChange = { onSettingsChange(settings.copy(scrollSpeed = it)) },
            valueRange = EditorSettings.MIN_SCROLL_SPEED..EditorSettings.MAX_SCROLL_SPEED,
            valueDisplay = { "%.1f lines".format(it) },
            description = "Lines to scroll per mouse wheel tick"
        )
    }

    Spacer(modifier = Modifier.height(16.dp))

    // Tabs and Indentation Section
    SettingsSection(title = "Tabs and Indentation") {
        SettingsSlider(
            label = "Tab Size",
            value = settings.tabSize.toFloat(),
            onValueChange = { onSettingsChange(settings.copy(tabSize = it.toInt())) },
            valueRange = EditorSettings.MIN_TAB_SIZE.toFloat()..EditorSettings.MAX_TAB_SIZE.toFloat(),
            steps = EditorSettings.MAX_TAB_SIZE - EditorSettings.MIN_TAB_SIZE - 1,
            valueDisplay = { "${it.toInt()} spaces" },
            description = "Number of spaces per tab"
        )

        SettingsToggle(
            label = "Use Spaces for Tabs",
            checked = settings.useSpacesForTabs,
            onCheckedChange = { onSettingsChange(settings.copy(useSpacesForTabs = it)) },
            description = "Insert spaces when pressing Tab"
        )
    }

    Spacer(modifier = Modifier.height(16.dp))

    // Text Wrapping Section
    SettingsSection(title = "Text Wrapping") {
        SettingsToggle(
            label = "Word Wrap",
            checked = settings.wordWrap,
            onCheckedChange = { onSettingsChange(settings.copy(wordWrap = it)) },
            description = "Wrap long lines to fit the editor width"
        )
    }
}

@Composable
private fun FeaturesSettings(
    settings: EditorSettings,
    onSettingsChange: (EditorSettings) -> Unit
) {
    // Code Navigation Section
    SettingsSection(title = "Code Navigation") {
        SettingsToggle(
            label = "Code Folding",
            checked = settings.foldingEnabled,
            onCheckedChange = { onSettingsChange(settings.copy(foldingEnabled = it)) },
            description = "Enable collapsing code blocks"
        )
    }

    Spacer(modifier = Modifier.height(16.dp))

    // Bracket Settings Section
    SettingsSection(title = "Brackets") {
        SettingsToggle(
            label = "Rainbow Brackets",
            checked = settings.rainbowBracketsEnabled,
            onCheckedChange = { onSettingsChange(settings.copy(rainbowBracketsEnabled = it)) },
            description = "Colorize matching brackets by nesting level"
        )

        SettingsToggle(
            label = "Bracket Matching",
            checked = settings.bracketMatchingEnabled,
            onCheckedChange = { onSettingsChange(settings.copy(bracketMatchingEnabled = it)) },
            description = "Highlight matching brackets when cursor is adjacent"
        )
    }

    Spacer(modifier = Modifier.height(16.dp))

    // Visual Guides Section
    SettingsSection(title = "Visual Guides") {
        SettingsToggle(
            label = "Indent Guides",
            checked = settings.indentGuidesEnabled,
            onCheckedChange = { onSettingsChange(settings.copy(indentGuidesEnabled = it)) },
            description = "Show vertical lines at indentation levels"
        )

        SettingsToggle(
            label = "Mark Occurrences",
            checked = settings.markOccurrencesEnabled,
            onCheckedChange = { onSettingsChange(settings.copy(markOccurrencesEnabled = it)) },
            description = "Highlight other occurrences of selected word"
        )
    }
}

@Composable
private fun CaretSettings(
    settings: EditorSettings,
    onSettingsChange: (EditorSettings) -> Unit
) {
    SettingsSection(title = "Cursor Appearance") {
        SettingsDropdown(
            label = "Caret Style",
            selectedValue = settings.caretStyle,
            options = EditorSettings.caretStyles,
            onValueChange = { onSettingsChange(settings.copy(caretStyle = it)) },
            description = "Shape of the text cursor"
        )

        SettingsSlider(
            label = "Caret Blink Rate",
            value = settings.caretBlinkRate.toFloat(),
            onValueChange = { onSettingsChange(settings.copy(caretBlinkRate = it.toInt())) },
            valueRange = EditorSettings.MIN_CARET_BLINK_RATE.toFloat()..EditorSettings.MAX_CARET_BLINK_RATE.toFloat(),
            steps = 9,
            valueDisplay = { if (it.toInt() == 0) "No blink" else "${it.toInt()} ms" },
            description = "Cursor blink interval (0 = no blink)"
        )
    }
}

@Composable
private fun MinimapSettings(
    settings: EditorSettings,
    onSettingsChange: (EditorSettings) -> Unit
) {
    // State for color picker dialog
    var showBackgroundColorPicker by remember { mutableStateOf(false) }
    var showForegroundColorPicker by remember { mutableStateOf(false) }

    SettingsSection(title = "Code Overview") {
        SettingsToggle(
            label = "Show Minimap",
            checked = settings.showMinimap,
            onCheckedChange = { onSettingsChange(settings.copy(showMinimap = it)) },
            description = "Display code overview on the right side"
        )

        SettingsSlider(
            label = "Minimap Width",
            value = settings.minimapWidth.toFloat(),
            onValueChange = { onSettingsChange(settings.copy(minimapWidth = it.toInt())) },
            valueRange = EditorSettings.MIN_MINIMAP_WIDTH.toFloat()..EditorSettings.MAX_MINIMAP_WIDTH.toFloat(),
            valueDisplay = { "${it.toInt()} px" },
            description = "Width of the minimap panel",
            enabled = settings.showMinimap
        )
    }

    Spacer(modifier = Modifier.height(24.dp))

    SettingsSection(title = "Minimap Colors") {
        SettingsToggle(
            label = "Use Editor Theme Colors",
            checked = settings.minimapUseEditorColors,
            onCheckedChange = { onSettingsChange(settings.copy(minimapUseEditorColors = it)) },
            description = "When enabled, minimap uses the same colors as the editor. Disable to customize."
        )

        // Show custom color options when not using editor colors
        if (!settings.minimapUseEditorColors) {
            Spacer(modifier = Modifier.height(12.dp))

            // Background color picker
            MinimapColorSetting(
                label = "Background Color",
                colorHex = settings.minimapBackgroundColor,
                defaultColorHex = "1E1F22",
                onColorChange = { onSettingsChange(settings.copy(minimapBackgroundColor = it)) },
                onPickerClick = { showBackgroundColorPicker = true }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Foreground color picker
            MinimapColorSetting(
                label = "Text Color",
                colorHex = settings.minimapForegroundColor,
                defaultColorHex = "BCBEC4",
                onColorChange = { onSettingsChange(settings.copy(minimapForegroundColor = it)) },
                onPickerClick = { showForegroundColorPicker = true }
            )
        }
    }

    // Color picker dialogs
    if (showBackgroundColorPicker) {
        ColorPickerDialog(
            initialColor = parseHexToColor(settings.minimapBackgroundColor ?: "1E1F22"),
            title = "Minimap Background Color",
            onColorSelected = { color ->
                onSettingsChange(settings.copy(minimapBackgroundColor = colorToHex(color)))
                showBackgroundColorPicker = false
            },
            onDismiss = { showBackgroundColorPicker = false }
        )
    }

    if (showForegroundColorPicker) {
        ColorPickerDialog(
            initialColor = parseHexToColor(settings.minimapForegroundColor ?: "BCBEC4"),
            title = "Minimap Text Color",
            onColorSelected = { color ->
                onSettingsChange(settings.copy(minimapForegroundColor = colorToHex(color)))
                showForegroundColorPicker = false
            },
            onDismiss = { showForegroundColorPicker = false }
        )
    }
}

@Composable
private fun MinimapColorSetting(
    label: String,
    colorHex: String?,
    defaultColorHex: String,
    onColorChange: (String?) -> Unit,
    onPickerClick: () -> Unit
) {
    val displayHex = colorHex ?: defaultColorHex
    val color = parseHexToColor(displayHex)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = TextPrimary,
                fontSize = 13.sp
            )
            Text(
                text = "#$displayHex",
                color = TextMuted,
                fontSize = 11.sp
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Color swatch
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(color)
                    .border(1.dp, BorderColor, RoundedCornerShape(4.dp))
                    .clickable { onPickerClick() }
            )

            // Reset button
            if (colorHex != null) {
                Text(
                    text = "Reset",
                    color = AccentColor,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .clickable { onColorChange(null) }
                        .padding(4.dp)
                )
            }
        }
    }
}

/**
 * Parse hex string to Compose Color.
 */
private fun parseHexToColor(hex: String): Color {
    return try {
        val cleanHex = hex.removePrefix("#").removePrefix("0x")
        when (cleanHex.length) {
            6 -> {
                val colorLong = cleanHex.toLong(16)
                Color(
                    red = ((colorLong shr 16) and 0xFF).toInt() / 255f,
                    green = ((colorLong shr 8) and 0xFF).toInt() / 255f,
                    blue = (colorLong and 0xFF).toInt() / 255f,
                    alpha = 1f
                )
            }
            8 -> {
                val colorLong = cleanHex.toLong(16)
                Color(
                    alpha = ((colorLong shr 24) and 0xFF).toInt() / 255f,
                    red = ((colorLong shr 16) and 0xFF).toInt() / 255f,
                    green = ((colorLong shr 8) and 0xFF).toInt() / 255f,
                    blue = (colorLong and 0xFF).toInt() / 255f
                )
            }
            else -> Color.Gray
        }
    } catch (e: Exception) {
        Color.Gray
    }
}

/**
 * Convert Compose Color to hex string.
 */
private fun colorToHex(color: Color): String {
    val r = (color.red * 255).toInt()
    val g = (color.green * 255).toInt()
    val b = (color.blue * 255).toInt()
    return "%02X%02X%02X".format(r, g, b)
}

// ========== Settings Components ==========

@Composable
private fun SettingsSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        Text(
            text = title,
            color = TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content
        )
    }
}

@Composable
private fun SettingsToggle(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    description: String? = null,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(SurfaceColor)
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = if (enabled) TextPrimary else TextMuted,
                fontSize = 13.sp
            )
            if (description != null) {
                Text(
                    text = description,
                    color = TextMuted,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = AccentColor,
                checkedTrackColor = AccentColor.copy(alpha = 0.5f),
                uncheckedThumbColor = TextMuted,
                uncheckedTrackColor = BorderColor
            )
        )
    }
}

@Composable
private fun SettingsSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    valueDisplay: (Float) -> String = { "%.1f".format(it) },
    description: String? = null,
    enabled: Boolean = true
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(SurfaceColor)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    color = if (enabled) TextPrimary else TextMuted,
                    fontSize = 13.sp
                )
                if (description != null) {
                    Text(
                        text = description,
                        color = TextMuted,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
            Text(
                text = valueDisplay(value),
                color = AccentColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = AccentColor,
                activeTrackColor = AccentColor,
                inactiveTrackColor = BorderColor,
                disabledThumbColor = TextMuted,
                disabledActiveTrackColor = TextMuted,
                disabledInactiveTrackColor = BorderColor
            ),
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun SettingsDropdown(
    label: String,
    selectedValue: String,
    options: List<String>,
    onValueChange: (String) -> Unit,
    description: String? = null,
    enabled: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(SurfaceColor)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    color = if (enabled) TextPrimary else TextMuted,
                    fontSize = 13.sp
                )
                if (description != null) {
                    Text(
                        text = description,
                        color = TextMuted,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            Box {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(BackgroundColor)
                        .border(1.dp, BorderColor, RoundedCornerShape(4.dp))
                        .clickable(enabled = enabled) { expanded = true }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = selectedValue,
                        color = if (enabled) TextPrimary else TextMuted,
                        fontSize = 13.sp
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = "Expand",
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.background(SurfaceColor)
                ) {
                    options.forEach { option ->
                        DropdownMenuItem(
                            onClick = {
                                onValueChange(option)
                                expanded = false
                            }
                        ) {
                            Text(
                                text = option,
                                color = if (option == selectedValue) AccentColor else TextPrimary,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * A sectioned dropdown selector with grouped options (like JetBrains font selector).
 * Each section has a non-selectable header.
 */
@Composable
private fun SettingsSectionedDropdown(
    label: String,
    sections: Map<String, List<String>>,
    selectedOption: String,
    onOptionSelected: (String) -> Unit,
    description: String? = null,
    enabled: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(SurfaceColor)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    color = if (enabled) TextPrimary else TextMuted,
                    fontSize = 13.sp
                )
                if (description != null) {
                    Text(
                        text = description,
                        color = TextMuted,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
            Box {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(BackgroundColor)
                        .border(1.dp, BorderColor, RoundedCornerShape(4.dp))
                        .clickable(enabled = enabled) { expanded = true }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = selectedOption,
                        color = TextPrimary,
                        fontSize = 13.sp,
                        maxLines = 1
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = "Expand",
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier
                        .background(SurfaceColor)
                        .heightIn(max = 400.dp)
                ) {
                    sections.forEach { (sectionName, options) ->
                        if (options.isNotEmpty()) {
                            // Section header (non-selectable)
                            DropdownMenuItem(
                                onClick = { /* Non-selectable */ },
                                enabled = false
                            ) {
                                Text(
                                    text = sectionName,
                                    color = AccentColor,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            // Section items
                            options.forEach { option ->
                                DropdownMenuItem(
                                    onClick = {
                                        onOptionSelected(option)
                                        expanded = false
                                    },
                                    modifier = Modifier.padding(start = 8.dp)
                                ) {
                                    Text(
                                        text = option,
                                        color = if (option == selectedOption) AccentColor else TextPrimary,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                            // Divider between sections (except after last)
                            if (sectionName != sections.keys.last()) {
                                Divider(color = BorderColor, modifier = Modifier.padding(vertical = 4.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
