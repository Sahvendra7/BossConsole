package ai.rever.bosseditor.settings

import ai.rever.boss.plugin.pathutils.BossDirectories
import ai.rever.bosseditor.lsp.logging.LogCategory
import ai.rever.bosseditor.lsp.logging.LspLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Singleton manager for BossEditor settings.
 *
 * Handles loading, saving, and reactive updates to editor settings.
 * Settings are persisted to ~/.boss/editor-settings.json
 *
 * Usage:
 * ```kotlin
 * val manager = EditorSettingsManager.instance
 * val settings by manager.settings.collectAsState()
 *
 * // Update a single setting
 * manager.updateSetting { it.copy(fontSize = 16f) }
 *
 * // Reset to defaults
 * manager.resetToDefaults()
 * ```
 */
class EditorSettingsManager private constructor(
    private val settingsPath: String = DEFAULT_SETTINGS_PATH
) {
    private val logger = LspLogger.forComponent("EditorSettingsManager")
    private val _settings = MutableStateFlow(EditorSettings.Default)
    val settings: StateFlow<EditorSettings> = _settings.asStateFlow()

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    init {
        loadFromFile()
    }

    /**
     * Updates all settings at once.
     */
    fun updateSettings(newSettings: EditorSettings) {
        _settings.value = newSettings
        saveToFile()
    }

    /**
     * Updates a single setting using a transform function.
     */
    fun updateSetting(transform: (EditorSettings) -> EditorSettings) {
        _settings.value = transform(_settings.value)
        saveToFile()
    }

    /**
     * Resets all settings to defaults.
     */
    fun resetToDefaults() {
        _settings.value = EditorSettings.Default
        saveToFile()
    }

    /**
     * Loads settings from file.
     */
    private fun loadFromFile() {
        try {
            val file = File(settingsPath)
            if (file.exists()) {
                val content = file.readText()
                _settings.value = json.decodeFromString<EditorSettings>(content)
            }
        } catch (e: Exception) {
            logger.warn(LogCategory.GENERAL, "Failed to load settings, using defaults", error = e)
            _settings.value = EditorSettings.Default
        }
    }

    /**
     * Saves settings to file.
     */
    private fun saveToFile() {
        try {
            val file = File(settingsPath)
            file.parentFile?.mkdirs()
            val content = json.encodeToString(EditorSettings.serializer(), _settings.value)
            file.writeText(content)
        } catch (e: Exception) {
            logger.warn(LogCategory.GENERAL, "Failed to save settings", error = e)
        }
    }

    companion object {
        private val DEFAULT_SETTINGS_PATH: String by lazy {
            BossDirectories.resolve("editor-settings.json").absolutePath
        }

        /** Singleton instance */
        val instance: EditorSettingsManager by lazy {
            EditorSettingsManager()
        }

        /**
         * Creates a manager with a custom settings path (for testing).
         */
        fun withCustomPath(path: String): EditorSettingsManager {
            return EditorSettingsManager(path)
        }
    }
}
