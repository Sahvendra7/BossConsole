package ai.rever.bosseditor.largefile

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import java.io.File
import java.io.RandomAccessFile

class CharsetBoundaryDetectorTest {

    @Test
    fun testAsciiBytesAreNotContinuation() {
        // ASCII bytes (0x00-0x7F) are never continuation bytes
        for (i in 0x00..0x7F) {
            assertFalse(
                CharsetBoundaryDetector.isUtf8ContinuationByte(i.toByte()),
                "ASCII byte 0x${i.toString(16)} should not be a continuation byte"
            )
        }
    }

    @Test
    fun testUtf8ContinuationBytesDetected() {
        // Continuation bytes have pattern 10xxxxxx (0x80-0xBF)
        for (i in 0x80..0xBF) {
            assertTrue(
                CharsetBoundaryDetector.isUtf8ContinuationByte(i.toByte()),
                "Byte 0x${i.toString(16)} should be a continuation byte"
            )
        }
    }

    @Test
    fun testUtf8StartBytesAreNotContinuation() {
        // 2-byte start bytes: 110xxxxx (0xC0-0xDF)
        for (i in 0xC0..0xDF) {
            assertFalse(
                CharsetBoundaryDetector.isUtf8ContinuationByte(i.toByte()),
                "2-byte start 0x${i.toString(16)} should not be a continuation byte"
            )
        }

        // 3-byte start bytes: 1110xxxx (0xE0-0xEF)
        for (i in 0xE0..0xEF) {
            assertFalse(
                CharsetBoundaryDetector.isUtf8ContinuationByte(i.toByte()),
                "3-byte start 0x${i.toString(16)} should not be a continuation byte"
            )
        }

        // 4-byte start bytes: 11110xxx (0xF0-0xF7)
        for (i in 0xF0..0xF7) {
            assertFalse(
                CharsetBoundaryDetector.isUtf8ContinuationByte(i.toByte()),
                "4-byte start 0x${i.toString(16)} should not be a continuation byte"
            )
        }
    }

    @Test
    fun testFindNextUtf8BoundaryWithAscii() {
        // ASCII-only content - every byte is a boundary
        val content = "Hello World"
        val tempFile = createTempFileWithBytes(content.toByteArray(Charsets.UTF_8))

        try {
            RandomAccessFile(tempFile, "r").use { raf ->
                // Position 0 is already at boundary (start of 'H')
                assertEquals(0L, CharsetBoundaryDetector.findNextUtf8Boundary(raf, 0, 128))

                // Position 5 is at boundary (space character)
                assertEquals(5L, CharsetBoundaryDetector.findNextUtf8Boundary(raf, 5, 128))
            }
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun testFindNextUtf8BoundaryWithMultiByteCharacters() {
        // "中" is 3 bytes: E4 B8 AD
        val content = "中"
        val bytes = content.toByteArray(Charsets.UTF_8)
        assertEquals(3, bytes.size, "Chinese character should be 3 bytes")

        val tempFile = createTempFileWithBytes(bytes)

        try {
            RandomAccessFile(tempFile, "r").use { raf ->
                // Position 0 is at boundary (start of char)
                assertEquals(0L, CharsetBoundaryDetector.findNextUtf8Boundary(raf, 0, 128))

                // Position 1 is a continuation byte, should skip to position 3 (EOF)
                assertEquals(3L, CharsetBoundaryDetector.findNextUtf8Boundary(raf, 1, 128))

                // Position 2 is also a continuation byte, should skip to position 3 (EOF)
                assertEquals(3L, CharsetBoundaryDetector.findNextUtf8Boundary(raf, 2, 128))
            }
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun testFindPreviousUtf8BoundaryWithAscii() {
        val content = "Hello World"
        val tempFile = createTempFileWithBytes(content.toByteArray(Charsets.UTF_8))

        try {
            RandomAccessFile(tempFile, "r").use { raf ->
                // From position 5, previous boundary should be at 4 ('o')
                assertEquals(4L, CharsetBoundaryDetector.findPreviousUtf8Boundary(raf, 5, 128))

                // From position 1, previous boundary should be at 0 ('H')
                assertEquals(0L, CharsetBoundaryDetector.findPreviousUtf8Boundary(raf, 1, 128))
            }
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun testFindPreviousUtf8BoundaryWithMultiByteCharacters() {
        // "A中B" - A is 1 byte, 中 is 3 bytes, B is 1 byte
        // Bytes: 41 E4 B8 AD 42
        val content = "A中B"
        val bytes = content.toByteArray(Charsets.UTF_8)
        assertEquals(5, bytes.size)

        val tempFile = createTempFileWithBytes(bytes)

        try {
            RandomAccessFile(tempFile, "r").use { raf ->
                // From position 5 (after 'B'), previous boundary is at 4 ('B')
                assertEquals(4L, CharsetBoundaryDetector.findPreviousUtf8Boundary(raf, 5, 128))

                // From position 4 ('B'), previous boundary is at 1 (start of '中')
                assertEquals(1L, CharsetBoundaryDetector.findPreviousUtf8Boundary(raf, 4, 128))

                // From position 3 (continuation byte AD), should go back to 1 (start of '中')
                assertEquals(1L, CharsetBoundaryDetector.findPreviousUtf8Boundary(raf, 3, 128))

                // From position 1 (start of '中'), previous boundary is at 0 ('A')
                assertEquals(0L, CharsetBoundaryDetector.findPreviousUtf8Boundary(raf, 1, 128))
            }
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun testFindNextBoundaryAtEof() {
        val content = "Test"
        val tempFile = createTempFileWithBytes(content.toByteArray(Charsets.UTF_8))

        try {
            RandomAccessFile(tempFile, "r").use { raf ->
                // At or beyond EOF should return EOF
                assertEquals(4L, CharsetBoundaryDetector.findNextUtf8Boundary(raf, 4, 128))
                assertEquals(4L, CharsetBoundaryDetector.findNextUtf8Boundary(raf, 100, 128))
            }
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun testFindPreviousBoundaryAtStart() {
        val content = "Test"
        val tempFile = createTempFileWithBytes(content.toByteArray(Charsets.UTF_8))

        try {
            RandomAccessFile(tempFile, "r").use { raf ->
                // At or before start should return 0
                assertEquals(0L, CharsetBoundaryDetector.findPreviousUtf8Boundary(raf, 0, 128))
            }
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun testMaxShiftLimitRespected() {
        // Create content where we'd need to shift more than maxShift to find boundary
        // "中" is 3 bytes, so create multiple characters
        val content = "中中中中中" // 15 bytes
        val bytes = content.toByteArray(Charsets.UTF_8)
        val tempFile = createTempFileWithBytes(bytes)

        try {
            RandomAccessFile(tempFile, "r").use { raf ->
                // With maxShift of 1, starting at position 1 (continuation byte)
                // should only shift 1 byte forward to position 2 (still continuation)
                val result = CharsetBoundaryDetector.findNextUtf8Boundary(raf, 1, 1)
                // Should stop after 1 shift
                assertEquals(2L, result)
            }
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun testFourByteUtf8Characters() {
        // Emoji "😀" is 4 bytes: F0 9F 98 80
        val content = "😀"
        val bytes = content.toByteArray(Charsets.UTF_8)
        assertEquals(4, bytes.size, "Emoji should be 4 bytes")

        val tempFile = createTempFileWithBytes(bytes)

        try {
            RandomAccessFile(tempFile, "r").use { raf ->
                // Position 0 is at boundary
                assertEquals(0L, CharsetBoundaryDetector.findNextUtf8Boundary(raf, 0, 128))

                // Positions 1, 2, 3 are continuation bytes, should skip to 4 (EOF)
                assertEquals(4L, CharsetBoundaryDetector.findNextUtf8Boundary(raf, 1, 128))
                assertEquals(4L, CharsetBoundaryDetector.findNextUtf8Boundary(raf, 2, 128))
                assertEquals(4L, CharsetBoundaryDetector.findNextUtf8Boundary(raf, 3, 128))
            }
        } finally {
            tempFile.delete()
        }
    }

    private fun createTempFileWithBytes(bytes: ByteArray): File {
        val tempFile = File.createTempFile("charset_test_", ".bin")
        tempFile.writeBytes(bytes)
        return tempFile
    }
}
