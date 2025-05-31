package ai.rever.boss

import BossDarkSurface
import ai.rever.boss.components.window_panel.components.main_window_panels.createBossAppContext
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.awt.Color
import java.io.File

fun main() {
    // Set up proper temp directories for native libraries
    setupNativeLibraryPaths()
    
    application {
        val windowState = rememberWindowState(
            size = DpSize(1280.dp, 800.dp) // Set larger initial window size
        )

        Window(
            onCloseRequest = ::exitApplication,
            title = "BOSS - Business Operating System Service",
            state = windowState
        ) {
            window.background = Color(BossDarkSurface.value.toInt())
            window.rootPane.putClientProperty("apple.awt.fullWindowContent", true)
            window.rootPane.putClientProperty("apple.awt.transparentTitleBar", true)
            window.rootPane.putClientProperty("apple.awt.windowTitleVisible", false)

            with(createBossAppContext) {
                BossApp()
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
        e.printStackTrace()
    }
}