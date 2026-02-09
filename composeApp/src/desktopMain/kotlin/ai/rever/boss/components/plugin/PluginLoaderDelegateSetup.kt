package ai.rever.boss.components.plugin

import ai.rever.boss.plugin.PluginLoaderDelegateImpl
import ai.rever.boss.plugin.api.PluginContext
import ai.rever.boss.utils.logging.BossLogger
import ai.rever.boss.utils.logging.LogCategory

/**
 * Desktop implementation of PluginLoaderDelegateSetup.
 *
 * Registers the PluginLoaderDelegateImpl so that dynamic plugins
 * (like plugin-manager) can interact with the plugin system.
 */
actual object PluginLoaderDelegateSetup {

    private val logger = BossLogger.forComponent("PluginLoaderDelegateSetup")

    /**
     * Register the PluginLoaderDelegate with the plugin context.
     *
     * @param context Plugin context for registration
     * @param dynamicPluginManager The dynamic plugin manager
     */
    actual fun register(
        context: PluginContext,
        dynamicPluginManager: DynamicPluginManager
    ) {
        logger.info(LogCategory.SYSTEM, "Registering PluginLoaderDelegate for dynamic plugins")

        val delegate = PluginLoaderDelegateImpl(dynamicPluginManager)
        context.registerPluginAPI(delegate)

        logger.debug(LogCategory.SYSTEM, "PluginLoaderDelegate registered successfully")
    }
}
