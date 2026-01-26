package ai.rever.boss.plugin.panel.bookmarks

import ai.rever.boss.plugin.api.PanelComponentWithUI
import ai.rever.boss.plugin.api.PanelInfo
import ai.rever.boss.plugin.api.Plugin
import ai.rever.boss.plugin.api.PluginContext
import ai.rever.boss.plugin.api.TabIcon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.arkivanov.decompose.ComponentContext

/**
 * Plugin for Bookmarks panel
 *
 * This plugin provides the Bookmarks panel which allows users to:
 * - ⭐ Favorites: Quick access to bookmarked tabs
 * - 📂 Collections: Organized bookmark groups
 * - 💼 Workspaces: Saved tab layouts
 *
 * Access Control:
 * - Available to all users
 *
 * Note: This plugin uses CompositionLocals for accessing:
 * - LocalSplitViewOperations: For tab/workspace operations
 * - LocalBookmarkDataProvider: For bookmark management
 * - LocalWorkspaceDataProvider: For workspace management
 * - LocalProjectPath: For current project path
 *
 * These must be provided by the parent composition in composeApp.
 */
object BookmarksPanelPlugin : Plugin {
    override val pluginId = "bookmarks-panel"
    override val displayName = "Bookmarks Panel"

    /**
     * Register the plugin with a component factory.
     *
     * This is the preferred registration method when the component is implemented
     * in composeApp and uses CompositionLocals for providers.
     *
     * @param context The plugin context for registration
     * @param componentFactory Factory to create the bookmarks component
     */
    fun register(
        context: PluginContext,
        componentFactory: (ctx: ComponentContext, panelInfo: PanelInfo) -> PanelComponentWithUI
    ) {
        context.panelRegistry.registerPanel(BookmarksInfo) { ctx, panelInfo ->
            componentFactory(ctx, panelInfo)
        }
    }

    /**
     * Register the plugin with composition-level providers.
     *
     * Note: Most data providers are accessed via CompositionLocals in the component.
     * Only composition-level UI providers need to be passed here.
     *
     * @param context The plugin context for registration
     * @param faviconLoaderProvider Function to load favicon images
     * @param contextMenuProvider Function to create context menu modifiers
     * @param dialogProvider Provider for dialog composables
     */
    fun registerWithProviders(
        context: PluginContext,
        faviconLoaderProvider: @Composable (String?) -> TabIcon.Image?,
        contextMenuProvider: @Composable (Modifier, List<ContextMenuItemData>) -> Modifier,
        dialogProvider: BookmarksDialogProvider
    ) {
        context.panelRegistry.registerPanel(BookmarksInfo) { ctx, panelInfo ->
            BookmarksComponent(
                ctx = ctx,
                panelInfo = panelInfo,
                faviconLoaderProvider = faviconLoaderProvider,
                contextMenuProvider = contextMenuProvider,
                dialogProvider = dialogProvider
            )
        }
    }

    /**
     * Unregister the panel.
     *
     * @param context The plugin context for unregistration
     */
    fun unregister(context: PluginContext) {
        context.panelRegistry.unregisterPanel(BookmarksInfo.id)
    }

    override fun register(context: PluginContext) {
        // No-op: This plugin requires explicit registration with UI providers
        // Use register(context, faviconLoaderProvider, contextMenuProvider, dialogProvider) instead
    }
}
