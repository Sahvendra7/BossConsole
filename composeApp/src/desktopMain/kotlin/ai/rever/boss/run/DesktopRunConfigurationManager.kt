package ai.rever.boss.run

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Desktop implementation of RunConfigurationManager.
 * Manages run configurations with JSON persistence in ~/.boss/run-configurations.json
 */
actual object RunConfigurationManager {
    private val settingsFile = File(System.getProperty("user.home"), ".boss/run-configurations.json")
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    private val detector = DesktopMainFunctionDetector()

    private val _currentSettings = MutableStateFlow(RunConfigurationSettings())
    actual val currentSettings: StateFlow<RunConfigurationSettings> = _currentSettings.asStateFlow()

    private val _detectedConfigurations = MutableStateFlow<List<RunConfiguration>>(emptyList())
    actual val detectedConfigurations: StateFlow<List<RunConfiguration>> = _detectedConfigurations.asStateFlow()

    private val _selectedConfiguration = MutableStateFlow<RunConfiguration?>(null)
    actual val selectedConfiguration: StateFlow<RunConfiguration?> = _selectedConfiguration.asStateFlow()

    init {
        // Ensure directory exists
        settingsFile.parentFile?.mkdirs()

        // Load settings on initialization
        loadSettingsSync()
    }

    /**
     * Load settings synchronously on startup.
     */
    private fun loadSettingsSync() {
        try {
            if (settingsFile.exists()) {
                val content = settingsFile.readText()
                val settings = json.decodeFromString<RunConfigurationSettings>(content)
                _currentSettings.value = settings

                // Restore selected configuration
                settings.lastUsedConfigId?.let { lastId ->
                    val config = settings.configurations.find { it.id == lastId }
                    _selectedConfiguration.value = config
                }

                println("[Run] Loaded ${settings.configurations.size} configurations from ${settingsFile.absolutePath}")
            } else {
                println("[Run] No settings file found, starting with empty configurations")
            }
        } catch (e: Exception) {
            println("[Run] Failed to load settings: ${e.message}")
            _currentSettings.value = RunConfigurationSettings()
        }
    }

    /**
     * Scan a project directory for runnable entry points.
     */
    actual suspend fun scanProject(projectPath: String) = withContext(Dispatchers.IO) {
        try {
            println("[Run] Scanning project: $projectPath")
            val detected = detector.scanProject(projectPath)
            _detectedConfigurations.value = detected
            println("[Run] Found ${detected.size} runnable configurations")

            // Auto-select first configuration if none selected
            if (_selectedConfiguration.value == null && detected.isNotEmpty()) {
                _selectedConfiguration.value = detected.first()
            }
        } catch (e: Exception) {
            println("[Run] Failed to scan project: ${e.message}")
        }
    }

    /**
     * Add a new run configuration.
     */
    actual suspend fun addConfiguration(config: RunConfiguration) {
        val current = _currentSettings.value
        val updated = current.copy(
            configurations = current.configurations + config
        )
        _currentSettings.value = updated
        saveSettings()
    }

    /**
     * Remove a run configuration by ID.
     */
    actual suspend fun removeConfiguration(configId: String) {
        val current = _currentSettings.value
        val updated = current.copy(
            configurations = current.configurations.filter { it.id != configId },
            lastUsedConfigId = if (current.lastUsedConfigId == configId) null else current.lastUsedConfigId,
            recentConfigIds = current.recentConfigIds.filter { it != configId }
        )
        _currentSettings.value = updated

        // Clear selection if removed config was selected
        if (_selectedConfiguration.value?.id == configId) {
            _selectedConfiguration.value = null
        }

        saveSettings()
    }

    /**
     * Update an existing run configuration.
     */
    actual suspend fun updateConfiguration(config: RunConfiguration) {
        val current = _currentSettings.value
        val updated = current.copy(
            configurations = current.configurations.map {
                if (it.id == config.id) config else it
            }
        )
        _currentSettings.value = updated

        // Update selected if it's the same config
        if (_selectedConfiguration.value?.id == config.id) {
            _selectedConfiguration.value = config
        }

        saveSettings()
    }

    /**
     * Select a configuration as the current one.
     */
    actual suspend fun selectConfiguration(configId: String) {
        // Look in both user configs and detected configs
        val config = _currentSettings.value.configurations.find { it.id == configId }
            ?: _detectedConfigurations.value.find { it.id == configId }

        _selectedConfiguration.value = config

        if (config != null) {
            // Update recent list
            val current = _currentSettings.value
            val recentIds = (listOf(configId) + current.recentConfigIds)
                .distinct()
                .take(current.maxRecentConfigs)

            _currentSettings.value = current.copy(
                lastUsedConfigId = configId,
                recentConfigIds = recentIds
            )
            saveSettings()
        }
    }

    /**
     * Clear all detected configurations.
     */
    actual suspend fun clearDetected() {
        _detectedConfigurations.value = emptyList()
    }

    /**
     * Save current settings to disk.
     */
    actual suspend fun saveSettings() = withContext(Dispatchers.IO) {
        try {
            val content = json.encodeToString(RunConfigurationSettings.serializer(), _currentSettings.value)
            settingsFile.writeText(content)
            println("[Run] Settings saved to ${settingsFile.absolutePath}")
        } catch (e: Exception) {
            println("[Run] Failed to save settings: ${e.message}")
        }
    }
}
