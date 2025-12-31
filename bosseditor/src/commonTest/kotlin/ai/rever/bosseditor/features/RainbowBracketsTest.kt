package ai.rever.bosseditor.features

import ai.rever.bosseditor.core.EditorDocument
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RainbowBracketsTest {

    @Test
    fun testSimpleBrackets() {
        val doc = EditorDocument("foo()")
        val rainbow = RainbowBrackets(doc)

        val brackets = rainbow.getRainbowBrackets()
        assertEquals(2, brackets.size)

        // Opening paren at depth 0
        val open = brackets.find { it.char == '(' }
        assertNotNull(open)
        assertEquals(3, open.offset)
        assertEquals(0, open.depth)

        // Closing paren at depth 0
        val close = brackets.find { it.char == ')' }
        assertNotNull(close)
        assertEquals(4, close.offset)
        assertEquals(0, close.depth)
    }

    @Test
    fun testNestedBrackets() {
        val doc = EditorDocument("a(b(c))")
        val rainbow = RainbowBrackets(doc)

        val brackets = rainbow.getRainbowBrackets()
        assertEquals(4, brackets.size)

        // Sort by offset for easier testing
        val sorted = brackets.sortedBy { it.offset }

        // Outer open at depth 0
        assertEquals('(', sorted[0].char)
        assertEquals(0, sorted[0].depth)

        // Inner open at depth 1
        assertEquals('(', sorted[1].char)
        assertEquals(1, sorted[1].depth)

        // Inner close at depth 1
        assertEquals(')', sorted[2].char)
        assertEquals(1, sorted[2].depth)

        // Outer close at depth 0
        assertEquals(')', sorted[3].char)
        assertEquals(0, sorted[3].depth)
    }

    @Test
    fun testDeeplyNestedBrackets() {
        val doc = EditorDocument("a{b{c{d{e}}}}")
        val rainbow = RainbowBrackets(doc)

        val brackets = rainbow.getRainbowBrackets()
        assertEquals(8, brackets.size) // 4 opens + 4 closes

        // Check depths cycle through 0-3
        val opens = brackets.filter { it.char == '{' }.sortedBy { it.offset }
        assertEquals(0, opens[0].depth)
        assertEquals(1, opens[1].depth)
        assertEquals(2, opens[2].depth)
        assertEquals(3, opens[3].depth)

        // Closes should have matching depths
        val closes = brackets.filter { it.char == '}' }.sortedBy { it.offset }
        assertEquals(3, closes[0].depth) // innermost
        assertEquals(2, closes[1].depth)
        assertEquals(1, closes[2].depth)
        assertEquals(0, closes[3].depth) // outermost
    }

    @Test
    fun testMixedBracketTypes() {
        val doc = EditorDocument("a(b[c{d}])")
        val rainbow = RainbowBrackets(doc)

        val brackets = rainbow.getRainbowBrackets()
        assertEquals(6, brackets.size)

        val sorted = brackets.sortedBy { it.offset }

        // ( at depth 0
        assertEquals('(', sorted[0].char)
        assertEquals(0, sorted[0].depth)

        // [ at depth 1
        assertEquals('[', sorted[1].char)
        assertEquals(1, sorted[1].depth)

        // { at depth 2
        assertEquals('{', sorted[2].char)
        assertEquals(2, sorted[2].depth)

        // } at depth 2
        assertEquals('}', sorted[3].char)
        assertEquals(2, sorted[3].depth)

        // ] at depth 1
        assertEquals(']', sorted[4].char)
        assertEquals(1, sorted[4].depth)

        // ) at depth 0
        assertEquals(')', sorted[5].char)
        assertEquals(0, sorted[5].depth)
    }

    @Test
    fun testDepthCycling() {
        // Create 5 levels of nesting to test color cycling
        val doc = EditorDocument("a{b{c{d{e{f}}}}}")
        val rainbow = RainbowBrackets(doc)

        val brackets = rainbow.getRainbowBrackets()
        val opens = brackets.filter { it.char == '{' }.sortedBy { it.offset }

        assertEquals(5, opens.size)
        assertEquals(0, opens[0].depth) // depth 0 -> color 1
        assertEquals(1, opens[1].depth) // depth 1 -> color 2
        assertEquals(2, opens[2].depth) // depth 2 -> color 3
        assertEquals(3, opens[3].depth) // depth 3 -> color 4
        assertEquals(4, opens[4].depth) // depth 4 -> cycles to color 1 (4 % 4 = 0)
    }

    @Test
    fun testEmptyDocument() {
        val doc = EditorDocument("")
        val rainbow = RainbowBrackets(doc)

        val brackets = rainbow.getRainbowBrackets()
        assertTrue(brackets.isEmpty())
    }

    @Test
    fun testNoBrackets() {
        val doc = EditorDocument("hello world")
        val rainbow = RainbowBrackets(doc)

        val brackets = rainbow.getRainbowBrackets()
        assertTrue(brackets.isEmpty())
    }

    @Test
    fun testBracketsInStrings() {
        // Brackets inside strings should be ignored
        val doc = EditorDocument("""a("()")b""")
        val rainbow = RainbowBrackets(doc)

        val brackets = rainbow.getRainbowBrackets()
        // Only the outer brackets should be counted, not the ones in the string
        assertTrue(brackets.isEmpty() || brackets.none { it.offset in 3..4 })
    }

    @Test
    fun testGetRainbowBracketsForLine() {
        val doc = EditorDocument("a(\nb(\nc\n)\n)")
        val rainbow = RainbowBrackets(doc)

        // Line 0: "a("
        val line0Brackets = rainbow.getRainbowBracketsForLine(0)
        assertEquals(1, line0Brackets.size)
        assertEquals('(', line0Brackets[0].char)
        assertEquals(0, line0Brackets[0].depth)

        // Line 1: "b("
        val line1Brackets = rainbow.getRainbowBracketsForLine(1)
        assertEquals(1, line1Brackets.size)
        assertEquals('(', line1Brackets[0].char)
        assertEquals(1, line1Brackets[0].depth)

        // Line 2: "c" - no brackets
        val line2Brackets = rainbow.getRainbowBracketsForLine(2)
        assertTrue(line2Brackets.isEmpty())

        // Line 3: ")" - inner close
        val line3Brackets = rainbow.getRainbowBracketsForLine(3)
        assertEquals(1, line3Brackets.size)
        assertEquals(')', line3Brackets[0].char)
        assertEquals(1, line3Brackets[0].depth)

        // Line 4: ")" - outer close
        val line4Brackets = rainbow.getRainbowBracketsForLine(4)
        assertEquals(1, line4Brackets.size)
        assertEquals(')', line4Brackets[0].char)
        assertEquals(0, line4Brackets[0].depth)
    }

    @Test
    fun testGetDepthAtOffset() {
        val doc = EditorDocument("a(b(c))")
        val rainbow = RainbowBrackets(doc)

        // Offset 1 is outer (
        assertEquals(0, rainbow.getDepthAtOffset(1))

        // Offset 3 is inner (
        assertEquals(1, rainbow.getDepthAtOffset(3))

        // Offset 5 is inner )
        assertEquals(1, rainbow.getDepthAtOffset(5))

        // Offset 6 is outer )
        assertEquals(0, rainbow.getDepthAtOffset(6))

        // Non-bracket offsets should return null
        assertNull(rainbow.getDepthAtOffset(0))
        assertNull(rainbow.getDepthAtOffset(2))
        assertNull(rainbow.getDepthAtOffset(4))
    }

    @Test
    fun testMultipleLinesWithMixedBrackets() {
        val doc = EditorDocument("""
            fun test() {
                if (x) {
                    println(y)
                }
            }
        """.trimIndent())
        val rainbow = RainbowBrackets(doc)

        val brackets = rainbow.getRainbowBrackets()
        assertTrue(brackets.isNotEmpty())

        // Verify we have parentheses and braces
        assertTrue(brackets.any { it.char == '(' })
        assertTrue(brackets.any { it.char == ')' })
        assertTrue(brackets.any { it.char == '{' })
        assertTrue(brackets.any { it.char == '}' })
    }

    @Test
    fun testSortedByOffset() {
        val doc = EditorDocument("a(b[c{d}e]f)")
        val rainbow = RainbowBrackets(doc)

        val brackets = rainbow.getRainbowBrackets()

        // Verify brackets are sorted by offset
        for (i in 0 until brackets.size - 1) {
            assertTrue(brackets[i].offset < brackets[i + 1].offset)
        }
    }
}
