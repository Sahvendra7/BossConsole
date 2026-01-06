package ai.rever.boss.aiassistant

import kotlinx.coroutines.flow.StateFlow

/**
 * Manager for AI Assistant settings.
 * Handles persistence and retrieval of AI assistant configurations.
 *
 * Issue #445: Terminal context menu for AI coding assistants
 */
expect object AIAssistantSettingsManager {
    /**
     * Current AI assistant settings as a reactive flow.
     */
    val currentSettings: StateFlow<AIAssistantSettings>

    /**
     * Save current settings to persistent storage.
     */
    suspend fun saveSettings()

    /**
     * Update settings and persist.
     */
    suspend fun updateSettings(settings: AIAssistantSettings)

    /**
     * Reset settings to defaults.
     */
    suspend fun resetToDefault()

    /**
     * Update configuration for a specific assistant.
     */
    suspend fun updateAssistantConfig(config: AIAssistantConfig)

    /**
     * Enable or disable YOLO mode for an assistant.
     */
    suspend fun setYoloEnabled(assistant: AIAssistant, enabled: Boolean)

    /**
     * Set a custom command for an assistant.
     */
    suspend fun setCustomCommand(assistant: AIAssistant, command: String?)

    /**
     * Enable or disable an assistant in the menu.
     */
    suspend fun setAssistantEnabled(assistant: AIAssistant, enabled: Boolean)

    /**
     * Set whether to show unavailable assistants in menu.
     */
    suspend fun setShowUnavailableAssistants(show: Boolean)
}
