package ai.rever.boss.run

import kotlinx.coroutines.flow.StateFlow

/**
 * Expect declaration for RunConfigurationManager.
 * Manages run configurations with persistence.
 * Platform-specific implementations handle file I/O.
 */
expect object RunConfigurationManager {
    /**
     * Current run configuration settings.
     */
    val currentSettings: StateFlow<RunConfigurationSettings>

    /**
     * List of auto-detected run configurations from project scan.
     */
    val detectedConfigurations: StateFlow<List<RunConfiguration>>

    /**
     * Currently selected run configuration.
     */
    val selectedConfiguration: StateFlow<RunConfiguration?>

    /**
     * Scan a project directory for runnable entry points.
     */
    suspend fun scanProject(projectPath: String)

    /**
     * Add a new run configuration.
     */
    suspend fun addConfiguration(config: RunConfiguration)

    /**
     * Remove a run configuration by ID.
     */
    suspend fun removeConfiguration(configId: String)

    /**
     * Update an existing run configuration.
     */
    suspend fun updateConfiguration(config: RunConfiguration)

    /**
     * Select a configuration as the current one.
     */
    suspend fun selectConfiguration(configId: String)

    /**
     * Clear all detected configurations.
     */
    suspend fun clearDetected()

    /**
     * Save current settings to disk.
     */
    suspend fun saveSettings()
}
