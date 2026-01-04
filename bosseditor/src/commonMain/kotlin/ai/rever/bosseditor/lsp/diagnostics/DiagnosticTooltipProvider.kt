package ai.rever.bosseditor.lsp.diagnostics

import ai.rever.bosseditor.lsp.protocol.Diagnostic
import ai.rever.bosseditor.lsp.protocol.DiagnosticSeverity
import ai.rever.bosseditor.lsp.protocol.DiagnosticTag
import androidx.compose.ui.graphics.Color

/**
 * Provides tooltip content for diagnostics at specific positions.
 *
 * This provider:
 * - Returns formatted tooltip content for hover
 * - Supports multiple diagnostics at one position
 * - Provides rich formatting with severity icons
 * - Handles code actions and quick fixes
 *
 * ## Usage
 * ```kotlin
 * val tooltipProvider = DiagnosticTooltipProvider(diagnosticsProvider)
 *
 * // Check if position has tooltip
 * if (tooltipProvider.hasTooltip(uri, line, character)) {
 *     val content = tooltipProvider.getTooltipContent(uri, line, character)
 *     showTooltip(content)
 * }
 * ```
 */
class DiagnosticTooltipProvider(
    private val diagnosticsProvider: LspDiagnosticsProvider
) {
    /**
     * Check if a position has diagnostic tooltips.
     *
     * @param uri The document URI
     * @param line The line number (0-based)
     * @param character The character offset (0-based)
     * @return true if there are diagnostics at this position
     */
    fun hasTooltip(uri: String, line: Int, character: Int): Boolean {
        return diagnosticsProvider.getDiagnosticsAtPosition(uri, line, character).isNotEmpty()
    }

    /**
     * Get tooltip content for a position.
     *
     * @param uri The document URI
     * @param line The line number (0-based)
     * @param character The character offset (0-based)
     * @return Tooltip content, or null if no diagnostics
     */
    fun getTooltipContent(uri: String, line: Int, character: Int): DiagnosticTooltipContent? {
        val diagnostics = diagnosticsProvider.getDiagnosticsAtPosition(uri, line, character)
        if (diagnostics.isEmpty()) return null

        return DiagnosticTooltipContent(
            entries = diagnostics.map { createTooltipEntry(it) },
            position = TooltipPosition(line, character)
        )
    }

    /**
     * Get tooltip content for a line (all diagnostics on the line).
     *
     * Useful for gutter icon hover.
     *
     * @param uri The document URI
     * @param line The line number (0-based)
     * @return Tooltip content, or null if no diagnostics
     */
    fun getLineTooltipContent(uri: String, line: Int): DiagnosticTooltipContent? {
        val diagnostics = diagnosticsProvider.getDiagnosticsForLine(uri, line)
        if (diagnostics.isEmpty()) return null

        return DiagnosticTooltipContent(
            entries = diagnostics.map { createTooltipEntry(it) },
            position = TooltipPosition(line, 0)
        )
    }

    /**
     * Get a brief summary for a position.
     *
     * Returns a short one-line summary suitable for inline display.
     *
     * @param uri The document URI
     * @param line The line number (0-based)
     * @param character The character offset (0-based)
     * @return Brief summary, or null if no diagnostics
     */
    fun getBriefSummary(uri: String, line: Int, character: Int): String? {
        val diagnostics = diagnosticsProvider.getDiagnosticsAtPosition(uri, line, character)
        if (diagnostics.isEmpty()) return null

        val most = diagnostics.minByOrNull { it.severity ?: DiagnosticSeverity.HINT }
            ?: return null

        return if (diagnostics.size == 1) {
            most.message
        } else {
            "${most.message} (+${diagnostics.size - 1} more)"
        }
    }

    /**
     * Create a tooltip entry from a diagnostic.
     */
    private fun createTooltipEntry(diagnostic: Diagnostic): DiagnosticTooltipEntry {
        val tags = diagnostic.tags ?: emptyList()

        return DiagnosticTooltipEntry(
            severity = diagnostic.severity ?: DiagnosticSeverity.HINT,
            message = diagnostic.message,
            source = diagnostic.source,
            code = diagnostic.code,
            codeDescription = diagnostic.codeDescription?.href,
            isDeprecated = DiagnosticTag.DEPRECATED in tags,
            isUnnecessary = DiagnosticTag.UNNECESSARY in tags,
            relatedInformation = diagnostic.relatedInformation?.map { info ->
                RelatedInformation(
                    uri = info.location.uri,
                    line = info.location.range.start.line,
                    character = info.location.range.start.character,
                    message = info.message
                )
            }
        )
    }
}

/**
 * Content for a diagnostic tooltip.
 */
data class DiagnosticTooltipContent(
    /**
     * Individual diagnostic entries.
     */
    val entries: List<DiagnosticTooltipEntry>,

    /**
     * Position where the tooltip should appear.
     */
    val position: TooltipPosition
) {
    /**
     * Check if this tooltip has multiple entries.
     */
    val hasMultiple: Boolean get() = entries.size > 1

    /**
     * Get the highest severity in this tooltip.
     */
    val maxSeverity: Int
        get() = entries.minOfOrNull { it.severity } ?: DiagnosticSeverity.HINT

    /**
     * Get formatted plain text content.
     */
    fun toPlainText(): String {
        return entries.joinToString("\n\n") { it.toPlainText() }
    }

    /**
     * Get formatted markdown content.
     */
    fun toMarkdown(): String {
        return entries.joinToString("\n\n---\n\n") { it.toMarkdown() }
    }
}

/**
 * A single diagnostic entry in a tooltip.
 */
data class DiagnosticTooltipEntry(
    /**
     * Diagnostic severity.
     */
    val severity: Int,

    /**
     * The diagnostic message.
     */
    val message: String,

    /**
     * Optional source of the diagnostic (e.g., "eslint", "rust-analyzer").
     */
    val source: String?,

    /**
     * Optional diagnostic code.
     */
    val code: String?,

    /**
     * Optional URL for more information about the code.
     */
    val codeDescription: String?,

    /**
     * Whether this is for deprecated code.
     */
    val isDeprecated: Boolean,

    /**
     * Whether this is for unnecessary code.
     */
    val isUnnecessary: Boolean,

    /**
     * Related information from other locations.
     */
    val relatedInformation: List<RelatedInformation>?
) {
    /**
     * Get the severity label.
     */
    val severityLabel: String
        get() = when (severity) {
            DiagnosticSeverity.ERROR -> "Error"
            DiagnosticSeverity.WARNING -> "Warning"
            DiagnosticSeverity.INFORMATION -> "Info"
            DiagnosticSeverity.HINT -> "Hint"
            else -> "Diagnostic"
        }

    /**
     * Get the severity icon character.
     */
    val severityIcon: String
        get() = when (severity) {
            DiagnosticSeverity.ERROR -> "✕"
            DiagnosticSeverity.WARNING -> "⚠"
            DiagnosticSeverity.INFORMATION -> "ℹ"
            DiagnosticSeverity.HINT -> "💡"
            else -> "•"
        }

    /**
     * Get the severity color.
     */
    val severityColor: Color
        get() = DiagnosticColors.forSeverity(severity)

    /**
     * Format as plain text.
     */
    fun toPlainText(): String = buildString {
        // Header line
        append("$severityIcon $severityLabel")
        if (source != null) append(" [$source]")
        if (code != null) append(" ($code)")
        appendLine()

        // Message
        append(message)

        // Tags
        if (isDeprecated) append(" [deprecated]")
        if (isUnnecessary) append(" [unnecessary]")

        // Related information
        relatedInformation?.forEach { info ->
            appendLine()
            append("  → ${info.message}")
            append(" (${info.uri}:${info.line + 1}:${info.character + 1})")
        }
    }

    /**
     * Format as markdown.
     */
    fun toMarkdown(): String = buildString {
        // Header with severity
        append("**$severityLabel**")
        if (source != null) append(" `$source`")
        if (code != null) {
            if (codeDescription != null) {
                append(" [$code]($codeDescription)")
            } else {
                append(" `$code`")
            }
        }
        appendLine()
        appendLine()

        // Message
        append(message)

        // Tags as italic
        if (isDeprecated || isUnnecessary) {
            appendLine()
            appendLine()
            if (isDeprecated) append("*deprecated* ")
            if (isUnnecessary) append("*unnecessary*")
        }

        // Related information
        relatedInformation?.forEach { info ->
            appendLine()
            appendLine()
            append("↳ ${info.message}")
            appendLine()
            append("  `${info.uri}:${info.line + 1}:${info.character + 1}`")
        }
    }
}

/**
 * Position for tooltip display.
 */
data class TooltipPosition(
    /**
     * Line number (0-based).
     */
    val line: Int,

    /**
     * Character offset (0-based).
     */
    val character: Int
)

/**
 * Related diagnostic information from another location.
 */
data class RelatedInformation(
    /**
     * URI of the related location.
     */
    val uri: String,

    /**
     * Line number (0-based).
     */
    val line: Int,

    /**
     * Character offset (0-based).
     */
    val character: Int,

    /**
     * Message describing the relation.
     */
    val message: String
)

/**
 * Extension to get tooltip content for a decoration.
 */
fun DiagnosticDecoration.toTooltipEntry(): DiagnosticTooltipEntry {
    return DiagnosticTooltipEntry(
        severity = severity,
        message = message,
        source = source,
        code = code,
        codeDescription = null,
        isDeprecated = type == DecorationType.STRIKETHROUGH,
        isUnnecessary = type == DecorationType.FADED,
        relatedInformation = null
    )
}
