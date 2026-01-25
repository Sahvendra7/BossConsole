package ai.rever.bosseditor.rendering

import ai.rever.bosseditor.core.EditorDocument
import ai.rever.bosseditor.features.BracketMatcher
import ai.rever.bosseditor.features.IndentGuides
import ai.rever.bosseditor.features.MarkOccurrences
import ai.rever.bosseditor.features.RainbowBrackets
import ai.rever.bosseditor.fold.FoldRegion
import ai.rever.bosseditor.fold.FoldType
import ai.rever.bosseditor.fold.VisualLineMapper
import ai.rever.bosseditor.highlight.TokenCache
import ai.rever.bosseditor.highlight.lexers.JavaLexer
import ai.rever.bosseditor.testutil.DocumentGenerators
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.measureTime

/**
 * Performance benchmark tests for BossEditor rendering operations.
 *
 * These tests ensure that common rendering operations meet performance
 * targets for smooth 60fps rendering on large documents.
 *
 * ## Performance Targets
 * - Frame budget: 16.6ms for 60fps
 * - Token retrieval: <5ms for 50 visible lines
 * - Visual line mapping: <5ms for 200 lookups
 * - Bracket matching: <10ms per match
 * - Mark occurrences: <50ms for full document scan
 * - Scrolling frame: avg <16ms, P99 <33ms
 */
class RenderingPerformanceTest {

    companion object {
        // Detect if running on CI (GitHub Actions sets this)
        private val isCI = System.getenv("CI") == "true" || System.getenv("GITHUB_ACTIONS") == "true"

        // CI multiplier - CI runners are slower and more variable
        private val CI_MULTIPLIER = if (isCI) 10 else 1

        // Performance thresholds in milliseconds (relaxed on CI)
        private val FRAME_BUDGET_MS = 16L * CI_MULTIPLIER        // 60fps target
        private val TOKEN_RETRIEVAL_50_LINES_MS = 5L * CI_MULTIPLIER
        private val VISUAL_LINE_LOOKUP_MS = 5L * CI_MULTIPLIER
        private val BRACKET_MATCH_MS = 10L * CI_MULTIPLIER
        private val MARK_OCCURRENCES_MS = 50L * CI_MULTIPLIER
        private val RAINBOW_BRACKETS_MS = 100L * CI_MULTIPLIER
        private val INDENT_GUIDES_MS = 100L * CI_MULTIPLIER  // Increased for Windows CI variability
        private val SCROLL_FRAME_AVG_MS = 16L * CI_MULTIPLIER
        private val SCROLL_FRAME_P99_MS = 33L * CI_MULTIPLIER
        private val FAST_OPERATION_MS = 50L * CI_MULTIPLIER      // For quick operations

        // JVM warmup iterations to stabilize JIT compilation
        private const val WARMUP_ITERATIONS = 5
    }

    // =========================================================================
    // Token Retrieval Benchmarks
    // =========================================================================

    @Test
    fun benchmarkTokenRetrieval10kDoc() {
        val doc = EditorDocument(DocumentGenerators.createLargeJavaDocument(10_000))
        val cache = TokenCache(doc, JavaLexer())

        // Pre-tokenize to warm up cache
        cache.pretokenize(0, doc.lineCount - 1)

        // Measure time to retrieve tokens for 50 consecutive lines (simulating viewport)
        val duration = measureTime {
            repeat(10) { iteration ->
                val startLine = (iteration * 1000) % (doc.lineCount - 50)
                for (line in startLine until startLine + 50) {
                    cache.getLineTokens(line)
                }
            }
        }

        val avgPerBatch = duration.inWholeMilliseconds / 10

        println("Token retrieval (50 lines from 10k doc, 10 batches): avg ${avgPerBatch}ms per batch")
        assertTrue(
            avgPerBatch < TOKEN_RETRIEVAL_50_LINES_MS,
            "Token retrieval should complete in <${TOKEN_RETRIEVAL_50_LINES_MS}ms, took ${avgPerBatch}ms"
        )

        cache.dispose()
    }

    @Test
    fun benchmarkTokenRetrievalWithCacheMiss() {
        val doc = EditorDocument(DocumentGenerators.createLargeJavaDocument(10_000))
        val cache = TokenCache(doc, JavaLexer())

        // Don't pre-tokenize - test cold cache performance
        val duration = measureTime {
            // Retrieve tokens for 50 lines (cold cache)
            for (line in 5000 until 5050) {
                cache.getLineTokens(line)
            }
        }

        println("Token retrieval (50 lines, cold cache): ${duration.inWholeMilliseconds}ms")
        assertTrue(
            duration.inWholeMilliseconds < TOKEN_RETRIEVAL_50_LINES_MS * 3, // Allow 3x for cold cache
            "Cold cache token retrieval should complete in <${TOKEN_RETRIEVAL_50_LINES_MS * 3}ms"
        )

        cache.dispose()
    }

    // =========================================================================
    // Visual Line Mapper Benchmarks
    // =========================================================================

    @Test
    fun benchmarkVisualLineMapperLookups() {
        val doc = EditorDocument(DocumentGenerators.createLargeJavaDocument(10_000))

        // Create mapper with some collapsed folds (simulate 10 collapsed regions)
        val collapsedRegions = (0 until 10).map { i ->
            val startLine = i * 1000
            FoldRegion(
                startLine = startLine,
                endLine = startLine + 50,
                type = FoldType.CODE,
                placeholder = "...",
                isCollapsed = true
            )
        }

        val mapper = VisualLineMapper.create(doc.lineCount, collapsedRegions)

        val duration = measureTime {
            repeat(100) { i ->
                // Visual to document mapping
                val visualLine = i * 50
                if (visualLine < mapper.visibleLineCount) {
                    mapper.visualToDocument(visualLine)
                }
            }
            repeat(100) { i ->
                // Document to visual mapping
                val docLine = i * 100
                if (docLine < doc.lineCount) {
                    mapper.documentToVisual(docLine)
                }
            }
        }

        println("Visual line mapper (200 lookups with folds): ${duration.inWholeMilliseconds}ms")
        assertTrue(
            duration.inWholeMilliseconds < VISUAL_LINE_LOOKUP_MS,
            "Visual line lookups should complete in <${VISUAL_LINE_LOOKUP_MS}ms"
        )
    }

    @Test
    fun benchmarkVisualLineMapperNoFolds() {
        val doc = EditorDocument(DocumentGenerators.createLargeJavaDocument(10_000))

        // No folds - simple 1:1 mapping
        val mapper = VisualLineMapper.noFolds(doc.lineCount)

        val duration = measureTime {
            repeat(1000) { i ->
                val line = i * 10
                if (line < doc.lineCount) {
                    mapper.visualToDocument(line)
                    mapper.documentToVisual(line)
                }
            }
        }

        println("Visual line mapper (2000 lookups, no folds): ${duration.inWholeMilliseconds}ms")
        assertTrue(
            duration.inWholeMilliseconds < VISUAL_LINE_LOOKUP_MS,
            "Simple visual line lookups should be nearly instant"
        )
    }

    // =========================================================================
    // Bracket Matching Benchmarks
    // =========================================================================

    @Test
    fun benchmarkBracketMatchingLargeDoc() {
        val doc = EditorDocument(DocumentGenerators.createLargeJavaDocumentWithNesting(10_000))
        val matcher = BracketMatcher(doc)

        // Test matching at various positions with different nesting depths
        val testPositions = listOf(
            100,   // Near start
            5000,  // Middle
            9900,  // Near end
            2500,  // Quarter
            7500   // Three-quarters
        )

        val durations = testPositions.map { pos ->
            val actualPos = pos.coerceAtMost(doc.length - 1)
            measureTime {
                matcher.findMatchingBracket(actualPos)
            }.inWholeMilliseconds
        }

        val maxDuration = durations.maxOrNull() ?: 0
        val avgDuration = durations.average()

        println("Bracket matching (5 positions in 10k doc): max ${maxDuration}ms, avg ${avgDuration}ms")
        assertTrue(
            maxDuration < BRACKET_MATCH_MS,
            "Bracket matching should complete in <${BRACKET_MATCH_MS}ms, took ${maxDuration}ms"
        )
    }

    @Test
    fun benchmarkFindAllBracketPairs() {
        val doc = EditorDocument(DocumentGenerators.createLargeJavaDocumentWithNesting(5_000))
        val matcher = BracketMatcher(doc)

        val duration = measureTime {
            matcher.findAllBracketPairs()
        }

        println("Find all bracket pairs (5k doc): ${duration.inWholeMilliseconds}ms")
        assertTrue(
            duration.inWholeMilliseconds < BRACKET_MATCH_MS * 10, // Allow more time for full document
            "Finding all bracket pairs should be reasonable"
        )
    }

    // =========================================================================
    // Mark Occurrences Benchmarks
    // =========================================================================

    @Test
    fun benchmarkMarkOccurrencesLargeDoc() {
        val doc = EditorDocument(DocumentGenerators.createLargeJavaDocument(10_000))
        val markOccurrences = MarkOccurrences(doc)

        // Find occurrences of a common word
        val duration = measureTime {
            markOccurrences.findOccurrences(500) // Position in middle of document
        }

        println("Mark occurrences (10k doc): ${duration.inWholeMilliseconds}ms")
        assertTrue(
            duration.inWholeMilliseconds < MARK_OCCURRENCES_MS,
            "Mark occurrences should complete in <${MARK_OCCURRENCES_MS}ms"
        )
    }

    @Test
    fun benchmarkMarkOccurrencesVisible() {
        val doc = EditorDocument(DocumentGenerators.createLargeJavaDocument(10_000))
        val markOccurrences = MarkOccurrences(doc)

        // Find occurrences only in visible range (more efficient)
        val visibleStartOffset = doc.getLineStartOffset(5000)
        val visibleEndOffset = doc.getLineEndOffset(5050.coerceAtMost(doc.lineCount - 1))
        val duration = measureTime {
            markOccurrences.findVisibleOccurrences(
                offset = visibleStartOffset + 100, // Position in visible area
                visibleStart = visibleStartOffset,
                visibleEnd = visibleEndOffset
            )
        }

        println("Mark occurrences (visible range only): ${duration.inWholeMilliseconds}ms")
        assertTrue(
            duration.inWholeMilliseconds < TOKEN_RETRIEVAL_50_LINES_MS,
            "Visible-only mark occurrences should be very fast"
        )
    }

    // =========================================================================
    // Rainbow Brackets Benchmarks
    // =========================================================================

    @Test
    fun benchmarkRainbowBracketsFullDoc() {
        val doc = EditorDocument(DocumentGenerators.createLargeJavaDocumentWithNesting(5_000))
        val rainbow = RainbowBrackets(doc)

        val duration = measureTime {
            rainbow.getRainbowBrackets()
        }

        println("Rainbow brackets (5k doc): ${duration.inWholeMilliseconds}ms")
        assertTrue(
            duration.inWholeMilliseconds < RAINBOW_BRACKETS_MS,
            "Rainbow brackets should complete in <${RAINBOW_BRACKETS_MS}ms"
        )
    }

    @Test
    fun benchmarkRainbowBracketsForLine() {
        val doc = EditorDocument(DocumentGenerators.createLargeJavaDocumentWithNesting(5_000))
        val rainbow = RainbowBrackets(doc)

        // Pre-compute all brackets once
        val allBrackets = rainbow.getRainbowBrackets()

        // Measure time to filter brackets for individual lines (simulating render)
        val duration = measureTime {
            repeat(100) { i ->
                val line = i * 50
                if (line < doc.lineCount) {
                    // Filter pre-computed brackets for this line (what rendering does)
                    val lineStart = doc.getLineStartOffset(line)
                    val lineEnd = if (line < doc.lineCount - 1) doc.getLineStartOffset(line + 1) else doc.length
                    allBrackets.filter { it.offset in lineStart until lineEnd }
                }
            }
        }

        println("Rainbow brackets filter for 100 lines: ${duration.inWholeMilliseconds}ms")
        assertTrue(
            duration.inWholeMilliseconds < FAST_OPERATION_MS,
            "Per-line bracket filtering should be <${FAST_OPERATION_MS}ms, took ${duration.inWholeMilliseconds}ms"
        )
    }

    // =========================================================================
    // Indent Guides Benchmarks
    // =========================================================================

    @Test
    fun benchmarkIndentGuidesLargeDoc() {
        val doc = EditorDocument(DocumentGenerators.createLargeJavaDocumentWithNesting(10_000))
        val guides = IndentGuides(doc)

        val duration = measureTime {
            guides.calculateGuides()
        }

        println("Indent guides calculation (10k doc): ${duration.inWholeMilliseconds}ms")
        assertTrue(
            duration.inWholeMilliseconds < INDENT_GUIDES_MS,
            "Indent guides should complete in <${INDENT_GUIDES_MS}ms"
        )
    }

    @Test
    fun benchmarkIndentGuidesInRange() {
        val doc = EditorDocument(DocumentGenerators.createLargeJavaDocumentWithNesting(5_000))
        val guides = IndentGuides(doc)

        // Pre-calculate all guides once
        val allGuides = guides.calculateGuides()

        // Measure time to filter guides for ranges (simulating render)
        val duration = measureTime {
            repeat(100) { i ->
                val startLine = i * 50
                val endLine = startLine + 50
                if (endLine < doc.lineCount) {
                    // Filter pre-computed guides for this range (what rendering does)
                    allGuides.filter { guide ->
                        guide.startLine <= endLine && guide.endLine >= startLine
                    }
                }
            }
        }

        println("Indent guides filter for 100 ranges: ${duration.inWholeMilliseconds}ms")
        assertTrue(
            duration.inWholeMilliseconds < FAST_OPERATION_MS,
            "Range-based guide filtering should be <${FAST_OPERATION_MS}ms, took ${duration.inWholeMilliseconds}ms"
        )
    }

    // =========================================================================
    // Scrolling Frame Time Simulation
    // =========================================================================

    @Test
    fun benchmarkScrollingFrameTime() {
        val doc = EditorDocument(DocumentGenerators.createLargeJavaDocumentWithNesting(10_000))
        val cache = TokenCache(doc, JavaLexer())
        val matcher = BracketMatcher(doc)
        val markOccurrences = MarkOccurrences(doc)

        // Pre-tokenize
        cache.pretokenize(0, doc.lineCount - 1)

        // JVM warmup - run a few iterations to stabilize JIT compilation
        repeat(WARMUP_ITERATIONS) { warmupStep ->
            val startLine = warmupStep * 100
            val visibleEnd = (startLine + 50).coerceAtMost(doc.lineCount - 1)
            for (line in startLine..visibleEnd) {
                cache.getLineTokens(line)
            }
            matcher.findMatchingBracket(doc.getLineStartOffset(startLine + 25))
        }

        // Simulate 100 scroll frames
        val frameTimes = (0 until 100).map { scrollStep ->
            val startLine = scrollStep * 90 // Scroll through document

            measureTime {
                // Operations typically done per frame:
                // 1. Retrieve tokens for visible lines (50 lines)
                val visibleEnd = (startLine + 50).coerceAtMost(doc.lineCount - 1)
                for (line in startLine..visibleEnd) {
                    cache.getLineTokens(line)
                }

                // 2. Check bracket match at caret position
                val caretOffset = doc.getLineStartOffset(startLine + 25)
                matcher.findMatchingBracket(caretOffset)

                // 3. Update mark occurrences (visible range only)
                val visStartOffset = doc.getLineStartOffset(startLine)
                val visEndOffset = doc.getLineEndOffset(visibleEnd)
                markOccurrences.findVisibleOccurrences(
                    offset = caretOffset,
                    visibleStart = visStartOffset,
                    visibleEnd = visEndOffset
                )
            }.inWholeMilliseconds
        }

        val avg = frameTimes.average()
        val sorted = frameTimes.sorted()
        val p99Index = (sorted.size * 0.99).toInt().coerceAtMost(sorted.size - 1)
        val p99 = sorted[p99Index]
        val max = frameTimes.maxOrNull() ?: 0

        println("Scrolling frame time (100 frames):")
        println("  Average: ${avg}ms")
        println("  P99: ${p99}ms")
        println("  Max: ${max}ms")

        assertTrue(
            avg < SCROLL_FRAME_AVG_MS,
            "Average frame time ${avg}ms exceeds budget ${SCROLL_FRAME_AVG_MS}ms"
        )
        assertTrue(
            p99 < SCROLL_FRAME_P99_MS,
            "P99 frame time ${p99}ms exceeds ${SCROLL_FRAME_P99_MS}ms"
        )

        cache.dispose()
    }

    @Test
    fun benchmarkRapidScrolling() {
        val doc = EditorDocument(DocumentGenerators.createLargeJavaDocument(10_000))
        val cache = TokenCache(doc, JavaLexer())

        // Pre-tokenize
        cache.pretokenize(0, doc.lineCount - 1)

        // JVM warmup
        repeat(WARMUP_ITERATIONS) { i ->
            for (line in i * 100 until i * 100 + 50) {
                cache.getLineTokens(line)
            }
        }

        // Simulate rapid scrolling (jumping around the document)
        val frameTimes = (0 until 50).map { i ->
            // Jump to random positions
            val startLine = when (i % 5) {
                0 -> 0
                1 -> 2500
                2 -> 5000
                3 -> 7500
                else -> 9500
            }

            measureTime {
                val visibleEnd = (startLine + 50).coerceAtMost(doc.lineCount - 1)
                for (line in startLine..visibleEnd) {
                    cache.getLineTokens(line)
                }
            }.inWholeMilliseconds
        }

        val avg = frameTimes.average()
        val max = frameTimes.maxOrNull() ?: 0

        println("Rapid scrolling (50 jumps): avg ${avg}ms, max ${max}ms")
        assertTrue(
            avg < SCROLL_FRAME_AVG_MS,
            "Rapid scrolling average ${avg}ms exceeds budget"
        )

        cache.dispose()
    }

    // =========================================================================
    // Cache Statistics
    // =========================================================================

    @Test
    fun benchmarkCacheEffectiveness() {
        val doc = EditorDocument(DocumentGenerators.createLargeJavaDocument(5_000))
        val cache = TokenCache(doc, JavaLexer())

        // Pre-tokenize first 1000 lines
        cache.pretokenize(0, 999)

        val stats1 = cache.getStats()
        println("After pretokenize 1000 lines: ${stats1.cachedLines} cached, ${stats1.hitRate * 100}% hit rate")

        // Retrieve same lines again (should be cached)
        val duration = measureTime {
            repeat(10) {
                for (line in 0 until 1000) {
                    cache.getLineTokens(line)
                }
            }
        }

        println("10x retrieval of 1000 cached lines: ${duration.inWholeMilliseconds}ms")
        assertTrue(
            duration.inWholeMilliseconds < FAST_OPERATION_MS,
            "Cached retrieval should be very fast"
        )

        cache.dispose()
    }
}
