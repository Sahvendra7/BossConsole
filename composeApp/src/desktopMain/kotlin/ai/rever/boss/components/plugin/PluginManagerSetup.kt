package ai.rever.boss.components.plugin

import ai.rever.boss.plugin.PluginManagerOperationsFactory
import ai.rever.boss.plugin.api.ActiveTabsProvider
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
     * @param dynamicPluginManager The dynamic plugin manager for plugin operations
     * @param activeTabsProvider Provider for opening URLs in browser tabs
     */
    actual fun registerPluginManagerPanel(
        context: PluginContext,
        dynamicPluginManager: DynamicPluginManager,
        activeTabsProvider: ActiveTabsProvider?
    ) {
        logger.info(LogCategory.SYSTEM, "Registering Plugin Manager panel")

        // Create component binding for late binding between operations and component
        val binding = PluginManagerPanelPlugin.ComponentBinding()

        // Create operations provider using the factory
        val operationsProvider = PluginManagerOperationsFactory.createProvider(
            dynamicPluginManager = dynamicPluginManager,
            getComponent = { binding.component }
        )

        // Create URL opener callback using ActiveTabsProvider
        val onOpenUrl: ((String) -> Unit)? = activeTabsProvider?.let { provider ->
            { url: String ->
                // Extract a display name from the URL for the tab title
                val title = try {
                    java.net.URI(url).host ?: url
                } catch (e: Exception) {
                    url.take(50)
                }
                provider.createBrowserTab(url, title)
            }
        }

        // Register with binding using the full implementation
        PluginManagerPanelPlugin.registerWithBinding(
            context = context,
            binding = binding,
            operationsFactory = { _ -> operationsProvider() },
            onOpenUrl = onOpenUrl
        )

        logger.info(LogCategory.SYSTEM, "Plugin Manager panel registered with DynamicPluginManager integration")
    }
}
