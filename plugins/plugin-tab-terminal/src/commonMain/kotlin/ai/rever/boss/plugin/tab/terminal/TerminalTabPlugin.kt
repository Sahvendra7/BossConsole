package ai.rever.boss.plugin.tab.terminal

import ai.rever.boss.plugin.api.Plugin
import ai.rever.boss.plugin.api.PluginContext
import ai.rever.boss.plugin.api.TabComponentWithUI
import ai.rever.boss.plugin.api.TabInfo
import com.arkivanov.decompose.ComponentContext

/**
 * Plugin for Terminal tab type
 *
 * This plugin provides terminal tab capabilities using BossTerm library.
 * Terminal tabs support shell commands, working directory, and title updates.
 */
object TerminalTabPlugin : Plugin {
    override val pluginId = "terminal-tab"
    override val displayName = "Terminal Tab"

    /**
     * Register the plugin with a component factory.
     *
     * @param context The plugin context with tab registry
     * @param componentFactory Factory function that creates the actual terminal component.
     *                         This allows the implementation to stay in composeApp.
     */
    fun register(
        context: PluginContext,
        componentFactory: (tabInfo: TabInfo, componentContext: ComponentContext) -> TabComponentWithUI
    ) {
        context.tabRegistry.registerTabType(TerminalTabType) { tabInfo, ctx ->
            componentFactory(tabInfo, ctx)
        }
    }

    override fun register(context: PluginContext) {
        // No-op: This plugin requires explicit registration with component factory
    }
}
