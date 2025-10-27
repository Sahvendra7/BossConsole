package ai.rever.boss.components.plugin.tab_types.fluck

import ai.rever.boss.components.plugin.DefaultPlugin
import ai.rever.boss.components.registery.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arkivanov.decompose.ComponentContext
import kotlin.time.Clock

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
    var historyIndex: Int = -1 // Current position in navigation history
) : TabInfo {
    override val title: String get() = _title
    override val icon: ImageVector get() = _icon
    override val tabIcon: TabIcon? get() = _tabIcon ?: TabIcon.Vector(_icon)
    val currentUrl: String get() = _currentUrl
    
    fun updateTitle(newTitle: String): FluckTabInfo {
        return copy(_title = newTitle)
    }
    
    fun updateIcon(newIcon: ImageVector): FluckTabInfo {
        return copy(_icon = newIcon, _tabIcon = TabIcon.Vector(newIcon))
    }
    
    fun updateTabIcon(newTabIcon: TabIcon): FluckTabInfo {
        return copy(_tabIcon = newTabIcon)
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

// Platform-specific browser view state creation
// Returns null if no valid window is available
expect fun createBrowserViewState(browser: Any): Any?

// Platform-specific browser disposal
expect fun disposeBrowser(browser: Any)

// Platform-specific browser view state disposal
expect fun disposeBrowserViewState(browserViewState: Any)

// Platform-specific browser state retrieval
expect fun getBrowserState(url: String): Pair<Any, Any>?

// Platform-specific FluckTabComponent creation
expect fun createFluckTabComponent(
    config: TabInfo,
    componentContext: ComponentContext,
    onTitleUpdate: (String) -> Unit,
    onIconUpdate: (ImageVector) -> Unit,
    onTabIconUpdate: (TabIcon) -> Unit,
    onOpenInNewTab: (String) -> Unit,
    onNavigationUpdate: ((String, String) -> Unit)? = null
): FluckTabComponent

open class FluckTabComponent(
    override val config: TabInfo,
    private val componentContext: ComponentContext,
    private val onTitleUpdate: (String) -> Unit,
    private val onIconUpdate: (ImageVector) -> Unit,
    private val onTabIconUpdate: (TabIcon) -> Unit,
    private val onOpenInNewTab: (String) -> Unit,
    private val onNavigationUpdate: ((String, String) -> Unit)? = null
) : TabComponentWithUI, ComponentContext by componentContext {

    // Store the URL to load
    private val initialUrl = (config as? FluckTabInfo)?.url ?: "https://www.risalabs.ai"

    // Browser state will be initialized lazily in Content() - NOT during construction
    // This prevents blocking the UI thread during window initialization
    private var browserError: Throwable? = null
    private var browserState: Pair<Any, Any>? = null
    val browser: Any? get() = browserState?.first
    val browserViewState: Any? get() = browserState?.second

    private var isDisposed = false
    
    // Method to be overridden by platform-specific classes
    open fun reload() {
        // Default implementation does nothing
        // Platform-specific implementations will override this
    }

    override val tabTypeInfo = Fluck

    @Composable
    override fun Content() {
        // Lazy initialization - happens AFTER window is composed and displayed
        var browserState by remember { mutableStateOf<Pair<Any, Any>?>(null) }
        var browserError by remember { mutableStateOf<Throwable?>(null) }
        var isInitializing by remember { mutableStateOf(true) }

        // Initialize browser asynchronously when composable enters composition
        LaunchedEffect(Unit) {
            try {
                // This runs in a coroutine, won't block UI thread
                kotlinx.coroutines.delay(100) // Give window time to be displayed
                val state = getBrowserState(initialUrl)
                if (state != null) {
                    this@FluckTabComponent.browserState = state
                    browserState = state
                } else {
                    browserError = Exception("Could not initialize browser - window not ready")
                }
            } catch (e: Exception) {
                browserError = e
            } finally {
                isInitializing = false
            }
        }

        if (!isDisposed) {
            when {
                browserError != null -> {
                    // Show error message instead of browser
                    BrowserErrorView(
                        error = browserError!!,
                        url = initialUrl
                    )
                }
                browserState != null -> {
                    val browser = browserState!!.first
                    val browserViewState = browserState!!.second
                    FluckView(
                        fileId = config.id,
                        content = initialUrl,
                        browser = browser,
                        browserViewState = browserViewState,
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
                        }
                    )
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
        if (!isDisposed) {
            isDisposed = true
            // Dispose the browser and view state
            // The composable's DisposableEffect and disposal guards will handle cleanup coordination
            try {
                browserViewState?.let { disposeBrowserViewState(it) }
                browser?.let { disposeBrowser(it) }
            } catch (e: Exception) {
                println("Error disposing browser: ${e.message}")
            }
        }
    }
}

@Composable
fun BrowserErrorView(error: Throwable, url: String) {
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
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Text(
                    text = "Try using the code editor or terminal tabs instead.",
                    fontSize = 14.sp,
                    color = Color(0xFFCCCCCC)
                )
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
        }
    )
}
