package ai.rever.boss.components.plugin.tab_types.fluck

import ai.rever.boss.config.JxBrowserConfig
import com.teamdev.jxbrowser.engine.Engine
import com.teamdev.jxbrowser.engine.EngineOptions
import com.teamdev.jxbrowser.permission.PermissionType
import com.teamdev.jxbrowser.permission.callback.RequestPermissionCallback
import java.nio.file.Paths

// Singleton engine for all browser tabs
object FluckEngine {
    private var _engine: Engine? = null
    private var initializationError: Throwable? = null
    private var attemptCount = 0
    
    // Expose current engine instance for shutdown purposes
    val currentEngine: Engine? 
        get() = _engine
    
    val engine: Engine
        get() {
            // Return cached engine if available
            _engine?.let { return it }
            
            // Throw cached error if initialization failed before and we've tried too many times
            if (attemptCount > 3) {
                initializationError?.let { throw it }
            }
            
            // Try to initialize
            return initializeEngine()
        }
    
    private fun initializeEngine(): Engine {
        return try {
            attemptCount++
            
            // Get user's home directory dynamically
            val userHome = System.getProperty("user.home")
            val chromiumDir = Paths.get(userHome, ".boss", "jxbrowser-chromium")
            
            // Use the selected profile
            val selectedProfile = BrowserSettings.currentProfile
            val profileDirPath = Paths.get(userHome, ".boss", selectedProfile)
            
            // Create directories if they don't exist
            chromiumDir.toFile().mkdirs()
            profileDirPath.toFile().mkdirs()
            
            val optionsBuilder = EngineOptions.newBuilder(JxBrowserConfig.renderingMode)
                .licenseKey(JxBrowserConfig.licenseKey)
                .chromiumDir(chromiumDir)
                .userDataDir(profileDirPath)
            
            // Add user agent if configured
            BrowserSettings.userAgent?.let { ua ->
                val userAgentMapping = mapOf(
                    "Chrome" to "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                    "Firefox" to "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.15; rv:121.0) Gecko/20100101 Firefox/121.0",
                    "Safari" to "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.1 Safari/605.1.15",
                    "Edge" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36 Edg/120.0.0.0"
                )
                
                val userAgentString = when (ua) {
                    "Default" -> null
                    "Chrome", "Firefox", "Safari", "Edge" -> userAgentMapping[ua]
                    "Custom" -> BrowserSettings.customUserAgent
                    else -> ua
                }
                
                userAgentString?.let {
                    optionsBuilder.userAgent(it)
                }
            }
            
            val newEngine = Engine.newInstance(optionsBuilder.build())
            
            // Set up permission handlers for the engine
            setupPermissionHandlers(newEngine)
            
            _engine = newEngine
            
            println("JxBrowser initialized with profile: $selectedProfile")
            
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

    private fun setupPermissionHandlers(engine: Engine) {
        // Set up permission handler for all browsers created from this engine
        val profile = engine.profiles().defaultProfile()
        val permissions = profile.permissions()
        
        permissions.set(RequestPermissionCallback::class.java, object : RequestPermissionCallback {
            override fun on(params: RequestPermissionCallback.Params, action: RequestPermissionCallback.Action) {
                val permissionType = params.permissionType()

                // Auto-grant camera and microphone permissions for video conferencing
                when (permissionType) {
                    PermissionType.VIDEO_CAPTURE -> {
                        action.grant()
                    }
                    PermissionType.AUDIO_CAPTURE -> {
                        action.grant()
                    }
                    PermissionType.NOTIFICATIONS -> {
                        action.grant()
                    }
                    else -> {
                        // For other permissions, auto-grant as well
                        action.grant()
                    }
                }
            }
        })
    }
}