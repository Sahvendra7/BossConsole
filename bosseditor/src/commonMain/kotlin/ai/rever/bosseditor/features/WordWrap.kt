package ai.rever.bosseditor.features

import ai.rever.bosseditor.core.EditorDocument

/**
 * Word wrap support for the editor.
 *
 * Handles soft wrapping of long lines to fit within the viewport width.
 * This affects visual display only - the document content remains unchanged.
 *
 * ## Usage
 * ```kotlin
 * val wordWrap = WordWrap(document, config)
 * wordWrap.setViewportWidth(800f, charWidth = 8f)
 * val wrappedLines = wordWrap.getWrappedLines(lineNumber)
 * ```
 */
class WordWrap(
    private val document: EditorDocument,
    private val config: WordWrapConfig = WordWrapConfig()
) {
    /**
     * Maximum characters per visual line based on viewport.
     */
    private var maxCharsPerLine: Int = Int.MAX_VALUE

    /**
     * Cached wrap points for each document line.
     * Maps document line -> list of character offsets where wraps occur.
     */
    private val wrapCache = mutableMapOf<Int, List<Int>>()

    /**
     * Version tracker for cache invalidation.
     */
    private var cacheVersion = 0

    /**
     * Sets the viewport width for calculating wrap points.
     *
     * @param viewportWidth The available width in pixels
     * @param charWidth The width of a single character
     * @param gutterWidth Width of the gutter area to exclude
     */
    fun setViewportWidth(viewportWidth: Float, charWidth: Float, gutterWidth: Float = 0f) {
        val availableWidth = viewportWidth - gutterWidth - config.rightMargin
        val newMaxChars = (availableWidth / charWidth).toInt().coerceAtLeast(config.minLineLength)

        if (newMaxChars != maxCharsPerLine) {
            maxCharsPerLine = newMaxChars
            invalidateCache()
        }
    }

    /**
     * Sets a fixed character limit per line.
     *
     * @param maxChars Maximum characters per visual line
     */
    fun setFixedWidth(maxChars: Int) {
        val newMaxChars = maxChars.coerceAtLeast(config.minLineLength)
        if (newMaxChars != maxCharsPerLine) {
            maxCharsPerLine = newMaxChars
            invalidateCache()
        }
    }

    /**
     * Invalidates the wrap cache, forcing recalculation.
     */
    fun invalidateCache() {
        wrapCache.clear()
        cacheVersion++
    }

    /**
     * Invalidates cache for specific lines.
     *
     * @param startLine First line to invalidate
     * @param endLine Last line to invalidate (inclusive)
     */
    fun invalidateLines(startLine: Int, endLine: Int) {
        for (line in startLine..endLine) {
            wrapCache.remove(line)
        }
        cacheVersion++
    }

    /**
     * Gets the wrap points for a document line.
     *
     * @param lineNumber The document line number
     * @return List of character offsets where the line should wrap
     */
    fun getWrapPoints(lineNumber: Int): List<Int> {
        if (!config.enabled || maxCharsPerLine == Int.MAX_VALUE) {
            return emptyList()
        }

        return wrapCache.getOrPut(lineNumber) {
            calculateWrapPoints(lineNumber)
        }
    }

    /**
     * Gets the wrapped visual lines for a document line.
     *
     * @param lineNumber The document line number
     * @return List of substrings representing each visual line
     */
    fun getWrappedLines(lineNumber: Int): List<WrappedLine> {
        val lineStart = document.getLineStartOffset(lineNumber)
        val lineEnd = document.getLineEndOffset(lineNumber)
        val lineText = document.getText(lineStart, lineEnd)

        if (!config.enabled || lineText.length <= maxCharsPerLine) {
            return listOf(WrappedLine(lineText, 0, lineText.length, isFirstSegment = true, isLastSegment = true))
        }

        val wrapPoints = getWrapPoints(lineNumber)
        if (wrapPoints.isEmpty()) {
            return listOf(WrappedLine(lineText, 0, lineText.length, isFirstSegment = true, isLastSegment = true))
        }

        val result = mutableListOf<WrappedLine>()
        var startOffset = 0

        for ((index, wrapPoint) in wrapPoints.withIndex()) {
            result.add(
                WrappedLine(
                    text = lineText.substring(startOffset, wrapPoint),
                    startOffset = startOffset,
                    endOffset = wrapPoint,
                    isFirstSegment = index == 0,
                    isLastSegment = false
                )
            )
            startOffset = wrapPoint
        }

        // Add final segment
        result.add(
            WrappedLine(
                text = lineText.substring(startOffset),
                startOffset = startOffset,
                endOffset = lineText.length,
                isFirstSegment = wrapPoints.isEmpty(),
                isLastSegment = true
            )
        )

        return result
    }

    /**
     * Calculates the number of visual lines for a document line.
     *
     * @param lineNumber The document line number
     * @return Number of visual lines (1 if no wrapping)
     */
    fun getVisualLineCount(lineNumber: Int): Int {
        if (!config.enabled) return 1

        val wrapPoints = getWrapPoints(lineNumber)
        return wrapPoints.size + 1
    }

    /**
     * Gets the total visual line count for the entire document.
     *
     * @return Total number of visual lines
     */
    fun getTotalVisualLineCount(): Int {
        if (!config.enabled) return document.lineCount

        var total = 0
        for (line in 0 until document.lineCount) {
            total += getVisualLineCount(line)
        }
        return total
    }

    /**
     * Converts a visual line number to document line number.
     *
     * @param visualLine The visual line number
     * @return Pair of (document line, segment index within that line)
     */
    fun visualToDocumentLine(visualLine: Int): Pair<Int, Int> {
        if (!config.enabled) return Pair(visualLine, 0)

        var currentVisual = 0
        for (docLine in 0 until document.lineCount) {
            val lineVisualCount = getVisualLineCount(docLine)
            if (currentVisual + lineVisualCount > visualLine) {
                return Pair(docLine, visualLine - currentVisual)
            }
            currentVisual += lineVisualCount
        }

        // Past end of document
        return Pair(document.lineCount - 1, 0)
    }

    /**
     * Converts a document line to its first visual line number.
     *
     * @param documentLine The document line number
     * @return The visual line number of the first segment
     */
    fun documentToVisualLine(documentLine: Int): Int {
        if (!config.enabled) return documentLine

        var visualLine = 0
        for (line in 0 until documentLine.coerceAtMost(document.lineCount)) {
            visualLine += getVisualLineCount(line)
        }
        return visualLine
    }

    /**
     * Calculates wrap points for a line.
     */
    private fun calculateWrapPoints(lineNumber: Int): List<Int> {
        if (lineNumber < 0 || lineNumber >= document.lineCount) {
            return emptyList()
        }

        val lineStart = document.getLineStartOffset(lineNumber)
        val lineEnd = document.getLineEndOffset(lineNumber)
        val lineText = document.getText(lineStart, lineEnd)

        if (lineText.length <= maxCharsPerLine) {
            return emptyList()
        }

        val wrapPoints = mutableListOf<Int>()
        var position = 0

        while (position + maxCharsPerLine < lineText.length) {
            val targetPosition = position + maxCharsPerLine

            // Find best wrap point based on wrap mode
            val wrapPoint = when (config.wrapMode) {
                WrapMode.ANYWHERE -> targetPosition
                WrapMode.WORD_BOUNDARY -> findWordBoundary(lineText, position, targetPosition)
                WrapMode.WORD_BOUNDARY_OR_ANYWHERE -> {
                    val wordBoundary = findWordBoundary(lineText, position, targetPosition)
                    // If word boundary is too far back, wrap at target
                    if (wordBoundary < position + maxCharsPerLine / 2) {
                        targetPosition
                    } else {
                        wordBoundary
                    }
                }
            }

            wrapPoints.add(wrapPoint)
            position = wrapPoint
        }

        return wrapPoints
    }

    /**
     * Finds the best word boundary for wrapping.
     */
    private fun findWordBoundary(text: String, start: Int, target: Int): Int {
        // Look backwards from target for a space or other break character
        for (i in target downTo start + 1) {
            val char = text[i - 1]
            if (char == ' ' || char == '\t' || char == '-') {
                return i
            }
        }
        // No good break point found, wrap at target
        return target
    }
}

/**
 * Represents a wrapped segment of a document line.
 */
data class WrappedLine(
    /**
     * The text content of this visual line segment.
     */
    val text: String,

    /**
     * Start offset within the document line.
     */
    val startOffset: Int,

    /**
     * End offset within the document line.
     */
    val endOffset: Int,

    /**
     * Whether this is the first segment of the document line.
     */
    val isFirstSegment: Boolean,

    /**
     * Whether this is the last segment of the document line.
     */
    val isLastSegment: Boolean
) {
    /**
     * Whether this segment is a continuation (not the first).
     */
    val isContinuation: Boolean get() = !isFirstSegment

    /**
     * Length of this segment in characters.
     */
    val length: Int get() = endOffset - startOffset
}

/**
 * Configuration for word wrapping.
 */
data class WordWrapConfig(
    /**
     * Whether word wrap is enabled.
     */
    val enabled: Boolean = false,

    /**
     * How to determine wrap points.
     */
    val wrapMode: WrapMode = WrapMode.WORD_BOUNDARY_OR_ANYWHERE,

    /**
     * Minimum characters per line (prevents excessively narrow wrapping).
     */
    val minLineLength: Int = 20,

    /**
     * Right margin to leave (in pixels).
     */
    val rightMargin: Float = 8f,

    /**
     * Whether to show a visual indicator for wrapped lines.
     */
    val showWrapIndicator: Boolean = true,

    /**
     * Indentation for continuation lines.
     */
    val continuationIndent: Int = 0
)

/**
 * Mode for determining where to wrap lines.
 */
enum class WrapMode {
    /**
     * Wrap at exact character limit, even mid-word.
     */
    ANYWHERE,

    /**
     * Only wrap at word boundaries (spaces, hyphens).
     */
    WORD_BOUNDARY,

    /**
     * Prefer word boundaries, but wrap mid-word if line is too long.
     */
    WORD_BOUNDARY_OR_ANYWHERE
}
