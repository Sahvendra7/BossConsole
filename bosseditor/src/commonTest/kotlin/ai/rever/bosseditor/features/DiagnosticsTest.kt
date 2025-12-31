package ai.rever.bosseditor.features

import ai.rever.bosseditor.core.EditorPosition
import ai.rever.bosseditor.core.EditorRange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DiagnosticsTest {

    @Test
    fun testDiagnosticCreation() {
        val range = EditorRange(
            EditorPosition(0, 5),
            EditorPosition(0, 10)
        )
        val diagnostic = Diagnostic(
            range = range,
            message = "Unresolved reference",
            severity = DiagnosticSeverity.ERROR,
            source = "kotlin",
            code = "UNRESOLVED_REFERENCE"
        )

        assertEquals(range, diagnostic.range)
        assertEquals("Unresolved reference", diagnostic.message)
        assertEquals(DiagnosticSeverity.ERROR, diagnostic.severity)
        assertEquals("kotlin", diagnostic.source)
        assertEquals("UNRESOLVED_REFERENCE", diagnostic.code)
        assertEquals(0, diagnostic.startLine)
        assertEquals(0, diagnostic.endLine)
        assertFalse(diagnostic.isMultiLine)
    }

    @Test
    fun testMultiLineDiagnostic() {
        val range = EditorRange(
            EditorPosition(5, 0),
            EditorPosition(10, 20)
        )
        val diagnostic = Diagnostic(
            range = range,
            message = "Multi-line error",
            severity = DiagnosticSeverity.WARNING
        )

        assertEquals(5, diagnostic.startLine)
        assertEquals(10, diagnostic.endLine)
        assertTrue(diagnostic.isMultiLine)
    }

    @Test
    fun testDiagnosticFactoryMethods() {
        val range = EditorRange(
            EditorPosition(1, 0),
            EditorPosition(1, 5)
        )

        val error = Diagnostic.error(range, "Error message")
        assertEquals(DiagnosticSeverity.ERROR, error.severity)

        val warning = Diagnostic.warning(range, "Warning message")
        assertEquals(DiagnosticSeverity.WARNING, warning.severity)

        val info = Diagnostic.info(range, "Info message")
        assertEquals(DiagnosticSeverity.INFO, info.severity)

        val hint = Diagnostic.hint(range, "Hint message")
        assertEquals(DiagnosticSeverity.HINT, hint.severity)
    }

    @Test
    fun testPointDiagnostic() {
        val position = EditorPosition(5, 10)
        val diagnostic = Diagnostic.at(
            position = position,
            message = "Point diagnostic",
            severity = DiagnosticSeverity.INFO
        )

        assertEquals(position, diagnostic.range.start)
        assertEquals(position, diagnostic.range.end)
        assertFalse(diagnostic.isMultiLine)
    }

    @Test
    fun testDiagnosticsManagerSetAndGet() {
        val manager = DiagnosticsManager()
        val diagnostics = listOf(
            Diagnostic.error(
                EditorRange(EditorPosition(0, 0), EditorPosition(0, 5)),
                "Error 1"
            ),
            Diagnostic.warning(
                EditorRange(EditorPosition(1, 0), EditorPosition(1, 10)),
                "Warning 1"
            ),
            Diagnostic.info(
                EditorRange(EditorPosition(0, 10), EditorPosition(0, 15)),
                "Info 1"
            )
        )

        manager.setDiagnostics(diagnostics)

        assertEquals(3, manager.getAllDiagnostics().size)
    }

    @Test
    fun testDiagnosticsManagerGetByLine() {
        val manager = DiagnosticsManager()
        manager.setDiagnostics(listOf(
            Diagnostic.error(
                EditorRange(EditorPosition(0, 0), EditorPosition(0, 5)),
                "Error on line 0"
            ),
            Diagnostic.warning(
                EditorRange(EditorPosition(1, 0), EditorPosition(1, 10)),
                "Warning on line 1"
            ),
            Diagnostic.info(
                EditorRange(EditorPosition(0, 10), EditorPosition(0, 15)),
                "Info on line 0"
            )
        ))

        val line0Diagnostics = manager.getDiagnosticsForLine(0)
        assertEquals(2, line0Diagnostics.size)

        val line1Diagnostics = manager.getDiagnosticsForLine(1)
        assertEquals(1, line1Diagnostics.size)

        val line2Diagnostics = manager.getDiagnosticsForLine(2)
        assertTrue(line2Diagnostics.isEmpty())
    }

    @Test
    fun testDiagnosticsManagerHighestSeverity() {
        val manager = DiagnosticsManager()
        manager.setDiagnostics(listOf(
            Diagnostic.info(
                EditorRange(EditorPosition(0, 0), EditorPosition(0, 5)),
                "Info"
            ),
            Diagnostic.error(
                EditorRange(EditorPosition(0, 10), EditorPosition(0, 15)),
                "Error"
            ),
            Diagnostic.warning(
                EditorRange(EditorPosition(0, 20), EditorPosition(0, 25)),
                "Warning"
            )
        ))

        val severity = manager.getHighestSeverityForLine(0)
        assertEquals(DiagnosticSeverity.ERROR, severity)
    }

    @Test
    fun testDiagnosticsManagerClear() {
        val manager = DiagnosticsManager()
        manager.setDiagnostics(listOf(
            Diagnostic.error(
                EditorRange(EditorPosition(0, 0), EditorPosition(0, 5)),
                "Error"
            )
        ))

        assertTrue(manager.hasErrors())
        manager.clear()
        assertFalse(manager.hasErrors())
        assertTrue(manager.getAllDiagnostics().isEmpty())
    }

    @Test
    fun testDiagnosticsManagerCountBySeverity() {
        val manager = DiagnosticsManager()
        manager.setDiagnostics(listOf(
            Diagnostic.error(
                EditorRange(EditorPosition(0, 0), EditorPosition(0, 5)),
                "Error 1"
            ),
            Diagnostic.error(
                EditorRange(EditorPosition(1, 0), EditorPosition(1, 5)),
                "Error 2"
            ),
            Diagnostic.warning(
                EditorRange(EditorPosition(2, 0), EditorPosition(2, 5)),
                "Warning"
            )
        ))

        assertEquals(2, manager.countBySeverity(DiagnosticSeverity.ERROR))
        assertEquals(1, manager.countBySeverity(DiagnosticSeverity.WARNING))
        assertEquals(0, manager.countBySeverity(DiagnosticSeverity.INFO))
    }

    @Test
    fun testMultiLineDiagnosticIndexing() {
        val manager = DiagnosticsManager()
        manager.setDiagnostics(listOf(
            Diagnostic.error(
                EditorRange(EditorPosition(5, 0), EditorPosition(10, 20)),
                "Multi-line error"
            )
        ))

        // Diagnostic should appear on all lines it spans
        for (line in 5..10) {
            val diagnostics = manager.getDiagnosticsForLine(line)
            assertEquals(1, diagnostics.size)
        }

        // Should not appear on other lines
        assertTrue(manager.getDiagnosticsForLine(4).isEmpty())
        assertTrue(manager.getDiagnosticsForLine(11).isEmpty())
    }
}
