package ai.rever.bosseditor.features

import ai.rever.bosseditor.core.EditorDocument
import ai.rever.bosseditor.core.OffsetRange

/**
 * Manages current line highlighting state.
 *
 * Provides logic for determining which lines should be highlighted
 * based on cursor position and selection state.
 *
 * ## Usage
 * ```kotlin
 * val highlighter = CurrentLineHighlighter(document, config)
 * val linesToHighlight = highlighter.getHighlightedLines(caretOffset, selection)
 * // Render background for each highlighted line
 * ```
 */
class CurrentLineHighlighter(
    private val document: EditorDocument,
    private val config: CurrentLineConfig = CurrentLineConfig()
) {
    /**
     * Gets the lines that should be highlighted based on caret position.
     *
     * @param caretOffset The current caret position
     * @param selection Optional selection range
     * @return Set of line numbers to highlight
     */
    fun getHighlightedLines(
        caretOffset: Int,
        selection: OffsetRange? = null
    ): Set<Int> {
        // Don't highlight if feature is disabled
        if (!config.enabled) {
            return emptySet()
        }

        val caretLine = document.offsetToPosition(caretOffset).line

        // Handle selection behavior
        if (selection != null && !selection.isEmpty) {
            return when (config.selectionBehavior) {
                SelectionBehavior.HIDE -> emptySet()
                SelectionBehavior.SHOW_CARET_LINE -> setOf(caretLine)
                SelectionBehavior.SHOW_ALL_SELECTED -> {
                    val startLine = document.offsetToPosition(selection.start).line
                    val endLine = document.offsetToPosition(selection.end).line
                    (startLine..endLine).toSet()
                }
            }
        }

        // No selection - highlight the line containing the caret
        return setOf(caretLine)
    }

    /**
     * Checks if a specific line should be highlighted.
     *
     * @param lineNumber The line to check
     * @param caretOffset The current caret position
     * @param selection Optional selection range
     * @return True if the line should be highlighted
     */
    fun isLineHighlighted(
        lineNumber: Int,
        caretOffset: Int,
        selection: OffsetRange? = null
    ): Boolean {
        if (!config.enabled) {
            return false
        }

        val caretLine = document.offsetToPosition(caretOffset).line

        // Handle selection behavior
        if (selection != null && !selection.isEmpty) {
            return when (config.selectionBehavior) {
                SelectionBehavior.HIDE -> false
                SelectionBehavior.SHOW_CARET_LINE -> caretLine == lineNumber
                SelectionBehavior.SHOW_ALL_SELECTED -> {
                    val startLine = document.offsetToPosition(selection.start).line
                    val endLine = document.offsetToPosition(selection.end).line
                    lineNumber in startLine..endLine
                }
            }
        }

        return caretLine == lineNumber
    }

    /**
     * Gets highlight info for rendering.
     *
     * @param caretOffset The current caret position
     * @param selection Optional selection range
     * @param firstVisibleLine First visible line in viewport
     * @param visibleLineCount Number of visible lines
     * @return Highlight info for visible lines
     */
    fun getVisibleHighlightInfo(
        caretOffset: Int,
        selection: OffsetRange?,
        firstVisibleLine: Int,
        visibleLineCount: Int
    ): CurrentLineHighlightInfo {
        val highlightedLines = getHighlightedLines(caretOffset, selection)

        // Filter to visible lines
        val visibleHighlighted = highlightedLines.filter { line ->
            line >= firstVisibleLine && line < firstVisibleLine + visibleLineCount
        }.toSet()

        return CurrentLineHighlightInfo(
            highlightedLines = visibleHighlighted,
            caretLine = document.offsetToPosition(caretOffset).line,
            hasSelection = selection != null && !selection.isEmpty
        )
    }
}

/**
 * Configuration for current line highlighting.
 */
data class CurrentLineConfig(
    /**
     * Whether current line highlighting is enabled.
     */
    val enabled: Boolean = true,

    /**
     * How to handle highlighting when there's a selection.
     */
    val selectionBehavior: SelectionBehavior = SelectionBehavior.HIDE,

    /**
     * Whether to highlight the full line width or just the text area.
     */
    val fullLineWidth: Boolean = true,

    /**
     * Whether to show highlight in gutter as well.
     */
    val highlightGutter: Boolean = true
)

/**
 * Behavior for current line highlight when there's a selection.
 */
enum class SelectionBehavior {
    /**
     * Hide the current line highlight when there's a selection.
     */
    HIDE,

    /**
     * Only show highlight on the line containing the caret.
     */
    SHOW_CARET_LINE,

    /**
     * Highlight all lines that are part of the selection.
     */
    SHOW_ALL_SELECTED
}

/**
 * Information about current line highlighting for rendering.
 */
data class CurrentLineHighlightInfo(
    /**
     * Set of line numbers that should be highlighted.
     */
    val highlightedLines: Set<Int>,

    /**
     * The line number containing the caret.
     */
    val caretLine: Int,

    /**
     * Whether there is an active selection.
     */
    val hasSelection: Boolean
) {
    /**
     * Checks if a specific line should be highlighted.
     */
    fun shouldHighlight(lineNumber: Int): Boolean = lineNumber in highlightedLines

    /**
     * Whether any lines are highlighted.
     */
    val hasHighlight: Boolean get() = highlightedLines.isNotEmpty()
}

/**
 * Extension function to get visual line info adjusted for folding.
 *
 * When code folding is active, visual lines may not match document lines.
 */
fun CurrentLineHighlighter.getVisualHighlightInfo(
    caretOffset: Int,
    selection: OffsetRange?,
    firstVisibleLine: Int,
    visibleLineCount: Int,
    documentToVisualLine: (Int) -> Int?
): Set<Int> {
    val highlightedLines = getHighlightedLines(caretOffset, selection)

    // Convert document lines to visual lines
    return highlightedLines.mapNotNull { docLine ->
        documentToVisualLine(docLine)?.takeIf { visualLine ->
            visualLine >= firstVisibleLine && visualLine < firstVisibleLine + visibleLineCount
        }
    }.toSet()
}
