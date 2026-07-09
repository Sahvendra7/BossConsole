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

        // Give the API-layer hot swap a way to tear down plugin-hosting UI
        // before it closes any classloader (avoids NoClassDefFoundError from
        // Compose disposing a plugin's UI against a closed loader). Process-
        // wide + spans all windows, so set once; register() runs per window.
        if (DynamicPluginManager.pluginUiTeardown == null) {
            DynamicPluginManager.pluginUiTeardown = { delegate.teardownAllPluginTabs() }
        }
        // Per-plugin teardown for the shared uninstall path, so plugin-manager
        // updates and update notifications reload tab-hosting plugins cleanly.
        if (DynamicPluginManager.pluginTabsTeardown == null) {
            DynamicPluginManager.pluginTabsTeardown = { id -> delegate.teardownPluginTabs(id) }
        }

        logger.debug(LogCategory.SYSTEM, "PluginLoaderDelegate registered successfully")
    }
}
