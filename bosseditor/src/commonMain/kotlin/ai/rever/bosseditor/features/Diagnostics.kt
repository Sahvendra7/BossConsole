package ai.rever.bosseditor.features

import ai.rever.bosseditor.core.EditorPosition
import ai.rever.bosseditor.core.EditorRange

/**
 * Severity levels for editor diagnostics.
 * Matches common LSP/IDE severity levels.
 */
enum class DiagnosticSeverity {
    /** Critical error that prevents compilation/execution */
    ERROR,

    /** Warning that may indicate a problem */
    WARNING,

    /** Informational message */
    INFO,

    /** Style/convention hint */
    HINT
}

/**
 * Represents a diagnostic (error, warning, info, hint) in the editor.
 *
 * Diagnostics are displayed as:
 * - Squiggly underlines under the affected code
 * - Gutter icons on the affected lines
 * - Tooltips on hover
 *
 * @property range The range in the document where the diagnostic applies
 * @property message The diagnostic message to display
 * @property severity The severity level (error, warning, info, hint)
 * @property source Optional source identifier (e.g., "kotlin", "eslint")
 * @property code Optional error code (e.g., "E0001", "unused-variable")
 */
data class Diagnostic(
    val range: EditorRange,
    val message: String,
    val severity: DiagnosticSeverity,
    val source: String? = null,
    val code: String? = null
) {
    /** The starting line of this diagnostic */
    val startLine: Int get() = range.start.line

    /** The ending line of this diagnostic */
    val endLine: Int get() = range.end.line

    /** Whether this diagnostic spans multiple lines */
    val isMultiLine: Boolean get() = startLine != endLine

    companion object {
        /**
         * Creates a diagnostic for a single position (point diagnostic).
         */
        fun at(
            position: EditorPosition,
            message: String,
            severity: DiagnosticSeverity,
            source: String? = null,
            code: String? = null
        ): Diagnostic = Diagnostic(
            range = EditorRange(position, position),
            message = message,
            severity = severity,
            source = source,
            code = code
        )

        /**
         * Creates an error diagnostic.
         */
        fun error(
            range: EditorRange,
            message: String,
            source: String? = null,
            code: String? = null
        ): Diagnostic = Diagnostic(range, message, DiagnosticSeverity.ERROR, source, code)

        /**
         * Creates a warning diagnostic.
         */
        fun warning(
            range: EditorRange,
            message: String,
            source: String? = null,
            code: String? = null
        ): Diagnostic = Diagnostic(range, message, DiagnosticSeverity.WARNING, source, code)

        /**
         * Creates an info diagnostic.
         */
        fun info(
            range: EditorRange,
            message: String,
            source: String? = null,
            code: String? = null
        ): Diagnostic = Diagnostic(range, message, DiagnosticSeverity.INFO, source, code)

        /**
         * Creates a hint diagnostic.
         */
        fun hint(
            range: EditorRange,
            message: String,
            source: String? = null,
            code: String? = null
        ): Diagnostic = Diagnostic(range, message, DiagnosticSeverity.HINT, source, code)
    }
}

/**
 * Manages diagnostics for the editor.
 * Provides efficient lookup of diagnostics by line and position.
 */
class DiagnosticsManager {
    private val diagnostics = mutableListOf<Diagnostic>()
    private var diagnosticsByLine: Map<Int, List<Diagnostic>> = emptyMap()

    /**
     * Sets the diagnostics, replacing any existing ones.
     */
    fun setDiagnostics(newDiagnostics: List<Diagnostic>) {
        diagnostics.clear()
        diagnostics.addAll(newDiagnostics)
        rebuildIndex()
    }

    /**
     * Adds a single diagnostic.
     */
    fun addDiagnostic(diagnostic: Diagnostic) {
        diagnostics.add(diagnostic)
        rebuildIndex()
    }

    /**
     * Removes all diagnostics.
     */
    fun clear() {
        diagnostics.clear()
        diagnosticsByLine = emptyMap()
    }

    /**
     * Gets all diagnostics.
     */
    fun getAllDiagnostics(): List<Diagnostic> = diagnostics.toList()

    /**
     * Gets diagnostics for a specific line.
     */
    fun getDiagnosticsForLine(line: Int): List<Diagnostic> {
        return diagnosticsByLine[line] ?: emptyList()
    }

    /**
     * Gets diagnostics at a specific position.
     */
    fun getDiagnosticsAtPosition(position: EditorPosition): List<Diagnostic> {
        return getDiagnosticsForLine(position.line).filter { diagnostic ->
            position in diagnostic.range
        }
    }

    /**
     * Gets the highest severity diagnostic for a line.
     * Useful for determining which gutter icon to show.
     */
    fun getHighestSeverityForLine(line: Int): DiagnosticSeverity? {
        return getDiagnosticsForLine(line)
            .minByOrNull { it.severity.ordinal }
            ?.severity
    }

    /**
     * Checks if there are any errors in the document.
     */
    fun hasErrors(): Boolean = diagnostics.any { it.severity == DiagnosticSeverity.ERROR }

    /**
     * Checks if there are any warnings in the document.
     */
    fun hasWarnings(): Boolean = diagnostics.any { it.severity == DiagnosticSeverity.WARNING }

    /**
     * Gets count of diagnostics by severity.
     */
    fun countBySeverity(severity: DiagnosticSeverity): Int =
        diagnostics.count { it.severity == severity }

    private fun rebuildIndex() {
        val byLine = mutableMapOf<Int, MutableList<Diagnostic>>()
        for (diagnostic in diagnostics) {
            for (line in diagnostic.startLine..diagnostic.endLine) {
                byLine.getOrPut(line) { mutableListOf() }.add(diagnostic)
            }
        }
        diagnosticsByLine = byLine
    }
}
