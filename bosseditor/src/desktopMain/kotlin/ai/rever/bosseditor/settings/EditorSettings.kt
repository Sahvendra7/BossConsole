package ai.rever.bosseditor.settings

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * Comprehensive editor settings for BossEditor.
 *
 * Settings are organized into categories:
 * - Visual: Font, colors, theme
 * - Behavior: Scroll speed, tab handling
 * - Features: Code folding, rainbow brackets, indent guides
 *
 * All settings are serializable for JSON persistence to ~/.boss/editor-settings.json
 */
@Serializable
data class EditorSettings(
    // ========== Visual Settings ==========

    /** Font family name (null = JetBrains Mono or system default) */
    val fontFamily: String? = null,

    /** Font size in scaled pixels */
    val fontSize: Float = 14f,

    /** Line height multiplier (1.0 = tight, 1.2 = comfortable, 1.5 = spacious) */
    val lineSpacing: Float = 1.2f,

    /** Theme name (Dark, Light, Dracula, Monokai, Solarized Dark, Solarized Light) */
    val themeName: String = "Dark",

    /** Whether to show line numbers in gutter */
    val showLineNumbers: Boolean = true,

    /** Whether to highlight the current line */
    val highlightCurrentLine: Boolean = true,

    // ========== Behavior Settings ==========

    /** Lines to scroll per mouse wheel tick (0.5 to 5.0) */
    val scrollSpeed: Float = 1.5f,

    /** Tab size in spaces */
    val tabSize: Int = 4,

    /** Whether to use spaces instead of tabs */
    val useSpacesForTabs: Boolean = true,

    /** Whether word wrap is enabled */
    val wordWrap: Boolean = false,

    // ========== Feature Settings ==========

    /** Whether code folding is enabled */
    val foldingEnabled: Boolean = true,

    /** Whether rainbow bracket colorization is enabled */
    val rainbowBracketsEnabled: Boolean = true,

    /** Whether indent guides are shown */
    val indentGuidesEnabled: Boolean = true,

    /** Whether bracket matching highlight is enabled */
    val bracketMatchingEnabled: Boolean = true,

    /** Whether mark occurrences is enabled */
    val markOccurrencesEnabled: Boolean = true,

    // ========== Caret Settings ==========

    /** Caret blink rate in milliseconds (0 = no blink) */
    val caretBlinkRate: Int = 530,

    /** Caret style: "line", "block", "underline" */
    val caretStyle: String = "line",

    // ========== Minimap Settings ==========

    /** Whether to show the minimap */
    val showMinimap: Boolean = false,

    /** Minimap width in pixels */
    val minimapWidth: Int = 80,

    /** Whether minimap should use editor theme colors (true) or custom colors (false) */
    val minimapUseEditorColors: Boolean = true,

    /** Custom minimap background color in ARGB hex (e.g., "FF1E1F22"), null = use editor color */
    val minimapBackgroundColor: String? = null,

    /** Custom minimap text/content color in ARGB hex, null = use editor color */
    val minimapForegroundColor: String? = null
) {
    companion object {
        /** Default settings instance */
        val Default = EditorSettings()

        /** Available theme names */
        val availableThemes = listOf(
            "Dark",
            "Light",
            "Dracula",
            "Monokai",
            "Solarized Dark",
            "Solarized Light"
        )

        /** Available caret styles */
        val caretStyles = listOf("line", "block", "underline")

        /** Minimum and maximum values for sliders */
        const val MIN_FONT_SIZE = 8f
        const val MAX_FONT_SIZE = 32f
        const val MIN_LINE_SPACING = 1.0f
        const val MAX_LINE_SPACING = 2.0f
        const val MIN_SCROLL_SPEED = 0.5f
        const val MAX_SCROLL_SPEED = 5.0f
        const val MIN_TAB_SIZE = 2
        const val MAX_TAB_SIZE = 8
        const val MIN_CARET_BLINK_RATE = 0
        const val MAX_CARET_BLINK_RATE = 1000
        const val MIN_MINIMAP_WIDTH = 50
        const val MAX_MINIMAP_WIDTH = 150
    }
}
