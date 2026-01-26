package ai.rever.boss.plugin.panel.fluck

import ai.rever.boss.plugin.api.PanelComponentWithUI
import ai.rever.boss.plugin.api.PanelInfo
import ai.rever.boss.plugin.api.Plugin
import ai.rever.boss.plugin.api.PluginContext
import com.arkivanov.decompose.ComponentContext

/**
 * Plugin for Fluck/ChatGPT panel
 *
 * This plugin provides a browser panel for ChatGPT access.
 *
 * Access Control:
 * - Available to all users
 *
 * Note: This plugin requires platform-specific browser implementation.
 * The actual browser content is provided via a component factory.
 */
object FluckPanelPlugin : Plugin {
    override val pluginId = "fluck-panel"
    override val displayName = "ChatGPT Panel"

    /**
     * Register the plugin with a component factory.
     *
     * @param context The plugin context for registration
     * @param componentFactory Factory to create the fluck panel component
     */
    fun register(
        context: PluginContext,
        componentFactory: (ctx: ComponentContext, panelInfo: PanelInfo) -> PanelComponentWithUI
    ) {
        context.panelRegistry.registerPanel(FluckPanelInfo) { ctx, panelInfo ->
            componentFactory(ctx, panelInfo)
        }
    }

    /**
     * Register the plugin with a content provider for clean plugin architecture.
     *
     * @param context The plugin context for registration
     * @param contentProviderFactory Factory to create the content provider for each panel instance
     */
    fun registerWithProviders(
        context: PluginContext,
        contentProviderFactory: () -> FluckPanelContentProvider
    ) {
        context.panelRegistry.registerPanel(FluckPanelInfo) { ctx, panelInfo ->
            FluckPanelComponent(
                ctx = ctx,
                panelInfo = panelInfo,
                contentProvider = contentProviderFactory()
            )
        }
    }

    /**
     * Unregister the panel.
     *
     * @param context The plugin context for unregistration
     */
    fun unregister(context: PluginContext) {
        context.panelRegistry.unregisterPanel(FluckPanelInfo.id)
    }

    override fun register(context: PluginContext) {
        // No-op: This plugin requires explicit registration with component factory
        // Use register(context, componentFactory) instead
    }
}
