package ai.rever.boss.updater

/**
 * Platform-specific update settings
 *
 * Provides access to update configuration that persists across app restarts.
 * Actual implementations handle platform-specific storage (e.g., File I/O on desktop).
 */
expect object UpdateSettings {
    /**
     * Whether automatic update checks are enabled
     */
    var autoCheckEnabled: Boolean

    /**
     * Interval between automatic update checks in hours
     */
    var checkIntervalHours: Long
}

/**
 * Platform-specific settings manager for persisting update preferences
 */
expect object UpdateSettingsManager {
    /**
     * Save current settings to persistent storage
     */
    suspend fun saveSettings()
}
