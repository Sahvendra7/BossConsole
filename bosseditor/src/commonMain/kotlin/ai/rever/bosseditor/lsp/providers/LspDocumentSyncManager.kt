package ai.rever.bosseditor.lsp.providers

import ai.rever.bosseditor.lsp.client.LspClient
import ai.rever.bosseditor.lsp.client.LspMethods
import ai.rever.bosseditor.lsp.protocol.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*

/**
 * Manages document synchronization between the editor and LSP server.
 *
 * This component:
 * - Tracks open documents and their versions
 * - Sends didOpen, didChange, didClose notifications
 * - Requests semantic tokens on document changes
 * - Coordinates with LspSemanticTokenProvider for token updates
 *
 * **Note**: This class uses JVM-specific `synchronized()` for thread safety.
 * While placed in commonMain for code organization, this is desktop-only
 * as BOSS targets only desktop platforms (macOS, Windows, Linux).
 *
 * Usage:
 * ```kotlin
 * val syncManager = LspDocumentSyncManager(client, semanticTokenProvider)
 *
 * // When a document is opened
 * syncManager.documentOpened(uri, languageId, content)
 *
 * // When content changes
 * syncManager.documentChanged(uri, newContent)
 *
 * // When a document is closed
 * syncManager.documentClosed(uri)
 * ```
 */
class LspDocumentSyncManager(
    private val client: LspClient,
    private val semanticTokenProvider: LspSemanticTokenProvider,
    private val config: DocumentSyncConfig = DocumentSyncConfig()
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    /**
     * Tracked document states.
     */
    private val documents = mutableMapOf<String, DocumentState>()

    /**
     * Lock for thread-safe access to documents.
     */
    private val documentsLock = Any()

    /**
     * Coroutine scope for async operations.
     * Uses Dispatchers.IO since LSP requests are I/O-bound (network/IPC).
     */
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Debounce jobs for semantic token requests.
     */
    private val tokenRequestJobs = mutableMapOf<String, Job>()

    /**
     * Lock for thread-safe access to tokenRequestJobs.
     */
    private val jobsLock = Any()

    /**
     * Semantic tokens legend from the server.
     */
    private var semanticTokensLegend: SemanticTokensLegend? = null

    /**
     * Whether the server supports semantic tokens.
     */
    val supportsSemanticTokens: Boolean
        get() = semanticTokensLegend != null

    /**
     * Initialize the document sync manager with server capabilities.
     *
     * Should be called after receiving InitializeResult from the server.
     *
     * @param capabilities The server capabilities from initialization
     */
    fun initialize(capabilities: ServerCapabilities) {
        // Extract semantic tokens legend if available
        capabilities.semanticTokensProvider?.let { provider ->
            semanticTokensLegend = provider.legend
            semanticTokenProvider.setLegend(provider.legend)
        }
    }

    /**
     * Notify the server that a document was opened.
     *
     * @param uri The document URI (file:///path/to/file)
     * @param languageId The language identifier (e.g., "kotlin", "python")
     * @param content The full document content
     */
    fun documentOpened(uri: String, languageId: String, content: String) {
        val version = 1
        val lineCount = content.lines().size

        val state = DocumentState(
            uri = uri,
            languageId = languageId,
            version = version,
            content = content,
            lineCount = lineCount
        )

        synchronized(documentsLock) {
            documents[uri] = state
        }

        // Update semantic provider line count
        semanticTokenProvider.setLineCount(lineCount)

        // Send notification
        val params = DidOpenTextDocumentParams(
            textDocument = TextDocumentItem(
                uri = uri,
                languageId = languageId,
                version = version,
                text = content
            )
        )
        client.notify(LspMethods.DID_OPEN, json.encodeToJsonElement(params))

        // Request semantic tokens
        requestSemanticTokens(uri)
    }

    /**
     * Notify the server that a document's content changed.
     *
     * @param uri The document URI
     * @param newContent The new full document content
     * @param changeRange Optional range of the change (for incremental sync)
     */
    fun documentChanged(
        uri: String,
        newContent: String,
        changeRange: Range? = null
    ) {
        val state = synchronized(documentsLock) {
            documents[uri]
        } ?: return

        val newVersion = state.version + 1
        val newLineCount = newContent.lines().size

        // Update state
        val newState = state.copy(
            version = newVersion,
            content = newContent,
            lineCount = newLineCount
        )

        synchronized(documentsLock) {
            documents[uri] = newState
        }

        // Update semantic provider line count
        semanticTokenProvider.setLineCount(newLineCount)

        // Determine the change event based on server capabilities
        val contentChanges = if (changeRange != null && supportsIncrementalSync()) {
            // Incremental change
            listOf(TextDocumentContentChangeEvent.incremental(changeRange, newContent))
        } else {
            // Full document sync
            listOf(TextDocumentContentChangeEvent.fullDocument(newContent))
        }

        val params = DidChangeTextDocumentParams(
            textDocument = VersionedTextDocumentIdentifier(
                uri = uri,
                version = newVersion
            ),
            contentChanges = contentChanges
        )
        client.notify(LspMethods.DID_CHANGE, json.encodeToJsonElement(params))

        // Invalidate semantic tokens for changed lines
        changeRange?.let { range ->
            val lineDelta = newLineCount - state.lineCount
            semanticTokenProvider.invalidateLines(
                startLine = range.start.line,
                endLine = range.end.line,
                lineDelta = lineDelta
            )
        } ?: semanticTokenProvider.clear()

        // Request semantic tokens with debounce
        requestSemanticTokensDebounced(uri)
    }

    /**
     * Notify the server that a document was saved.
     *
     * @param uri The document URI
     * @param content Optional content if includeText is enabled
     */
    fun documentSaved(uri: String, content: String? = null) {
        val params = DidSaveTextDocumentParams(
            textDocument = TextDocumentIdentifier(uri),
            text = content
        )
        client.notify(LspMethods.DID_SAVE, json.encodeToJsonElement(params))
    }

    /**
     * Notify the server that a document was closed.
     *
     * @param uri The document URI
     */
    fun documentClosed(uri: String) {
        synchronized(documentsLock) {
            documents.remove(uri)
        }

        // Cancel any pending token requests
        synchronized(jobsLock) {
            tokenRequestJobs.remove(uri)?.cancel()
        }

        // Clear semantic tokens
        semanticTokenProvider.clear()

        val params = DidCloseTextDocumentParams(
            textDocument = TextDocumentIdentifier(uri)
        )
        client.notify(LspMethods.DID_CLOSE, json.encodeToJsonElement(params))
    }

    /**
     * Request semantic tokens for a document.
     *
     * @param uri The document URI
     */
    fun requestSemanticTokens(uri: String) {
        if (semanticTokensLegend == null) return

        scope.launch {
            try {
                val params = SemanticTokensParams(
                    textDocument = TextDocumentIdentifier(uri)
                )

                val result = client.request(
                    LspMethods.SEMANTIC_TOKENS_FULL,
                    json.encodeToJsonElement(params)
                )

                result?.let { processSemanticTokensResult(it) }

            } catch (e: Exception) {
                if (e !is CancellationException) {
                    println("[LspDocumentSyncManager] Error requesting semantic tokens: ${e.message}")
                }
            }
        }
    }

    /**
     * Request semantic tokens with debouncing.
     *
     * This prevents excessive requests during rapid typing.
     *
     * @param uri The document URI
     */
    private fun requestSemanticTokensDebounced(uri: String) {
        if (semanticTokensLegend == null) return

        synchronized(jobsLock) {
            // Cancel existing job for this URI
            tokenRequestJobs.remove(uri)?.cancel()

            // Create new debounced job
            tokenRequestJobs[uri] = scope.launch {
                delay(config.semanticTokenDebounceMs)
                requestSemanticTokens(uri)
            }
        }
    }

    /**
     * Process semantic tokens result from the server.
     */
    private fun processSemanticTokensResult(result: JsonElement) {
        try {
            val tokens = json.decodeFromJsonElement<SemanticTokens>(result)
            semanticTokenProvider.updateTokens(
                data = tokens.data,
                resultId = tokens.resultId
            )
        } catch (e: Exception) {
            println("[LspDocumentSyncManager] Error processing semantic tokens: ${e.message}")
        }
    }

    /**
     * Check if the server supports incremental document sync.
     * Returns false if capabilities are not available or sync mode is not INCREMENTAL.
     */
    private fun supportsIncrementalSync(): Boolean {
        val capabilities = client.serverCapabilities ?: return false
        val syncOptions = capabilities.textDocumentSync ?: return false
        return syncOptions.change == TextDocumentSyncKind.INCREMENTAL
    }

    /**
     * Get the current document state.
     *
     * @param uri The document URI
     * @return The document state, or null if not tracked
     */
    fun getDocumentState(uri: String): DocumentState? {
        return synchronized(documentsLock) {
            documents[uri]
        }
    }

    /**
     * Get the current version of a document.
     *
     * @param uri The document URI
     * @return The document version, or 0 if not tracked
     */
    fun getDocumentVersion(uri: String): Int {
        return synchronized(documentsLock) {
            documents[uri]?.version ?: 0
        }
    }

    /**
     * Dispose of the sync manager and release resources.
     */
    fun dispose() {
        scope.cancel()

        synchronized(jobsLock) {
            tokenRequestJobs.values.forEach { it.cancel() }
            tokenRequestJobs.clear()
        }

        synchronized(documentsLock) {
            documents.clear()
        }

        semanticTokenProvider.clear()
    }
}

/**
 * Configuration for document synchronization.
 */
data class DocumentSyncConfig(
    /**
     * Debounce delay for semantic token requests (in milliseconds).
     */
    val semanticTokenDebounceMs: Long = 300,

    /**
     * Whether to request semantic tokens automatically on changes.
     */
    val autoRequestSemanticTokens: Boolean = true
)

/**
 * Represents the current state of an open document.
 */
data class DocumentState(
    /**
     * The document URI.
     */
    val uri: String,

    /**
     * The language identifier.
     */
    val languageId: String,

    /**
     * The current version number.
     */
    val version: Int,

    /**
     * The current document content.
     */
    val content: String,

    /**
     * Total number of lines in the document.
     */
    val lineCount: Int
)
