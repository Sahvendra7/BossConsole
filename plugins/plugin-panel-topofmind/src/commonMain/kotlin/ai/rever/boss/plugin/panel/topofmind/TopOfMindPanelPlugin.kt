package ai.rever.boss.plugin.panel.topofmind

import ai.rever.boss.plugin.api.PanelComponentWithUI
import ai.rever.boss.plugin.api.PanelInfo
import ai.rever.boss.plugin.api.Plugin
import ai.rever.boss.plugin.api.PluginContext
import ai.rever.boss.plugin.api.TabIcon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Tab
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import com.arkivanov.decompose.ComponentContext

/**
 * Plugin for Top of Mind panel
 *
 * This plugin provides the Top of Mind panel which allows users to:
 * - View all active tabs organized by workspace
 * - Search across all active tabs
 * - Switch between workspaces
 * - Navigate to specific tabs
 *
 * Access Control:
 * - Available to all users
 *
 * Note: This plugin uses CompositionLocals for accessing:
 * - LocalSplitViewOperations: For split view operations
 * - LocalWorkspaceDataProvider: For workspace management
 *
 * These must be provided by the parent composition in composeApp.
 */
object TopOfMindPanelPlugin : Plugin {
    override val pluginId = "topofmind-panel"
    override val displayName = "Top of Mind Panel"

    /**
     * Register the plugin with a component factory.
     *
     * This is the preferred registration method when the component is implemented
     * in composeApp and uses CompositionLocals for providers.
     *
     * @param context The plugin context for registration
     * @param componentFactory Factory to create the top of mind component
     */
    fun register(
        context: PluginContext,
        componentFactory: (ctx: ComponentContext, panelInfo: PanelInfo) -> PanelComponentWithUI
    ) {
        context.panelRegistry.registerPanel(TopOfMindInfo) { ctx, panelInfo ->
            componentFactory(ctx, panelInfo)
        }
    }

    /**
     * Register the plugin with composition-level providers.
     *
     * @param context The plugin context for registration
     * @param collectAllActiveTabs Function to collect all active tabs
     * @param getAllPanelStates Function to get current panel states for reactivity
     * @param faviconLoader Function to load favicon for a tab
     * @param getTabUrl Function to get URL from a tab (for FluckTabInfo)
     * @param getFaviconCacheKey Function to get favicon cache key from a tab
     * @param getFallbackIcon Function to get fallback icon for a tab
     */
    fun registerWithProviders(
        context: PluginContext,
        collectAllActiveTabs: () -> List<ActiveTab>,
        getAllPanelStates: @Composable () -> List<Triple<String, Int, List<String>>>,
        faviconLoader: @Composable (String?) -> TabIcon.Image?,
        getTabUrl: (ActiveTab) -> String? = { null },
        getFaviconCacheKey: (ActiveTab) -> String? = { null },
        getFallbackIcon: (ActiveTab) -> ImageVector = { Icons.Outlined.Tab }
    ) {
        context.panelRegistry.registerPanel(TopOfMindInfo) { ctx, panelInfo ->
            TopOfMindComponent(
                ctx = ctx,
                panelInfo = panelInfo,
                collectAllActiveTabs = collectAllActiveTabs,
                getAllPanelStates = getAllPanelStates,
                faviconLoader = faviconLoader,
                getTabUrl = getTabUrl,
                getFaviconCacheKey = getFaviconCacheKey,
                getFallbackIcon = getFallbackIcon
            )
        }
    }

    /**
     * Unregister the panel.
     *
     * @param context The plugin context for unregistration
     */
    fun unregister(context: PluginContext) {
        context.panelRegistry.unregisterPanel(TopOfMindInfo.id)
    }

    override fun register(context: PluginContext) {
        // No-op: This plugin requires explicit registration with providers
        // Use register(context, collectAllActiveTabs, getAllPanelStates, faviconLoader, ...) instead
    }
}
