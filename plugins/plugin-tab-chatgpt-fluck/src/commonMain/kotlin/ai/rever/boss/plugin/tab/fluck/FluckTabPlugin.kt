package ai.rever.boss.plugin.tab.fluck

import ai.rever.boss.plugin.api.Plugin
import ai.rever.boss.plugin.api.PluginContext
import ai.rever.boss.plugin.api.TabComponentWithUI
import ai.rever.boss.plugin.api.TabInfo
import com.arkivanov.decompose.ComponentContext

/**
 * Plugin for Fluck (browser) tab type
 *
 * This plugin provides embedded web browser capabilities using JxBrowser.
 * Fluck tabs support web navigation, favicons, and tab title updates.
 */
object FluckTabPlugin : Plugin {
    override val pluginId = "fluck-tab"
    override val displayName = "Fluck Browser Tab"

    /**
     * Register the plugin with a component factory.
     *
     * @param context The plugin context with tab registry
     * @param componentFactory Factory function that creates the actual Fluck component.
     *                         This allows the implementation to stay in composeApp.
     */
    fun register(
        context: PluginContext,
        componentFactory: (tabInfo: TabInfo, componentContext: ComponentContext) -> TabComponentWithUI
    ) {
        context.tabRegistry.registerTabType(FluckTabType) { tabInfo, ctx ->
            componentFactory(tabInfo, ctx)
        }
    }

    override fun register(context: PluginContext) {
        // No-op: This plugin requires explicit registration with component factory
    }
}
