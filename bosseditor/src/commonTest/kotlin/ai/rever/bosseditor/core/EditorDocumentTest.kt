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

    // ============================================
    // Unicode/Emoji Handling Tests
    // ============================================

    @Test
    fun testUnicodeBasicMultilingual() {
        // Basic multilingual plane characters
        val doc = EditorDocument("café résumé")
        assertEquals(11, doc.length)
        assertEquals('é', doc.charAt(3))
        assertEquals("café", doc.getText(0, 4))
    }

    @Test
    fun testUnicodeEmoji() {
        // Emoji are outside BMP (surrogate pairs in Java/Kotlin)
        val doc = EditorDocument("Hello 👋 World")
        // 👋 is a surrogate pair (2 chars in Kotlin String)
        assertEquals(14, doc.length) // "Hello " (6) + 👋 (2) + " World" (6)
        assertEquals("Hello 👋 World", doc.getText())
    }

    @Test
    fun testUnicodeInsertEmoji() {
        val doc = EditorDocument("Hello World")
        doc.insert(6, "👋 ")
        assertEquals("Hello 👋 World", doc.getText())
    }

    @Test
    fun testUnicodeCombiningCharacters() {
        // e + combining acute accent = é
        val doc = EditorDocument("caf\u0065\u0301") // cafe with combining accent
        assertEquals(5, doc.length) // c, a, f, e, combining accent
        assertEquals("caf\u0065\u0301", doc.getText())
    }

    @Test
    fun testUnicodeJapanese() {
        val doc = EditorDocument("こんにちは") // "Hello" in Japanese
        assertEquals(5, doc.length)
        assertEquals('こ', doc.charAt(0))
        assertEquals("こん", doc.getText(0, 2))
    }

    @Test
    fun testUnicodeMixed() {
        val doc = EditorDocument("Hello 世界 🌍")
        // "Hello " (6) + "世界" (2) + " " (1) + 🌍 (2 - surrogate pair)
        assertEquals(11, doc.length)
    }

    // ============================================
    // Boundary Condition Tests
    // ============================================

    @Test
    fun testInsertEmptyString() {
        val doc = EditorDocument("hello")
        doc.insert(3, "")
        assertEquals("hello", doc.getText())
        assertEquals(5, doc.length)
    }

    @Test
    fun testDeleteEmptyRange() {
        val doc = EditorDocument("hello")
        doc.delete(2, 2) // Empty range
        assertEquals("hello", doc.getText())
    }

    @Test
    fun testReplaceWithSameText() {
        val doc = EditorDocument("hello")
        doc.replace(0, 5, "hello")
        assertEquals("hello", doc.getText())
    }

    @Test
    fun testDeleteEntireDocument() {
        val doc = EditorDocument("hello world")
        doc.delete(0, 11)
        assertEquals("", doc.getText())
        assertEquals(0, doc.length)
        assertEquals(1, doc.lineCount)
    }

    @Test
    fun testReplaceEntireDocument() {
        val doc = EditorDocument("old content")
        doc.replace(0, 11, "new content here")
        assertEquals("new content here", doc.getText())
    }

    @Test
    fun testInsertAtOffset0EmptyDoc() {
        val doc = EditorDocument()
        doc.insert(0, "hello")
        assertEquals("hello", doc.getText())
    }

    @Test
    fun testSingleCharacterOperations() {
        val doc = EditorDocument()
        doc.insert(0, "a")
        assertEquals("a", doc.getText())
        doc.insert(1, "b")
        assertEquals("ab", doc.getText())
        doc.delete(0, 1)
        assertEquals("b", doc.getText())
        doc.delete(0, 1)
        assertEquals("", doc.getText())
    }

    // ============================================
    // Large Document Tests
    // ============================================

    @Test
    fun testLargeDocument10kLines() {
        val doc = EditorDocument()
        val lines = (1..10000).map { "Line $it" }
        doc.setText(lines.joinToString("\n"))

        assertEquals(10000, doc.lineCount)
        assertEquals("Line 1", doc.getLineText(0))
        assertEquals("Line 5000", doc.getLineText(4999))
        assertEquals("Line 10000", doc.getLineText(9999))
    }

    @Test
    fun testLargeDocumentInsertInMiddle() {
        val doc = EditorDocument()
        val initialText = "x".repeat(100000)
        doc.setText(initialText)

        // Insert in the middle (triggers gap movement)
        doc.insert(50000, "INSERTED")

        assertEquals(100008, doc.length)
        assertEquals("INSERTED", doc.getText(50000, 50008))
    }

    @Test
    fun testRapidSequentialInsertsAtDifferentPositions() {
        val doc = EditorDocument("0123456789")

        // Insert at alternating positions to stress gap buffer
        doc.insert(0, "A")  // A0123456789
        doc.insert(11, "B") // A0123456789B
        doc.insert(1, "C")  // AC0123456789B
        doc.insert(12, "D") // AC0123456789DB
        doc.insert(2, "E")  // ACE0123456789DB

        assertEquals("ACE0123456789DB", doc.getText())
    }

    @Test
    fun testManySmallInserts() {
        val doc = EditorDocument()
        // 1000 single character inserts
        for (i in 0 until 1000) {
            doc.insert(doc.length, (('a'.code + (i % 26)).toChar()).toString())
        }
        assertEquals(1000, doc.length)
    }

    // ============================================
    // Listener Tests
    // ============================================

    @Test
    fun testDocumentListenerCalledOnInsert() {
        val doc = EditorDocument("hello")
        var changeReceived: DocumentChange? = null

        doc.addDocumentListener(object : DocumentListener {
            override fun documentChanged(change: DocumentChange) {
                changeReceived = change
            }
        })

        doc.insert(5, " world")

        assertEquals(5, changeReceived?.offset)
        assertEquals("", changeReceived?.oldText)
        assertEquals(" world", changeReceived?.newText)
    }

    @Test
    fun testDocumentListenerCalledOnDelete() {
        val doc = EditorDocument("hello world")
        var changeReceived: DocumentChange? = null

        doc.addDocumentListener(object : DocumentListener {
            override fun documentChanged(change: DocumentChange) {
                changeReceived = change
            }
        })

        doc.delete(5, 11)

        assertEquals(5, changeReceived?.offset)
        assertEquals(" world", changeReceived?.oldText)
        assertEquals("", changeReceived?.newText)
    }

    @Test
    fun testRemoveDocumentListener() {
        val doc = EditorDocument("hello")
        var callCount = 0

        val listener = object : DocumentListener {
            override fun documentChanged(change: DocumentChange) {
                callCount++
            }
        }

        doc.addDocumentListener(listener)
        doc.insert(5, " world")
        assertEquals(1, callCount)

        doc.removeDocumentListener(listener)
        doc.insert(11, "!")
        assertEquals(1, callCount) // Should not have increased
    }

    @Test
    fun testModifyDocumentDuringListenerCallback() {
        val doc = EditorDocument("hello")
        var secondChange: DocumentChange? = null

        doc.addDocumentListener(object : DocumentListener {
            override fun documentChanged(change: DocumentChange) {
                if (change.newText == " world") {
                    // This is the first change, trigger a second change
                    doc.insert(doc.length, "!")
                } else {
                    secondChange = change
                }
            }
        })

        doc.insert(5, " world")

        // Document should have both changes applied
        assertEquals("hello world!", doc.getText())
        assertEquals("!", secondChange?.newText)
    }

    // ============================================
    // Edge Case Line Operations
    // ============================================

    @Test
    fun testDocumentWithOnlyNewlines() {
        val doc = EditorDocument("\n\n\n")
        assertEquals(4, doc.lineCount)
        assertEquals("", doc.getLineText(0))
        assertEquals("", doc.getLineText(1))
        assertEquals("", doc.getLineText(2))
        assertEquals("", doc.getLineText(3))
    }

    @Test
    fun testConsecutiveNewlines() {
        val doc = EditorDocument("a\n\n\nb")
        assertEquals(4, doc.lineCount)
        assertEquals("a", doc.getLineText(0))
        assertEquals("", doc.getLineText(1))
        assertEquals("", doc.getLineText(2))
        assertEquals("b", doc.getLineText(3))
    }

    @Test
    fun testLineOperationsAfterLargeInsert() {
        val doc = EditorDocument("line1\nline2")
        val largeText = "x".repeat(10000) + "\n"
        doc.insert(6, largeText) // Insert at start of "line2"

        // Result: "line1\n" + "xxx...xxx\n" + "line2"
        assertEquals(3, doc.lineCount)
        assertEquals("line1", doc.getLineText(0))
        assertEquals("x".repeat(10000), doc.getLineText(1))
        assertEquals("line2", doc.getLineText(2))
    }

    @Test
    fun testGetLineTextOutOfBounds() {
        val doc = EditorDocument("hello")
        assertFailsWith<IllegalArgumentException> {
            doc.getLineText(5) // Only line 0 exists
        }
    }

    @Test
    fun testPositionToOffsetClampsBeyondEnd() {
        val doc = EditorDocument("hello")
        // Position beyond document end should clamp
        val offset = doc.positionToOffset(EditorPosition(0, 100))
        assertEquals(5, offset) // Clamped to end of line
    }

    @Test
    fun testPositionToOffsetThrowsForInvalidLine() {
        val doc = EditorDocument("hello")
        // Line beyond document should throw
        assertFailsWith<IllegalArgumentException> {
            doc.positionToOffset(EditorPosition(100, 0))
        }
    }

    // ============================================
    // Incremental Line Index Update Tests
    // ============================================

    @Test
    fun testIncrementalLineIndexInsertMultiLine() {
        val doc = EditorDocument("Line 1\nLine 2\nLine 3")
        assertEquals(3, doc.lineCount)

        // Insert 2 lines in the middle (after "Line 1\n")
        doc.insert(7, "New A\nNew B\n")

        assertEquals(5, doc.lineCount)
        assertEquals("Line 1", doc.getLineText(0))
        assertEquals("New A", doc.getLineText(1))
        assertEquals("New B", doc.getLineText(2))
        assertEquals("Line 2", doc.getLineText(3))
        assertEquals("Line 3", doc.getLineText(4))
    }

    @Test
    fun testIncrementalLineIndexDeleteMultiLine() {
        val doc = EditorDocument("A\nB\nC\nD\nE")
        assertEquals(5, doc.lineCount)

        // Delete lines B and C (offsets 2-6, includes "B\nC\n")
        doc.delete(2, 6)

        assertEquals(3, doc.lineCount)
        assertEquals("A", doc.getLineText(0))
        assertEquals("D", doc.getLineText(1))
        assertEquals("E", doc.getLineText(2))
    }

    @Test
    fun testLineIndexConsistencyAfterManyEdits() {
        val doc = EditorDocument()

        // Build document with many inserts
        repeat(100) { i ->
            doc.insert(doc.length, "Line $i\n")
        }

        // Verify line index integrity
        assertEquals(101, doc.lineCount) // 100 lines + trailing empty line

        // Delete some lines from middle
        val startOffset = doc.getLineStartOffset(50)
        val endOffset = doc.getLineStartOffset(60)
        doc.delete(startOffset, endOffset)

        // Verify document is still consistent
        for (i in 0 until doc.lineCount) {
            doc.getLineText(i) // Should not throw
            doc.getLineStartOffset(i) // Should not throw
        }

        assertEquals(91, doc.lineCount) // 101 - 10 deleted lines
    }

    @Test
    fun testInsertNewlinesAtDocumentStart() {
        val doc = EditorDocument("content")
        doc.insert(0, "A\nB\n")

        assertEquals(3, doc.lineCount)
        assertEquals("A", doc.getLineText(0))
        assertEquals("B", doc.getLineText(1))
        assertEquals("content", doc.getLineText(2))
    }

    @Test
    fun testInsertNewlinesAtDocumentEnd() {
        val doc = EditorDocument("content")
        doc.insert(doc.length, "\nA\nB")

        assertEquals(3, doc.lineCount)
        assertEquals("content", doc.getLineText(0))
        assertEquals("A", doc.getLineText(1))
        assertEquals("B", doc.getLineText(2))
    }

    @Test
    fun testDeleteEntireDocumentWithMultipleLines() {
        val doc = EditorDocument("Line1\nLine2\nLine3")
        assertEquals(3, doc.lineCount)

        doc.delete(0, doc.length)

        assertEquals(1, doc.lineCount) // Empty document has 1 line
        assertEquals("", doc.getLineText(0))
    }

    @Test
    fun testAlternatingInsertDelete() {
        val doc = EditorDocument("start")

        // Insert newlines
        doc.insert(5, "\nA\nB")
        assertEquals(3, doc.lineCount)

        // Delete a line
        doc.delete(6, 8) // Delete "A\n"
        assertEquals(2, doc.lineCount)

        // Insert again
        doc.insert(6, "X\nY\n")
        assertEquals(4, doc.lineCount)

        assertEquals("start", doc.getLineText(0))
        assertEquals("X", doc.getLineText(1))
        assertEquals("Y", doc.getLineText(2))
        assertEquals("B", doc.getLineText(3))
    }

    @Test
    fun testDeleteSingleNewline() {
        val doc = EditorDocument("A\nB")
        assertEquals(2, doc.lineCount)
        assertEquals("A", doc.getLineText(0))
        assertEquals("B", doc.getLineText(1))

        doc.delete(1, 2) // Delete just the newline
        assertEquals(1, doc.lineCount)
        assertEquals("AB", doc.getText())
    }

    @Test
    fun testDeleteExactLineRange() {
        // Verifies correct handling when deletion endpoint coincides with a line start.
        // A line starting exactly at endOffset should be REMOVED (not just shifted)
        // because its preceding newline is being deleted, merging it with the previous line.
        val doc = EditorDocument("A\nB\nC")
        assertEquals(3, doc.lineCount)

        doc.delete(0, 2) // Delete "A\n" exactly - removes line starting at offset 2
        // Result: "B\nC" with lineStarts [0, 2]
        assertEquals(2, doc.lineCount)
        assertEquals("B\nC", doc.getText())
        assertEquals("B", doc.getLineText(0))
        assertEquals("C", doc.getLineText(1))
    }

    @Test
    fun testListenerExceptionDoesNotBreakOtherListeners() {
        val doc = EditorDocument("test")
        var listener1Called = false
        var listener2Called = false

        doc.addDocumentListener(object : DocumentListener {
            override fun documentChanged(change: DocumentChange) {
                listener1Called = true
                throw RuntimeException("Listener 1 failed intentionally")
            }
        })

        doc.addDocumentListener(object : DocumentListener {
            override fun documentChanged(change: DocumentChange) {
                listener2Called = true
            }
        })

        // This should not throw - exceptions in listeners are caught
        doc.insert(0, "x")

        assertTrue(listener1Called, "First listener should have been called")
        assertTrue(listener2Called, "Second listener should still be called despite first throwing")
    }
}
