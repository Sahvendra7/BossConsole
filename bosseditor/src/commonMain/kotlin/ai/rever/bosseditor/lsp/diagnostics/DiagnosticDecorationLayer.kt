package ai.rever.bosseditor.lsp.diagnostics

import ai.rever.bosseditor.lsp.protocol.Diagnostic
import ai.rever.bosseditor.lsp.protocol.DiagnosticSeverity
import ai.rever.bosseditor.lsp.protocol.DiagnosticTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextDecoration

/**
 * Provides text decorations (squiggly underlines, strikethrough) for diagnostics.
 *
 * This layer computes visual decorations for diagnostics that can be applied
 * to text in the editor. Decorations include:
 * - Squiggly/wavy underlines for errors and warnings
 * - Strikethrough for deprecated code
 * - Faded text for unused code
 *
 * ## Usage
 * ```kotlin
 * val decorationLayer = DiagnosticDecorationLayer(diagnosticsProvider)
 *
 * // Get decorations for a line
 * val decorations = decorationLayer.getLineDecorations(uri, lineNumber)
 *
 * // Apply to text
 * decorations.forEach { decoration ->
 *     applyDecoration(decoration)
 * }
 * ```
 */
class DiagnosticDecorationLayer(
    private val diagnosticsProvider: LspDiagnosticsProvider
) {
    /**
     * Get text decorations for a line.
     *
     * @param uri The document URI
     * @param line The line number (0-based)
     * @return List of decorations to apply to the line
     */
    fun getLineDecorations(uri: String, line: Int): List<DiagnosticDecoration> {
        val diagnostics = diagnosticsProvider.getDiagnosticsForLine(uri, line)
        return diagnostics.map { diagnostic ->
            createDecoration(diagnostic, line)
        }
    }

    /**
     * Get text decorations for a range of lines.
     *
     * @param uri The document URI
     * @param startLine Start line (inclusive, 0-based)
     * @param endLine End line (inclusive, 0-based)
     * @return Map of line number to decorations
     */
    fun getDecorationsInRange(
        uri: String,
        startLine: Int,
        endLine: Int
    ): Map<Int, List<DiagnosticDecoration>> {
        val diagnosticsByLine = diagnosticsProvider.getDiagnosticsInRange(uri, startLine, endLine)

        return diagnosticsByLine.mapValues { (line, diagnostics) ->
            diagnostics.map { diagnostic ->
                createDecoration(diagnostic, line)
            }
        }
    }

    /**
     * Create a decoration from a diagnostic.
     */
    private fun createDecoration(diagnostic: Diagnostic, lineNumber: Int): DiagnosticDecoration {
        val range = diagnostic.range
        val tags = diagnostic.tags ?: emptyList()

        // Determine decoration type based on severity and tags
        val decorationType = when {
            DiagnosticTag.DEPRECATED in tags -> DecorationType.STRIKETHROUGH
            DiagnosticTag.UNNECESSARY in tags -> DecorationType.FADED
            diagnostic.severity == DiagnosticSeverity.ERROR -> DecorationType.SQUIGGLY_ERROR
            diagnostic.severity == DiagnosticSeverity.WARNING -> DecorationType.SQUIGGLY_WARNING
            diagnostic.severity == DiagnosticSeverity.INFORMATION -> DecorationType.SQUIGGLY_INFO
            diagnostic.severity == DiagnosticSeverity.HINT -> DecorationType.SQUIGGLY_HINT
            else -> DecorationType.SQUIGGLY_INFO
        }

        // Calculate character range for this line
        val (startChar, endChar) = if (range.start.line == range.end.line) {
            // Single line diagnostic
            range.start.character to range.end.character
        } else if (lineNumber == range.start.line) {
            // First line of multi-line diagnostic
            range.start.character to Int.MAX_VALUE
        } else if (lineNumber == range.end.line) {
            // Last line of multi-line diagnostic
            0 to range.end.character
        } else {
            // Middle line of multi-line diagnostic
            0 to Int.MAX_VALUE
        }

        return DiagnosticDecoration(
            startOffset = startChar,
            endOffset = endChar,
            type = decorationType,
            message = diagnostic.message,
            severity = diagnostic.severity ?: DiagnosticSeverity.HINT,
            source = diagnostic.source,
            code = diagnostic.code
        )
    }

    /**
     * Get a SpanStyle for a decoration type.
     *
     * This can be used with Compose Text to apply visual styling.
     *
     * @param decoration The decoration to convert
     * @return SpanStyle for the decoration
     */
    fun getSpanStyle(decoration: DiagnosticDecoration): SpanStyle {
        return when (decoration.type) {
            DecorationType.SQUIGGLY_ERROR -> SpanStyle(
                textDecoration = TextDecoration.Underline,
                color = DiagnosticColors.ERROR
            )
            DecorationType.SQUIGGLY_WARNING -> SpanStyle(
                textDecoration = TextDecoration.Underline,
                color = DiagnosticColors.WARNING
            )
            DecorationType.SQUIGGLY_INFO -> SpanStyle(
                textDecoration = TextDecoration.Underline,
                color = DiagnosticColors.INFORMATION
            )
            DecorationType.SQUIGGLY_HINT -> SpanStyle(
                textDecoration = TextDecoration.Underline,
                color = DiagnosticColors.HINT
            )
            DecorationType.STRIKETHROUGH -> SpanStyle(
                textDecoration = TextDecoration.LineThrough,
                color = Color.Gray
            )
            DecorationType.FADED -> SpanStyle(
                color = Color.Gray.copy(alpha = 0.6f)
            )
        }
    }

    /**
     * Check if a line has any decorations.
     *
     * @param uri The document URI
     * @param line The line number (0-based)
     * @return true if the line has diagnostic decorations
     */
    fun hasDecorations(uri: String, line: Int): Boolean {
        return diagnosticsProvider.hasLineDiagnostics(uri, line)
    }
}

/**
 * Represents a text decoration for a diagnostic.
 */
data class DiagnosticDecoration(
    /**
     * Start character offset within the line (0-based).
     */
    val startOffset: Int,

    /**
     * End character offset within the line (exclusive, 0-based).
     * Use Int.MAX_VALUE to extend to end of line.
     */
    val endOffset: Int,

    /**
     * The type of decoration to render.
     */
    val type: DecorationType,

    /**
     * The diagnostic message.
     */
    val message: String,

    /**
     * The diagnostic severity.
     */
    val severity: Int,

    /**
     * Optional source of the diagnostic.
     */
    val source: String? = null,

    /**
     * Optional diagnostic code.
     */
    val code: String? = null
) {
    /**
     * Get the length of this decoration.
     * Returns -1 if the decoration extends to end of line.
     */
    val length: Int
        get() = if (endOffset == Int.MAX_VALUE) -1 else endOffset - startOffset

    /**
     * Check if this decoration overlaps with a character offset.
     */
    fun contains(offset: Int): Boolean {
        return offset >= startOffset && (endOffset == Int.MAX_VALUE || offset < endOffset)
    }

    /**
     * Check if this decoration overlaps with a range.
     */
    fun overlaps(start: Int, end: Int): Boolean {
        return startOffset < end && (endOffset == Int.MAX_VALUE || endOffset > start)
    }
}

/**
 * Types of diagnostic decorations.
 */
enum class DecorationType {
    /**
     * Red squiggly underline for errors.
     */
    SQUIGGLY_ERROR,

    /**
     * Yellow squiggly underline for warnings.
     */
    SQUIGGLY_WARNING,

    /**
     * Blue underline for information.
     */
    SQUIGGLY_INFO,

    /**
     * Light underline for hints.
     */
    SQUIGGLY_HINT,

    /**
     * Strikethrough for deprecated code.
     */
    STRIKETHROUGH,

    /**
     * Faded/dimmed text for unused code.
     */
    FADED
}

/**
 * Parameters for rendering a squiggly line.
 *
 * Used by rendering code to draw wavy underlines.
 */
data class SquigglyLineParams(
    /**
     * X coordinate to start drawing.
     */
    val startX: Float,

    /**
     * X coordinate to end drawing.
     */
    val endX: Float,

    /**
     * Y coordinate (baseline of text).
     */
    val y: Float,

    /**
     * Color of the squiggly line.
     */
    val color: Color,

    /**
     * Wave amplitude (how tall the waves are).
     */
    val amplitude: Float = 2f,

    /**
     * Wave period (how wide each wave is).
     */
    val period: Float = 4f,

    /**
     * Line thickness.
     */
    val strokeWidth: Float = 1f
)

/**
 * Extension to get squiggly line color from decoration type.
 */
fun DecorationType.toColor(): Color {
    return when (this) {
        DecorationType.SQUIGGLY_ERROR -> DiagnosticColors.ERROR
        DecorationType.SQUIGGLY_WARNING -> DiagnosticColors.WARNING
        DecorationType.SQUIGGLY_INFO -> DiagnosticColors.INFORMATION
        DecorationType.SQUIGGLY_HINT -> DiagnosticColors.HINT
        DecorationType.STRIKETHROUGH -> Color.Gray
        DecorationType.FADED -> Color.Gray.copy(alpha = 0.6f)
    }
}
