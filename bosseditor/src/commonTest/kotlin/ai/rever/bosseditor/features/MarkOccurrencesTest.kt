package ai.rever.bosseditor.features

import ai.rever.bosseditor.core.EditorDocument
import ai.rever.bosseditor.core.OffsetRange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MarkOccurrencesTest {

    @Test
    fun testFindOccurrencesOfWord() {
        val doc = EditorDocument("foo bar foo baz foo")
        val marker = MarkOccurrences(doc)

        // Offset 0 is on "foo"
        val occurrences = marker.findOccurrences(0)

        assertEquals(3, occurrences.size)
        assertEquals(0, occurrences[0].start)
        assertEquals(3, occurrences[0].end)
        assertEquals(8, occurrences[1].start)
        assertEquals(11, occurrences[1].end)
        assertEquals(16, occurrences[2].start)
        assertEquals(19, occurrences[2].end)
    }

    @Test
    fun testFindOccurrencesMiddleOfWord() {
        val doc = EditorDocument("hello world hello")
        val marker = MarkOccurrences(doc)

        // Offset 2 is in the middle of "hello"
        val occurrences = marker.findOccurrences(2)

        assertEquals(2, occurrences.size)
        assertEquals(0, occurrences[0].start)
        assertEquals(5, occurrences[0].end)
        assertEquals(12, occurrences[1].start)
        assertEquals(17, occurrences[1].end)
    }

    @Test
    fun testFindOccurrencesEndOfWord() {
        val doc = EditorDocument("test abc test")
        val marker = MarkOccurrences(doc)

        // Offset 4 is right after "test"
        val occurrences = marker.findOccurrences(4)

        assertEquals(2, occurrences.size)
    }

    @Test
    fun testOccurrencesAtWordBoundary() {
        val doc = EditorDocument("foo bar baz")
        val marker = MarkOccurrences(doc)

        // Offset 3 is on whitespace after "foo", but previous char is 'o'
        // so it finds "foo" (cursor at end of word behavior)
        val occurrences = marker.findOccurrences(3)

        assertEquals(1, occurrences.size)
        assertEquals("foo", doc.getText(occurrences[0].start, occurrences[0].end))
    }

    @Test
    fun testNoOccurrencesForShortWord() {
        val doc = EditorDocument("a a a a a")
        val marker = MarkOccurrences(doc)

        // Single char words are too short by default (minWordLength = 2)
        val occurrences = marker.findOccurrences(0)

        assertTrue(occurrences.isEmpty())
    }

    @Test
    fun testCustomMinWordLength() {
        val doc = EditorDocument("a a a a a")
        val marker = MarkOccurrences(doc, MarkOccurrencesConfig(minWordLength = 1))

        val occurrences = marker.findOccurrences(0)

        assertEquals(5, occurrences.size)
    }

    @Test
    fun testCaseSensitive() {
        val doc = EditorDocument("Foo foo FOO")
        val marker = MarkOccurrences(doc, MarkOccurrencesConfig(caseSensitive = true))

        // Only exact case matches
        val occurrences = marker.findOccurrences(0)

        assertEquals(1, occurrences.size)
    }

    @Test
    fun testCaseInsensitive() {
        val doc = EditorDocument("Foo foo FOO")
        val marker = MarkOccurrences(doc, MarkOccurrencesConfig(caseSensitive = false))

        val occurrences = marker.findOccurrences(0)

        assertEquals(3, occurrences.size)
    }

    @Test
    fun testWholeWordMatching() {
        val doc = EditorDocument("foo foobar barfoo foobarfoo")
        val marker = MarkOccurrences(doc, MarkOccurrencesConfig(wholeWord = true))

        val occurrences = marker.findOccurrences(0)

        // Only "foo" should match, not "foobar", "barfoo", or "foobarfoo"
        assertEquals(1, occurrences.size)
        assertEquals(0, occurrences[0].start)
        assertEquals(3, occurrences[0].end)
    }

    @Test
    fun testPartialWordMatching() {
        val doc = EditorDocument("foo foobar barfoo")
        val marker = MarkOccurrences(doc, MarkOccurrencesConfig(wholeWord = false))

        val occurrences = marker.findOccurrences(0)

        // "foo" should match in all three words
        assertEquals(3, occurrences.size)
    }

    @Test
    fun testGetWordAtOffset() {
        val doc = EditorDocument("hello world")
        val marker = MarkOccurrences(doc)

        val wordRange = marker.getWordAtOffset(2)
        assertNotNull(wordRange)
        assertEquals(0, wordRange.start)
        assertEquals(5, wordRange.end)

        val wordRange2 = marker.getWordAtOffset(8)
        assertNotNull(wordRange2)
        assertEquals(6, wordRange2.start)
        assertEquals(11, wordRange2.end)
    }

    @Test
    fun testGetWordAtOffsetOnWhitespace() {
        val doc = EditorDocument("hello world")
        val marker = MarkOccurrences(doc)

        // Offset 5 is space, but previous char 'o' is a word char
        // So it finds "hello" (cursor at end of word)
        val wordRange = marker.getWordAtOffset(5)
        assertNotNull(wordRange)
        assertEquals(0, wordRange.start)
        assertEquals(5, wordRange.end)
    }

    @Test
    fun testFindOccurrencesOfSelection() {
        val doc = EditorDocument("hello world hello")
        val marker = MarkOccurrences(doc)

        val selection = OffsetRange(0, 5) // "hello"
        val occurrences = marker.findOccurrencesOfSelection(selection)

        assertEquals(2, occurrences.size)
    }

    @Test
    fun testEmptySelection() {
        val doc = EditorDocument("hello world")
        val marker = MarkOccurrences(doc)

        val selection = OffsetRange(0, 0)
        val occurrences = marker.findOccurrencesOfSelection(selection)

        assertTrue(occurrences.isEmpty())
    }

    @Test
    fun testMultilineSelectionIgnored() {
        val doc = EditorDocument("hello\nworld\nhello")
        val marker = MarkOccurrences(doc)

        val selection = OffsetRange(0, 11) // "hello\nworld"
        val occurrences = marker.findOccurrencesOfSelection(selection)

        // By default, multiline selections don't match
        assertTrue(occurrences.isEmpty())
    }

    @Test
    fun testMultilineSelectionAllowed() {
        val doc = EditorDocument("hello\nworld\nhello\nworld")
        val marker = MarkOccurrences(doc, MarkOccurrencesConfig(highlightMultilineSelection = true))

        val selection = OffsetRange(0, 11) // "hello\nworld"
        val occurrences = marker.findOccurrencesOfSelection(selection)

        // Should find both occurrences
        assertEquals(2, occurrences.size)
    }

    @Test
    fun testMaxOccurrences() {
        val doc = EditorDocument("foo foo foo foo foo foo foo foo foo foo foo")
        val marker = MarkOccurrences(doc, MarkOccurrencesConfig(maxOccurrences = 5))

        val occurrences = marker.findOccurrences(0)

        // Should be limited to 5
        assertEquals(5, occurrences.size)
    }

    @Test
    fun testFindVisibleOccurrences() {
        val doc = EditorDocument("foo bar foo baz foo qux foo")
        val marker = MarkOccurrences(doc)

        // Only search in a visible range (positions 4-15)
        val occurrences = marker.findVisibleOccurrences(0, 4, 15)

        // Should only find "foo" at position 8-11
        assertEquals(1, occurrences.size)
        assertEquals(8, occurrences[0].start)
        assertEquals(11, occurrences[0].end)
    }

    @Test
    fun testWordCharacters() {
        val doc = EditorDocument("foo_bar foo_bar")
        val marker = MarkOccurrences(doc)

        // Underscore should be part of word
        val wordRange = marker.getWordAtOffset(0)
        assertNotNull(wordRange)
        assertEquals(0, wordRange.start)
        assertEquals(7, wordRange.end)
    }

    @Test
    fun testDollarSignInWord() {
        val doc = EditorDocument("\$foo \$foo")
        val marker = MarkOccurrences(doc)

        // Dollar sign should be part of word (for variables)
        val occurrences = marker.findOccurrences(0)

        assertEquals(2, occurrences.size)
    }

    @Test
    fun testEmptyDocument() {
        val doc = EditorDocument("")
        val marker = MarkOccurrences(doc)

        val occurrences = marker.findOccurrences(0)
        assertTrue(occurrences.isEmpty())

        val wordRange = marker.getWordAtOffset(0)
        assertNull(wordRange)
    }

    @Test
    fun testOutOfBoundsOffset() {
        val doc = EditorDocument("hello")
        val marker = MarkOccurrences(doc)

        val wordRange = marker.getWordAtOffset(100)
        assertNull(wordRange)
    }

    @Test
    fun testOccurrenceResultData() {
        val doc = EditorDocument("foo bar foo")
        val marker = MarkOccurrences(doc)

        val occurrences = marker.findOccurrences(0)
        val result = OccurrenceResult(
            text = "foo",
            occurrences = occurrences,
            originRange = OffsetRange(0, 3)
        )

        assertEquals("foo", result.text)
        assertEquals(2, result.count)
        assertTrue(result.hasOtherOccurrences)
    }

    @Test
    fun testSingleOccurrence() {
        val doc = EditorDocument("unique hello world")
        val marker = MarkOccurrences(doc)

        val occurrences = marker.findOccurrences(0)

        assertEquals(1, occurrences.size)

        val result = OccurrenceResult(
            text = "unique",
            occurrences = occurrences,
            originRange = OffsetRange(0, 6)
        )
        assertEquals(false, result.hasOtherOccurrences)
    }
}
