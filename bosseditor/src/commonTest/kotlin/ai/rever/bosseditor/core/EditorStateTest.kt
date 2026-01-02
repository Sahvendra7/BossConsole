package ai.rever.bosseditor.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EditorStateTest {

    // ============================================
    // Initial State Tests
    // ============================================

    @Test
    fun testInitialStateEmpty() {
        val state = EditorState()
        assertEquals("", state.document.getText())
        assertEquals(EditorPosition.ZERO, state.caretPosition.value)
        assertNull(state.selection.value)
        assertFalse(state.isModified.value)
        assertFalse(state.hasSelection)
    }

    @Test
    fun testInitialStateWithText() {
        val state = EditorState("hello world")
        assertEquals("hello world", state.document.getText())
        assertEquals(EditorPosition.ZERO, state.caretPosition.value)
        assertFalse(state.isModified.value)
    }

    // ============================================
    // Caret Positioning Tests
    // ============================================

    @Test
    fun testMoveCaretToPosition() {
        val state = EditorState("hello\nworld")
        state.moveCaret(EditorPosition(1, 3))
        assertEquals(EditorPosition(1, 3), state.caretPosition.value)
    }

    @Test
    fun testMoveCaretClampsToDocumentEnd() {
        val state = EditorState("hello")
        state.moveCaret(EditorPosition(0, 100)) // Beyond line length
        assertEquals(EditorPosition(0, 5), state.caretPosition.value)
    }

    @Test
    fun testMoveCaretClampsToLastLine() {
        val state = EditorState("hello")
        state.moveCaret(EditorPosition(100, 0)) // Beyond line count
        // Line clamped to 0, column stays 0 (valid for line 0)
        assertEquals(EditorPosition(0, 0), state.caretPosition.value)
    }

    @Test
    fun testEditorPositionRejectsNegative() {
        // EditorPosition enforces non-negative values at construction time
        assertFailsWith<IllegalArgumentException> {
            EditorPosition(-1, 0)
        }
        assertFailsWith<IllegalArgumentException> {
            EditorPosition(0, -1)
        }
    }

    @Test
    fun testMoveCaretToOffset() {
        val state = EditorState("hello\nworld")
        state.moveCaretToOffset(8) // "wo|rld"
        assertEquals(EditorPosition(1, 2), state.caretPosition.value)
    }

    @Test
    fun testMoveCaretToOffsetClampsToEnd() {
        val state = EditorState("hello")
        state.moveCaretToOffset(100)
        assertEquals(5, state.caretOffset)
    }

    @Test
    fun testMoveCaretToOffsetClampsToZero() {
        val state = EditorState("hello")
        state.moveCaretToOffset(-10)
        assertEquals(0, state.caretOffset)
    }

    @Test
    fun testMoveCaretBy() {
        val state = EditorState("line1\nline2\nline3")
        state.moveCaret(EditorPosition(1, 2))
        state.moveCaretBy(1, 1) // Move down and right
        assertEquals(EditorPosition(2, 3), state.caretPosition.value)
    }

    @Test
    fun testMoveCaretToStart() {
        val state = EditorState("hello\nworld")
        state.moveCaret(EditorPosition(1, 3))
        state.moveCaretToStart()
        assertEquals(EditorPosition.ZERO, state.caretPosition.value)
    }

    @Test
    fun testMoveCaretToEnd() {
        val state = EditorState("hello\nworld")
        state.moveCaretToEnd()
        assertEquals(EditorPosition(1, 5), state.caretPosition.value)
    }

    @Test
    fun testMoveCaretToLineStart() {
        val state = EditorState("hello\nworld")
        state.moveCaret(EditorPosition(1, 3))
        state.moveCaretToLineStart()
        assertEquals(EditorPosition(1, 0), state.caretPosition.value)
    }

    @Test
    fun testMoveCaretToLineEnd() {
        val state = EditorState("hello\nworld")
        state.moveCaret(EditorPosition(0, 2))
        state.moveCaretToLineEnd()
        assertEquals(EditorPosition(0, 5), state.caretPosition.value)
    }

    @Test
    fun testEmptyDocumentCaretPosition() {
        val state = EditorState()
        assertEquals(EditorPosition.ZERO, state.caretPosition.value)
        state.moveCaret(EditorPosition(10, 10))
        assertEquals(EditorPosition(0, 0), state.caretPosition.value)
    }

    // ============================================
    // Selection Tests
    // ============================================

    @Test
    fun testSetSelection() {
        val state = EditorState("hello world")
        state.setSelection(EditorRange(EditorPosition(0, 0), EditorPosition(0, 5)))

        assertTrue(state.hasSelection)
        assertEquals("hello", state.selectedText)
    }

    @Test
    fun testEditorRangeRejectsReversed() {
        // EditorRange enforces start <= end at construction time
        assertFailsWith<IllegalArgumentException> {
            EditorRange(EditorPosition(0, 5), EditorPosition(0, 0))
        }
    }

    @Test
    fun testSetSelectionClampsPositions() {
        val state = EditorState("hello world")
        // Set selection with out-of-bounds positions
        state.setSelection(EditorRange(EditorPosition(0, 0), EditorPosition(0, 100)))

        val sel = state.selection.value
        assertNotNull(sel)
        // End should be clamped to line length (11)
        assertEquals(EditorPosition(0, 11), sel.end)
    }

    @Test
    fun testClearSelection() {
        val state = EditorState("hello world")
        state.setSelection(EditorRange(EditorPosition(0, 0), EditorPosition(0, 5)))
        assertTrue(state.hasSelection)

        state.clearSelection()
        assertFalse(state.hasSelection)
        assertNull(state.selection.value)
    }

    @Test
    fun testMoveCaretClearsSelection() {
        val state = EditorState("hello world")
        state.setSelection(EditorRange(EditorPosition(0, 0), EditorPosition(0, 5)))
        assertTrue(state.hasSelection)

        state.moveCaret(EditorPosition(0, 3))
        assertFalse(state.hasSelection)
    }

    @Test
    fun testMoveCaretWithExtendSelectionKeepsSelection() {
        val state = EditorState("hello world")
        state.moveCaret(EditorPosition(0, 0))
        state.moveCaret(EditorPosition(0, 5), extendSelection = true)

        assertTrue(state.hasSelection)
        assertEquals("hello", state.selectedText)
    }

    @Test
    fun testSelectAll() {
        val state = EditorState("hello\nworld")
        state.selectAll()

        assertTrue(state.hasSelection)
        assertEquals("hello\nworld", state.selectedText)
    }

    @Test
    fun testSelectAllEmptyDocument() {
        val state = EditorState()
        state.selectAll()

        // Empty document - selection exists but is empty
        val sel = state.selection.value
        assertNotNull(sel)
        assertTrue(sel.isEmpty)
    }

    @Test
    fun testSelectWord() {
        val state = EditorState("hello world")
        state.moveCaret(EditorPosition(0, 2)) // Inside "hello"
        state.selectWord()

        assertTrue(state.hasSelection)
        assertEquals("hello", state.selectedText)
    }

    @Test
    fun testSelectWordAtBoundary() {
        val state = EditorState("hello_world test")
        state.moveCaret(EditorPosition(0, 7)) // Inside "hello_world"
        state.selectWord()

        // Underscore is a word character
        assertEquals("hello_world", state.selectedText)
    }

    @Test
    fun testSelectWordOnWhitespace() {
        val state = EditorState("hello   world")
        state.moveCaret(EditorPosition(0, 6)) // On whitespace
        state.selectWord()

        // No word at whitespace
        assertFalse(state.hasSelection)
    }

    @Test
    fun testSelectLine() {
        val state = EditorState("line1\nline2\nline3")
        state.moveCaret(EditorPosition(1, 2))
        state.selectLine()

        assertTrue(state.hasSelection)
        // selectLine includes newline if not last line
        assertEquals("line2\n", state.selectedText)
    }

    @Test
    fun testSelectLineLastLine() {
        val state = EditorState("line1\nline2")
        state.moveCaret(EditorPosition(1, 2))
        state.selectLine()

        // Last line doesn't have trailing newline
        assertEquals("line2", state.selectedText)
    }

    @Test
    fun testSelectedTextNoSelection() {
        val state = EditorState("hello world")
        assertEquals("", state.selectedText)
    }

    // ============================================
    // Text Editing Tests
    // ============================================

    @Test
    fun testInsertTextAtCaret() {
        val state = EditorState("helloworld")
        state.moveCaret(EditorPosition(0, 5))
        state.insertText(" ")

        assertEquals("hello world", state.document.getText())
        assertEquals(EditorPosition(0, 6), state.caretPosition.value)
    }

    @Test
    fun testInsertTextReplacesSelection() {
        val state = EditorState("hello world")
        state.setSelection(EditorRange(EditorPosition(0, 6), EditorPosition(0, 11)))
        state.insertText("kotlin")

        assertEquals("hello kotlin", state.document.getText())
        assertFalse(state.hasSelection)
    }

    @Test
    fun testDeleteBackward() {
        val state = EditorState("hello")
        state.moveCaret(EditorPosition(0, 5))
        state.deleteBackward()

        assertEquals("hell", state.document.getText())
        assertEquals(EditorPosition(0, 4), state.caretPosition.value)
    }

    @Test
    fun testDeleteBackwardAtStart() {
        val state = EditorState("hello")
        state.moveCaret(EditorPosition(0, 0))
        state.deleteBackward()

        // Should do nothing
        assertEquals("hello", state.document.getText())
    }

    @Test
    fun testDeleteBackwardWithSelection() {
        val state = EditorState("hello world")
        state.setSelection(EditorRange(EditorPosition(0, 0), EditorPosition(0, 6)))
        state.deleteBackward()

        assertEquals("world", state.document.getText())
        assertFalse(state.hasSelection)
    }

    @Test
    fun testDeleteForward() {
        val state = EditorState("hello")
        state.moveCaret(EditorPosition(0, 0))
        state.deleteForward()

        assertEquals("ello", state.document.getText())
    }

    @Test
    fun testDeleteForwardAtEnd() {
        val state = EditorState("hello")
        state.moveCaret(EditorPosition(0, 5))
        state.deleteForward()

        // Should do nothing
        assertEquals("hello", state.document.getText())
    }

    @Test
    fun testDeleteForwardWithSelection() {
        val state = EditorState("hello world")
        state.setSelection(EditorRange(EditorPosition(0, 5), EditorPosition(0, 11)))
        state.deleteForward()

        assertEquals("hello", state.document.getText())
    }

    @Test
    fun testDeleteSelection() {
        val state = EditorState("hello world")
        state.setSelection(EditorRange(EditorPosition(0, 5), EditorPosition(0, 11)))
        state.deleteSelection()

        assertEquals("hello", state.document.getText())
        assertEquals(EditorPosition(0, 5), state.caretPosition.value)
        assertFalse(state.hasSelection)
    }

    @Test
    fun testDeleteSelectionNoOp() {
        val state = EditorState("hello")
        state.deleteSelection() // No selection

        assertEquals("hello", state.document.getText())
    }

    // ============================================
    // Modification State Tests
    // ============================================

    @Test
    fun testIsModifiedAfterInsert() {
        val state = EditorState("hello")
        assertFalse(state.isModified.value)

        state.insertText(" world")
        assertTrue(state.isModified.value)
    }

    @Test
    fun testIsModifiedAfterDelete() {
        val state = EditorState("hello")
        state.moveCaret(EditorPosition(0, 5))
        state.deleteBackward()

        assertTrue(state.isModified.value)
    }

    @Test
    fun testMarkAsSavedClearsModified() {
        val state = EditorState("hello")
        state.insertText(" world")
        assertTrue(state.isModified.value)

        state.markAsSaved()
        assertFalse(state.isModified.value)
    }

    @Test
    fun testSetTextResetsModified() {
        val state = EditorState("hello")
        state.insertText(" world")
        assertTrue(state.isModified.value)

        state.setText("new content")
        assertFalse(state.isModified.value)
    }

    @Test
    fun testSetTextResetsCaret() {
        val state = EditorState("hello")
        state.moveCaret(EditorPosition(0, 5))

        state.setText("new")
        assertEquals(EditorPosition.ZERO, state.caretPosition.value)
    }

    @Test
    fun testSetTextClearsSelection() {
        val state = EditorState("hello")
        state.selectAll()
        assertTrue(state.hasSelection)

        state.setText("new")
        assertFalse(state.hasSelection)
    }

    @Test
    fun testSetTextClearsUndoHistory() {
        val state = EditorState("hello")
        state.insertText(" world")
        state.undoManager.breakUndoGroup()
        assertTrue(state.undoManager.canUndo)

        state.setText("new")
        assertFalse(state.undoManager.canUndo)
    }

    // ============================================
    // Undo/Redo Tests
    // ============================================

    @Test
    fun testUndoForwardsToUndoManager() {
        val state = EditorState("hello")
        state.moveCaretToEnd() // Position at end before inserting
        state.insertText(" world")
        state.undoManager.breakUndoGroup()

        assertTrue(state.undo())
        assertEquals("hello", state.document.getText())
    }

    @Test
    fun testRedoForwardsToUndoManager() {
        val state = EditorState("hello")
        state.moveCaretToEnd() // Position at end before inserting
        state.insertText(" world")
        state.undoManager.breakUndoGroup()
        state.undo()

        assertTrue(state.redo())
        assertEquals("hello world", state.document.getText())
    }

    @Test
    fun testUndoReturnsFalseWhenEmpty() {
        val state = EditorState("hello")
        assertFalse(state.undo())
    }

    @Test
    fun testRedoReturnsFalseWhenEmpty() {
        val state = EditorState("hello")
        assertFalse(state.redo())
    }

    // ============================================
    // Caret Adjustment After Change Tests
    // ============================================

    @Test
    fun testCaretAdjustedAfterInsertBefore() {
        val state = EditorState("world")
        state.moveCaret(EditorPosition(0, 3)) // wo|rld

        // Insert before caret
        state.document.insert(0, "hello ")

        // Caret should move by insert length
        assertEquals(EditorPosition(0, 9), state.caretPosition.value) // hello wo|rld
    }

    @Test
    fun testCaretAdjustedAfterDeleteBefore() {
        val state = EditorState("hello world")
        // Position caret at offset 6 (the space after "hello")
        // Using a position that remains valid after the delete
        state.moveCaret(EditorPosition(0, 6)) // hello |world

        // Delete "hel" from start
        state.document.delete(0, 3) // Delete "hel" -> "lo world"

        // Caret should adjust: was at offset 6, delete 3 chars before it
        // New offset = 6 - 3 = 3 (the space in "lo world")
        assertEquals(EditorPosition(0, 3), state.caretPosition.value)
    }

    @Test
    fun testCaretAdjustedWhenInsideDeletedRange() {
        val state = EditorState("hello world")
        state.moveCaret(EditorPosition(0, 7)) // hello w|orld

        // Delete range containing caret
        state.document.delete(5, 11)

        // Caret should move to start of deleted range
        assertEquals(EditorPosition(0, 5), state.caretPosition.value)
    }

    // ============================================
    // State Listener Tests
    // ============================================

    @Test
    fun testCaretMovedListenerCalled() {
        val state = EditorState("hello")
        var movedTo: EditorPosition? = null

        state.addStateListener(object : EditorStateListener {
            override fun caretMoved(position: EditorPosition) {
                movedTo = position
            }
        })

        state.moveCaret(EditorPosition(0, 3))
        assertEquals(EditorPosition(0, 3), movedTo)
    }

    @Test
    fun testSelectionChangedListenerCalled() {
        val state = EditorState("hello")
        var selectionChanged = false

        state.addStateListener(object : EditorStateListener {
            override fun selectionChanged(selection: EditorRange?) {
                selectionChanged = true
            }
        })

        state.setSelection(EditorRange(EditorPosition(0, 0), EditorPosition(0, 3)))
        assertTrue(selectionChanged)
    }

    @Test
    fun testRemoveStateListener() {
        val state = EditorState("hello")
        var callCount = 0

        val listener = object : EditorStateListener {
            override fun caretMoved(position: EditorPosition) {
                callCount++
            }
        }

        state.addStateListener(listener)
        state.moveCaret(EditorPosition(0, 1))
        assertEquals(1, callCount)

        state.removeStateListener(listener)
        state.moveCaret(EditorPosition(0, 2))
        assertEquals(1, callCount) // Should not increase
    }

    // ============================================
    // Edge Cases
    // ============================================

    @Test
    fun testCaretOffsetEmptyDocument() {
        val state = EditorState()
        assertEquals(0, state.caretOffset)
    }

    @Test
    fun testMultiLineCaretNavigation() {
        val state = EditorState("line1\nline2\nline3")

        state.moveCaretToEnd()
        assertEquals(EditorPosition(2, 5), state.caretPosition.value)

        state.moveCaretToStart()
        assertEquals(EditorPosition.ZERO, state.caretPosition.value)

        state.moveCaret(EditorPosition(1, 0))
        state.moveCaretToLineEnd()
        assertEquals(EditorPosition(1, 5), state.caretPosition.value)
    }

    @Test
    fun testInsertNewlineUpdatesCaret() {
        val state = EditorState("hello")
        state.moveCaret(EditorPosition(0, 5))
        state.insertText("\nworld")

        assertEquals(EditorPosition(1, 5), state.caretPosition.value)
        assertEquals("hello\nworld", state.document.getText())
    }

    @Test
    fun testSelectWordEmptyDocument() {
        val state = EditorState()
        state.selectWord()

        // Should not crash, no selection
        assertFalse(state.hasSelection)
    }

    @Test
    fun testFilePathStored() {
        val state = EditorState("content", filePath = "/path/to/file.kt")
        assertEquals("/path/to/file.kt", state.filePath)
    }

    @Test
    fun testSetTextRejectsOversizedContent() {
        val state = EditorState()

        // Create text that exceeds the 100MB limit (100MB / 2 bytes per char = 50M chars)
        // We use a smaller test value to avoid OOM in tests: ~60MB worth
        val oversizedText = "x".repeat(60_000_000) // 60M chars = ~120MB in JVM

        val exception = assertFailsWith<IllegalArgumentException> {
            state.setText(oversizedText)
        }

        assertTrue(exception.message?.contains("too large") == true)
        assertTrue(exception.message?.contains("exceeds maximum") == true)
    }
}
