package ai.rever.boss.cli

import ai.rever.boss.window.WindowManager
import ai.rever.boss.services.URLHandlerService
import ai.rever.boss.components.events.FileEventBus
import ai.rever.boss.components.events.TerminalEventBus
import ai.rever.boss.components.workspaces.LayoutWorkspace
import ai.rever.boss.components.workspaces.WorkspaceSerializer
import ai.rever.boss.components.workspaces.applyWorkspace
import ai.rever.boss.components.window_panel.SplitViewState
import kotlinx.coroutines.*
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Handles CLI commands with queueing for app lifecycle coordination.
 *
 * Commands may arrive before the app UI is ready, so they are queued
 * and executed once WindowManager and other services are initialized.
 *
 * Thread Safety: All UI operations use Dispatchers.Main.
 */
class CLICommandHandler private constructor() {
    private val commandQueue = ConcurrentLinkedQueue<CLICommand>()
    private val terminalQueue = ConcurrentLinkedQueue<String>()  // Use empty string as sentinel for null
    private val workspaceQueue = ConcurrentLinkedQueue<String>()
    private val fileQueue = ConcurrentLinkedQueue<String>()

    @Volatile
    private var isInitialized = false

    @Volatile
    private var isTerminalHandlerReady = false

    @Volatile
    private var isFileHandlerReady = false

    @Volatile
    private var isWorkspaceHandlerReady = false

    // Service references - set during initialization
    private var windowManager: WindowManager? = null
    private var getSplitViewState: (() -> SplitViewState?)? = null

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    companion object {
        @Volatile
        private var instance: CLICommandHandler? = null

        fun getInstance(): CLICommandHandler {
            return instance ?: synchronized(this) {
                instance ?: CLICommandHandler().also { instance = it }
            }
        }
    }

    /**
     * Register services once app is initialized.
     * Should be called from main.kt after app setup.
     */
    fun initialize(
        windowManager: WindowManager,
        getSplitViewState: () -> SplitViewState?
    ) {
        this.windowManager = windowManager
        this.getSplitViewState = getSplitViewState
        this.isInitialized = true

        println("CLI: Initialized with services")

        // Execute queued commands
        executeQueuedCommands()
    }

    /**
     * Queue a command for execution.
     * If app is ready, executes immediately.
     * Otherwise, queues for later execution.
     */
    fun queueCommand(command: CLICommand) {
        if (isInitialized) {
            scope.launch {
                executeCommand(command)
            }
        } else {
            commandQueue.offer(command)
            println("CLI: Queued command: $command")
        }
    }

    /**
     * Mark terminal handler as ready and process queued terminal events.
     * Should be called from BossApp.kt after TerminalEventBus listener is set up.
     */
    fun markTerminalHandlerReady() {
        isTerminalHandlerReady = true
        println("CLI: Terminal handler marked as ready")

        // Process queued terminal events
        scope.launch {
            while (terminalQueue.isNotEmpty()) {
                val command = terminalQueue.poll()
                if (command != null) {
                    // Convert empty string sentinel back to null
                    val actualCommand = if (command.isEmpty()) null else command
                    println("CLI: Processing queued terminal command${if (actualCommand != null) ": $actualCommand" else " (no command)"}")
                    try {
                        handleOpenTerminal(actualCommand)
                    } catch (e: Exception) {
                        println("CLI: Failed to process queued terminal event: ${e.message}")
                    }
                }
            }
        }
    }

    /**
     * Mark file handler as ready and process queued file events.
     * Should be called from BossApp.kt after FileEventBus listener is set up.
     */
    fun markFileHandlerReady() {
        isFileHandlerReady = true
        println("CLI: File handler marked as ready")

        // Process queued file events
        scope.launch {
            while (fileQueue.isNotEmpty()) {
                val filePath = fileQueue.poll()
                if (filePath != null) {
                    println("CLI: Processing queued file: $filePath")
                    try {
                        handleOpenFile(filePath)
                    } catch (e: Exception) {
                        println("CLI: Failed to process queued file event: ${e.message}")
                    }
                }
            }
        }
    }

    /**
     * Mark workspace handler as ready and process queued workspace loads.
     * Should be called from BossApp.kt after Last Session workspace is loaded.
     */
    fun markWorkspaceHandlerReady() {
        isWorkspaceHandlerReady = true
        println("CLI: Workspace handler marked as ready")

        // Process queued workspace loads
        scope.launch {
            while (workspaceQueue.isNotEmpty()) {
                val configPath = workspaceQueue.poll()
                if (configPath != null) {
                    println("CLI: Processing queued workspace: $configPath")
                    try {
                        handleLoadWorkspace(configPath)
                    } catch (e: Exception) {
                        println("CLI: Failed to process queued workspace: ${e.message}")
                    }
                }
            }
        }
    }

    private fun executeQueuedCommands() {
        scope.launch {
            // Process command queue
            while (commandQueue.isNotEmpty()) {
                val command = commandQueue.poll()
                if (command != null) {
                    executeCommand(command)
                }
            }

            // Note: Workspace queue is now processed by markWorkspaceHandlerReady()
            // This ensures workspaces load AFTER Last Session, not during app initialization
        }
    }

    private suspend fun executeCommand(command: CLICommand) {
        try {
            println("CLI: Executing command: $command")

            when (command) {
                is CLICommand.OpenUrl -> handleOpenUrl(command.url)
                is CLICommand.LoadWorkspace -> handleLoadWorkspace(command.configPath)
                is CLICommand.OpenFile -> {
                    if (isFileHandlerReady) {
                        // File handler ready - execute immediately
                        println("CLI: File handler ready, executing immediately: ${command.filePath}")
                        handleOpenFile(command.filePath)
                    } else {
                        // File handler not ready - queue for later (cold start)
                        println("CLI: File handler not ready, queueing file: ${command.filePath}")
                        fileQueue.add(command.filePath)
                    }
                }
                is CLICommand.OpenFolder -> handleOpenFolder(command.folderPath)
                is CLICommand.OpenTerminal -> {
                    val queuedCommand = command.command ?: ""  // Use empty string as sentinel for null

                    if (isTerminalHandlerReady) {
                        // Terminal handler ready - execute immediately
                        println("CLI: Terminal handler ready, executing immediately: ${if (queuedCommand.isEmpty()) "(no command)" else queuedCommand}")
                        val actualCommand = if (queuedCommand.isEmpty()) null else queuedCommand
                        handleOpenTerminal(actualCommand)
                    } else {
                        // Terminal handler not ready - queue for later (cold start)
                        println("CLI: Terminal handler not ready, queueing command: ${if (queuedCommand.isEmpty()) "(no command)" else queuedCommand}")
                        terminalQueue.add(queuedCommand)
                    }
                }
            }
        } catch (e: Exception) {
            println("CLI: Error executing command: ${e.message}")
        }
    }

    /**
     * Opens URL in Fluck browser tab.
     */
    private suspend fun handleOpenUrl(url: String) {
        // Normalize and validate URL (adds https:// if missing)
        val normalizedUrl = CLISecurityValidator.normalizeAndValidateUrl(url)
        if (normalizedUrl == null) {
            println("CLI: Invalid URL: $url")
            return
        }

        withContext(Dispatchers.Main) {
            URLHandlerService.handleURL(normalizedUrl)
        }
    }

    /**
     * Loads workspace configuration from file.
     * 
     * Emits workspace load event via WorkspaceEventBus for BossApp to handle.
     * This ensures workspace loading has access to splitViewState and workspaceManager.
     */
    private suspend fun handleLoadWorkspace(configPath: String) {
        // Validate file exists
        val file = File(configPath).absoluteFile
        if (!file.exists()) {
            println("CLI: Workspace config not found: ${file.absolutePath}")
            return
        }

        if (!file.canRead()) {
            println("CLI: Cannot read workspace config: ${file.absolutePath}")
            return
        }

        // Validate path for security (prevent path traversal)
        if (!CLISecurityValidator.isValidPath(file.absolutePath)) {
            println("CLI: Invalid workspace path (security check failed): ${file.absolutePath}")
            return
        }

        // Queue workspace if handler not ready (cold start)
        // This ensures workspace loads AFTER Last Session, preventing tab destruction
        if (!isWorkspaceHandlerReady) {
            println("CLI: Workspace handler not ready, queueing workspace: ${file.absolutePath}")
            workspaceQueue.add(file.absolutePath)
            return
        }

        // Emit workspace load event - BossApp will handle the actual loading
        // This is much simpler than trying to access splitViewState from CLI layer
        ai.rever.boss.components.events.WorkspaceEventBus.loadWorkspace(file.absolutePath)
        println("CLI: Emitted workspace load event for ${file.absolutePath}")
    }

    /**
     * Opens file in editor tab.
     * 
     * Direct emit only - queueing is handled in executeCommand().
     * This is called from markFileHandlerReady() after handler is ready.
     */
    private suspend fun handleOpenFile(filePath: String) {
        val file = File(filePath).absoluteFile

        if (!file.exists()) {
            println("CLI: File not found: ${file.absolutePath}")
            return
        }

        if (!file.isFile) {
            println("CLI: Not a file: ${file.absolutePath}")
            return
        }

        if (!file.canRead()) {
            println("CLI: Cannot read file: ${file.absolutePath}")
            return
        }

        // Security validation
        if (!CLISecurityValidator.isValidPath(file.absolutePath)) {
            println("CLI: Invalid file path (security check failed): ${file.absolutePath}")
            return
        }

        // Track file processing (prevents New Tab Dialog race condition)
        ai.rever.boss.services.FileHandlerService.incrementProcessing()

        // Emit file open event via FileEventBus
        // The active window's BossApp will listen and create the editor tab
        CoroutineScope(Dispatchers.Main).launch {
            try {
                FileEventBus.openFile(file.absolutePath)
                println("CLI: Emitted file open event for ${file.absolutePath}")

                // CRITICAL: Wait for file tab to actually be created before decrementing
                // The event emission is instant, but file tab creation is async and takes time.
                // If we decrement immediately, BossApp's state check will see 0 tabs + isProcessing=false
                // and show New Tab Dialog / load Last Session, which clears panels and destroys
                // the tab being created.
                //
                // Timeline without delay:
                // - t=0ms: Event emitted, counter decremented to 0
                // - t=0ms: File tab creation starts (async)
                // - t=200ms: BossApp debounce checks: tabs=0, isProcessing=false → shows dialog
                // - t=250ms: File tab creation completes but gets immediately destroyed
                //
                // With 500ms delay:
                // - t=0ms: Event emitted
                // - t=0ms: File tab creation starts (async)
                // - t=200ms: BossApp debounce checks: tabs=0, but isProcessing=true → waits
                // - t=250ms: File tab creation completes, tabs=1
                // - t=500ms: Counter decremented, isProcessing=false (tab already exists)
                delay(500)
            } catch (e: Exception) {
                println("CLI: Failed to emit file event: ${e.message}")
            } finally {
                // Always decrement, even on error
                ai.rever.boss.services.FileHandlerService.decrementProcessing()
            }
        }
    }

    /**
     * Opens folder in codebase plugin.
     */
    private suspend fun handleOpenFolder(folderPath: String) {
        val folder = File(folderPath).absoluteFile

        if (!folder.exists()) {
            println("CLI: Folder not found: ${folder.absolutePath}")
            return
        }

        if (!folder.isDirectory) {
            println("CLI: Not a directory: ${folder.absolutePath}")
            return
        }

        if (!folder.canRead()) {
            println("CLI: Cannot read folder: ${folder.absolutePath}")
            return
        }

        // Security validation
        if (!CLISecurityValidator.isValidPath(folder.absolutePath)) {
            println("CLI: Invalid folder path (security check failed): ${folder.absolutePath}")
            return
        }

        withContext(Dispatchers.Main) {
            // Import Project and ProjectState from CodeBase.kt
            val project = ai.rever.boss.components.plugin.panels.left_top.Project(
                name = folder.name,
                path = folder.absolutePath,
                lastOpened = System.currentTimeMillis()
            )

            ai.rever.boss.components.plugin.panels.left_top.ProjectState.selectProject(project)
            println("CLI: Folder opened in codebase plugin: ${folder.absolutePath}")
        }
    }

    /**
     * Opens terminal tab, optionally with command.
     *
     * Direct emit only - queueing is handled in executeCommand().
     * This is called from markTerminalHandlerReady() after handler is ready.
     */
    private suspend fun handleOpenTerminal(command: String?) {
        // Validate command for security if provided
        if (command != null && !CLISecurityValidator.isValidCommand(command)) {
            println("CLI: Invalid terminal command (security check failed): $command")
            return
        }

        // Track terminal processing (prevents New Tab Dialog race condition)
        ai.rever.boss.services.TerminalHandlerService.incrementProcessing()

        // Emit terminal open event via TerminalEventBus
        // The active window's BossApp will listen and create the terminal tab
        CoroutineScope(Dispatchers.Main).launch {
            try {
                TerminalEventBus.openTerminal(command)
                println("CLI: Emitted terminal open event${if (command != null) " with command: $command" else ""}")

                // CRITICAL: Wait for terminal tab to actually be created before decrementing
                // The event emission is instant, but terminal tab creation is async and takes time.
                // If we decrement immediately, BossApp's state check will see 0 tabs + isProcessing=false
                // and show New Tab Dialog / load Last Session, which clears panels and destroys
                // the tab being created.
                //
                // Timeline without delay:
                // - t=0ms: Event emitted, counter decremented to 0
                // - t=0ms: Terminal tab creation starts (async)
                // - t=200ms: BossApp debounce checks: tabs=0, isProcessing=false → shows dialog
                // - t=250ms: Terminal tab creation completes but gets immediately destroyed
                //
                // With 500ms delay:
                // - t=0ms: Event emitted
                // - t=0ms: Terminal tab creation starts (async)
                // - t=200ms: BossApp debounce checks: tabs=0, but isProcessing=true → waits
                // - t=250ms: Terminal tab creation completes, tabs=1
                // - t=500ms: Counter decremented, isProcessing=false (tab already exists)
                delay(500)
            } catch (e: Exception) {
                println("CLI: Failed to emit terminal event: ${e.message}")
            } finally {
                // Always decrement, even on error
                ai.rever.boss.services.TerminalHandlerService.decrementProcessing()
            }
        }
    }
}

/**
 * Sealed class representing CLI commands.
 */
sealed class CLICommand {
    data class OpenUrl(val url: String) : CLICommand()
    data class LoadWorkspace(val configPath: String) : CLICommand()
    data class OpenFile(val filePath: String) : CLICommand()
    data class OpenFolder(val folderPath: String) : CLICommand()
    data class OpenTerminal(val command: String?) : CLICommand()
}
