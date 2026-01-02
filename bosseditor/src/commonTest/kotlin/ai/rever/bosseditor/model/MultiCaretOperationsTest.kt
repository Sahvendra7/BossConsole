package ai.rever.bosseditor.model

import ai.rever.bosseditor.core.EditorDocument
import ai.rever.bosseditor.core.EditorPosition
import ai.rever.bosseditor.core.EditorRange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MultiCaretOperationsTest {

    private fun createSetup(text: String): Triple<EditorDocument, MultiCaretModel, MultiCaretOperations> {
        val doc = EditorDocument(text)
        val model = MultiCaretModel(doc)
        val ops = MultiCaretOperations(doc, model)
        return Triple(doc, model, ops)
    }

    @Test
    fun testInsertAtSingleCaret() {
        val (doc, model, ops) = createSetup("hello")

        model.setSingleCaret(EditorPosition(0, 5))
        ops.insertAtAllCarets(" world")

        assertEquals("hello world", doc.getText(0, doc.length))
        assertEquals(EditorPosition(0, 11), model.primaryCaret.position)
    }

    @Test
    fun testInsertAtMultipleCarets() {
        val (doc, model, ops) = createSetup("line1\nline2\nline3")

        model.setSingleCaret(EditorPosition(0, 5))
        model.addCaret(EditorPosition(1, 5))
        model.addCaret(EditorPosition(2, 5))

        ops.insertAtAllCarets("X")

        assertEquals("line1X\nline2X\nline3X", doc.getText(0, doc.length))
    }

    @Test
    fun testInsertWithSelection() {
        val (doc, model, ops) = createSetup("hello world")

        // Select "world"
        val selection = EditorRange(EditorPosition(0, 6), EditorPosition(0, 11))
        model.setSingleCaret(EditorPosition(0, 11))
        model.updatePrimaryCaret(EditorPosition(0, 11), selection)

        ops.insertAtAllCarets("universe")

        assertEquals("hello universe", doc.getText(0, doc.length))
    }

    @Test
    fun testBackspaceAtSingleCaret() {
        val (doc, model, ops) = createSetup("hello")

        model.setSingleCaret(EditorPosition(0, 5))
        ops.backspaceAtAllCarets()

        assertEquals("hell", doc.getText(0, doc.length))
        assertEquals(EditorPosition(0, 4), model.primaryCaret.position)
    }

    @Test
    fun testBackspaceAtMultipleCarets() {
        val (doc, model, ops) = createSetup("abc\nabc\nabc")

        model.setSingleCaret(EditorPosition(0, 3))
        model.addCaret(EditorPosition(1, 3))
        model.addCaret(EditorPosition(2, 3))

        ops.backspaceAtAllCarets()

        assertEquals("ab\nab\nab", doc.getText(0, doc.length))
    }

    @Test
    fun testBackspaceWithSelection() {
        val (doc, model, ops) = createSetup("hello world")

        val selection = EditorRange(EditorPosition(0, 0), EditorPosition(0, 6))
        model.setSingleCaret(EditorPosition(0, 6))
        model.updatePrimaryCaret(EditorPosition(0, 6), selection)

        ops.backspaceAtAllCarets()

        assertEquals("world", doc.getText(0, doc.length))
    }

    @Test
    fun testDeleteAtSingleCaret() {
        val (doc, model, ops) = createSetup("hello")

        model.setSingleCaret(EditorPosition(0, 0))
        ops.deleteAtAllCarets()

        assertEquals("ello", doc.getText(0, doc.length))
        assertEquals(EditorPosition(0, 0), model.primaryCaret.position)
    }

    @Test
    fun testDeleteAtMultipleCarets() {
        val (doc, model, ops) = createSetup("abc\nabc\nabc")

        model.setSingleCaret(EditorPosition(0, 0))
        model.addCaret(EditorPosition(1, 0))
        model.addCaret(EditorPosition(2, 0))

        ops.deleteAtAllCarets()

        assertEquals("bc\nbc\nbc", doc.getText(0, doc.length))
    }

    @Test
    fun testDeleteWordBefore() {
        val (doc, model, ops) = createSetup("hello world")

        model.setSingleCaret(EditorPosition(0, 11))
        ops.deleteWordBeforeAllCarets()

        assertEquals("hello ", doc.getText(0, doc.length))
    }

    @Test
    fun testDeleteWordAfter() {
        val (doc, model, ops) = createSetup("hello world")

        model.setSingleCaret(EditorPosition(0, 0))
        ops.deleteWordAfterAllCarets()

        assertEquals(" world", doc.getText(0, doc.length))
    }

    @Test
    fun testGetSelectedTexts() {
        val (doc, model, ops) = createSetup("hello world foo bar")

        // Add selections for "hello" and "foo"
        model.setSingleCaret(EditorPosition(0, 5))
        model.updatePrimaryCaret(
            EditorPosition(0, 5),
            EditorRange(EditorPosition(0, 0), EditorPosition(0, 5))
        )
        model.addCaretWithSelection(
            EditorPosition(0, 15),
            EditorRange(EditorPosition(0, 12), EditorPosition(0, 15))
        )

        val texts = ops.getSelectedTexts()

        assertEquals(2, texts.size)
        assertTrue("hello" in texts)
        assertTrue("foo" in texts)
    }

    @Test
    fun testGetCombinedSelectedText() {
        val (doc, model, ops) = createSetup("line1\nline2")

        model.setSingleCaret(EditorPosition(0, 5))
        model.updatePrimaryCaret(
            EditorPosition(0, 5),
            EditorRange(EditorPosition(0, 0), EditorPosition(0, 5))
        )
        model.addCaretWithSelection(
            EditorPosition(1, 5),
            EditorRange(EditorPosition(1, 0), EditorPosition(1, 5))
        )

        val combined = ops.getCombinedSelectedText()

        assertEquals("line1\nline2", combined)
    }

    @Test
    fun testDeleteAllSelections() {
        val (doc, model, ops) = createSetup("hello beautiful world")

        // Select "beautiful "
        model.setSingleCaret(EditorPosition(0, 16))
        model.updatePrimaryCaret(
            EditorPosition(0, 16),
            EditorRange(EditorPosition(0, 6), EditorPosition(0, 16))
        )

        ops.deleteAllSelections()

        assertEquals("hello world", doc.getText(0, doc.length))
    }

    @Test
    fun testInsertDifferentAtCarets() {
        val (doc, model, ops) = createSetup("a\nb\nc")

        model.setSingleCaret(EditorPosition(0, 1))
        model.addCaret(EditorPosition(1, 1))
        model.addCaret(EditorPosition(2, 1))

        ops.insertDifferentAtCarets(listOf("1", "2", "3"))

        assertEquals("a1\nb2\nc3", doc.getText(0, doc.length))
    }

    @Test
    fun testAddCaretAbove() {
        val (doc, model, ops) = createSetup("line1\nline2\nline3")

        model.setSingleCaret(EditorPosition(2, 3))
        ops.addCaretAbove()

        assertEquals(2, model.caretCount)
        val positions = model.allPositions
        assertTrue(positions.any { it.line == 1 && it.column == 3 })
        assertTrue(positions.any { it.line == 2 && it.column == 3 })
    }

    @Test
    fun testAddCaretBelow() {
        val (doc, model, ops) = createSetup("line1\nline2\nline3")

        model.setSingleCaret(EditorPosition(0, 3))
        ops.addCaretBelow()

        assertEquals(2, model.caretCount)
        val positions = model.allPositions
        assertTrue(positions.any { it.line == 0 && it.column == 3 })
        assertTrue(positions.any { it.line == 1 && it.column == 3 })
    }

    @Test
    fun testAddCaretAboveAtFirstLine() {
        val (doc, model, ops) = createSetup("line1\nline2")

        model.setSingleCaret(EditorPosition(0, 0))
        ops.addCaretAbove()

        // Should not add caret above first line
        assertEquals(1, model.caretCount)
    }

    @Test
    fun testAddCaretBelowAtLastLine() {
        val (doc, model, ops) = createSetup("line1\nline2")

        model.setSingleCaret(EditorPosition(1, 0))
        ops.addCaretBelow()

        // Should not add caret below last line
        assertEquals(1, model.caretCount)
    }

    @Test
    fun testSelectNextOccurrence() {
        val (doc, model, ops) = createSetup("foo bar foo baz foo")

        // Select first "foo"
        model.setSingleCaret(EditorPosition(0, 3))
        model.updatePrimaryCaret(
            EditorPosition(0, 3),
            EditorRange(EditorPosition(0, 0), EditorPosition(0, 3))
        )

        ops.selectNextOccurrence()

        assertEquals(2, model.caretCount)
    }

    @Test
    fun testSelectNextOccurrenceWraps() {
        val (doc, model, ops) = createSetup("foo bar foo")

        // Position caret after last "foo"
        model.setSingleCaret(EditorPosition(0, 11))
        model.updatePrimaryCaret(
            EditorPosition(0, 11),
            EditorRange(EditorPosition(0, 8), EditorPosition(0, 11))
        )

        ops.selectNextOccurrence()

        // Should wrap to first "foo"
        assertEquals(2, model.caretCount)
    }

    @Test
    fun testSelectAllOccurrences() {
        val (doc, model, ops) = createSetup("foo bar foo baz foo")

        // Select first "foo"
        model.setSingleCaret(EditorPosition(0, 3))
        model.updatePrimaryCaret(
            EditorPosition(0, 3),
            EditorRange(EditorPosition(0, 0), EditorPosition(0, 3))
        )

        ops.selectAllOccurrences()

        assertEquals(3, model.caretCount)

        // All should have "foo" selected
        val selections = model.allSelections
        assertEquals(3, selections.size)
    }

    @Test
    fun testOperationsInReverseOrder() {
        // Verify that operations process carets in reverse order
        // to avoid offset invalidation
        val (doc, model, ops) = createSetup("AAA\nBBB\nCCC")

        model.setSingleCaret(EditorPosition(0, 0))
        model.addCaret(EditorPosition(1, 0))
        model.addCaret(EditorPosition(2, 0))

        ops.insertAtAllCarets("X")

        // All lines should have X at start
        assertEquals("XAAA\nXBBB\nXCCC", doc.getText(0, doc.length))
    }

    @Test
    fun testBackspaceAtStartOfLine() {
        val (doc, model, ops) = createSetup("hello")

        model.setSingleCaret(EditorPosition(0, 0))
        ops.backspaceAtAllCarets()

        // Nothing should happen at start
        assertEquals("hello", doc.getText(0, doc.length))
        assertEquals(EditorPosition(0, 0), model.primaryCaret.position)
    }

    @Test
    fun testDeleteAtEndOfDocument() {
        val (doc, model, ops) = createSetup("hello")

        model.setSingleCaret(EditorPosition(0, 5))
        ops.deleteAtAllCarets()

        // Nothing should happen at end
        assertEquals("hello", doc.getText(0, doc.length))
    }

    @Test
    fun testEmptyInsert() {
        val (doc, model, ops) = createSetup("hello")

        model.setSingleCaret(EditorPosition(0, 0))
        ops.insertAtAllCarets("")

        assertEquals("hello", doc.getText(0, doc.length))
    }

    @Test
    fun testBlockSelectionInsert() {
        val (doc, model, ops) = createSetup("line1\nline2\nline3")

        // Create block selection from column 0-3 on all lines
        model.createBlockSelection(0, 2, 0, 3)

        ops.insertAtAllCarets("X")

        // Each line should have "lineX" plus the rest
        val result = doc.getText(0, doc.length)
        assertTrue(result.contains("X"))
    }
}
