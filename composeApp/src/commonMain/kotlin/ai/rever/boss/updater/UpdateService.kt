package ai.rever.boss.updater

import ai.rever.boss.utils.Version
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * GitHub Release data models
 */
@Serializable
data class GitHubRelease(
    val tag_name: String,
    val name: String,
    val body: String,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val published_at: String,
    val assets: List<GitHubAsset> = emptyList()
)

@Serializable
data class GitHubAsset(
    val name: String,
    val download_url: String? = null,
    val size: Long = 0,
    val content_type: String = ""
)

/**
 * Update information for the application
 */
data class UpdateInfo(
    val available: Boolean,
    val currentVersion: Version,
    val latestVersion: Version,
    val releaseNotes: String,
    val downloadUrl: String? = null,
    val assetSize: Long = 0,
    val assetName: String = ""
) {
    val isNewerVersionAvailable: Boolean
        get() = available && latestVersion.isNewerThan(currentVersion)
}

/**
 * Platform-specific update service interface
 */
expect class UpdateService() {
    suspend fun checkForUpdates(): UpdateInfo
    suspend fun downloadUpdate(updateInfo: UpdateInfo, onProgress: (progress: Float) -> Unit): String?
    suspend fun installUpdate(downloadPath: String): Boolean
    fun getCurrentPlatform(): String
    fun getExpectedAssetName(version: Version): String
}

/**
 * Update check result sealed class
 */
sealed class UpdateResult {
    object NoUpdateAvailable : UpdateResult()
    data class UpdateAvailable(val updateInfo: UpdateInfo) : UpdateResult()
    data class Error(val message: String, val exception: Exception? = null) : UpdateResult()
}