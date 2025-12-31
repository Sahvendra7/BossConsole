package ai.rever.bosseditor.features

import ai.rever.bosseditor.core.EditorDocument
import ai.rever.bosseditor.highlight.Token
import ai.rever.bosseditor.highlight.TokenProvider
import ai.rever.bosseditor.highlight.TokenType
import ai.rever.bosseditor.highlight.LexerState
import ai.rever.bosseditor.theme.EditorColors
import org.jetbrains.skia.*

/**
 * Renders the minimap (document overview) for the editor.
 *
 * The minimap provides a scaled-down view of the entire document,
 * showing syntax highlighting colors and the current viewport position.
 */
class MinimapRenderer(
    private val document: EditorDocument,
    private val tokenProvider: TokenProvider?,
    private val colors: EditorColors
) {
    private val minimap = Minimap(document)

    /**
     * Current minimap configuration.
     */
    var config: MinimapConfig
        get() = minimap.config
        set(value) {
            minimap.config = value
        }

    /**
     * Renders the minimap to the canvas.
     *
     * @param canvas The Skia canvas to render to
     * @param x The X position of the minimap
     * @param y The Y position of the minimap
     * @param width The width of the minimap
     * @param height The height of the minimap
     * @param state The current minimap state
     */
    fun render(
        canvas: Canvas,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        state: MinimapState
    ) {
        if (!config.enabled || document.lineCount == 0) return

        // Draw background
        drawBackground(canvas, x, y, width, height)

        // Calculate line height based on document size and available height
        val lineCount = document.lineCount
        val lineHeight = calculateLineHeight(height, lineCount)

        // Draw document content
        if (tokenProvider != null && config.renderCharacters) {
            drawWithSyntaxHighlighting(canvas, x, y, width, lineHeight, state)
        } else {
            drawSimplified(canvas, x, y, width, lineHeight, state)
        }

        // Draw viewport indicator
        if (config.showSlider) {
            drawViewportIndicator(canvas, x, y, width, height, state)
        }

        // Draw highlights (search, selection, occurrences)
        drawHighlights(canvas, x, y, width, height, state)

        // Draw diagnostic markers
        drawDiagnosticMarkers(canvas, x, width, height, state)
    }

    private fun drawBackground(canvas: Canvas, x: Float, y: Float, width: Float, height: Float) {
        val paint = Paint().apply {
            color = colors.minimapBackground.toSkiaColor()
            mode = PaintMode.FILL
        }
        canvas.drawRect(Rect.makeXYWH(x, y, width, height), paint)
    }

    private fun calculateLineHeight(availableHeight: Float, lineCount: Int): Float {
        val naturalHeight = lineCount * config.lineHeight
        return if (naturalHeight > availableHeight) {
            availableHeight / lineCount
        } else {
            config.lineHeight
        }
    }

    private fun drawSimplified(
        canvas: Canvas,
        x: Float,
        y: Float,
        width: Float,
        lineHeight: Float,
        state: MinimapState
    ) {
        val paint = Paint().apply {
            mode = PaintMode.FILL
        }

        val lines = minimap.getMinimapLines()
        val maxChars = (width / config.charWidth).toInt()

        for (line in lines) {
            if (line.isEmpty) continue

            val lineY = y + line.lineNumber * lineHeight
            val displayLength = minOf(line.length, maxChars)
            val lineWidth = displayLength * config.charWidth

            paint.color = colors.minimapForeground.toSkiaColor()
            canvas.drawRect(
                Rect.makeXYWH(x + 2f, lineY, lineWidth, maxOf(lineHeight - 1f, 1f)),
                paint
            )
        }
    }

    private fun drawWithSyntaxHighlighting(
        canvas: Canvas,
        x: Float,
        y: Float,
        width: Float,
        lineHeight: Float,
        state: MinimapState
    ) {
        val paint = Paint().apply {
            mode = PaintMode.FILL
        }

        // Track lexer state across lines
        var lexerState = LexerState.NORMAL

        val lines = minimap.getMinimapLinesWithTokens { lineNumber ->
            if (tokenProvider == null) return@getMinimapLinesWithTokens emptyList()

            val lineStart = document.getLineStartOffset(lineNumber)
            val lineEnd = document.getLineEndOffset(lineNumber)
            val lineText = document.getText(lineStart, lineEnd)
                .trimEnd('\n', '\r')

            val result = tokenProvider.tokenizeLine(lineText, lineNumber, lexerState)
            lexerState = result.endState

            result.tokens
        }

        val maxChars = (width / config.charWidth).toInt()

        for (line in lines) {
            val lineY = y + line.lineNumber * lineHeight

            for (segment in line.segments) {
                if (segment.startColumn >= maxChars) continue

                val startX = x + 2f + segment.startColumn * config.charWidth
                val endColumn = minOf(segment.endColumn, maxChars)
                val segmentWidth = (endColumn - segment.startColumn) * config.charWidth

                if (segmentWidth <= 0) continue

                paint.color = getTokenColor(segment.tokenType).toSkiaColor()
                canvas.drawRect(
                    Rect.makeXYWH(startX, lineY, segmentWidth, maxOf(lineHeight - 1f, 1f)),
                    paint
                )
            }
        }
    }

    private fun drawViewportIndicator(
        canvas: Canvas,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        state: MinimapState
    ) {
        val bounds = minimap.getViewportBounds(
            state.firstVisibleLine,
            state.visibleLineCount,
            height
        )

        // Draw viewport background
        val bgPaint = Paint().apply {
            color = colors.minimapViewport.toSkiaColor()
            mode = PaintMode.FILL
        }
        canvas.drawRect(
            Rect.makeXYWH(x, y + bounds.y, width, bounds.height),
            bgPaint
        )

        // Draw viewport border
        val borderPaint = Paint().apply {
            color = colors.minimapViewportBorder.toSkiaColor()
            mode = PaintMode.STROKE
            strokeWidth = 1f
        }
        canvas.drawRect(
            Rect.makeXYWH(x, y + bounds.y, width, bounds.height),
            borderPaint
        )
    }

    private fun drawHighlights(
        canvas: Canvas,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        state: MinimapState
    ) {
        val paint = Paint().apply {
            mode = PaintMode.FILL
        }

        // Draw search highlights
        if (config.showSearchHighlights && state.searchResults.isNotEmpty()) {
            val searchHighlights = minimap.getSearchHighlights(state.searchResults, height)
            paint.color = colors.minimapSearchHighlight.toSkiaColor()
            for (highlight in searchHighlights) {
                canvas.drawRect(
                    Rect.makeXYWH(x + width - 4f, y + highlight.y, 3f, maxOf(highlight.height, 2f)),
                    paint
                )
            }
        }

        // Draw selection highlights
        if (config.showSelection && state.selection != null) {
            val selectionHighlights = minimap.getSelectionHighlights(state.selection, height)
            paint.color = colors.minimapSelection.toSkiaColor()
            for (highlight in selectionHighlights) {
                canvas.drawRect(
                    Rect.makeXYWH(x, y + highlight.y, width, maxOf(highlight.height, 2f)),
                    paint
                )
            }
        }

        // Draw occurrence highlights
        if (state.occurrences.isNotEmpty()) {
            val occurrenceHighlights = minimap.getOccurrenceHighlights(state.occurrences, height)
            paint.color = colors.minimapOccurrence.toSkiaColor()
            for (highlight in occurrenceHighlights) {
                canvas.drawRect(
                    Rect.makeXYWH(x + width - 4f, y + highlight.y, 3f, maxOf(highlight.height, 2f)),
                    paint
                )
            }
        }
    }

    private fun drawDiagnosticMarkers(
        canvas: Canvas,
        x: Float,
        width: Float,
        height: Float,
        state: MinimapState
    ) {
        if (state.diagnostics.isEmpty()) return

        val markers = minimap.getDiagnosticMarkers(state.diagnostics, height)
        val paint = Paint().apply {
            mode = PaintMode.FILL
        }

        for (marker in markers) {
            paint.color = when (marker.severity) {
                DiagnosticSeverity.ERROR -> colors.minimapError.toSkiaColor()
                DiagnosticSeverity.WARNING -> colors.minimapWarning.toSkiaColor()
                DiagnosticSeverity.INFO -> colors.minimapInfo.toSkiaColor()
                DiagnosticSeverity.HINT -> colors.minimapHint.toSkiaColor()
            }
            canvas.drawRect(
                Rect.makeXYWH(x + width - 4f, marker.y, 3f, 2f),
                paint
            )
        }
    }

    private fun getTokenColor(tokenType: TokenType): androidx.compose.ui.graphics.Color {
        // Use the theme's token color mapping
        return colors.getTokenColor(tokenType)
    }

    /**
     * Handles click on the minimap.
     *
     * @param clickY The Y coordinate of the click relative to minimap top
     * @param minimapHeight The total height of the minimap
     * @return The line number to scroll to
     */
    fun getLineFromClick(clickY: Float, minimapHeight: Float): Int {
        return minimap.getLineFromY(clickY, minimapHeight)
    }

    /**
     * Calculates the optimal width for the minimap.
     */
    fun calculateOptimalWidth(): Float {
        return minimap.calculateWidth(config.maxWidth)
    }
}

/**
 * State for minimap rendering.
 */
data class MinimapState(
    /**
     * First visible line in the editor viewport.
     */
    val firstVisibleLine: Int = 0,

    /**
     * Number of visible lines in the editor viewport.
     */
    val visibleLineCount: Int = 30,

    /**
     * Current selection range (if any).
     */
    val selection: ai.rever.bosseditor.core.OffsetRange? = null,

    /**
     * Search result ranges.
     */
    val searchResults: List<ai.rever.bosseditor.core.OffsetRange> = emptyList(),

    /**
     * Mark occurrences ranges.
     */
    val occurrences: List<ai.rever.bosseditor.core.OffsetRange> = emptyList(),

    /**
     * Diagnostic information (errors, warnings).
     */
    val diagnostics: List<DiagnosticInfo> = emptyList(),

    /**
     * Whether the minimap is being hovered.
     */
    val isHovered: Boolean = false,

    /**
     * Whether the viewport indicator is being dragged.
     */
    val isDragging: Boolean = false
)

/**
 * Extension function to convert Compose Color to Skia Color.
 */
private fun androidx.compose.ui.graphics.Color.toSkiaColor(): Int {
    return (alpha * 255).toInt() shl 24 or
            (red * 255).toInt() shl 16 or
            (green * 255).toInt() shl 8 or
            (blue * 255).toInt()
}
