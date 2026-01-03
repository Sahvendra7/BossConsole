package ai.rever.bosseditor.vcs

import ai.rever.bosseditor.features.FileBlameInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import java.io.File
import java.lang.reflect.Method

class GitServiceTest {

    private val gitService = GitService()

    @Test
    fun testParseBlameOutputSimple() {
        // Git blame porcelain format: no leading spaces, tab before content
        val output = buildString {
            appendLine("abc123def456789012345678901234567890abcd 1 1 1")
            appendLine("author John Doe")
            appendLine("author-mail <john@example.com>")
            appendLine("author-time 1609459200")
            appendLine("author-tz +0000")
            appendLine("committer John Doe")
            appendLine("committer-mail <john@example.com>")
            appendLine("committer-time 1609459200")
            appendLine("committer-tz +0000")
            appendLine("summary Initial commit")
            appendLine("filename test.kt")
            appendLine("\tline content here")  // Tab before content
        }

        val result = parseBlameOutput(output, "/test/path.kt")

        assertNotNull(result)
        assertEquals("/test/path.kt", result.filePath)
        assertTrue(result.lines.containsKey(0), "Expected line 0 to exist in blame map")

        val blameInfo = result.lines[0]
        assertNotNull(blameInfo)
        assertEquals("abc123def456789012345678901234567890abcd", blameInfo.commitHash)
        assertEquals("John Doe", blameInfo.author)
        assertEquals("john@example.com", blameInfo.authorEmail)
        assertEquals(1609459200L, blameInfo.timestamp)
        assertEquals("Initial commit", blameInfo.summary)
    }

    @Test
    fun testParseBlameOutputMultipleLines() {
        val output = buildString {
            // First commit, two lines
            appendLine("abc123def456789012345678901234567890abcd 1 1 2")
            appendLine("author John Doe")
            appendLine("author-mail <john@example.com>")
            appendLine("author-time 1609459200")
            appendLine("author-tz +0000")
            appendLine("committer John Doe")
            appendLine("committer-mail <john@example.com>")
            appendLine("committer-time 1609459200")
            appendLine("committer-tz +0000")
            appendLine("summary First commit")
            appendLine("filename test.kt")
            appendLine("\tfirst line")
            // Continuation of same commit
            appendLine("abc123def456789012345678901234567890abcd 2 2")
            appendLine("\tsecond line")
            // Second commit, one line
            appendLine("def456789012345678901234567890abcdef1234 3 3 1")
            appendLine("author Jane Smith")
            appendLine("author-mail <jane@example.com>")
            appendLine("author-time 1609545600")
            appendLine("author-tz +0000")
            appendLine("committer Jane Smith")
            appendLine("committer-mail <jane@example.com>")
            appendLine("committer-time 1609545600")
            appendLine("committer-tz +0000")
            appendLine("summary Second commit")
            appendLine("filename test.kt")
            appendLine("\tthird line")
        }

        val result = parseBlameOutput(output, "/test/path.kt")

        assertNotNull(result)
        assertEquals(3, result.lines.size)

        // Line 0 and 1 should be from John Doe
        assertEquals("John Doe", result.lines[0]?.author)
        assertEquals("John Doe", result.lines[1]?.author)

        // Line 2 should be from Jane Smith
        assertEquals("Jane Smith", result.lines[2]?.author)
    }

    @Test
    fun testParseBlameOutputMalformedLineNumber() {
        // If line number parsing fails, the entry should be skipped
        val output = buildString {
            appendLine("abc123def456789012345678901234567890abcd 1 INVALID 1")
            appendLine("author John Doe")
            appendLine("author-mail <john@example.com>")
            appendLine("author-time 1609459200")
            appendLine("author-tz +0000")
            appendLine("committer John Doe")
            appendLine("committer-mail <john@example.com>")
            appendLine("committer-time 1609459200")
            appendLine("committer-tz +0000")
            appendLine("summary Test commit")
            appendLine("filename test.kt")
            appendLine("\tline content")
        }

        val result = parseBlameOutput(output, "/test/path.kt")

        assertNotNull(result)
        // Entry with invalid line number should be skipped
        assertTrue(result.lines.isEmpty(), "Lines with invalid line numbers should be skipped")
    }

    @Test
    fun testParseBlameOutputInsufficientParts() {
        // Line with less than 3 space-separated parts should be skipped
        val output = buildString {
            appendLine("abc123def456789012345678901234567890abcd 1")  // Only 2 parts
            appendLine("author John Doe")
            appendLine("\tline content")
        }

        val result = parseBlameOutput(output, "/test/path.kt")

        assertNotNull(result)
        assertTrue(result.lines.isEmpty())
    }

    @Test
    fun testParseBlameOutputEmptyInput() {
        val result = parseBlameOutput("", "/test/path.kt")

        assertNotNull(result)
        assertEquals("/test/path.kt", result.filePath)
        assertTrue(result.lines.isEmpty())
    }

    @Test
    fun testParseBlameOutputCachedCommit() {
        // Same commit hash on multiple lines should use cached info
        val output = buildString {
            appendLine("abc123def456789012345678901234567890abcd 1 1 3")
            appendLine("author Cached Author")
            appendLine("author-mail <cached@example.com>")
            appendLine("author-time 1609459200")
            appendLine("author-tz +0000")
            appendLine("committer Cached Author")
            appendLine("committer-mail <cached@example.com>")
            appendLine("committer-time 1609459200")
            appendLine("committer-tz +0000")
            appendLine("summary Cached commit")
            appendLine("filename test.kt")
            appendLine("\tfirst line")
            // Continuation lines use cached info
            appendLine("abc123def456789012345678901234567890abcd 2 2")
            appendLine("\tsecond line")
            appendLine("abc123def456789012345678901234567890abcd 3 3")
            appendLine("\tthird line")
        }

        val result = parseBlameOutput(output, "/test/path.kt")

        assertNotNull(result)
        assertEquals(3, result.lines.size)

        // All three lines should have the same author (cached)
        assertEquals("Cached Author", result.lines[0]?.author)
        assertEquals("Cached Author", result.lines[1]?.author)
        assertEquals("Cached Author", result.lines[2]?.author)
    }

    @Test
    fun testIsGitRepositoryNonExistent() = runBlocking {
        val nonExistentDir = File("/nonexistent/path/that/does/not/exist")
        val result = gitService.isGitRepository(nonExistentDir)
        assertFalse(result)
    }

    @Test
    fun testIsGitRepositoryNull() = runBlocking {
        val result = gitService.isGitRepository(null)
        assertFalse(result)
    }

    @Test
    fun testBlameNonExistentFile() = runBlocking {
        val result = gitService.blame("/nonexistent/file/path.kt")
        assertNull(result)
    }

    // Helper function to access private parseBlameOutput method via reflection
    private fun parseBlameOutput(output: String, filePath: String): FileBlameInfo {
        val method: Method = GitService::class.java.getDeclaredMethod(
            "parseBlameOutput",
            String::class.java,
            String::class.java
        )
        method.isAccessible = true
        return method.invoke(gitService, output, filePath) as FileBlameInfo
    }

    private fun assertFalse(condition: Boolean) {
        assertTrue(!condition)
    }
}
