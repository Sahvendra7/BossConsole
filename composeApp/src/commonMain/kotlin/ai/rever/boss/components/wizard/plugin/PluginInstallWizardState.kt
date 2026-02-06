package ai.rever.boss.components.wizard.plugin

import ai.rever.boss.components.wizard.WizardState
import ai.rever.boss.components.wizard.wizardStateOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Information about a plugin available for installation.
 *
 * @property id Unique plugin identifier
 * @property name Human-readable name
 * @property description Plugin description
 * @property version Plugin version
 * @property icon Optional icon for the plugin
 * @property isDefault Whether this plugin should be selected by default
 * @property category The category this plugin belongs to
 * @property downloadUrl URL to download the plugin
 */
data class WizardPluginInfo(
    val id: String,
    val name: String,
    val description: String,
    val version: String,
    val icon: ImageVector? = null,
    val isDefault: Boolean = false,
    val category: PluginCategory = PluginCategory.OTHER,
    val downloadUrl: String = ""
)

/**
 * State management for the plugin installation wizard.
 *
 * Manages:
 * - Navigation through wizard steps
 * - Plugin selection state per category
 * - Installation progress tracking
 */
class PluginInstallWizardState(
    availablePlugins: List<WizardPluginInfo>
) {
    /**
     * Wizard step navigation state.
     */
    val wizardState: WizardState<PluginInstallStep> = wizardStateOf(
        steps = PluginInstallStep.allSteps,
        initialStepIndex = 0
    )

    /**
     * Map of plugin ID to selection state.
     */
    private val _selectedPlugins = mutableStateMapOf<String, Boolean>()

    /**
     * Installation progress (0.0 to 1.0).
     */
    var installationProgress by mutableStateOf(0f)
        private set

    /**
     * Current installation status message.
     */
    var installationStatus by mutableStateOf("")
        private set

    /**
     * Whether installation is currently in progress.
     */
    var isInstalling by mutableStateOf(false)
        private set

    /**
     * List of successfully installed plugin IDs.
     */
    var installedPluginIds by mutableStateOf<List<String>>(emptyList())
        private set

    /**
     * Error message if installation failed.
     */
    var installationError by mutableStateOf<String?>(null)
        private set

    /**
     * All available plugins grouped by category.
     */
    private val pluginsByCategory: Map<PluginCategory, List<WizardPluginInfo>> =
        availablePlugins.groupBy { it.category }

    init {
        // Initialize with default selections
        availablePlugins.forEach { plugin ->
            _selectedPlugins[plugin.id] = plugin.isDefault
        }
    }

    /**
     * Get plugins for a specific category.
     */
    fun getPluginsForCategory(category: PluginCategory): List<WizardPluginInfo> {
        return pluginsByCategory[category] ?: emptyList()
    }

    /**
     * Check if a plugin is selected.
     */
    fun isPluginSelected(pluginId: String): Boolean {
        return _selectedPlugins[pluginId] == true
    }

    /**
     * Toggle a plugin's selection state.
     */
    fun togglePlugin(pluginId: String) {
        _selectedPlugins[pluginId] = !isPluginSelected(pluginId)
    }

    /**
     * Set a plugin's selection state.
     */
    fun setPluginSelected(pluginId: String, selected: Boolean) {
        _selectedPlugins[pluginId] = selected
    }

    /**
     * Select all plugins in a category.
     */
    fun selectAllInCategory(category: PluginCategory) {
        getPluginsForCategory(category).forEach { plugin ->
            _selectedPlugins[plugin.id] = true
        }
    }

    /**
     * Deselect all plugins in a category.
     */
    fun deselectAllInCategory(category: PluginCategory) {
        getPluginsForCategory(category).forEach { plugin ->
            _selectedPlugins[plugin.id] = false
        }
    }

    /**
     * Get the count of selected plugins in a category.
     */
    fun getSelectedCountInCategory(category: PluginCategory): Int {
        return getPluginsForCategory(category).count { isPluginSelected(it.id) }
    }

    /**
     * Get all selected plugin IDs.
     */
    fun getSelectedPluginIds(): List<String> {
        return _selectedPlugins.filter { it.value }.keys.toList()
    }

    /**
     * Get all selected plugins.
     */
    fun getSelectedPlugins(): List<WizardPluginInfo> {
        val selectedIds = getSelectedPluginIds().toSet()
        return pluginsByCategory.values.flatten().filter { it.id in selectedIds }
    }

    /**
     * Check if any plugins are selected.
     */
    fun hasSelectedPlugins(): Boolean {
        return _selectedPlugins.any { it.value }
    }

    /**
     * Update installation progress.
     */
    fun updateProgress(progress: Float, status: String) {
        installationProgress = progress.coerceIn(0f, 1f)
        installationStatus = status
    }

    /**
     * Mark installation as started.
     */
    fun startInstallation() {
        isInstalling = true
        installationProgress = 0f
        installationStatus = "Preparing installation..."
        installationError = null
        installedPluginIds = emptyList()
    }

    /**
     * Mark installation as complete.
     */
    fun completeInstallation(installedIds: List<String>) {
        isInstalling = false
        installationProgress = 1f
        installationStatus = "Installation complete"
        installedPluginIds = installedIds
    }

    /**
     * Mark installation as failed.
     */
    fun failInstallation(error: String) {
        isInstalling = false
        installationError = error
    }

    /**
     * Navigate to the next step.
     */
    fun goToNextStep() {
        wizardState.goToNextStep()
    }

    /**
     * Navigate to the previous step.
     */
    fun goToPreviousStep() {
        wizardState.goToPreviousStep()
    }

    /**
     * Skip to the installing step.
     */
    fun skipToInstalling() {
        val installingIndex = PluginInstallStep.allSteps.indexOf(PluginInstallStep.Installing)
        wizardState.goToStep(installingIndex)
    }

    /**
     * Reset the wizard to initial state.
     */
    fun reset() {
        wizardState.reset()
        installationProgress = 0f
        installationStatus = ""
        isInstalling = false
        installedPluginIds = emptyList()
        installationError = null
    }
}

/**
 * Remember a plugin installation wizard state.
 */
@Composable
fun rememberPluginInstallWizardState(
    availablePlugins: List<WizardPluginInfo>
): PluginInstallWizardState {
    return remember(availablePlugins) {
        PluginInstallWizardState(availablePlugins)
    }
}
