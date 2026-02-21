package ai.rever.bosseditor.rendering

import ai.rever.bosseditor.core.EditorDocument
import ai.rever.bosseditor.core.EditorPosition
import ai.rever.bosseditor.core.EditorRange
import ai.rever.bosseditor.core.OffsetRange
import ai.rever.bosseditor.features.BracketMatch
import ai.rever.bosseditor.features.Diagnostic
import ai.rever.bosseditor.features.GutterIcon
import ai.rever.bosseditor.features.Hyperlink
import ai.rever.bosseditor.features.IndentGuide
import ai.rever.bosseditor.features.InlayHint
import ai.rever.bosseditor.features.QuickFixLine
import ai.rever.bosseditor.features.RainbowBracket
import ai.rever.bosseditor.features.SpellingError
import ai.rever.bosseditor.fold.FoldRegion
import ai.rever.bosseditor.fold.VisualLineMapper
import ai.rever.bosseditor.highlight.Token
import ai.rever.bosseditor.highlight.TokenType
import ai.rever.bosseditor.model.Caret
import ai.rever.bosseditor.theme.EditorColors
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.FontFamily

/**
 * Holds all the state needed for editor rendering.
 * This is a snapshot of the editor state, making rendering lock-free.
 *
 * The context is created at the start of each frame and passed to the renderer.
 * This pattern (from BossTerm) allows:
 * - Lock-free rendering (no synchronization needed during draw)
 * - Consistent state within a frame (no mid-frame changes)
 * - Easy parameter passing (single object vs many parameters)
 */
data class EditorRenderingContext(
    // Document snapshot
    val documentVersion: Long,
    val lineCount: Int,
    val getLineText: (Int) -> String,
    val getLineLength: (Int) -> Int,
    val getLineStartOffset: (Int) -> Int,

    // Dimensions
    val charWidth: Float,
    val lineHeight: Float,
    val fontSize: Float,
    val baselineOffset: Float,

    // Visible area
    val visibleLineRange: IntRange,
    val viewportWidth: Float,
    val viewportHeight: Float,
    val scrollOffsetX: Float,
    val scrollOffsetY: Float,

    // Font and text
    val textMeasurer: TextMeasurer,
    val fontFamily: FontFamily,

    // Colors/Theme
    val colors: EditorColors,

    // Caret state
    val caretPosition: EditorPosition,
    val caretVisible: Boolean,
    val caretBlinkVisible: Boolean,
    val isFocused: Boolean,

    // Selection state
    val selection: EditorRange?,

    // Current line highlight
    val highlightCurrentLine: Boolean,

    // Search state (for search highlighting)
    val searchQuery: String?,
    val searchMatches: List<EditorRange>,
    val currentSearchMatchIndex: Int,

    // Line numbers
    val showLineNumbers: Boolean,
    val gutterWidth: Float,
    val gutterIconStripWidth: Float = 0f,

    // Folding state
    val visualLineMapper: VisualLineMapper,
    val allFoldRegions: List<FoldRegion>,
    val foldingEnabled: Boolean,

    // Token highlighting (will be populated from lexer in Phase 4)
    val getLineTokens: (Int) -> List<EditorToken>,

    // Bracket matching (Phase 11)
    val bracketMatch: BracketMatch?,

    // Mark occurrences (Phase 11) - list of offset ranges in document
    val markOccurrences: List<OffsetRange>,

    // Multi-caret support (Phase 15)
    val allCarets: List<Caret>,
    val hasMultipleCarets: Boolean,

    // Rainbow brackets (Phase 16) - brackets with nesting depth for colorization
    val rainbowBrackets: List<RainbowBracket>,
    val rainbowBracketsEnabled: Boolean,

    // Diagnostics (Phase 17) - errors, warnings, info, hints with squiggly underlines
    val diagnostics: List<Diagnostic>,

    // Hyperlinks (Phase 17) - clickable links in code
    val hyperlinks: List<Hyperlink>,
    val hyperlinkUnderlineVisible: Boolean, // True when Cmd/Ctrl is held

    // Gutter icons (Phase 17) - icons in the gutter (run, debug, breakpoints, etc.)
    val gutterIcons: List<GutterIcon>,

    // Inlay hints (Phase 18) - inline hints for types, parameter names, etc.
    val inlayHints: List<InlayHint>,
    val inlayHintsEnabled: Boolean,

    // Indent guides (Phase 19) - vertical lines showing indentation levels
    val indentGuides: List<IndentGuide>,
    val activeIndentGuide: IndentGuide?,
    val indentGuidesEnabled: Boolean,
    val tabSize: Int,

    // Spelling errors (Phase 19) - misspelled words in comments/strings
    val spellingErrors: List<SpellingError>,
    val spellCheckEnabled: Boolean,

    // Quick fixes (Phase 19) - lightbulb actions for fixing issues
    val quickFixLines: List<QuickFixLine>,

    // Helper for offset to position conversion (cached from document)
    val offsetToPosition: (Int) -> EditorPosition
) {
    /**
     * Lazy-computed index of spelling errors by line for efficient rendering.
     * Only errors for visible lines need to be rendered, so this avoids O(n) iteration.
     */
    val spellingErrorsByLine: Map<Int, List<SpellingError>> by lazy {
        spellingErrors.groupBy { it.line }
    }

    companion object {
        /**
         * Creates a rendering context from an EditorState.
         */
        fun from(
            document: EditorDocument,
            caretPosition: EditorPosition,
            selection: EditorRange?,
            charWidth: Float,
            lineHeight: Float,
            fontSize: Float,
            baselineOffset: Float,
            viewportWidth: Float,
            viewportHeight: Float,
            scrollOffsetX: Float,
            scrollOffsetY: Float,
            textMeasurer: TextMeasurer,
            fontFamily: FontFamily,
            colors: EditorColors,
            caretVisible: Boolean = true,
            caretBlinkVisible: Boolean = true,
            isFocused: Boolean = true,
            highlightCurrentLine: Boolean = true,
            searchQuery: String? = null,
            searchMatches: List<EditorRange> = emptyList(),
            currentSearchMatchIndex: Int = -1,
            showLineNumbers: Boolean = true,
            gutterWidth: Float = 30f,
            gutterIconStripWidth: Float = 0f,
            visualLineMapper: VisualLineMapper? = null,
            allFoldRegions: List<FoldRegion> = emptyList(),
            foldingEnabled: Boolean = true,
            getLineTokens: (Int) -> List<EditorToken> = { emptyList() },
            bracketMatch: BracketMatch? = null,
            markOccurrences: List<OffsetRange> = emptyList(),
            allCarets: List<Caret> = emptyList(),
            rainbowBrackets: List<RainbowBracket> = emptyList(),
            rainbowBracketsEnabled: Boolean = true,
            diagnostics: List<Diagnostic> = emptyList(),
            hyperlinks: List<Hyperlink> = emptyList(),
            hyperlinkUnderlineVisible: Boolean = false,
            gutterIcons: List<GutterIcon> = emptyList(),
            inlayHints: List<InlayHint> = emptyList(),
            inlayHintsEnabled: Boolean = true,
            indentGuides: List<IndentGuide> = emptyList(),
            activeIndentGuide: IndentGuide? = null,
            indentGuidesEnabled: Boolean = true,
            tabSize: Int = 4,
            spellingErrors: List<SpellingError> = emptyList(),
            spellCheckEnabled: Boolean = true,
            quickFixLines: List<QuickFixLine> = emptyList(),
            offsetToPosition: ((Int) -> EditorPosition)? = null
        ): EditorRenderingContext {
            // Create the visual line mapper (needed for visible line calculation)
            val mapper = visualLineMapper ?: VisualLineMapper.noFolds(document.lineCount)

            // Calculate visible lines in terms of VISUAL lines (accounting for folds)
            val firstVisibleVisualLine = (scrollOffsetY / lineHeight).toInt().coerceAtLeast(0)
            val visibleLineCount = (viewportHeight / lineHeight).toInt() + 2 // +2 for partial lines
            // Ensure lastVisibleVisualLine is at least 0 (for empty documents) so caret can still be drawn
            val lastVisibleVisualLine = (firstVisibleVisualLine + visibleLineCount)
                .coerceAtMost(maxOf(mapper.visibleLineCount - 1, 0))

            return EditorRenderingContext(
                documentVersion = document.documentVersion,
                lineCount = document.lineCount,
                getLineText = { line -> document.getLineText(line) },
                getLineLength = { line -> document.getLineLength(line) },
                getLineStartOffset = { line -> document.getLineStartOffset(line) },
                charWidth = charWidth,
                lineHeight = lineHeight,
                fontSize = fontSize,
                baselineOffset = baselineOffset,
                visibleLineRange = firstVisibleVisualLine..lastVisibleVisualLine,
                viewportWidth = viewportWidth,
                viewportHeight = viewportHeight,
                scrollOffsetX = scrollOffsetX,
                scrollOffsetY = scrollOffsetY,
                textMeasurer = textMeasurer,
                fontFamily = fontFamily,
                colors = colors,
                caretPosition = caretPosition,
                caretVisible = caretVisible,
                caretBlinkVisible = caretBlinkVisible,
                isFocused = isFocused,
                selection = selection,
                highlightCurrentLine = highlightCurrentLine,
                searchQuery = searchQuery,
                searchMatches = searchMatches,
                currentSearchMatchIndex = currentSearchMatchIndex,
                showLineNumbers = showLineNumbers,
                gutterWidth = gutterWidth,
                gutterIconStripWidth = gutterIconStripWidth,
                visualLineMapper = mapper,
                allFoldRegions = allFoldRegions,
                foldingEnabled = foldingEnabled,
                getLineTokens = getLineTokens,
                bracketMatch = bracketMatch,
                markOccurrences = markOccurrences,
                allCarets = allCarets,
                hasMultipleCarets = allCarets.size > 1,
                rainbowBrackets = rainbowBrackets,
                rainbowBracketsEnabled = rainbowBracketsEnabled,
                diagnostics = diagnostics,
                hyperlinks = hyperlinks,
                hyperlinkUnderlineVisible = hyperlinkUnderlineVisible,
                gutterIcons = gutterIcons,
                inlayHints = inlayHints,
                inlayHintsEnabled = inlayHintsEnabled,
                indentGuides = indentGuides,
                activeIndentGuide = activeIndentGuide,
                indentGuidesEnabled = indentGuidesEnabled,
                tabSize = tabSize,
                spellingErrors = spellingErrors,
                spellCheckEnabled = spellCheckEnabled,
                quickFixLines = quickFixLines,
                offsetToPosition = offsetToPosition ?: { offset -> document.offsetToPosition(offset) }
            )
        }
    }
}

/**
 * Represents a syntax token for rendering.
 * Uses column positions (0-indexed) within a line.
 */
data class EditorToken(
    val startColumn: Int,
    val endColumn: Int,
    val type: TokenType
) {
    companion object {
        /**
         * Converts a highlight Token (offset-based) to an EditorToken (column-based).
         * Since tokens are line-local, offsets equal columns.
         */
        fun fromToken(token: Token): EditorToken {
            return EditorToken(
                startColumn = token.startOffset,
                endColumn = token.endOffset,
                type = token.type
            )
        }

        /**
         * Converts a list of highlight Tokens to EditorTokens.
         */
        fun fromTokens(tokens: List<Token>): List<EditorToken> {
            return tokens.map { fromToken(it) }
        }
    }
}
