package ai.rever.boss.keymap

import ai.rever.boss.keymap.model.KeymapSettings
import ai.rever.boss.keymap.presets.KeymapPresets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Desktop implementation of KeymapSettingsManager.
 * Manages loading and saving of keyboard shortcut settings.
 * Follows the BOSS settings management pattern with:
 * - JSON persistence in ~/.boss/keymap-settings.json
 * - Automatic directory creation
 * - Synchronous load on init, asynchronous save
 * - Graceful error handling with fallback to defaults
 */
actual object KeymapSettingsManager {
    private val settingsFile = File(System.getProperty("user.home"), ".boss/keymap-settings.json")
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    private val _currentSettings = MutableStateFlow<KeymapSettings>(KeymapPresets.getBOSSDefault())
    actual val currentSettings: StateFlow<KeymapSettings> = _currentSettings.asStateFlow()

    init {
        // Ensure directory exists
        settingsFile.parentFile?.mkdirs()

        // Load settings on initialization
        loadSettingsSync()
    }

    /**
     * Load settings synchronously on startup.
     * If file doesn't exist, uses default keymap.
     */
    private fun loadSettingsSync() {
        try {
            if (settingsFile.exists()) {
                val content = settingsFile.readText()
                val settings = json.decodeFromString<KeymapSettings>(content)
                _currentSettings.value = settings
                println("[Keymap] Loaded settings from ${settingsFile.absolutePath}")
            } else {
                // First run - create default keymap file
                println("[Keymap] No settings file found, creating default keymap")
                val defaultSettings = KeymapPresets.getBOSSDefault()
                _currentSettings.value = defaultSettings

                // Save default settings to file
                try {
                    val content = json.encodeToString(KeymapSettings.serializer(), defaultSettings)
                    settingsFile.writeText(content)
                    println("[Keymap] Created default settings file at ${settingsFile.absolutePath}")
                } catch (e: Exception) {
                    println("[Keymap] Warning: Could not write default settings file: ${e.message}")
                }
            }
        } catch (e: Exception) {
            println("[Keymap] Failed to load settings: ${e.message}")
            println("[Keymap] Falling back to default keymap")
            _currentSettings.value = KeymapPresets.getBOSSDefault()
        }
    }

    /**
     * Save current settings to disk asynchronously.
     */
    actual suspend fun saveSettings() = withContext(Dispatchers.IO) {
        try {
            val content = json.encodeToString(KeymapSettings.serializer(), _currentSettings.value)
            settingsFile.writeText(content)
            println("[Keymap] Settings saved to ${settingsFile.absolutePath}")
        } catch (e: Exception) {
            println("[Keymap] Failed to save settings: ${e.message}")
        }
    }

    /**
     * Update the current settings and save to disk.
     */
    actual suspend fun updateSettings(settings: KeymapSettings) {
        _currentSettings.value = settings
        saveSettings()
    }

    /**
     * Load a preset keymap by name.
     */
    actual suspend fun loadPreset(presetName: String) {
        val preset = when (presetName) {
            "BOSS Default" -> KeymapPresets.getBOSSDefault()
            "VS Code" -> KeymapPresets.getVSCodePreset()
            "IntelliJ IDEA" -> KeymapPresets.getIntelliJPreset()
            "Emacs" -> KeymapPresets.getEmacsPreset()
            else -> {
                println("[Keymap] Unknown preset: $presetName, using BOSS Default")
                KeymapPresets.getBOSSDefault()
            }
        }
        updateSettings(preset)
    }

    /**
     * Reset to default BOSS keymap.
     */
    actual suspend fun resetToDefault() {
        updateSettings(KeymapPresets.getBOSSDefault())
    }

    /**
     * Import keymap from JSON string.
     * Returns null if import fails.
     */
    actual suspend fun importFromJson(jsonString: String): KeymapSettings? {
        return try {
            val settings = json.decodeFromString<KeymapSettings>(jsonString)
            updateSettings(settings)
            settings
        } catch (e: Exception) {
            println("[Keymap] Failed to import settings: ${e.message}")
            null
        }
    }

    /**
     * Export current keymap to JSON string.
     */
    actual fun exportToJson(): String {
        return json.encodeToString(KeymapSettings.serializer(), _currentSettings.value)
    }

    /**
     * Import keymap from file.
     * Returns null if import fails.
     */
    suspend fun importFromFile(file: File): KeymapSettings? = withContext(Dispatchers.IO) {
        try {
            val content = file.readText()
            importFromJson(content)
        } catch (e: Exception) {
            println("[Keymap] Failed to import from file: ${e.message}")
            null
        }
    }

    /**
     * Export current keymap to file.
     */
    suspend fun exportToFile(file: File) = withContext(Dispatchers.IO) {
        try {
            file.writeText(exportToJson())
            println("[Keymap] Exported settings to ${file.absolutePath}")
        } catch (e: Exception) {
            println("[Keymap] Failed to export to file: ${e.message}")
        }
    }
}
