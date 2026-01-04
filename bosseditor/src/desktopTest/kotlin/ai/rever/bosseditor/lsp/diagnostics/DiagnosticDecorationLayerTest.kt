package ai.rever.bosseditor.lsp.diagnostics

import ai.rever.bosseditor.lsp.protocol.Diagnostic
import ai.rever.bosseditor.lsp.protocol.DiagnosticSeverity
import ai.rever.bosseditor.lsp.protocol.DiagnosticTag
import ai.rever.bosseditor.lsp.protocol.Position
import ai.rever.bosseditor.lsp.protocol.Range
import androidx.compose.ui.text.style.TextDecoration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DiagnosticDecorationLayerTest {

    private fun createDiagnostic(
        startLine: Int,
        startChar: Int,
        endLine: Int = startLine,
        endChar: Int = startChar + 5,
        message: String = "Test diagnostic",
        severity: Int = DiagnosticSeverity.ERROR,
        source: String? = "test",
        code: String? = null,
        tags: List<Int>? = null
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
            tags = tags
        )
    }

    @Test
    fun testGetLineDecorationsEmpty() {
        val provider = LspDiagnosticsProvider()
        val layer = DiagnosticDecorationLayer(provider)
        val uri = "file:///test.kt"

        val decorations = layer.getLineDecorations(uri, 0)
        assertTrue(decorations.isEmpty())
    }

    @Test
    fun testGetLineDecorationsError() {
        val provider = LspDiagnosticsProvider()
        val layer = DiagnosticDecorationLayer(provider)
        val uri = "file:///test.kt"

        provider.updateDiagnostics(uri, listOf(
            createDiagnostic(0, 5, endChar = 10, severity = DiagnosticSeverity.ERROR, message = "Error here")
        ))

        val decorations = layer.getLineDecorations(uri, 0)
        assertEquals(1, decorations.size)

        val decoration = decorations[0]
        assertEquals(5, decoration.startOffset)
        assertEquals(10, decoration.endOffset)
        assertEquals(DecorationType.SQUIGGLY_ERROR, decoration.type)
        assertEquals("Error here", decoration.message)
        assertEquals(DiagnosticSeverity.ERROR, decoration.severity)
    }

    @Test
    fun testGetLineDecorationsWarning() {
        val provider = LspDiagnosticsProvider()
        val layer = DiagnosticDecorationLayer(provider)
        val uri = "file:///test.kt"

        provider.updateDiagnostics(uri, listOf(
            createDiagnostic(0, 0, severity = DiagnosticSeverity.WARNING)
        ))

        val decorations = layer.getLineDecorations(uri, 0)
        assertEquals(DecorationType.SQUIGGLY_WARNING, decorations[0].type)
    }

    @Test
    fun testGetLineDecorationsInfo() {
        val provider = LspDiagnosticsProvider()
        val layer = DiagnosticDecorationLayer(provider)
        val uri = "file:///test.kt"

        provider.updateDiagnostics(uri, listOf(
            createDiagnostic(0, 0, severity = DiagnosticSeverity.INFORMATION)
        ))

        val decorations = layer.getLineDecorations(uri, 0)
        assertEquals(DecorationType.SQUIGGLY_INFO, decorations[0].type)
    }

    @Test
    fun testGetLineDecorationsHint() {
        val provider = LspDiagnosticsProvider()
        val layer = DiagnosticDecorationLayer(provider)
        val uri = "file:///test.kt"

        provider.updateDiagnostics(uri, listOf(
            createDiagnostic(0, 0, severity = DiagnosticSeverity.HINT)
        ))

        val decorations = layer.getLineDecorations(uri, 0)
        assertEquals(DecorationType.SQUIGGLY_HINT, decorations[0].type)
    }

    @Test
    fun testGetLineDecorationsDeprecated() {
        val provider = LspDiagnosticsProvider()
        val layer = DiagnosticDecorationLayer(provider)
        val uri = "file:///test.kt"

        provider.updateDiagnostics(uri, listOf(
            createDiagnostic(0, 0, severity = DiagnosticSeverity.HINT, tags = listOf(DiagnosticTag.DEPRECATED))
        ))

        val decorations = layer.getLineDecorations(uri, 0)
        assertEquals(DecorationType.STRIKETHROUGH, decorations[0].type)
    }

    @Test
    fun testGetLineDecorationsUnnecessary() {
        val provider = LspDiagnosticsProvider()
        val layer = DiagnosticDecorationLayer(provider)
        val uri = "file:///test.kt"

        provider.updateDiagnostics(uri, listOf(
            createDiagnostic(0, 0, severity = DiagnosticSeverity.HINT, tags = listOf(DiagnosticTag.UNNECESSARY))
        ))

        val decorations = layer.getLineDecorations(uri, 0)
        assertEquals(DecorationType.FADED, decorations[0].type)
    }

    @Test
    fun testGetLineDecorationsMultiLine_StartLine() {
        val provider = LspDiagnosticsProvider()
        val layer = DiagnosticDecorationLayer(provider)
        val uri = "file:///test.kt"

        provider.updateDiagnostics(uri, listOf(
            createDiagnostic(0, 5, endLine = 2, endChar = 10, message = "Multi-line")
        ))

        val decorations = layer.getLineDecorations(uri, 0)
        assertEquals(1, decorations.size)
        assertEquals(5, decorations[0].startOffset)
        assertEquals(Int.MAX_VALUE, decorations[0].endOffset)
    }

    @Test
    fun testGetLineDecorationsMultiLine_MiddleLine() {
        val provider = LspDiagnosticsProvider()
        val layer = DiagnosticDecorationLayer(provider)
        val uri = "file:///test.kt"

        // Note: Diagnostics are indexed by their START line only in the provider
        // Middle lines won't have decorations in the current implementation
        provider.updateDiagnostics(uri, listOf(
            createDiagnostic(0, 5, endLine = 2, endChar = 10, message = "Multi-line")
        ))

        // Middle lines are not indexed - no decorations returned
        val decorations = layer.getLineDecorations(uri, 1)
        assertEquals(0, decorations.size)
    }

    @Test
    fun testGetLineDecorationsMultiLine_EndLine() {
        val provider = LspDiagnosticsProvider()
        val layer = DiagnosticDecorationLayer(provider)
        val uri = "file:///test.kt"

        // Note: Diagnostics are indexed by their START line only in the provider
        // End lines won't have decorations in the current implementation
        provider.updateDiagnostics(uri, listOf(
            createDiagnostic(0, 5, endLine = 2, endChar = 10, message = "Multi-line")
        ))

        // End line is not indexed - no decorations returned
        val decorations = layer.getLineDecorations(uri, 2)
        assertEquals(0, decorations.size)
    }

    @Test
    fun testGetDecorationsInRange() {
        val provider = LspDiagnosticsProvider()
        val layer = DiagnosticDecorationLayer(provider)
        val uri = "file:///test.kt"

        provider.updateDiagnostics(uri, listOf(
            createDiagnostic(0, 0, message = "Line 0"),
            createDiagnostic(2, 0, message = "Line 2"),
            createDiagnostic(4, 0, message = "Line 4"),
            createDiagnostic(6, 0, message = "Line 6")
        ))

        val decorations = layer.getDecorationsInRange(uri, 1, 5)
        assertEquals(2, decorations.size)
        assertTrue(decorations.containsKey(2))
        assertTrue(decorations.containsKey(4))
    }

    @Test
    fun testGetSpanStyleError() {
        val provider = LspDiagnosticsProvider()
        val layer = DiagnosticDecorationLayer(provider)

        val decoration = DiagnosticDecoration(
            startOffset = 0,
            endOffset = 5,
            type = DecorationType.SQUIGGLY_ERROR,
            message = "Error",
            severity = DiagnosticSeverity.ERROR
        )

        val style = layer.getSpanStyle(decoration)
        assertEquals(TextDecoration.Underline, style.textDecoration)
        assertEquals(DiagnosticColors.ERROR, style.color)
    }

    @Test
    fun testGetSpanStyleStrikethrough() {
        val provider = LspDiagnosticsProvider()
        val layer = DiagnosticDecorationLayer(provider)

        val decoration = DiagnosticDecoration(
            startOffset = 0,
            endOffset = 5,
            type = DecorationType.STRIKETHROUGH,
            message = "Deprecated",
            severity = DiagnosticSeverity.HINT
        )

        val style = layer.getSpanStyle(decoration)
        assertEquals(TextDecoration.LineThrough, style.textDecoration)
    }

    @Test
    fun testGetSpanStyleFaded() {
        val provider = LspDiagnosticsProvider()
        val layer = DiagnosticDecorationLayer(provider)

        val decoration = DiagnosticDecoration(
            startOffset = 0,
            endOffset = 5,
            type = DecorationType.FADED,
            message = "Unused",
            severity = DiagnosticSeverity.HINT
        )

        val style = layer.getSpanStyle(decoration)
        assertNotNull(style.color)
        // Faded should have reduced alpha
        assertTrue(style.color.alpha < 1.0f)
    }

    @Test
    fun testHasDecorations() {
        val provider = LspDiagnosticsProvider()
        val layer = DiagnosticDecorationLayer(provider)
        val uri = "file:///test.kt"

        provider.updateDiagnostics(uri, listOf(
            createDiagnostic(0, 0)
        ))

        assertTrue(layer.hasDecorations(uri, 0))
        assertFalse(layer.hasDecorations(uri, 1))
    }

    @Test
    fun testDiagnosticDecorationLength() {
        val decoration = DiagnosticDecoration(
            startOffset = 5,
            endOffset = 15,
            type = DecorationType.SQUIGGLY_ERROR,
            message = "Test",
            severity = DiagnosticSeverity.ERROR
        )

        assertEquals(10, decoration.length)
    }

    @Test
    fun testDiagnosticDecorationLengthToEndOfLine() {
        val decoration = DiagnosticDecoration(
            startOffset = 5,
            endOffset = Int.MAX_VALUE,
            type = DecorationType.SQUIGGLY_ERROR,
            message = "Test",
            severity = DiagnosticSeverity.ERROR
        )

        assertEquals(-1, decoration.length)
    }

    @Test
    fun testDiagnosticDecorationContains() {
        val decoration = DiagnosticDecoration(
            startOffset = 5,
            endOffset = 15,
            type = DecorationType.SQUIGGLY_ERROR,
            message = "Test",
            severity = DiagnosticSeverity.ERROR
        )

        assertFalse(decoration.contains(4))
        assertTrue(decoration.contains(5))
        assertTrue(decoration.contains(10))
        assertTrue(decoration.contains(14))
        assertFalse(decoration.contains(15))
    }

    @Test
    fun testDiagnosticDecorationContainsToEndOfLine() {
        val decoration = DiagnosticDecoration(
            startOffset = 5,
            endOffset = Int.MAX_VALUE,
            type = DecorationType.SQUIGGLY_ERROR,
            message = "Test",
            severity = DiagnosticSeverity.ERROR
        )

        assertFalse(decoration.contains(4))
        assertTrue(decoration.contains(5))
        assertTrue(decoration.contains(100))
        assertTrue(decoration.contains(1000))
    }

    @Test
    fun testDiagnosticDecorationOverlaps() {
        val decoration = DiagnosticDecoration(
            startOffset = 5,
            endOffset = 15,
            type = DecorationType.SQUIGGLY_ERROR,
            message = "Test",
            severity = DiagnosticSeverity.ERROR
        )

        // No overlap
        assertFalse(decoration.overlaps(0, 5))
        assertFalse(decoration.overlaps(15, 20))

        // Partial overlap
        assertTrue(decoration.overlaps(0, 10))
        assertTrue(decoration.overlaps(10, 20))

        // Full overlap
        assertTrue(decoration.overlaps(5, 15))
        assertTrue(decoration.overlaps(0, 20))

        // Inside
        assertTrue(decoration.overlaps(7, 10))
    }

    @Test
    fun testDecorationTypeToColor() {
        assertEquals(DiagnosticColors.ERROR, DecorationType.SQUIGGLY_ERROR.toColor())
        assertEquals(DiagnosticColors.WARNING, DecorationType.SQUIGGLY_WARNING.toColor())
        assertEquals(DiagnosticColors.INFORMATION, DecorationType.SQUIGGLY_INFO.toColor())
        assertEquals(DiagnosticColors.HINT, DecorationType.SQUIGGLY_HINT.toColor())
    }
}
