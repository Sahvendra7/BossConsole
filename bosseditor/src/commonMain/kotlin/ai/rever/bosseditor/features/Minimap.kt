package ai.rever.bosseditor.features

import ai.rever.bosseditor.core.EditorDocument
import ai.rever.bosseditor.core.OffsetRange
import ai.rever.bosseditor.highlight.Token
import ai.rever.bosseditor.highlight.TokenType

/**
 * Minimap (document overview) feature for the editor.
 *
 * Provides a scaled-down view of the entire document that:
 * - Shows syntax highlighting colors
 * - Indicates the current viewport position
 * - Allows click-to-scroll navigation
 * - Highlights search results and selections
 *
 * ## Usage
 * ```kotlin
 * val minimap = Minimap(document)
 * minimap.config = MinimapConfig(scale = 0.1f, maxWidth = 120f)
 *
 * // Get lines to render
 * val lines = minimap.getMinimapLines()
 *
 * // Handle click
 * val targetLine = minimap.getLineFromY(clickY)
 * ```
 */
class Minimap(
    private val document: EditorDocument
) {
    /**
     * Minimap configuration.
     */
    var config: MinimapConfig = MinimapConfig()

    /**
     * Gets all lines for minimap rendering with their tokens.
     * Each line is represented as a simplified color block.
     */
    fun getMinimapLines(): List<MinimapLine> {
        val lines = mutableListOf<MinimapLine>()
        val lineCount = document.lineCount

        for (lineNumber in 0 until lineCount) {
            val lineStart = document.getLineStartOffset(lineNumber)
            val lineEnd = document.getLineEndOffset(lineNumber)
            val lineLength = lineEnd - lineStart

            // Skip the newline character in length calculation
            val contentLength = if (lineNumber < lineCount - 1) {
                maxOf(0, lineLength - 1)
            } else {
                lineLength
            }

            lines.add(MinimapLine(
                lineNumber = lineNumber,
                length = contentLength,
                isEmpty = contentLength == 0
            ))
        }

        return lines
    }

    /**
     * Gets minimap lines with syntax highlighting information.
     *
     * @param getLineTokens Function to get tokens for a line
     * @return List of minimap lines with color segments
     */
    fun getMinimapLinesWithTokens(
        getLineTokens: (Int) -> List<Token>
    ): List<MinimapLineWithTokens> {
        val lines = mutableListOf<MinimapLineWithTokens>()
        val lineCount = document.lineCount

        for (lineNumber in 0 until lineCount) {
            val lineStart = document.getLineStartOffset(lineNumber)
            val tokens = getLineTokens(lineNumber)

            val segments = tokens.map { token ->
                MinimapSegment(
                    startColumn = token.startOffset - lineStart,
                    endColumn = token.endOffset - lineStart,
                    tokenType = token.type
                )
            }

            lines.add(MinimapLineWithTokens(
                lineNumber = lineNumber,
                segments = segments
            ))
        }

        return lines
    }

    /**
     * Converts a Y coordinate in the minimap to a document line number.
     *
     * @param y The Y coordinate in minimap space
     * @param minimapHeight Total height of the minimap
     * @return The corresponding line number
     */
    fun getLineFromY(y: Float, minimapHeight: Float): Int {
        val lineCount = document.lineCount
        if (lineCount == 0) return 0

        val lineHeight = minimapHeight / lineCount
        val lineNumber = (y / lineHeight).toInt()

        return lineNumber.coerceIn(0, lineCount - 1)
    }

    /**
     * Calculates the viewport indicator bounds in minimap coordinates.
     *
     * @param firstVisibleLine First visible line in the editor
     * @param visibleLineCount Number of visible lines
     * @param minimapHeight Total minimap height
     * @return The viewport rectangle (y, height)
     */
    fun getViewportBounds(
        firstVisibleLine: Int,
        visibleLineCount: Int,
        minimapHeight: Float
    ): ViewportBounds {
        val lineCount = document.lineCount
        if (lineCount == 0) return ViewportBounds(0f, minimapHeight)

        val lineHeight = minimapHeight / lineCount
        val y = firstVisibleLine * lineHeight
        val height = visibleLineCount * lineHeight

        return ViewportBounds(
            y = y.coerceIn(0f, minimapHeight),
            height = height.coerceAtMost(minimapHeight - y)
        )
    }

    /**
     * Gets highlight ranges for search results in minimap coordinates.
     *
     * @param searchResults List of search result offset ranges
     * @param minimapHeight Total minimap height
     * @return List of Y positions to highlight
     */
    fun getSearchHighlights(
        searchResults: List<OffsetRange>,
        minimapHeight: Float
    ): List<MinimapHighlight> {
        val lineCount = document.lineCount
        if (lineCount == 0) return emptyList()

        val lineHeight = minimapHeight / lineCount
        val highlights = mutableListOf<MinimapHighlight>()

        for (result in searchResults) {
            val position = document.offsetToPosition(result.start)
            val y = position.line * lineHeight

            highlights.add(MinimapHighlight(
                y = y,
                height = lineHeight,
                type = HighlightType.SEARCH
            ))
        }

        return highlights
    }

    /**
     * Gets highlight for selections in minimap coordinates.
     *
     * @param selection The current selection range
     * @param minimapHeight Total minimap height
     * @return List of selection highlights
     */
    fun getSelectionHighlights(
        selection: OffsetRange?,
        minimapHeight: Float
    ): List<MinimapHighlight> {
        if (selection == null || selection.isEmpty) return emptyList()

        val lineCount = document.lineCount
        if (lineCount == 0) return emptyList()

        val lineHeight = minimapHeight / lineCount
        val highlights = mutableListOf<MinimapHighlight>()

        val startPos = document.offsetToPosition(selection.start)
        val endPos = document.offsetToPosition(selection.end)

        for (line in startPos.line..endPos.line) {
            val y = line * lineHeight
            highlights.add(MinimapHighlight(
                y = y,
                height = lineHeight,
                type = HighlightType.SELECTION
            ))
        }

        return highlights
    }

    /**
     * Gets highlight for mark occurrences in minimap coordinates.
     *
     * @param occurrences List of occurrence ranges
     * @param minimapHeight Total minimap height
     * @return List of occurrence highlights
     */
    fun getOccurrenceHighlights(
        occurrences: List<OffsetRange>,
        minimapHeight: Float
    ): List<MinimapHighlight> {
        val lineCount = document.lineCount
        if (lineCount == 0) return emptyList()

        val lineHeight = minimapHeight / lineCount
        val highlights = mutableListOf<MinimapHighlight>()

        for (occurrence in occurrences) {
            val position = document.offsetToPosition(occurrence.start)
            val y = position.line * lineHeight

            highlights.add(MinimapHighlight(
                y = y,
                height = lineHeight,
                type = HighlightType.OCCURRENCE
            ))
        }

        return highlights
    }

    /**
     * Gets error/warning markers for minimap.
     *
     * @param diagnostics List of diagnostic ranges with severity
     * @param minimapHeight Total minimap height
     * @return List of diagnostic markers
     */
    fun getDiagnosticMarkers(
        diagnostics: List<DiagnosticInfo>,
        minimapHeight: Float
    ): List<MinimapMarker> {
        val lineCount = document.lineCount
        if (lineCount == 0) return emptyList()

        val lineHeight = minimapHeight / lineCount
        val markers = mutableListOf<MinimapMarker>()

        for (diagnostic in diagnostics) {
            val position = document.offsetToPosition(diagnostic.range.start)
            val y = position.line * lineHeight

            markers.add(MinimapMarker(
                y = y,
                severity = diagnostic.severity
            ))
        }

        return markers
    }

    /**
     * Calculates the optimal minimap width based on document content.
     *
     * @param maxWidth Maximum allowed width
     * @return Calculated width
     */
    fun calculateWidth(maxWidth: Float): Float {
        if (document.lineCount == 0) return config.minWidth

        var maxLineLength = 0
        for (line in 0 until minOf(document.lineCount, 1000)) {
            val lineStart = document.getLineStartOffset(line)
            val lineEnd = document.getLineEndOffset(line)
            maxLineLength = maxOf(maxLineLength, lineEnd - lineStart)
        }

        val calculatedWidth = maxLineLength * config.charWidth
        return calculatedWidth.coerceIn(config.minWidth, maxWidth)
    }
}

/**
 * Configuration for minimap rendering.
 */
data class MinimapConfig(
    /**
     * Scale factor for the minimap (0.0 to 1.0).
     */
    val scale: Float = 0.1f,

    /**
     * Width of a single character in minimap (in pixels).
     */
    val charWidth: Float = 1.5f,

    /**
     * Height of a single line in minimap (in pixels).
     */
    val lineHeight: Float = 2f,

    /**
     * Minimum minimap width.
     */
    val minWidth: Float = 50f,

    /**
     * Maximum minimap width.
     */
    val maxWidth: Float = 120f,

    /**
     * Whether to show the minimap slider/viewport.
     */
    val showSlider: Boolean = true,

    /**
     * Whether to render actual characters or just blocks.
     */
    val renderCharacters: Boolean = false,

    /**
     * Whether to show search highlights.
     */
    val showSearchHighlights: Boolean = true,

    /**
     * Whether to show selection in minimap.
     */
    val showSelection: Boolean = true,

    /**
     * Whether minimap is enabled.
     */
    val enabled: Boolean = true
)

/**
 * A simplified line representation for minimap.
 */
data class MinimapLine(
    val lineNumber: Int,
    val length: Int,
    val isEmpty: Boolean
)

/**
 * A line with syntax highlighting segments for minimap.
 */
data class MinimapLineWithTokens(
    val lineNumber: Int,
    val segments: List<MinimapSegment>
)

/**
 * A colored segment within a minimap line.
 */
data class MinimapSegment(
    val startColumn: Int,
    val endColumn: Int,
    val tokenType: TokenType
)

/**
 * Viewport indicator bounds.
 */
data class ViewportBounds(
    val y: Float,
    val height: Float
)

/**
 * A highlight region in the minimap.
 */
data class MinimapHighlight(
    val y: Float,
    val height: Float,
    val type: HighlightType
)

/**
 * Type of minimap highlight.
 */
enum class HighlightType {
    SEARCH,
    SELECTION,
    OCCURRENCE,
    MODIFIED,
    ADDED,
    DELETED
}

/**
 * A marker on the minimap (for errors/warnings).
 */
data class MinimapMarker(
    val y: Float,
    val severity: DiagnosticSeverity
)

/**
 * Diagnostic information for minimap markers.
 */
data class DiagnosticInfo(
    val range: OffsetRange,
    val severity: DiagnosticSeverity,
    val message: String = ""
)

/**
 * Severity level for diagnostics.
 */
enum class DiagnosticSeverity {
    ERROR,
    WARNING,
    INFO,
    HINT
}
