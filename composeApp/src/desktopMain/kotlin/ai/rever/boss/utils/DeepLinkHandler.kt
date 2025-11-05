package ai.rever.boss.utils

import ai.rever.boss.services.URLHandlerService
import ai.rever.boss.components.plugin.panels.left_top.Project
import ai.rever.boss.components.plugin.panels.left_top.ProjectState
import ai.rever.boss.components.plugin.panels.left_top.CodeBaseInfo
import ai.rever.boss.components.events.PanelEventBus
import ai.rever.boss.components.registery.PanelId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.awt.Desktop
import java.net.URI
import java.net.URLDecoder
import java.io.File

actual object DeepLinkHandler {
    private val _deepLinkFlow = MutableStateFlow<String?>(null)
    actual val deepLinkFlow: StateFlow<String?> = _deepLinkFlow

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val isWindows = System.getProperty("os.name").lowercase().contains("windows")
    private val isMacOS = System.getProperty("os.name").lowercase().contains("mac")

    init {
        setupPlatformHandler()
    }
    
    private fun setupPlatformHandler() {
        when {
            isMacOS -> setupMacOSHandler()
            isWindows -> setupWindowsHandler()
            else -> setupDefaultHandler()
        }
    }
    
    private fun setupMacOSHandler() {
        // macOS uses Desktop.setOpenURIHandler which works well
        if (Desktop.isDesktopSupported()) {
            try {
                Desktop.getDesktop().setOpenURIHandler { event ->
                    val uri = event.uri.toString()
                    println("Received deep link (macOS): $uri")

                    // Handle http/https URLs for default browser functionality
                    if (uri.startsWith("http://") || uri.startsWith("https://")) {
                        println("Handling as HTTP(S) URL")
                        URLHandlerService.handleURL(uri)
                    } else {
                        // Handle boss:// deep links for auth
                        _deepLinkFlow.value = uri
                    }
                }
                println("macOS deep link handler registered successfully")
            } catch (e: Exception) {
                println("Failed to set up macOS deep link handler: ${e.message}")
            }
        }
    }
    
    private fun setupWindowsHandler() {
        // Windows requires registry setup and command line argument handling
        try {
            // Register protocol if not already registered
            if (!WindowsProtocolHandler.isProtocolRegistered()) {
                println("Registering Windows protocol handler...")
                WindowsProtocolHandler.registerProtocol()
            } else {
                println("Windows protocol handler already registered")
            }
            
            // On Windows, deep links come through command line args when the app is already running
            // For new instances, we need to check args in main()
            if (Desktop.isDesktopSupported()) {
                // This might not work on all Windows versions, but try it
                try {
                    Desktop.getDesktop().setOpenURIHandler { event ->
                        val uri = event.uri.toString()
                        println("Received deep link (Windows via Desktop): $uri")

                        // Handle http/https URLs for default browser functionality
                        if (uri.startsWith("http://") || uri.startsWith("https://")) {
                            println("Handling as HTTP(S) URL")
                            URLHandlerService.handleURL(uri)
                        } else {
                            // Handle boss:// deep links for auth
                            _deepLinkFlow.value = uri
                        }
                    }
                } catch (e: Exception) {
                    println("Desktop.setOpenURIHandler not supported on Windows: ${e.message}")
                }
            }
        } catch (e: Exception) {
            println("Failed to set up Windows deep link handler: ${e.message}")
        }
    }
    
    private fun setupDefaultHandler() {
        // Linux and other platforms
        if (Desktop.isDesktopSupported()) {
            try {
                Desktop.getDesktop().setOpenURIHandler { event ->
                    val uri = event.uri.toString()
                    println("Received deep link: $uri")

                    // Handle http/https URLs for default browser functionality
                    if (uri.startsWith("http://") || uri.startsWith("https://")) {
                        println("Handling as HTTP(S) URL")
                        URLHandlerService.handleURL(uri)
                    } else {
                        // Handle boss:// deep links for auth
                        _deepLinkFlow.value = uri
                    }
                }
            } catch (e: Exception) {
                println("Failed to set up deep link handler: ${e.message}")
            }
        }
    }
    
    /**
     * Process command line arguments for deep links (needed for Windows)
     */
    fun processCommandLineArgs(args: Array<String>) {
        if (isWindows) {
            WindowsProtocolHandler.extractDeepLinkFromArgs(args)?.let { url ->
                println("Received deep link from command line: $url")
                processDeepLink(url)
            }
        }
    }
    
    actual fun processDeepLink(uri: String) {
        println("DeepLinkHandler: Processing deep link: $uri")

        when {
            uri.startsWith("boss://url") -> handleUrlLink(uri)
            uri.startsWith("boss://workspace") -> handleWorkspaceLink(uri)
            uri.startsWith("boss://file") -> handleFileLink(uri)
            uri.startsWith("boss://terminal") -> handleTerminalLink(uri)
            uri.startsWith("boss://folder") -> handleFolderLink(uri)
            uri.startsWith("boss://plugin") -> handlePluginLink(uri)
            else -> {
                // Default: emit to flow for auth/other handlers
                _deepLinkFlow.value = uri
            }
        }
    }
    
    actual fun clearDeepLink() {
        _deepLinkFlow.value = null
    }

    /**
     * Handle boss://terminal deep links
     * Examples:
     *   boss://terminal
     *   boss://terminal?command=ls%20-la
     */
    private fun handleTerminalLink(uri: String) {
        println("DeepLinkHandler: Handling terminal link")

        val params = parseQueryParams(uri)
        val command = params["command"]?.urlDecode()

        // Create a CLI command and queue it
        val cliCommand = ai.rever.boss.cli.CLICommand.OpenTerminal(command)
        ai.rever.boss.cli.CLICommandHandler.getInstance().queueCommand(cliCommand)

        println("DeepLinkHandler: Terminal command queued${if (command != null) " with command: $command" else ""}")
    }

    /**
     * Handle boss://folder deep links
     * Examples:
     *   boss://folder?path=/Users/name/project
     *   boss://folder?path=/path&name=MyProject
     */
    private fun handleFolderLink(uri: String) {
        println("DeepLinkHandler: Handling folder link")

        val params = parseQueryParams(uri)
        val path = params["path"]?.urlDecode()

        if (path == null) {
            println("DeepLinkHandler: Missing 'path' parameter in folder deep link")
            return
        }

        val folder = File(path).absoluteFile

        if (!folder.exists()) {
            println("DeepLinkHandler: Folder does not exist: ${folder.absolutePath}")
            return
        }

        if (!folder.isDirectory) {
            println("DeepLinkHandler: Path is not a directory: ${folder.absolutePath}")
            return
        }

        val name = params["name"] ?: folder.name

        // Update ProjectState directly (reactive)
        scope.launch(Dispatchers.Main) {
            ProjectState.selectProject(
                Project(
                    name = name,
                    path = folder.absolutePath,
                    lastOpened = System.currentTimeMillis()
                )
            )
            println("DeepLinkHandler: Folder opened in codebase: ${folder.absolutePath}")

            // Emit panel open event to show the codebase panel
            PanelEventBus.openPanel(CodeBaseInfo.id)
            println("DeepLinkHandler: Emitted codebase panel open event")
        }
    }

    /**
     * Handle boss://plugin deep links
     * Opens any panel by its panel ID.
     * Examples:
     *   boss://plugin?id=bookmarks
     *   boss://plugin?id=terminal
     *   boss://plugin?id=secret-manager
     */
    private fun handlePluginLink(uri: String) {
        println("DeepLinkHandler: Handling plugin link")

        val params = parseQueryParams(uri)
        val panelIdStr = params["id"]?.urlDecode()

        if (panelIdStr == null) {
            println("DeepLinkHandler: Missing 'id' parameter in plugin deep link")
            return
        }

        // Emit panel open event
        scope.launch(Dispatchers.Main) {
            // Create PanelId with panelId string
            // The event handler in BossApp will look it up in the registry
            val panelId = PanelId(
                panelId = panelIdStr,
                defaultOrder = 0,  // Will be ignored, registry has real value
                pluginId = "ai.rever.boss"  // Default plugin
            )

            PanelEventBus.openPanel(panelId)
            println("DeepLinkHandler: Emitted panel open event for: $panelIdStr")
        }
    }

    /**
     * Handle boss://url deep links
     * Examples:
     *   boss://url?url=https%3A%2F%2Fexample.com
     */
    private fun handleUrlLink(uri: String) {
        println("DeepLinkHandler: Handling URL link")

        val params = parseQueryParams(uri)
        val url = params["url"]?.urlDecode()

        if (url == null) {
            println("DeepLinkHandler: Missing 'url' parameter in URL deep link")
            return
        }

        // Queue command via CLI handler
        val cliCommand = ai.rever.boss.cli.CLICommand.OpenUrl(url)
        ai.rever.boss.cli.CLICommandHandler.getInstance().queueCommand(cliCommand)

        println("DeepLinkHandler: URL command queued: $url")
    }

    /**
     * Handle boss://workspace deep links
     * Examples:
     *   boss://workspace?path=/path/to/workspace.json
     */
    private fun handleWorkspaceLink(uri: String) {
        println("DeepLinkHandler: Handling workspace link")

        val params = parseQueryParams(uri)
        val path = params["path"]?.urlDecode()

        if (path == null) {
            println("DeepLinkHandler: Missing 'path' parameter in workspace deep link")
            return
        }

        // Queue command via CLI handler
        val cliCommand = ai.rever.boss.cli.CLICommand.LoadWorkspace(path)
        ai.rever.boss.cli.CLICommandHandler.getInstance().queueCommand(cliCommand)

        println("DeepLinkHandler: Workspace command queued: $path")
    }

    /**
     * Handle boss://file deep links
     * Examples:
     *   boss://file?path=/path/to/file.kt
     */
    private fun handleFileLink(uri: String) {
        println("DeepLinkHandler: Handling file link")

        val params = parseQueryParams(uri)
        val path = params["path"]?.urlDecode()

        if (path == null) {
            println("DeepLinkHandler: Missing 'path' parameter in file deep link")
            return
        }

        // Queue command via CLI handler
        val cliCommand = ai.rever.boss.cli.CLICommand.OpenFile(path)
        ai.rever.boss.cli.CLICommandHandler.getInstance().queueCommand(cliCommand)

        println("DeepLinkHandler: File command queued: $path")
    }

    /**
     * Parse query parameters from URL
     * Example: boss://terminal?command=ls&title=test -> {command: "ls", title: "test"}
     */
    private fun parseQueryParams(uri: String): Map<String, String> {
        val query = uri.substringAfter("?", "")
        if (query.isEmpty() || query == uri) return emptyMap()

        return query.split("&")
            .mapNotNull { param ->
                val parts = param.split("=", limit = 2)
                if (parts.size == 2) parts[0] to parts[1] else null
            }
            .toMap()
    }

    /**
     * URL decode a string
     */
    private fun String.urlDecode(): String {
        return try {
            URLDecoder.decode(this, "UTF-8")
        } catch (e: Exception) {
            println("DeepLinkHandler: Error decoding URL: ${e.message}")
            this
        }
    }

    actual fun extractVerificationToken(uri: String): String? {
        // Extract token from URLs like: boss://auth/verify#access_token=xxx or boss://auth/verify?token=xxx
        return try {
            val url = URI(uri)
            
            // First try URL fragment (after #) - this is what Supabase sends
            val fragment = url.fragment
            if (fragment != null) {
                val params = fragment.split("&").associate {
                    val parts = it.split("=", limit = 2)
                    if (parts.size == 2) parts[0] to parts[1] else parts[0] to ""
                }
                // Return access_token from Supabase success redirect
                params["access_token"]?.let { return it }
            }
            
            // Fallback: try query parameters (after ?) for manual token input
            val query = url.query
            if (query != null) {
                val params = query.split("&").associate {
                    val parts = it.split("=", limit = 2)
                    if (parts.size == 2) parts[0] to parts[1] else parts[0] to ""
                }
                return params["token"]
            }
            
            null
        } catch (e: Exception) {
            println("Error extracting verification token: ${e.message}")
            null
        }
    }
    
    actual fun extractVerificationType(uri: String): String? {
        // Extract type from URLs like: boss://auth/verify#access_token=xxx&type=recovery
        return try {
            val url = URI(uri)
            
            // First try URL fragment (after #) - this is what Supabase sends
            val fragment = url.fragment
            if (fragment != null) {
                val params = fragment.split("&").associate {
                    val parts = it.split("=", limit = 2)
                    if (parts.size == 2) parts[0] to parts[1] else parts[0] to ""
                }
                params["type"]?.let { return it }
            }
            
            // Fallback: try query parameters (after ?) 
            val query = url.query
            if (query != null) {
                val params = query.split("&").associate {
                    val parts = it.split("=", limit = 2)
                    if (parts.size == 2) parts[0] to parts[1] else parts[0] to ""
                }
                return params["type"]
            }
            
            null
        } catch (e: Exception) {
            println("Error extracting verification type: ${e.message}")
            null
        }
    }
}
