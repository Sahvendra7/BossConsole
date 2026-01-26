package ai.rever.boss.plugin.panel.console

import ai.rever.boss.plugin.api.Plugin
import ai.rever.boss.plugin.api.PluginContext

/**
 * Console panel plugin.
 *
 * Displays captured stdout/stderr logs in a side panel.
 * Desktop-only implementation.
 */
expect object ConsolePanelPlugin : Plugin {
    override val pluginId: String
    override val displayName: String
    override fun register(context: PluginContext)
}
