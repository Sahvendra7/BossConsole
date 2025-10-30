package ai.rever.boss

import ai.rever.boss.utils.DeepLinkHandler
import ai.rever.boss.services.passkey.PasskeyPlatformInit
import ai.rever.boss.window.WindowManager
import ai.rever.boss.window.BossWindow
import ai.rever.boss.components.plugin.panels.bottom.console.GlobalLogCapture
import androidx.compose.runtime.key
import androidx.compose.ui.window.application
import java.io.File

fun main(args: Array<String>) {
    // Set up proper temp directories for native libraries
    setupNativeLibraryPaths()
    
    // Initialize deep link handler
    DeepLinkHandler
    
    // Process command line arguments for deep links (Windows)
    DeepLinkHandler.processCommandLineArgs(args)
    
    // Initialize passkey service for desktop platforms
    PasskeyPlatformInit.initialize()

    // Start global log capture from app startup
    GlobalLogCapture.start()

    // Debug: Check environment variables
    println("=== Checking LLM API Keys in Environment ===")
    println("Current working directory: ${System.getProperty("user.dir")}")
    println("Java version: ${System.getProperty("java.version")}")
    println("OS: ${System.getProperty("os.name")} ${System.getProperty("os.version")}")
    
    val apiKeys = mapOf(
        "ANTHROPIC_API_KEY" to System.getenv("ANTHROPIC_API_KEY"),
        "OPENAI_API_KEY" to System.getenv("OPENAI_API_KEY"),
        "TOGETHER_API_KEY" to System.getenv("TOGETHER_API_KEY"),
        "CUSTOM_LLM_API_KEY" to System.getenv("CUSTOM_LLM_API_KEY")
    )
    
    apiKeys.forEach { (key, value) ->
        if (value != null) {
            println("$key = ${value.take(10)}...${if (value.length > 10) " (${value.length} chars)" else ""}")
        } else {
            println("$key = (not set)")
        }
    }
    
    // Check all environment variables starting with certain patterns
    println("\n=== All ENV vars containing 'ANTHROPIC' or 'CLAUDE' ===")
    System.getenv().filterKeys { 
        it.contains("ANTHROPIC", ignoreCase = true) || 
        it.contains("CLAUDE", ignoreCase = true) 
    }.forEach { (key, value) ->
        println("$key = ${value.take(20)}...${if (value.length > 20) " (truncated)" else ""}")
    }
    
    println("===========================================")

    // Create initial window BEFORE application{} to prevent auto-recreation
    // This runs once on startup, not during recomposition
    WindowManager.createNewWindow()

    application {
        // Render each window with stable identity via key()
        // This prevents re-composition of existing windows when new windows are added
        //
        // IMPORTANT: No auto-creation logic here!
        // When all windows close, app stays running (standard macOS behavior)
        // User can create new windows via UI elements (+ button, File menu, etc.)
        WindowManager.windows.forEach { windowState ->
            key(windowState.id) {
                BossWindow(
                    windowState = windowState,
                    onCloseRequest = {
                        WindowManager.closeWindow(windowState.id)
                        ai.rever.boss.utils.WindowFocusManager.unregisterWindow(windowState.id)
                        // Don't call exitApplication - keep app running (macOS style)
                        // When window count reaches 0, app stays in Dock
                        // User can quit via Cmd+Q or right-click Dock → Quit
                    }
                )
            }
        }
    }
}

private fun setupNativeLibraryPaths() {
    // Ensure temp directories exist and are set properly
    val userHome = System.getProperty("user.home")
    val bossDir = File(userHome, ".boss")
    val tempDir = File(bossDir, "temp")
    val pty4jDir = File(tempDir, "pty4j")
    
    // Create directories if they don't exist
    bossDir.mkdirs()
    tempDir.mkdirs()
    pty4jDir.mkdirs()
    
    // Extract PTY4J native libraries from classpath if needed
    extractPty4jNatives(pty4jDir)
    
    // Set system properties for native libraries
    System.setProperty("pty4j.tmpdir", pty4jDir.absolutePath)
    System.setProperty("pty4j.preferred.native.folder", pty4jDir.absolutePath)
    
    // Check if we're running from an app bundle
    val appPath = System.getProperty("java.home")
    if (appPath.contains(".app")) {
        // We're in an app bundle, check for bundled natives
        val bundledNatives = File(appPath, "../../app/pty4j-native")
        if (bundledNatives.exists()) {
            System.setProperty("pty4j.preferred.native.folder", bundledNatives.absolutePath)
            println("Using bundled PTY4J natives: ${bundledNatives.absolutePath}")
        }
    }
    
    // Also set java.io.tmpdir to a proper location
    if (!System.getProperty("java.io.tmpdir").startsWith(userHome)) {
        System.setProperty("java.io.tmpdir", tempDir.absolutePath)
    }
}

private fun extractPty4jNatives(targetDir: File) {
    try {
        // Create platform-specific directory
        val platformDir = File(targetDir, "darwin")
        if (!platformDir.exists()) {
            platformDir.mkdirs()
        }
        
        // Check if libpty.dylib already exists
        val libptyFile = File(platformDir, "libpty.dylib")
        if (libptyFile.exists() && libptyFile.length() > 0) {
            println("PTY4J natives already extracted")
            return
        }
        
        // Find PTY4J jar in classpath
        val classLoader = Thread.currentThread().contextClassLoader
        
        // Search for native resources - PTY4J stores them under resources/com/pty4j/native/
        val nativeResources = listOf(
            "com/pty4j/native/darwin/libpty.dylib",
            "resources/com/pty4j/native/darwin/libpty.dylib",
            "darwin/libpty.dylib",
            "native/darwin/libpty.dylib"
        )
        
        var extracted = false
        for (resource in nativeResources) {
            try {
                val resourceStream = classLoader.getResourceAsStream(resource)
                if (resourceStream != null) {
                    resourceStream.use { input ->
                        libptyFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    libptyFile.setExecutable(true)
                    println("Extracted PTY4J native from: $resource")
                    extracted = true
                    break
                }
            } catch (e: Exception) {
                // Try next resource
            }
        }
        
        if (!extracted) {
            println("Warning: Could not extract PTY4J native libraries")
        }
    } catch (e: Exception) {
        println("Error extracting PTY4J natives: ${e.message}")
    }
}
