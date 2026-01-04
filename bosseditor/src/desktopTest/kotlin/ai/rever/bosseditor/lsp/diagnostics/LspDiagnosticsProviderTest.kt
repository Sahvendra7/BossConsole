package ai.rever.bosseditor.lsp.diagnostics

import ai.rever.bosseditor.lsp.protocol.Diagnostic
import ai.rever.bosseditor.lsp.protocol.DiagnosticSeverity
import ai.rever.bosseditor.lsp.protocol.Position
import ai.rever.bosseditor.lsp.protocol.PublishDiagnosticsParams
import ai.rever.bosseditor.lsp.protocol.Range
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LspDiagnosticsProviderTest {

    private fun createDiagnostic(
        startLine: Int,
        startChar: Int,
        endLine: Int = startLine,
        endChar: Int = startChar + 5,
        message: String = "Test diagnostic",
        severity: Int = DiagnosticSeverity.ERROR,
        source: String? = "test",
        code: String? = null
    ): Diagnostic {
        return Diagnostic(
            range = Range(
                start = Position(startLine, startChar),
                end = Position(endLine, endChar)
            ),
            message = message,
            severity = severity,
            source = source,
            code = code
        )
    }

    @Test
    fun testUpdateDiagnostics() {
        val provider = LspDiagnosticsProvider()
        val uri = "file:///test.kt"

        val diagnostics = listOf(
            createDiagnostic(0, 0, message = "Error 1"),
            createDiagnostic(1, 5, message = "Error 2")
        )

        provider.updateDiagnostics(uri, diagnostics)

        val result = provider.getAllDiagnostics(uri)
        assertEquals(2, result.size)
    }

    @Test
    fun testUpdateDiagnosticsWithParams() {
        val provider = LspDiagnosticsProvider()
        val uri = "file:///test.kt"

        val params = PublishDiagnosticsParams(
            uri = uri,
            diagnostics = listOf(
                createDiagnostic(0, 0, message = "Error 1")
            ),
            version = 1
        )

        provider.updateDiagnostics(params)

        val result = provider.getAllDiagnostics(uri)
        assertEquals(1, result.size)
    }

    @Test
    fun testGetDiagnosticsForLine() {
        val provider = LspDiagnosticsProvider()
        val uri = "file:///test.kt"

        val diagnostics = listOf(
            createDiagnostic(0, 0, message = "Line 0 Error"),
            createDiagnostic(1, 5, message = "Line 1 Error"),
            createDiagnostic(1, 10, message = "Line 1 Warning", severity = DiagnosticSeverity.WARNING),
            createDiagnostic(2, 0, message = "Line 2 Error")
        )

        provider.updateDiagnostics(uri, diagnostics)

        val line1Diagnostics = provider.getDiagnosticsForLine(uri, 1)
        assertEquals(2, line1Diagnostics.size)
        // Should be sorted by severity (errors first)
        assertEquals(DiagnosticSeverity.ERROR, line1Diagnostics[0].severity)
    }

    @Test
    fun testGetDiagnosticsForLineEmpty() {
        val provider = LspDiagnosticsProvider()
        val uri = "file:///test.kt"

        val diagnostics = listOf(
            createDiagnostic(0, 0, message = "Line 0 Error")
        )

        provider.updateDiagnostics(uri, diagnostics)

        val result = provider.getDiagnosticsForLine(uri, 5)
        assertTrue(result.isEmpty())
    }

    @Test
    fun testGetDiagnosticsAtPosition() {
        val provider = LspDiagnosticsProvider()
        val uri = "file:///test.kt"

        val diagnostics = listOf(
            createDiagnostic(0, 0, endChar = 10, message = "Range 0-10"),
            createDiagnostic(0, 5, endChar = 15, message = "Range 5-15"),
            createDiagnostic(0, 20, endChar = 25, message = "Range 20-25")
        )

        provider.updateDiagnostics(uri, diagnostics)

        // Position 7 should match first two diagnostics
        val pos7 = provider.getDiagnosticsAtPosition(uri, 0, 7)
        assertEquals(2, pos7.size)

        // Position 22 should match only last diagnostic
        val pos22 = provider.getDiagnosticsAtPosition(uri, 0, 22)
        assertEquals(1, pos22.size)
        assertEquals("Range 20-25", pos22[0].message)

        // Position 17 should match none
        val pos17 = provider.getDiagnosticsAtPosition(uri, 0, 17)
        assertTrue(pos17.isEmpty())
    }

    @Test
    fun testGetDiagnosticsAtPositionMultiLine() {
        val provider = LspDiagnosticsProvider()
        val uri = "file:///test.kt"

        // Multi-line diagnostic from line 0 char 5 to line 2 char 10
        // Note: Diagnostics are indexed by their START line only
        val diagnostics = listOf(
            createDiagnostic(0, 5, endLine = 2, endChar = 10, message = "Multi-line")
        )

        provider.updateDiagnostics(uri, diagnostics)

        // Check start line - diagnostics are indexed by start line
        assertTrue(provider.getDiagnosticsAtPosition(uri, 0, 6).isNotEmpty())
        assertTrue(provider.getDiagnosticsAtPosition(uri, 0, 4).isEmpty())

        // Middle lines are not indexed - diagnostics only appear in the start line index
        // This is expected behavior for the current simple implementation
        assertTrue(provider.getDiagnosticsAtPosition(uri, 1, 0).isEmpty())

        // End line is also not indexed
        assertTrue(provider.getDiagnosticsAtPosition(uri, 2, 5).isEmpty())
    }

    @Test
    fun testGetDiagnosticsInRange() {
        val provider = LspDiagnosticsProvider()
        val uri = "file:///test.kt"

        val diagnostics = listOf(
            createDiagnostic(0, 0, message = "Line 0"),
            createDiagnostic(2, 0, message = "Line 2"),
            createDiagnostic(4, 0, message = "Line 4"),
            createDiagnostic(6, 0, message = "Line 6")
        )

        provider.updateDiagnostics(uri, diagnostics)

        val range = provider.getDiagnosticsInRange(uri, 1, 5)
        assertEquals(2, range.size)
        assertTrue(range.containsKey(2))
        assertTrue(range.containsKey(4))
        assertFalse(range.containsKey(0))
        assertFalse(range.containsKey(6))
    }

    @Test
    fun testGetMostSevereDiagnostic() {
        val provider = LspDiagnosticsProvider()
        val uri = "file:///test.kt"

        val diagnostics = listOf(
            createDiagnostic(0, 0, message = "Hint", severity = DiagnosticSeverity.HINT),
            createDiagnostic(0, 5, message = "Error", severity = DiagnosticSeverity.ERROR),
            createDiagnostic(0, 10, message = "Warning", severity = DiagnosticSeverity.WARNING)
        )

        provider.updateDiagnostics(uri, diagnostics)

        val most = provider.getMostSevereDiagnostic(uri, 0)
        assertNotNull(most)
        assertEquals("Error", most.message)
    }

    @Test
    fun testGetMostSevereDiagnosticEmpty() {
        val provider = LspDiagnosticsProvider()
        val uri = "file:///test.kt"

        val result = provider.getMostSevereDiagnostic(uri, 0)
        assertNull(result)
    }

    @Test
    fun testGetLineSeverity() {
        val provider = LspDiagnosticsProvider()
        val uri = "file:///test.kt"

        val diagnostics = listOf(
            createDiagnostic(0, 0, message = "Warning", severity = DiagnosticSeverity.WARNING),
            createDiagnostic(0, 5, message = "Info", severity = DiagnosticSeverity.INFORMATION)
        )

        provider.updateDiagnostics(uri, diagnostics)

        val severity = provider.getLineSeverity(uri, 0)
        assertEquals(DiagnosticSeverity.WARNING, severity)
    }

    @Test
    fun testGetLineSeverityNull() {
        val provider = LspDiagnosticsProvider()
        val uri = "file:///test.kt"

        val severity = provider.getLineSeverity(uri, 0)
        assertNull(severity)
    }

    @Test
    fun testHasLineDiagnostics() {
        val provider = LspDiagnosticsProvider()
        val uri = "file:///test.kt"

        val diagnostics = listOf(
            createDiagnostic(0, 0, message = "Error")
        )

        provider.updateDiagnostics(uri, diagnostics)

        assertTrue(provider.hasLineDiagnostics(uri, 0))
        assertFalse(provider.hasLineDiagnostics(uri, 1))
    }

    @Test
    fun testGetDiagnosticCounts() {
        val provider = LspDiagnosticsProvider()
        val uri = "file:///test.kt"

        val diagnostics = listOf(
            createDiagnostic(0, 0, severity = DiagnosticSeverity.ERROR),
            createDiagnostic(1, 0, severity = DiagnosticSeverity.ERROR),
            createDiagnostic(2, 0, severity = DiagnosticSeverity.WARNING),
            createDiagnostic(3, 0, severity = DiagnosticSeverity.INFORMATION),
            createDiagnostic(4, 0, severity = DiagnosticSeverity.HINT),
            createDiagnostic(5, 0, severity = DiagnosticSeverity.HINT)
        )

        provider.updateDiagnostics(uri, diagnostics)

        val counts = provider.getDiagnosticCounts(uri)
        assertEquals(2, counts.errors)
        assertEquals(1, counts.warnings)
        assertEquals(1, counts.information)
        assertEquals(2, counts.hints)
        assertEquals(6, counts.total)
        assertTrue(counts.hasErrors)
        assertTrue(counts.hasWarnings)
    }

    @Test
    fun testClearDiagnostics() {
        val provider = LspDiagnosticsProvider()
        val uri = "file:///test.kt"

        provider.updateDiagnostics(uri, listOf(createDiagnostic(0, 0)))
        assertEquals(1, provider.getAllDiagnostics(uri).size)

        provider.clearDiagnostics(uri)
        assertTrue(provider.getAllDiagnostics(uri).isEmpty())
    }

    @Test
    fun testClearAll() {
        val provider = LspDiagnosticsProvider()

        provider.updateDiagnostics("file:///test1.kt", listOf(createDiagnostic(0, 0)))
        provider.updateDiagnostics("file:///test2.kt", listOf(createDiagnostic(0, 0)))

        assertEquals(2, provider.getUrisWithDiagnostics().size)

        provider.clearAll()

        assertTrue(provider.getUrisWithDiagnostics().isEmpty())
    }

    @Test
    fun testGetUrisWithDiagnostics() {
        val provider = LspDiagnosticsProvider()

        provider.updateDiagnostics("file:///test1.kt", listOf(createDiagnostic(0, 0)))
        provider.updateDiagnostics("file:///test2.kt", listOf(createDiagnostic(0, 0)))

        val uris = provider.getUrisWithDiagnostics()
        assertEquals(2, uris.size)
        assertTrue(uris.contains("file:///test1.kt"))
        assertTrue(uris.contains("file:///test2.kt"))
    }

    @Test
    fun testDiagnosticsUpdateFlowAvailable() {
        // Test that the diagnostics updates flow is available
        val provider = LspDiagnosticsProvider()
        assertNotNull(provider.diagnosticsUpdates)
    }

    @Test
    fun testDiagnosticsSortedByPosition() {
        val provider = LspDiagnosticsProvider()
        val uri = "file:///test.kt"

        // Add in random order
        val diagnostics = listOf(
            createDiagnostic(2, 5, message = "Third"),
            createDiagnostic(0, 0, message = "First"),
            createDiagnostic(1, 10, message = "Second")
        )

        provider.updateDiagnostics(uri, diagnostics)

        val result = provider.getAllDiagnostics(uri)
        assertEquals("First", result[0].message)
        assertEquals("Second", result[1].message)
        assertEquals("Third", result[2].message)
    }

    @Test
    fun testReplaceDiagnostics() {
        val provider = LspDiagnosticsProvider()
        val uri = "file:///test.kt"

        provider.updateDiagnostics(uri, listOf(
            createDiagnostic(0, 0, message = "Old")
        ))

        assertEquals("Old", provider.getAllDiagnostics(uri)[0].message)

        provider.updateDiagnostics(uri, listOf(
            createDiagnostic(0, 0, message = "New")
        ))

        assertEquals(1, provider.getAllDiagnostics(uri).size)
        assertEquals("New", provider.getAllDiagnostics(uri)[0].message)
    }
}
