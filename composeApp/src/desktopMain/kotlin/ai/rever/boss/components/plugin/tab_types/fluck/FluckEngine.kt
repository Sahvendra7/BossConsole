package ai.rever.boss.components.plugin.tab_types.fluck

import ai.rever.boss.config.JxBrowserConfig
import com.teamdev.jxbrowser.engine.Engine
import com.teamdev.jxbrowser.engine.EngineOptions
import java.nio.file.Paths

// Singleton engine for all browser tabs
object FluckEngine {
    private var _engine: Engine? = null
    private var initializationError: Throwable? = null
    
    val engine: Engine
        get() {
            // Return cached engine if available
            _engine?.let { return it }
            
            // Throw cached error if initialization failed before
            initializationError?.let { throw it }
            
            // Try to initialize
            return try {
                // Get user's home directory dynamically
                val userHome = System.getProperty("user.home")
                val chromiumDir = Paths.get(userHome, ".boss", "jxbrowser-chromium")
                val userDataDir = Paths.get(userHome, ".boss", "browser-profile")
                
                // Create directories if they don't exist
                chromiumDir.toFile().mkdirs()
                userDataDir.toFile().mkdirs()
                
                val newEngine = Engine.newInstance(
                    EngineOptions.newBuilder(JxBrowserConfig.renderingMode)
                        .licenseKey(JxBrowserConfig.licenseKey)
                        .chromiumDir(chromiumDir)
                        .userDataDir(userDataDir)
                        .build()
                )
                _engine = newEngine
                newEngine
            } catch (e: Exception) {
                println("JxBrowser initialization failed:")
                println("- Error: ${e.message}")
                println("- Type: ${e.javaClass.name}")
                println("- User home: ${System.getProperty("user.home")}")
                println("- OS: ${System.getProperty("os.name")} ${System.getProperty("os.version")}")
                println("- Arch: ${System.getProperty("os.arch")}")
                println("- Java: ${System.getProperty("java.version")}")
                e.printStackTrace()
                initializationError = e
                throw e
            }
        }
    
    fun isAvailable(): Boolean {
        return try {
            engine
            true
        } catch (e: Exception) {
            false
        }
    }
} 