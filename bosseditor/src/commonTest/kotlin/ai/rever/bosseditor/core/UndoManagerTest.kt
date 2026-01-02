package ai.rever.bosseditor.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UndoManagerTest {

    // ============================================
    // Basic Undo/Redo Tests
    // ============================================

    @Test
    fun testInitialState() {
        val doc = EditorDocument()
        val undoManager = UndoManager(doc)
        assertFalse(undoManager.canUndo)
        assertFalse(undoManager.canRedo)
        assertEquals(0, undoManager.undoCount)
        assertEquals(0, undoManager.redoCount)
    }

    @Test
    fun testSingleUndo() {
        val doc = EditorDocument("hello")
        val undoManager = UndoManager(doc)

        doc.insert(5, " world")
        assertEquals("hello world", doc.getText())

        // Force finalize current group
        undoManager.breakUndoGroup()

        assertTrue(undoManager.canUndo)
        assertTrue(undoManager.undo())
        assertEquals("hello", doc.getText())
        assertFalse(undoManager.canUndo)
    }

    @Test
    fun testSingleRedo() {
        val doc = EditorDocument("hello")
        val undoManager = UndoManager(doc)

        doc.insert(5, " world")
        undoManager.breakUndoGroup()

        undoManager.undo()
        assertEquals("hello", doc.getText())

        assertTrue(undoManager.canRedo)
        assertTrue(undoManager.redo())
        assertEquals("hello world", doc.getText())
        assertFalse(undoManager.canRedo)
    }

    @Test
    fun testMultipleUndos() {
        val doc = EditorDocument()
        val undoManager = UndoManager(doc)

        doc.insert(0, "first")
        undoManager.breakUndoGroup()

        doc.insert(5, " second")
        undoManager.breakUndoGroup()

        doc.insert(12, " third")
        undoManager.breakUndoGroup()

        assertEquals("first second third", doc.getText())
        assertEquals(3, undoManager.undoCount)

        undoManager.undo()
        assertEquals("first second", doc.getText())

        undoManager.undo()
        assertEquals("first", doc.getText())

        undoManager.undo()
        assertEquals("", doc.getText())

        assertFalse(undoManager.canUndo)
        assertEquals(3, undoManager.redoCount)
    }

    @Test
    fun testUndoOnEmptyStackReturnsFalse() {
        val doc = EditorDocument()
        val undoManager = UndoManager(doc)
        assertFalse(undoManager.undo())
    }

    @Test
    fun testRedoOnEmptyStackReturnsFalse() {
        val doc = EditorDocument()
        val undoManager = UndoManager(doc)
        assertFalse(undoManager.redo())
    }

    @Test
    fun testUndoThenRedoRestoresState() {
        val doc = EditorDocument("original")
        val undoManager = UndoManager(doc)

        doc.replace(0, 8, "modified")
        undoManager.breakUndoGroup()

        assertEquals("modified", doc.getText())

        undoManager.undo()
        assertEquals("original", doc.getText())

        undoManager.redo()
        assertEquals("modified", doc.getText())
    }

    @Test
    fun testNewEditClearsRedoStack() {
        val doc = EditorDocument()
        val undoManager = UndoManager(doc)

        doc.insert(0, "first")
        undoManager.breakUndoGroup()

        doc.insert(5, " second")
        undoManager.breakUndoGroup()

        // Undo to create redo stack
        undoManager.undo()
        assertEquals(1, undoManager.redoCount)

        // New edit should clear redo
        doc.insert(5, " new")
        undoManager.breakUndoGroup()

        assertEquals(0, undoManager.redoCount)
        assertFalse(undoManager.canRedo)
    }

    // ============================================
    // Coalescing Tests
    // ============================================

    @Test
    fun testTypingSequenceCoalesces() {
        val doc = EditorDocument()
        val undoManager = UndoManager(doc)

        // Simulate typing "hello" character by character
        doc.insert(0, "h")
        doc.insert(1, "e")
        doc.insert(2, "l")
        doc.insert(3, "l")
        doc.insert(4, "o")

        undoManager.breakUndoGroup()

        assertEquals("hello", doc.getText())
        // All characters should be in one undo group
        assertEquals(1, undoManager.undoCount)

        undoManager.undo()
        assertEquals("", doc.getText())
    }

    @Test
    fun testBackspaceSequenceCoalesces() {
        val doc = EditorDocument("hello")
        val undoManager = UndoManager(doc)

        // Simulate backspace from end
        doc.delete(4, 5) // delete 'o'
        doc.delete(3, 4) // delete 'l'
        doc.delete(2, 3) // delete 'l'

        undoManager.breakUndoGroup()

        assertEquals("he", doc.getText())
        assertEquals(1, undoManager.undoCount)

        undoManager.undo()
        assertEquals("hello", doc.getText())
    }

    @Test
    fun testDeleteKeySequenceCoalesces() {
        val doc = EditorDocument("hello")
        val undoManager = UndoManager(doc)

        // Simulate delete key from start (deletions at same offset)
        doc.delete(0, 1) // delete 'h'
        doc.delete(0, 1) // delete 'e'
        doc.delete(0, 1) // delete 'l'

        undoManager.breakUndoGroup()

        assertEquals("lo", doc.getText())
        assertEquals(1, undoManager.undoCount)

        undoManager.undo()
        assertEquals("hello", doc.getText())
    }

    @Test
    fun testNewlineBreaksCoalescing() {
        val doc = EditorDocument()
        val undoManager = UndoManager(doc)

        // Type "ab" then newline
        doc.insert(0, "a")
        doc.insert(1, "b")
        doc.insert(2, "\n") // Newline should break coalescing
        doc.insert(3, "c")

        undoManager.breakUndoGroup()

        assertEquals("ab\nc", doc.getText())
        // Should be 3 groups: "ab", newline, "c"
        assertEquals(3, undoManager.undoCount)
    }

    @Test
    fun testWhitespaceBoundaryBreaksCoalescing() {
        val doc = EditorDocument()
        val undoManager = UndoManager(doc)

        // Type "ab" then space then "c"
        doc.insert(0, "a")
        doc.insert(1, "b")
        doc.insert(2, " ") // Whitespace after non-whitespace
        doc.insert(3, "c") // Non-whitespace after whitespace

        undoManager.breakUndoGroup()

        assertEquals("ab c", doc.getText())
        // "ab" coalesces, then " " breaks (whitespace after non-whitespace),
        // then "c" breaks again (non-whitespace after whitespace)
        // So should be 3 groups
        assertEquals(3, undoManager.undoCount)
    }

    @Test
    fun testNonAdjacentEditsDoNotCoalesce() {
        val doc = EditorDocument("hello world")
        val undoManager = UndoManager(doc)

        // Insert at different non-adjacent positions
        doc.insert(0, "X")
        doc.insert(12, "Y") // Not adjacent to previous insert

        undoManager.breakUndoGroup()

        assertEquals("Xhello worldY", doc.getText())
        assertEquals(2, undoManager.undoCount)
    }

    // ============================================
    // Stack Limit Tests
    // ============================================

    @Test
    fun testStackLimitTrimsOldest() {
        val doc = EditorDocument()
        val undoManager = UndoManager(doc, maxUndoCount = 3)

        // Add 5 edits
        for (i in 1..5) {
            doc.insert(doc.length, "$i")
            undoManager.breakUndoGroup()
        }

        assertEquals("12345", doc.getText())
        // Should only have 3 undo groups (oldest 2 trimmed)
        assertEquals(3, undoManager.undoCount)

        // Undo all 3
        undoManager.undo()
        assertEquals("1234", doc.getText())
        undoManager.undo()
        assertEquals("123", doc.getText())
        undoManager.undo()
        assertEquals("12", doc.getText())

        // Can't undo further - oldest edits were trimmed
        assertFalse(undoManager.canUndo)
    }

    // ============================================
    // Compound Edit Tests
    // ============================================

    @Test
    fun testCompoundEditGroupsOperations() {
        val doc = EditorDocument()
        val undoManager = UndoManager(doc)

        undoManager.beginCompoundEdit()
        doc.insert(0, "hello")
        doc.insert(5, " ")
        doc.insert(6, "world")
        undoManager.endCompoundEdit()

        assertEquals("hello world", doc.getText())
        assertEquals(1, undoManager.undoCount)

        undoManager.undo()
        assertEquals("", doc.getText())
    }

    @Test
    fun testNestedCompoundEdits() {
        val doc = EditorDocument()
        val undoManager = UndoManager(doc)

        undoManager.beginCompoundEdit()
        doc.insert(0, "A")

        undoManager.beginCompoundEdit() // Nested
        doc.insert(1, "B")
        undoManager.endCompoundEdit()

        doc.insert(2, "C")
        undoManager.endCompoundEdit()

        assertEquals("ABC", doc.getText())
        assertEquals(1, undoManager.undoCount) // All in one group

        undoManager.undo()
        assertEquals("", doc.getText())
    }

    @Test
    fun testEmptyCompoundEditDoesNotCreateGroup() {
        val doc = EditorDocument()
        val undoManager = UndoManager(doc)

        undoManager.beginCompoundEdit()
        // No edits
        undoManager.endCompoundEdit()

        assertEquals(0, undoManager.undoCount)
    }

    @Test
    fun testCompoundEditUndoRedoPreservesState() {
        val doc = EditorDocument("start")
        val undoManager = UndoManager(doc)

        undoManager.beginCompoundEdit()
        doc.delete(0, 5)
        doc.insert(0, "end")
        undoManager.endCompoundEdit()

        assertEquals("end", doc.getText())

        undoManager.undo()
        assertEquals("start", doc.getText())

        undoManager.redo()
        assertEquals("end", doc.getText())
    }

    // ============================================
    // Clear Tests
    // ============================================

    @Test
    fun testClearClearsAllStacks() {
        val doc = EditorDocument()
        val undoManager = UndoManager(doc)

        doc.insert(0, "hello")
        undoManager.breakUndoGroup()
        undoManager.undo()

        assertTrue(undoManager.canRedo)
        assertEquals(1, undoManager.redoCount)

        undoManager.clear()

        assertFalse(undoManager.canUndo)
        assertFalse(undoManager.canRedo)
        assertEquals(0, undoManager.undoCount)
        assertEquals(0, undoManager.redoCount)
    }

    // ============================================
    // Replace Operation Tests
    // ============================================

    @Test
    fun testReplaceOperationUndo() {
        val doc = EditorDocument("hello world")
        val undoManager = UndoManager(doc)

        doc.replace(0, 5, "goodbye")
        undoManager.breakUndoGroup()

        assertEquals("goodbye world", doc.getText())

        undoManager.undo()
        assertEquals("hello world", doc.getText())
    }

    @Test
    fun testReplaceOperationRedo() {
        val doc = EditorDocument("hello world")
        val undoManager = UndoManager(doc)

        doc.replace(6, 11, "universe")
        undoManager.breakUndoGroup()

        undoManager.undo()
        assertEquals("hello world", doc.getText())

        undoManager.redo()
        assertEquals("hello universe", doc.getText())
    }

    // ============================================
    // Edge Cases
    // ============================================

    @Test
    fun testBreakUndoGroupWithNoCurrentGroup() {
        val doc = EditorDocument()
        val undoManager = UndoManager(doc)

        // Should not throw
        undoManager.breakUndoGroup()
        assertEquals(0, undoManager.undoCount)
    }

    @Test
    fun testMultipleBreakUndoGroupCalls() {
        val doc = EditorDocument()
        val undoManager = UndoManager(doc)

        doc.insert(0, "test")
        undoManager.breakUndoGroup()
        undoManager.breakUndoGroup() // Second call should be no-op
        undoManager.breakUndoGroup()

        assertEquals(1, undoManager.undoCount)
    }

    @Test
    fun testUnmatchedEndCompoundEdit() {
        val doc = EditorDocument()
        val undoManager = UndoManager(doc)

        // End without begin should be safe (no-op)
        undoManager.endCompoundEdit()
        assertEquals(0, undoManager.undoCount)
    }

    @Test
    fun testDeleteEntireDocument() {
        val doc = EditorDocument("hello world")
        val undoManager = UndoManager(doc)

        doc.delete(0, 11)
        undoManager.breakUndoGroup()

        assertEquals("", doc.getText())

        undoManager.undo()
        assertEquals("hello world", doc.getText())
    }
}
