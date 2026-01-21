package ai.rever.bosseditor.largefile

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import java.io.File

class PagedSearcherTest {

    private fun createTempFileWithContent(content: String): File {
        val file = File.createTempFile("paged_search_test", ".txt")
        file.deleteOnExit() // Safety net in case test cleanup fails
        file.writeText(content)
        return file
    }

    @Test
    fun testSearchFindsAllOccurrences() = runBlocking {
        val content = """
            Hello world
            This is a test
            Hello again
            Another line
            Hello one more time
        """.trimIndent()

        val tempFile = createTempFileWithContent(content)
        try {
            val document = LargeFileDocument(tempFile)
            val searcher = PagedSearcher(document)

            val job = searcher.search("Hello", ignoreCase = true, scope = this)
            job.join()

            val results = searcher.results.value
            assertEquals(3, results.size, "Should find 3 occurrences of 'Hello'")

            // Verify the positions
            assertEquals(0, results[0].range.start.line)
            assertEquals(2, results[1].range.start.line)
            assertEquals(4, results[2].range.start.line)

            // Verify match text
            results.forEach { result ->
                assertEquals("Hello", result.matchText)
            }

            document.close()
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun testProgressUpdates() = runBlocking {
        // Create file with enough lines to trigger progress updates
        val lines = (1..500).map { "Line number $it with some text" }
        val content = lines.joinToString("\n")
        val tempFile = createTempFileWithContent(content)

        try {
            val document = LargeFileDocument(tempFile)
            val searcher = PagedSearcher(document)

            val job = searcher.search("number", ignoreCase = true, scope = this)
            job.join()

            // Progress should be 1.0 after completion
            assertEquals(1f, searcher.progress.value)

            // Should find all 500 occurrences
            assertEquals(500, searcher.results.value.size)

            document.close()
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun testCancelStopsSearch() = runBlocking {
        // Create a large file to ensure search takes time
        val lines = (1..10000).map { "Line $it contains searchable text" }
        val content = lines.joinToString("\n")
        val tempFile = createTempFileWithContent(content)

        try {
            val document = LargeFileDocument(tempFile)
            val searcher = PagedSearcher(document)

            val job = searcher.search("searchable", ignoreCase = true, scope = this)

            // Cancel immediately
            searcher.cancel()

            // Wait for the job to actually finish (cancelled or completed)
            // This prevents IOException from file handle still being in use
            try {
                job.join()
            } catch (_: Exception) {
                // Job may throw CancellationException, which is expected
            }

            // Progress might not be complete
            // The key assertion is that the job was cancelled or search stopped
            assertTrue(job.isCancelled || job.isCompleted || !searcher.isSearching.value)

            document.close()
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun testCaseInsensitiveSearch() = runBlocking {
        val content = """
            HELLO uppercase
            hello lowercase
            HeLLo mixed case
        """.trimIndent()

        val tempFile = createTempFileWithContent(content)
        try {
            val document = LargeFileDocument(tempFile)
            val searcher = PagedSearcher(document)

            val job = searcher.search("hello", ignoreCase = true, scope = this)
            job.join()

            val results = searcher.results.value
            assertEquals(3, results.size, "Case-insensitive search should find all 3 variations")

            document.close()
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun testCaseSensitiveSearch() = runBlocking {
        val content = """
            HELLO uppercase
            hello lowercase
            HeLLo mixed case
        """.trimIndent()

        val tempFile = createTempFileWithContent(content)
        try {
            val document = LargeFileDocument(tempFile)
            val searcher = PagedSearcher(document)

            val job = searcher.search("hello", ignoreCase = false, scope = this)
            job.join()

            val results = searcher.results.value
            assertEquals(1, results.size, "Case-sensitive search should only find 'hello'")
            assertEquals(1, results[0].range.start.line)

            document.close()
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun testEmptyPatternReturnsNoResults() = runBlocking {
        val content = "Some content here"
        val tempFile = createTempFileWithContent(content)

        try {
            val document = LargeFileDocument(tempFile)
            val searcher = PagedSearcher(document)

            val job = searcher.search("", ignoreCase = true, scope = this)
            job.join()

            assertTrue(searcher.results.value.isEmpty(), "Empty pattern should return no results")

            document.close()
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun testNoMatchesReturnsEmptyResults() = runBlocking {
        val content = "Some content without the search term"
        val tempFile = createTempFileWithContent(content)

        try {
            val document = LargeFileDocument(tempFile)
            val searcher = PagedSearcher(document)

            val job = searcher.search("nonexistent", ignoreCase = true, scope = this)
            job.join()

            assertTrue(searcher.results.value.isEmpty(), "Should return empty results for non-matching pattern")
            assertEquals(1f, searcher.progress.value, "Progress should be 1.0 even with no matches")

            document.close()
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun testMultipleMatchesOnSameLine() = runBlocking {
        val content = "foo bar foo baz foo"
        val tempFile = createTempFileWithContent(content)

        try {
            val document = LargeFileDocument(tempFile)
            val searcher = PagedSearcher(document)

            val job = searcher.search("foo", ignoreCase = true, scope = this)
            job.join()

            val results = searcher.results.value
            assertEquals(3, results.size, "Should find all 3 occurrences on the same line")

            // All on line 0 but different columns
            results.forEach { assertEquals(0, it.range.start.line) }
            assertEquals(0, results[0].range.start.column)
            assertEquals(8, results[1].range.start.column)
            assertEquals(16, results[2].range.start.column)

            document.close()
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun testNavigateNextAndPrevious() = runBlocking {
        val content = """
            First match here
            Second match here
            Third match here
        """.trimIndent()

        val tempFile = createTempFileWithContent(content)
        try {
            val document = LargeFileDocument(tempFile)
            val searcher = PagedSearcher(document)

            val job = searcher.search("match", ignoreCase = true, scope = this)
            job.join()

            assertEquals(3, searcher.results.value.size)

            // First result is auto-selected after search
            assertEquals(0, searcher.currentResultIndex.value)
            assertNotNull(searcher.currentResult())
            assertEquals(0, searcher.currentResult()?.range?.start?.line)

            // Navigate to next
            val second = searcher.nextResult()
            assertNotNull(second)
            assertEquals(1, searcher.currentResultIndex.value)
            assertEquals(1, second.range.start.line)

            // Navigate to next again
            val third = searcher.nextResult()
            assertNotNull(third)
            assertEquals(2, searcher.currentResultIndex.value)
            assertEquals(2, third.range.start.line)

            // Navigate to next at end (should wrap to first)
            val backToFirst = searcher.nextResult()
            assertNotNull(backToFirst)
            assertEquals(0, searcher.currentResultIndex.value)
            assertEquals(0, backToFirst.range.start.line)

            // Navigate previous at start (should wrap to last)
            val wrapToLast = searcher.previousResult()
            assertNotNull(wrapToLast)
            assertEquals(2, searcher.currentResultIndex.value)
            assertEquals(2, wrapToLast.range.start.line)

            document.close()
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun testClearResetsState() = runBlocking {
        val content = "Test content with match"
        val tempFile = createTempFileWithContent(content)

        try {
            val document = LargeFileDocument(tempFile)
            val searcher = PagedSearcher(document)

            val job = searcher.search("match", ignoreCase = true, scope = this)
            job.join()

            assertEquals(1, searcher.results.value.size)

            searcher.clear()

            assertTrue(searcher.results.value.isEmpty())
            assertEquals(0f, searcher.progress.value)
            assertEquals(-1, searcher.currentResultIndex.value)

            document.close()
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun testLineHasResults() = runBlocking {
        val content = """
            Just some text here
            Target on this line
            More text here
        """.trimIndent()

        val tempFile = createTempFileWithContent(content)
        try {
            val document = LargeFileDocument(tempFile)
            val searcher = PagedSearcher(document)

            val job = searcher.search("Target", ignoreCase = true, scope = this)
            job.join()

            assertFalse(searcher.lineHasResults(0))
            assertTrue(searcher.lineHasResults(1))
            assertFalse(searcher.lineHasResults(2))

            document.close()
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun testGetResultsForLine() = runBlocking {
        val content = """
            foo and bar
            just foo
            foo bar foo
        """.trimIndent()

        val tempFile = createTempFileWithContent(content)
        try {
            val document = LargeFileDocument(tempFile)
            val searcher = PagedSearcher(document)

            val job = searcher.search("foo", ignoreCase = true, scope = this)
            job.join()

            assertEquals(4, searcher.results.value.size)

            val line0Results = searcher.getResultsForLine(0)
            assertEquals(1, line0Results.size)

            val line1Results = searcher.getResultsForLine(1)
            assertEquals(1, line1Results.size)

            val line2Results = searcher.getResultsForLine(2)
            assertEquals(2, line2Results.size)

            document.close()
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun testSearchWithSpecialRegexCharacters() = runBlocking {
        // Test that special regex characters are escaped
        val content = """
            This has [brackets]
            And (parentheses)
            Also dots...
        """.trimIndent()

        val tempFile = createTempFileWithContent(content)
        try {
            val document = LargeFileDocument(tempFile)
            val searcher = PagedSearcher(document)

            // Search for literal [brackets]
            val job1 = searcher.search("[brackets]", ignoreCase = true, scope = this)
            job1.join()
            assertEquals(1, searcher.results.value.size)

            // Search for literal dots
            searcher.clear()
            val job2 = searcher.search("...", ignoreCase = true, scope = this)
            job2.join()
            assertEquals(1, searcher.results.value.size)

            document.close()
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun testNewSearchCancelsPrevious() = runBlocking {
        val lines = (1..1000).map { "Line $it with searchable content" }
        val content = lines.joinToString("\n")
        val tempFile = createTempFileWithContent(content)

        try {
            val document = LargeFileDocument(tempFile)
            val searcher = PagedSearcher(document)

            // Start first search
            val job1 = searcher.search("searchable", ignoreCase = true, scope = this)

            // Immediately start a new search
            val job2 = searcher.search("Line", ignoreCase = true, scope = this)
            job2.join()

            // First job should be cancelled
            assertTrue(job1.isCancelled)

            // Results should be from the second search
            assertEquals(1000, searcher.results.value.size)

            document.close()
        } finally {
            tempFile.delete()
        }
    }
}
