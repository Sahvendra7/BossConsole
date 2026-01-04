package ai.rever.bosseditor.lsp.protocol

import kotlinx.serialization.Serializable

/**
 * LSP Diagnostics types.
 *
 * Diagnostics are used to report errors, warnings, and other information
 * about the code to the client.
 *
 * Reference: https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_publishDiagnostics
 */

/**
 * Represents a diagnostic, such as a compiler error or warning.
 */
@Serializable
data class Diagnostic(
    /**
     * The range at which the message applies.
     */
    val range: Range,

    /**
     * The diagnostic's severity. Can be omitted. If omitted it's up to
     * the client to interpret diagnostics as error, warning, info or hint.
     */
    val severity: Int? = null,

    /**
     * The diagnostic's code, which might appear in the user interface.
     */
    val code: String? = null,

    /**
     * An optional property to describe the error code.
     */
    val codeDescription: CodeDescription? = null,

    /**
     * A human-readable string describing the source of this diagnostic,
     * e.g. 'typescript' or 'super lint'.
     */
    val source: String? = null,

    /**
     * The diagnostic's message.
     */
    val message: String,

    /**
     * Additional metadata about the diagnostic.
     */
    val tags: List<Int>? = null,

    /**
     * An array of related diagnostic information, e.g. when symbol-names
     * within a scope collide.
     */
    val relatedInformation: List<DiagnosticRelatedInformation>? = null,

    /**
     * A data entry field that is preserved between a
     * `textDocument/publishDiagnostics` notification and
     * `textDocument/codeAction` request.
     */
    val data: String? = null
)

/**
 * Structure to capture a description for an error code.
 */
@Serializable
data class CodeDescription(
    /**
     * An URI to open with more information about the diagnostic error.
     */
    val href: String
)

/**
 * Represents a related message and source code location for a diagnostic.
 */
@Serializable
data class DiagnosticRelatedInformation(
    /**
     * The location of this related diagnostic information.
     */
    val location: Location,

    /**
     * The message of this related diagnostic information.
     */
    val message: String
)

/**
 * The diagnostic's severity.
 */
object DiagnosticSeverity {
    /**
     * Reports an error.
     */
    const val ERROR = 1

    /**
     * Reports a warning.
     */
    const val WARNING = 2

    /**
     * Reports an information.
     */
    const val INFORMATION = 3

    /**
     * Reports a hint.
     */
    const val HINT = 4
}

/**
 * The diagnostic tags.
 */
object DiagnosticTag {
    /**
     * Unused or unnecessary code.
     * Clients are allowed to render diagnostics with this tag faded out.
     */
    const val UNNECESSARY = 1

    /**
     * Deprecated or obsolete code.
     * Clients are allowed to render diagnostics with this tag strike through.
     */
    const val DEPRECATED = 2
}

/**
 * Parameters for textDocument/publishDiagnostics notification.
 *
 * Sent from the server to the client to signal results of validation runs.
 */
@Serializable
data class PublishDiagnosticsParams(
    /**
     * The URI for which diagnostic information is reported.
     */
    val uri: String,

    /**
     * Optional the version number of the document the diagnostics are
     * published for.
     */
    val version: Int? = null,

    /**
     * An array of diagnostic information items.
     */
    val diagnostics: List<Diagnostic>
)

/**
 * LSP method name for publishing diagnostics.
 */
object DiagnosticsMethods {
    const val PUBLISH_DIAGNOSTICS = "textDocument/publishDiagnostics"
}
