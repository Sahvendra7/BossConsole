package ai.rever.boss.plugin.panel.rparecorder

import ai.rever.boss.plugin.api.PanelComponentWithUI
import ai.rever.boss.plugin.api.PanelInfo
import ai.rever.boss.plugin.api.Plugin
import ai.rever.boss.plugin.api.PluginContext
import com.arkivanov.decompose.ComponentContext

/**
 * Plugin for RPA Recorder panel
 *
 * This plugin provides browser interaction recording for RPA automation.
 * Users can record their browser actions and generate RPA configurations.
 *
 * Access Control:
 * - Available to all users
 */
object RpaRecorderPanelPlugin : Plugin {
    override val pluginId = "rparecorder-panel"
    override val displayName = "RPA Recorder Panel"

    /**
     * Register the plugin with a component factory.
     */
    fun register(
        context: PluginContext,
        componentFactory: (ctx: ComponentContext, panelInfo: PanelInfo) -> PanelComponentWithUI
    ) {
        context.panelRegistry.registerPanel(RpaRecorderInfo) { ctx, panelInfo ->
            componentFactory(ctx, panelInfo)
        }
    }

    /**
     * Unregister the panel.
     */
    fun unregister(context: PluginContext) {
        context.panelRegistry.unregisterPanel(RpaRecorderInfo.id)
    }

    override fun register(context: PluginContext) {
        // No-op: This plugin requires explicit registration with component factory
    }
}
