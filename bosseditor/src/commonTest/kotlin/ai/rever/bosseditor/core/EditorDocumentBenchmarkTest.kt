package ai.rever.bosseditor.core

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.measureTime

/**
 * Benchmark tests for EditorDocument performance.
 *
 * These tests verify that large document operations meet performance targets:
 * - Multi-line paste in 10k line document: <100ms
 * - Multi-line delete in 10k line document: <100ms
 * - Sequential edits with newlines: reasonable performance
 * - Single-line edits: no regression from optimization
 */
class EditorDocumentBenchmarkTest {

    companion object {
        // Detect if running on CI (GitHub Actions sets this)
        private val isCI = System.getenv("CI") == "true" || System.getenv("GITHUB_ACTIONS") == "true"

        // CI multiplier - CI runners are slower and more variable
        private val CI_MULTIPLIER = if (isCI) 10 else 1

        // Performance thresholds in milliseconds (relaxed on CI)
        private val MULTI_LINE_OPERATION_THRESHOLD_MS = 100L * CI_MULTIPLIER
        private val SINGLE_CHAR_EDIT_THRESHOLD_MS = 200L * CI_MULTIPLIER
        private val SEQUENTIAL_EDIT_THRESHOLD_MS = 500L * CI_MULTIPLIER
        private val MIXED_OPERATION_THRESHOLD_MS = 200L * CI_MULTIPLIER
    }

    @Test
    fun benchmarkMultiLinePaste10kLines() {
        // Create a 10k line document
        val doc = EditorDocument()
        val lines = (1..10000).map { "Line $it content here" }
        doc.setText(lines.joinToString("\n"))

        assertEquals(10000, doc.lineCount)

        // Paste 100 lines in the middle
        val pasteContent = (1..100).map { "Pasted line $it" }.joinToString("\n")
        val middleOffset = doc.length / 2

        val duration = measureTime {
            doc.insert(middleOffset, pasteContent)
        }

        println("Multi-line paste (100 lines into 10k doc): ${duration.inWholeMilliseconds}ms")
        assertTrue(
            duration.inWholeMilliseconds < MULTI_LINE_OPERATION_THRESHOLD_MS,
            "Multi-line paste should complete in <${MULTI_LINE_OPERATION_THRESHOLD_MS}ms, took ${duration.inWholeMilliseconds}ms"
        )
    }

    @Test
    fun benchmarkMultiLineDelete10kLines() {
        val doc = EditorDocument()
        val lines = (1..10000).map { "Line $it content here" }
        doc.setText(lines.joinToString("\n"))

        // Delete 100 lines from the middle
        val startLine = 5000
        val startOffset = doc.getLineStartOffset(startLine)
        val endOffset = doc.getLineStartOffset(startLine + 100)

        val duration = measureTime {
            doc.delete(startOffset, endOffset)
        }

        println("Multi-line delete (100 lines from 10k doc): ${duration.inWholeMilliseconds}ms")
        assertTrue(
            duration.inWholeMilliseconds < MULTI_LINE_OPERATION_THRESHOLD_MS,
            "Multi-line delete should complete in <${MULTI_LINE_OPERATION_THRESHOLD_MS}ms, took ${duration.inWholeMilliseconds}ms"
        )
    }

    @Test
    fun benchmarkSequentialEditsWithNewlines() {
        val doc = EditorDocument()
        doc.setText("Initial content\n".repeat(1000))

        val duration = measureTime {
            repeat(100) { i ->
                doc.insert(doc.length / 2, "New line $i\n")
            }
        }

        println("100 sequential newline inserts: ${duration.inWholeMilliseconds}ms")
        assertTrue(
            duration.inWholeMilliseconds < SEQUENTIAL_EDIT_THRESHOLD_MS,
            "Sequential inserts should complete in <${SEQUENTIAL_EDIT_THRESHOLD_MS}ms, took ${duration.inWholeMilliseconds}ms"
        )
    }

    @Test
    fun benchmarkSingleCharEditPerformance() {
        // Ensure no regression for single-char edits
        val doc = EditorDocument()
        val lines = (1..10000).map { "Line $it content here" }
        doc.setText(lines.joinToString("\n"))

        val duration = measureTime {
            repeat(1000) {
                doc.insert(100, "x") // Single char, no newline
            }
        }

        println("1000 single-char inserts in 10k doc: ${duration.inWholeMilliseconds}ms")
        assertTrue(
            duration.inWholeMilliseconds < SINGLE_CHAR_EDIT_THRESHOLD_MS,
            "Single-char inserts should be fast, took ${duration.inWholeMilliseconds}ms"
        )
    }

    @Test
    fun benchmarkLargeBlockPaste() {
        val doc = EditorDocument()
        val lines = (1..5000).map { "Line $it" }
        doc.setText(lines.joinToString("\n"))

        // Paste 500 lines at once
        val pasteContent = (1..500).map { "Bulk paste line $it with some content" }.joinToString("\n")

        val duration = measureTime {
            doc.insert(doc.length / 2, "\n" + pasteContent + "\n")
        }

        println("Large block paste (500 lines into 5k doc): ${duration.inWholeMilliseconds}ms")
        assertTrue(
            duration.inWholeMilliseconds < MULTI_LINE_OPERATION_THRESHOLD_MS,
            "Large block paste should complete in <${MULTI_LINE_OPERATION_THRESHOLD_MS}ms, took ${duration.inWholeMilliseconds}ms"
        )
    }

    @Test
    fun benchmarkMixedOperations() {
        val doc = EditorDocument()
        doc.setText((1..5000).map { "Line $it" }.joinToString("\n"))

        val duration = measureTime {
            repeat(50) { i ->
                // Insert multi-line
                doc.insert(doc.length / 2, "New $i\nAnother $i\n")
                // Delete some lines
                if (doc.lineCount > 100) {
                    val start = doc.getLineStartOffset(50)
                    val end = doc.getLineStartOffset(52)
                    doc.delete(start, end)
                }
            }
        }

        println("50 mixed insert/delete operations: ${duration.inWholeMilliseconds}ms")
        assertTrue(
            duration.inWholeMilliseconds < MIXED_OPERATION_THRESHOLD_MS,
            "Mixed operations should complete in <${MIXED_OPERATION_THRESHOLD_MS}ms, took ${duration.inWholeMilliseconds}ms"
        )
    }

    private fun assertEquals(expected: Int, actual: Int) {
        kotlin.test.assertEquals(expected, actual)
    }
}
