package ai.rever.boss.components.plugin.panels.right_top

import ai.rever.boss.components.plugin.tab_types.fluck.FluckTabComponent
import androidx.compose.runtime.Composable
import com.teamdev.jxbrowser.browser.Browser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Desktop implementation of browser accessor using JxBrowser
 */
actual class BrowserAccessor {
    actual fun getActiveBrowserIntegration(): BrowserIntegration? {
        // Direct implementation - don't rely on ConnectToFluckBrowser being called
        val tabId = selectedTabId ?: return null
        
        // Get the browser directly from the stored reference if available
        if (currentBrowserIntegration != null && currentBrowserIntegration!!.isBrowserAvailable()) {
            return currentBrowserIntegration
        }
        
        // Try to find browser directly if we have access to split view state
        val splitViewState = lastKnownSplitViewState
        if (splitViewState != null) {
            val browser = findBrowserForTab(splitViewState, tabId)
            if (browser != null) {
                currentBrowserIntegration = DesktopBrowserIntegration(browser)
                return currentBrowserIntegration
            }
        }
        
        return null
    }
    
    actual companion object {
        var currentBrowserIntegration: BrowserIntegration? = null
        actual var selectedTabId: String? = null
        var lastKnownSplitViewState: ai.rever.boss.components.window_panel.SplitViewState? = null
    }
}

/**
 * Desktop browser integration using JxBrowser
 */
class DesktopBrowserIntegration(
    private val browser: Browser
) : BrowserIntegration {
    
    override suspend fun executeJavaScript(script: String): Any? = withContext(Dispatchers.Main) {
        try {
            val mainFrame = browser.mainFrame().orElse(null)
            if (mainFrame != null) {
                mainFrame.executeJavaScript<Any>(script)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
    
    override fun isBrowserAvailable(): Boolean {
        return !browser.isClosed
    }
    
    override suspend fun getCurrentUrl(): String? = withContext(Dispatchers.Main) {
        try {
            browser.url()
        } catch (e: Exception) {
            null
        }
    }
}

/**
 * Direct browser lookup function
 */
private fun findBrowserForTab(
    splitViewState: ai.rever.boss.components.window_panel.SplitViewState,
    tabId: String
): Browser? {
    // Get all active Fluck tabs
    val activeFluckTabs = splitViewState.collectAllActiveFluckTabs()
    
    // Find the selected tab
    val selectedTab = activeFluckTabs.find { activeTab ->
        val tabInfo = activeTab.tabInfo
        when (tabInfo) {
            is ai.rever.boss.components.plugin.tab_types.fluck.FluckTabInfo -> tabInfo.id == tabId
            is FluckTabComponent -> tabInfo.config.id == tabId
            else -> false
        }
    }
    
    if (selectedTab != null) {
        val tabInfo = selectedTab.tabInfo
        
        // Get browser based on tab info type
        return when (tabInfo) {
            is ai.rever.boss.components.plugin.tab_types.fluck.FluckTabInfo -> {
                val component = findFluckTabComponentById(splitViewState, tabInfo.id)
                component?.browser as? Browser
            }
            is FluckTabComponent -> {
                tabInfo.browser as? Browser
            }
            else -> null
        }
    }
    
    return null
}

/**
 * Helper function to find FluckTabComponent by ID in the SplitViewState
 */
private fun findFluckTabComponentById(
    splitViewState: ai.rever.boss.components.window_panel.SplitViewState, 
    tabId: String
): FluckTabComponent? {
    // Search through all panels
    val allPanels = splitViewState.getAllPanels()
    
    for (panel in allPanels) {
        val tabsComponent = panel.tabsComponent
        
        // Use the public API method to get the component
        val component = tabsComponent.getComponentById(tabId)
        
        if (component is FluckTabComponent) {
            return component
        }
    }
    
    return null
}

/**
 * Composable helper to find and connect to Fluck browser
 */
@Composable
fun ConnectToFluckBrowser() {
    // This function is kept for compatibility but the actual browser
    // lookup is done directly in BrowserAccessor.getActiveBrowserIntegration()
}

/**
 * Desktop implementation of browser connection setup
 */
@Composable
actual fun SetupBrowserConnection() {
    ConnectToFluckBrowser()
}

/**
 * Desktop implementation to store split view state
 */
actual fun storeSplitViewState(splitViewState: Any) {
    BrowserAccessor.lastKnownSplitViewState = splitViewState as? ai.rever.boss.components.window_panel.SplitViewState
}

/**
 * Desktop implementation to create FluckTabInfo from ActiveTab
 */
actual fun createFluckTabInfo(activeTab: Any): FluckTabInfo? {
    // ActiveTab is from TopOfMind package
    val activeTabTyped = activeTab as? ai.rever.boss.components.plugin.panels.left_bottom.TopOfMind.ActiveTab
        ?: return null
    
    val tabInfo = activeTabTyped.tabInfo
    
    // Check if this is a Fluck tab by checking the TabInfo type
    if (tabInfo is ai.rever.boss.components.plugin.tab_types.fluck.FluckTabInfo) {
        val result = FluckTabInfo(
            id = tabInfo.id,
            title = tabInfo.title,
            url = tabInfo.currentUrl,
            panelId = activeTabTyped.panelId,
            tabComponent = tabInfo // Store the FluckTabInfo itself
        )
        return result
    }
    return null
}

/**
 * Desktop implementation of RPA Recorder Factory
 */
actual class RpaRecorderFactory {
    actual fun createComponent(ctx: com.arkivanov.decompose.ComponentContext, panelInfo: ai.rever.boss.components.registery.PanelInfo): RpaRecorderComponent {
        return DesktopRpaRecorderComponent(ctx, panelInfo)
    }
}

/**
 * Desktop RPA Recorder Component with file saving
 */
class DesktopRpaRecorderComponent(
    ctx: com.arkivanov.decompose.ComponentContext,
    panelInfo: ai.rever.boss.components.registery.PanelInfo
) : RpaRecorderComponent(ctx, panelInfo) {
    
    override fun saveAndOpenConfiguration(filename: String, content: String) {
        try {
            // Get user's downloads directory
            val userHome = System.getProperty("user.home")
            val downloadsDir = java.io.File(userHome, "Downloads")
            
            // Create rpa_config subdirectory
            val rpaConfigDir = java.io.File(downloadsDir, "rpa_config")
            if (!rpaConfigDir.exists()) {
                rpaConfigDir.mkdirs()
            }
            
            // Save file in rpa_config directory
            val file = java.io.File(rpaConfigDir, filename)
            file.writeText(content)
            
            // Open the rpa_config directory in file manager
            val desktop = java.awt.Desktop.getDesktop()
            if (desktop.isSupported(java.awt.Desktop.Action.OPEN)) {
                desktop.open(rpaConfigDir)
            }
            
            println("RPA Configuration saved to: ${file.absolutePath}")
        } catch (e: Exception) {
            println("Error saving RPA configuration: ${e.message}")
            e.printStackTrace()
        }
    }
}

