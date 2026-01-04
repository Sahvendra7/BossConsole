package ai.rever.bosseditor.lsp.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * LSP Navigation Protocol Types
 *
 * These types follow the Language Server Protocol specification for
 * navigation features including:
 * - Go to Definition (textDocument/definition)
 * - Go to Type Definition (textDocument/typeDefinition)
 * - Go to Implementation (textDocument/implementation)
 * - Find References (textDocument/references)
 * - Hover (textDocument/hover)
 * - Declaration (textDocument/declaration)
 */

// ============================================================================
// Definition
// ============================================================================

/**
 * Parameters for textDocument/definition request.
 *
 * The go to definition request is sent from the client to the server to resolve
 * the definition location of a symbol at a given text document position.
 */
@Serializable
data class DefinitionParams(
    /**
     * The text document.
     */
    val textDocument: TextDocumentIdentifier,

    /**
     * The position inside the text document.
     */
    val position: Position
)

/**
 * Parameters for textDocument/typeDefinition request.
 *
 * The go to type definition request is sent from the client to the server
 * to resolve the type definition location of a symbol at a given text document position.
 */
@Serializable
data class TypeDefinitionParams(
    /**
     * The text document.
     */
    val textDocument: TextDocumentIdentifier,

    /**
     * The position inside the text document.
     */
    val position: Position
)

/**
 * Parameters for textDocument/implementation request.
 *
 * The go to implementation request is sent from the client to the server
 * to resolve the implementation location of a symbol at a given text document position.
 */
@Serializable
data class ImplementationParams(
    /**
     * The text document.
     */
    val textDocument: TextDocumentIdentifier,

    /**
     * The position inside the text document.
     */
    val position: Position
)

/**
 * Parameters for textDocument/declaration request.
 *
 * The go to declaration request is sent from the client to the server
 * to resolve the declaration location of a symbol at a given text document position.
 */
@Serializable
data class DeclarationParams(
    /**
     * The text document.
     */
    val textDocument: TextDocumentIdentifier,

    /**
     * The position inside the text document.
     */
    val position: Position
)

// ============================================================================
// References
// ============================================================================

/**
 * Parameters for textDocument/references request.
 *
 * The references request is sent from the client to the server to resolve
 * project-wide references for the symbol denoted by the given text document position.
 */
@Serializable
data class ReferenceParams(
    /**
     * The text document.
     */
    val textDocument: TextDocumentIdentifier,

    /**
     * The position inside the text document.
     */
    val position: Position,

    /**
     * Context carrying additional information.
     */
    val context: ReferenceContext
)

/**
 * Context for reference requests.
 */
@Serializable
data class ReferenceContext(
    /**
     * Include the declaration of the current symbol.
     */
    val includeDeclaration: Boolean
)

// ============================================================================
// Hover
// ============================================================================

/**
 * Parameters for textDocument/hover request.
 *
 * The hover request is sent from the client to the server to request hover
 * information at a given text document position.
 */
@Serializable
data class HoverParams(
    /**
     * The text document.
     */
    val textDocument: TextDocumentIdentifier,

    /**
     * The position inside the text document.
     */
    val position: Position
)

/**
 * The result of a hover request.
 */
@Serializable
data class Hover(
    /**
     * The hover's content.
     */
    val contents: HoverContents,

    /**
     * An optional range is a range inside a text document that is used
     * to visualize a hover, e.g. by changing the background color.
     */
    val range: Range? = null
)

/**
 * Hover contents can be a MarkupContent, a MarkedString, or an array of MarkedStrings.
 *
 * Due to kotlinx.serialization limitations with union types, we represent this
 * as a sealed class hierarchy.
 */
@Serializable
sealed class HoverContents {
    /**
     * Markup content (preferred format).
     */
    @Serializable
    data class Markup(val content: MarkupContent) : HoverContents()

    /**
     * Plain string content.
     */
    @Serializable
    data class PlainString(val value: String) : HoverContents()

    /**
     * Multiple strings (legacy format).
     */
    @Serializable
    data class MultipleStrings(val values: List<String>) : HoverContents()
}

/**
 * Simplified hover content that can be directly deserialized from various formats.
 *
 * Use this when you want simpler handling of hover responses.
 */
data class SimpleHoverContent(
    /**
     * The content as markdown or plain text.
     */
    val text: String,

    /**
     * Whether the content is markdown.
     */
    val isMarkdown: Boolean,

    /**
     * The optional highlight range.
     */
    val range: Range? = null
)

// ============================================================================
// Document Symbol
// ============================================================================

/**
 * Parameters for textDocument/documentSymbol request.
 *
 * The document symbol request is sent from the client to the server
 * to return a flat list of all symbols found in a given text document.
 */
@Serializable
data class DocumentSymbolParams(
    /**
     * The text document.
     */
    val textDocument: TextDocumentIdentifier
)

/**
 * Represents information about programming constructs like variables, classes, interfaces etc.
 */
@Serializable
data class SymbolInformation(
    /**
     * The name of this symbol.
     */
    val name: String,

    /**
     * The kind of this symbol.
     */
    val kind: Int,

    /**
     * Tags for this symbol.
     */
    val tags: List<Int>? = null,

    /**
     * Indicates if this symbol is deprecated.
     */
    val deprecated: Boolean? = null,

    /**
     * The location of this symbol. The location's range is used by a tool
     * to reveal the location in the editor.
     */
    val location: Location,

    /**
     * The name of the symbol containing this symbol.
     */
    val containerName: String? = null
)

/**
 * Represents programming constructs like variables, classes, interfaces etc.
 * that appear in a document. Document symbols can be hierarchical.
 */
@Serializable
data class DocumentSymbol(
    /**
     * The name of this symbol.
     */
    val name: String,

    /**
     * More detail for this symbol, e.g. the signature of a function.
     */
    val detail: String? = null,

    /**
     * The kind of this symbol.
     */
    val kind: Int,

    /**
     * Tags for this symbol.
     */
    val tags: List<Int>? = null,

    /**
     * Indicates if this symbol is deprecated.
     */
    val deprecated: Boolean? = null,

    /**
     * The range enclosing this symbol not including leading/trailing whitespace
     * but everything else like comments.
     */
    val range: Range,

    /**
     * The range that should be selected and revealed when this symbol is being picked.
     */
    val selectionRange: Range,

    /**
     * Children of this symbol, e.g. properties of a class.
     */
    val children: List<DocumentSymbol>? = null
)

/**
 * A symbol kind.
 */
object SymbolKind {
    const val FILE = 1
    const val MODULE = 2
    const val NAMESPACE = 3
    const val PACKAGE = 4
    const val CLASS = 5
    const val METHOD = 6
    const val PROPERTY = 7
    const val FIELD = 8
    const val CONSTRUCTOR = 9
    const val ENUM = 10
    const val INTERFACE = 11
    const val FUNCTION = 12
    const val VARIABLE = 13
    const val CONSTANT = 14
    const val STRING = 15
    const val NUMBER = 16
    const val BOOLEAN = 17
    const val ARRAY = 18
    const val OBJECT = 19
    const val KEY = 20
    const val NULL = 21
    const val ENUM_MEMBER = 22
    const val STRUCT = 23
    const val EVENT = 24
    const val OPERATOR = 25
    const val TYPE_PARAMETER = 26
}

/**
 * Symbol tags.
 */
object SymbolTag {
    /**
     * Render a symbol as obsolete, usually using a strike-out.
     */
    const val DEPRECATED = 1
}

// ============================================================================
// Workspace Symbol
// ============================================================================

/**
 * Parameters for workspace/symbol request.
 */
@Serializable
data class WorkspaceSymbolParams(
    /**
     * A query string to filter symbols by.
     */
    val query: String
)

// ============================================================================
// Rename
// ============================================================================

/**
 * Parameters for textDocument/rename request.
 */
@Serializable
data class RenameParams(
    /**
     * The text document.
     */
    val textDocument: TextDocumentIdentifier,

    /**
     * The position inside the text document.
     */
    val position: Position,

    /**
     * The new name of the symbol.
     */
    val newName: String
)

/**
 * Parameters for textDocument/prepareRename request.
 */
@Serializable
data class PrepareRenameParams(
    /**
     * The text document.
     */
    val textDocument: TextDocumentIdentifier,

    /**
     * The position inside the text document.
     */
    val position: Position
)

/**
 * Result of a prepare rename request.
 */
@Serializable
data class PrepareRenameResult(
    /**
     * The range of the string to rename.
     */
    val range: Range,

    /**
     * A placeholder text of the string content to be renamed.
     */
    val placeholder: String
)
