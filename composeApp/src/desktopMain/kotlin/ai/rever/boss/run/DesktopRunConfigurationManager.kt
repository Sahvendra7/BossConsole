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
     * Note: Does NOT auto-select any configuration - user must explicitly select one.
     * Existing configs are deduplicated and names made unique.
     */
    private fun loadSettingsSync() {
        try {
            if (settingsFile.exists()) {
                val content = settingsFile.readText()
                val settings = json.decodeFromString<RunConfigurationSettings>(content)

                // Deduplicate by filePath and make names unique
                val deduplicated = settings.configurations
                    .distinctBy { it.filePath }
                val withUniqueNames = makeStoredNamesUnique(deduplicated)

                val cleanedSettings = settings.copy(configurations = withUniqueNames)
                _currentSettings.value = cleanedSettings

                // Don't auto-select - leave selectedConfiguration as null
                println("[Run] Loaded ${cleanedSettings.configurations.size} configurations from ${settingsFile.absolutePath}")

                // Save cleaned settings if we deduplicated anything
                if (deduplicated.size != settings.configurations.size) {
                    settingsFile.writeText(json.encodeToString(RunConfigurationSettings.serializer(), cleanedSettings))
                    println("[Run] Cleaned up ${settings.configurations.size - deduplicated.size} duplicate configurations")
                }
            } else {
                println("[Run] No settings file found, starting with empty configurations")
            }
        } catch (e: Exception) {
            println("[Run] Failed to load settings: ${e.message}")
            _currentSettings.value = RunConfigurationSettings()
        }
    }

    /**
     * Make stored configuration names unique using parent directory context.
     */
    private fun makeStoredNamesUnique(configs: List<RunConfiguration>): List<RunConfiguration> {
        val nameGroups = configs.groupBy { it.name }

        return configs.map { config ->
            val group = nameGroups[config.name] ?: return@map config
            if (group.size <= 1) {
                config
            } else {
                // Add parent directory to make unique
                val parts = config.filePath.split("/")
                val uniqueName = if (parts.size >= 2) {
                    val parentAndFile = parts.takeLast(2).joinToString("/")
                    config.name.replace(Regex("\\([^)]+\\)$")) { "($parentAndFile)" }
                } else {
                    config.name
                }
                config.copy(name = uniqueName)
            }
        }
    }

    /**
     * Scan a project directory for runnable entry points.
     * Note: Does NOT auto-select any configuration - user must explicitly select one.
     * Names are made unique by adding path context when duplicates exist.
     */
    actual suspend fun scanProject(projectPath: String) = withContext(Dispatchers.IO) {
        try {
            println("[Run] Scanning project: $projectPath")
            val detected = detector.scanProject(projectPath)
            val detectedWithUniqueNames = makeNamesUnique(detected, projectPath)
            _detectedConfigurations.value = detectedWithUniqueNames
            println("[Run] Found ${detectedWithUniqueNames.size} runnable configurations")
            // Don't auto-select - user must choose from dropdown
        } catch (e: Exception) {
            println("[Run] Failed to scan project: ${e.message}")
        }
    }

    /**
     * Make configuration names unique by adding parent directory context for duplicates.
     * E.g., two "main (Main.kt)" become "main (app/Main.kt)" and "main (lib/Main.kt)"
     */
    private fun makeNamesUnique(configs: List<RunConfiguration>, projectPath: String): List<RunConfiguration> {
        // Group by name to find duplicates
        val nameGroups = configs.groupBy { it.name }

        return configs.map { config ->
            val group = nameGroups[config.name] ?: return@map config
            if (group.size <= 1) {
                config
            } else {
                // Add parent directory to make unique
                val relativePath = config.filePath.removePrefix(projectPath).removePrefix("/")
                val parts = relativePath.split("/")
                val uniqueName = if (parts.size >= 2) {
                    // Include parent directory: "main (parent/Main.kt)"
                    val parentAndFile = parts.takeLast(2).joinToString("/")
                    config.name.replace(Regex("\\([^)]+\\)$")) { "($parentAndFile)" }
                } else {
                    config.name
                }
                config.copy(name = uniqueName)
            }
        }
    }

    /**
     * Add a new run configuration.
     * - Checks for duplicates by filePath (same file = same config)
     * - Generates unique name with number suffix if name already exists
     */
    actual suspend fun addConfiguration(config: RunConfiguration) {
        val current = _currentSettings.value

        // Check if configuration with same filePath already exists
        val existingByPath = current.configurations.find { it.filePath == config.filePath }
        if (existingByPath != null) {
            println("[Run] Configuration for '${config.filePath}' already exists, skipping")
            return
        }

        // Generate unique name if needed
        val uniqueName = generateUniqueName(config.name, current.configurations.map { it.name })
        val configWithUniqueName = if (uniqueName != config.name) {
            config.copy(name = uniqueName)
        } else {
            config
        }

        val updated = current.copy(
            configurations = current.configurations + configWithUniqueName
        )
        _currentSettings.value = updated
        saveSettings()
        println("[Run] Added configuration: ${configWithUniqueName.name}")
    }

    /**
     * Generate a unique name by appending a number suffix if needed.
     * E.g., "Main" -> "Main", "Main" (if exists) -> "Main (2)", etc.
     */
    private fun generateUniqueName(baseName: String, existingNames: List<String>): String {
        if (baseName !in existingNames) {
            return baseName
        }

        var counter = 2
        while (true) {
            val candidateName = "$baseName ($counter)"
            if (candidateName !in existingNames) {
                return candidateName
            }
            counter++
        }
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
