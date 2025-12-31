package ai.rever.bosseditor.input

import ai.rever.bosseditor.core.EditorPosition
import ai.rever.bosseditor.core.EditorRange
import ai.rever.bosseditor.core.EditorState
import ai.rever.bosseditor.model.SelectionMode
import androidx.compose.ui.input.key.*
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection

/**
 * Handles all keyboard and mouse input for the editor.
 *
 * Features:
 * - Navigation (arrows, Home, End, Page Up/Down)
 * - Selection (Shift + navigation)
 * - Word navigation (Ctrl/Alt + arrows)
 * - Text editing (typing, delete, backspace)
 * - Clipboard (copy, cut, paste)
 * - Undo/redo
 * - Select all
 *
 * Platform-aware: Uses Cmd on macOS, Ctrl on Windows/Linux.
 */
class EditorInputHandler(
    private val state: EditorState,
    private val onTextChanged: () -> Unit = {},
    private val onScrollRequest: (deltaLines: Int) -> Unit = {}
) {
    /**
     * Number of visible lines for page up/down.
     * Should be set by the editor component.
     */
    var visibleLines: Int = 20

    /**
     * Handles a key event.
     * Returns true if the event was consumed.
     */
    fun handleKeyEvent(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false

        val isShift = event.isShiftPressed
        val isCtrl = event.isCtrlPressed
        val isMeta = event.isMetaPressed
        val isAlt = event.isAltPressed

        // On macOS, use Cmd; on other platforms, use Ctrl
        val isCmdOrCtrl = isMeta || isCtrl

        return when {
            // Navigation
            handleNavigation(event, isShift, isCmdOrCtrl, isAlt) -> true

            // Clipboard
            handleClipboard(event, isCmdOrCtrl, isShift) -> true

            // Undo/Redo
            handleUndoRedo(event, isCmdOrCtrl, isShift) -> true

            // Select all (Cmd/Ctrl + A)
            isCmdOrCtrl && event.key == Key.A -> {
                state.selectAll()
                true
            }

            // Text editing (only if not a modifier key combo)
            !isCmdOrCtrl && !isAlt && handleTextInput(event) -> true

            else -> false
        }
    }

    /**
     * Handles navigation keys.
     */
    private fun handleNavigation(
        event: KeyEvent,
        isShift: Boolean,
        isCmdOrCtrl: Boolean,
        isAlt: Boolean
    ): Boolean {
        val extendSelection = isShift

        return when (event.key) {
            Key.DirectionLeft -> {
                when {
                    isCmdOrCtrl -> state.moveCaretToLineStart(extendSelection)
                    isAlt -> moveToPreviousWord(extendSelection)
                    else -> state.moveCaretBy(0, -1, extendSelection)
                }
                true
            }

            Key.DirectionRight -> {
                when {
                    isCmdOrCtrl -> state.moveCaretToLineEnd(extendSelection)
                    isAlt -> moveToNextWord(extendSelection)
                    else -> state.moveCaretBy(0, 1, extendSelection)
                }
                true
            }

            Key.DirectionUp -> {
                when {
                    isCmdOrCtrl -> state.moveCaretToStart(extendSelection)
                    isAlt -> {
                        // Alt+Up: Move line up (future feature)
                        state.moveCaretBy(-1, 0, extendSelection)
                    }
                    else -> state.moveCaretBy(-1, 0, extendSelection)
                }
                true
            }

            Key.DirectionDown -> {
                when {
                    isCmdOrCtrl -> state.moveCaretToEnd(extendSelection)
                    isAlt -> {
                        // Alt+Down: Move line down (future feature)
                        state.moveCaretBy(1, 0, extendSelection)
                    }
                    else -> state.moveCaretBy(1, 0, extendSelection)
                }
                true
            }

            Key.Home -> {
                if (isCmdOrCtrl) {
                    state.moveCaretToStart(extendSelection)
                } else {
                    state.moveCaretToLineStart(extendSelection)
                }
                true
            }

            Key.MoveEnd -> {
                if (isCmdOrCtrl) {
                    state.moveCaretToEnd(extendSelection)
                } else {
                    state.moveCaretToLineEnd(extendSelection)
                }
                true
            }

            Key.PageUp -> {
                movePageUp(extendSelection)
                true
            }

            Key.PageDown -> {
                movePageDown(extendSelection)
                true
            }

            else -> false
        }
    }

    /**
     * Handles clipboard operations.
     */
    private fun handleClipboard(
        event: KeyEvent,
        isCmdOrCtrl: Boolean,
        isShift: Boolean
    ): Boolean {
        if (!isCmdOrCtrl) return false

        return when (event.key) {
            Key.C -> {
                copy()
                true
            }

            Key.X -> {
                cut()
                onTextChanged()
                true
            }

            Key.V -> {
                paste()
                onTextChanged()
                true
            }

            // Shift+Insert for paste (Windows convention)
            Key.Insert -> if (isShift) {
                paste()
                onTextChanged()
                true
            } else false

            else -> false
        }
    }

    /**
     * Handles undo/redo.
     */
    private fun handleUndoRedo(
        event: KeyEvent,
        isCmdOrCtrl: Boolean,
        isShift: Boolean
    ): Boolean {
        if (!isCmdOrCtrl) return false

        return when {
            event.key == Key.Z && !isShift -> {
                state.undo()
                onTextChanged()
                true
            }

            event.key == Key.Z && isShift -> {
                state.redo()
                onTextChanged()
                true
            }

            event.key == Key.Y -> {
                state.redo()
                onTextChanged()
                true
            }

            else -> false
        }
    }

    /**
     * Handles text input (typing, backspace, delete, etc.).
     */
    private fun handleTextInput(event: KeyEvent): Boolean {
        return when (event.key) {
            Key.Backspace -> {
                if (event.isAltPressed) {
                    deleteWordBackward()
                } else {
                    state.deleteBackward()
                }
                onTextChanged()
                true
            }

            Key.Delete -> {
                if (event.isAltPressed) {
                    deleteWordForward()
                } else {
                    state.deleteForward()
                }
                onTextChanged()
                true
            }

            Key.Enter, Key.NumPadEnter -> {
                insertNewLine()
                onTextChanged()
                true
            }

            Key.Tab -> {
                if (event.isShiftPressed) {
                    // Shift+Tab: outdent (future feature)
                    state.insertText("    ")
                } else {
                    state.insertText("    ") // 4 spaces
                }
                onTextChanged()
                true
            }

            else -> {
                // Character input
                val codePoint = event.utf16CodePoint
                if (codePoint != 0) {
                    val char = codePoint.toChar()
                    if (!char.isISOControl()) {
                        state.insertText(char.toString())
                        onTextChanged()
                        return true
                    }
                }
                false
            }
        }
    }

    // --- Navigation helpers ---

    private fun moveToNextWord(extendSelection: Boolean) {
        val offset = state.caretOffset
        val newOffset = findNextWordBoundary(offset, forward = true)
        state.moveCaretToOffset(newOffset, extendSelection)
    }

    private fun moveToPreviousWord(extendSelection: Boolean) {
        val offset = state.caretOffset
        val newOffset = findNextWordBoundary(offset, forward = false)
        state.moveCaretToOffset(newOffset, extendSelection)
    }

    private fun movePageUp(extendSelection: Boolean) {
        val currentLine = state.caretPosition.value.line
        val newLine = maxOf(0, currentLine - visibleLines)
        state.moveCaret(
            EditorPosition(newLine, state.caretPosition.value.column),
            extendSelection
        )
        onScrollRequest(-visibleLines)
    }

    private fun movePageDown(extendSelection: Boolean) {
        val currentLine = state.caretPosition.value.line
        val newLine = minOf(state.document.lineCount - 1, currentLine + visibleLines)
        state.moveCaret(
            EditorPosition(newLine, state.caretPosition.value.column),
            extendSelection
        )
        onScrollRequest(visibleLines)
    }

    private fun findNextWordBoundary(offset: Int, forward: Boolean): Int {
        val doc = state.document
        if (doc.length == 0) return 0

        var pos = offset.coerceIn(0, doc.length)

        if (forward) {
            // Skip current word
            while (pos < doc.length && isWordChar(doc.charAt(pos))) {
                pos++
            }
            // Skip whitespace
            while (pos < doc.length && !isWordChar(doc.charAt(pos))) {
                pos++
            }
        } else {
            // Move back one if at word boundary
            if (pos > 0) pos--
            // Skip whitespace
            while (pos > 0 && !isWordChar(doc.charAt(pos))) {
                pos--
            }
            // Find start of word
            while (pos > 0 && isWordChar(doc.charAt(pos - 1))) {
                pos--
            }
        }

        return pos
    }

    private fun isWordChar(char: Char): Boolean {
        return char.isLetterOrDigit() || char == '_'
    }

    // --- Text editing helpers ---

    private fun insertNewLine() {
        val currentPos = state.caretPosition.value
        val lineText = state.document.getLineText(currentPos.line)

        // Auto-indent: copy leading whitespace from current line
        val leadingWhitespace = lineText.takeWhile { it.isWhitespace() }

        state.insertText("\n$leadingWhitespace")
    }

    private fun deleteWordBackward() {
        val offset = state.caretOffset
        if (offset == 0) return

        val newOffset = findNextWordBoundary(offset, forward = false)
        val startPos = state.document.offsetToPosition(newOffset)
        val endPos = state.caretPosition.value

        // Select the word and delete
        state.setSelection(EditorRange(startPos, endPos))
        state.deleteSelection()
    }

    private fun deleteWordForward() {
        val offset = state.caretOffset
        if (offset >= state.document.length) return

        val newOffset = findNextWordBoundary(offset, forward = true)
        val startPos = state.caretPosition.value
        val endPos = state.document.offsetToPosition(newOffset)

        // Select the word and delete
        state.setSelection(EditorRange(startPos, endPos))
        state.deleteSelection()
    }

    // --- Clipboard operations ---

    /**
     * Copies selected text to clipboard.
     */
    fun copy() {
        val text = state.selectedText
        if (text.isNotEmpty()) {
            setClipboardContent(text)
        }
    }

    /**
     * Cuts selected text to clipboard.
     */
    fun cut() {
        val text = state.selectedText
        if (text.isNotEmpty()) {
            setClipboardContent(text)
            state.deleteSelection()
        }
    }

    /**
     * Pastes text from clipboard.
     */
    fun paste() {
        val text = getClipboardContent()
        if (text != null && text.isNotEmpty()) {
            state.insertText(text)
        }
    }

    private fun setClipboardContent(text: String) {
        try {
            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
            clipboard.setContents(StringSelection(text), null)
        } catch (e: Exception) {
            // Ignore clipboard errors
        }
    }

    private fun getClipboardContent(): String? {
        return try {
            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
            if (clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
                clipboard.getData(DataFlavor.stringFlavor) as? String
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}

/**
 * Mouse event types for editor interaction.
 */
enum class EditorMouseEventType {
    PRESS,
    RELEASE,
    DRAG,
    MOVE
}

/**
 * Mouse button types.
 */
enum class EditorMouseButton {
    LEFT,
    MIDDLE,
    RIGHT
}

/**
 * Mouse event data for editor interaction.
 */
data class EditorMouseEvent(
    val type: EditorMouseEventType,
    val button: EditorMouseButton,
    val position: EditorPosition,
    val clickCount: Int,
    val isShift: Boolean,
    val isCtrl: Boolean,
    val isAlt: Boolean,
    val isMeta: Boolean
)

/**
 * Handles mouse events for the editor.
 */
class EditorMouseHandler(
    private val state: EditorState,
    private val onSelectionChanged: () -> Unit = {}
) {
    private var isDragging = false
    private var dragStartPosition: EditorPosition? = null

    /**
     * Handles a mouse event.
     */
    fun handleMouseEvent(event: EditorMouseEvent) {
        when (event.type) {
            EditorMouseEventType.PRESS -> handlePress(event)
            EditorMouseEventType.RELEASE -> handleRelease(event)
            EditorMouseEventType.DRAG -> handleDrag(event)
            EditorMouseEventType.MOVE -> handleMove(event)
        }
    }

    private fun handlePress(event: EditorMouseEvent) {
        if (event.button != EditorMouseButton.LEFT) return

        when (event.clickCount) {
            1 -> {
                // Single click: position caret or start selection
                if (event.isShift) {
                    // Shift+click: extend selection
                    extendSelectionTo(event.position)
                } else {
                    // Start potential drag selection
                    isDragging = true
                    dragStartPosition = event.position
                    state.moveCaret(event.position)
                    state.clearSelection()
                }
            }

            2 -> {
                // Double click: select word
                state.selectWord()
                isDragging = false
                onSelectionChanged()
            }

            3 -> {
                // Triple click: select line
                state.selectLine()
                isDragging = false
                onSelectionChanged()
            }
        }
    }

    private fun handleRelease(event: EditorMouseEvent) {
        isDragging = false
        dragStartPosition = null
    }

    private fun handleDrag(event: EditorMouseEvent) {
        if (!isDragging) return

        val startPos = dragStartPosition ?: return

        // Extend selection from start to current position
        val currentPos = event.position
        val range = if (startPos <= currentPos) {
            EditorRange(startPos, currentPos)
        } else {
            EditorRange(currentPos, startPos)
        }

        state.setSelection(range)
        state.moveCaret(currentPos, extendSelection = true)
        onSelectionChanged()
    }

    private fun handleMove(event: EditorMouseEvent) {
        // Handle hover effects (e.g., hyperlinks) in the future
    }

    private fun extendSelectionTo(position: EditorPosition) {
        val currentSel = state.selection.value
        val caretPos = state.caretPosition.value

        val anchor = currentSel?.let {
            if (caretPos == it.start) it.end else it.start
        } ?: caretPos

        val newRange = if (anchor <= position) {
            EditorRange(anchor, position)
        } else {
            EditorRange(position, anchor)
        }

        state.setSelection(newRange)
        state.moveCaret(position, extendSelection = true)
        onSelectionChanged()
    }
}
