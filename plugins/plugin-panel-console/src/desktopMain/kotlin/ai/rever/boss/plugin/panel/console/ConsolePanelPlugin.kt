package ai.rever.boss.plugin.panel.console

import ai.rever.boss.plugin.api.Plugin
import ai.rever.boss.plugin.api.PluginContext

/**
 * Console panel plugin - Desktop implementation.
 *
 * Displays captured stdout/stderr logs in a side panel.
 */
actual object ConsolePanelPlugin : Plugin {
    actual override val pluginId: String = "ai.rever.boss.plugin.console"
    actual override val displayName: String = "Console"

    actual override fun register(context: PluginContext) {
        context.panelRegistry.registerPanel(ConsoleInfo) { ctx, panelInfo ->
            ConsoleComponent(ctx, panelInfo)
        }
    }
}
