package ai.rever.boss.plugin.browser

import ai.rever.boss.components.plugin.tab_types.fluck.FluckEngine
import ai.rever.boss.utils.logging.BossLogger
import ai.rever.boss.utils.logging.LogCategory
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.teamdev.jxbrowser.browser.Browser
import com.teamdev.jxbrowser.browser.event.BrowserClosed
import com.teamdev.jxbrowser.browser.event.TitleChanged
import com.teamdev.jxbrowser.event.Subscription
import com.teamdev.jxbrowser.navigation.event.NavigationFinished
import com.teamdev.jxbrowser.view.compose.BrowserView
import com.teamdev.jxbrowser.view.compose.BrowserViewState
import kotlinx.coroutines.MainScope
import java.awt.Window
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Desktop implementation of [BrowserHandle] that wraps a JxBrowser [Browser] instance.
 *
 * @param browser The underlying JxBrowser Browser instance
 * @param config The configuration used to create this browser
 * @param engineGeneration The engine generation at the time this browser was created
 */
internal class BrowserHandleImpl(
    private val browser: Browser,
    private val config: BrowserConfig,
    private val engineGeneration: Long
) : BrowserHandle {

    private val logger = BossLogger.forComponent("BrowserHandleImpl")

    override val id: String = UUID.randomUUID().toString()

    private var _disposed = false
    private val subscriptions = mutableListOf<Subscription>()

    private val navigationListeners = CopyOnWriteArrayList<(String) -> Unit>()
    private val titleListeners = CopyOnWriteArrayList<(String) -> Unit>()
    private val faviconListeners = CopyOnWriteArrayList<(String?) -> Unit>()

    // BrowserViewState for Compose rendering - managed per Content() call
    private var currentViewState: BrowserViewState? = null

    init {
        setupEventListeners()
        setupBrowserHandlers()

        // Load initial URL
        if (config.url.isNotBlank()) {
            browser.navigation().loadUrl(config.url)
        }
    }

    private fun setupEventListeners() {
        // Navigation finished
        subscriptions += browser.navigation().on(NavigationFinished::class.java) { event ->
            val url = event.url()
            navigationListeners.forEach { listener ->
                try {
                    listener(url)
                } catch (e: Exception) {
                    logger.warn(LogCategory.BROWSER, "Navigation listener threw exception", error = e)
                }
            }
        }

        // Title changed
        subscriptions += browser.on(TitleChanged::class.java) { event ->
            val title = event.title()
            titleListeners.forEach { listener ->
                try {
                    listener(title)
                } catch (e: Exception) {
                    logger.warn(LogCategory.BROWSER, "Title listener threw exception", error = e)
                }
            }
        }

        // Browser closed
        subscriptions += browser.on(BrowserClosed::class.java) {
            logger.debug(LogCategory.BROWSER, "Browser closed", mapOf("handleId" to id))
            _disposed = true
        }
    }

    private fun setupBrowserHandlers() {
        // Setup download handler if enabled
        if (config.enableDownloads) {
            FluckEngine.setupBrowserDownloadHandler(browser)
        }

        // Setup keyboard interceptor for menu shortcuts
        FluckEngine.setupKeyboardInterceptor(browser)

        // Setup screen capture handler
        FluckEngine.setupCaptureSessionHandler(browser)
    }

    override val isValid: Boolean
        get() = !_disposed && !browser.isClosed &&
                FluckEngine.currentEngineGeneration == engineGeneration

    override suspend fun loadUrl(url: String) {
        if (!isValid) {
            logger.warn(LogCategory.BROWSER, "Cannot load URL - browser invalid", mapOf("handleId" to id))
            return
        }
        browser.navigation().loadUrl(url)
    }

    override fun getCurrentUrl(): String {
        if (!isValid) return ""
        return browser.url()
    }

    override fun getTitle(): String {
        if (!isValid) return ""
        return browser.title()
    }

    override fun addNavigationListener(listener: (String) -> Unit) {
        navigationListeners.add(listener)
    }

    override fun removeNavigationListener(listener: (String) -> Unit) {
        navigationListeners.remove(listener)
    }

    override fun addTitleListener(listener: (String) -> Unit) {
        titleListeners.add(listener)
    }

    override fun removeTitleListener(listener: (String) -> Unit) {
        titleListeners.remove(listener)
    }

    override fun addFaviconListener(listener: (String?) -> Unit) {
        faviconListeners.add(listener)
    }

    override fun removeFaviconListener(listener: (String?) -> Unit) {
        faviconListeners.remove(listener)
    }

    override fun goBack() {
        if (isValid && browser.navigation().canGoBack()) {
            browser.navigation().goBack()
        }
    }

    override fun goForward() {
        if (isValid && browser.navigation().canGoForward()) {
            browser.navigation().goForward()
        }
    }

    override fun reload() {
        if (isValid) {
            browser.navigation().reload()
        }
    }

    override fun canGoBack(): Boolean {
        return isValid && browser.navigation().canGoBack()
    }

    override fun canGoForward(): Boolean {
        return isValid && browser.navigation().canGoForward()
    }

    @Composable
    override fun Content() {
        if (!isValid) {
            // Show nothing if browser is invalid
            return
        }

        // Create BrowserViewState on first composition
        var viewState by remember { mutableStateOf<BrowserViewState?>(null) }

        DisposableEffect(browser) {
            // Find a valid window to associate with the BrowserView
            val awtWindow = Window.getWindows()
                .firstOrNull { window ->
                    try {
                        window.isDisplayable && window.isShowing
                    } catch (e: Exception) {
                        false
                    }
                }

            if (awtWindow != null) {
                try {
                    val newState = BrowserViewState(browser, MainScope(), awtWindow)
                    viewState = newState
                    currentViewState = newState
                } catch (e: Exception) {
                    logger.warn(LogCategory.BROWSER, "Failed to create BrowserViewState", error = e)
                }
            } else {
                logger.warn(LogCategory.BROWSER, "No valid window available for BrowserViewState")
            }

            onDispose {
                viewState?.close()
                viewState = null
                currentViewState = null
            }
        }

        // Render the browser view if available
        viewState?.let { state ->
            BrowserView(
                state = state,
                modifier = Modifier.fillMaxSize()
            )
        }
    }

    override fun dispose() {
        if (_disposed) return

        _disposed = true

        // Unsubscribe from all events
        subscriptions.forEach { it.unsubscribe() }
        subscriptions.clear()

        // Clear listeners
        navigationListeners.clear()
        titleListeners.clear()
        faviconListeners.clear()

        // Close browser view state
        currentViewState?.close()
        currentViewState = null

        // Close browser
        if (!browser.isClosed) {
            browser.close()
        }

        logger.debug(LogCategory.BROWSER, "Browser handle disposed", mapOf("handleId" to id))
    }
}
