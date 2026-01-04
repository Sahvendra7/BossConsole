package ai.rever.bosseditor.lsp.diagnostics

import ai.rever.bosseditor.lsp.protocol.Diagnostic
import ai.rever.bosseditor.lsp.protocol.DiagnosticSeverity
import androidx.compose.ui.graphics.Color

/**
 * Renders diagnostic indicators in the editor gutter.
 *
 * Provides:
 * - Gutter icons based on diagnostic severity
 * - Colors for different severity levels
 * - Tooltip text aggregation for multiple diagnostics
 *
 * ## Usage
 * ```kotlin
 * val renderer = DiagnosticGutterRenderer(diagnosticsProvider)
 *
 * // In gutter rendering code
 * val icon = renderer.getGutterIcon(uri, lineNumber)
 * if (icon != null) {
 *     drawGutterIcon(icon)
 * }
 * ```
 */
class DiagnosticGutterRenderer(
    private val diagnosticsProvider: LspDiagnosticsProvider
) {
    /**
     * Get the gutter icon for a line.
     *
     * @param uri The document URI
     * @param line The line number (0-based)
     * @return The gutter icon info, or null if no diagnostics
     */
    fun getGutterIcon(uri: String, line: Int): GutterIconInfo? {
        val severity = diagnosticsProvider.getLineSeverity(uri, line) ?: return null
        val diagnostics = diagnosticsProvider.getDiagnosticsForLine(uri, line)

        return GutterIconInfo(
            type = severityToIconType(severity),
            color = severityToColor(severity),
            tooltip = formatTooltip(diagnostics),
            diagnosticCount = diagnostics.size
        )
    }

    /**
     * Get gutter icons for a range of lines.
     *
     * Useful for rendering the visible viewport.
     *
     * @param uri The document URI
     * @param startLine Start line (inclusive, 0-based)
     * @param endLine End line (inclusive, 0-based)
     * @return Map of line number to gutter icon info
     */
    fun getGutterIconsInRange(
        uri: String,
        startLine: Int,
        endLine: Int
    ): Map<Int, GutterIconInfo> {
        val result = mutableMapOf<Int, GutterIconInfo>()

        for (line in startLine..endLine) {
            getGutterIcon(uri, line)?.let { icon ->
                result[line] = icon
            }
        }

        return result
    }

    /**
     * Check if a line has a gutter icon.
     *
     * @param uri The document URI
     * @param line The line number (0-based)
     * @return true if the line has diagnostics to show
     */
    fun hasGutterIcon(uri: String, line: Int): Boolean {
        return diagnosticsProvider.hasLineDiagnostics(uri, line)
    }

    /**
     * Convert severity to icon type.
     */
    private fun severityToIconType(severity: Int): GutterIconType {
        return when (severity) {
            DiagnosticSeverity.ERROR -> GutterIconType.ERROR
            DiagnosticSeverity.WARNING -> GutterIconType.WARNING
            DiagnosticSeverity.INFORMATION -> GutterIconType.INFO
            DiagnosticSeverity.HINT -> GutterIconType.HINT
            else -> GutterIconType.INFO
        }
    }

    /**
     * Convert severity to color.
     */
    private fun severityToColor(severity: Int): Color {
        return when (severity) {
            DiagnosticSeverity.ERROR -> DiagnosticColors.ERROR
            DiagnosticSeverity.WARNING -> DiagnosticColors.WARNING
            DiagnosticSeverity.INFORMATION -> DiagnosticColors.INFORMATION
            DiagnosticSeverity.HINT -> DiagnosticColors.HINT
            else -> DiagnosticColors.INFORMATION
        }
    }

    /**
     * Format tooltip text from diagnostics.
     */
    private fun formatTooltip(diagnostics: List<Diagnostic>): String {
        if (diagnostics.isEmpty()) return ""

        return if (diagnostics.size == 1) {
            formatSingleDiagnostic(diagnostics.first())
        } else {
            diagnostics.mapIndexed { index, diagnostic ->
                "${index + 1}. ${formatSingleDiagnostic(diagnostic)}"
            }.joinToString("\n")
        }
    }

    /**
     * Format a single diagnostic for tooltip.
     */
    private fun formatSingleDiagnostic(diagnostic: Diagnostic): String {
        val prefix = when (diagnostic.severity) {
            DiagnosticSeverity.ERROR -> "Error"
            DiagnosticSeverity.WARNING -> "Warning"
            DiagnosticSeverity.INFORMATION -> "Info"
            DiagnosticSeverity.HINT -> "Hint"
            else -> "Diagnostic"
        }

        val source = diagnostic.source?.let { " [$it]" } ?: ""
        val code = diagnostic.code?.let { " ($it)" } ?: ""

        return "$prefix$source$code: ${diagnostic.message}"
    }
}

/**
 * Information about a gutter icon.
 */
data class GutterIconInfo(
    /**
     * The icon type.
     */
    val type: GutterIconType,

    /**
     * The icon color.
     */
    val color: Color,

    /**
     * Tooltip text to show on hover.
     */
    val tooltip: String,

    /**
     * Number of diagnostics on this line.
     */
    val diagnosticCount: Int
)

/**
 * Types of gutter icons.
 */
enum class GutterIconType {
    ERROR,
    WARNING,
    INFO,
    HINT
}

/**
 * Colors for diagnostic severity levels.
 *
 * These colors follow common IDE conventions:
 * - Red for errors
 * - Yellow/Orange for warnings
 * - Blue for information
 * - Gray/Green for hints
 */
object DiagnosticColors {
    /**
     * Error color (red).
     */
    val ERROR = Color(0xFFE51400)

    /**
     * Warning color (yellow/orange).
     */
    val WARNING = Color(0xFFDDB100)

    /**
     * Information color (blue).
     */
    val INFORMATION = Color(0xFF3794FF)

    /**
     * Hint color (gray/green).
     */
    val HINT = Color(0xFF75BEFF)

    /**
     * Error background color (light red).
     */
    val ERROR_BACKGROUND = Color(0x33E51400)

    /**
     * Warning background color (light yellow).
     */
    val WARNING_BACKGROUND = Color(0x33DDB100)

    /**
     * Get color for a severity level.
     */
    fun forSeverity(severity: Int): Color {
        return when (severity) {
            DiagnosticSeverity.ERROR -> ERROR
            DiagnosticSeverity.WARNING -> WARNING
            DiagnosticSeverity.INFORMATION -> INFORMATION
            DiagnosticSeverity.HINT -> HINT
            else -> INFORMATION
        }
    }

    /**
     * Get background color for a severity level.
     */
    fun backgroundForSeverity(severity: Int): Color {
        return when (severity) {
            DiagnosticSeverity.ERROR -> ERROR_BACKGROUND
            DiagnosticSeverity.WARNING -> WARNING_BACKGROUND
            else -> Color.Transparent
        }
    }
}
