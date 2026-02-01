package ai.rever.boss.plugin.api

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

/**
 * Provider interface for accessing active tabs across the application.
 *
 * This interface allows the TopOfMind panel to display all open tabs
 * organized by workspace without direct coupling to SplitViewState.
 */
interface ActiveTabsProvider {
    /**
     * StateFlow of all active tabs across all workspaces.
     */
    val activeTabs: StateFlow<List<ActiveTabData>>

    /**
     * Refresh the active tabs list.
     */
    suspend fun refreshTabs()

    /**
     * Select/focus a specific tab.
     *
     * @param tabId The ID of the tab to select
     * @param panelId The panel containing the tab
     */
    fun selectTab(tabId: String, panelId: String)

    /**
     * Get the URL of a tab (if it's a browser tab).
     *
     * @param tabId The ID of the tab
     * @return The URL or null if not a browser tab
     */
    fun getTabUrl(tabId: String): String?

    /**
     * Get the favicon cache key for a tab (if it's a browser tab).
     *
     * @param tabId The ID of the tab
     * @return The favicon cache key or null
     */
    fun getFaviconCacheKey(tabId: String): String?

    /**
     * Load a favicon by cache key.
     * This is a composable function that loads and displays the favicon.
     *
     * @param cacheKey The favicon cache key
     * @return A composable painter or null if not found
     */
    @Composable
    fun loadFavicon(cacheKey: String?): Painter?

    /**
     * Get the fallback icon for a tab type.
     *
     * @param typeId The tab type identifier
     * @return The fallback icon vector
     */
    fun getFallbackIcon(typeId: String): ImageVector?
}

/**
 * Data class representing an active tab.
 */
@Serializable
data class ActiveTabData(
    val tabId: String,
    val typeId: String,
    val title: String,
    val workspaceId: String,
    val workspaceName: String,
    val panelId: String,
    val windowId: String,
    val splitPosition: String? = null,
    val url: String? = null,
    val faviconCacheKey: String? = null
)

/**
 * Data class representing workspace layout info for TopOfMind display.
 */
@Serializable
sealed class WorkspaceLayoutData {
    @Serializable
    data class SinglePanel(val panelId: String) : WorkspaceLayoutData()

    @Serializable
    data class VerticalSplit(val leftPanelId: String, val rightPanelId: String) : WorkspaceLayoutData()

    @Serializable
    data class HorizontalSplit(val topPanelId: String, val bottomPanelId: String) : WorkspaceLayoutData()
}
