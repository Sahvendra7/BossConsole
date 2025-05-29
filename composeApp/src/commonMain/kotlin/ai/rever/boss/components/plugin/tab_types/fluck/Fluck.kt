package ai.rever.boss.components.plugin.tab_types.fluck

import ai.rever.boss.components.plugin.DefaultPlugin
import ai.rever.boss.components.registery.TabComponentWithUI
import ai.rever.boss.components.registery.TabInfo
import ai.rever.boss.components.registery.TabTypeId
import ai.rever.boss.components.registery.TabTypeInfo
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Language
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.vector.ImageVector
import com.arkivanov.decompose.ComponentContext

object Fluck: TabTypeInfo {
    override val typeId = TabTypeId("fluck")
    override val displayName = "FLUCK"
    override val icon = Icons.Outlined.Language
}

// Mutable tab info for dynamic title updates
data class FluckTabInfo(
    override val id: String,
    override val typeId: TabTypeId,
    private var _title: String,
    override val icon: ImageVector,
    val url: String = "" // Add URL to store the initial URL
) : TabInfo {
    override val title: String get() = _title
    
    fun updateTitle(newTitle: String): FluckTabInfo {
        return copy(_title = newTitle)
    }
}

// Platform-specific browser creation
expect fun createBrowser(): Any

// Platform-specific browser view state creation
expect fun createBrowserViewState(browser: Any): Any

// Platform-specific browser disposal
expect fun disposeBrowser(browser: Any)

// Platform-specific browser view state disposal
expect fun disposeBrowserViewState(viewState: Any)

class FluckTabComponent(
    override val config: TabInfo,
    private val componentContext: ComponentContext,
    private val onTitleUpdate: (String) -> Unit,
    private val onOpenInNewTab: (String) -> Unit
) : TabComponentWithUI, ComponentContext by componentContext {

    // Store the URL to load
    private val initialUrl = if (config is FluckTabInfo) config.url else "https://www.google.com"
    
    // Create browser instance that persists for the lifetime of this component
    val browser: Any = createBrowser()
    
    // Create browser view state that persists for the lifetime of this component
    val browserViewState: Any = createBrowserViewState(browser)

    override val tabTypeInfo = Fluck

    @Composable
    override fun Content() {
        FluckView(
            fileId = config.id,
            content = initialUrl,
            browser = browser,
            browserViewState = browserViewState,
            onContentChange = { }, // Not used for browser
            onTitleChange = onTitleUpdate,
            onOpenInNewTab = onOpenInNewTab
        )
    }
    
    // Clean up browser when component is destroyed
    fun dispose() {
        disposeBrowserViewState(browserViewState)
        disposeBrowser(browser)
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
        onOpenInNewTab = { url ->
            // Create a new tab with the specified URL
            parentComponent?.let { parent ->
                val newTabId = "browser_${System.currentTimeMillis()}"
                val newTab = FluckTabInfo(
                    id = newTabId,
                    typeId = TabTypeId("fluck"),
                    _title = "Loading...",
                    icon = Icons.Outlined.Language,
                    url = url
                )
                parent.addTab(newTab)
            }
        }
    )
}