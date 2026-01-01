package ai.rever.bosseditor.model

import ai.rever.bosseditor.core.EditorDocument
import ai.rever.bosseditor.core.EditorPosition
import ai.rever.bosseditor.core.EditorRange
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Selection mode determines how selection behaves.
 */
enum class SelectionMode {
    /** Normal character-based selection */
    NORMAL,
    /** Line-based selection (entire lines) */
    LINE,
    /** Block/column selection (rectangular) */
    BLOCK
}

/**
 * Manages text selection state.
 *
 * Uses anchor-based selection model:
 * - Anchor: Where selection started (fixed point)
 * - Caret: Where selection currently ends (moving point)
 *
 * Selection range is always normalized: start <= end.
 *
 * Supports:
 * - Normal (character) selection
 * - Line selection (triple-click)
 * - Block/column selection (Alt+drag) - planned
 */
class SelectionModel(private val document: EditorDocument) {

    private val _selection = MutableStateFlow<EditorRange?>(null)
    val selection: StateFlow<EditorRange?> = _selection.asStateFlow()

    private val _mode = MutableStateFlow(SelectionMode.NORMAL)
    val mode: StateFlow<SelectionMode> = _mode.asStateFlow()

    /**
     * The anchor position where selection started.
     * Null when no selection is active.
     */
    private var anchor: EditorPosition? = null

    /**
     * Current selection range (normalized: start <= end).
     */
    val currentSelection: EditorRange?
        get() = _selection.value

    /**
     * Returns true if there is an active selection.
     */
    val hasSelection: Boolean
        get() = _selection.value?.let { !it.isEmpty } ?: false

    /**
     * Returns the selected text.
     */
    fun getSelectedText(): String {
        val sel = _selection.value ?: return ""
        if (sel.isEmpty) return ""

        val startOffset = document.positionToOffset(sel.start)
        val endOffset = document.positionToOffset(sel.end)
        return document.getText(startOffset, endOffset)
    }

    /**
     * Starts a new selection at the given position.
     *
     * @param position The starting position (anchor)
     * @param mode The selection mode (NORMAL, LINE, BLOCK)
     */
    fun startSelection(position: EditorPosition, mode: SelectionMode = SelectionMode.NORMAL) {
        val clamped = clampPosition(position)
        anchor = clamped
        _mode.value = mode

        when (mode) {
            SelectionMode.NORMAL -> {
                // Empty selection at start
                _selection.value = EditorRange(clamped, clamped)
            }
            SelectionMode.LINE -> {
                // Select entire line
                val lineStart = EditorPosition(clamped.line, 0)
                val lineEnd = if (clamped.line + 1 < document.lineCount) {
                    EditorPosition(clamped.line + 1, 0)
                } else {
                    EditorPosition(clamped.line, document.getLineLength(clamped.line))
                }
                anchor = lineStart
                _selection.value = EditorRange(lineStart, lineEnd)
            }
            SelectionMode.BLOCK -> {
                // Block selection - handled differently
                anchor = clamped
                _selection.value = EditorRange(clamped, clamped)
            }
        }
    }

    /**
     * Extends the selection to the given position.
     * The anchor remains fixed; only the end moves.
     *
     * @param position The new end position
     */
    fun extendTo(position: EditorPosition) {
        val anchorPos = anchor ?: return
        val clamped = clampPosition(position)

        when (_mode.value) {
            SelectionMode.NORMAL -> {
                _selection.value = if (anchorPos <= clamped) {
                    EditorRange(anchorPos, clamped)
                } else {
                    EditorRange(clamped, anchorPos)
                }
            }
            SelectionMode.LINE -> {
                // Extend by lines
                val startLine = minOf(anchorPos.line, clamped.line)
                val endLine = maxOf(anchorPos.line, clamped.line)

                val start = EditorPosition(startLine, 0)
                val end = if (endLine + 1 < document.lineCount) {
                    EditorPosition(endLine + 1, 0)
                } else {
                    EditorPosition(endLine, document.getLineLength(endLine))
                }
                _selection.value = EditorRange(start, end)
            }
            SelectionMode.BLOCK -> {
                // For block selection, store the raw range
                // Rendering will interpret this as a rectangular selection
                _selection.value = if (anchorPos <= clamped) {
                    EditorRange(anchorPos, clamped)
                } else {
                    EditorRange(clamped, anchorPos)
                }
            }
        }
    }

    /**
     * Sets a specific selection range.
     */
    fun setSelection(range: EditorRange?) {
        if (range == null) {
            clear()
            return
        }

        val start = clampPosition(range.start)
        val end = clampPosition(range.end)
        val normalized = if (start <= end) EditorRange(start, end) else EditorRange(end, start)

        anchor = normalized.start
        _selection.value = normalized
        _mode.value = SelectionMode.NORMAL
    }

    /**
     * Selects all text in the document.
     */
    fun selectAll() {
        val start = EditorPosition.ZERO
        val lastLine = document.lineCount - 1
        val end = EditorPosition(lastLine, document.getLineLength(lastLine))

        anchor = start
        _selection.value = EditorRange(start, end)
        _mode.value = SelectionMode.NORMAL
    }

    /**
     * Selects the word at the given position.
     * Returns the selection range, or null if no word at position.
     */
    fun selectWord(position: EditorPosition): EditorRange? {
        val offset = document.positionToOffset(clampPosition(position))
        val wordRange = findWordAt(offset) ?: return null

        val start = document.offsetToPosition(wordRange.first)
        val end = document.offsetToPosition(wordRange.second)
        val range = EditorRange(start, end)

        anchor = start
        _selection.value = range
        _mode.value = SelectionMode.NORMAL

        return range
    }

    /**
     * Selects the entire line at the given position.
     * Returns the selection range.
     */
    fun selectLine(position: EditorPosition): EditorRange {
        val clamped = clampPosition(position)
        val line = clamped.line

        val start = EditorPosition(line, 0)
        val end = if (line + 1 < document.lineCount) {
            EditorPosition(line + 1, 0)
        } else {
            EditorPosition(line, document.getLineLength(line))
        }

        val range = EditorRange(start, end)
        anchor = start
        _selection.value = range
        _mode.value = SelectionMode.LINE

        return range
    }

    /**
     * Clears the current selection.
     */
    fun clear() {
        anchor = null
        _selection.value = null
        _mode.value = SelectionMode.NORMAL
    }

    /**
     * Checks if the selection contains the given position.
     */
    fun contains(position: EditorPosition): Boolean {
        val sel = _selection.value ?: return false
        return sel.contains(position)
    }

    /**
     * Returns the offset range of the selection.
     */
    fun getOffsetRange(): IntRange? {
        val sel = _selection.value ?: return null
        val startOffset = document.positionToOffset(sel.start)
        val endOffset = document.positionToOffset(sel.end)
        return startOffset until endOffset
    }

    // --- Private helpers ---

    private fun clampPosition(position: EditorPosition): EditorPosition {
        val lineCount = document.lineCount.coerceAtLeast(1)
        val line = position.line.coerceIn(0, lineCount - 1)
        val lineLength = if (document.lineCount > 0) document.getLineLength(line) else 0
        val column = position.column.coerceIn(0, lineLength)

        return if (line == position.line && column == position.column) {
            position
        } else {
            EditorPosition(line, column)
        }
    }

    private fun findWordAt(offset: Int): Pair<Int, Int>? {
        if (document.length == 0) return null
        val clampedOffset = offset.coerceIn(0, document.length - 1)

        var start = clampedOffset
        var end = clampedOffset

        // Check if we're on a word character
        if (!isWordChar(document.charAt(clampedOffset))) {
            return null
        }

        // Expand backward
        while (start > 0 && isWordChar(document.charAt(start - 1))) {
            start--
        }

        // Expand forward
        while (end < document.length && isWordChar(document.charAt(end))) {
            end++
        }

        return if (start < end) Pair(start, end) else null
    }

    private fun isWordChar(char: Char): Boolean {
        return char.isLetterOrDigit() || char == '_'
    }
}
