package ai.rever.bosseditor.lsp.client

import ai.rever.bosseditor.lsp.protocol.*
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.io.File

/**
 * Desktop implementation of LspClient using process stdio.
 *
 * Spawns a language server process and communicates via stdin/stdout.
 */
class DesktopLspClient(
    private val command: List<String>,
    private val workingDirectory: File? = null,
    private val environment: Map<String, String> = emptyMap(),
    private val config: LspClientConfig = LspClientConfig()
) : LspClient {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        isLenient = true
    }

    private var process: Process? = null
    private var transport: LspTransport? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val notificationHandlers = mutableListOf<(String, JsonElement?) -> Unit>()
    private val methodNotificationHandlers = mutableMapOf<String, MutableList<(String, JsonElement?) -> Unit>>()

    override var state: LspClientState = LspClientState.DISCONNECTED
        private set

    override val isInitialized: Boolean
        get() = state == LspClientState.INITIALIZED

    override var serverCapabilities: ServerCapabilities? = null
        private set

    /**
     * Start the language server process and establish connection.
     */
    fun start() {
        if (state != LspClientState.DISCONNECTED) {
            throw LspException("Client already started")
        }

        state = LspClientState.CONNECTING

        try {
            val processBuilder = ProcessBuilder(command).apply {
                workingDirectory?.let { directory(it) }
                environment().putAll(this@DesktopLspClient.environment)
                redirectErrorStream(false)
            }

            process = processBuilder.start()

            val proc = process ?: throw LspConnectionException("Failed to start process")

            transport = LspTransport(
                input = proc.inputStream,
                output = proc.outputStream,
                config = config
            ).also { it.start() }

            // Subscribe to notifications
            scope.launch {
                transport?.notifications?.collect { (method, params) ->
                    dispatchNotification(method, params)
                }
            }

            // Monitor process for unexpected exit (waits indefinitely until process terminates)
            scope.launch {
                proc.waitFor()  // Wait indefinitely - this is a monitor, not a shutdown
                if (state != LspClientState.DISCONNECTED) {
                    state = LspClientState.ERROR
                    println("[DesktopLspClient] Language server process exited unexpectedly")
                }
            }

            state = LspClientState.CONNECTED

        } catch (e: Exception) {
            state = LspClientState.ERROR
            throw LspConnectionException("Failed to start language server: ${e.message}", e)
        }
    }

    override suspend fun request(method: String, params: JsonElement?): JsonElement? {
        val t = transport ?: throw LspException("Not connected")
        return t.sendRequest(method, params)
    }

    override fun notify(method: String, params: JsonElement?) {
        val t = transport ?: throw LspException("Not connected")
        t.sendNotification(method, params)
    }

    override fun onNotification(method: String?, handler: (String, JsonElement?) -> Unit) {
        if (method != null) {
            methodNotificationHandlers.getOrPut(method) { mutableListOf() }.add(handler)
        } else {
            notificationHandlers.add(handler)
        }
    }

    override fun onRequest(method: String, handler: suspend (JsonElement?) -> JsonElement?) {
        transport?.registerRequestHandler(method, handler)
    }

    override suspend fun initialize(params: InitializeParams): InitializeResult {
        if (state == LspClientState.DISCONNECTED) {
            start()
        }

        if (state != LspClientState.CONNECTED) {
            throw LspException("Cannot initialize: invalid state $state")
        }

        state = LspClientState.INITIALIZING

        try {
            val paramsJson = json.encodeToJsonElement(params)
            val resultJson = request(LspMethods.INITIALIZE, paramsJson)
                ?: throw LspException("Initialize returned null")

            val result = json.decodeFromJsonElement<InitializeResult>(resultJson)
            serverCapabilities = result.capabilities

            state = LspClientState.INITIALIZED
            return result

        } catch (e: Exception) {
            state = LspClientState.ERROR
            throw e
        }
    }

    override fun initialized() {
        if (state != LspClientState.INITIALIZED) {
            throw LspException("Cannot send initialized: not initialized")
        }
        notify(LspMethods.INITIALIZED, json.encodeToJsonElement(InitializedParams()))
    }

    override suspend fun shutdown() {
        if (state != LspClientState.INITIALIZED) {
            return
        }

        state = LspClientState.SHUTTING_DOWN

        try {
            request(LspMethods.SHUTDOWN, null)
        } catch (e: Exception) {
            println("[DesktopLspClient] Shutdown error: ${e.message}")
        }
    }

    override fun exit() {
        try {
            notify(LspMethods.EXIT, null)
        } catch (e: Exception) {
            // Ignore - process may already be gone
        }
    }

    override fun dispose() {
        state = LspClientState.DISCONNECTED

        scope.cancel()
        transport?.stop()
        transport = null

        // Best-effort process cleanup on background thread to avoid blocking UI.
        // Note: This is fire-and-forget - if app exits immediately, OS will clean up orphaned process.
        val proc = process
        process = null
        if (proc != null) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    // Try graceful shutdown first
                    proc.destroy()
                    delay(50) // Allow process to clean up

                    // Force kill if still running
                    if (proc.isAlive) {
                        proc.destroyForcibly()
                    }
                } catch (e: Exception) {
                    println("[DesktopLspClient] Process disposal error: ${e.message}")
                }
            }
        }

        notificationHandlers.clear()
        methodNotificationHandlers.clear()
        serverCapabilities = null
    }

    private fun dispatchNotification(method: String, params: JsonElement?) {
        // Global handlers
        notificationHandlers.forEach { handler ->
            try {
                handler(method, params)
            } catch (e: Exception) {
                println("[DesktopLspClient] Notification handler error: ${e.message}")
            }
        }

        // Method-specific handlers
        methodNotificationHandlers[method]?.forEach { handler ->
            try {
                handler(method, params)
            } catch (e: Exception) {
                println("[DesktopLspClient] Notification handler error: ${e.message}")
            }
        }
    }

    companion object {
        /**
         * Create default client capabilities for BossEditor.
         */
        fun createDefaultCapabilities(): ClientCapabilities {
            return ClientCapabilities(
                workspace = WorkspaceClientCapabilities(
                    applyEdit = true,
                    workspaceEdit = WorkspaceEditClientCapabilities(
                        documentChanges = true
                    ),
                    didChangeConfiguration = DidChangeConfigurationClientCapabilities(
                        dynamicRegistration = false
                    ),
                    didChangeWatchedFiles = DidChangeWatchedFilesClientCapabilities(
                        dynamicRegistration = false
                    ),
                    symbol = WorkspaceSymbolClientCapabilities(
                        dynamicRegistration = false
                    ),
                    executeCommand = ExecuteCommandClientCapabilities(
                        dynamicRegistration = false
                    ),
                    workspaceFolders = true,
                    configuration = true
                ),
                textDocument = TextDocumentClientCapabilities(
                    synchronization = TextDocumentSyncClientCapabilities(
                        dynamicRegistration = false,
                        willSave = true,
                        willSaveWaitUntil = false,
                        didSave = true
                    ),
                    completion = CompletionClientCapabilities(
                        dynamicRegistration = false,
                        completionItem = CompletionItemClientCapabilities(
                            snippetSupport = false,
                            commitCharactersSupport = true,
                            documentationFormat = listOf(MarkupKind.MARKDOWN, MarkupKind.PLAINTEXT),
                            deprecatedSupport = true,
                            preselectSupport = true
                        ),
                        contextSupport = true
                    ),
                    hover = HoverClientCapabilities(
                        dynamicRegistration = false,
                        contentFormat = listOf(MarkupKind.MARKDOWN, MarkupKind.PLAINTEXT)
                    ),
                    signatureHelp = SignatureHelpClientCapabilities(
                        dynamicRegistration = false,
                        signatureInformation = SignatureInformationClientCapabilities(
                            documentationFormat = listOf(MarkupKind.MARKDOWN, MarkupKind.PLAINTEXT),
                            parameterInformation = ParameterInformationClientCapabilities(
                                labelOffsetSupport = true
                            )
                        ),
                        contextSupport = true
                    ),
                    definition = DefinitionClientCapabilities(
                        dynamicRegistration = false,
                        linkSupport = true
                    ),
                    references = ReferenceClientCapabilities(
                        dynamicRegistration = false
                    ),
                    documentHighlight = DocumentHighlightClientCapabilities(
                        dynamicRegistration = false
                    ),
                    documentSymbol = DocumentSymbolClientCapabilities(
                        dynamicRegistration = false,
                        hierarchicalDocumentSymbolSupport = true
                    ),
                    codeAction = CodeActionClientCapabilities(
                        dynamicRegistration = false,
                        codeActionLiteralSupport = CodeActionLiteralSupportCapabilities(
                            codeActionKind = CodeActionKindCapabilities(
                                valueSet = listOf(
                                    "quickfix",
                                    "refactor",
                                    "refactor.extract",
                                    "refactor.inline",
                                    "refactor.rewrite",
                                    "source",
                                    "source.organizeImports"
                                )
                            )
                        )
                    ),
                    formatting = DocumentFormattingClientCapabilities(
                        dynamicRegistration = false
                    ),
                    rangeFormatting = DocumentRangeFormattingClientCapabilities(
                        dynamicRegistration = false
                    ),
                    rename = RenameClientCapabilities(
                        dynamicRegistration = false,
                        prepareSupport = true
                    ),
                    publishDiagnostics = PublishDiagnosticsClientCapabilities(
                        relatedInformation = true,
                        tagSupport = DiagnosticTagSupportCapabilities(
                            valueSet = listOf(DiagnosticTag.UNNECESSARY, DiagnosticTag.DEPRECATED)
                        ),
                        versionSupport = true
                    ),
                    semanticTokens = SemanticTokensClientCapabilities(
                        dynamicRegistration = false,
                        requests = SemanticTokensRequestsCapabilities(
                            range = true,
                            full = SemanticTokensFullRequestCapabilities(
                                delta = true
                            )
                        ),
                        tokenTypes = SemanticTokenTypes.ALL,
                        tokenModifiers = SemanticTokenModifiers.ALL,
                        formats = listOf("relative"),
                        overlappingTokenSupport = false,
                        multilineTokenSupport = true
                    )
                ),
                general = GeneralClientCapabilities(
                    positionEncodings = listOf("utf-16")
                )
            )
        }

        /**
         * Create initialization parameters for a workspace.
         */
        fun createInitializeParams(
            processId: Int?,
            rootUri: String?,
            workspaceFolders: List<WorkspaceFolder>? = null,
            clientName: String = "BossEditor",
            clientVersion: String = "1.0.0"
        ): InitializeParams {
            return InitializeParams(
                processId = processId,
                clientInfo = ClientInfo(
                    name = clientName,
                    version = clientVersion
                ),
                rootUri = rootUri,
                capabilities = createDefaultCapabilities(),
                trace = TraceValue.OFF,
                workspaceFolders = workspaceFolders
            )
        }
    }
}
