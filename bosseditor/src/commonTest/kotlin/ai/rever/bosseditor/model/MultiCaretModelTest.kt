package ai.rever.bosseditor.model

import ai.rever.bosseditor.core.EditorDocument
import ai.rever.bosseditor.core.EditorPosition
import ai.rever.bosseditor.core.EditorRange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MultiCaretModelTest {

    @Test
    fun testInitialState() {
        val doc = EditorDocument("hello world")
        val model = MultiCaretModel(doc)

        assertEquals(1, model.caretCount)
        assertFalse(model.hasMultipleCarets)
        assertEquals(EditorPosition.ZERO, model.primaryCaret.position)
    }

    @Test
    fun testSetSingleCaret() {
        val doc = EditorDocument("hello world")
        val model = MultiCaretModel(doc)

        model.setSingleCaret(EditorPosition(0, 5))

        assertEquals(1, model.caretCount)
        assertEquals(EditorPosition(0, 5), model.primaryCaret.position)
    }

    @Test
    fun testAddCaret() {
        val doc = EditorDocument("line1\nline2\nline3")
        val model = MultiCaretModel(doc)

        model.setSingleCaret(EditorPosition(0, 0))
        val added = model.addCaret(EditorPosition(1, 0))

        assertTrue(added)
        assertEquals(2, model.caretCount)
        assertTrue(model.hasMultipleCarets)
    }

    @Test
    fun testAddCaretToggle() {
        val doc = EditorDocument("hello world")
        val model = MultiCaretModel(doc)

        model.setSingleCaret(EditorPosition(0, 0))
        model.addCaret(EditorPosition(0, 5))
        assertEquals(2, model.caretCount)

        // Adding at same position should toggle (remove)
        val removed = model.addCaret(EditorPosition(0, 5))
        assertFalse(removed)
        assertEquals(1, model.caretCount)
    }

    @Test
    fun testAddCaretDoesNotRemoveLastCaret() {
        val doc = EditorDocument("hello")
        val model = MultiCaretModel(doc)

        model.setSingleCaret(EditorPosition(0, 0))

        // Trying to toggle the only caret should not remove it
        model.addCaret(EditorPosition(0, 0))
        assertEquals(1, model.caretCount)
    }

    @Test
    fun testClearSecondaryCarets() {
        val doc = EditorDocument("line1\nline2\nline3")
        val model = MultiCaretModel(doc)

        model.setSingleCaret(EditorPosition(0, 0))
        model.addCaret(EditorPosition(1, 0))
        model.addCaret(EditorPosition(2, 0))
        assertEquals(3, model.caretCount)

        model.clearSecondaryCarets()
        assertEquals(1, model.caretCount)
    }

    @Test
    fun testMoveAllCarets() {
        val doc = EditorDocument("line1\nline2\nline3")
        val model = MultiCaretModel(doc)

        model.setSingleCaret(EditorPosition(0, 0))
        model.addCaret(EditorPosition(1, 0))
        model.addCaret(EditorPosition(2, 0))

        // Move all carets right by 2
        model.moveAllCarets { caret ->
            EditorPosition(caret.position.line, caret.position.column + 2)
        }

        val positions = model.allPositions
        assertEquals(3, positions.size)
        assertTrue(positions.all { it.column == 2 })
    }

    @Test
    fun testCreateBlockSelection() {
        val doc = EditorDocument("line1\nline2\nline3\nline4\nline5")
        val model = MultiCaretModel(doc)

        model.createBlockSelection(
            startLine = 1,
            endLine = 3,
            startColumn = 1,
            endColumn = 4
        )

        assertEquals(3, model.caretCount)

        val selections = model.allSelections
        assertEquals(3, selections.size)

        // Each selection should span columns 1-4
        selections.forEach { sel ->
            assertEquals(1, sel.start.column)
            assertEquals(4, sel.end.column)
        }
    }

    @Test
    fun testBlockSelectionWithShortLines() {
        val doc = EditorDocument("long line here\nab\nmedium line")
        val model = MultiCaretModel(doc)

        model.createBlockSelection(
            startLine = 0,
            endLine = 2,
            startColumn = 0,
            endColumn = 10
        )

        assertEquals(3, model.caretCount)

        val selections = model.allSelections
        // Line 1 ("ab") should have selection 0-2 (clamped)
        val line1Selection = selections.find { it.start.line == 1 }
        assertTrue(line1Selection == null || line1Selection.end.column <= 2)
    }

    @Test
    fun testCaretWithSelection() {
        val doc = EditorDocument("hello world")
        val model = MultiCaretModel(doc)

        val selection = EditorRange(EditorPosition(0, 0), EditorPosition(0, 5))
        model.setSingleCaret(EditorPosition(0, 5))
        model.updatePrimaryCaret(EditorPosition(0, 5), selection)

        assertTrue(model.primaryCaret.hasSelection)
        assertEquals(EditorPosition(0, 0), model.primaryCaret.selection?.start)
        assertEquals(EditorPosition(0, 5), model.primaryCaret.selection?.end)
    }

    @Test
    fun testExtendSelectionForAll() {
        val doc = EditorDocument("line1\nline2\nline3")
        val model = MultiCaretModel(doc)

        model.setSingleCaret(EditorPosition(0, 0))
        model.addCaret(EditorPosition(1, 0))
        model.addCaret(EditorPosition(2, 0))

        model.startSelectionForAll()
        model.extendSelectionForAll { caret ->
            EditorPosition(caret.position.line, 3)
        }

        val selections = model.allSelections
        assertEquals(3, selections.size)
        selections.forEach { sel ->
            assertEquals(0, sel.start.column)
            assertEquals(3, sel.end.column)
        }
    }

    @Test
    fun testClearAllSelections() {
        val doc = EditorDocument("hello world")
        val model = MultiCaretModel(doc)

        val selection = EditorRange(EditorPosition(0, 0), EditorPosition(0, 5))
        model.setSingleCaret(EditorPosition(0, 5))
        model.updatePrimaryCaret(EditorPosition(0, 5), selection)
        assertTrue(model.primaryCaret.hasSelection)

        model.clearAllSelections()
        assertFalse(model.primaryCaret.hasSelection)
    }

    @Test
    fun testGetAllCaretOffsets() {
        val doc = EditorDocument("line1\nline2\nline3")
        val model = MultiCaretModel(doc)

        model.setSingleCaret(EditorPosition(0, 0))  // offset 0
        model.addCaret(EditorPosition(1, 0))  // offset 6
        model.addCaret(EditorPosition(2, 0))  // offset 12

        val offsets = model.getAllCaretOffsets()
        assertEquals(3, offsets.size)
        assertTrue(0 in offsets)
        assertTrue(6 in offsets)
        assertTrue(12 in offsets)
    }

    @Test
    fun testCaretsSortedByPosition() {
        val doc = EditorDocument("line1\nline2\nline3")
        val model = MultiCaretModel(doc)

        // Add in reverse order
        model.setSingleCaret(EditorPosition(2, 0))
        model.addCaret(EditorPosition(0, 0))
        model.addCaret(EditorPosition(1, 0))

        val positions = model.allPositions
        assertEquals(EditorPosition(0, 0), positions[0])
        assertEquals(EditorPosition(1, 0), positions[1])
        assertEquals(EditorPosition(2, 0), positions[2])
    }

    @Test
    fun testMergeOverlappingCarets() {
        val doc = EditorDocument("hello world")
        val model = MultiCaretModel(doc)

        model.setSingleCaret(EditorPosition(0, 5))
        model.addCaret(EditorPosition(0, 5))  // Same position

        // Should merge to single caret
        assertEquals(1, model.caretCount)
    }

    @Test
    fun testSetCaretsFromPositions() {
        val doc = EditorDocument("line1\nline2\nline3")
        val model = MultiCaretModel(doc)

        val positions = listOf(
            EditorPosition(0, 2),
            EditorPosition(1, 3),
            EditorPosition(2, 1)
        )

        model.setCaretsFromPositions(positions)

        assertEquals(3, model.caretCount)
        assertEquals(positions.sorted(), model.allPositions.sorted())
    }

    @Test
    fun testUpdatePrimaryCaret() {
        val doc = EditorDocument("hello world")
        val model = MultiCaretModel(doc)

        model.setSingleCaret(EditorPosition(0, 0))
        model.updatePrimaryCaret(EditorPosition(0, 5))

        assertEquals(EditorPosition(0, 5), model.primaryCaret.position)
    }

    @Test
    fun testBlockSelectionData() {
        val block = BlockSelection(
            startLine = 1,
            endLine = 5,
            startColumn = 3,
            endColumn = 10
        )

        assertEquals(5, block.lineCount)
        assertEquals(7, block.width)

        val normalized = block.normalize()
        assertEquals(1, normalized.startLine)
        assertEquals(5, normalized.endLine)
        assertEquals(3, normalized.startColumn)
        assertEquals(10, normalized.endColumn)
    }

    @Test
    fun testBlockSelectionReverseNormalize() {
        val block = BlockSelection(
            startLine = 5,
            endLine = 1,
            startColumn = 10,
            endColumn = 3
        )

        val normalized = block.normalize()
        assertEquals(1, normalized.startLine)
        assertEquals(5, normalized.endLine)
        assertEquals(3, normalized.startColumn)
        assertEquals(10, normalized.endColumn)
    }

    @Test
    fun testBlockSelectionGetRangeForLine() {
        val block = BlockSelection(1, 5, 3, 10).normalize()

        val range = block.getRangeForLine(3, 15)
        assertEquals(3, range?.start)
        assertEquals(10, range?.end)

        // Line outside block
        val outsideRange = block.getRangeForLine(0, 15)
        assertEquals(null, outsideRange)

        // Short line
        val shortRange = block.getRangeForLine(3, 5)
        assertEquals(3, shortRange?.start)
        assertEquals(5, shortRange?.end)
    }

    @Test
    fun testCaretData() {
        val caret = Caret(
            position = EditorPosition(5, 10),
            selection = EditorRange(EditorPosition(5, 5), EditorPosition(5, 15)),
            id = 42
        )

        assertEquals(EditorPosition(5, 10), caret.position)
        assertTrue(caret.hasSelection)
        assertEquals(42, caret.id)
    }

    @Test
    fun testCaretMoveTo() {
        val caret = Caret(
            position = EditorPosition(0, 0),
            selection = EditorRange(EditorPosition(0, 0), EditorPosition(0, 5))
        )

        val moved = caret.moveTo(EditorPosition(1, 3))

        assertEquals(EditorPosition(1, 3), moved.position)
        assertEquals(null, moved.selection)  // Selection cleared
    }

    @Test
    fun testCaretSelectTo() {
        val caret = Caret(position = EditorPosition(0, 5))

        val selected = caret.selectTo(EditorPosition(0, 0), EditorPosition(0, 10))

        assertEquals(EditorPosition(0, 10), selected.position)
        assertEquals(EditorPosition(0, 0), selected.selection?.start)
        assertEquals(EditorPosition(0, 10), selected.selection?.end)
    }

    @Test
    fun testCaretSelectToReverse() {
        val caret = Caret(position = EditorPosition(0, 5))

        val selected = caret.selectTo(EditorPosition(0, 10), EditorPosition(0, 0))

        assertEquals(EditorPosition(0, 0), selected.position)
        assertEquals(EditorPosition(0, 0), selected.selection?.start)
        assertEquals(EditorPosition(0, 10), selected.selection?.end)
    }

    @Test
    fun testClampPositionToDocument() {
        val doc = EditorDocument("ab")  // 2 chars, 1 line
        val model = MultiCaretModel(doc)

        model.setSingleCaret(EditorPosition(10, 100))

        // Should be clamped to valid position
        val pos = model.primaryCaret.position
        assertEquals(0, pos.line)
        assertTrue(pos.column <= 2)
    }
}
