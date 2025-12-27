package ai.rever.boss.components.plugin.tab_types.fluck

import ai.rever.boss.components.plugin.DefaultPlugin
import ai.rever.boss.components.registery.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arkivanov.decompose.ComponentContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Clock
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

object Fluck: TabTypeInfo {
    override val typeId = TabTypeId("fluck")
    override val displayName = "FLUCK"
    override val icon = Icons.Outlined.Language
}

// Mutable tab info for dynamic title and icon updates
data class FluckTabInfo(
    override val id: String,
    override val typeId: TabTypeId,
    private var _title: String,
    private var _icon: ImageVector = Icons.Outlined.Language,
    private var _tabIcon: TabIcon? = null,
    val url: String = "", // Initial URL
    private var _currentUrl: String = url, // Current URL being viewed
    val navigationHistory: MutableList<Pair<String, String>> = mutableListOf(), // List of (title, url) pairs
    var historyIndex: Int = -1, // Current position in navigation history
    private var _currentZoomLevel: Double = 1.0, // Current zoom level (1.0 = 100%)
    var faviconCacheKey: String? = null // Cache key for persisted favicon
) : TabInfo {
    override val title: String get() = _title
    override val icon: ImageVector get() = _icon
    override val tabIcon: TabIcon? get() = _tabIcon ?: TabIcon.Vector(_icon)
    val currentUrl: String get() = _currentUrl
    val currentZoomLevel: Double get() = _currentZoomLevel
    
    fun updateTitle(newTitle: String): FluckTabInfo {
        return copy(_title = newTitle)
    }
    
    fun updateIcon(newIcon: ImageVector): FluckTabInfo {
        return copy(_icon = newIcon, _tabIcon = TabIcon.Vector(newIcon))
    }
    
    fun updateTabIcon(newTabIcon: TabIcon): FluckTabInfo {
        return copy(_tabIcon = newTabIcon)
    }

    fun updateFaviconCacheKey(newCacheKey: String?): FluckTabInfo {
        return copy(faviconCacheKey = newCacheKey)
    }

    fun updateZoomLevel(newLevel: Double): FluckTabInfo {
        return copy(_currentZoomLevel = newLevel)
    }

    fun navigateToPage(title: String, url: String) {
        // Update current URL
        _currentUrl = url
        
        // If we're not at the end of history, truncate forward history
        if (historyIndex < navigationHistory.size - 1) {
            // Remove all entries after current index
            while (navigationHistory.size > historyIndex + 1) {
                navigationHistory.removeAt(navigationHistory.size - 1)
            }
        }
        
        // Don't add duplicate consecutive entries
        if (navigationHistory.isEmpty() || navigationHistory.lastOrNull()?.second != url) {
            navigationHistory.add(Pair(title, url))
            historyIndex = navigationHistory.size - 1
        }
    }
    
    fun navigateBack() {
        if (historyIndex > 0) {
            historyIndex--
            _currentUrl = navigationHistory[historyIndex].second
        }
    }
    
    fun navigateForward() {
        if (historyIndex < navigationHistory.size - 1) {
            historyIndex++
            _currentUrl = navigationHistory[historyIndex].second
        }
    }

}

// Platform-specific browser creation
expect fun createBrowser(): Any

// Platform-specific browser reset (clears profile, cache, cookies)
expect fun resetBrowserProfile(): Boolean

// Platform-specific browser view state creation
// Returns null if no valid window is available
expect fun createBrowserViewState(browser: Any): Any?

// Platform-specific browser disposal
expect fun disposeBrowser(browser: Any)

// Platform-specific browser view state disposal
expect fun disposeBrowserViewState(browserViewState: Any)

// Platform-specific browser state retrieval
expect fun getBrowserState(
    url: String,
    onOpenInNewTab: ((String) -> Unit)? = null
): Pair<Any, Any>?

// Platform-specific FluckTabComponent creation
expect fun createFluckTabComponent(
    config: TabInfo,
    componentContext: ComponentContext,
    onTitleUpdate: (String) -> Unit,
    onIconUpdate: (ImageVector) -> Unit,
    onTabIconUpdate: (TabIcon) -> Unit,
    onOpenInNewTab: (String) -> Unit,
    onNavigationUpdate: ((String, String) -> Unit)? = null,
    onFaviconCacheKeyUpdate: ((String?) -> Unit)? = null
): FluckTabComponent

open class FluckTabComponent(
    override val config: TabInfo,
    private val componentContext: ComponentContext,
    private val onTitleUpdate: (String) -> Unit,
    private val onIconUpdate: (ImageVector) -> Unit,
    private val onTabIconUpdate: (TabIcon) -> Unit,
    private val onOpenInNewTab: (String) -> Unit,
    private val onNavigationUpdate: ((String, String) -> Unit)? = null,
    private val onFaviconCacheKeyUpdate: ((String?) -> Unit)? = null
) : TabComponentWithUI, ComponentContext by componentContext {

    // Store the URL to load
    private val initialUrl = (config as? FluckTabInfo)?.url ?: "https://www.risalabs.ai"

    // Browser state will be initialized lazily in Content() - NOT during construction
    // This prevents blocking the UI thread during window initialization
    private var browserError: Throwable? = null
    private var browserState: Pair<Any, Any>? = null
    val browser: Any? get() = browserState?.first
    val browserViewState: Any? get() = browserState?.second

    // Thread-safe disposal flag using AtomicBoolean to prevent race conditions
    // between UI thread checks and IO thread disposal
    private val isDisposedAtomic = AtomicBoolean(false)

    // Read-write lock for thread-safe browser access
    // Read lock: Multiple threads can check browser state simultaneously
    // Write lock: Exclusive access during disposal
    // Internal visibility allows JxBrowserCompose to acquire read locks
    internal val browserLock = ReentrantReadWriteLock()

    // Convenience property for backward compatibility
    private val isDisposed: Boolean get() = isDisposedAtomic.get()

    // Retry mechanism for browser initialization (Issue #162)
    private var retryCount = 0
    private val maxRetries = 3
    private var retryTrigger by mutableStateOf(0)

    // Method to be overridden by platform-specific classes
    open fun reload() {
        // Default implementation does nothing
        // Platform-specific implementations will override this
    }

    open fun zoomIn() {
        // Default implementation does nothing
        // Platform-specific implementations will override this
    }

    open fun zoomOut() {
        // Default implementation does nothing
        // Platform-specific implementations will override this
    }

    open fun actualSize() {
        // Default implementation does nothing
        // Platform-specific implementations will override this
    }

    override val tabTypeInfo = Fluck

    @Composable
    override fun Content() {
        // Local Compose state to trigger recomposition when browser is ready
        // Initialized from class-level browserState which persists across tab switches
        var localBrowserState by remember(config.id) {
            mutableStateOf(this@FluckTabComponent.browserState)
        }

        // Initialize browser only once - check class-level browserState
        // Retry mechanism: LaunchedEffect re-runs when retryTrigger changes (Issue #162)
        LaunchedEffect(config.id, retryTrigger) {
            if (this@FluckTabComponent.browserState == null && browserError == null) {
                // Check if we should retry
                if (retryCount >= maxRetries) {
                    println("❌ [BrowserRetry] Max retries ($maxRetries) exhausted for tab ${config.id}")
                    browserError = Exception("Failed to initialize browser after $maxRetries attempts")
                    return@LaunchedEffect
                }

                try {
                    // Exponential backoff: 100ms, 200ms, 400ms
                    val delayMs = 100L * (1 shl retryCount)

                    if (retryCount > 0) {
                        println("🔄 [BrowserRetry] Attempt ${retryCount + 1}/$maxRetries for tab ${config.id}, waiting ${delayMs}ms")
                    }

                    kotlinx.coroutines.delay(delayMs)

                    // Pass callback to configure popup handler
                    // OAuth popups with dimensions will be real popups, regular links will be tabs
                    val state = getBrowserState(initialUrl, onOpenInNewTab)

                    if (state != null) {
                        this@FluckTabComponent.browserState = state
                        localBrowserState = state  // Update local state to trigger recomposition

                        if (retryCount > 0) {
                            println("✅ [BrowserRetry] Success on attempt ${retryCount + 1}/$maxRetries for tab ${config.id}")
                        }

                        // Reset retry count on success
                        retryCount = 0
                    } else {
                        // State creation failed, increment retry and try again
                        retryCount++
                        println("⚠️  [BrowserRetry] Failed attempt ${retryCount}/$maxRetries: Could not initialize browser - window not ready")

                        if (retryCount < maxRetries) {
                            // Trigger retry by incrementing retryTrigger
                            retryTrigger++
                        } else {
                            // Max retries reached
                            browserError = Exception("Could not initialize browser after $maxRetries attempts - window not ready")
                        }
                    }
                } catch (e: Exception) {
                    retryCount++
                    println("⚠️  [BrowserRetry] Failed attempt $retryCount/$maxRetries: ${e.message}")

                    if (retryCount < maxRetries) {
                        // Trigger retry by incrementing retryTrigger
                        retryTrigger++
                    } else {
                        // Max retries reached
                        println("❌ [BrowserRetry] Max retries exhausted for tab ${config.id}: ${e.message}")
                        browserError = e
                    }
                }
            } else if (this@FluckTabComponent.browserState != null) {
                // Browser already exists (tab switch), use it
                localBrowserState = this@FluckTabComponent.browserState
            }
        }

        if (!isDisposed) {
            when {
                browserError != null -> {
                    // Show error message instead of browser with retry/reset options (Issue #162)
                    BrowserErrorView(
                        error = browserError!!,
                        url = initialUrl,
                        retryCount = retryCount,
                        maxRetries = maxRetries,
                        onRetry = {
                            // Clear error and trigger retry
                            browserError = null
                            retryTrigger++
                        },
                        onReset = {
                            // Full reset: clear error, reset counter, clear browser state
                            browserError = null
                            retryCount = 0
                            this@FluckTabComponent.browserState = null
                            retryTrigger++
                        },
                        onResetBrowser = {
                            // Reset browser profile to fix persistent issues (Issue #340)
                            val success = resetBrowserProfile()
                            if (success) {
                                // Clear error and retry after browser reset
                                browserError = null
                                retryCount = 0
                                this@FluckTabComponent.browserState = null
                                retryTrigger++
                            }
                        }
                    )
                }
                localBrowserState != null -> {
                    val browser = localBrowserState!!.first
                    val browserViewState = localBrowserState!!.second
                    // Wrap FluckView in key() to ensure proper state isolation per browser instance
                    // This prevents URL bar state from being shared across tabs (fixes #151)
                    key(browser) {
                        FluckView(
                            fileId = config.id,
                            content = initialUrl,
                            browser = browser,
                            browserViewState = browserViewState,
                            browserLock = browserLock,
                            onContentChange = { }, // Not used for browser
                            onTitleChange = onTitleUpdate,
                            onIconChange = onIconUpdate,
                            onTabIconUpdate = onTabIconUpdate,
                            onOpenInNewTab = onOpenInNewTab,
                            onNavigationUpdate = onNavigationUpdate,
                            onNavigationStateChange = { isBack ->
                                // Handle back/forward navigation
                                if (config is FluckTabInfo) {
                                    if (isBack) {
                                        (config as? FluckTabInfo)?.navigateBack()
                                    } else {
                                        (config as? FluckTabInfo)?.navigateForward()
                                    }
                                }
                            },
                            onFaviconCached = { cacheKey ->
                                // Update favicon cache key through proper callback (Issue #160)
                                onFaviconCacheKeyUpdate?.invoke(cacheKey)
                            }
                        )
                    }
                }
                else -> {
                    // Loading state
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }
    
    fun dispose() {
        // Use compareAndSet for atomic thread-safe disposal flag update
        if (isDisposedAtomic.compareAndSet(false, true)) {
            // Dispose the browser and view state on background thread to avoid blocking UI
            // The composable's DisposableEffect and disposal guards will handle cleanup coordination
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    // First: Dispose view state with write lock (quick operation)
                    browserLock.write {
                        browserViewState?.let { disposeBrowserViewState(it) }
                    }

                    // Delay OUTSIDE the lock to prevent blocking read lock acquisitions
                    // During this delay, event handlers and RPA polling can still acquire read locks
                    // This allows JxBrowser's internal RPC queue to drain without freezing other operations
                    // Issue #255: 150ms delay prevents race condition in SharedMemoryTransport
                    delay(150)

                    // Finally: Dispose browser with write lock (ensures exclusive access for closure)
                    browserLock.write {
                        browser?.let { disposeBrowser(it) }
                    }
                } catch (e: Exception) {
                    println("Error disposing browser: ${e.message}")
                }
            }
        }
    }
}

@Composable
fun BrowserErrorView(
    error: Throwable,
    url: String,
    retryCount: Int = 0,
    maxRetries: Int = 3,
    onRetry: (() -> Unit)? = null,
    onReset: (() -> Unit)? = null,
    onResetBrowser: (() -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = 4.dp,
            backgroundColor = Color(0xFF2B2D30)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Outlined.Warning,
                    contentDescription = "Error",
                    tint = Color(0xFFFF6B6B),
                    modifier = Modifier.size(48.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Browser Not Available",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Unable to initialize the web browser component.",
                    fontSize = 14.sp,
                    color = Color(0xFFCCCCCC)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "URL: $url",
                    fontSize = 12.sp,
                    color = Color(0xFF999999)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Error: ${error.message ?: error.toString()}",
                    fontSize = 12.sp,
                    color = Color(0xFF999999),
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                // Show retry progress if retries were attempted
                if (retryCount > 0) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Attempted $retryCount/$maxRetries times with exponential backoff",
                        fontSize = 12.sp,
                        color = Color(0xFF888888),
                        fontStyle = FontStyle.Italic
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Show appropriate button based on retry status
                if (retryCount < maxRetries && onRetry != null) {
                    // Still have retries left - show Retry button
                    Button(
                        onClick = onRetry,
                        colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF4A90E2))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Retry",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Retry Loading (Attempt ${retryCount + 1}/$maxRetries)")
                    }
                } else if (retryCount >= maxRetries && onReset != null) {
                    // Max retries exhausted - show Reset Tab button
                    Button(
                        onClick = onReset,
                        colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFFE2724A))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reset",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Reset Tab")
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Resets retry counter and attempts to load the browser again",
                        fontSize = 11.sp,
                        color = Color(0xFF888888),
                        textAlign = TextAlign.Center
                    )

                    // Show Reset Browser option for persistent issues
                    if (onResetBrowser != null) {
                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "If the issue persists, try resetting the browser:",
                            fontSize = 12.sp,
                            color = Color(0xFFAAAAAA),
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Button(
                            onClick = onResetBrowser,
                            colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFFE05555))
                        ) {
                            Text("Reset Browser", color = Color.White)
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "This clears browser cache, cookies, and sessions",
                            fontSize = 10.sp,
                            color = Color(0xFF777777),
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    // No callbacks provided - show fallback message
                    Text(
                        text = "Try using the code editor or terminal tabs instead.",
                        fontSize = 14.sp,
                        color = Color(0xFFCCCCCC)
                    )
                }
            }
        }
    }
}

fun DefaultPlugin.registerFluck() = tabRegistry.registerTabType(Fluck) { tabInfo, ctx ->
    // Find the parent component
    val parentComponent = ctx as? ai.rever.boss.components.window_panel.components.main_window_panels.BossTabsComponent
    
    createFluckTabComponent(
        config = tabInfo, 
        componentContext = ctx,
        onTitleUpdate = { newTitle ->
            // Update the tab title when the page title changes
            parentComponent?.let { parent ->
                // Find the tab by ID instead of by reference
                val tabs = parent.tabsState.value.tabs
                val tabIndex = tabs.indexOfFirst { it.id == tabInfo.id }
                
                if (tabIndex >= 0) {
                    val currentTab = tabs[tabIndex]
                    if (currentTab is FluckTabInfo) {
                        // Update using the current tab info, not the original one
                        parent.updateTab(tabIndex, currentTab.updateTitle(newTitle))
                    }
                }
            }
        },
        onIconUpdate = { newIcon ->
            // Update the tab icon when the favicon is loaded
            parentComponent?.let { parent ->
                val tabs = parent.tabsState.value.tabs
                val tabIndex = tabs.indexOfFirst { it.id == tabInfo.id }
                
                if (tabIndex >= 0) {
                    val currentTab = tabs[tabIndex]
                    if (currentTab is FluckTabInfo) {
                        parent.updateTab(tabIndex, currentTab.updateIcon(newIcon))
                    }
                }
            }
        },
        onTabIconUpdate = { newTabIcon ->
            // Update the tab icon with the actual favicon
            parentComponent?.let { parent ->
                val tabs = parent.tabsState.value.tabs
                val tabIndex = tabs.indexOfFirst { it.id == tabInfo.id }
                
                if (tabIndex >= 0) {
                    val currentTab = tabs[tabIndex]
                    if (currentTab is FluckTabInfo) {
                        parent.updateTab(tabIndex, currentTab.updateTabIcon(newTabIcon))
                    }
                }
            }
        },
        onOpenInNewTab = { url ->
            // Create a new tab with the specified URL
            parentComponent?.let { parent ->
                val newTabId = "browser_${Clock.System.now().toEpochMilliseconds()}"
                val newTab = FluckTabInfo(
                    id = newTabId,
                    typeId = TabTypeId("fluck"),
                    _title = "Loading...",
                    url = url
                )
                parent.addTab(newTab)
            }
        },
        onNavigationUpdate = { title, url ->
            // Update navigation history
            parentComponent?.let { parent ->
                val tabs = parent.tabsState.value.tabs
                val tabIndex = tabs.indexOfFirst { it.id == tabInfo.id }

                if (tabIndex >= 0) {
                    val currentTab = tabs[tabIndex]
                    if (currentTab is FluckTabInfo) {
                        // Navigate to page
                        currentTab.navigateToPage(title, url)
                    }
                }
            }
        },
        onFaviconCacheKeyUpdate = { newCacheKey ->
            // Update favicon cache key (Issue #160)
            parentComponent?.let { parent ->
                val tabs = parent.tabsState.value.tabs
                val tabIndex = tabs.indexOfFirst { it.id == tabInfo.id }

                if (tabIndex >= 0) {
                    val currentTab = tabs[tabIndex]
                    if (currentTab is FluckTabInfo) {
                        parent.updateTab(tabIndex, currentTab.updateFaviconCacheKey(newCacheKey))
                    }
                }
            }
        }
    )
}
