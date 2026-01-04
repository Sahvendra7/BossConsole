package ai.rever.bosseditor.lsp.diagnostics

import ai.rever.bosseditor.lsp.protocol.Diagnostic
import ai.rever.bosseditor.lsp.protocol.DiagnosticSeverity
import ai.rever.bosseditor.lsp.protocol.PublishDiagnosticsParams
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Provides access to LSP diagnostics for documents.
 *
 * This provider:
 * - Stores diagnostics received from language servers
 * - Organizes diagnostics by document URI and line number
 * - Provides efficient lookup methods for rendering
 * - Emits updates when diagnostics change
 *
 * **Note**: This class uses JVM-specific `synchronized()` for thread safety.
 * While placed in commonMain for code organization, this is desktop-only
 * as BOSS targets only desktop platforms (macOS, Windows, Linux).
 *
 * ## Usage
 * ```kotlin
 * val provider = LspDiagnosticsProvider()
 *
 * // Subscribe to updates
 * provider.diagnosticsUpdates.collect { update ->
 *     refreshEditorDecorations(update.uri)
 * }
 *
 * // Get diagnostics for rendering
 * val lineDiagnostics = provider.getDiagnosticsForLine(uri, lineNumber)
 * val allDiagnostics = provider.getAllDiagnostics(uri)
 * ```
 */
class LspDiagnosticsProvider {

    /**
     * Diagnostics organized by URI.
     * Each URI maps to a list of diagnostics sorted by start position.
     */
    private val diagnosticsByUri = mutableMapOf<String, List<Diagnostic>>()

    /**
     * Diagnostics indexed by line for fast lookup.
     * Key: URI, Value: Map of line number to diagnostics on that line.
     */
    private val diagnosticsByLine = mutableMapOf<String, Map<Int, List<Diagnostic>>>()

    /**
     * Lock for thread-safe access.
     */
    private val lock = Any()

    /**
     * Flow of diagnostics updates.
     */
    private val _diagnosticsUpdates = MutableSharedFlow<DiagnosticsUpdate>(extraBufferCapacity = 16)
    val diagnosticsUpdates: Flow<DiagnosticsUpdate> = _diagnosticsUpdates.asSharedFlow()

    /**
     * Update diagnostics for a document.
     *
     * Called when receiving textDocument/publishDiagnostics notification.
     *
     * @param params The publish diagnostics parameters from the server
     */
    fun updateDiagnostics(params: PublishDiagnosticsParams) {
        updateDiagnostics(params.uri, params.diagnostics, params.version)
    }

    /**
     * Update diagnostics for a document.
     *
     * @param uri The document URI
     * @param diagnostics The new diagnostics list
     * @param version Optional document version
     */
    fun updateDiagnostics(uri: String, diagnostics: List<Diagnostic>, version: Int? = null) {
        val sortedDiagnostics = diagnostics.sortedWith(
            compareBy(
                { it.range.start.line },
                { it.range.start.character },
                { it.severity ?: DiagnosticSeverity.HINT }
            )
        )

        val byLine = sortedDiagnostics
            .groupBy { it.range.start.line }
            .mapValues { (_, diags) -> diags.sortedBy { it.severity ?: DiagnosticSeverity.HINT } }

        synchronized(lock) {
            diagnosticsByUri[uri] = sortedDiagnostics
            diagnosticsByLine[uri] = byLine
        }

        _diagnosticsUpdates.tryEmit(
            DiagnosticsUpdate(
                uri = uri,
                diagnostics = sortedDiagnostics,
                version = version
            )
        )
    }

    /**
     * Get all diagnostics for a document.
     *
     * @param uri The document URI
     * @return List of diagnostics, sorted by position
     */
    fun getAllDiagnostics(uri: String): List<Diagnostic> {
        return synchronized(lock) {
            diagnosticsByUri[uri] ?: emptyList()
        }
    }

    /**
     * Get diagnostics for a specific line.
     *
     * @param uri The document URI
     * @param line The line number (0-based)
     * @return List of diagnostics on this line, sorted by severity (errors first)
     */
    fun getDiagnosticsForLine(uri: String, line: Int): List<Diagnostic> {
        return synchronized(lock) {
            diagnosticsByLine[uri]?.get(line) ?: emptyList()
        }
    }

    /**
     * Get diagnostics that overlap with a position.
     *
     * @param uri The document URI
     * @param line The line number (0-based)
     * @param character The character offset (0-based)
     * @return List of diagnostics that contain this position
     */
    fun getDiagnosticsAtPosition(uri: String, line: Int, character: Int): List<Diagnostic> {
        return getDiagnosticsForLine(uri, line).filter { diagnostic ->
            val range = diagnostic.range
            if (range.start.line == range.end.line) {
                // Single line diagnostic
                character >= range.start.character && character <= range.end.character
            } else if (line == range.start.line) {
                character >= range.start.character
            } else if (line == range.end.line) {
                character <= range.end.character
            } else {
                // Multi-line diagnostic, position is in the middle
                line > range.start.line && line < range.end.line
            }
        }
    }

    /**
     * Get diagnostics in a range of lines.
     *
     * @param uri The document URI
     * @param startLine Start line (inclusive, 0-based)
     * @param endLine End line (inclusive, 0-based)
     * @return Map of line number to diagnostics
     */
    fun getDiagnosticsInRange(uri: String, startLine: Int, endLine: Int): Map<Int, List<Diagnostic>> {
        return synchronized(lock) {
            diagnosticsByLine[uri]
                ?.filterKeys { it in startLine..endLine }
                ?: emptyMap()
        }
    }

    /**
     * Get the most severe diagnostic on a line.
     *
     * @param uri The document URI
     * @param line The line number (0-based)
     * @return The most severe diagnostic, or null if none
     */
    fun getMostSevereDiagnostic(uri: String, line: Int): Diagnostic? {
        return getDiagnosticsForLine(uri, line).minByOrNull { it.severity ?: DiagnosticSeverity.HINT }
    }

    /**
     * Get the severity level for a line (for gutter icon).
     *
     * @param uri The document URI
     * @param line The line number (0-based)
     * @return The highest severity on this line, or null if no diagnostics
     */
    fun getLineSeverity(uri: String, line: Int): Int? {
        return getDiagnosticsForLine(uri, line)
            .mapNotNull { it.severity }
            .minOrNull()
    }

    /**
     * Check if a line has any diagnostics.
     *
     * @param uri The document URI
     * @param line The line number (0-based)
     * @return true if there are diagnostics on this line
     */
    fun hasLineDiagnostics(uri: String, line: Int): Boolean {
        return synchronized(lock) {
            diagnosticsByLine[uri]?.containsKey(line) == true
        }
    }

    /**
     * Get diagnostic counts by severity for a document.
     *
     * @param uri The document URI
     * @return Map of severity to count
     */
    fun getDiagnosticCounts(uri: String): DiagnosticCounts {
        val diagnostics = getAllDiagnostics(uri)
        return DiagnosticCounts(
            errors = diagnostics.count { it.severity == DiagnosticSeverity.ERROR },
            warnings = diagnostics.count { it.severity == DiagnosticSeverity.WARNING },
            information = diagnostics.count { it.severity == DiagnosticSeverity.INFORMATION },
            hints = diagnostics.count { it.severity == DiagnosticSeverity.HINT }
        )
    }

    /**
     * Clear diagnostics for a document.
     *
     * @param uri The document URI
     */
    fun clearDiagnostics(uri: String) {
        synchronized(lock) {
            diagnosticsByUri.remove(uri)
            diagnosticsByLine.remove(uri)
        }

        _diagnosticsUpdates.tryEmit(
            DiagnosticsUpdate(
                uri = uri,
                diagnostics = emptyList(),
                version = null
            )
        )
    }

    /**
     * Clear all diagnostics.
     */
    fun clearAll() {
        val uris = synchronized(lock) {
            val keys = diagnosticsByUri.keys.toList()
            diagnosticsByUri.clear()
            diagnosticsByLine.clear()
            keys
        }

        uris.forEach { uri ->
            _diagnosticsUpdates.tryEmit(
                DiagnosticsUpdate(
                    uri = uri,
                    diagnostics = emptyList(),
                    version = null
                )
            )
        }
    }

    /**
     * Get all URIs that have diagnostics.
     *
     * @return Set of URIs with diagnostics
     */
    fun getUrisWithDiagnostics(): Set<String> {
        return synchronized(lock) {
            diagnosticsByUri.keys.toSet()
        }
    }
}

/**
 * Represents a diagnostics update event.
 */
data class DiagnosticsUpdate(
    /**
     * The document URI.
     */
    val uri: String,

    /**
     * The updated diagnostics list.
     */
    val diagnostics: List<Diagnostic>,

    /**
     * Optional document version.
     */
    val version: Int?
)

/**
 * Counts of diagnostics by severity.
 */
data class DiagnosticCounts(
    val errors: Int,
    val warnings: Int,
    val information: Int,
    val hints: Int
) {
    val total: Int get() = errors + warnings + information + hints
    val hasErrors: Boolean get() = errors > 0
    val hasWarnings: Boolean get() = warnings > 0
}
