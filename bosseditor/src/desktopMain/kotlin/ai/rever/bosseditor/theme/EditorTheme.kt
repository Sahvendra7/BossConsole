package ai.rever.bosseditor.theme

import ai.rever.bosseditor.highlight.TokenType
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Theme system for BossEditor.
 *
 * Supports 6 themes matching BOSS themes:
 * - Dark (IntelliJ Dark)
 * - Light (IntelliJ Light)
 * - Dracula
 * - Monokai
 * - Solarized Dark
 * - Solarized Light
 *
 * Colors are ported from RSyntaxThemeMapper for consistency.
 */

/**
 * All colors needed for editor rendering.
 */
@Immutable
data class EditorColors(
    // Editor background and text
    val background: Color,
    val text: Color,
    val caret: Color,
    val selectionBackground: Color,
    val selectionForeground: Color?,
    val currentLineHighlight: Color,
    val marginLine: Color,

    // Bracket matching
    val matchedBracketBackground: Color,
    val matchedBracketForeground: Color,

    // Gutter
    val gutterBackground: Color,
    val gutterBorder: Color,
    val lineNumber: Color,
    val lineNumberActive: Color,
    val foldIndicator: Color,
    val foldBackground: Color,

    // Fold placeholders and guides
    val foldPlaceholderBackground: Color,
    val foldPlaceholderHover: Color,
    val foldPlaceholderBorder: Color,
    val foldPlaceholderText: Color,
    val foldGuide: Color,
    val indentGuide: Color,
    val activeIndentGuide: Color,

    // Syntax highlighting
    val keyword: Color,
    val dataType: Color,
    val function: Color,
    val comment: Color,
    val docComment: Color,
    val commentKeyword: Color,
    val commentMarkup: Color,
    val string: Color,
    val number: Color,
    val boolean: Color,
    val operator: Color,
    val separator: Color,
    val preprocessor: Color,
    val annotation: Color,
    val variable: Color,
    val property: Color,
    val parameter: Color,
    val localVariable: Color,
    val regex: Color,
    val markupTag: Color,
    val markupTagName: Color,
    val markupAttribute: Color,
    val error: Color,

    // Search
    val searchMatchBackground: Color,
    val currentSearchMatchBackground: Color,

    // Other
    val hyperlink: Color,
    val markOccurrences: Color
) {
    /**
     * Gets the color for a token type.
     */
    fun getTokenColor(tokenType: TokenType): Color = when (tokenType) {
        // Basic
        TokenType.DEFAULT -> text
        TokenType.WHITESPACE -> text

        // Keywords
        TokenType.KEYWORD -> keyword
        TokenType.KEYWORD_MODIFIER -> keyword
        TokenType.KEYWORD_CONTROL -> keyword

        // Identifiers
        TokenType.IDENTIFIER -> text
        TokenType.FUNCTION -> function
        TokenType.FUNCTION_CALL -> function
        TokenType.TYPE -> dataType
        TokenType.TYPE_PARAMETER -> dataType
        TokenType.INTERFACE -> dataType
        TokenType.ENUM -> dataType
        TokenType.ENUM_MEMBER -> variable

        // Variables
        TokenType.VARIABLE -> variable
        TokenType.PARAMETER -> parameter
        TokenType.PROPERTY -> property
        TokenType.LOCAL_VARIABLE -> localVariable
        TokenType.CONSTANT -> variable

        // Literals
        TokenType.STRING -> string
        TokenType.STRING_ESCAPE -> regex  // Use regex color for escape sequences
        TokenType.STRING_TEMPLATE -> variable
        TokenType.CHAR -> string
        TokenType.NUMBER -> number
        TokenType.BOOLEAN -> boolean
        TokenType.NULL -> keyword

        // Comments
        TokenType.COMMENT -> comment
        TokenType.COMMENT_BLOCK -> comment
        TokenType.COMMENT_DOC -> docComment
        TokenType.COMMENT_DOC_TAG -> commentKeyword

        // Operators and punctuation
        TokenType.OPERATOR -> operator
        TokenType.OPERATOR_LOGICAL -> operator
        TokenType.OPERATOR_COMPARISON -> operator
        TokenType.PUNCTUATION -> separator
        TokenType.BRACKET -> separator
        TokenType.PARENTHESIS -> separator

        // Annotations
        TokenType.ANNOTATION -> annotation

        // Special
        TokenType.PREPROCESSOR -> preprocessor
        TokenType.REGEX -> regex
        TokenType.LABEL -> annotation

        // Markup/HTML
        TokenType.MARKUP_TAG -> markupTag
        TokenType.MARKUP_ATTRIBUTE -> markupAttribute
        TokenType.MARKUP_ENTITY -> markupTag

        // Semantic
        TokenType.SEMANTIC_VARIABLE -> variable
        TokenType.SEMANTIC_PARAMETER -> parameter
        TokenType.SEMANTIC_PROPERTY -> property
        TokenType.SEMANTIC_FUNCTION -> function

        // Errors
        TokenType.ERROR -> error
        TokenType.ERROR_DEPRECATED -> error

        // Special rendering
        TokenType.TODO -> commentKeyword
        TokenType.FIXME -> error
        TokenType.HYPERLINK -> hyperlink

        // Diff/Patch
        TokenType.INSERTION -> string  // Green-ish for added lines
        TokenType.DELETION -> error    // Red for removed lines
        TokenType.MODIFICATION -> variable  // Yellow/orange for changes
        TokenType.ESCAPE -> regex      // Same as string escapes
    }
}

/**
 * Editor theme configuration.
 */
@Immutable
data class EditorTheme(
    val name: String,
    val isDark: Boolean,
    val colors: EditorColors
) {
    companion object {
        /**
         * Gets a theme by name.
         */
        fun forName(name: String): EditorTheme = when (name) {
            "Light" -> Light
            "Dracula" -> Dracula
            "Monokai" -> Monokai
            "Solarized Dark" -> SolarizedDark
            "Solarized Light" -> SolarizedLight
            else -> Dark // Default
        }

        /**
         * Available theme names.
         */
        val availableThemes = listOf(
            "Dark",
            "Light",
            "Dracula",
            "Monokai",
            "Solarized Dark",
            "Solarized Light"
        )

        // ========== Theme Definitions ==========

        /**
         * IntelliJ Dark theme (default).
         */
        val Dark = EditorTheme(
            name = "Dark",
            isDark = true,
            colors = EditorColors(
                // Editor colors - IntelliJ Dark theme (expUI_darkScheme.xml)
                background = Color(0xFF1E1F22),            // #1e1f22
                text = Color(0xFFBCBEC4),                  // #bcbec4
                caret = Color(0xFFCED0D6),                 // #ced0d6
                selectionBackground = Color(0xFF214283),  // #214283
                selectionForeground = null,
                currentLineHighlight = Color(0xFF26282E), // #26282e
                marginLine = Color(0xFF393B40),            // #393b40

                // Bracket matching
                matchedBracketBackground = Color(0xFF3B514D),
                matchedBracketForeground = Color(0xFFFFEF28),

                // Gutter
                gutterBackground = Color(0xFF1E1F22),
                gutterBorder = Color(0xFF393B40),
                lineNumber = Color(0xFF4B5059),            // #4b5059
                lineNumberActive = Color(0xFFA1A3AB),      // brighter for active line
                foldIndicator = Color(0xFF6E737A),
                foldBackground = Color(0xFF1E1F22),

                // Fold placeholders and guides
                foldPlaceholderBackground = Color(0xFF2B2D30),
                foldPlaceholderHover = Color(0xFF3B3D40),
                foldPlaceholderBorder = Color(0xFF393B40),
                foldPlaceholderText = Color(0xFF8A8D91),
                foldGuide = Color(0xFF393B40),
                indentGuide = Color(0xFF393B40),
                activeIndentGuide = Color(0xFF6E737A),

                // Syntax colors - IntelliJ Dark exact colors
                keyword = Color(0xFFCF8E6D),               // #cf8e6d
                dataType = Color(0xFFBCBEC4),              // #bcbec4
                function = Color(0xFF56A8F5),              // #56a8f5
                comment = Color(0xFF7A7E85),               // #7a7e85
                docComment = Color(0xFF6AAB73),            // #6aab73
                commentKeyword = Color(0xFF67A37C),        // #67a37c
                commentMarkup = Color(0xFF68A67E),         // #68a67e
                string = Color(0xFF6AAB73),                // #6aab73
                number = Color(0xFF2AACB8),                // #2aacb8
                boolean = Color(0xFFCF8E6D),               // same as keyword
                operator = Color(0xFFBCBEC4),              // #bcbec4
                separator = Color(0xFFBCBEC4),
                preprocessor = Color(0xFFCF8E6D),
                annotation = Color(0xFFB3AE60),            // #b3ae60
                variable = Color(0xFFC77DBB),              // #c77dbb
                property = Color(0xFFC77DBB),              // same as variable
                parameter = Color(0xFFBCBEC4),             // same as text
                localVariable = Color(0xFFBCBEC4),         // same as text
                regex = Color(0xFF6AAB73),
                markupTag = Color(0xFF2FBAA3),             // #2fbaa3
                markupTagName = Color(0xFF2FBAA3),
                markupAttribute = Color(0xFFBCBEC4),
                error = Color(0xFFF75464),                 // #f75464

                // Search
                searchMatchBackground = Color(0xFF32593D),
                currentSearchMatchBackground = Color(0xFF5A8F5C),

                // Other
                hyperlink = Color(0xFF548AF7),             // #548af7
                markOccurrences = Color(0xFF32593D)
            )
        )

        /**
         * IntelliJ Light theme.
         */
        val Light = EditorTheme(
            name = "Light",
            isDark = false,
            colors = EditorColors(
                background = Color(0xFFFFFFFF),
                text = Color(0xFF000000),
                caret = Color(0xFF000000),
                selectionBackground = Color(0xFFADD6FF),
                selectionForeground = null,
                currentLineHighlight = Color(0xFFFCFCED),
                marginLine = Color(0xFFE0E0E0),

                matchedBracketBackground = Color(0xFFCCFFCC),
                matchedBracketForeground = Color(0xFF000000),

                gutterBackground = Color(0xFFF6F8FA),
                gutterBorder = Color(0xFFE0E0E0),
                lineNumber = Color(0xFF6E7681),
                lineNumberActive = Color(0xFF000000),
                foldIndicator = Color(0xFF6E7681),
                foldBackground = Color(0xFFF6F8FA),

                // Fold placeholders and guides
                foldPlaceholderBackground = Color(0xFFEEF0F2),
                foldPlaceholderHover = Color(0xFFE4E6E8),
                foldPlaceholderBorder = Color(0xFFD0D7DE),
                foldPlaceholderText = Color(0xFF6E7681),
                foldGuide = Color(0xFFD0D7DE),
                indentGuide = Color(0xFFE0E0E0),
                activeIndentGuide = Color(0xFF6E7681),

                keyword = Color(0xFFCF222E),
                dataType = Color(0xFF095079),
                function = Color(0xFF795E26),
                comment = Color(0xFF6E7781),
                docComment = Color(0xFF0A3D06),
                commentKeyword = Color(0xFF6E7781),
                commentMarkup = Color(0xFF6E7781),
                string = Color(0xFF0A3D06),
                number = Color(0xFF098658),
                boolean = Color(0xFFCF222E),
                operator = Color(0xFF000000),
                separator = Color(0xFF000000),
                preprocessor = Color(0xFFCF222E),
                annotation = Color(0xFF795E26),
                variable = Color(0xFF000000),
                property = Color(0xFF000000),
                parameter = Color(0xFF000000),
                localVariable = Color(0xFF000000),
                regex = Color(0xFF032F62),
                markupTag = Color(0xFF22863A),
                markupTagName = Color(0xFF22863A),
                markupAttribute = Color(0xFF095079),
                error = Color(0xFFCF222E),

                searchMatchBackground = Color(0xFFFFFF00),
                currentSearchMatchBackground = Color(0xFFFFA500),

                hyperlink = Color(0xFF0066CC),
                markOccurrences = Color(0xFFE8E8E8)
            )
        )

        /**
         * Dracula theme.
         */
        val Dracula = EditorTheme(
            name = "Dracula",
            isDark = true,
            colors = EditorColors(
                background = Color(0xFF282A36),
                text = Color(0xFFF8F8F2),
                caret = Color(0xFFF8F8F0),
                selectionBackground = Color(0xFF44475A),
                selectionForeground = null,
                currentLineHighlight = Color(0xFF44475A),
                marginLine = Color(0xFF44475A),

                matchedBracketBackground = Color(0xFF44475A),
                matchedBracketForeground = Color(0xFFFFB86C),

                gutterBackground = Color(0xFF21222C),
                gutterBorder = Color(0xFF44475A),
                lineNumber = Color(0xFF6272A4),
                lineNumberActive = Color(0xFFF8F8F2),
                foldIndicator = Color(0xFF6272A4),
                foldBackground = Color(0xFF21222C),

                // Fold placeholders and guides
                foldPlaceholderBackground = Color(0xFF343746),
                foldPlaceholderHover = Color(0xFF44475A),
                foldPlaceholderBorder = Color(0xFF44475A),
                foldPlaceholderText = Color(0xFF6272A4),
                foldGuide = Color(0xFF44475A),
                indentGuide = Color(0xFF44475A),
                activeIndentGuide = Color(0xFF6272A4),

                keyword = Color(0xFFFF79C6),       // Pink
                dataType = Color(0xFF8BE9FD),      // Cyan
                function = Color(0xFF50FA7B),      // Green
                comment = Color(0xFF6272A4),       // Comment purple
                docComment = Color(0xFF50FA7B),    // Green
                commentKeyword = Color(0xFF6272A4),
                commentMarkup = Color(0xFF6272A4),
                string = Color(0xFFF1FA8C),        // Yellow
                number = Color(0xFFBD93F9),        // Purple
                boolean = Color(0xFFBD93F9),       // Purple
                operator = Color(0xFFFF79C6),      // Pink
                separator = Color(0xFFF8F8F2),
                preprocessor = Color(0xFFFF79C6),
                annotation = Color(0xFF50FA7B),    // Green
                variable = Color(0xFFF8F8F2),
                property = Color(0xFFF8F8F2),
                parameter = Color(0xFFFFB86C),     // Orange
                localVariable = Color(0xFFF8F8F2),
                regex = Color(0xFFFF5555),         // Red
                markupTag = Color(0xFFFF79C6),
                markupTagName = Color(0xFFFF79C6),
                markupAttribute = Color(0xFF50FA7B),
                error = Color(0xFFFF5555),

                searchMatchBackground = Color(0xFF50FA7B).copy(alpha = 0.3f),
                currentSearchMatchBackground = Color(0xFFFFB86C).copy(alpha = 0.5f),

                hyperlink = Color(0xFF8BE9FD),
                markOccurrences = Color(0xFF44475A)
            )
        )

        /**
         * Monokai theme.
         */
        val Monokai = EditorTheme(
            name = "Monokai",
            isDark = true,
            colors = EditorColors(
                background = Color(0xFF272822),
                text = Color(0xFFF8F8F2),
                caret = Color(0xFFF8F8F0),
                selectionBackground = Color(0xFF49483E),
                selectionForeground = null,
                currentLineHighlight = Color(0xFF3E3D32),
                marginLine = Color(0xFF3E3D32),

                matchedBracketBackground = Color(0xFF49483E),
                matchedBracketForeground = Color(0xFFF8F8F2),

                gutterBackground = Color(0xFF1E1F1C),
                gutterBorder = Color(0xFF3E3D32),
                lineNumber = Color(0xFF75715E),
                lineNumberActive = Color(0xFFF8F8F2),
                foldIndicator = Color(0xFF75715E),
                foldBackground = Color(0xFF1E1F1C),

                // Fold placeholders and guides
                foldPlaceholderBackground = Color(0xFF383830),
                foldPlaceholderHover = Color(0xFF49483E),
                foldPlaceholderBorder = Color(0xFF49483E),
                foldPlaceholderText = Color(0xFF75715E),
                foldGuide = Color(0xFF3E3D32),
                indentGuide = Color(0xFF3E3D32),
                activeIndentGuide = Color(0xFF75715E),

                keyword = Color(0xFFF92672),       // Pink/Red
                dataType = Color(0xFF66D9EF),      // Blue
                function = Color(0xFFA6E22E),      // Green
                comment = Color(0xFF75715E),       // Gray
                docComment = Color(0xFFA6E22E),    // Green
                commentKeyword = Color(0xFF75715E),
                commentMarkup = Color(0xFF75715E),
                string = Color(0xFFE6DB74),        // Yellow
                number = Color(0xFFAE81FF),        // Purple
                boolean = Color(0xFFAE81FF),       // Purple
                operator = Color(0xFFF92672),      // Pink/Red
                separator = Color(0xFFF8F8F2),
                preprocessor = Color(0xFFF92672),
                annotation = Color(0xFFA6E22E),    // Green
                variable = Color(0xFFF8F8F2),
                property = Color(0xFFF8F8F2),
                parameter = Color(0xFFFD971F),     // Orange
                localVariable = Color(0xFFF8F8F2),
                regex = Color(0xFFE6DB74),         // Yellow
                markupTag = Color(0xFFF92672),
                markupTagName = Color(0xFFF92672),
                markupAttribute = Color(0xFFA6E22E),
                error = Color(0xFFF92672),

                searchMatchBackground = Color(0xFFA6E22E).copy(alpha = 0.3f),
                currentSearchMatchBackground = Color(0xFFFD971F).copy(alpha = 0.5f),

                hyperlink = Color(0xFF66D9EF),
                markOccurrences = Color(0xFF49483E)
            )
        )

        /**
         * Solarized Dark theme.
         */
        val SolarizedDark = EditorTheme(
            name = "Solarized Dark",
            isDark = true,
            colors = EditorColors(
                background = Color(0xFF002B36),
                text = Color(0xFF839496),
                caret = Color(0xFF839496),
                selectionBackground = Color(0xFF073642),
                selectionForeground = null,
                currentLineHighlight = Color(0xFF073642),
                marginLine = Color(0xFF073642),

                matchedBracketBackground = Color(0xFF073642),
                matchedBracketForeground = Color(0xFFB58900),

                gutterBackground = Color(0xFF073642),
                gutterBorder = Color(0xFF073642),
                lineNumber = Color(0xFF586E75),
                lineNumberActive = Color(0xFF93A1A1),
                foldIndicator = Color(0xFF586E75),
                foldBackground = Color(0xFF073642),

                // Fold placeholders and guides
                foldPlaceholderBackground = Color(0xFF0A4351),
                foldPlaceholderHover = Color(0xFF073642),
                foldPlaceholderBorder = Color(0xFF586E75),
                foldPlaceholderText = Color(0xFF657B83),
                foldGuide = Color(0xFF073642),
                indentGuide = Color(0xFF073642),
                activeIndentGuide = Color(0xFF586E75),

                keyword = Color(0xFF859900),       // Green
                dataType = Color(0xFFB58900),      // Yellow
                function = Color(0xFF268BD2),      // Blue
                comment = Color(0xFF586E75),       // Base01
                docComment = Color(0xFF859900),    // Green
                commentKeyword = Color(0xFF586E75),
                commentMarkup = Color(0xFF586E75),
                string = Color(0xFF2AA198),        // Cyan
                number = Color(0xFFD33682),        // Magenta
                boolean = Color(0xFFCB4B16),       // Orange
                operator = Color(0xFF839496),
                separator = Color(0xFF839496),
                preprocessor = Color(0xFFCB4B16),
                annotation = Color(0xFF268BD2),
                variable = Color(0xFF268BD2),
                property = Color(0xFF268BD2),
                parameter = Color(0xFF839496),
                localVariable = Color(0xFF839496),
                regex = Color(0xFFDC322F),         // Red
                markupTag = Color(0xFF268BD2),
                markupTagName = Color(0xFF268BD2),
                markupAttribute = Color(0xFF93A1A1),
                error = Color(0xFFDC322F),

                searchMatchBackground = Color(0xFF859900).copy(alpha = 0.3f),
                currentSearchMatchBackground = Color(0xFFB58900).copy(alpha = 0.5f),

                hyperlink = Color(0xFF268BD2),
                markOccurrences = Color(0xFF073642)
            )
        )

        /**
         * Solarized Light theme.
         */
        val SolarizedLight = EditorTheme(
            name = "Solarized Light",
            isDark = false,
            colors = EditorColors(
                background = Color(0xFFFDF6E3),
                text = Color(0xFF657B83),
                caret = Color(0xFF657B83),
                selectionBackground = Color(0xFFEEE8D5),
                selectionForeground = null,
                currentLineHighlight = Color(0xFFEEE8D5),
                marginLine = Color(0xFFEEE8D5),

                matchedBracketBackground = Color(0xFFEEE8D5),
                matchedBracketForeground = Color(0xFFB58900),

                gutterBackground = Color(0xFFEEE8D5),
                gutterBorder = Color(0xFFEEE8D5),
                lineNumber = Color(0xFF93A1A1),
                lineNumberActive = Color(0xFF586E75),
                foldIndicator = Color(0xFF93A1A1),
                foldBackground = Color(0xFFEEE8D5),

                // Fold placeholders and guides
                foldPlaceholderBackground = Color(0xFFE8E2CC),
                foldPlaceholderHover = Color(0xFFEEE8D5),
                foldPlaceholderBorder = Color(0xFF93A1A1),
                foldPlaceholderText = Color(0xFF657B83),
                foldGuide = Color(0xFFEEE8D5),
                indentGuide = Color(0xFFEEE8D5),
                activeIndentGuide = Color(0xFF93A1A1),

                keyword = Color(0xFF859900),       // Green
                dataType = Color(0xFFB58900),      // Yellow
                function = Color(0xFF268BD2),      // Blue
                comment = Color(0xFF93A1A1),       // Base1
                docComment = Color(0xFF859900),    // Green
                commentKeyword = Color(0xFF93A1A1),
                commentMarkup = Color(0xFF93A1A1),
                string = Color(0xFF2AA198),        // Cyan
                number = Color(0xFFD33682),        // Magenta
                boolean = Color(0xFFCB4B16),       // Orange
                operator = Color(0xFF657B83),
                separator = Color(0xFF657B83),
                preprocessor = Color(0xFFCB4B16),
                annotation = Color(0xFF268BD2),
                variable = Color(0xFF268BD2),
                property = Color(0xFF268BD2),
                parameter = Color(0xFF657B83),
                localVariable = Color(0xFF657B83),
                regex = Color(0xFFDC322F),         // Red
                markupTag = Color(0xFF268BD2),
                markupTagName = Color(0xFF268BD2),
                markupAttribute = Color(0xFF586E75),
                error = Color(0xFFDC322F),

                searchMatchBackground = Color(0xFF859900).copy(alpha = 0.3f),
                currentSearchMatchBackground = Color(0xFFB58900).copy(alpha = 0.5f),

                hyperlink = Color(0xFF268BD2),
                markOccurrences = Color(0xFFEEE8D5)
            )
        )
    }
}

/**
 * CompositionLocal for providing editor theme.
 */
val LocalEditorTheme = staticCompositionLocalOf { EditorTheme.Dark }
