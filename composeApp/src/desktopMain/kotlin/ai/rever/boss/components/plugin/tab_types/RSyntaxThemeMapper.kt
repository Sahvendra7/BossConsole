package ai.rever.boss.components.plugin.tab_types

import ai.rever.boss.utils.toAwtColor
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea
import org.fife.ui.rsyntaxtextarea.SyntaxScheme
import org.fife.ui.rsyntaxtextarea.Style
import org.fife.ui.rsyntaxtextarea.Token
import org.fife.ui.rsyntaxtextarea.Theme
import org.fife.ui.rsyntaxtextarea.folding.FoldParserManager
import java.awt.Color
import java.awt.Font

/**
 * Maps BOSS editor themes to RSyntaxTextArea themes.
 *
 * BOSS supports 6 themes: Dark, Light, Dracula, Monokai, Solarized Dark, Solarized Light
 * RSyntaxTextArea has built-in themes: dark, default, druid, eclipse, idea, monokai, vs
 *
 * This mapper creates custom themes based on BOSS color schemes.
 */
object RSyntaxThemeMapper {

    /**
     * Applies the current BOSS theme to an RSyntaxTextArea instance.
     */
    fun applyTheme(textArea: RSyntaxTextArea, bossTheme: String = CodeEditorSettings.theme) {
        val theme = createTheme(bossTheme, textArea.font)
        theme.apply(textArea)

        // Also explicitly set the textArea foreground color for base text
        val colors = getThemeColors(bossTheme)
        textArea.foreground = colors.text

        // CRITICAL FIX: RSyntaxTextArea 3.5.4's KotlinTokenMaker is broken - it doesn't
        // recognize keywords. Use our FixedKotlinTokenMaker instead.
        val currentStyle = textArea.syntaxEditingStyle
        if (currentStyle == org.fife.ui.rsyntaxtextarea.SyntaxConstants.SYNTAX_STYLE_KOTLIN) {
            val doc = textArea.document
            if (doc is org.fife.ui.rsyntaxtextarea.RSyntaxDocument) {
                // Use our fixed Kotlin TokenMaker that properly recognizes keywords
                val fixedTokenMaker = FixedKotlinTokenMaker()
                doc.setSyntaxStyle(fixedTokenMaker)
            }

            // Register our custom Kotlin fold parser that handles import folding
            FoldParserManager.get().addFoldParserMapping(
                org.fife.ui.rsyntaxtextarea.SyntaxConstants.SYNTAX_STYLE_KOTLIN,
                KotlinFoldParser()
            )

            // Re-fold the document to apply the new fold parser
            textArea.foldManager?.reparse()
        }

        // Save and restore text to force re-tokenization with the fixed TokenMaker
        val savedText = textArea.text
        if (savedText.isNotEmpty()) {
            val savedCaretPos = textArea.caretPosition.coerceIn(0, savedText.length)
            textArea.text = ""
            textArea.text = savedText
            textArea.caretPosition = savedCaretPos.coerceIn(0, textArea.document.length)
        }

        // Force RSyntaxTextArea to re-tokenize the document with the new syntax scheme
        textArea.forceReparsing(0)

        // Trigger full UI refresh
        textArea.revalidate()
        textArea.repaint()
    }

    /**
     * Creates an RSyntaxTextArea Theme based on BOSS theme name.
     */
    fun createTheme(bossTheme: String, baseFont: Font? = null): Theme {
        // Try to load a base theme first, then customize
        val theme = loadBaseTheme(bossTheme)

        // Customize the theme with BOSS colors
        customizeTheme(theme, bossTheme, baseFont)

        return theme
    }

    /**
     * Loads a base RSyntaxTextArea theme that's closest to the BOSS theme.
     */
    private fun loadBaseTheme(bossTheme: String): Theme {
        val resourcePath = when (bossTheme) {
            "Light" -> "/org/fife/ui/rsyntaxtextarea/themes/idea.xml"
            "Dracula" -> "/org/fife/ui/rsyntaxtextarea/themes/dark.xml"
            "Monokai" -> "/org/fife/ui/rsyntaxtextarea/themes/monokai.xml"
            "Solarized Dark" -> "/org/fife/ui/rsyntaxtextarea/themes/dark.xml"
            "Solarized Light" -> "/org/fife/ui/rsyntaxtextarea/themes/idea.xml"
            else -> "/org/fife/ui/rsyntaxtextarea/themes/dark.xml" // Dark theme
        }

        return try {
            val stream = RSyntaxThemeMapper::class.java.getResourceAsStream(resourcePath)
            if (stream == null) {
                createFallbackTheme()
            } else {
                stream.use { Theme.load(it) }
            }
        } catch (e: Exception) {
            // Create a minimal fallback theme
            createFallbackTheme()
        }
    }

    /**
     * Creates a minimal fallback theme if base theme loading fails.
     */
    private fun createFallbackTheme(): Theme {
        return RSyntaxThemeMapper::class.java.getResourceAsStream(
            "/org/fife/ui/rsyntaxtextarea/themes/dark.xml"
        )?.use { stream ->
            Theme.load(stream)
        } ?: throw IllegalStateException("Failed to load fallback theme")
    }

    /**
     * Customizes the theme with BOSS-specific colors.
     */
    private fun customizeTheme(theme: Theme, bossTheme: String, baseFont: Font?) {
        val colors = getThemeColors(bossTheme)
        val scheme = theme.scheme

        // Set background color
        theme.bgColor = colors.background

        // Set caret color (cursor)
        theme.caretColor = colors.caret

        // Set selection colors
        theme.selectionBG = colors.selectionBackground
        theme.selectionFG = colors.selectionForeground

        // Set current line highlight
        theme.currentLineHighlight = colors.currentLineHighlight

        // Set margin line color
        theme.marginLineColor = colors.marginLine

        // Set matched bracket colors
        theme.matchedBracketBG = colors.matchedBracketBackground
        theme.matchedBracketFG = colors.matchedBracketForeground
        theme.matchedBracketAnimate = true

        // Set gutter colors
        theme.gutterBackgroundColor = colors.gutterBackground
        theme.gutterBorderColor = colors.gutterBorder
        theme.lineNumberColor = colors.lineNumber
        theme.foldIndicatorFG = colors.foldIndicator
        theme.foldBG = colors.foldBackground

        // Set hyperlink color
        theme.hyperlinkFG = colors.hyperlink

        // Set mark occurrences color
        theme.markOccurrencesColor = colors.markOccurrences

        // Customize syntax scheme colors
        customizeSyntaxScheme(scheme, colors, baseFont)
    }

    /**
     * Customizes the syntax highlighting colors.
     */
    private fun customizeSyntaxScheme(scheme: SyntaxScheme, colors: ThemeColors, baseFont: Font?) {
        // Default text
        scheme.getStyle(Token.IDENTIFIER)?.apply {
            foreground = colors.text
            baseFont?.let { font = it }
        }

        // Null/default style
        scheme.getStyle(Token.NULL)?.apply {
            foreground = colors.text
            background = null
        }

        // Reserved words (keywords)
        scheme.getStyle(Token.RESERVED_WORD)?.apply {
            foreground = colors.keyword
            font = baseFont?.deriveFont(Font.BOLD)
        }
        scheme.getStyle(Token.RESERVED_WORD_2)?.apply {
            foreground = colors.keyword
            font = baseFont?.deriveFont(Font.BOLD)
        }

        // Data types
        scheme.getStyle(Token.DATA_TYPE)?.apply {
            foreground = colors.dataType
        }

        // Functions - IntelliJ Darcula doesn't specially color regular function calls
        // They use text color. Only static methods get yellow italic, but RSyntaxTextArea
        // doesn't distinguish, so we use text color for authenticity.
        scheme.getStyle(Token.FUNCTION)?.apply {
            foreground = colors.function
            // No italic - IntelliJ only uses italic for static methods
        }

        // Comments
        scheme.getStyle(Token.COMMENT_EOL)?.apply {
            foreground = colors.comment
            font = baseFont?.deriveFont(Font.ITALIC)
        }
        scheme.getStyle(Token.COMMENT_MULTILINE)?.apply {
            foreground = colors.comment
            font = baseFont?.deriveFont(Font.ITALIC)
        }
        scheme.getStyle(Token.COMMENT_DOCUMENTATION)?.apply {
            foreground = colors.docComment
            font = baseFont?.deriveFont(Font.ITALIC)
        }
        scheme.getStyle(Token.COMMENT_KEYWORD)?.apply {
            foreground = colors.commentKeyword
            font = baseFont?.deriveFont(Font.BOLD or Font.ITALIC)
        }
        scheme.getStyle(Token.COMMENT_MARKUP)?.apply {
            foreground = colors.commentMarkup
        }

        // Strings
        scheme.getStyle(Token.LITERAL_STRING_DOUBLE_QUOTE)?.apply {
            foreground = colors.string
        }
        scheme.getStyle(Token.LITERAL_CHAR)?.apply {
            foreground = colors.string
        }
        scheme.getStyle(Token.LITERAL_BACKQUOTE)?.apply {
            foreground = colors.string
        }

        // Numbers
        scheme.getStyle(Token.LITERAL_NUMBER_DECIMAL_INT)?.apply {
            foreground = colors.number
        }
        scheme.getStyle(Token.LITERAL_NUMBER_FLOAT)?.apply {
            foreground = colors.number
        }
        scheme.getStyle(Token.LITERAL_NUMBER_HEXADECIMAL)?.apply {
            foreground = colors.number
        }

        // Booleans
        scheme.getStyle(Token.LITERAL_BOOLEAN)?.apply {
            foreground = colors.boolean
        }

        // Operators
        scheme.getStyle(Token.OPERATOR)?.apply {
            foreground = colors.operator
        }

        // Separators (brackets, parentheses)
        scheme.getStyle(Token.SEPARATOR)?.apply {
            foreground = colors.separator
        }

        // Preprocessor
        scheme.getStyle(Token.PREPROCESSOR)?.apply {
            foreground = colors.preprocessor
        }

        // Annotations
        scheme.getStyle(Token.ANNOTATION)?.apply {
            foreground = colors.annotation
        }

        // Variables
        scheme.getStyle(Token.VARIABLE)?.apply {
            foreground = colors.variable
        }

        // Regex
        scheme.getStyle(Token.REGEX)?.apply {
            foreground = colors.regex
        }

        // Markup (HTML/XML tags)
        scheme.getStyle(Token.MARKUP_TAG_DELIMITER)?.apply {
            foreground = colors.markupTag
        }
        scheme.getStyle(Token.MARKUP_TAG_NAME)?.apply {
            foreground = colors.markupTagName
        }
        scheme.getStyle(Token.MARKUP_TAG_ATTRIBUTE)?.apply {
            foreground = colors.markupAttribute
        }
        scheme.getStyle(Token.MARKUP_TAG_ATTRIBUTE_VALUE)?.apply {
            foreground = colors.string
        }

        // Errors
        scheme.getStyle(Token.ERROR_IDENTIFIER)?.apply {
            foreground = colors.error
        }
        scheme.getStyle(Token.ERROR_NUMBER_FORMAT)?.apply {
            foreground = colors.error
        }
        scheme.getStyle(Token.ERROR_STRING_DOUBLE)?.apply {
            foreground = colors.error
        }
        scheme.getStyle(Token.ERROR_CHAR)?.apply {
            foreground = colors.error
        }
    }

    /**
     * Gets the color palette for a specific BOSS theme.
     */
    private fun getThemeColors(bossTheme: String): ThemeColors {
        return when (bossTheme) {
            "Light" -> lightThemeColors()
            "Dracula" -> draculaThemeColors()
            "Monokai" -> monokaiThemeColors()
            "Solarized Dark" -> solarizedDarkThemeColors()
            "Solarized Light" -> solarizedLightThemeColors()
            else -> darkThemeColors() // Default "Dark" theme
        }
    }

    // ========== Theme Color Definitions ==========

    private fun darkThemeColors() = ThemeColors(
        // Editor colors - IntelliJ Dark theme (expUI_darkScheme.xml)
        background = Color(0x1E, 0x1F, 0x22),       // #1e1f22 - CONSOLE_BACKGROUND_KEY
        text = Color(0xBC, 0xBE, 0xC4),             // #bcbec4 - DEFAULT_IDENTIFIER
        caret = Color(0xCE, 0xD0, 0xD6),            // #ced0d6 - CARET_COLOR
        selectionBackground = Color(0x21, 0x42, 0x83), // #214283 - SELECTION_BACKGROUND
        selectionForeground = null,
        currentLineHighlight = Color(0x26, 0x28, 0x2E), // #26282e - CARET_ROW_COLOR
        marginLine = Color(0x39, 0x3B, 0x40),       // #393b40 - RIGHT_MARGIN_COLOR

        // Bracket matching
        matchedBracketBackground = Color(0x3B, 0x51, 0x4D),
        matchedBracketForeground = Color(0xFF, 0xEF, 0x28),

        // Gutter
        gutterBackground = Color(0x1E, 0x1F, 0x22), // same as background
        gutterBorder = Color(0x39, 0x3B, 0x40),
        lineNumber = Color(0x4B, 0x50, 0x59),       // #4b5059 - LINE_NUMBERS_COLOR
        foldIndicator = Color(0x6E, 0x73, 0x7A),  // Brighter for visibility
        foldBackground = Color(0x1E, 0x1F, 0x22),

        // Syntax colors - IntelliJ Dark theme exact colors
        // Reference: expUI_darkScheme.xml in intellij-community
        keyword = Color(0xCF, 0x8E, 0x6D),          // #cf8e6d - DEFAULT_KEYWORD
        dataType = Color(0xBC, 0xBE, 0xC4),         // #bcbec4 - same as identifier
        function = Color(0x56, 0xA8, 0xF5),         // #56a8f5 - DEFAULT_FUNCTION_DECLARATION (blue)
        comment = Color(0x7A, 0x7E, 0x85),          // #7a7e85 - DEFAULT_LINE_COMMENT
        docComment = Color(0x6A, 0xAB, 0x73),       // #6aab73 - DEFAULT_DOC_COMMENT (green)
        commentKeyword = Color(0x67, 0xA3, 0x7C),   // #67a37c - DEFAULT_DOC_COMMENT_TAG
        commentMarkup = Color(0x68, 0xA6, 0x7E),    // #68a67e - DEFAULT_DOC_MARKUP
        string = Color(0x6A, 0xAB, 0x73),           // #6aab73 - DEFAULT_STRING
        number = Color(0x2A, 0xAC, 0xB8),           // #2aacb8 - DEFAULT_NUMBER (cyan)
        boolean = Color(0xCF, 0x8E, 0x6D),          // #cf8e6d - same as keyword
        operator = Color(0xBC, 0xBE, 0xC4),         // #bcbec4 - DEFAULT_OPERATION_SIGN
        separator = Color(0xBC, 0xBE, 0xC4),        // #bcbec4
        preprocessor = Color(0xCF, 0x8E, 0x6D),     // #cf8e6d
        annotation = Color(0xB3, 0xAE, 0x60),       // #b3ae60 - DEFAULT_METADATA
        variable = Color(0xC7, 0x7D, 0xBB),         // #c77dbb - DEFAULT_INSTANCE_FIELD (purple/pink)
        regex = Color(0x6A, 0xAB, 0x73),            // #6aab73
        markupTag = Color(0x2F, 0xBA, 0xA3),        // #2fbaa3 - HTML_CUSTOM_TAG_NAME
        markupTagName = Color(0x2F, 0xBA, 0xA3),    // #2fbaa3
        markupAttribute = Color(0xBC, 0xBE, 0xC4),  // #bcbec4
        error = Color(0xF7, 0x54, 0x64),            // #f75464 - BAD_CHARACTER

        // Other
        hyperlink = Color(0x54, 0x8A, 0xF7),        // #548af7 - CTRL_CLICKABLE
        markOccurrences = Color(0x32, 0x59, 0x3D)
    )

    private fun lightThemeColors() = ThemeColors(
        // Editor colors
        background = Color(0xFF, 0xFF, 0xFF),
        text = Color(0x00, 0x00, 0x00),
        caret = Color(0x00, 0x00, 0x00),
        selectionBackground = Color(0xAD, 0xD6, 0xFF),
        selectionForeground = null,
        currentLineHighlight = Color(0xFC, 0xFC, 0xED),
        marginLine = Color(0xE0, 0xE0, 0xE0),

        // Bracket matching
        matchedBracketBackground = Color(0xCC, 0xFF, 0xCC),
        matchedBracketForeground = Color(0x00, 0x00, 0x00),

        // Gutter
        gutterBackground = Color(0xF6, 0xF8, 0xFA),
        gutterBorder = Color(0xE0, 0xE0, 0xE0),
        lineNumber = Color(0x6E, 0x76, 0x81),
        foldIndicator = Color(0x6E, 0x76, 0x81),
        foldBackground = Color(0xF6, 0xF8, 0xFA),

        // Syntax colors
        keyword = Color(0xCF, 0x22, 0x2E),
        dataType = Color(0x09, 0x50, 0x79),
        function = Color(0x79, 0x5E, 0x26),
        comment = Color(0x6E, 0x77, 0x81),
        docComment = Color(0x0A, 0x3D, 0x06),        // Green for doc comments
        commentKeyword = Color(0x6E, 0x77, 0x81),
        commentMarkup = Color(0x6E, 0x77, 0x81),
        string = Color(0x0A, 0x3D, 0x06),
        number = Color(0x09, 0x86, 0x58),
        boolean = Color(0xCF, 0x22, 0x2E),
        operator = Color(0x00, 0x00, 0x00),
        separator = Color(0x00, 0x00, 0x00),
        preprocessor = Color(0xCF, 0x22, 0x2E),
        annotation = Color(0x79, 0x5E, 0x26),
        variable = Color(0x00, 0x00, 0x00),
        regex = Color(0x03, 0x2F, 0x62),
        markupTag = Color(0x22, 0x86, 0x3A),
        markupTagName = Color(0x22, 0x86, 0x3A),
        markupAttribute = Color(0x09, 0x50, 0x79),
        error = Color(0xCF, 0x22, 0x2E),

        // Other
        hyperlink = Color(0x00, 0x66, 0xCC),
        markOccurrences = Color(0xE8, 0xE8, 0xE8)
    )

    private fun draculaThemeColors() = ThemeColors(
        // Editor colors
        background = Color(0x28, 0x2A, 0x36),
        text = Color(0xF8, 0xF8, 0xF2),
        caret = Color(0xF8, 0xF8, 0xF0),
        selectionBackground = Color(0x44, 0x47, 0x5A),
        selectionForeground = null,
        currentLineHighlight = Color(0x44, 0x47, 0x5A),
        marginLine = Color(0x44, 0x47, 0x5A),

        // Bracket matching
        matchedBracketBackground = Color(0x44, 0x47, 0x5A),
        matchedBracketForeground = Color(0xFF, 0xB8, 0x6C),

        // Gutter
        gutterBackground = Color(0x21, 0x22, 0x2C),
        gutterBorder = Color(0x44, 0x47, 0x5A),
        lineNumber = Color(0x62, 0x72, 0xA4),
        foldIndicator = Color(0x62, 0x72, 0xA4),
        foldBackground = Color(0x21, 0x22, 0x2C),

        // Syntax colors - Dracula palette
        keyword = Color(0xFF, 0x79, 0xC6),      // Pink
        dataType = Color(0x8B, 0xE9, 0xFD),     // Cyan
        function = Color(0x50, 0xFA, 0x7B),     // Green
        comment = Color(0x62, 0x72, 0xA4),      // Comment purple
        docComment = Color(0x50, 0xFA, 0x7B),   // Green for doc comments
        commentKeyword = Color(0x62, 0x72, 0xA4),
        commentMarkup = Color(0x62, 0x72, 0xA4),
        string = Color(0xF1, 0xFA, 0x8C),       // Yellow
        number = Color(0xBD, 0x93, 0xF9),       // Purple
        boolean = Color(0xBD, 0x93, 0xF9),      // Purple
        operator = Color(0xFF, 0x79, 0xC6),     // Pink
        separator = Color(0xF8, 0xF8, 0xF2),
        preprocessor = Color(0xFF, 0x79, 0xC6),
        annotation = Color(0x50, 0xFA, 0x7B),   // Green
        variable = Color(0xF8, 0xF8, 0xF2),
        regex = Color(0xFF, 0x55, 0x55),        // Red
        markupTag = Color(0xFF, 0x79, 0xC6),
        markupTagName = Color(0xFF, 0x79, 0xC6),
        markupAttribute = Color(0x50, 0xFA, 0x7B),
        error = Color(0xFF, 0x55, 0x55),        // Red

        // Other
        hyperlink = Color(0x8B, 0xE9, 0xFD),
        markOccurrences = Color(0x44, 0x47, 0x5A)
    )

    private fun monokaiThemeColors() = ThemeColors(
        // Editor colors
        background = Color(0x27, 0x28, 0x22),
        text = Color(0xF8, 0xF8, 0xF2),
        caret = Color(0xF8, 0xF8, 0xF0),
        selectionBackground = Color(0x49, 0x48, 0x3E),
        selectionForeground = null,
        currentLineHighlight = Color(0x3E, 0x3D, 0x32),
        marginLine = Color(0x3E, 0x3D, 0x32),

        // Bracket matching
        matchedBracketBackground = Color(0x49, 0x48, 0x3E),
        matchedBracketForeground = Color(0xF8, 0xF8, 0xF2),

        // Gutter
        gutterBackground = Color(0x1E, 0x1F, 0x1C),
        gutterBorder = Color(0x3E, 0x3D, 0x32),
        lineNumber = Color(0x75, 0x71, 0x5E),
        foldIndicator = Color(0x75, 0x71, 0x5E),
        foldBackground = Color(0x1E, 0x1F, 0x1C),

        // Syntax colors - Monokai palette
        keyword = Color(0xF9, 0x26, 0x72),      // Pink/Red
        dataType = Color(0x66, 0xD9, 0xEF),     // Blue
        function = Color(0xA6, 0xE2, 0x2E),     // Green
        comment = Color(0x75, 0x71, 0x5E),      // Gray
        docComment = Color(0xA6, 0xE2, 0x2E),   // Green for doc comments
        commentKeyword = Color(0x75, 0x71, 0x5E),
        commentMarkup = Color(0x75, 0x71, 0x5E),
        string = Color(0xE6, 0xDB, 0x74),       // Yellow
        number = Color(0xAE, 0x81, 0xFF),       // Purple
        boolean = Color(0xAE, 0x81, 0xFF),      // Purple
        operator = Color(0xF9, 0x26, 0x72),     // Pink/Red
        separator = Color(0xF8, 0xF8, 0xF2),
        preprocessor = Color(0xF9, 0x26, 0x72),
        annotation = Color(0xA6, 0xE2, 0x2E),   // Green
        variable = Color(0xF8, 0xF8, 0xF2),
        regex = Color(0xE6, 0xDB, 0x74),        // Yellow
        markupTag = Color(0xF9, 0x26, 0x72),
        markupTagName = Color(0xF9, 0x26, 0x72),
        markupAttribute = Color(0xA6, 0xE2, 0x2E),
        error = Color(0xF9, 0x26, 0x72),

        // Other
        hyperlink = Color(0x66, 0xD9, 0xEF),
        markOccurrences = Color(0x49, 0x48, 0x3E)
    )

    private fun solarizedDarkThemeColors() = ThemeColors(
        // Editor colors - Solarized Dark base
        background = Color(0x00, 0x2B, 0x36),
        text = Color(0x83, 0x94, 0x96),
        caret = Color(0x83, 0x94, 0x96),
        selectionBackground = Color(0x07, 0x36, 0x42),
        selectionForeground = null,
        currentLineHighlight = Color(0x07, 0x36, 0x42),
        marginLine = Color(0x07, 0x36, 0x42),

        // Bracket matching
        matchedBracketBackground = Color(0x07, 0x36, 0x42),
        matchedBracketForeground = Color(0xB5, 0x89, 0x00),

        // Gutter
        gutterBackground = Color(0x07, 0x36, 0x42),
        gutterBorder = Color(0x07, 0x36, 0x42),
        lineNumber = Color(0x58, 0x6E, 0x75),
        foldIndicator = Color(0x58, 0x6E, 0x75),
        foldBackground = Color(0x07, 0x36, 0x42),

        // Syntax colors - Solarized palette
        keyword = Color(0x85, 0x99, 0x00),      // Green
        dataType = Color(0xB5, 0x89, 0x00),     // Yellow
        function = Color(0x26, 0x8B, 0xD2),     // Blue
        comment = Color(0x58, 0x6E, 0x75),      // Base01
        docComment = Color(0x85, 0x99, 0x00),   // Green for doc comments
        commentKeyword = Color(0x58, 0x6E, 0x75),
        commentMarkup = Color(0x58, 0x6E, 0x75),
        string = Color(0x2A, 0xA1, 0x98),       // Cyan
        number = Color(0xD3, 0x36, 0x82),       // Magenta
        boolean = Color(0xCB, 0x4B, 0x16),      // Orange
        operator = Color(0x83, 0x94, 0x96),
        separator = Color(0x83, 0x94, 0x96),
        preprocessor = Color(0xCB, 0x4B, 0x16),
        annotation = Color(0x26, 0x8B, 0xD2),
        variable = Color(0x26, 0x8B, 0xD2),
        regex = Color(0xDC, 0x32, 0x2F),        // Red
        markupTag = Color(0x26, 0x8B, 0xD2),
        markupTagName = Color(0x26, 0x8B, 0xD2),
        markupAttribute = Color(0x93, 0xA1, 0xA1),
        error = Color(0xDC, 0x32, 0x2F),

        // Other
        hyperlink = Color(0x26, 0x8B, 0xD2),
        markOccurrences = Color(0x07, 0x36, 0x42)
    )

    private fun solarizedLightThemeColors() = ThemeColors(
        // Editor colors - Solarized Light base
        background = Color(0xFD, 0xF6, 0xE3),
        text = Color(0x65, 0x7B, 0x83),
        caret = Color(0x65, 0x7B, 0x83),
        selectionBackground = Color(0xEE, 0xE8, 0xD5),
        selectionForeground = null,
        currentLineHighlight = Color(0xEE, 0xE8, 0xD5),
        marginLine = Color(0xEE, 0xE8, 0xD5),

        // Bracket matching
        matchedBracketBackground = Color(0xEE, 0xE8, 0xD5),
        matchedBracketForeground = Color(0xB5, 0x89, 0x00),

        // Gutter
        gutterBackground = Color(0xEE, 0xE8, 0xD5),
        gutterBorder = Color(0xEE, 0xE8, 0xD5),
        lineNumber = Color(0x93, 0xA1, 0xA1),
        foldIndicator = Color(0x93, 0xA1, 0xA1),
        foldBackground = Color(0xEE, 0xE8, 0xD5),

        // Syntax colors - Solarized palette (same as dark)
        keyword = Color(0x85, 0x99, 0x00),      // Green
        dataType = Color(0xB5, 0x89, 0x00),     // Yellow
        function = Color(0x26, 0x8B, 0xD2),     // Blue
        comment = Color(0x93, 0xA1, 0xA1),      // Base1
        docComment = Color(0x85, 0x99, 0x00),   // Green for doc comments
        commentKeyword = Color(0x93, 0xA1, 0xA1),
        commentMarkup = Color(0x93, 0xA1, 0xA1),
        string = Color(0x2A, 0xA1, 0x98),       // Cyan
        number = Color(0xD3, 0x36, 0x82),       // Magenta
        boolean = Color(0xCB, 0x4B, 0x16),      // Orange
        operator = Color(0x65, 0x7B, 0x83),
        separator = Color(0x65, 0x7B, 0x83),
        preprocessor = Color(0xCB, 0x4B, 0x16),
        annotation = Color(0x26, 0x8B, 0xD2),
        variable = Color(0x26, 0x8B, 0xD2),
        regex = Color(0xDC, 0x32, 0x2F),        // Red
        markupTag = Color(0x26, 0x8B, 0xD2),
        markupTagName = Color(0x26, 0x8B, 0xD2),
        markupAttribute = Color(0x58, 0x6E, 0x75),
        error = Color(0xDC, 0x32, 0x2F),

        // Other
        hyperlink = Color(0x26, 0x8B, 0xD2),
        markOccurrences = Color(0xEE, 0xE8, 0xD5)
    )

    /**
     * Data class holding all theme colors.
     */
    data class ThemeColors(
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
        val foldIndicator: Color,
        val foldBackground: Color,

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
        val regex: Color,
        val markupTag: Color,
        val markupTagName: Color,
        val markupAttribute: Color,
        val error: Color,

        // Other
        val hyperlink: Color,
        val markOccurrences: Color
    )
}
