package ai.rever.boss.components.plugin.tab_types.fluck

import ai.rever.boss.components.plugin.DefaultPlugin
import ai.rever.boss.components.registery.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arkivanov.decompose.ComponentContext

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
    val url: String = "", // Add URL to store the initial URL
    val navigationHistory: MutableList<Pair<String, String>> = mutableListOf() // List of (title, url) pairs
) : TabInfo {
    override val title: String get() = _title
    override val icon: ImageVector get() = _icon
    override val tabIcon: TabIcon? get() = _tabIcon ?: TabIcon.Vector(_icon)
    
    fun updateTitle(newTitle: String): FluckTabInfo {
        return copy(_title = newTitle)
    }
    
    fun updateIcon(newIcon: ImageVector): FluckTabInfo {
        return copy(_icon = newIcon, _tabIcon = TabIcon.Vector(newIcon))
    }
    
    fun updateTabIcon(newTabIcon: TabIcon): FluckTabInfo {
        return copy(_tabIcon = newTabIcon)
    }
    
    fun updateTitleAndIcon(newTitle: String, newIcon: ImageVector): FluckTabInfo {
        return copy(_title = newTitle, _icon = newIcon, _tabIcon = TabIcon.Vector(newIcon))
    }
    
    fun addToHistory(title: String, url: String) {
        // Don't add duplicate consecutive entries
        if (navigationHistory.isEmpty() || navigationHistory.last().second != url) {
            navigationHistory.add(Pair(title, url))
        }
    }
}

// Platform-specific browser creation
expect fun createBrowser(): Any

// Platform-specific browser view state creation
expect fun createBrowserViewState(browser: Any): Any

// Platform-specific browser disposal
expect fun disposeBrowser(browser: Any)

// Platform-specific browser view state disposal
expect fun disposeBrowserViewState(browserViewState: Any)

class FluckTabComponent(
    override val config: TabInfo,
    private val componentContext: ComponentContext,
    private val onTitleUpdate: (String) -> Unit,
    private val onIconUpdate: (ImageVector) -> Unit,
    private val onTabIconUpdate: (TabIcon) -> Unit,
    private val onOpenInNewTab: (String) -> Unit,
    private val onNavigationUpdate: ((String, String) -> Unit)? = null
) : TabComponentWithUI, ComponentContext by componentContext {

    // Store the URL to load
    private val initialUrl = if (config is FluckTabInfo) config.url else "https://www.risalabs.ai"
    
    // Create browser instance with error handling
    private var browserError: Throwable? = null
    val browser: Any? = try {
        createBrowser()
    } catch (e: Throwable) {
        browserError = e
        println("Failed to create browser: ${e.message}")
        null
    }
    
    val browserViewState: Any? = browser?.let {
        try {
            createBrowserViewState(it)
        } catch (e: Throwable) {
            browserError = e
            println("Failed to create browser view state: ${e.message}")
            null
        }
    }
    
    private var isDisposed = false

    override val tabTypeInfo = Fluck

    @Composable
    override fun Content() {
        if (!isDisposed) {
            when {
                browserError != null -> {
                    // Show error message instead of browser
                    BrowserErrorView(
                        error = browserError!!,
                        url = initialUrl
                    )
                }
                browser != null && browserViewState != null -> {
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
                        onNavigationUpdate = onNavigationUpdate
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
            browserViewState?.let { disposeBrowserViewState(it) }
            browser?.let { disposeBrowser(it) }
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
    
    FluckTabComponent(
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
                val newTabId = "browser_${kotlinx.datetime.Clock.System.now().toEpochMilliseconds()}"
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
                        // Add to history
                        currentTab.addToHistory(title, url)
                    }
                }
            }
        }
    )
}