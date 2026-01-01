package ai.rever.bosseditor.features

import ai.rever.bosseditor.core.EditorDocument
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BracketMatcherTest {

    @Test
    fun testMatchParentheses() {
        val doc = EditorDocument("foo(bar)")
        val matcher = BracketMatcher(doc)

        // At opening paren
        val matchFromOpen = matcher.findMatchingBracket(3)
        assertNotNull(matchFromOpen)
        assertEquals('(', matchFromOpen.sourceBracket)
        assertEquals(3, matchFromOpen.sourceOffset)
        assertEquals(')', matchFromOpen.matchingBracket)
        assertEquals(7, matchFromOpen.matchingOffset)

        // At closing paren
        val matchFromClose = matcher.findMatchingBracket(7)
        assertNotNull(matchFromClose)
        assertEquals(')', matchFromClose.sourceBracket)
        assertEquals(7, matchFromClose.sourceOffset)
        assertEquals('(', matchFromClose.matchingBracket)
        assertEquals(3, matchFromClose.matchingOffset)
    }

    @Test
    fun testMatchSquareBrackets() {
        val doc = EditorDocument("arr[0]")
        val matcher = BracketMatcher(doc)

        val matchFromOpen = matcher.findMatchingBracket(3)
        assertNotNull(matchFromOpen)
        assertEquals('[', matchFromOpen.sourceBracket)
        assertEquals(']', matchFromOpen.matchingBracket)
    }

    @Test
    fun testMatchCurlyBraces() {
        val doc = EditorDocument("{ code }")
        val matcher = BracketMatcher(doc)

        val matchFromOpen = matcher.findMatchingBracket(0)
        assertNotNull(matchFromOpen)
        assertEquals('{', matchFromOpen.sourceBracket)
        assertEquals('}', matchFromOpen.matchingBracket)
    }

    @Test
    fun testNestedBrackets() {
        val doc = EditorDocument("foo((bar))")
        val matcher = BracketMatcher(doc)

        // Outer opening paren
        val outerMatch = matcher.findMatchingBracket(3)
        assertNotNull(outerMatch)
        assertEquals(9, outerMatch.matchingOffset)

        // Inner opening paren
        val innerMatch = matcher.findMatchingBracket(4)
        assertNotNull(innerMatch)
        assertEquals(8, innerMatch.matchingOffset)
    }

    @Test
    fun testDeeplyNested() {
        val doc = EditorDocument("a(b[c{d}e]f)")
        val matcher = BracketMatcher(doc)

        // Test each bracket pair
        val parenMatch = matcher.findMatchingBracket(1)
        assertNotNull(parenMatch)
        assertEquals(11, parenMatch.matchingOffset)

        val bracketMatch = matcher.findMatchingBracket(3)
        assertNotNull(bracketMatch)
        assertEquals(9, bracketMatch.matchingOffset)

        val braceMatch = matcher.findMatchingBracket(5)
        assertNotNull(braceMatch)
        assertEquals(7, braceMatch.matchingOffset)
    }

    @Test
    fun testNoBracketAtOffset() {
        val doc = EditorDocument("hello world")
        val matcher = BracketMatcher(doc)

        val match = matcher.findMatchingBracket(0)
        assertNull(match)
    }

    @Test
    fun testUnmatchedOpenBracket() {
        val doc = EditorDocument("foo(bar")
        val matcher = BracketMatcher(doc)

        val match = matcher.findMatchingBracket(3)
        assertNull(match)
    }

    @Test
    fun testUnmatchedCloseBracket() {
        val doc = EditorDocument("foo)bar")
        val matcher = BracketMatcher(doc)

        val match = matcher.findMatchingBracket(3)
        assertNull(match)
    }

    @Test
    fun testBracketInString() {
        val doc = EditorDocument("""foo("(bar)")""")
        val matcher = BracketMatcher(doc)

        // Opening paren of function call should match closing
        val match = matcher.findMatchingBracket(3)
        assertNotNull(match)
        assertEquals(11, match.matchingOffset)
    }

    @Test
    fun testBracketInComment() {
        val doc = EditorDocument("foo(bar) // (comment)")
        val matcher = BracketMatcher(doc)

        // Opening paren should match
        val match = matcher.findMatchingBracket(3)
        assertNotNull(match)
        assertEquals(7, match.matchingOffset)
    }

    @Test
    fun testBracketAfterCaret() {
        val doc = EditorDocument("foo()")
        val matcher = BracketMatcher(doc)

        // Caret right after opening paren
        val match = matcher.findMatchingBracket(4)
        assertNotNull(match)
    }

    @Test
    fun testEmptyDocument() {
        val doc = EditorDocument("")
        val matcher = BracketMatcher(doc)

        val match = matcher.findMatchingBracket(0)
        assertNull(match)
    }

    @Test
    fun testFindAllBracketPairs() {
        val doc = EditorDocument("a(b[c]d)e")
        val matcher = BracketMatcher(doc)

        val pairs = matcher.findAllBracketPairs()
        assertEquals(2, pairs.size)

        // Check inner pair
        val innerPair = pairs.find { it.openChar == '[' }
        assertNotNull(innerPair)
        assertEquals(3, innerPair.openOffset)
        assertEquals(5, innerPair.closeOffset)

        // Check outer pair
        val outerPair = pairs.find { it.openChar == '(' }
        assertNotNull(outerPair)
        assertEquals(1, outerPair.openOffset)
        assertEquals(7, outerPair.closeOffset)
    }

    @Test
    fun testFindUnmatchedBrackets() {
        val doc = EditorDocument("a(b[c)d")
        val matcher = BracketMatcher(doc)

        val unmatched = matcher.findUnmatchedBrackets()
        assertTrue(unmatched.isNotEmpty())
    }

    @Test
    fun testGetBracketDepth() {
        val doc = EditorDocument("a{b{c}d}e")
        val matcher = BracketMatcher(doc)

        assertEquals(0, matcher.getBracketDepth(0)) // before first {
        assertEquals(1, matcher.getBracketDepth(2)) // inside first {
        assertEquals(2, matcher.getBracketDepth(4)) // inside second {
        assertEquals(1, matcher.getBracketDepth(6)) // after inner }
        assertEquals(0, matcher.getBracketDepth(8)) // after outer }
    }

    @Test
    fun testAngleBrackets() {
        val doc = EditorDocument("List<String>")
        val matcher = BracketMatcher(doc)
        matcher.config = BracketMatcherConfig(matchAngleBrackets = true)

        val match = matcher.findMatchingBracket(4)
        assertNotNull(match)
        assertEquals('<', match.sourceBracket)
        assertEquals('>', match.matchingBracket)
    }

    @Test
    fun testAngleBracketsDisabledByDefault() {
        val doc = EditorDocument("List<String>")
        val matcher = BracketMatcher(doc)

        // By default, angle brackets are not matched
        val match = matcher.findMatchingBracket(4)
        assertNull(match)
    }

    @Test
    fun testMultipleLinesParentheses() {
        val doc = EditorDocument("foo(\n  bar,\n  baz\n)")
        val matcher = BracketMatcher(doc)

        val match = matcher.findMatchingBracket(3)
        assertNotNull(match)
        assertEquals('(', match.sourceBracket)
        assertEquals(')', match.matchingBracket)
    }

    @Test
    fun testTripleQuotedStringIgnored() {
        val doc = EditorDocument("\"\"\"a(b)c\"\"\"()")
        val matcher = BracketMatcher(doc)

        // The parentheses inside triple-quoted string should be ignored
        val match = matcher.findMatchingBracket(11)
        assertNotNull(match)
        assertEquals(12, match.matchingOffset)
    }

    @Test
    fun testEscapedCharInString() {
        val doc = EditorDocument(""""a\"b()"(x)""")
        val matcher = BracketMatcher(doc)

        // The escaped quote shouldn't end the string
        // The ( at position 8 should match ) at position 9
        val match = matcher.findMatchingBracket(8)
        assertNotNull(match)
    }
}
