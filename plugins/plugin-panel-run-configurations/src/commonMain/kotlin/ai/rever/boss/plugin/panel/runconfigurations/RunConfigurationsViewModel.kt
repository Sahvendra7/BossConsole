package ai.rever.boss.plugin.panel.runconfigurations

import ai.rever.boss.plugin.api.RunConfigurationData
import ai.rever.boss.plugin.api.RunConfigurationDataProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for the Run Configurations panel.
 *
 * Manages the state and actions for detecting and executing run configurations.
 */
class RunConfigurationsViewModel(
    private val dataProvider: RunConfigurationDataProvider
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    /**
     * List of auto-detected run configurations from project scan.
     */
    val detectedConfigurations: StateFlow<List<RunConfigurationData>> = dataProvider.detectedConfigurations

    /**
     * Whether a project scan is currently in progress.
     */
    val isScanning: StateFlow<Boolean> = dataProvider.isScanning

    /**
     * Last error that occurred during scanning or configuration operations.
     */
    val lastError: StateFlow<String?> = dataProvider.lastError

    /**
     * Scan the project for run configurations.
     *
     * @param projectPath Path to the project to scan
     * @param windowId The window that initiated the scan
     */
    fun scanProject(projectPath: String, windowId: String) {
        scope.launch {
            dataProvider.scanProject(projectPath, windowId)
        }
    }

    /**
     * Execute a run configuration.
     *
     * @param config The configuration to execute
     * @param windowId The window that initiated the run
     */
    fun execute(config: RunConfigurationData, windowId: String) {
        scope.launch {
            dataProvider.execute(config, windowId)
        }
    }

    /**
     * Clear the last error.
     */
    fun clearError() {
        scope.launch {
            dataProvider.clearError()
        }
    }

    /**
     * Dispose the view model and cancel all coroutines.
     */
    fun dispose() {
        scope.cancel()
    }
}
