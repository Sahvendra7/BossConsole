package ai.rever.bosseditor.largefile

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class LargeFileAdapterTest {

    @Test
    fun testReadPage0ReturnsApproximately8KBText() {
        val content = "a".repeat(20_000) // 20KB of ASCII content
        val tempFile = createTempFileWithContent(content)

        try {
            val adapter = LargeFileAdapter(tempFile)
            val page = adapter.getPageText(0)

            assertNotNull(page)
            assertEquals(0L, page.pageNumber)
            assertEquals(0L, page.byteStart)
            // Page should be approximately 8KB (may be slightly more due to boundary alignment)
            assertTrue(page.text.length >= LargeFileConstants.PAGE_SIZE - LargeFileConstants.MAX_PAGE_BORDER_SHIFT)
            assertTrue(page.text.length <= LargeFileConstants.PAGE_SIZE + LargeFileConstants.MAX_PAGE_BORDER_SHIFT)
            assertFalse(page.isLastPage)

            adapter.close()
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun testReadLastPageReturnsRemainingText() {
        val content = "b".repeat(20_000) // 20KB of ASCII content
        val tempFile = createTempFileWithContent(content)

        try {
            val adapter = LargeFileAdapter(tempFile)
            val lastPageNumber = adapter.pagesAmount - 1
            val page = adapter.getPageText(lastPageNumber)

            assertNotNull(page)
            assertEquals(lastPageNumber, page.pageNumber)
            assertTrue(page.isLastPage)
            assertEquals(adapter.fileSize, page.byteEnd)
            assertTrue(page.text.isNotEmpty())

            adapter.close()
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun testInvalidPageNumberReturnsNull() {
        val content = "c".repeat(10_000)
        val tempFile = createTempFileWithContent(content)

        try {
            val adapter = LargeFileAdapter(tempFile)

            assertNull(adapter.getPageText(-1))
            assertNull(adapter.getPageText(adapter.pagesAmount))
            assertNull(adapter.getPageText(adapter.pagesAmount + 100))

            adapter.close()
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun testUtf8BoundaryNoGarbledCharacters() {
        // Create content with multi-byte UTF-8 characters at page boundaries
        // Chinese characters are 3 bytes each in UTF-8
        val chineseChars = "中文字符" // Each char is 3 bytes
        val builder = StringBuilder()
        // Fill with enough content to create multiple pages
        repeat(3000) {
            builder.append(chineseChars)
        }
        val content = builder.toString()
        val tempFile = createTempFileWithContent(content)

        try {
            val adapter = LargeFileAdapter(tempFile)

            // Read all pages and verify no garbled characters
            for (i in 0 until adapter.pagesAmount) {
                val page = adapter.getPageText(i)
                assertNotNull(page, "Page $i should not be null")

                // Check that text doesn't contain replacement character (garbled UTF-8)
                assertFalse(
                    page.text.contains('\uFFFD'),
                    "Page $i contains garbled UTF-8 characters"
                )

                // Verify all characters are valid Chinese or expected
                for (char in page.text) {
                    assertTrue(
                        char.isLetterOrDigit() || char.isWhitespace() || Character.isIdeographic(char.code),
                        "Unexpected character: ${char.code}"
                    )
                }
            }

            adapter.close()
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun testConcurrentReadsAreThreadSafe() {
        val content = "d".repeat(100_000) // 100KB
        val tempFile = createTempFileWithContent(content)

        try {
            val adapter = LargeFileAdapter(tempFile)
            val executor = Executors.newFixedThreadPool(10)
            val latch = CountDownLatch(100)
            val errorOccurred = AtomicBoolean(false)

            repeat(100) { iteration ->
                executor.submit {
                    try {
                        val pageNum = (iteration % adapter.pagesAmount)
                        val page = adapter.getPageText(pageNum)
                        if (page == null) {
                            errorOccurred.set(true)
                        }
                    } catch (e: Exception) {
                        errorOccurred.set(true)
                        println("Concurrent read error: ${e.message}")
                    } finally {
                        latch.countDown()
                    }
                }
            }

            assertTrue(latch.await(30, TimeUnit.SECONDS), "Concurrent reads timed out")
            assertFalse(errorOccurred.get(), "Errors occurred during concurrent reads")

            executor.shutdown()
            adapter.close()
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun testCloseReleasesFileHandle() {
        val content = "e".repeat(1000)
        val tempFile = createTempFileWithContent(content)

        try {
            val adapter = LargeFileAdapter(tempFile)
            adapter.getPageText(0) // Read something first
            adapter.close()

            // After close, the file should be deletable (handle released)
            assertTrue(tempFile.delete(), "File should be deletable after close")

            // Recreate for cleanup block
            tempFile.createNewFile()
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun testEmptyFile() {
        val tempFile = createTempFileWithContent("")

        try {
            val adapter = LargeFileAdapter(tempFile)

            assertEquals(0L, adapter.fileSize)
            assertEquals(0L, adapter.pagesAmount)
            assertNull(adapter.getPageText(0))

            adapter.close()
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun testSmallFileSinglePage() {
        val content = "Small content"
        val tempFile = createTempFileWithContent(content)

        try {
            val adapter = LargeFileAdapter(tempFile)

            assertEquals(1L, adapter.pagesAmount)

            val page = adapter.getPageText(0)
            assertNotNull(page)
            assertEquals(content, page.text)
            assertTrue(page.isLastPage)
            assertEquals(0L, page.byteStart)
            assertEquals(content.toByteArray(Charsets.UTF_8).size.toLong(), page.byteEnd)

            adapter.close()
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun testLineCountCalculation() {
        val content = "line1\nline2\nline3"
        val tempFile = createTempFileWithContent(content)

        try {
            val adapter = LargeFileAdapter(tempFile)
            val page = adapter.getPageText(0)

            assertNotNull(page)
            assertEquals(3, page.lineCount)

            adapter.close()
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun testPerformanceReadPageUnder10ms() {
        // Create a larger file to test performance
        val content = "f".repeat(1_000_000) // 1MB
        val tempFile = createTempFileWithContent(content)

        try {
            val adapter = LargeFileAdapter(tempFile)

            // Warm up
            adapter.getPageText(0)

            // Measure time
            val startTime = System.nanoTime()
            adapter.getPageText(adapter.pagesAmount / 2)
            val elapsed = System.nanoTime() - startTime
            val elapsedMs = elapsed / 1_000_000.0

            assertTrue(
                elapsedMs < 10.0,
                "Page read took ${elapsedMs}ms, expected < 10ms"
            )

            adapter.close()
        } finally {
            tempFile.delete()
        }
    }

    private fun createTempFileWithContent(content: String): File {
        val tempFile = File.createTempFile("largefile_test_", ".txt")
        tempFile.writeText(content, Charsets.UTF_8)
        return tempFile
    }
}
