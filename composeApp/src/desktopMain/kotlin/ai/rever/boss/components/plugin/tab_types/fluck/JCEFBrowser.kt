package ai.rever.boss.components.plugin.tab_types.fluck

import me.friwi.jcefmaven.CefAppBuilder
import me.friwi.jcefmaven.CefInitializationException
import me.friwi.jcefmaven.MavenCefAppHandlerAdapter
import me.friwi.jcefmaven.UnsupportedPlatformException
import org.cef.CefApp
import org.cef.CefClient
import org.cef.browser.CefBrowser
import org.cef.browser.CefMessageRouter
import org.cef.handler.CefLoadHandler
import org.cef.handler.CefLoadHandlerAdapter
import java.awt.Component
import java.io.File
import java.io.IOException
import javax.swing.JPanel
import javax.swing.SwingUtilities

object JCEFBrowser {
    private var cefApp: CefApp? = null
    private var initialized = false
    private val initializationListeners = mutableListOf<(Boolean, String?) -> Unit>()
    
    @Synchronized
    fun initialize(onComplete: (Boolean, String?) -> Unit) {
        if (initialized) {
            onComplete(true, null)
            return
        }
        
        initializationListeners.add(onComplete)
        
        if (cefApp != null) {
            // Already initializing
            return
        }
        
        Thread {
            try {
                val builder = CefAppBuilder()
                builder.setInstallDir(File(System.getProperty("user.home"), ".jcef"))
                builder.setProgressHandler { state, percent ->
                    when (state) {
                        me.friwi.jcefmaven.EnumProgress.DOWNLOADING -> {
                            println("Downloading JCEF: ${percent.toInt()}%")
                        }
                        me.friwi.jcefmaven.EnumProgress.EXTRACTING -> {
                            println("Extracting JCEF: ${percent.toInt()}%")
                        }
                        me.friwi.jcefmaven.EnumProgress.LOCATING -> {
                            println("Locating JCEF...")
                        }
                        me.friwi.jcefmaven.EnumProgress.INITIALIZING -> {
                            println("Initializing JCEF...")
                        }
                        me.friwi.jcefmaven.EnumProgress.INITIALIZED -> {
                            println("JCEF initialized successfully")
                        }
                        me.friwi.jcefmaven.EnumProgress.INSTALL -> {
                            println("Installing JCEF...")
                        }
                    }
                }
                
                // Configure CEF settings
                val settings = builder.getCefSettings()
                settings.windowless_rendering_enabled = false
                settings.cache_path = File(System.getProperty("user.home"), ".jcef/cache").absolutePath
                settings.remote_debugging_port = 9222
                settings.command_line_args_disabled = false
                
                // Add command line switches for better rendering
                builder.addJcefArgs("--disable-gpu-sandbox")
                builder.addJcefArgs("--disable-software-rasterizer")
                builder.addJcefArgs("--disable-gpu")
                builder.addJcefArgs("--enable-logging")
                builder.addJcefArgs("--v=1")
                
                // Add app handler
                builder.setAppHandler(object : MavenCefAppHandlerAdapter() {
                    override fun stateHasChanged(state: CefApp.CefAppState) {
                        if (state == CefApp.CefAppState.TERMINATED) {
                            cefApp = null
                            initialized = false
                        }
                    }
                })
                
                // Build and get CefApp instance
                cefApp = builder.build()
                
                // Note: The CefAppBuilder handles message loop internally
                
                // Ensure JCEF is fully initialized before notifying listeners
                println("Waiting for JCEF to be fully ready...")
                Thread.sleep(2000) // Give JCEF time to fully initialize
                
                SwingUtilities.invokeLater {
                    initialized = true
                    println("JCEF initialization complete, notifying listeners")
                    initializationListeners.forEach { it(true, null) }
                    initializationListeners.clear()
                }
                
            } catch (e: IOException) {
                val error = "Failed to initialize JCEF: ${e.message}"
                e.printStackTrace()
                SwingUtilities.invokeLater {
                    initializationListeners.forEach { it(false, error) }
                    initializationListeners.clear()
                }
            } catch (e: UnsupportedPlatformException) {
                val error = "Unsupported platform: ${e.message}"
                e.printStackTrace()
                SwingUtilities.invokeLater {
                    initializationListeners.forEach { it(false, error) }
                    initializationListeners.clear()
                }
            } catch (e: InterruptedException) {
                val error = "Initialization interrupted: ${e.message}"
                e.printStackTrace()
                SwingUtilities.invokeLater {
                    initializationListeners.forEach { it(false, error) }
                    initializationListeners.clear()
                }
            } catch (e: CefInitializationException) {
                val error = "CEF initialization failed: ${e.message}"
                e.printStackTrace()
                SwingUtilities.invokeLater {
                    initializationListeners.forEach { it(false, error) }
                    initializationListeners.clear()
                }
            } catch (e: Exception) {
                val error = "Unexpected error: ${e.message}"
                e.printStackTrace()
                SwingUtilities.invokeLater {
                    initializationListeners.forEach { it(false, error) }
                    initializationListeners.clear()
                }
            }
        }.start()
    }
    
    fun createBrowser(
        url: String,
        onLoadingStateChange: (Boolean, Boolean, Boolean) -> Unit = { _, _, _ -> },
        onAddressChange: (String) -> Unit = {}
    ): BrowserInstance? {
        val app = cefApp ?: return null
        
        return try {
            println("Creating CefClient...")
            val client = app.createClient()
            
            // Add lifecycle handler BEFORE creating browser
            client.addLifeSpanHandler(object : org.cef.handler.CefLifeSpanHandlerAdapter() {
                override fun onAfterCreated(browser: CefBrowser?) {
                    println("*** onAfterCreated called - Browser ID: ${browser?.identifier}")
                    if (browser != null && browser.identifier != -1) {
                        // Browser is now ready with proper ID
                        SwingUtilities.invokeLater {
                            println("*** Browser ready, loading URL: $url")
                            browser.loadURL(url)
                            browser.setFocus(true)
                            browser.uiComponent?.requestFocusInWindow()
                        }
                    }
                }
                
                override fun onBeforeClose(browser: CefBrowser?) {
                    println("Browser closing: ${browser?.identifier}")
                }
            })
            
            // Add display handler to track rendering
            client.addDisplayHandler(object : org.cef.handler.CefDisplayHandlerAdapter() {
                override fun onAddressChange(browser: CefBrowser?, frame: org.cef.browser.CefFrame?, url: String?) {
                    println("Address changed to: $url")
                }
                
                override fun onTitleChange(browser: CefBrowser?, title: String?) {
                    println("Title changed to: $title")
                }
            })
            
            // Add focus handler
            client.addFocusHandler(object : org.cef.handler.CefFocusHandlerAdapter() {
                override fun onGotFocus(browser: CefBrowser?) {
                    println("Browser got focus")
                }
                
                override fun onTakeFocus(browser: CefBrowser?, next: Boolean) {
                    println("Browser take focus: next=$next")
                }
                
                override fun onSetFocus(browser: CefBrowser?, source: org.cef.handler.CefFocusHandler.FocusSource?): Boolean {
                    println("Browser set focus: source=$source")
                    return false // Allow focus
                }
            })
            
            // Add load handler
            client.addLoadHandler(object : CefLoadHandlerAdapter() {
                override fun onLoadingStateChange(
                    browser: CefBrowser?,
                    isLoading: Boolean,
                    canGoBack: Boolean,
                    canGoForward: Boolean
                ) {
                    println("Loading state changed: loading=$isLoading, canGoBack=$canGoBack, canGoForward=$canGoForward")
                    SwingUtilities.invokeLater {
                        onLoadingStateChange(isLoading, canGoBack, canGoForward)
                    }
                }
                
                override fun onLoadStart(browser: CefBrowser?, frame: org.cef.browser.CefFrame?, transitionType: org.cef.network.CefRequest.TransitionType?) {
                    println("Load started for URL: ${browser?.url}")
                    println("Browser ID: ${browser?.identifier}, Frame: ${frame?.identifier}")
                }
                
                override fun onLoadEnd(browser: CefBrowser?, frame: org.cef.browser.CefFrame?, httpStatusCode: Int) {
                    println("Load ended with status: $httpStatusCode, URL: ${browser?.url}")
                    println("Browser size: ${browser?.uiComponent?.size}")
                    println("Is browser showing: ${browser?.uiComponent?.isShowing}")
                    browser?.url?.let { currentUrl ->
                        SwingUtilities.invokeLater {
                            onAddressChange(currentUrl)
                        }
                    }
                }
                
                override fun onLoadError(
                    browser: CefBrowser?,
                    frame: org.cef.browser.CefFrame?,
                    errorCode: org.cef.handler.CefLoadHandler.ErrorCode?,
                    errorText: String?,
                    failedUrl: String?
                ) {
                    println("Load error: $errorCode - $errorText for URL: $failedUrl")
                }
            })
            
            // Create message router for JS integration if needed
            val msgRouter = CefMessageRouter.create()
            client.addMessageRouter(msgRouter)
            
            // Create browser with on-screen rendering
            println("Creating browser with URL: $url")
            
            // Create browser - ID will be -1 until it's added to a visible window
            val browser = client.createBrowser(url, false, false)
            
            println("Browser created - initial ID: ${browser.identifier} (this is normal)")
            
            // The browser will get a proper ID when added to a visible component
            // This happens in the lifecycle handler onAfterCreated
            
            BrowserInstance(client, browser, msgRouter)
        } catch (e: Exception) {
            println("Error creating browser: ${e.message}")
            e.printStackTrace()
            null
        }
    }
    
    fun shutdown() {
        cefApp?.dispose()
        cefApp = null
        initialized = false
    }
    
    data class BrowserInstance(
        val client: CefClient,
        val browser: CefBrowser,
        val messageRouter: CefMessageRouter
    ) {
        val panel: JCEFPanel by lazy { JCEFPanel(browser) }
        val component: Component
            get() = panel
        
        init {
            // Ensure browser is visible and focused
            SwingUtilities.invokeLater {
                // Access panel to trigger initialization
                panel.isVisible = true
                panel.requestFocusInWindow()
                
                // Check browser state
                println("BrowserInstance init - Browser ID: ${browser.identifier}")
                println("Browser URL: ${browser.url}")
                println("Is browser loading: ${browser.isLoading}")
            }
        }
        
        fun loadURL(url: String) {
            println("loadURL called with: $url")
            if (browser.identifier != -1) {
                println("Browser has valid ID, loading URL directly")
                browser.loadURL(url)
            } else {
                println("Browser ID is -1, queueing URL load")
                // If browser isn't ready yet, it will be loaded in onAfterCreated
            }
            SwingUtilities.invokeLater {
                println("Browser state - URL: ${browser.url}, loading: ${browser.isLoading}, ID: ${browser.identifier}")
            }
        }
        
        fun goBack() {
            browser.goBack()
        }
        
        fun goForward() {
            browser.goForward()
        }
        
        fun reload() {
            browser.reload()
        }
        
        fun forceRefresh() {
            SwingUtilities.invokeLater {
                panel.forceRefresh()
                browser.reload()
            }
        }
        
        fun dispose() {
            browser.close(true)
            client.dispose()
        }
    }
}