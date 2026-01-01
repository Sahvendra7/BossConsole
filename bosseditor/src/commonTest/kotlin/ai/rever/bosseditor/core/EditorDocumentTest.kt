package ai.rever.bosseditor.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class EditorDocumentTest {

    @Test
    fun testEmptyDocument() {
        val doc = EditorDocument()
        assertEquals(0, doc.length)
        assertEquals(1, doc.lineCount)
        assertEquals("", doc.getText())
    }

    @Test
    fun testInitialText() {
        val doc = EditorDocument("hello world")
        assertEquals(11, doc.length)
        assertEquals(1, doc.lineCount)
        assertEquals("hello world", doc.getText())
    }

    @Test
    fun testMultiLineInitialText() {
        val doc = EditorDocument("line1\nline2\nline3")
        assertEquals(17, doc.length)
        assertEquals(3, doc.lineCount)
    }

    @Test
    fun testCharAt() {
        val doc = EditorDocument("hello")
        assertEquals('h', doc.charAt(0))
        assertEquals('e', doc.charAt(1))
        assertEquals('l', doc.charAt(2))
        assertEquals('l', doc.charAt(3))
        assertEquals('o', doc.charAt(4))
    }

    @Test
    fun testCharAtOutOfBounds() {
        val doc = EditorDocument("hello")
        assertFailsWith<IllegalArgumentException> {
            doc.charAt(10)
        }
    }

    @Test
    fun testGetTextRange() {
        val doc = EditorDocument("hello world")
        assertEquals("hello", doc.getText(0, 5))
        assertEquals("world", doc.getText(6, 11))
        assertEquals("lo wo", doc.getText(3, 8))
    }

    @Test
    fun testGetTextRangeEmpty() {
        val doc = EditorDocument("hello")
        assertEquals("", doc.getText(2, 2))
    }

    @Test
    fun testInsertAtStart() {
        val doc = EditorDocument("world")
        doc.insert(0, "hello ")
        assertEquals("hello world", doc.getText())
        assertEquals(11, doc.length)
    }

    @Test
    fun testInsertAtEnd() {
        val doc = EditorDocument("hello")
        doc.insert(5, " world")
        assertEquals("hello world", doc.getText())
    }

    @Test
    fun testInsertInMiddle() {
        val doc = EditorDocument("helloworld")
        doc.insert(5, " ")
        assertEquals("hello world", doc.getText())
    }

    @Test
    fun testInsertNewlines() {
        val doc = EditorDocument("line1line2")
        doc.insert(5, "\n")
        assertEquals("line1\nline2", doc.getText())
        assertEquals(2, doc.lineCount)
    }

    @Test
    fun testDeleteFromStart() {
        val doc = EditorDocument("hello world")
        doc.delete(0, 6)
        assertEquals("world", doc.getText())
    }

    @Test
    fun testDeleteFromEnd() {
        val doc = EditorDocument("hello world")
        doc.delete(5, 11)
        assertEquals("hello", doc.getText())
    }

    @Test
    fun testDeleteFromMiddle() {
        val doc = EditorDocument("hello world")
        doc.delete(5, 6)
        assertEquals("helloworld", doc.getText())
    }

    @Test
    fun testDeleteNewlines() {
        val doc = EditorDocument("line1\nline2")
        doc.delete(5, 6)
        assertEquals("line1line2", doc.getText())
        assertEquals(1, doc.lineCount)
    }

    @Test
    fun testReplace() {
        val doc = EditorDocument("hello world")
        doc.replace(6, 11, "kotlin")
        assertEquals("hello kotlin", doc.getText())
    }

    @Test
    fun testReplaceWithEmpty() {
        val doc = EditorDocument("hello world")
        doc.replace(5, 6, "")
        assertEquals("helloworld", doc.getText())
    }

    @Test
    fun testReplaceEmpty() {
        val doc = EditorDocument("hello")
        doc.replace(2, 2, "XX")
        assertEquals("heXXllo", doc.getText())
    }

    @Test
    fun testGetLineText() {
        val doc = EditorDocument("line1\nline2\nline3")
        assertEquals("line1", doc.getLineText(0))
        assertEquals("line2", doc.getLineText(1))
        assertEquals("line3", doc.getLineText(2))
    }

    @Test
    fun testGetLineStartOffset() {
        val doc = EditorDocument("line1\nline2\nline3")
        assertEquals(0, doc.getLineStartOffset(0))
        assertEquals(6, doc.getLineStartOffset(1))
        assertEquals(12, doc.getLineStartOffset(2))
    }

    @Test
    fun testGetLineEndOffset() {
        val doc = EditorDocument("line1\nline2\nline3")
        // getLineEndOffset returns start of next line (includes newline)
        assertEquals(6, doc.getLineEndOffset(0))  // Points to start of line2
        assertEquals(12, doc.getLineEndOffset(1)) // Points to start of line3
        assertEquals(17, doc.getLineEndOffset(2)) // Document length
    }

    @Test
    fun testOffsetToPosition() {
        val doc = EditorDocument("line1\nline2\nline3")

        val pos0 = doc.offsetToPosition(0)
        assertEquals(0, pos0.line)
        assertEquals(0, pos0.column)

        val pos5 = doc.offsetToPosition(5)
        assertEquals(0, pos5.line)
        assertEquals(5, pos5.column)

        val pos6 = doc.offsetToPosition(6)
        assertEquals(1, pos6.line)
        assertEquals(0, pos6.column)

        val pos8 = doc.offsetToPosition(8)
        assertEquals(1, pos8.line)
        assertEquals(2, pos8.column)
    }

    @Test
    fun testPositionToOffset() {
        val doc = EditorDocument("line1\nline2\nline3")

        assertEquals(0, doc.positionToOffset(EditorPosition(0, 0)))
        assertEquals(5, doc.positionToOffset(EditorPosition(0, 5)))
        assertEquals(6, doc.positionToOffset(EditorPosition(1, 0)))
        assertEquals(8, doc.positionToOffset(EditorPosition(1, 2)))
        assertEquals(12, doc.positionToOffset(EditorPosition(2, 0)))
    }

    @Test
    fun testDocumentVersion() {
        val doc = EditorDocument("hello")
        val v1 = doc.documentVersion

        doc.insert(5, " world")
        val v2 = doc.documentVersion
        assertTrue(v2 > v1)

        doc.delete(0, 6)
        val v3 = doc.documentVersion
        assertTrue(v3 > v2)
    }

    @Test
    fun testSetText() {
        val doc = EditorDocument("hello")
        doc.setText("new content")
        assertEquals("new content", doc.getText())
        assertEquals(11, doc.length)
        assertEquals(1, doc.lineCount)
    }

    @Test
    fun testSetTextMultiline() {
        val doc = EditorDocument("hello")
        doc.setText("line1\nline2\nline3")
        assertEquals(3, doc.lineCount)
    }

    @Test
    fun testLargeInsert() {
        val doc = EditorDocument("hello")
        val largeText = "x".repeat(10000)
        doc.insert(5, largeText)
        assertEquals(10005, doc.length)
        assertEquals("hello" + largeText, doc.getText())
    }

    @Test
    fun testMultipleInserts() {
        val doc = EditorDocument("")
        for (i in 0 until 100) {
            doc.insert(doc.length, "line$i\n")
        }
        assertEquals(101, doc.lineCount) // 100 lines + empty last line
    }

    @Test
    fun testMultipleDeletes() {
        val doc = EditorDocument("0123456789")
        doc.delete(0, 1)
        assertEquals("123456789", doc.getText())
        doc.delete(0, 1)
        assertEquals("23456789", doc.getText())
        doc.delete(0, 1)
        assertEquals("3456789", doc.getText())
    }

    @Test
    fun testLineCountAfterInsert() {
        val doc = EditorDocument("hello")
        assertEquals(1, doc.lineCount)

        doc.insert(5, "\nworld")
        assertEquals(2, doc.lineCount)

        doc.insert(doc.length, "\n")
        assertEquals(3, doc.lineCount)
    }

    @Test
    fun testLineCountAfterDelete() {
        val doc = EditorDocument("line1\nline2\nline3")
        assertEquals(3, doc.lineCount)

        doc.delete(5, 6) // Delete first newline
        assertEquals(2, doc.lineCount)
    }

    @Test
    fun testEmptyLines() {
        val doc = EditorDocument("line1\n\nline3")
        assertEquals(3, doc.lineCount)
        assertEquals("line1", doc.getLineText(0))
        assertEquals("", doc.getLineText(1))
        assertEquals("line3", doc.getLineText(2))
    }

    @Test
    fun testTrailingNewline() {
        val doc = EditorDocument("hello\n")
        assertEquals(2, doc.lineCount)
        assertEquals("hello", doc.getLineText(0))
        assertEquals("", doc.getLineText(1))
    }

    @Test
    fun testOffsetRangeEmpty() {
        val range = OffsetRange(5, 5)
        assertTrue(range.isEmpty)
        assertEquals(0, range.length)
    }

    @Test
    fun testOffsetRangeNonEmpty() {
        val range = OffsetRange(0, 10)
        assertTrue(!range.isEmpty)
        assertEquals(10, range.length)
    }

    @Test
    fun testOffsetRangeContains() {
        val range = OffsetRange(5, 10)
        assertTrue(range.contains(5))
        assertTrue(range.contains(7))
        assertTrue(range.contains(9))
        assertTrue(!range.contains(4))
        assertTrue(!range.contains(10))
    }

    @Test
    fun testOffsetRangeOverlaps() {
        val range1 = OffsetRange(0, 10)
        val range2 = OffsetRange(5, 15)
        val range3 = OffsetRange(10, 20)
        val range4 = OffsetRange(20, 30)

        assertTrue(range1.overlaps(range2))
        assertTrue(range2.overlaps(range1))
        assertTrue(!range1.overlaps(range3)) // Adjacent but not overlapping
        assertTrue(!range1.overlaps(range4))
    }
}
