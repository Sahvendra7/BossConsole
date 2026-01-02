package ai.rever.bosseditor.model

import ai.rever.bosseditor.core.EditorDocument
import ai.rever.bosseditor.core.EditorPosition
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages the caret (text cursor) state and movement.
 *
 * Features:
 * - Position tracking with observable state
 * - Preferred column for vertical navigation (sticky column)
 * - Word and line boundary navigation
 * - Smart home (toggle between line start and first non-whitespace)
 *
 * The preferred column is used when moving up/down to maintain horizontal
 * position even when lines are shorter than the current column.
 */
class CaretModel(private val document: EditorDocument) {

    private val _position = MutableStateFlow(EditorPosition.ZERO)
    val position: StateFlow<EditorPosition> = _position.asStateFlow()

    /**
     * The preferred column for vertical navigation.
     * When moving up/down, the caret tries to return to this column if the line is long enough.
     * Reset when moving horizontally or clicking.
     */
    private var preferredColumn: Int = 0

    /**
     * Current caret position.
     */
    val currentPosition: EditorPosition
        get() = _position.value

    /**
     * Current line number (0-indexed).
     */
    val line: Int
        get() = _position.value.line

    /**
     * Current column number (0-indexed).
     */
    val column: Int
        get() = _position.value.column

    /**
     * Current offset in the document.
     */
    val offset: Int
        get() = document.positionToOffset(_position.value)

    /**
     * Moves the caret to the specified position.
     *
     * @param position The target position
     * @param updatePreferredColumn If true, updates the preferred column (default true for horizontal moves)
     */
    fun moveTo(position: EditorPosition, updatePreferredColumn: Boolean = true) {
        val clamped = clampPosition(position)
        _position.value = clamped
        if (updatePreferredColumn) {
            preferredColumn = clamped.column
        }
    }

    /**
     * Moves the caret to the specified offset.
     */
    fun moveToOffset(offset: Int, updatePreferredColumn: Boolean = true) {
        val clampedOffset = offset.coerceIn(0, document.length)
        moveTo(document.offsetToPosition(clampedOffset), updatePreferredColumn)
    }

    /**
     * Moves the caret left by one character.
     * At line start, moves to end of previous line.
     */
    fun moveLeft(): EditorPosition {
        val pos = _position.value
        val newPos = when {
            pos.column > 0 -> EditorPosition(pos.line, pos.column - 1)
            pos.line > 0 -> {
                val prevLineLength = document.getLineLength(pos.line - 1)
                EditorPosition(pos.line - 1, prevLineLength)
            }
            else -> pos
        }
        moveTo(newPos)
        return newPos
    }

    /**
     * Moves the caret right by one character.
     * At line end, moves to start of next line.
     */
    fun moveRight(): EditorPosition {
        val pos = _position.value
        val lineLength = document.getLineLength(pos.line)
        val newPos = when {
            pos.column < lineLength -> EditorPosition(pos.line, pos.column + 1)
            pos.line < document.lineCount - 1 -> EditorPosition(pos.line + 1, 0)
            else -> pos
        }
        moveTo(newPos)
        return newPos
    }

    /**
     * Moves the caret up by one line.
     * Uses preferred column to maintain horizontal position.
     */
    fun moveUp(): EditorPosition {
        val pos = _position.value
        if (pos.line == 0) return pos

        val newLine = pos.line - 1
        val newLineLength = document.getLineLength(newLine)
        val newColumn = minOf(preferredColumn, newLineLength)

        val newPos = EditorPosition(newLine, newColumn)
        moveTo(newPos, updatePreferredColumn = false)
        return newPos
    }

    /**
     * Moves the caret down by one line.
     * Uses preferred column to maintain horizontal position.
     */
    fun moveDown(): EditorPosition {
        val pos = _position.value
        if (pos.line >= document.lineCount - 1) return pos

        val newLine = pos.line + 1
        val newLineLength = document.getLineLength(newLine)
        val newColumn = minOf(preferredColumn, newLineLength)

        val newPos = EditorPosition(newLine, newColumn)
        moveTo(newPos, updatePreferredColumn = false)
        return newPos
    }

    /**
     * Moves the caret to the start of the current line.
     *
     * @param smartHome If true, toggles between column 0 and first non-whitespace
     */
    fun moveToLineStart(smartHome: Boolean = true): EditorPosition {
        val pos = _position.value
        val lineText = document.getLineText(pos.line)

        val firstNonWhitespace = lineText.indexOfFirst { !it.isWhitespace() }
            .takeIf { it >= 0 } ?: 0

        val newColumn = if (smartHome && pos.column != firstNonWhitespace && firstNonWhitespace > 0) {
            firstNonWhitespace
        } else if (smartHome && pos.column == firstNonWhitespace) {
            0
        } else {
            0
        }

        val newPos = EditorPosition(pos.line, newColumn)
        moveTo(newPos)
        return newPos
    }

    /**
     * Moves the caret to the end of the current line.
     */
    fun moveToLineEnd(): EditorPosition {
        val pos = _position.value
        val lineLength = document.getLineLength(pos.line)
        val newPos = EditorPosition(pos.line, lineLength)
        moveTo(newPos)
        return newPos
    }

    /**
     * Moves the caret to the start of the document.
     */
    fun moveToDocumentStart(): EditorPosition {
        val newPos = EditorPosition.ZERO
        moveTo(newPos)
        return newPos
    }

    /**
     * Moves the caret to the end of the document.
     */
    fun moveToDocumentEnd(): EditorPosition {
        val lastLine = document.lineCount - 1
        val lastColumn = document.getLineLength(lastLine)
        val newPos = EditorPosition(lastLine, lastColumn)
        moveTo(newPos)
        return newPos
    }

    /**
     * Moves the caret to the start of the next word.
     */
    fun moveToNextWord(): EditorPosition {
        val offset = this.offset
        val newOffset = findNextWordBoundary(offset, forward = true)
        moveToOffset(newOffset)
        return _position.value
    }

    /**
     * Moves the caret to the start of the previous word.
     */
    fun moveToPreviousWord(): EditorPosition {
        val offset = this.offset
        val newOffset = findNextWordBoundary(offset, forward = false)
        moveToOffset(newOffset)
        return _position.value
    }

    /**
     * Moves the caret up by a page (multiple lines).
     *
     * @param visibleLines Number of visible lines in the viewport
     */
    fun movePageUp(visibleLines: Int): EditorPosition {
        val pos = _position.value
        val newLine = maxOf(0, pos.line - visibleLines)
        val newLineLength = document.getLineLength(newLine)
        val newColumn = minOf(preferredColumn, newLineLength)

        val newPos = EditorPosition(newLine, newColumn)
        moveTo(newPos, updatePreferredColumn = false)
        return newPos
    }

    /**
     * Moves the caret down by a page (multiple lines).
     *
     * @param visibleLines Number of visible lines in the viewport
     */
    fun movePageDown(visibleLines: Int): EditorPosition {
        val pos = _position.value
        val newLine = minOf(document.lineCount - 1, pos.line + visibleLines)
        val newLineLength = document.getLineLength(newLine)
        val newColumn = minOf(preferredColumn, newLineLength)

        val newPos = EditorPosition(newLine, newColumn)
        moveTo(newPos, updatePreferredColumn = false)
        return newPos
    }

    /**
     * Resets the preferred column to the current column.
     * Call this after horizontal moves or clicks.
     */
    fun resetPreferredColumn() {
        preferredColumn = _position.value.column
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

    private fun findNextWordBoundary(offset: Int, forward: Boolean): Int {
        if (document.length == 0) return 0

        var pos = offset.coerceIn(0, document.length)

        if (forward) {
            // Skip current word
            while (pos < document.length && isWordChar(document.charAt(pos))) {
                pos++
            }
            // Skip whitespace
            while (pos < document.length && !isWordChar(document.charAt(pos))) {
                pos++
            }
        } else {
            // Move back one if at word boundary
            if (pos > 0) pos--
            // Skip whitespace
            while (pos > 0 && !isWordChar(document.charAt(pos))) {
                pos--
            }
            // Find start of word
            while (pos > 0 && isWordChar(document.charAt(pos - 1))) {
                pos--
            }
        }

        return pos
    }

    private fun isWordChar(char: Char): Boolean {
        return char.isLetterOrDigit() || char == '_'
    }
}
