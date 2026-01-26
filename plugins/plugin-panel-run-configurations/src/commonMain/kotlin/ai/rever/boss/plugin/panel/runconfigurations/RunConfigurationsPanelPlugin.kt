package ai.rever.boss.plugin.panel.runconfigurations

import ai.rever.boss.plugin.api.Plugin
import ai.rever.boss.plugin.api.PluginContext
import ai.rever.boss.plugin.api.RunConfigurationDataProvider

/**
 * Plugin for the Run Configurations panel.
 *
 * Detects runnable files in the current project (main functions, scripts, tests).
 * Unlike the top bar run dropdown which shows run history, this plugin auto-detects
 * configurations from the project source code.
 *
 * Usage:
 * 1. Set the dataProvider before calling register()
 * 2. Call register(context) to register the panel
 *
 * Note: Window-specific context (windowId, projectPath) is provided via
 * WindowContextProvider set on the component at registration time.
 */
object RunConfigurationsPanelPlugin : Plugin {
    override val pluginId = "ai.rever.boss.plugin.run-configurations"
    override val displayName = "Run Configurations"

    /**
     * Data provider implementation - must be set before registration.
     * Set by composeApp before calling register().
     */
    var dataProvider: RunConfigurationDataProvider? = null

    /**
     * Register the panel with the given context and window context provider.
     *
     * @param context Plugin context for registration
     * @param windowContextProvider Provider for window-specific context (windowId, projectPath)
     */
    fun register(context: PluginContext, windowContextProvider: WindowContextProviderForPlugin) {
        val provider = dataProvider
            ?: throw IllegalStateException("RunConfigurationsPanelPlugin.dataProvider must be set before registration")

        context.panelRegistry.registerPanel(RunConfigurationsInfo) { ctx, panelInfo ->
            RunConfigurationsComponent(
                ctx = ctx,
                panelInfo = panelInfo,
                dataProvider = provider,
                windowContextProvider = windowContextProvider
            )
        }
    }

    // Fallback for Plugin interface - requires manual window context setup
    override fun register(context: PluginContext) {
        throw IllegalStateException(
            "Use register(context, windowContextProvider) instead. " +
            "This plugin requires window-specific context."
        )
    }
}

/**
 * Provider interface for window-specific context.
 * Implemented by composeApp to provide access to window state.
 */
interface WindowContextProviderForPlugin {
    /**
     * Get the current window ID.
     * Returns null if window ID is not available.
     */
    fun getWindowId(): String?

    /**
     * Get the current project path.
     * Returns empty string if no project is selected.
     */
    fun getProjectPath(): String
}
