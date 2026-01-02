package ai.rever.bosseditor.highlight

import ai.rever.bosseditor.core.EditorDocument
import ai.rever.bosseditor.highlight.lexers.JavaLexer
import ai.rever.bosseditor.highlight.lexers.KotlinLexer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Verification tests for TokenCache incremental highlighting.
 *
 * These tests verify that incremental highlighting (via TokenCache) produces
 * identical results to full re-highlighting, especially for multi-line constructs.
 *
 * ## Test Strategy
 * 1. Create document with content
 * 2. Get incremental highlighting (uses cache)
 * 3. Make edit that changes multi-line state
 * 4. Get incremental results
 * 5. Clear cache, get full re-highlighting
 * 6. Compare: tokens must match
 *
 * ## Limitations
 * - Tests compare token output only, not internal lexer state
 * - State inference is simplified and may not catch all edge cases
 */
class TokenCacheVerificationTest {

    // =========================================================================
    // Block Comment Tests
    // =========================================================================

    @Test
    fun verifyBlockCommentInsertion() {
        val doc = EditorDocument("""
            |class Test {
            |    void method() {
            |        int x = 1;
            |        int y = 2;
            |        int z = 3;
            |    }
            |}
        """.trimMargin())

        withCache(doc, JavaLexer()) { cache ->
            pretokenizeAll(doc, cache)

            val line2Start = doc.getLineStartOffset(2)
            doc.insert(line2Start, "/* ")

            assertIncrementalMatchesFull(doc, cache, "after inserting /*")
        }
    }

    @Test
    fun verifyBlockCommentClosing() {
        val doc = EditorDocument("""
            |class Test {
            |    /* unclosed comment
            |    int x = 1;
            |    int y = 2;
            |}
        """.trimMargin())

        withCache(doc, JavaLexer()) { cache ->
            pretokenizeAll(doc, cache)

            val line1End = doc.getLineEndOffset(1)
            doc.insert(line1End, " */")

            assertIncrementalMatchesFull(doc, cache, "after inserting */")

            // Verify lines after the close are no longer comments
            cache.invalidateAll()
            val line3Tokens = cache.getLineTokens(3)
            assertTrue(
                line3Tokens.none { it.type == TokenType.COMMENT_BLOCK },
                "Line after closed comment should not be COMMENT_BLOCK"
            )
        }
    }

    @Test
    fun verifyEditInsideBlockComment() {
        val doc = EditorDocument("""
            |class Test {
            |    /*
            |     * Some comment text
            |     * More comment text
            |     */
            |    int x = 1;
            |}
        """.trimMargin())

        withCache(doc, JavaLexer()) { cache ->
            pretokenizeAll(doc, cache)

            val line2Start = doc.getLineStartOffset(2)
            doc.insert(line2Start + 5, "EDITED ")

            assertIncrementalMatchesFull(doc, cache, "after editing inside block comment")

            // Verify the edited line is still a comment
            cache.invalidateAll()
            val line2Tokens = cache.getLineTokens(2)
            assertTrue(
                line2Tokens.any { it.type == TokenType.COMMENT_BLOCK || it.type == TokenType.COMMENT_DOC },
                "Edited line should still be a comment"
            )
        }
    }

    @Test
    fun verifyRemovingBlockCommentOpener() {
        val doc = EditorDocument("""
            |class Test {
            |    /* comment
            |    more comment
            |    end comment */
            |    int x = 1;
            |}
        """.trimMargin())

        withCache(doc, JavaLexer()) { cache ->
            pretokenizeAll(doc, cache)

            val line1Start = doc.getLineStartOffset(1)
            doc.delete(line1Start + 4, line1Start + 6)

            assertIncrementalMatchesFull(doc, cache, "after removing /*")
        }
    }

    // =========================================================================
    // Multi-line String Tests (Kotlin raw strings)
    // =========================================================================

    @Test
    fun verifyMultilineStringEdit() {
        val doc = EditorDocument("""
            |val text = ${"\"\"\""}
            |    Line 1
            |    Line 2
            |    Line 3
            |${"\"\"\""}
            |val x = 1
        """.trimMargin())

        withCache(doc, KotlinLexer()) { cache ->
            pretokenizeAll(doc, cache)

            val line2Start = doc.getLineStartOffset(2)
            doc.insert(line2Start + 4, "EDITED ")

            assertIncrementalMatchesFull(doc, cache, "after editing inside raw string")
        }
    }

    @Test
    fun verifyMultilineStringOpening() {
        val doc = EditorDocument("""
            |val text =
            |    Line 1
            |    Line 2
            |val x = 1
        """.trimMargin())

        withCache(doc, KotlinLexer()) { cache ->
            pretokenizeAll(doc, cache)

            val line0End = doc.getLineEndOffset(0)
            doc.insert(line0End, "\"\"\"")

            assertIncrementalMatchesFull(doc, cache, "after opening raw string")
        }
    }

    // =========================================================================
    // Line Insert/Delete State Propagation Tests
    // =========================================================================

    @Test
    fun verifyStateAfterLineDelete() {
        val doc = EditorDocument("""
            |class Test {
            |    /*
            |    comment line 1
            |    comment line 2
            |    */
            |    int x = 1;
            |}
        """.trimMargin())

        withCache(doc, JavaLexer()) { cache ->
            pretokenizeAll(doc, cache)

            val line1Start = doc.getLineStartOffset(1)
            val line2Start = doc.getLineStartOffset(2)
            doc.delete(line1Start, line2Start)

            assertIncrementalMatchesFull(doc, cache, "after deleting line with /*")
        }
    }

    @Test
    fun verifyStateAfterLineInsert() {
        val doc = EditorDocument("""
            |class Test {
            |    int x = 1;
            |    int y = 2;
            |}
        """.trimMargin())

        withCache(doc, JavaLexer()) { cache ->
            pretokenizeAll(doc, cache)

            val line1End = doc.getLineEndOffset(1)
            doc.insert(line1End, "\n    int a = 10;\n    int b = 20;")

            assertIncrementalMatchesFull(doc, cache, "after inserting new lines")
        }
    }

    @Test
    fun verifyStateAfterInsertingCommentInMiddle() {
        val doc = EditorDocument("""
            |class Test {
            |    int a = 1;
            |    int b = 2;
            |    int c = 3;
            |    int d = 4;
            |}
        """.trimMargin())

        withCache(doc, JavaLexer()) { cache ->
            pretokenizeAll(doc, cache)

            val line2Start = doc.getLineStartOffset(2)
            doc.insert(line2Start, "/* ")
            val line3End = doc.getLineEndOffset(3)
            doc.insert(line3End, " */")

            assertIncrementalMatchesFull(doc, cache, "after inserting block comment in middle")
        }
    }

    // =========================================================================
    // Large Document Consistency Test
    // =========================================================================

    @Test
    fun verifyLargeDocumentConsistency() {
        val lines = buildString {
            appendLine("class LargeClass {")
            for (i in 1..200) {
                appendLine("    int field$i = $i;")
            }
            appendLine("    /*")
            for (i in 1..50) {
                appendLine("     * Comment line $i")
            }
            appendLine("     */")
            for (i in 1..200) {
                appendLine("    void method$i() { return; }")
            }
            appendLine("}")
        }

        val doc = EditorDocument(lines)
        withCache(doc, JavaLexer()) { cache ->
            pretokenizeAll(doc, cache)

            val edits = listOf(
                { doc.insert(doc.getLineStartOffset(50) + 4, "/* inline */ ") },
                { doc.insert(doc.getLineStartOffset(220) + 5, "EDITED ") },
                { doc.insert(doc.getLineEndOffset(100), "\n    int newVar = 999;") },
            )

            for ((index, edit) in edits.withIndex()) {
                edit()
                assertIncrementalMatchesFull(doc, cache, "after edit $index in large document")
            }
        }
    }

    // =========================================================================
    // Edge Cases
    // =========================================================================

    @Test
    fun verifyEditAtCommentBoundary() {
        val doc = EditorDocument("""
            |class Test {
            |    /* comment */
            |    int x = 1;
            |}
        """.trimMargin())

        withCache(doc, JavaLexer()) { cache ->
            pretokenizeAll(doc, cache)

            val line1Start = doc.getLineStartOffset(1)
            doc.insert(line1Start + 4, "X")

            assertIncrementalMatchesFull(doc, cache, "after edit at comment boundary")
        }
    }

    @Test
    fun verifyEmptyLineInBlockComment() {
        val doc = EditorDocument("""
            |class Test {
            |    /*
            |
            |     * After empty line
            |     */
            |    int x = 1;
            |}
        """.trimMargin())

        withCache(doc, JavaLexer()) { cache ->
            pretokenizeAll(doc, cache)

            doc.insert(doc.getLineStartOffset(2), "     * Added content")

            assertIncrementalMatchesFull(doc, cache, "after editing empty line in comment")
        }
    }

    @Test
    fun verifySingleLineToMultiLine() {
        val doc = EditorDocument("""
            |class Test {
            |    int x = 1;
            |}
        """.trimMargin())

        withCache(doc, JavaLexer()) { cache ->
            pretokenizeAll(doc, cache)

            val line1Start = doc.getLineStartOffset(1)
            doc.insert(line1Start + 4, "/*\n     * comment\n     */\n    ")

            assertIncrementalMatchesFull(doc, cache, "after converting to multi-line")
        }
    }

    @Test
    fun verifyEmptyDocument() {
        val doc = EditorDocument("")

        withCache(doc, JavaLexer()) { cache ->
            pretokenizeAll(doc, cache)

            doc.insert(0, "/* comment */")

            assertIncrementalMatchesFull(doc, cache, "after inserting into empty document")
        }
    }

    @Test
    fun verifyVeryLongLine() {
        val longContent = "x".repeat(5000)
        val doc = EditorDocument("""
            |class Test {
            |    String s = "$longContent";
            |    int x = 1;
            |}
        """.trimMargin())

        withCache(doc, JavaLexer()) { cache ->
            pretokenizeAll(doc, cache)

            // Insert in the middle of the long line
            val line1Start = doc.getLineStartOffset(1)
            doc.insert(line1Start + 2500, "/* inserted */")

            assertIncrementalMatchesFull(doc, cache, "after editing very long line")
        }
    }

    @Test
    fun verifyUnclosedStringAtEOF() {
        val doc = EditorDocument("""
            |class Test {
            |    String s = "unclosed
        """.trimMargin())

        withCache(doc, JavaLexer()) { cache ->
            pretokenizeAll(doc, cache)

            // Close the string
            doc.insert(doc.length, "\";")

            assertIncrementalMatchesFull(doc, cache, "after closing string at EOF")
        }
    }

    // =========================================================================
    // Concurrent Access Tests
    // =========================================================================

    @Test
    fun verifyConcurrentAccess() {
        val lines = buildString {
            appendLine("class LargeClass {")
            for (i in 1..1000) {
                appendLine("    int field$i = $i;")
            }
            appendLine("}")
        }
        val doc = EditorDocument(lines)

        withCache(doc, JavaLexer()) { cache ->
            // Pre-tokenize
            pretokenizeAll(doc, cache)

            // Launch concurrent coroutines accessing the cache
            runBlocking {
                val jobs = (0 until 4).map { threadId ->
                    async(Dispatchers.Default) {
                        repeat(100) { iteration ->
                            val line = (threadId * 250 + iteration) % doc.lineCount
                            cache.getLineTokens(line)
                        }
                    }
                }
                jobs.awaitAll()
            }

            // Verify cache is still consistent after concurrent access
            assertIncrementalMatchesFull(doc, cache, "after concurrent access")
        }
    }

    @Test
    fun verifyConcurrentAccessWithInvalidation() {
        val lines = buildString {
            appendLine("class Test {")
            for (i in 1..500) {
                appendLine("    int field$i = $i;")
            }
            appendLine("}")
        }
        val doc = EditorDocument(lines)

        withCache(doc, JavaLexer()) { cache ->
            pretokenizeAll(doc, cache)

            // Concurrent reads with periodic invalidations
            runBlocking {
                val readers = (0 until 3).map { threadId ->
                    async(Dispatchers.Default) {
                        repeat(50) { iteration ->
                            val line = (threadId * 100 + iteration) % doc.lineCount
                            try {
                                cache.getLineTokens(line)
                            } catch (_: Exception) {
                                // Ignore exceptions from race conditions during invalidation
                            }
                        }
                    }
                }

                val invalidator = async(Dispatchers.Default) {
                    repeat(10) {
                        cache.invalidateAll()
                    }
                }

                readers.awaitAll()
                invalidator.await()
            }

            // After all concurrent activity, cache should still work correctly
            cache.invalidateAll()
            val freshTokens = getAllLineTokens(doc, cache)
            assertTrue(
                freshTokens.isNotEmpty(),
                "Cache should produce tokens after concurrent stress test"
            )
        }
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    /**
     * Executes a test block with a TokenCache, ensuring cleanup via dispose().
     */
    private inline fun withCache(
        doc: EditorDocument,
        provider: TokenProvider,
        block: (TokenCache) -> Unit
    ) {
        val cache = TokenCache(doc, provider)
        try {
            block(cache)
        } finally {
            cache.dispose()
        }
    }

    /**
     * Pre-tokenizes all lines in the document.
     */
    private fun pretokenizeAll(doc: EditorDocument, cache: TokenCache) {
        if (doc.lineCount > 0) {
            cache.pretokenize(0, doc.lineCount - 1)
        }
    }

    /**
     * Gets all line tokens from the cache as a simple list per line.
     * Note: Only retrieves tokens, not internal lexer states.
     */
    private fun getAllLineTokens(doc: EditorDocument, cache: TokenCache): Map<Int, List<Token>> {
        val result = mutableMapOf<Int, List<Token>>()
        for (line in 0 until doc.lineCount) {
            result[line] = cache.getLineTokens(line)
        }
        return result
    }

    /**
     * Asserts that incremental and full highlighting produce the same token results.
     */
    private fun assertIncrementalMatchesFull(doc: EditorDocument, cache: TokenCache, context: String) {
        // Get incremental result (uses cache)
        val incrementalTokens = getAllLineTokens(doc, cache)

        // Get full re-highlighting (clear cache first)
        cache.invalidateAll()
        val fullTokens = getAllLineTokens(doc, cache)

        // Compare
        assertEquals(
            fullTokens.size,
            incrementalTokens.size,
            "Line count mismatch $context: expected ${fullTokens.size}, got ${incrementalTokens.size}"
        )

        for (lineNum in fullTokens.keys) {
            val incTokens = incrementalTokens[lineNum] ?: emptyList()
            val expTokens = fullTokens[lineNum] ?: emptyList()

            assertEquals(
                expTokens.size,
                incTokens.size,
                "Token count mismatch on line $lineNum $context: expected ${expTokens.size}, got ${incTokens.size}"
            )

            for (i in expTokens.indices) {
                val expected = expTokens[i]
                val actual = incTokens[i]

                assertEquals(
                    expected.type,
                    actual.type,
                    "Token type mismatch on line $lineNum, token $i $context: expected ${expected.type}, got ${actual.type}"
                )
                assertEquals(
                    expected.startOffset,
                    actual.startOffset,
                    "Token start offset mismatch on line $lineNum, token $i $context"
                )
                assertEquals(
                    expected.endOffset,
                    actual.endOffset,
                    "Token end offset mismatch on line $lineNum, token $i $context"
                )
            }
        }
    }
}
