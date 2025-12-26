package ai.rever.boss

import ai.rever.boss.cli.createBossCLI
import ai.rever.boss.cli.CLICommandHandler
import ai.rever.boss.utils.DeepLinkHandler
import ai.rever.boss.utils.SingleInstanceManager
import ai.rever.boss.services.passkey.PasskeyPlatformInit
import ai.rever.boss.window.WindowManager
import ai.rever.boss.window.BossWindow
import ai.rever.boss.components.plugin.panels.bottom.console.GlobalLogCapture
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.window.application
import com.github.ajalt.clikt.core.main
import java.io.File
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    // Set WM_CLASS for Linux desktop integration (must be before any AWT init)
    setLinuxWMClass()

    // Set up proper temp directories for native libraries
    setupNativeLibraryPaths()

    // Single-instance check: ensure only one BOSS instance runs
    // On Windows, this prevents multiple windows when clicking deep links
    if (!SingleInstanceManager.acquireLock()) {
        println("Another BOSS instance is already running")

        // Check if we have a deep link or URL to send to the existing instance
        val deepLink = args.firstOrNull {
            it.startsWith("boss://") ||
            it.startsWith("http://") ||
            it.startsWith("https://")
        }

        if (deepLink != null) {
            println("Sending URL to existing instance: $deepLink")

            // Try to send with retry logic (important for auth deep links during sign-in)
            // Note: runBlocking is acceptable here as this runs during pre-UI initialization,
            // before the Compose application starts. No UI thread exists yet to block.
            var success = false
            val maxRetries = 3
            for (attempt in 1..maxRetries) {
                if (SingleInstanceManager.sendToExistingInstance(deepLink)) {
                    println("URL sent successfully on attempt $attempt. Exiting this instance.")
                    success = true
                    break
                } else {
                    println("Failed to send URL (attempt $attempt/$maxRetries)")
                    if (attempt < maxRetries) {
                        // Use coroutine delay instead of Thread.sleep to avoid blocking
                        kotlinx.coroutines.runBlocking {
                            kotlinx.coroutines.delay(500)
                        }
                    }
                }
            }

            if (success) {
                exitProcess(0)
            } else {
                // IPC failed after retries - DO NOT create new window
                // This prevents duplicate windows during sign-in
                val switchKeyHint = when {
                    System.getProperty("os.name").startsWith("Mac", ignoreCase = true) -> "Cmd+Tab"
                    else -> "Alt+Tab"
                }

                println("ERROR: Could not send URL to existing BOSS instance after $maxRetries attempts.")
                println("The existing BOSS window is still running. Please:")
                println("  1. Switch to the existing window ($switchKeyHint)")
                println("  2. Manually paste the URL if needed: $deepLink")
                println("")
                println("This prevents creating duplicate windows during authentication.")
                exitProcess(1)
            }
        } else {
            val switchKeyHint = when {
                System.getProperty("os.name").startsWith("Mac", ignoreCase = true) -> "Cmd+Tab"
                else -> "Alt+Tab"
            }
            println("No URL to send. The existing BOSS window should already be visible.")
            println("Use $switchKeyHint to switch to the existing window, or close it to start a new instance.")
            exitProcess(0)
        }
    }

    // Register shutdown hook to release the single-instance lock AND close browser engine
    Runtime.getRuntime().addShutdownHook(Thread {
        try {
            // Stop performance monitoring to cancel background coroutines
            ai.rever.boss.performance.PerformanceMonitor.stop()
        } catch (e: Exception) {
            println("Error stopping performance monitor: ${e.message}")
        }
        try {
            // Close browser engine first to release lock files
            val engine = ai.rever.boss.components.plugin.tab_types.fluck.FluckEngine.currentEngine
            if (engine != null && !engine.isClosed) {
                println("Closing browser engine...")
                engine.close()
            }
        } catch (e: Exception) {
            println("Error closing browser engine: ${e.message}")
        }
        SingleInstanceManager.release()
    })

    println("Successfully acquired single-instance lock. Starting BOSS...")

    // Parse CLI arguments if provided
    if (args.isNotEmpty()) {
        try {
            // Check if args contain deep link protocols
            val hasDeepLink = args.any {
                it.startsWith("boss://") || it.startsWith("http://") || it.startsWith("https://")
            }

            // If it's a deep link, let DeepLinkHandler process it
            // Otherwise, treat as CLI command
            if (!hasDeepLink) {
                println("CLI: Processing arguments: ${args.joinToString(" ")}")
                createBossCLI().main(args)
                // Commands are queued, continue with app initialization
            }
        } catch (e: Exception) {
            println("CLI Error: ${e.message}")
            // Don't exit - let the app start normally
            // CLI errors shouldn't prevent GUI from launching
        }
    }

    // Initialize deep link handler
    DeepLinkHandler

    // Process command line arguments for deep links (Windows)
    DeepLinkHandler.processCommandLineArgs(args)
    
    // Initialize passkey service for desktop platforms
    PasskeyPlatformInit.initialize()

    // Start global log capture from app startup
    GlobalLogCapture.start()

    // Start performance monitoring from app startup
    ai.rever.boss.performance.PerformanceMonitor.start()

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
        // Initialize CLI handler once app is running
        LaunchedEffect(Unit) {
            CLICommandHandler.getInstance().initialize(
                windowManager = WindowManager,
                getSplitViewState = {
                    // Workspace loading now handled via WorkspaceManager from BossApp
                    // No need to expose SplitViewState to CLI handler
                    null
                }
            )
        }

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

/**
 * Set WM_CLASS for proper Linux desktop integration.
 * Must be called before any windows are created.
 * Requires JVM arg: --add-opens java.desktop/sun.awt.X11=ALL-UNNAMED
 */
private fun setLinuxWMClass() {
    if (!System.getProperty("os.name").lowercase().contains("linux")) return

    try {
        // Get toolkit instance (creates it if needed)
        val toolkit = java.awt.Toolkit.getDefaultToolkit()
        if (toolkit.javaClass.name == "sun.awt.X11.XToolkit") {
            val field = toolkit.javaClass.getDeclaredField("awtAppClassName")
            field.isAccessible = true
            field.set(toolkit, "BOSS")
        }
    } catch (e: Exception) {
        System.err.println("Could not set WM_CLASS: ${e.message}")
    }
}
