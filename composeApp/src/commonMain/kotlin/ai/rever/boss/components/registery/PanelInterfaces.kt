package ai.rever.boss.components.registery

import ai.rever.boss.components.model.Panel
import ai.rever.boss.components.model.SidebarItem
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import com.arkivanov.decompose.ComponentContext

data class PanelId(
    val panelId: String,
    val defaultOrder: Int,
    val pluginId: String = "ai.rever.boss"
)

interface PanelInfo {
    val id: PanelId
    val displayName: String
    val icon: ImageVector
    val defaultSlotPosition: Panel

    val sidebarItem get() = SidebarItem(id, icon, displayName)
}

interface PanelComponentWithUI: ComponentContext {
    val panelInfo: PanelInfo

    @Composable
    fun Content()
}

/**
 * Namespace for panel-related interfaces
 */
object PanelInterfaces {
    interface AppController {
        fun toggleSidebar()
        fun toggleBottomBar()
        fun isBottomBarVisible(): Boolean
        fun isSidebarVisible(): Boolean
    }
    
    /**
     * Interface for panels that need access to browser content
     */
    interface BrowserContentProvider {
        /**
         * Execute JavaScript in the active browser tab
         */
        suspend fun executeJavaScript(script: String): String?
        
        /**
         * Register a callback to be notified when browser events occur
         */
        fun registerBrowserEventCallback(callback: BrowserEventCallback)
        
        /**
         * Unregister a previously registered callback
         */
        fun unregisterBrowserEventCallback(callback: BrowserEventCallback)
        
        /**
         * Get the current URL of the active browser tab
         */
        suspend fun getCurrentUrl(): String?
    }
    
    /**
     * Callback interface for browser events
     */
    interface BrowserEventCallback {
        fun onNavigationCompleted(url: String)
        fun onJavaScriptResult(result: String)
    }
}