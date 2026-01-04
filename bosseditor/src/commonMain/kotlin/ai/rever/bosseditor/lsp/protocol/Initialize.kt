package ai.rever.bosseditor.lsp.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * LSP Initialize request/response types.
 *
 * The initialize request is sent as the first request from the client to the server.
 * Until the server has responded with an InitializeResult, the client must not send
 * any additional requests or notifications.
 */

/**
 * Initialize request parameters.
 */
@Serializable
data class InitializeParams(
    /**
     * The process Id of the parent process that started the server.
     * Is null if the process has not been started by another process.
     */
    val processId: Int?,

    /**
     * Information about the client.
     */
    val clientInfo: ClientInfo? = null,

    /**
     * The locale the client is currently showing the user interface in.
     */
    val locale: String? = null,

    /**
     * The rootPath of the workspace. Deprecated in favor of rootUri.
     */
    @Deprecated("Use rootUri instead")
    val rootPath: String? = null,

    /**
     * The rootUri of the workspace. Is null if no folder is open.
     */
    val rootUri: String?,

    /**
     * User provided initialization options.
     */
    val initializationOptions: JsonElement? = null,

    /**
     * The capabilities provided by the client (editor or tool).
     */
    val capabilities: ClientCapabilities,

    /**
     * The initial trace setting. If omitted trace is disabled ('off').
     */
    val trace: TraceValue? = null,

    /**
     * The workspace folders configured in the client when the server starts.
     */
    val workspaceFolders: List<WorkspaceFolder>? = null
)

/**
 * Information about the client.
 */
@Serializable
data class ClientInfo(
    /**
     * The name of the client as defined by the client.
     */
    val name: String,

    /**
     * The client's version as defined by the client.
     */
    val version: String? = null
)

/**
 * Trace value for logging.
 */
@Serializable
enum class TraceValue {
    @SerialName("off")
    OFF,

    @SerialName("messages")
    MESSAGES,

    @SerialName("verbose")
    VERBOSE
}

/**
 * A workspace folder.
 */
@Serializable
data class WorkspaceFolder(
    /**
     * The associated URI for this workspace folder.
     */
    val uri: String,

    /**
     * The name of the workspace folder.
     */
    val name: String
)

/**
 * Client capabilities.
 */
@Serializable
data class ClientCapabilities(
    /**
     * Workspace specific client capabilities.
     */
    val workspace: WorkspaceClientCapabilities? = null,

    /**
     * Text document specific client capabilities.
     */
    val textDocument: TextDocumentClientCapabilities? = null,

    /**
     * General client capabilities.
     */
    val general: GeneralClientCapabilities? = null
)

/**
 * Workspace specific client capabilities.
 */
@Serializable
data class WorkspaceClientCapabilities(
    /**
     * The client supports applying batch edits to the workspace.
     */
    val applyEdit: Boolean? = null,

    /**
     * Capabilities specific to WorkspaceEdit.
     */
    val workspaceEdit: WorkspaceEditClientCapabilities? = null,

    /**
     * Capabilities specific to workspace/didChangeConfiguration notification.
     */
    val didChangeConfiguration: DidChangeConfigurationClientCapabilities? = null,

    /**
     * Capabilities specific to workspace/didChangeWatchedFiles notification.
     */
    val didChangeWatchedFiles: DidChangeWatchedFilesClientCapabilities? = null,

    /**
     * Capabilities specific to workspace/symbol request.
     */
    val symbol: WorkspaceSymbolClientCapabilities? = null,

    /**
     * Capabilities specific to workspace/executeCommand request.
     */
    val executeCommand: ExecuteCommandClientCapabilities? = null,

    /**
     * The client has support for workspace folders.
     */
    val workspaceFolders: Boolean? = null,

    /**
     * The client supports workspace/configuration requests.
     */
    val configuration: Boolean? = null
)

@Serializable
data class WorkspaceEditClientCapabilities(
    val documentChanges: Boolean? = null
)

@Serializable
data class DidChangeConfigurationClientCapabilities(
    val dynamicRegistration: Boolean? = null
)

@Serializable
data class DidChangeWatchedFilesClientCapabilities(
    val dynamicRegistration: Boolean? = null
)

@Serializable
data class WorkspaceSymbolClientCapabilities(
    val dynamicRegistration: Boolean? = null
)

@Serializable
data class ExecuteCommandClientCapabilities(
    val dynamicRegistration: Boolean? = null
)

/**
 * Text document specific client capabilities.
 */
@Serializable
data class TextDocumentClientCapabilities(
    /**
     * Capabilities specific to textDocument/synchronization.
     */
    val synchronization: TextDocumentSyncClientCapabilities? = null,

    /**
     * Capabilities specific to textDocument/completion.
     */
    val completion: CompletionClientCapabilities? = null,

    /**
     * Capabilities specific to textDocument/hover.
     */
    val hover: HoverClientCapabilities? = null,

    /**
     * Capabilities specific to textDocument/signatureHelp.
     */
    val signatureHelp: SignatureHelpClientCapabilities? = null,

    /**
     * Capabilities specific to textDocument/definition.
     */
    val definition: DefinitionClientCapabilities? = null,

    /**
     * Capabilities specific to textDocument/references.
     */
    val references: ReferenceClientCapabilities? = null,

    /**
     * Capabilities specific to textDocument/documentHighlight.
     */
    val documentHighlight: DocumentHighlightClientCapabilities? = null,

    /**
     * Capabilities specific to textDocument/documentSymbol.
     */
    val documentSymbol: DocumentSymbolClientCapabilities? = null,

    /**
     * Capabilities specific to textDocument/codeAction.
     */
    val codeAction: CodeActionClientCapabilities? = null,

    /**
     * Capabilities specific to textDocument/formatting.
     */
    val formatting: DocumentFormattingClientCapabilities? = null,

    /**
     * Capabilities specific to textDocument/rangeFormatting.
     */
    val rangeFormatting: DocumentRangeFormattingClientCapabilities? = null,

    /**
     * Capabilities specific to textDocument/rename.
     */
    val rename: RenameClientCapabilities? = null,

    /**
     * Capabilities specific to textDocument/publishDiagnostics.
     */
    val publishDiagnostics: PublishDiagnosticsClientCapabilities? = null,

    /**
     * Capabilities specific to textDocument/semanticTokens.
     */
    val semanticTokens: SemanticTokensClientCapabilities? = null
)

@Serializable
data class TextDocumentSyncClientCapabilities(
    val dynamicRegistration: Boolean? = null,
    val willSave: Boolean? = null,
    val willSaveWaitUntil: Boolean? = null,
    val didSave: Boolean? = null
)

@Serializable
data class CompletionClientCapabilities(
    val dynamicRegistration: Boolean? = null,
    val completionItem: CompletionItemClientCapabilities? = null,
    val contextSupport: Boolean? = null
)

@Serializable
data class CompletionItemClientCapabilities(
    val snippetSupport: Boolean? = null,
    val commitCharactersSupport: Boolean? = null,
    val documentationFormat: List<MarkupKind>? = null,
    val deprecatedSupport: Boolean? = null,
    val preselectSupport: Boolean? = null
)

@Serializable
data class HoverClientCapabilities(
    val dynamicRegistration: Boolean? = null,
    val contentFormat: List<MarkupKind>? = null
)

@Serializable
data class SignatureHelpClientCapabilities(
    val dynamicRegistration: Boolean? = null,
    val signatureInformation: SignatureInformationClientCapabilities? = null,
    val contextSupport: Boolean? = null
)

@Serializable
data class SignatureInformationClientCapabilities(
    val documentationFormat: List<MarkupKind>? = null,
    val parameterInformation: ParameterInformationClientCapabilities? = null
)

@Serializable
data class ParameterInformationClientCapabilities(
    val labelOffsetSupport: Boolean? = null
)

@Serializable
data class DefinitionClientCapabilities(
    val dynamicRegistration: Boolean? = null,
    val linkSupport: Boolean? = null
)

@Serializable
data class ReferenceClientCapabilities(
    val dynamicRegistration: Boolean? = null
)

@Serializable
data class DocumentHighlightClientCapabilities(
    val dynamicRegistration: Boolean? = null
)

@Serializable
data class DocumentSymbolClientCapabilities(
    val dynamicRegistration: Boolean? = null,
    val hierarchicalDocumentSymbolSupport: Boolean? = null
)

@Serializable
data class CodeActionClientCapabilities(
    val dynamicRegistration: Boolean? = null,
    val codeActionLiteralSupport: CodeActionLiteralSupportCapabilities? = null
)

@Serializable
data class CodeActionLiteralSupportCapabilities(
    val codeActionKind: CodeActionKindCapabilities? = null
)

@Serializable
data class CodeActionKindCapabilities(
    val valueSet: List<String>? = null
)

@Serializable
data class DocumentFormattingClientCapabilities(
    val dynamicRegistration: Boolean? = null
)

@Serializable
data class DocumentRangeFormattingClientCapabilities(
    val dynamicRegistration: Boolean? = null
)

@Serializable
data class RenameClientCapabilities(
    val dynamicRegistration: Boolean? = null,
    val prepareSupport: Boolean? = null
)

@Serializable
data class PublishDiagnosticsClientCapabilities(
    val relatedInformation: Boolean? = null,
    val tagSupport: DiagnosticTagSupportCapabilities? = null,
    val versionSupport: Boolean? = null
)

@Serializable
data class DiagnosticTagSupportCapabilities(
    val valueSet: List<Int>? = null
)

@Serializable
data class SemanticTokensClientCapabilities(
    val dynamicRegistration: Boolean? = null,
    val requests: SemanticTokensRequestsCapabilities? = null,
    val tokenTypes: List<String>? = null,
    val tokenModifiers: List<String>? = null,
    val formats: List<String>? = null,
    val overlappingTokenSupport: Boolean? = null,
    val multilineTokenSupport: Boolean? = null
)

@Serializable
data class SemanticTokensRequestsCapabilities(
    val range: Boolean? = null,
    val full: SemanticTokensFullRequestCapabilities? = null
)

@Serializable
data class SemanticTokensFullRequestCapabilities(
    val delta: Boolean? = null
)

/**
 * General client capabilities.
 */
@Serializable
data class GeneralClientCapabilities(
    /**
     * Client capabilities specific to regular expressions.
     */
    val regularExpressions: RegularExpressionsClientCapabilities? = null,

    /**
     * Client capabilities specific to the client's markdown parser.
     */
    val markdown: MarkdownClientCapabilities? = null,

    /**
     * The position encodings supported by the client.
     */
    val positionEncodings: List<String>? = null
)

@Serializable
data class RegularExpressionsClientCapabilities(
    val engine: String? = null,
    val version: String? = null
)

@Serializable
data class MarkdownClientCapabilities(
    val parser: String? = null,
    val version: String? = null
)

/**
 * Initialize result returned by the server.
 */
@Serializable
data class InitializeResult(
    /**
     * The capabilities the language server provides.
     */
    val capabilities: ServerCapabilities,

    /**
     * Information about the server.
     */
    val serverInfo: ServerInfo? = null
)

/**
 * Information about the server.
 */
@Serializable
data class ServerInfo(
    /**
     * The name of the server as defined by the server.
     */
    val name: String,

    /**
     * The server's version as defined by the server.
     */
    val version: String? = null
)

/**
 * Server capabilities returned during initialization.
 */
@Serializable
data class ServerCapabilities(
    /**
     * The position encoding the server picked from the encodings offered by the client.
     */
    val positionEncoding: String? = null,

    /**
     * Defines how text documents are synced.
     */
    val textDocumentSync: TextDocumentSyncOptions? = null,

    /**
     * The server provides completion support.
     */
    val completionProvider: CompletionOptions? = null,

    /**
     * The server provides hover support.
     */
    val hoverProvider: Boolean? = null,

    /**
     * The server provides signature help support.
     */
    val signatureHelpProvider: SignatureHelpOptions? = null,

    /**
     * The server provides go to declaration support.
     */
    val declarationProvider: Boolean? = null,

    /**
     * The server provides goto definition support.
     */
    val definitionProvider: Boolean? = null,

    /**
     * The server provides goto type definition support.
     */
    val typeDefinitionProvider: Boolean? = null,

    /**
     * The server provides goto implementation support.
     */
    val implementationProvider: Boolean? = null,

    /**
     * The server provides find references support.
     */
    val referencesProvider: Boolean? = null,

    /**
     * The server provides document highlight support.
     */
    val documentHighlightProvider: Boolean? = null,

    /**
     * The server provides document symbol support.
     */
    val documentSymbolProvider: Boolean? = null,

    /**
     * The server provides code actions.
     */
    val codeActionProvider: Boolean? = null,

    /**
     * The server provides document formatting.
     */
    val documentFormattingProvider: Boolean? = null,

    /**
     * The server provides document range formatting.
     */
    val documentRangeFormattingProvider: Boolean? = null,

    /**
     * The server provides rename support.
     */
    val renameProvider: Boolean? = null,

    /**
     * The server provides workspace symbol support.
     */
    val workspaceSymbolProvider: Boolean? = null,

    /**
     * The server provides semantic tokens support.
     */
    val semanticTokensProvider: SemanticTokensOptions? = null
)

/**
 * Text document sync options.
 */
@Serializable
data class TextDocumentSyncOptions(
    /**
     * Open and close notifications are sent to the server.
     */
    val openClose: Boolean? = null,

    /**
     * Change notifications are sent to the server.
     */
    val change: Int? = null, // TextDocumentSyncKind

    /**
     * Will save notifications are sent to the server.
     */
    val willSave: Boolean? = null,

    /**
     * Will save wait until requests are sent to the server.
     */
    val willSaveWaitUntil: Boolean? = null,

    /**
     * Save notifications are sent to the server.
     */
    val save: SaveOptions? = null
)

/**
 * Save options.
 */
@Serializable
data class SaveOptions(
    /**
     * The client is supposed to include the content on save.
     */
    val includeText: Boolean? = null
)

/**
 * Completion options.
 */
@Serializable
data class CompletionOptions(
    /**
     * The additional characters, beyond the defaults, that trigger completion.
     */
    val triggerCharacters: List<String>? = null,

    /**
     * The list of all possible characters that commit a completion.
     */
    val allCommitCharacters: List<String>? = null,

    /**
     * The server provides support to resolve additional information for a completion item.
     */
    val resolveProvider: Boolean? = null
)

/**
 * Signature help options.
 */
@Serializable
data class SignatureHelpOptions(
    /**
     * The characters that trigger signature help automatically.
     */
    val triggerCharacters: List<String>? = null,

    /**
     * Characters that re-trigger signature help.
     */
    val retriggerCharacters: List<String>? = null
)

/**
 * Semantic tokens options.
 */
@Serializable
data class SemanticTokensOptions(
    /**
     * The legend used by the server.
     */
    val legend: SemanticTokensLegend,

    /**
     * Server supports providing semantic tokens for a specific range of a document.
     */
    val range: Boolean? = null,

    /**
     * Server supports providing semantic tokens for a full document.
     */
    val full: SemanticTokensFullOptions? = null
)

/**
 * Semantic tokens full options.
 */
@Serializable
data class SemanticTokensFullOptions(
    /**
     * The server supports deltas for full documents.
     */
    val delta: Boolean? = null
)

/**
 * Semantic tokens legend.
 */
@Serializable
data class SemanticTokensLegend(
    /**
     * The token types a server uses.
     */
    val tokenTypes: List<String>,

    /**
     * The token modifiers a server uses.
     */
    val tokenModifiers: List<String>
)

/**
 * Text document sync kind.
 */
object TextDocumentSyncKind {
    /**
     * Documents should not be synced at all.
     */
    const val NONE = 0

    /**
     * Documents are synced by always sending the full content.
     */
    const val FULL = 1

    /**
     * Documents are synced by sending incremental updates.
     */
    const val INCREMENTAL = 2
}

/**
 * Initialized notification parameters (empty).
 */
@Serializable
class InitializedParams

/**
 * Shutdown request (no parameters, returns null).
 */
@Serializable
class ShutdownParams
