package ai.rever.boss.plugin.panel.rpaengine

import ai.rever.boss.plugin.api.PanelComponentWithUI
import ai.rever.boss.plugin.api.PanelInfo
import ai.rever.boss.plugin.api.Plugin
import ai.rever.boss.plugin.api.PluginContext
import com.arkivanov.decompose.ComponentContext

/**
 * Plugin for RPA Engine panel
 *
 * This plugin provides RPA execution capabilities.
 * Users can load and execute RPA configurations in the browser.
 *
 * Access Control:
 * - Available to all users
 */
object RpaEnginePanelPlugin : Plugin {
    override val pluginId = "rpaengine-panel"
    override val displayName = "RPA Engine Panel"

    /**
     * Register the plugin with a component factory.
     */
    fun register(
        context: PluginContext,
        componentFactory: (ctx: ComponentContext, panelInfo: PanelInfo) -> PanelComponentWithUI
    ) {
        context.panelRegistry.registerPanel(RpaEngineInfo) { ctx, panelInfo ->
            componentFactory(ctx, panelInfo)
        }
    }

    /**
     * Unregister the panel.
     */
    fun unregister(context: PluginContext) {
        context.panelRegistry.unregisterPanel(RpaEngineInfo.id)
    }

    override fun register(context: PluginContext) {
        // No-op: This plugin requires explicit registration with component factory
    }
}
