package ai.rever.bosseditor.lsp.providers

import ai.rever.bosseditor.highlight.HighlightLayer
import ai.rever.bosseditor.logging.EditorLogCategory
import ai.rever.bosseditor.logging.EditorLogger
import ai.rever.bosseditor.lsp.client.LspClient
import ai.rever.bosseditor.lsp.client.LspMethods
import ai.rever.bosseditor.lsp.protocol.PublishDiagnosticsParams
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.json.*

private val logger = EditorLogger.forComponent("LspHighlightCoordinator")

/**
 * Coordinates LSP-based highlighting with BossEditor's HighlightLayer.
 *
 * This is the main entry point for integrating LSP semantic tokens with
 * the editor's highlighting system. It manages:
 * - Semantic token provider creation and updates
 * - Document synchronization with the LSP server
 * - Diagnostics handling
 * - HighlightLayer integration
 *
 * ## Usage
 * ```kotlin
 * // Create coordinator with LSP client
 * val coordinator = LspHighlightCoordinator(client)
 *
 * // Initialize with server capabilities
 * coordinator.initialize(initializeResult.capabilities)
 *
 * // Attach to highlight layer
 * coordinator.attachToHighlightLayer(highlightLayer)
 *
 * // When a file is opened
 * coordinator.documentOpened(uri, languageId, content)
 *
 * // When content changes
 * coordinator.documentChanged(uri, newContent)
 *
 * // Observe diagnostics
 * coordinator.diagnostics.collect { (uri, diagnostics) ->
 *     // Update error markers in editor
 * }
 * ```
 */
class LspHighlightCoordinator(
    private val client: LspClient,
    private val config: DocumentSyncConfig = DocumentSyncConfig()
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    /**
     * The semantic token provider used by this coordinator.
     */
    val semanticTokenProvider = LspSemanticTokenProvider()

    /**
     * The document sync manager for this coordinator.
     */
    val documentSyncManager = LspDocumentSyncManager(
        client = client,
        semanticTokenProvider = semanticTokenProvider,
        config = config
    )

    /**
     * Flow of diagnostics from the LSP server.
     * Emits pairs of (uri, diagnostics list).
     */
    private val _diagnostics = MutableSharedFlow<DiagnosticsUpdate>(extraBufferCapacity = 16)
    val diagnostics: Flow<DiagnosticsUpdate> = _diagnostics.asSharedFlow()

    /**
     * The highlight layer this coordinator is attached to.
     */
    private var highlightLayer: HighlightLayer? = null

    /**
     * Current document URI being edited.
     */
    private var currentDocumentUri: String? = null

    /**
     * Initialize the coordinator with server capabilities.
     *
     * Should be called after the LSP client has initialized.
     *
     * @param capabilities The server capabilities from InitializeResult
     */
    fun initialize(capabilities: ai.rever.bosseditor.lsp.protocol.ServerCapabilities) {
        documentSyncManager.initialize(capabilities)

        // Subscribe to diagnostics notifications
        client.onNotification(LspMethods.PUBLISH_DIAGNOSTICS) { _, params ->
            handleDiagnosticsNotification(params)
        }
    }

    /**
     * Attach this coordinator to a HighlightLayer.
     *
     * This sets the semantic token provider on the highlight layer,
     * enabling LSP-based semantic highlighting.
     *
     * @param layer The highlight layer to attach to
     */
    fun attachToHighlightLayer(layer: HighlightLayer) {
        highlightLayer = layer
        layer.setSemanticProvider(semanticTokenProvider)
    }

    /**
     * Detach from the current HighlightLayer.
     */
    fun detachFromHighlightLayer() {
        highlightLayer?.setSemanticProvider(null)
        highlightLayer = null
    }

    /**
     * Called when a document is opened in the editor.
     *
     * @param uri The document URI (file:///path/to/file)
     * @param languageId The language identifier (e.g., "kotlin", "python")
     * @param content The full document content
     */
    fun documentOpened(uri: String, languageId: String, content: String) {
        currentDocumentUri = uri
        documentSyncManager.documentOpened(uri, languageId, content)
    }

    /**
     * Called when the document content changes.
     *
     * @param uri The document URI
     * @param newContent The new full document content
     */
    fun documentChanged(uri: String, newContent: String) {
        documentSyncManager.documentChanged(uri, newContent)

        // Invalidate the highlight layer to force re-rendering
        highlightLayer?.invalidateAll()
    }

    /**
     * Called when a document is saved.
     *
     * @param uri The document URI
     * @param content Optional content if includeText is enabled
     */
    fun documentSaved(uri: String, content: String? = null) {
        documentSyncManager.documentSaved(uri, content)
    }

    /**
     * Called when a document is closed.
     *
     * @param uri The document URI
     */
    fun documentClosed(uri: String) {
        documentSyncManager.documentClosed(uri)
        if (currentDocumentUri == uri) {
            currentDocumentUri = null
        }
    }

    /**
     * Manually request a refresh of semantic tokens.
     *
     * Useful after significant changes or when tokens may be stale.
     */
    fun refreshSemanticTokens() {
        currentDocumentUri?.let { uri ->
            documentSyncManager.requestSemanticTokens(uri)
        }
    }

    /**
     * Handle diagnostics notification from the server.
     */
    private fun handleDiagnosticsNotification(params: JsonElement?) {
        if (params == null) return

        try {
            val diagnosticsParams = json.decodeFromJsonElement<PublishDiagnosticsParams>(params)
            _diagnostics.tryEmit(
                DiagnosticsUpdate(
                    uri = diagnosticsParams.uri,
                    version = diagnosticsParams.version,
                    diagnostics = diagnosticsParams.diagnostics
                )
            )
        } catch (e: Exception) {
            logger.error(EditorLogCategory.DIAGNOSTICS, "Error parsing diagnostics", error = e)
        }
    }

    /**
     * Get the current language ID from the highlight layer.
     */
    val currentLanguageId: String?
        get() = highlightLayer?.languageId

    /**
     * Check if semantic tokens are available.
     */
    val hasSemanticTokens: Boolean
        get() = semanticTokenProvider.isAvailable()

    /**
     * Check if the server supports semantic tokens.
     */
    val supportsSemanticTokens: Boolean
        get() = documentSyncManager.supportsSemanticTokens

    /**
     * Dispose of the coordinator and release resources.
     */
    fun dispose() {
        detachFromHighlightLayer()
        documentSyncManager.dispose()
        semanticTokenProvider.clear()
        currentDocumentUri = null
    }
}

/**
 * Represents a diagnostics update from the LSP server.
 */
data class DiagnosticsUpdate(
    /**
     * The document URI.
     */
    val uri: String,

    /**
     * The document version these diagnostics apply to.
     */
    val version: Int?,

    /**
     * The list of diagnostics for this document.
     */
    val diagnostics: List<ai.rever.bosseditor.lsp.protocol.Diagnostic>
)
