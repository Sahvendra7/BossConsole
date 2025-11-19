package ai.rever.boss.components.plugin.tab_types.fluck

/**
 * Represents the current status of a download.
 */
enum class DownloadStatus {
    /** Download is queued and will start soon */
    QUEUED,

    /** Download is actively downloading */
    DOWNLOADING,

    /** Download has been paused by user */
    PAUSED,

    /** Download completed successfully */
    COMPLETED,

    /** Download failed due to error */
    FAILED,

    /** Download was cancelled by user */
    CANCELLED
}

/**
 * Represents a single download item with all its metadata and current state.
 *
 * @property id Unique identifier for this download
 * @property fileName The name of the file being downloaded
 * @property destinationPath Absolute path where the file will be saved
 * @property url The URL from which the file is being downloaded
 * @property mimeType MIME type of the file (null if unknown)
 * @property status Current status of the download
 * @property receivedBytes Number of bytes downloaded so far
 * @property totalBytes Total file size in bytes (null if unknown/chunked transfer)
 * @property speed Current download speed in bytes per second (rolling average)
 * @property startedAt Timestamp when download started (milliseconds since epoch)
 * @property finishedAt Timestamp when download finished (null if still in progress)
 * @property canPause Whether this download supports pausing
 * @property canResume Whether this download supports resuming after pause
 * @property errorReason Description of error if status is FAILED (null otherwise)
 */
data class DownloadItem(
    val id: String,
    val fileName: String,
    val destinationPath: String,
    val url: String,
    val mimeType: String?,
    val status: DownloadStatus,
    val receivedBytes: Long,
    val totalBytes: Long?,
    val speed: Double,
    val startedAt: Long,
    val finishedAt: Long?,
    val canPause: Boolean,
    val canResume: Boolean,
    val errorReason: String?
) {
    /**
     * Calculates the download progress as a percentage (0.0 to 1.0).
     * Returns 0.0 if total size is unknown.
     */
    val progress: Float
        get() = totalBytes?.let {
            if (it > 0) (receivedBytes.toFloat() / it.toFloat()).coerceIn(0f, 1f)
            else 0f
        } ?: 0f

    /**
     * Estimates time remaining in seconds based on current speed.
     * Returns null if total size is unknown or speed is zero.
     */
    val estimatedTimeRemaining: Long?
        get() {
            val total = totalBytes ?: return null
            if (speed <= 0.0 || receivedBytes >= total) return null

            val remainingBytes = total - receivedBytes
            return (remainingBytes / speed).toLong()
        }

    /**
     * Checks if this download is actively in progress (not terminal state).
     */
    val isActive: Boolean
        get() = status == DownloadStatus.DOWNLOADING || status == DownloadStatus.QUEUED

    /**
     * Checks if this download is in a terminal state (completed, failed, or cancelled).
     */
    val isTerminal: Boolean
        get() = status in setOf(DownloadStatus.COMPLETED, DownloadStatus.FAILED, DownloadStatus.CANCELLED)
}

/**
 * Helper functions for formatting download information.
 */
object DownloadFormatters {
    /**
     * Formats bytes to human-readable string (e.g., "1.5 MB", "512 KB").
     */
    fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
            else -> "%.2f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
        }
    }

    /**
     * Formats speed to human-readable string (e.g., "1.5 MB/s").
     */
    fun formatSpeed(bytesPerSecond: Double): String {
        return "${formatBytes(bytesPerSecond.toLong())}/s"
    }

    /**
     * Formats time duration to human-readable string (e.g., "2m 30s", "45s").
     */
    fun formatDuration(seconds: Long): String {
        return when {
            seconds < 60 -> "${seconds}s"
            seconds < 3600 -> "${seconds / 60}m ${seconds % 60}s"
            else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
        }
    }

    /**
     * Formats progress information for display in UI.
     * Example: "50 MB of 100 MB • 2.5 MB/s • 20s remaining"
     */
    fun formatProgressInfo(download: DownloadItem): String {
        val parts = mutableListOf<String>()

        // Size information
        val total = download.totalBytes
        if (total != null) {
            parts.add("${formatBytes(download.receivedBytes)} of ${formatBytes(total)}")
        } else {
            parts.add(formatBytes(download.receivedBytes))
        }

        // Speed information
        if (download.speed > 0) {
            parts.add(formatSpeed(download.speed))
        }

        // Time remaining
        download.estimatedTimeRemaining?.let { seconds ->
            parts.add("${formatDuration(seconds)} remaining")
        }

        return parts.joinToString(" • ")
    }
}
