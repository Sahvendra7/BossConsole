package ai.rever.boss.updater

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Desktop implementation of update settings
 *
 * Controls automatic update checking behavior.
 * Settings are persisted to ~/.boss/update-settings.json
 */
actual object UpdateSettings {
    /**
     * Whether automatic update checks are enabled
     * Default: true (preserves current behavior)
     */
    actual var autoCheckEnabled: Boolean = true

    /**
     * Interval between automatic update checks in hours
     * Default: 6 hours
     */
    actual var checkIntervalHours: Long = 6
}

/**
 * Serializable data class for persisting update settings
 */
@Serializable
data class UpdateSettingsData(
    val autoCheckEnabled: Boolean = true,
    val checkIntervalHours: Long = 6
)

/**
 * Desktop implementation of update settings manager
 *
 * Settings are stored as JSON in ~/.boss/update-settings.json
 * Automatically loads settings on initialization.
 */
actual object UpdateSettingsManager {
    private val settingsFile = File(System.getProperty("user.home"), ".boss/update-settings.json")
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    init {
        // Ensure directory exists
        settingsFile.parentFile?.mkdirs()

        // Load settings on initialization
        loadSettingsSync()
    }

    /**
     * Load settings from disk synchronously
     * Called during initialization to restore user preferences
     */
    private fun loadSettingsSync() {
        try {
            if (settingsFile.exists()) {
                val content = settingsFile.readText()
                val settings = json.decodeFromString<UpdateSettingsData>(content)

                // Apply loaded settings
                UpdateSettings.autoCheckEnabled = settings.autoCheckEnabled
                UpdateSettings.checkIntervalHours = settings.checkIntervalHours

                println("✅ Loaded update settings: autoCheck=${settings.autoCheckEnabled}")
            } else {
                println("ℹ️ No saved update settings found, using defaults")
            }
        } catch (e: Exception) {
            println("⚠️ Failed to load update settings: ${e.message}")
            // Continue with defaults
        }
    }

    /**
     * Save current settings to disk
     * Should be called whenever settings are changed in the UI
     */
    actual suspend fun saveSettings() = withContext(Dispatchers.IO) {
        try {
            val settings = UpdateSettingsData(
                autoCheckEnabled = UpdateSettings.autoCheckEnabled,
                checkIntervalHours = UpdateSettings.checkIntervalHours
            )

            val content = json.encodeToString(UpdateSettingsData.serializer(), settings)
            settingsFile.writeText(content)

            println("✅ Saved update settings to: ${settingsFile.absolutePath}")
        } catch (e: Exception) {
            println("❌ Failed to save update settings: ${e.message}")
        }
    }
}
