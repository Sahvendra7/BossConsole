package ai.rever.bosseditor.lsp.server

import ai.rever.bosseditor.lsp.client.DesktopLspClient
import ai.rever.bosseditor.lsp.client.LspClient
import ai.rever.bosseditor.lsp.client.LspClientConfig
import ai.rever.bosseditor.lsp.client.LspClientState
import ai.rever.bosseditor.lsp.logging.LspLogger
import ai.rever.bosseditor.lsp.logging.LogCategory
import ai.rever.bosseditor.lsp.protocol.InitializeResult
import ai.rever.bosseditor.lsp.protocol.WorkspaceFolder
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/**
 * Manages the lifecycle of language server instances.
 *
 * Handles:
 * - Starting language servers on demand
 * - Reusing existing servers for the same language
 * - Graceful shutdown of servers
 * - Server health monitoring
 *
 * ## Usage
 * ```kotlin
 * val manager = LanguageServerManager()
 *
 * // Get or start a server for a file
 * val client = manager.getOrStartServerForFile("/path/to/file.py", "/workspace")
 *
 * // Or by language ID
 * val pythonClient = manager.getOrStartServer("python", "/workspace")
 *
 * // Stop a specific server
 * manager.stopServer("python")
 *
 * // Stop all servers on shutdown
 * manager.stopAll()
 * ```
 */
class LanguageServerManager(
    private val clientConfig: LspClientConfig = LspClientConfig(),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) {
    private val logger = LspLogger.forComponent("LanguageServerManager")

    companion object {
        /** Interval for server health monitoring in milliseconds */
        private const val HEALTH_CHECK_INTERVAL_MS = 5000L

        /** Timeout for graceful server shutdown in milliseconds */
        private const val SHUTDOWN_TIMEOUT_MS = 10_000L

        /** Pause before restarting a server in milliseconds */
        private const val RESTART_PAUSE_MS = 500L

        /** Buffer capacity for server events SharedFlow */
        private const val SERVER_EVENTS_BUFFER_CAPACITY = 32
    }

    /**
     * Active language server instances by language ID.
     */
    private val activeServers = mutableMapOf<String, ManagedServer>()

    /**
     * Mutex for thread-safe access to activeServers.
     * All access to activeServers must go through this mutex.
     */
    private val serversMutex = Mutex()

    /**
     * Flow of server state changes.
     */
    private val _serverEvents = MutableSharedFlow<ServerEvent>(extraBufferCapacity = SERVER_EVENTS_BUFFER_CAPACITY)
    val serverEvents: SharedFlow<ServerEvent> = _serverEvents.asSharedFlow()

    /**
     * Server discovery utility.
     */
    private val serverDiscovery = ServerDiscovery()

    /**
     * Get or start a language server for a file.
     *
     * @param filePath Path to the file being edited
     * @param workspaceRoot Root directory of the workspace
     * @return The LSP client, or null if no server is available for this file type
     */
    suspend fun getOrStartServerForFile(
        filePath: String,
        workspaceRoot: String
    ): LspClient? {
        val config = LanguageServerRegistry.getConfigForFile(filePath) ?: return null
        return getOrStartServer(config, workspaceRoot)
    }

    /**
     * Get or start a language server by language ID.
     *
     * @param languageId The LSP language identifier
     * @param workspaceRoot Root directory of the workspace
     * @return The LSP client, or null if no server is configured for this language
     */
    suspend fun getOrStartServer(
        languageId: String,
        workspaceRoot: String
    ): LspClient? {
        val config = LanguageServerRegistry.getConfigForLanguage(languageId) ?: return null
        return getOrStartServer(config, workspaceRoot)
    }

    /**
     * Get or start a language server with the given configuration.
     *
     * @param config The language server configuration
     * @param workspaceRoot Root directory of the workspace
     * @return The LSP client
     * @throws LanguageServerException if the server cannot be started
     */
    suspend fun getOrStartServer(
        config: LanguageServerConfig,
        workspaceRoot: String
    ): LspClient {
        serversMutex.withLock {
            val existing = activeServers[config.languageId]
            if (existing != null && existing.isRunning()) {
                return existing.client
            }
        }

        return startServer(config, workspaceRoot)
    }

    /**
     * Start a new language server.
     */
    private suspend fun startServer(
        config: LanguageServerConfig,
        workspaceRoot: String
    ): LspClient {
        // Check if server command is available
        val commandAvailable = serverDiscovery.isCommandAvailable(config.command.first())
        if (!commandAvailable) {
            emitEvent(ServerEvent.ServerUnavailable(config, "Command '${config.command.first()}' not found in PATH"))
            throw LanguageServerException(
                "Language server '${config.displayName}' is not installed. " +
                    "Command '${config.command.first()}' not found."
            )
        }

        emitEvent(ServerEvent.ServerStarting(config))

        try {
            val client = DesktopLspClient(
                command = config.command,
                workingDirectory = File(workspaceRoot),
                config = clientConfig
            )

            val managedServer = ManagedServer(
                config = config,
                client = client,
                workspaceRoot = workspaceRoot
            )

            // Store new server immediately and get existing (if any) for cleanup
            // This ensures there's always a server registered, avoiding gaps
            val existingServer = serversMutex.withLock {
                val existing = activeServers[config.languageId]
                activeServers[config.languageId] = managedServer
                existing
            }

            // Stop old server after new one is registered (fire-and-forget cleanup)
            existingServer?.let { existing ->
                scope.launch {
                    stopServerInternal(existing)
                }
            }

            // Initialize the server
            val initResult = initializeServer(client, config, workspaceRoot)
            managedServer.capabilities = LanguageServerCapabilities.fromServerCapabilities(
                initResult.capabilities
            )

            emitEvent(ServerEvent.ServerStarted(config, managedServer.capabilities!!))

            // Monitor server process
            monitorServer(managedServer)

            return client

        } catch (e: Exception) {
            emitEvent(ServerEvent.ServerError(config, e.message ?: "Unknown error"))
            throw LanguageServerException("Failed to start ${config.displayName}: ${e.message}", e)
        }
    }

    /**
     * Initialize a language server with the standard LSP handshake.
     */
    private suspend fun initializeServer(
        client: DesktopLspClient,
        config: LanguageServerConfig,
        workspaceRoot: String
    ): InitializeResult {
        val rootUri = "file://$workspaceRoot"

        val initParams = DesktopLspClient.createInitializeParams(
            processId = ProcessHandle.current().pid().toInt(),
            rootUri = rootUri,
            workspaceFolders = listOf(
                WorkspaceFolder(
                    uri = rootUri,
                    name = File(workspaceRoot).name
                )
            ),
            clientName = "BossEditor",
            clientVersion = "1.0.0"
        )

        val result = client.initialize(initParams)
        client.initialized()

        return result
    }

    /**
     * Monitor a server for unexpected termination.
     */
    private fun monitorServer(server: ManagedServer) {
        scope.launch {
            while (isActive) {
                delay(HEALTH_CHECK_INTERVAL_MS)

                if (server.client.state == LspClientState.ERROR ||
                    server.client.state == LspClientState.DISCONNECTED
                ) {
                    serversMutex.withLock {
                        if (activeServers[server.config.languageId] == server) {
                            activeServers.remove(server.config.languageId)
                        }
                    }
                    emitEvent(ServerEvent.ServerStopped(server.config, unexpected = true))
                    break
                }
            }
        }
    }

    /**
     * Stop a language server by language ID.
     *
     * @param languageId The language ID of the server to stop
     */
    suspend fun stopServer(languageId: String) {
        val server = serversMutex.withLock {
            activeServers.remove(languageId)
        } ?: return

        stopServerInternal(server)
        emitEvent(ServerEvent.ServerStopped(server.config, unexpected = false))
    }

    /**
     * Stop a server internally.
     */
    private suspend fun stopServerInternal(server: ManagedServer) {
        try {
            withTimeout(SHUTDOWN_TIMEOUT_MS) {
                server.client.shutdown()
                server.client.exit()
            }
        } catch (e: Exception) {
            // Force dispose on timeout or error
        } finally {
            server.client.dispose()
        }
    }

    /**
     * Stop all running language servers.
     */
    suspend fun stopAll() {
        val servers = serversMutex.withLock {
            val copy = activeServers.values.toList()
            activeServers.clear()
            copy
        }

        servers.forEach { server ->
            try {
                stopServerInternal(server)
                emitEvent(ServerEvent.ServerStopped(server.config, unexpected = false))
            } catch (e: Exception) {
                // Log but continue
                logger.warn(
                    LogCategory.SERVER,
                    "Error stopping server",
                    languageId = server.config.languageId,
                    error = e
                )
            }
        }
    }

    /**
     * Get the client for a language if a server is running.
     *
     * @param languageId The language ID
     * @return The client, or null if no server is running
     */
    suspend fun getRunningServer(languageId: String): LspClient? {
        return serversMutex.withLock {
            activeServers[languageId]?.takeIf { it.isRunning() }?.client
        }
    }

    /**
     * Get all running server instances.
     *
     * @return List of running server instances
     */
    suspend fun getRunningServers(): List<LanguageServerInstance> {
        return serversMutex.withLock {
            activeServers.values.map { it.toInstance() }
        }
    }

    /**
     * Check if a server is running for a language.
     *
     * @param languageId The language ID
     * @return true if a server is running
     */
    suspend fun isServerRunning(languageId: String): Boolean {
        return serversMutex.withLock {
            activeServers[languageId]?.isRunning() == true
        }
    }

    /**
     * Restart a server.
     *
     * @param languageId The language ID of the server to restart
     */
    suspend fun restartServer(languageId: String) {
        val server = serversMutex.withLock {
            activeServers[languageId]
        } ?: return

        val workspaceRoot = server.workspaceRoot
        val config = server.config

        stopServer(languageId)
        delay(RESTART_PAUSE_MS)
        getOrStartServer(config, workspaceRoot)
    }

    /**
     * Get server discovery utility.
     */
    fun getServerDiscovery(): ServerDiscovery = serverDiscovery

    /**
     * Emit a server event.
     */
    private fun emitEvent(event: ServerEvent) {
        _serverEvents.tryEmit(event)
    }

    /**
     * Dispose of the manager and stop all servers.
     * Uses fire-and-forget coroutine to avoid blocking the UI thread.
     */
    fun dispose() {
        scope.cancel()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                stopAll()
            } catch (e: Exception) {
                logger.error(LogCategory.SERVER, "Dispose error", error = e)
            }
        }
    }
}

/**
 * Wrapper for a managed language server instance.
 */
private class ManagedServer(
    val config: LanguageServerConfig,
    val client: DesktopLspClient,
    val workspaceRoot: String
) {
    var capabilities: LanguageServerCapabilities? = null

    fun isRunning(): Boolean {
        return client.state == LspClientState.INITIALIZED
    }

    fun toInstance(): LanguageServerInstance {
        return LanguageServerInstance(
            config = config,
            state = when (client.state) {
                LspClientState.DISCONNECTED -> LanguageServerState.STOPPED
                LspClientState.CONNECTING -> LanguageServerState.STARTING
                LspClientState.CONNECTED -> LanguageServerState.STARTING
                LspClientState.INITIALIZING -> LanguageServerState.INITIALIZING
                LspClientState.INITIALIZED -> LanguageServerState.RUNNING
                LspClientState.SHUTTING_DOWN -> LanguageServerState.STOPPING
                LspClientState.ERROR -> LanguageServerState.ERROR
            },
            capabilities = capabilities,
            workspaceRoot = workspaceRoot
        )
    }
}

/**
 * Events emitted by the language server manager.
 */
sealed class ServerEvent {
    abstract val config: LanguageServerConfig

    /**
     * Server is starting.
     */
    data class ServerStarting(
        override val config: LanguageServerConfig
    ) : ServerEvent()

    /**
     * Server has started successfully.
     */
    data class ServerStarted(
        override val config: LanguageServerConfig,
        val capabilities: LanguageServerCapabilities
    ) : ServerEvent()

    /**
     * Server has stopped.
     */
    data class ServerStopped(
        override val config: LanguageServerConfig,
        val unexpected: Boolean
    ) : ServerEvent()

    /**
     * Server encountered an error.
     */
    data class ServerError(
        override val config: LanguageServerConfig,
        val message: String
    ) : ServerEvent()

    /**
     * Server command not found.
     */
    data class ServerUnavailable(
        override val config: LanguageServerConfig,
        val message: String
    ) : ServerEvent()
}

/**
 * Exception thrown when a language server operation fails.
 */
class LanguageServerException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
