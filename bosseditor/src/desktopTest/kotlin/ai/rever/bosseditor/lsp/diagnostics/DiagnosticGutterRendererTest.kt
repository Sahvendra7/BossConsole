package ai.rever.bosseditor.lsp.diagnostics

import ai.rever.bosseditor.lsp.protocol.Diagnostic
import ai.rever.bosseditor.lsp.protocol.DiagnosticSeverity
import ai.rever.bosseditor.lsp.protocol.Position
import ai.rever.bosseditor.lsp.protocol.Range
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DiagnosticGutterRendererTest {

    private fun createDiagnostic(
        startLine: Int,
        startChar: Int,
        message: String = "Test diagnostic",
        severity: Int = DiagnosticSeverity.ERROR,
        source: String? = "test",
        code: String? = null
    ): Diagnostic {
        return Diagnostic(
            range = Range(
                start = Position(startLine, startChar),
                end = Position(startLine, startChar + 5)
            ),
            message = message,
            severity = severity,
            source = source,
            code = code
        )
    }

    @Test
    fun testGetGutterIconError() {
        val provider = LspDiagnosticsProvider()
        val renderer = DiagnosticGutterRenderer(provider)
        val uri = "file:///test.kt"

        provider.updateDiagnostics(uri, listOf(
            createDiagnostic(0, 0, severity = DiagnosticSeverity.ERROR)
        ))

        val icon = renderer.getGutterIcon(uri, 0)
        assertNotNull(icon)
        assertEquals(GutterIconType.ERROR, icon.type)
        assertEquals(DiagnosticColors.ERROR, icon.color)
        assertEquals(1, icon.diagnosticCount)
    }

    @Test
    fun testGetGutterIconWarning() {
        val provider = LspDiagnosticsProvider()
        val renderer = DiagnosticGutterRenderer(provider)
        val uri = "file:///test.kt"

        provider.updateDiagnostics(uri, listOf(
            createDiagnostic(0, 0, severity = DiagnosticSeverity.WARNING)
        ))

        val icon = renderer.getGutterIcon(uri, 0)
        assertNotNull(icon)
        assertEquals(GutterIconType.WARNING, icon.type)
        assertEquals(DiagnosticColors.WARNING, icon.color)
    }

    @Test
    fun testGetGutterIconInfo() {
        val provider = LspDiagnosticsProvider()
        val renderer = DiagnosticGutterRenderer(provider)
        val uri = "file:///test.kt"

        provider.updateDiagnostics(uri, listOf(
            createDiagnostic(0, 0, severity = DiagnosticSeverity.INFORMATION)
        ))

        val icon = renderer.getGutterIcon(uri, 0)
        assertNotNull(icon)
        assertEquals(GutterIconType.INFO, icon.type)
        assertEquals(DiagnosticColors.INFORMATION, icon.color)
    }

    @Test
    fun testGetGutterIconHint() {
        val provider = LspDiagnosticsProvider()
        val renderer = DiagnosticGutterRenderer(provider)
        val uri = "file:///test.kt"

        provider.updateDiagnostics(uri, listOf(
            createDiagnostic(0, 0, severity = DiagnosticSeverity.HINT)
        ))

        val icon = renderer.getGutterIcon(uri, 0)
        assertNotNull(icon)
        assertEquals(GutterIconType.HINT, icon.type)
        assertEquals(DiagnosticColors.HINT, icon.color)
    }

    @Test
    fun testGetGutterIconNull() {
        val provider = LspDiagnosticsProvider()
        val renderer = DiagnosticGutterRenderer(provider)
        val uri = "file:///test.kt"

        val icon = renderer.getGutterIcon(uri, 0)
        assertNull(icon)
    }

    @Test
    fun testGetGutterIconMostSevere() {
        val provider = LspDiagnosticsProvider()
        val renderer = DiagnosticGutterRenderer(provider)
        val uri = "file:///test.kt"

        provider.updateDiagnostics(uri, listOf(
            createDiagnostic(0, 0, severity = DiagnosticSeverity.WARNING),
            createDiagnostic(0, 5, severity = DiagnosticSeverity.ERROR),
            createDiagnostic(0, 10, severity = DiagnosticSeverity.HINT)
        ))

        val icon = renderer.getGutterIcon(uri, 0)
        assertNotNull(icon)
        assertEquals(GutterIconType.ERROR, icon.type)
        assertEquals(3, icon.diagnosticCount)
    }

    @Test
    fun testGetGutterIconTooltipSingle() {
        val provider = LspDiagnosticsProvider()
        val renderer = DiagnosticGutterRenderer(provider)
        val uri = "file:///test.kt"

        provider.updateDiagnostics(uri, listOf(
            createDiagnostic(0, 0, message = "Test error", severity = DiagnosticSeverity.ERROR, source = "test", code = "E001")
        ))

        val icon = renderer.getGutterIcon(uri, 0)
        assertNotNull(icon)
        assertTrue(icon.tooltip.contains("Error"))
        assertTrue(icon.tooltip.contains("Test error"))
        assertTrue(icon.tooltip.contains("[test]"))
        assertTrue(icon.tooltip.contains("(E001)"))
    }

    @Test
    fun testGetGutterIconTooltipMultiple() {
        val provider = LspDiagnosticsProvider()
        val renderer = DiagnosticGutterRenderer(provider)
        val uri = "file:///test.kt"

        provider.updateDiagnostics(uri, listOf(
            createDiagnostic(0, 0, message = "First error", severity = DiagnosticSeverity.ERROR),
            createDiagnostic(0, 5, message = "Second warning", severity = DiagnosticSeverity.WARNING)
        ))

        val icon = renderer.getGutterIcon(uri, 0)
        assertNotNull(icon)
        assertTrue(icon.tooltip.contains("1."))
        assertTrue(icon.tooltip.contains("2."))
        assertTrue(icon.tooltip.contains("First error"))
        assertTrue(icon.tooltip.contains("Second warning"))
    }

    @Test
    fun testGetGutterIconsInRange() {
        val provider = LspDiagnosticsProvider()
        val renderer = DiagnosticGutterRenderer(provider)
        val uri = "file:///test.kt"

        provider.updateDiagnostics(uri, listOf(
            createDiagnostic(0, 0, severity = DiagnosticSeverity.ERROR),
            createDiagnostic(2, 0, severity = DiagnosticSeverity.WARNING),
            createDiagnostic(5, 0, severity = DiagnosticSeverity.INFORMATION),
            createDiagnostic(10, 0, severity = DiagnosticSeverity.HINT)
        ))

        val icons = renderer.getGutterIconsInRange(uri, 1, 6)
        assertEquals(2, icons.size)
        assertTrue(icons.containsKey(2))
        assertTrue(icons.containsKey(5))
        assertFalse(icons.containsKey(0))
        assertFalse(icons.containsKey(10))
    }

    @Test
    fun testGetGutterIconsInRangeEmpty() {
        val provider = LspDiagnosticsProvider()
        val renderer = DiagnosticGutterRenderer(provider)
        val uri = "file:///test.kt"

        provider.updateDiagnostics(uri, listOf(
            createDiagnostic(0, 0, severity = DiagnosticSeverity.ERROR)
        ))

        val icons = renderer.getGutterIconsInRange(uri, 5, 10)
        assertTrue(icons.isEmpty())
    }

    @Test
    fun testHasGutterIcon() {
        val provider = LspDiagnosticsProvider()
        val renderer = DiagnosticGutterRenderer(provider)
        val uri = "file:///test.kt"

        provider.updateDiagnostics(uri, listOf(
            createDiagnostic(0, 0)
        ))

        assertTrue(renderer.hasGutterIcon(uri, 0))
        assertFalse(renderer.hasGutterIcon(uri, 1))
    }

    @Test
    fun testDiagnosticColorsForSeverity() {
        assertEquals(DiagnosticColors.ERROR, DiagnosticColors.forSeverity(DiagnosticSeverity.ERROR))
        assertEquals(DiagnosticColors.WARNING, DiagnosticColors.forSeverity(DiagnosticSeverity.WARNING))
        assertEquals(DiagnosticColors.INFORMATION, DiagnosticColors.forSeverity(DiagnosticSeverity.INFORMATION))
        assertEquals(DiagnosticColors.HINT, DiagnosticColors.forSeverity(DiagnosticSeverity.HINT))
        assertEquals(DiagnosticColors.INFORMATION, DiagnosticColors.forSeverity(99))
    }

    @Test
    fun testDiagnosticColorsBackgroundForSeverity() {
        assertEquals(DiagnosticColors.ERROR_BACKGROUND, DiagnosticColors.backgroundForSeverity(DiagnosticSeverity.ERROR))
        assertEquals(DiagnosticColors.WARNING_BACKGROUND, DiagnosticColors.backgroundForSeverity(DiagnosticSeverity.WARNING))
        assertEquals(androidx.compose.ui.graphics.Color.Transparent, DiagnosticColors.backgroundForSeverity(DiagnosticSeverity.INFORMATION))
    }
}
