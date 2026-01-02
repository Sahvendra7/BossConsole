package ai.rever.bosseditor.features

import ai.rever.bosseditor.core.EditorPosition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class InlayHintsTest {

    @Test
    fun testInlayHintCreation() {
        val hint = InlayHint(
            position = EditorPosition(5, 10),
            text = "Int",
            kind = InlayHintKind.TYPE,
            tooltip = "Type: Int"
        )

        assertEquals(5, hint.line)
        assertEquals(10, hint.column)
        assertEquals("Int", hint.text)
        assertEquals(InlayHintKind.TYPE, hint.kind)
        assertEquals("Type: Int", hint.tooltip)
    }

    @Test
    fun testParameterHintFactory() {
        val hint = InlayHint.parameter(
            position = EditorPosition(1, 5),
            parameterName = "name"
        )

        assertEquals(1, hint.line)
        assertEquals(5, hint.column)
        assertEquals("name:", hint.text)
        assertEquals(InlayHintKind.PARAMETER, hint.kind)
        assertEquals(InlayHintPosition.BEFORE, hint.hintPosition)
        assertFalse(hint.paddingLeft)
        assertTrue(hint.paddingRight)
    }

    @Test
    fun testTypeHintFactory() {
        val hint = InlayHint.type(
            position = EditorPosition(2, 10),
            typeName = "String"
        )

        assertEquals(2, hint.line)
        assertEquals(10, hint.column)
        assertEquals(": String", hint.text)
        assertEquals(InlayHintKind.TYPE, hint.kind)
        assertEquals(InlayHintPosition.AFTER, hint.hintPosition)
        assertFalse(hint.paddingLeft)
        assertFalse(hint.paddingRight)
    }

    @Test
    fun testChainHintFactory() {
        val hint = InlayHint.chain(
            position = EditorPosition(3, 15),
            resultType = "List<Int>"
        )

        assertEquals(3, hint.line)
        assertEquals(15, hint.column)
        assertEquals("List<Int>", hint.text)
        assertEquals(InlayHintKind.CHAIN, hint.kind)
        assertEquals(InlayHintPosition.AFTER, hint.hintPosition)
        assertTrue(hint.paddingLeft)
        assertFalse(hint.paddingRight)
    }

    @Test
    fun testInlayHintManagerSetHints() {
        val manager = InlayHintManager()

        val hints = listOf(
            InlayHint.type(EditorPosition(0, 5), "Int"),
            InlayHint.type(EditorPosition(1, 10), "String"),
            InlayHint.parameter(EditorPosition(2, 3), "name")
        )

        manager.setHints(hints)

        val all = manager.getAllHints()
        assertEquals(3, all.size)
    }

    @Test
    fun testInlayHintManagerAddHint() {
        val manager = InlayHintManager()

        manager.addHint(InlayHint.type(EditorPosition(0, 5), "Int"))
        assertEquals(1, manager.getAllHints().size)

        manager.addHint(InlayHint.type(EditorPosition(1, 10), "String"))
        assertEquals(2, manager.getAllHints().size)
    }

    @Test
    fun testInlayHintManagerClear() {
        val manager = InlayHintManager()

        manager.setHints(listOf(
            InlayHint.type(EditorPosition(0, 5), "Int"),
            InlayHint.type(EditorPosition(1, 10), "String")
        ))

        assertEquals(2, manager.getAllHints().size)

        manager.clear()
        assertTrue(manager.getAllHints().isEmpty())
    }

    @Test
    fun testGetHintsForLine() {
        val manager = InlayHintManager()

        manager.setHints(listOf(
            InlayHint.type(EditorPosition(0, 5), "Int"),
            InlayHint.type(EditorPosition(0, 15), "String"),
            InlayHint.type(EditorPosition(1, 10), "Boolean"),
            InlayHint.parameter(EditorPosition(2, 3), "x")
        ))

        val line0Hints = manager.getHintsForLine(0)
        assertEquals(2, line0Hints.size)
        // Should be sorted by column
        assertEquals(5, line0Hints[0].column)
        assertEquals(15, line0Hints[1].column)

        val line1Hints = manager.getHintsForLine(1)
        assertEquals(1, line1Hints.size)
        assertEquals(10, line1Hints[0].column)

        val line2Hints = manager.getHintsForLine(2)
        assertEquals(1, line2Hints.size)

        val line3Hints = manager.getHintsForLine(3)
        assertTrue(line3Hints.isEmpty())
    }

    @Test
    fun testGetHintsAtPosition() {
        val manager = InlayHintManager()

        manager.setHints(listOf(
            InlayHint.type(EditorPosition(0, 5), "Int"),
            InlayHint.parameter(EditorPosition(0, 5), "x"),
            InlayHint.type(EditorPosition(0, 10), "String")
        ))

        val hintsAt5 = manager.getHintsAtPosition(EditorPosition(0, 5))
        assertEquals(2, hintsAt5.size)

        val hintsAt10 = manager.getHintsAtPosition(EditorPosition(0, 10))
        assertEquals(1, hintsAt10.size)

        val hintsAt15 = manager.getHintsAtPosition(EditorPosition(0, 15))
        assertTrue(hintsAt15.isEmpty())
    }

    @Test
    fun testCalculateHintOffset() {
        val manager = InlayHintManager()
        val charWidth = 8f

        manager.setHints(listOf(
            InlayHint.parameter(EditorPosition(0, 3), "x"), // "x:" = 2 chars + padding
            InlayHint.parameter(EditorPosition(0, 8), "y")  // "y:" = 2 chars + padding
        ))

        // Before first hint - no offset
        val offset0 = manager.calculateHintOffset(0, 2, charWidth)
        assertEquals(0f, offset0)

        // After first hint (column 3) - should include first hint's width
        val offset5 = manager.calculateHintOffset(0, 5, charWidth)
        assertTrue(offset5 > 0f)

        // After second hint - should include both hints' widths
        val offset10 = manager.calculateHintOffset(0, 10, charWidth)
        assertTrue(offset10 > offset5)
    }

    @Test
    fun testInlayHintKindEnum() {
        // Verify all kinds exist
        assertEquals(4, InlayHintKind.entries.size)
        assertNotNull(InlayHintKind.PARAMETER)
        assertNotNull(InlayHintKind.TYPE)
        assertNotNull(InlayHintKind.CHAIN)
        assertNotNull(InlayHintKind.OTHER)
    }

    @Test
    fun testInlayHintPositionEnum() {
        assertEquals(2, InlayHintPosition.entries.size)
        assertNotNull(InlayHintPosition.BEFORE)
        assertNotNull(InlayHintPosition.AFTER)
    }

    @Test
    fun testHintsPreserveOrder() {
        val manager = InlayHintManager()

        val hint1 = InlayHint.type(EditorPosition(0, 15), "A")
        val hint2 = InlayHint.type(EditorPosition(0, 5), "B")
        val hint3 = InlayHint.type(EditorPosition(0, 10), "C")

        manager.setHints(listOf(hint1, hint2, hint3))

        // getHintsForLine should return sorted by column
        val line0Hints = manager.getHintsForLine(0)
        assertEquals(5, line0Hints[0].column)
        assertEquals(10, line0Hints[1].column)
        assertEquals(15, line0Hints[2].column)
    }

    @Test
    fun testDefaultValues() {
        val hint = InlayHint(
            position = EditorPosition(0, 0),
            text = "test"
        )

        assertEquals(InlayHintKind.OTHER, hint.kind)
        assertEquals(null, hint.tooltip)
        assertFalse(hint.paddingLeft)
        assertTrue(hint.paddingRight)
        assertEquals(InlayHintPosition.BEFORE, hint.hintPosition)
    }

    @Test
    fun testMultipleLinesRebuildIndex() {
        val manager = InlayHintManager()

        // Add hints for multiple lines
        manager.setHints(listOf(
            InlayHint.type(EditorPosition(0, 5), "Int"),
            InlayHint.type(EditorPosition(5, 10), "String"),
            InlayHint.type(EditorPosition(10, 15), "Boolean")
        ))

        // Verify each line has correct hints
        assertEquals(1, manager.getHintsForLine(0).size)
        assertTrue(manager.getHintsForLine(2).isEmpty())
        assertEquals(1, manager.getHintsForLine(5).size)
        assertEquals(1, manager.getHintsForLine(10).size)

        // Update hints
        manager.setHints(listOf(
            InlayHint.type(EditorPosition(2, 5), "Updated")
        ))

        // Old lines should be empty now
        assertTrue(manager.getHintsForLine(0).isEmpty())
        assertTrue(manager.getHintsForLine(5).isEmpty())
        assertEquals(1, manager.getHintsForLine(2).size)
    }
}
