package ai.rever.boss.components.plugin

import ai.rever.boss.plugin.SimplePluginManagerOperations
import ai.rever.boss.plugin.api.PluginContext
import ai.rever.boss.plugin.panel.manager.PluginManagerPanelPlugin
import ai.rever.boss.utils.logging.BossLogger
import ai.rever.boss.utils.logging.LogCategory

/**
 * Desktop implementation of Plugin Manager panel registration.
 */
actual object PluginManagerSetup {
    private val logger = BossLogger.forComponent("PluginManagerSetup")

    /**
     * Register the Plugin Manager panel with desktop-specific operations.
     *
     * @param context Plugin context for registration
     */
    actual fun registerPluginManagerPanel(context: PluginContext) {
        logger.info(LogCategory.SYSTEM, "Registering Plugin Manager panel")

        // Create component binding for late binding between operations and component
        val binding = PluginManagerPanelPlugin.ComponentBinding()

        // Register with binding
        PluginManagerPanelPlugin.registerWithBinding(
            context = context,
            binding = binding,
            operationsFactory = { b ->
                SimplePluginManagerOperations(b)
            }
        )

        logger.info(LogCategory.SYSTEM, "Plugin Manager panel registered")
    }
}
