package ai.rever.boss.utils

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.awt.Desktop
import java.net.URI

actual object DeepLinkHandler {
    private val _deepLinkFlow = MutableStateFlow<String?>(null)
    actual val deepLinkFlow: StateFlow<String?> = _deepLinkFlow
    
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
                    _deepLinkFlow.value = uri
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
                        _deepLinkFlow.value = uri
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
                    _deepLinkFlow.value = uri
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
        _deepLinkFlow.value = uri
    }
    
    actual fun clearDeepLink() {
        _deepLinkFlow.value = null
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