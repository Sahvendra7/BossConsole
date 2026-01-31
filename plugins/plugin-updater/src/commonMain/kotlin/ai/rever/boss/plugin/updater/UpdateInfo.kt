package ai.rever.boss.plugin.updater

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Information about an available plugin update.
 */
@Serializable
data class UpdateInfo(
    /**
     * Plugin ID.
     */
    @SerialName("pluginId")
    val pluginId: String,

    /**
     * Plugin display name.
     */
    @SerialName("displayName")
    val displayName: String,

    /**
     * Currently installed version.
     */
    @SerialName("currentVersion")
    val currentVersion: String,

    /**
     * Available new version.
     */
    @SerialName("newVersion")
    val newVersion: String,

    /**
     * Changelog for the new version.
     */
    @SerialName("changelog")
    val changelog: String = "",

    /**
     * Size of the update in bytes.
     */
    @SerialName("size")
    val size: Long = 0,

    /**
     * Whether this is a critical/security update.
     */
    @SerialName("critical")
    val critical: Boolean = false,

    /**
     * Release date of the new version.
     */
    @SerialName("releaseDate")
    val releaseDate: Long = 0,

    /**
     * Download URL for the new version.
     */
    @SerialName("downloadUrl")
    val downloadUrl: String = "",

    /**
     * Whether this update requires a restart.
     */
    @SerialName("requiresRestart")
    val requiresRestart: Boolean = false
)

/**
 * State of an update operation.
 */
sealed class UpdateState {
    /**
     * No update in progress.
     */
    data object Idle : UpdateState()

    /**
     * Checking for updates.
     */
    data object Checking : UpdateState()

    /**
     * Downloading update.
     */
    data class Downloading(
        val pluginId: String,
        val progress: Float
    ) : UpdateState()

    /**
     * Installing update.
     */
    data class Installing(
        val pluginId: String
    ) : UpdateState()

    /**
     * Update completed.
     */
    data class Completed(
        val pluginId: String,
        val newVersion: String
    ) : UpdateState()

    /**
     * Update failed.
     */
    data class Failed(
        val pluginId: String,
        val error: String,
        val exception: Throwable? = null
    ) : UpdateState()
}

/**
 * Result of checking for updates.
 */
data class UpdateCheckResult(
    /**
     * Available updates.
     */
    val availableUpdates: List<UpdateInfo>,

    /**
     * Plugins that failed to check.
     */
    val failedChecks: Map<String, String>,

    /**
     * Timestamp when the check was performed.
     */
    val checkedAt: Long = System.currentTimeMillis()
) {
    val hasUpdates: Boolean get() = availableUpdates.isNotEmpty()
    val hasCriticalUpdates: Boolean get() = availableUpdates.any { it.critical }
}

/**
 * Configuration for the update checker.
 */
data class UpdateCheckerConfig(
    /**
     * Interval between automatic update checks in milliseconds.
     * Set to 0 to disable automatic checks.
     */
    val checkIntervalMs: Long = 24 * 60 * 60 * 1000, // 24 hours

    /**
     * Whether to check for updates on startup.
     */
    val checkOnStartup: Boolean = true,

    /**
     * Whether to notify for non-critical updates.
     */
    val notifyNonCritical: Boolean = true,

    /**
     * Whether to auto-download updates (but not install).
     */
    val autoDownload: Boolean = false,

    /**
     * Whether to include pre-release versions.
     */
    val includePrerelease: Boolean = false
)
