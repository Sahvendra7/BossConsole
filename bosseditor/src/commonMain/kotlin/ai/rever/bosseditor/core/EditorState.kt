package ai.rever.bosseditor.core

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Central state holder for the editor.
 *
 * This class coordinates all editor components:
 * - Document (text buffer)
 * - Caret (cursor position)
 * - Selection
 * - Undo/redo
 * - Modification state
 *
 * All state changes flow through this class to ensure consistency.
 */
class EditorState(
    initialText: String = "",
    val filePath: String? = null
) {
    // Core document
    val document = EditorDocument(initialText)

    // Undo manager
    val undoManager = UndoManager(document)

    // Caret position (observable)
    private val _caretPosition = MutableStateFlow(EditorPosition.ZERO)
    val caretPosition: StateFlow<EditorPosition> = _caretPosition.asStateFlow()

    // Selection range (null = no selection)
    private val _selection = MutableStateFlow<EditorRange?>(null)
    val selection: StateFlow<EditorRange?> = _selection.asStateFlow()

    // Modification state
    private val _isModified = MutableStateFlow(false)
    val isModified: StateFlow<Boolean> = _isModified.asStateFlow()

    // Document version when last saved (for tracking modifications)
    private var savedVersion: Long = document.documentVersion

    // Scroll position (for virtual scrolling)
    private val _scrollOffset = MutableStateFlow(ScrollOffset(0, 0))
    val scrollOffset: StateFlow<ScrollOffset> = _scrollOffset.asStateFlow()

    // Visible line range (computed from scroll position and viewport)
    var visibleLineRange: IntRange = 0..0
        private set

    // State listeners
    private val stateListeners = mutableListOf<EditorStateListener>()

    init {
        // Listen to document changes
        document.addDocumentListener { change ->
            // Update modification state
            _isModified.value = document.documentVersion != savedVersion

            // Adjust caret position if needed
            adjustCaretAfterChange(change)

            // Clear selection on document change (unless it's from undo/redo)
            // The selection will be re-set by the operation that caused the change if needed
        }
    }

    /**
     * The current caret offset in the document.
     */
    val caretOffset: Int
        get() = document.positionToOffset(caretPosition.value)

    /**
     * Returns true if there is an active selection.
     */
    val hasSelection: Boolean
        get() = selection.value?.let { !it.isEmpty } ?: false

    /**
     * Returns the selected text, or empty string if no selection.
     */
    val selectedText: String
        get() {
            val sel = selection.value ?: return ""
            if (sel.isEmpty) return ""
            val startOffset = document.positionToOffset(sel.start)
            val endOffset = document.positionToOffset(sel.end)
            return document.getText(startOffset, endOffset)
        }

    // --- Caret operations ---

    /**
     * Moves the caret to the specified position.
     */
    fun moveCaret(position: EditorPosition, extendSelection: Boolean = false) {
        val clampedPosition = clampPosition(position)

        if (extendSelection) {
            extendSelectionTo(clampedPosition)
        } else {
            clearSelection()
        }

        _caretPosition.value = clampedPosition
        notifyCaretMoved()
    }

    /**
     * Moves the caret to the specified offset.
     */
    fun moveCaretToOffset(offset: Int, extendSelection: Boolean = false) {
        val clampedOffset = offset.coerceIn(0, document.length)
        val position = document.offsetToPosition(clampedOffset)
        moveCaret(position, extendSelection)
    }

    /**
     * Moves the caret by the specified delta.
     */
    fun moveCaretBy(lineDelta: Int, columnDelta: Int, extendSelection: Boolean = false) {
        val current = caretPosition.value
        val newLine = (current.line + lineDelta).coerceIn(0, document.lineCount - 1)
        val lineLength = document.getLineLength(newLine)
        val newColumn = (current.column + columnDelta).coerceIn(0, lineLength)
        moveCaret(EditorPosition(newLine, newColumn), extendSelection)
    }

    /**
     * Moves the caret to the start of the document.
     */
    fun moveCaretToStart(extendSelection: Boolean = false) {
        moveCaret(EditorPosition.ZERO, extendSelection)
    }

    /**
     * Moves the caret to the end of the document.
     */
    fun moveCaretToEnd(extendSelection: Boolean = false) {
        val lastLine = document.lineCount - 1
        val lastColumn = document.getLineLength(lastLine)
        moveCaret(EditorPosition(lastLine, lastColumn), extendSelection)
    }

    /**
     * Moves the caret to the start of the current line.
     */
    fun moveCaretToLineStart(extendSelection: Boolean = false) {
        moveCaret(caretPosition.value.toLineStart(), extendSelection)
    }

    /**
     * Moves the caret to the end of the current line.
     */
    fun moveCaretToLineEnd(extendSelection: Boolean = false) {
        val line = caretPosition.value.line
        val lineLength = document.getLineLength(line)
        moveCaret(EditorPosition(line, lineLength), extendSelection)
    }

    // --- Selection operations ---

    /**
     * Sets the selection range.
     */
    fun setSelection(range: EditorRange?) {
        _selection.value = range?.let {
            val start = clampPosition(it.start)
            val end = clampPosition(it.end)
            if (start <= end) EditorRange(start, end) else EditorRange(end, start)
        }
        notifySelectionChanged()
    }

    /**
     * Selects all text in the document.
     */
    fun selectAll() {
        val start = EditorPosition.ZERO
        val end = EditorPosition(
            document.lineCount - 1,
            document.getLineLength(document.lineCount - 1)
        )
        setSelection(EditorRange(start, end))
        _caretPosition.value = end
    }

    /**
     * Clears the current selection.
     */
    fun clearSelection() {
        if (_selection.value != null) {
            _selection.value = null
            notifySelectionChanged()
        }
    }

    /**
     * Selects the word at the current caret position.
     */
    fun selectWord() {
        val offset = caretOffset
        val wordRange = findWordAt(offset)
        if (wordRange != null) {
            val start = document.offsetToPosition(wordRange.first)
            val end = document.offsetToPosition(wordRange.second)
            setSelection(EditorRange(start, end))
            _caretPosition.value = end
        }
    }

    /**
     * Selects the current line.
     */
    fun selectLine() {
        val line = caretPosition.value.line
        val start = EditorPosition(line, 0)
        val end = if (line + 1 < document.lineCount) {
            EditorPosition(line + 1, 0)
        } else {
            EditorPosition(line, document.getLineLength(line))
        }
        setSelection(EditorRange(start, end))
        _caretPosition.value = end
    }

    // --- Text editing operations ---

    /**
     * Inserts text at the current caret position.
     * If there's a selection, it's replaced.
     */
    fun insertText(text: String) {
        val sel = selection.value
        if (sel != null && !sel.isEmpty) {
            // Replace selection
            val startOffset = document.positionToOffset(sel.start)
            val endOffset = document.positionToOffset(sel.end)
            document.replace(startOffset, endOffset, text)
            clearSelection()
            moveCaretToOffset(startOffset + text.length)
        } else {
            // Insert at caret
            val offset = caretOffset
            document.insert(offset, text)
            moveCaretToOffset(offset + text.length)
        }
    }

    /**
     * Deletes the character before the caret (backspace).
     */
    fun deleteBackward() {
        val sel = selection.value
        if (sel != null && !sel.isEmpty) {
            deleteSelection()
        } else {
            val offset = caretOffset
            if (offset > 0) {
                document.delete(offset - 1, offset)
                moveCaretToOffset(offset - 1)
            }
        }
    }

    /**
     * Deletes the character after the caret (delete key).
     */
    fun deleteForward() {
        val sel = selection.value
        if (sel != null && !sel.isEmpty) {
            deleteSelection()
        } else {
            val offset = caretOffset
            if (offset < document.length) {
                document.delete(offset, offset + 1)
            }
        }
    }

    /**
     * Deletes the current selection.
     */
    fun deleteSelection() {
        val sel = selection.value ?: return
        if (sel.isEmpty) return

        val startOffset = document.positionToOffset(sel.start)
        val endOffset = document.positionToOffset(sel.end)
        document.delete(startOffset, endOffset)
        clearSelection()
        moveCaret(sel.start)
    }

    // --- Undo/Redo ---

    /**
     * Undoes the last edit.
     */
    fun undo(): Boolean = undoManager.undo()

    /**
     * Redoes the last undone edit.
     */
    fun redo(): Boolean = undoManager.redo()

    // --- File operations ---

    /**
     * Marks the document as saved (clears modification flag).
     */
    fun markAsSaved() {
        savedVersion = document.documentVersion
        _isModified.value = false
    }

    /**
     * Sets the document text and resets state.
     */
    fun setText(text: String) {
        document.setText(text)
        undoManager.clear()
        _caretPosition.value = EditorPosition.ZERO
        clearSelection()
        savedVersion = document.documentVersion
        _isModified.value = false
    }

    // --- Scroll operations ---

    /**
     * Sets the scroll offset.
     */
    fun setScrollOffset(offset: ScrollOffset) {
        _scrollOffset.value = offset
    }

    /**
     * Updates the visible line range based on scroll position and viewport.
     */
    fun updateVisibleLineRange(firstLine: Int, lastLine: Int) {
        visibleLineRange = firstLine.coerceAtLeast(0)..lastLine.coerceAtMost(document.lineCount - 1)
    }

    // --- Listeners ---

    /**
     * Adds a state listener.
     */
    fun addStateListener(listener: EditorStateListener) {
        stateListeners.add(listener)
    }

    /**
     * Removes a state listener.
     */
    fun removeStateListener(listener: EditorStateListener) {
        stateListeners.remove(listener)
    }

    // --- Private helpers ---

    private fun clampPosition(position: EditorPosition): EditorPosition {
        val line = position.line.coerceIn(0, (document.lineCount - 1).coerceAtLeast(0))
        val lineLength = if (document.lineCount > 0) document.getLineLength(line) else 0
        val column = position.column.coerceIn(0, lineLength)
        return if (line == position.line && column == position.column) position
        else EditorPosition(line, column)
    }

    private fun extendSelectionTo(newPosition: EditorPosition) {
        val currentSel = selection.value
        val anchor = currentSel?.let {
            // Use the end opposite to caret as anchor
            if (caretPosition.value == it.start) it.end else it.start
        } ?: caretPosition.value

        val newSel = if (anchor <= newPosition) {
            EditorRange(anchor, newPosition)
        } else {
            EditorRange(newPosition, anchor)
        }

        _selection.value = newSel
    }

    private fun adjustCaretAfterChange(change: DocumentChange) {
        val caretOff = caretOffset
        val changeEnd = change.offset + change.oldLength

        val newOffset = when {
            caretOff <= change.offset -> caretOff
            caretOff >= changeEnd -> caretOff - change.oldLength + change.newLength
            else -> change.offset + change.newLength
        }

        val clampedOffset = newOffset.coerceIn(0, document.length)
        _caretPosition.value = document.offsetToPosition(clampedOffset)
    }

    private fun findWordAt(offset: Int): Pair<Int, Int>? {
        if (document.length == 0) return null
        val clampedOffset = offset.coerceIn(0, document.length - 1)

        // Find word boundaries
        var start = clampedOffset
        var end = clampedOffset

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

    private fun notifyCaretMoved() {
        val pos = caretPosition.value
        for (listener in stateListeners) {
            listener.caretMoved(pos)
        }
    }

    private fun notifySelectionChanged() {
        val sel = selection.value
        for (listener in stateListeners) {
            listener.selectionChanged(sel)
        }
    }
}

/**
 * Scroll offset for the editor viewport.
 */
data class ScrollOffset(
    val x: Int,
    val y: Int
)

/**
 * Listener for editor state changes.
 */
interface EditorStateListener {
    fun caretMoved(position: EditorPosition) {}
    fun selectionChanged(selection: EditorRange?) {}
}
