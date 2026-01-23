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
     * Applies migration to add any new actions from presets.
     */
    private fun loadSettingsSync() {
        try {
            if (settingsFile.exists()) {
                val content = settingsFile.readText()
                val loaded = json.decodeFromString<KeymapSettings>(content)
                println("[Keymap] Loaded settings from ${settingsFile.absolutePath}")

                // Apply migration to add any new actions from preset
                val migrated = migrateSettings(loaded)

                // Save if migration made changes
                if (migrated != loaded) {
                    try {
                        val migratedContent = json.encodeToString(KeymapSettings.serializer(), migrated)
                        settingsFile.writeText(migratedContent)
                        println("[Keymap] Migrated settings saved to ${settingsFile.absolutePath}")
                    } catch (e: Exception) {
                        println("[Keymap] Warning: Could not save migrated settings: ${e.message}")
                    }
                }

                _currentSettings.value = migrated
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
     * Migrate settings by adding any new actions from the preset that are missing.
     * This ensures existing users get new keybindings added to presets while
     * preserving their customizations.
     *
     * @param loaded The loaded user settings
     * @return Migrated settings with any missing actions added from the preset
     */
    private fun migrateSettings(loaded: KeymapSettings): KeymapSettings {
        // Get the preset that matches user's presetName
        val presetShortcuts = when (loaded.presetName) {
            "VS Code" -> KeymapPresets.getVSCodePreset().shortcuts
            "IntelliJ IDEA" -> KeymapPresets.getIntelliJPreset().shortcuts
            "Emacs" -> KeymapPresets.getEmacsPreset().shortcuts
            else -> KeymapPresets.getBOSSDefault().shortcuts
        }

        // Find actions in preset that are missing from user settings
        val missingActions = presetShortcuts.filterKeys { actionId ->
            !loaded.shortcuts.containsKey(actionId)
        }

        if (missingActions.isEmpty()) {
            return loaded // No migration needed
        }

        println("[Keymap] Migrating settings: adding ${missingActions.size} new action(s): ${missingActions.keys.joinToString()}")

        // Merge: user settings + missing actions from preset
        val mergedShortcuts = loaded.shortcuts + missingActions

        return loaded.copy(shortcuts = mergedShortcuts)
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
