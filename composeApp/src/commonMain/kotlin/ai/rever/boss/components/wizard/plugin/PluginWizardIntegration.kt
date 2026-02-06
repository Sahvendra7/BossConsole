package ai.rever.boss.components.wizard.plugin

import ai.rever.boss.components.plugin.DynamicPluginManager

/**
 * Platform-specific integration for the plugin install wizard.
 *
 * This provides access to platform-specific functionality like fetching
 * available plugins from the repository and installing them.
 */
expect object PluginWizardIntegration {
    /**
     * Get the list of available plugins for the wizard.
     *
     * @return List of plugins formatted for the wizard UI
     */
    suspend fun getAvailablePlugins(): List<WizardPluginInfo>

    /**
     * Install the selected plugins.
     *
     * @param dynamicPluginManager The plugin manager to use for installation
     * @param pluginIds List of plugin IDs to install
     * @param onProgress Progress callback (0.0 to 1.0, status message)
     * @return Result containing the list of successfully installed plugin IDs
     */
    suspend fun installPlugins(
        dynamicPluginManager: DynamicPluginManager,
        pluginIds: List<String>,
        onProgress: (Float, String) -> Unit
    ): Result<List<String>>
}
