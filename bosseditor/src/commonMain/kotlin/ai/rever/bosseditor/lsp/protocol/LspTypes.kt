package ai.rever.bosseditor.lsp.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Core LSP types used across multiple protocol messages.
 *
 * Based on LSP 3.17 specification.
 * Reference: https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/
 */

/**
 * Position in a text document expressed as zero-based line and character offset.
 *
 * A position is between two characters like an 'insert' cursor in an editor.
 * Special values like end of line are represented as a position at the end
 * of the line.
 */
@Serializable
data class Position(
    /**
     * Line position in a document (zero-based).
     */
    val line: Int,

    /**
     * Character offset on a line in a document (zero-based).
     *
     * The meaning of this offset is determined by the negotiated
     * `PositionEncodingKind`. If not specified, defaults to UTF-16 code units.
     */
    val character: Int
) {
    companion object {
        val ZERO = Position(0, 0)
    }
}

/**
 * A range in a text document expressed as start and end positions.
 *
 * A range is comparable to a selection in an editor. The end position is exclusive.
 */
@Serializable
data class Range(
    /**
     * The range's start position.
     */
    val start: Position,

    /**
     * The range's end position.
     */
    val end: Position
) {
    companion object {
        val ZERO = Range(Position.ZERO, Position.ZERO)
    }
}

/**
 * Represents a location inside a resource, such as a line inside a text file.
 */
@Serializable
data class Location(
    /**
     * The text document's URI.
     */
    val uri: String,

    /**
     * The location's range.
     */
    val range: Range
)

/**
 * Represents a link between a source and a target location.
 */
@Serializable
data class LocationLink(
    /**
     * Span of the origin of this link.
     * Used as the underlined span for mouse interaction.
     */
    val originSelectionRange: Range? = null,

    /**
     * The target resource identifier of this link.
     */
    val targetUri: String,

    /**
     * The full target range of this link.
     */
    val targetRange: Range,

    /**
     * The range that should be selected and revealed when this link is being followed.
     */
    val targetSelectionRange: Range
)

/**
 * Text document identifier. Used to identify a specific text document.
 */
@Serializable
data class TextDocumentIdentifier(
    /**
     * The text document's URI.
     */
    val uri: String
)

/**
 * Versioned text document identifier.
 * Used to denote a specific version of a text document.
 */
@Serializable
data class VersionedTextDocumentIdentifier(
    /**
     * The text document's URI.
     */
    val uri: String,

    /**
     * The version number of this document.
     * The version number increases after each change, including undo/redo.
     */
    val version: Int
)

/**
 * An item to transfer a text document from the client to the server.
 */
@Serializable
data class TextDocumentItem(
    /**
     * The text document's URI.
     */
    val uri: String,

    /**
     * The text document's language identifier.
     */
    val languageId: String,

    /**
     * The version number of this document (increases after each change).
     */
    val version: Int,

    /**
     * The content of the opened text document.
     */
    val text: String
)

/**
 * A text edit applicable to a text document.
 */
@Serializable
data class TextEdit(
    /**
     * The range of the text document to be manipulated.
     */
    val range: Range,

    /**
     * The string to be inserted. For delete operations use an empty string.
     */
    val newText: String
)

/**
 * Represents a reference to a command.
 */
@Serializable
data class Command(
    /**
     * Title of the command, like `save`.
     */
    val title: String,

    /**
     * The identifier of the actual command handler.
     */
    val command: String,

    /**
     * Arguments that the command handler should be invoked with.
     */
    val arguments: List<JsonElement>? = null
)

/**
 * A workspace edit represents changes to many resources managed in the workspace.
 */
@Serializable
data class WorkspaceEdit(
    /**
     * Holds changes to existing resources.
     * Key is the document URI.
     */
    val changes: Map<String, List<TextEdit>>? = null
)

/**
 * Markup content for documentation.
 */
@Serializable
data class MarkupContent(
    /**
     * The type of the Markup (plaintext or markdown).
     */
    val kind: MarkupKind,

    /**
     * The content itself.
     */
    val value: String
)

/**
 * Describes the content type that a client supports in various result literals.
 */
@Serializable
enum class MarkupKind {
    @SerialName("plaintext")
    PLAINTEXT,

    @SerialName("markdown")
    MARKDOWN
}

/**
 * A parameter literal used in textDocument/signatureHelp requests.
 */
@Serializable
data class ParameterInformation(
    /**
     * The label of this parameter information.
     */
    val label: String,

    /**
     * The human-readable doc-comment of this parameter.
     */
    val documentation: String? = null
)

/**
 * Represents the signature of something callable.
 */
@Serializable
data class SignatureInformation(
    /**
     * The label of this signature. Will be shown in the UI.
     */
    val label: String,

    /**
     * The human-readable doc-comment of this signature.
     */
    val documentation: String? = null,

    /**
     * The parameters of this signature.
     */
    val parameters: List<ParameterInformation>? = null,

    /**
     * The index of the active parameter.
     */
    val activeParameter: Int? = null
)

/**
 * Signature help represents the signature of something callable.
 */
@Serializable
data class SignatureHelp(
    /**
     * One or more signatures.
     */
    val signatures: List<SignatureInformation>,

    /**
     * The active signature.
     */
    val activeSignature: Int? = null,

    /**
     * The active parameter of the active signature.
     */
    val activeParameter: Int? = null
)
