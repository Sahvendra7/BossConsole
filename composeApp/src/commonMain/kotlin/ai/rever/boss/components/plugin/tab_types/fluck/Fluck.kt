package ai.rever.boss.components.plugin.tab_types.fluck

import ai.rever.boss.components.plugin.DefaultPlugin
import ai.rever.boss.components.registery.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Language
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.painter.Painter
import com.arkivanov.decompose.ComponentContext
import androidx.compose.runtime.DisposableEffect

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
    val url: String = "" // Add URL to store the initial URL
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
    private val onOpenInNewTab: (String) -> Unit
) : TabComponentWithUI, ComponentContext by componentContext {

    // Store the URL to load
    private val initialUrl = if (config is FluckTabInfo) config.url else "https://www.risalabs.ai"
    
    // Create browser instance that persists for the lifetime of this component
    val browser: Any = createBrowser()
    
    // Create browser view state that persists for the lifetime of this component
    val browserViewState: Any = createBrowserViewState(browser)

    override val tabTypeInfo = Fluck

    @Composable
    override fun Content() {
        // Use DisposableEffect to handle cleanup when the composable leaves the composition
        androidx.compose.runtime.DisposableEffect(config.id) {
            onDispose {
                // Clean up when tab is removed
                disposeBrowserViewState(browserViewState)
                disposeBrowser(browser)
            }
        }
        
        FluckView(
            fileId = config.id,
            content = initialUrl,
            browser = browser,
            browserViewState = browserViewState,
            onContentChange = { }, // Not used for browser
            onTitleChange = onTitleUpdate,
            onIconChange = onIconUpdate,
            onTabIconUpdate = onTabIconUpdate,
            onOpenInNewTab = onOpenInNewTab
        )
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
                val newTabId = "browser_${System.currentTimeMillis()}"
                val newTab = FluckTabInfo(
                    id = newTabId,
                    typeId = TabTypeId("fluck"),
                    _title = "Loading...",
                    url = url
                )
                parent.addTab(newTab)
            }
        }
    )
}