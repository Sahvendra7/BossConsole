package ai.rever.bosseditor.lsp.client

import ai.rever.bosseditor.lsp.protocol.*
import kotlinx.serialization.json.JsonElement

/**
 * LSP client interface for communicating with language servers.
 *
 * This interface abstracts the communication layer, allowing different
 * transport implementations (stdio, socket, etc.).
 */
interface LspClient {
    /**
     * The current state of the client connection.
     */
    val state: LspClientState

    /**
     * Whether the client is connected and initialized.
     */
    val isInitialized: Boolean

    /**
     * Server capabilities received during initialization.
     */
    val serverCapabilities: ServerCapabilities?

    /**
     * Send a request and wait for the response.
     *
     * @param method The LSP method name (e.g., "textDocument/completion")
     * @param params The request parameters (serialized to JSON)
     * @return The response result (as JsonElement, needs to be deserialized)
     * @throws LspException if the request fails or times out
     */
    suspend fun request(method: String, params: JsonElement?): JsonElement?

    /**
     * Send a notification (no response expected).
     *
     * @param method The LSP method name (e.g., "textDocument/didOpen")
     * @param params The notification parameters
     */
    fun notify(method: String, params: JsonElement?)

    /**
     * Register a handler for server notifications.
     *
     * @param method The notification method to handle (or null for all)
     * @param handler The callback to invoke when a notification is received
     */
    fun onNotification(method: String? = null, handler: (String, JsonElement?) -> Unit)

    /**
     * Register a handler for server requests (rare, but some servers use this).
     *
     * @param method The request method to handle
     * @param handler The callback to invoke, returning the response
     */
    fun onRequest(method: String, handler: suspend (JsonElement?) -> JsonElement?)

    /**
     * Initialize the connection with the language server.
     *
     * @param params The initialization parameters
     * @return The initialization result containing server capabilities
     */
    suspend fun initialize(params: InitializeParams): InitializeResult

    /**
     * Send the initialized notification (after initialize completes).
     */
    fun initialized()

    /**
     * Request shutdown of the language server.
     */
    suspend fun shutdown()

    /**
     * Send the exit notification (after shutdown).
     */
    fun exit()

    /**
     * Dispose of the client and release resources.
     */
    fun dispose()
}

/**
 * State of the LSP client connection.
 */
enum class LspClientState {
    /**
     * Not connected to any server.
     */
    DISCONNECTED,

    /**
     * Connecting to the server.
     */
    CONNECTING,

    /**
     * Connected but not yet initialized.
     */
    CONNECTED,

    /**
     * Initialize request sent, waiting for response.
     */
    INITIALIZING,

    /**
     * Fully initialized and ready for requests.
     */
    INITIALIZED,

    /**
     * Shutdown requested.
     */
    SHUTTING_DOWN,

    /**
     * Connection error occurred.
     */
    ERROR
}

/**
 * Exception thrown when LSP operations fail.
 */
open class LspException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

/**
 * Exception for LSP request timeouts.
 */
class LspTimeoutException(
    method: String,
    timeoutMs: Long
) : LspException("LSP request '$method' timed out after ${timeoutMs}ms")

/**
 * Exception for LSP request errors (server returned error response).
 */
class LspRequestException(
    method: String,
    val errorCode: Int,
    errorMessage: String,
    val errorData: JsonElement? = null
) : LspException("LSP request '$method' failed: [$errorCode] $errorMessage")

/**
 * Exception for connection errors.
 */
class LspConnectionException(
    message: String,
    cause: Throwable? = null
) : LspException(message, cause)

/**
 * Configuration for LSP client behavior.
 */
data class LspClientConfig(
    /**
     * Request timeout in milliseconds.
     */
    val requestTimeoutMs: Long = 30_000,

    /**
     * Initialize timeout in milliseconds (may take longer for large projects).
     */
    val initializeTimeoutMs: Long = 60_000,

    /**
     * Whether to log all messages for debugging.
     */
    val traceMessages: Boolean = false,

    /**
     * Maximum number of pending requests.
     */
    val maxPendingRequests: Int = 100
)

/**
 * LSP method names for common operations.
 */
object LspMethods {
    // Lifecycle
    const val INITIALIZE = "initialize"
    const val INITIALIZED = "initialized"
    const val SHUTDOWN = "shutdown"
    const val EXIT = "exit"

    // Document sync
    const val DID_OPEN = "textDocument/didOpen"
    const val DID_CHANGE = "textDocument/didChange"
    const val DID_SAVE = "textDocument/didSave"
    const val DID_CLOSE = "textDocument/didClose"

    // Language features
    const val COMPLETION = "textDocument/completion"
    const val HOVER = "textDocument/hover"
    const val SIGNATURE_HELP = "textDocument/signatureHelp"
    const val DEFINITION = "textDocument/definition"
    const val REFERENCES = "textDocument/references"
    const val DOCUMENT_HIGHLIGHT = "textDocument/documentHighlight"
    const val DOCUMENT_SYMBOL = "textDocument/documentSymbol"
    const val CODE_ACTION = "textDocument/codeAction"
    const val FORMATTING = "textDocument/formatting"
    const val RANGE_FORMATTING = "textDocument/rangeFormatting"
    const val RENAME = "textDocument/rename"

    // Semantic tokens
    const val SEMANTIC_TOKENS_FULL = "textDocument/semanticTokens/full"
    const val SEMANTIC_TOKENS_RANGE = "textDocument/semanticTokens/range"
    const val SEMANTIC_TOKENS_DELTA = "textDocument/semanticTokens/full/delta"

    // Notifications from server
    const val PUBLISH_DIAGNOSTICS = "textDocument/publishDiagnostics"
    const val LOG_MESSAGE = "window/logMessage"
    const val SHOW_MESSAGE = "window/showMessage"
}
