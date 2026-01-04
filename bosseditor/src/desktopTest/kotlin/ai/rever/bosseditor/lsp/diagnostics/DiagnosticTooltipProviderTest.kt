package ai.rever.bosseditor.lsp.diagnostics

import ai.rever.bosseditor.lsp.protocol.Diagnostic
import ai.rever.bosseditor.lsp.protocol.DiagnosticRelatedInformation
import ai.rever.bosseditor.lsp.protocol.DiagnosticSeverity
import ai.rever.bosseditor.lsp.protocol.DiagnosticTag
import ai.rever.bosseditor.lsp.protocol.Location
import ai.rever.bosseditor.lsp.protocol.Position
import ai.rever.bosseditor.lsp.protocol.Range
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DiagnosticTooltipProviderTest {

    private fun createDiagnostic(
        startLine: Int,
        startChar: Int,
        endLine: Int = startLine,
        endChar: Int = startChar + 5,
        message: String = "Test diagnostic",
        severity: Int = DiagnosticSeverity.ERROR,
        source: String? = "test",
        code: String? = null,
        tags: List<Int>? = null,
        relatedInformation: List<DiagnosticRelatedInformation>? = null
    ): Diagnostic {
        return Diagnostic(
            range = Range(
                start = Position(startLine, startChar),
                end = Position(endLine, endChar)
            ),
            message = message,
            severity = severity,
            source = source,
            code = code,
            tags = tags,
            relatedInformation = relatedInformation
        )
    }

    @Test
    fun testHasTooltip() {
        val provider = LspDiagnosticsProvider()
        val tooltipProvider = DiagnosticTooltipProvider(provider)
        val uri = "file:///test.kt"

        provider.updateDiagnostics(uri, listOf(
            createDiagnostic(0, 5, endChar = 15)
        ))

        assertFalse(tooltipProvider.hasTooltip(uri, 0, 0))
        assertTrue(tooltipProvider.hasTooltip(uri, 0, 10))
        assertFalse(tooltipProvider.hasTooltip(uri, 0, 20))
    }

    @Test
    fun testGetTooltipContentNull() {
        val provider = LspDiagnosticsProvider()
        val tooltipProvider = DiagnosticTooltipProvider(provider)
        val uri = "file:///test.kt"

        val content = tooltipProvider.getTooltipContent(uri, 0, 0)
        assertNull(content)
    }

    @Test
    fun testGetTooltipContentSingle() {
        val provider = LspDiagnosticsProvider()
        val tooltipProvider = DiagnosticTooltipProvider(provider)
        val uri = "file:///test.kt"

        provider.updateDiagnostics(uri, listOf(
            createDiagnostic(0, 0, message = "Test error", severity = DiagnosticSeverity.ERROR, source = "compiler", code = "E001")
        ))

        val content = tooltipProvider.getTooltipContent(uri, 0, 2)
        assertNotNull(content)
        assertEquals(1, content.entries.size)
        assertFalse(content.hasMultiple)
        assertEquals(DiagnosticSeverity.ERROR, content.maxSeverity)

        val entry = content.entries[0]
        assertEquals(DiagnosticSeverity.ERROR, entry.severity)
        assertEquals("Test error", entry.message)
        assertEquals("compiler", entry.source)
        assertEquals("E001", entry.code)
        assertEquals("Error", entry.severityLabel)
        assertEquals("✕", entry.severityIcon)
    }

    @Test
    fun testGetTooltipContentMultiple() {
        val provider = LspDiagnosticsProvider()
        val tooltipProvider = DiagnosticTooltipProvider(provider)
        val uri = "file:///test.kt"

        provider.updateDiagnostics(uri, listOf(
            createDiagnostic(0, 0, endChar = 20, message = "Error", severity = DiagnosticSeverity.ERROR),
            createDiagnostic(0, 5, endChar = 15, message = "Warning", severity = DiagnosticSeverity.WARNING)
        ))

        val content = tooltipProvider.getTooltipContent(uri, 0, 10)
        assertNotNull(content)
        assertEquals(2, content.entries.size)
        assertTrue(content.hasMultiple)
        assertEquals(DiagnosticSeverity.ERROR, content.maxSeverity)
    }

    @Test
    fun testGetLineTooltipContent() {
        val provider = LspDiagnosticsProvider()
        val tooltipProvider = DiagnosticTooltipProvider(provider)
        val uri = "file:///test.kt"

        provider.updateDiagnostics(uri, listOf(
            createDiagnostic(0, 0, message = "First"),
            createDiagnostic(0, 20, message = "Second")
        ))

        val content = tooltipProvider.getLineTooltipContent(uri, 0)
        assertNotNull(content)
        assertEquals(2, content.entries.size)
    }

    @Test
    fun testGetLineTooltipContentNull() {
        val provider = LspDiagnosticsProvider()
        val tooltipProvider = DiagnosticTooltipProvider(provider)
        val uri = "file:///test.kt"

        val content = tooltipProvider.getLineTooltipContent(uri, 0)
        assertNull(content)
    }

    @Test
    fun testGetBriefSummary() {
        val provider = LspDiagnosticsProvider()
        val tooltipProvider = DiagnosticTooltipProvider(provider)
        val uri = "file:///test.kt"

        provider.updateDiagnostics(uri, listOf(
            createDiagnostic(0, 0, message = "Single error")
        ))

        val summary = tooltipProvider.getBriefSummary(uri, 0, 2)
        assertEquals("Single error", summary)
    }

    @Test
    fun testGetBriefSummaryMultiple() {
        val provider = LspDiagnosticsProvider()
        val tooltipProvider = DiagnosticTooltipProvider(provider)
        val uri = "file:///test.kt"

        provider.updateDiagnostics(uri, listOf(
            createDiagnostic(0, 0, endChar = 20, message = "Most severe", severity = DiagnosticSeverity.ERROR),
            createDiagnostic(0, 5, endChar = 15, message = "Less severe", severity = DiagnosticSeverity.WARNING),
            createDiagnostic(0, 5, endChar = 15, message = "Least severe", severity = DiagnosticSeverity.HINT)
        ))

        val summary = tooltipProvider.getBriefSummary(uri, 0, 10)
        assertNotNull(summary)
        assertTrue(summary.contains("Most severe"))
        assertTrue(summary.contains("+2 more"))
    }

    @Test
    fun testGetBriefSummaryNull() {
        val provider = LspDiagnosticsProvider()
        val tooltipProvider = DiagnosticTooltipProvider(provider)
        val uri = "file:///test.kt"

        val summary = tooltipProvider.getBriefSummary(uri, 0, 0)
        assertNull(summary)
    }

    @Test
    fun testTooltipEntryDeprecated() {
        val provider = LspDiagnosticsProvider()
        val tooltipProvider = DiagnosticTooltipProvider(provider)
        val uri = "file:///test.kt"

        provider.updateDiagnostics(uri, listOf(
            createDiagnostic(0, 0, message = "Deprecated API", tags = listOf(DiagnosticTag.DEPRECATED))
        ))

        val content = tooltipProvider.getTooltipContent(uri, 0, 2)
        assertNotNull(content)
        assertTrue(content.entries[0].isDeprecated)
        assertFalse(content.entries[0].isUnnecessary)
    }

    @Test
    fun testTooltipEntryUnnecessary() {
        val provider = LspDiagnosticsProvider()
        val tooltipProvider = DiagnosticTooltipProvider(provider)
        val uri = "file:///test.kt"

        provider.updateDiagnostics(uri, listOf(
            createDiagnostic(0, 0, message = "Unused variable", tags = listOf(DiagnosticTag.UNNECESSARY))
        ))

        val content = tooltipProvider.getTooltipContent(uri, 0, 2)
        assertNotNull(content)
        assertFalse(content.entries[0].isDeprecated)
        assertTrue(content.entries[0].isUnnecessary)
    }

    @Test
    fun testTooltipEntryWithRelatedInformation() {
        val provider = LspDiagnosticsProvider()
        val tooltipProvider = DiagnosticTooltipProvider(provider)
        val uri = "file:///test.kt"

        provider.updateDiagnostics(uri, listOf(
            createDiagnostic(
                0, 0,
                message = "Error with related info",
                relatedInformation = listOf(
                    DiagnosticRelatedInformation(
                        location = Location(
                            uri = "file:///other.kt",
                            range = Range(Position(10, 5), Position(10, 10))
                        ),
                        message = "Related info here"
                    )
                )
            )
        ))

        val content = tooltipProvider.getTooltipContent(uri, 0, 2)
        assertNotNull(content)
        assertNotNull(content.entries[0].relatedInformation)
        assertEquals(1, content.entries[0].relatedInformation!!.size)
        assertEquals("file:///other.kt", content.entries[0].relatedInformation!![0].uri)
        assertEquals(10, content.entries[0].relatedInformation!![0].line)
        assertEquals(5, content.entries[0].relatedInformation!![0].character)
        assertEquals("Related info here", content.entries[0].relatedInformation!![0].message)
    }

    @Test
    fun testSeverityLabel() {
        assertEquals("Error", DiagnosticTooltipEntry(DiagnosticSeverity.ERROR, "", null, null, null, false, false, null).severityLabel)
        assertEquals("Warning", DiagnosticTooltipEntry(DiagnosticSeverity.WARNING, "", null, null, null, false, false, null).severityLabel)
        assertEquals("Info", DiagnosticTooltipEntry(DiagnosticSeverity.INFORMATION, "", null, null, null, false, false, null).severityLabel)
        assertEquals("Hint", DiagnosticTooltipEntry(DiagnosticSeverity.HINT, "", null, null, null, false, false, null).severityLabel)
    }

    @Test
    fun testSeverityIcon() {
        assertEquals("✕", DiagnosticTooltipEntry(DiagnosticSeverity.ERROR, "", null, null, null, false, false, null).severityIcon)
        assertEquals("⚠", DiagnosticTooltipEntry(DiagnosticSeverity.WARNING, "", null, null, null, false, false, null).severityIcon)
        assertEquals("ℹ", DiagnosticTooltipEntry(DiagnosticSeverity.INFORMATION, "", null, null, null, false, false, null).severityIcon)
        assertEquals("💡", DiagnosticTooltipEntry(DiagnosticSeverity.HINT, "", null, null, null, false, false, null).severityIcon)
    }

    @Test
    fun testToPlainText() {
        val entry = DiagnosticTooltipEntry(
            severity = DiagnosticSeverity.ERROR,
            message = "Undefined variable 'foo'",
            source = "compiler",
            code = "E0001",
            codeDescription = null,
            isDeprecated = false,
            isUnnecessary = false,
            relatedInformation = null
        )

        val text = entry.toPlainText()
        assertTrue(text.contains("✕ Error"))
        assertTrue(text.contains("[compiler]"))
        assertTrue(text.contains("(E0001)"))
        assertTrue(text.contains("Undefined variable 'foo'"))
    }

    @Test
    fun testToPlainTextWithTags() {
        val entry = DiagnosticTooltipEntry(
            severity = DiagnosticSeverity.WARNING,
            message = "Deprecated function",
            source = null,
            code = null,
            codeDescription = null,
            isDeprecated = true,
            isUnnecessary = false,
            relatedInformation = null
        )

        val text = entry.toPlainText()
        assertTrue(text.contains("[deprecated]"))
    }

    @Test
    fun testToMarkdown() {
        val entry = DiagnosticTooltipEntry(
            severity = DiagnosticSeverity.ERROR,
            message = "Type mismatch",
            source = "typescript",
            code = "TS2322",
            codeDescription = "https://example.com/ts2322",
            isDeprecated = false,
            isUnnecessary = false,
            relatedInformation = null
        )

        val markdown = entry.toMarkdown()
        assertTrue(markdown.contains("**Error**"))
        assertTrue(markdown.contains("`typescript`"))
        assertTrue(markdown.contains("[TS2322](https://example.com/ts2322)"))
        assertTrue(markdown.contains("Type mismatch"))
    }

    @Test
    fun testToMarkdownWithRelatedInfo() {
        val entry = DiagnosticTooltipEntry(
            severity = DiagnosticSeverity.ERROR,
            message = "Error here",
            source = null,
            code = null,
            codeDescription = null,
            isDeprecated = false,
            isUnnecessary = false,
            relatedInformation = listOf(
                RelatedInformation(
                    uri = "file:///other.kt",
                    line = 10,
                    character = 5,
                    message = "See also"
                )
            )
        )

        val markdown = entry.toMarkdown()
        assertTrue(markdown.contains("↳ See also"))
        assertTrue(markdown.contains("file:///other.kt:11:6"))
    }

    @Test
    fun testTooltipContentToPlainText() {
        val content = DiagnosticTooltipContent(
            entries = listOf(
                DiagnosticTooltipEntry(DiagnosticSeverity.ERROR, "First error", null, null, null, false, false, null),
                DiagnosticTooltipEntry(DiagnosticSeverity.WARNING, "Second warning", null, null, null, false, false, null)
            ),
            position = TooltipPosition(0, 5)
        )

        val text = content.toPlainText()
        assertTrue(text.contains("First error"))
        assertTrue(text.contains("Second warning"))
    }

    @Test
    fun testTooltipContentToMarkdown() {
        val content = DiagnosticTooltipContent(
            entries = listOf(
                DiagnosticTooltipEntry(DiagnosticSeverity.ERROR, "First", null, null, null, false, false, null),
                DiagnosticTooltipEntry(DiagnosticSeverity.WARNING, "Second", null, null, null, false, false, null)
            ),
            position = TooltipPosition(0, 5)
        )

        val markdown = content.toMarkdown()
        assertTrue(markdown.contains("---"))
        assertTrue(markdown.contains("First"))
        assertTrue(markdown.contains("Second"))
    }

    @Test
    fun testDecorationToTooltipEntry() {
        val decoration = DiagnosticDecoration(
            startOffset = 0,
            endOffset = 10,
            type = DecorationType.STRIKETHROUGH,
            message = "Deprecated method",
            severity = DiagnosticSeverity.HINT,
            source = "lint",
            code = "D001"
        )

        val entry = decoration.toTooltipEntry()
        assertEquals(DiagnosticSeverity.HINT, entry.severity)
        assertEquals("Deprecated method", entry.message)
        assertEquals("lint", entry.source)
        assertEquals("D001", entry.code)
        assertTrue(entry.isDeprecated)
        assertFalse(entry.isUnnecessary)
    }
}
