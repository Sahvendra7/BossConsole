package ai.rever.boss.components.plugin

import ai.rever.boss.plugin.api.PluginContext
import ai.rever.boss.plugin.panel.manager.PluginManagerPanelPlugin

/**
 * Expect declaration for Plugin Manager panel registration.
 *
 * This allows platform-specific implementations to provide the
 * PluginManagerOperations implementation.
 */
expect object PluginManagerSetup {
    /**
     * Register the Plugin Manager panel with platform-specific operations.
     *
     * @param context Plugin context for registration
     */
    fun registerPluginManagerPanel(context: PluginContext)
}
