package ai.rever.boss.config

import ai.rever.boss.utils.logging.BossLogger
import ai.rever.boss.utils.logging.LogCategory
import ai.rever.boss.utils.VersionConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.zip.ZipInputStream

/**
 * Utility for auto-downloading BOSS-branded Chromium binaries for development builds.
 *
 * The branded Chromium binaries are published to the BossConsole-Releases GitHub repository.
 * This class handles detecting the platform, downloading the appropriate zip file,
 * and extracting it to ~/.boss/boss-chromium/
 */
object ChromiumAutoDownloader {
    private val logger = BossLogger.forComponent("ChromiumAutoDownloader")
    // JxBrowser version from generated VersionConstants (source: gradle/libs.versions.toml)
    private val JXBROWSER_VERSION = VersionConstants.JXBROWSER_VERSION
    private const val RELEASES_BASE_URL = "https://github.com/risa-labs-inc/BossConsole-Releases/releases/download"
    private const val VERSION_FILE = "version.txt"

    /**
     * Download progress information
     */
    data class DownloadProgress(
        val bytesDownloaded: Long,
        val totalBytes: Long,
        val isComplete: Boolean = false,
        val isExtracting: Boolean = false,
        val error: String? = null
    ) {
        val progressFraction: Float
            get() = if (totalBytes > 0) bytesDownloaded.toFloat() / totalBytes else 0f

        val downloadedMB: Long
            get() = bytesDownloaded / (1024 * 1024)

        val totalMB: Long
            get() = totalBytes / (1024 * 1024)
    }

    /**
     * Get the target directory for Chromium installation
     */
    fun getChromiumDir(): Path =
        Paths.get(System.getProperty("user.home"), ".boss", "boss-chromium")

    /**
     * Check if Chromium is already installed, valid, and matches the current JxBrowser version.
     */
    fun isChromiumInstalled(): Boolean {
        val dir = getChromiumDir()
        if (!dir.toFile().exists()) return false

        // Check executable.name exists (required by JxBrowser)
        val executableNameFile = dir.resolve("executable.name").toFile()
        if (!executableNameFile.exists()) return false

        // Check version matches current JxBrowser version
        val versionFile = dir.resolve(VERSION_FILE).toFile()
        if (!versionFile.exists()) {
            logger.debug(LogCategory.BROWSER, "Chromium version file not found, will re-download")
            return false
        }

        val installedVersion = versionFile.readText().trim()
        if (installedVersion != JXBROWSER_VERSION) {
            logger.info(LogCategory.BROWSER, "Chromium version mismatch", mapOf(
                "installed" to installedVersion,
                "required" to JXBROWSER_VERSION
            ))
            return false
        }

        // On macOS, verify the executable has proper permissions
        // This catches cached Chromium from older versions that didn't set execute bit correctly
        if (System.getProperty("os.name").lowercase().contains("mac")) {
            val executableName = executableNameFile.readText().trim()
            val executablePath = dir.resolve("$executableName/Contents/MacOS/BOSS").toFile()
            if (executablePath.exists() && !executablePath.canExecute()) {
                logger.info(LogCategory.BROWSER, "Chromium executable missing execute permission, will re-download")
                return false
            }
        }

        return true
    }

    /**
     * Detect the current platform for download URL.
     * Must match the file names in BossConsole-Releases:
     * - boss-chromium-macos-arm64.zip
     * - boss-chromium-macos-x64.zip
     * - boss-chromium-windows-x64.zip
     * - boss-chromium-windows-arm64.zip
     * - boss-chromium-linux-x64.zip
     * - boss-chromium-linux-arm64.zip
     */
    fun detectPlatform(): String {
        val os = System.getProperty("os.name").lowercase()
        val arch = System.getProperty("os.arch").lowercase()
        return when {
            os.contains("mac") && (arch.contains("aarch64") || arch.contains("arm64")) -> "macos-arm64"
            os.contains("mac") -> "macos-x64"
            os.contains("win") && (arch.contains("aarch64") || arch.contains("arm64")) -> "windows-arm64"
            os.contains("win") -> "windows-x64"
            os.contains("linux") && (arch.contains("aarch64") || arch.contains("arm64")) -> "linux-arm64"
            os.contains("linux") -> "linux-x64"
            else -> {
                logger.warn(LogCategory.BROWSER, "Unknown platform, defaulting to linux-x64", mapOf("os" to os, "arch" to arch))
                "linux-x64"
            }
        }
    }

    /**
     * Get the download URL for the current platform.
     */
    fun getDownloadUrl(): String {
        val platform = detectPlatform()
        return "$RELEASES_BASE_URL/chromium-v$JXBROWSER_VERSION/boss-chromium-$platform.zip"
    }

    /**
     * Download and install Chromium with progress reporting
     *
     * @param onProgress Callback for download progress updates
     * @return Result containing the installation path on success, or an exception on failure
     */
    suspend fun downloadChromium(onProgress: (DownloadProgress) -> Unit): Result<Path> =
        withContext(Dispatchers.IO) {
            val targetDir = getChromiumDir()
            val url = getDownloadUrl()

            logger.info(LogCategory.BROWSER, "Downloading BOSS-branded Chromium", mapOf(
                "url" to url,
                "targetDir" to targetDir.toString()
            ))

            try {
                // Create parent directories
                Files.createDirectories(targetDir.parent)

                // Download to temp file with progress
                val tempFile = Files.createTempFile("boss-chromium-", ".zip")
                try {
                    downloadWithProgress(url, tempFile, onProgress)

                    // Update status to extracting
                    onProgress(DownloadProgress(0, 0, isExtracting = true))

                    // Delete existing directory if present
                    if (targetDir.toFile().exists()) {
                        targetDir.toFile().deleteRecursively()
                    }

                    // Extract
                    extractZip(tempFile, targetDir)

                    // Verify extraction produced executable.name
                    val executableNameFile = targetDir.resolve("executable.name").toFile()
                    if (!executableNameFile.exists()) {
                        throw IllegalStateException(
                            "Extraction completed but executable.name not found. " +
                            "The downloaded archive may be corrupted."
                        )
                    }

                    // Write version file to track installed version
                    targetDir.resolve(VERSION_FILE).toFile().writeText(JXBROWSER_VERSION)
                    logger.debug(LogCategory.BROWSER, "Version file written", mapOf("version" to JXBROWSER_VERSION))

                    // Clean up old JxBrowser default Chromium directory if it exists
                    cleanupOldChromium()

                    // Small delay after extraction to let file system sync
                    // This helps avoid a race condition in JxBrowser's IPC layer
                    // that can cause crashes on first launch after extraction
                    kotlinx.coroutines.delay(500)

                    logger.info(LogCategory.BROWSER, "BOSS-branded Chromium installed successfully", mapOf("path" to targetDir.toString()))
                    onProgress(DownloadProgress(0, 0, isComplete = true))
                    Result.success(targetDir)
                } finally {
                    // Clean up temp file
                    try {
                        Files.deleteIfExists(tempFile)
                    } catch (e: Exception) {
                        logger.debug(LogCategory.BROWSER, "Could not delete temp file")
                    }
                }
            } catch (e: Exception) {
                logger.error(LogCategory.BROWSER, "Chromium download failed", error = e)
                onProgress(DownloadProgress(0, 0, error = e.message ?: "Unknown error"))
                Result.failure(e)
            }
        }

    /**
     * Download a file with progress reporting
     */
    private fun downloadWithProgress(
        urlString: String,
        targetPath: Path,
        onProgress: (DownloadProgress) -> Unit
    ) {
        val url = URL(urlString)
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 30000
        connection.readTimeout = 60000
        connection.setRequestProperty("User-Agent", "BOSS-App")

        // Follow redirects (GitHub releases use redirects)
        connection.instanceFollowRedirects = true

        try {
            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw IllegalStateException("HTTP error: $responseCode ${connection.responseMessage}")
            }

            val totalBytes = connection.contentLengthLong
            var bytesDownloaded = 0L

            BufferedInputStream(connection.inputStream).use { input ->
                FileOutputStream(targetPath.toFile()).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        bytesDownloaded += bytesRead

                        // Report progress
                        onProgress(DownloadProgress(bytesDownloaded, totalBytes))
                    }
                }
            }

            logger.debug(LogCategory.BROWSER, "Download complete", mapOf("sizeMB" to bytesDownloaded / (1024 * 1024)))
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Extract a zip file to a target directory
     */
    private fun extractZip(zipPath: Path, targetDir: Path) {
        logger.debug(LogCategory.BROWSER, "Extracting Chromium", mapOf("targetDir" to targetDir.toString()))
        Files.createDirectories(targetDir)

        ZipInputStream(Files.newInputStream(zipPath)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val targetPath = targetDir.resolve(entry.name).normalize()

                // Security check: prevent zip slip attack
                if (!targetPath.startsWith(targetDir)) {
                    throw SecurityException("Zip entry outside target directory: ${entry.name}")
                }

                if (entry.isDirectory) {
                    Files.createDirectories(targetPath)
                } else {
                    // Ensure parent directories exist
                    Files.createDirectories(targetPath.parent)

                    Files.newOutputStream(targetPath).use { output ->
                        zis.copyTo(output)
                    }

                    // Preserve executable bit on Unix
                    if (!System.getProperty("os.name").lowercase().contains("win")) {
                        val name = entry.name.lowercase()
                        // Check if this is an executable file:
                        // - Files inside macOS .app/Contents/MacOS/ directories
                        // - Files containing "chromium" in the name
                        // - Shared libraries (.so files)
                        // - Shell scripts (.sh files)
                        // - Files without extensions (common for Unix executables)
                        val isMacOSExecutable = name.contains(".app/contents/macos/")
                        val isChromium = name.contains("chromium") || name.contains("boss")
                        val isSharedLib = name.endsWith(".so")
                        val isShellScript = name.endsWith(".sh")
                        // For "no extension" check, only look at the filename not the full path
                        val fileName = targetPath.fileName.toString()
                        val hasNoExtension = !fileName.contains(".")

                        if (isMacOSExecutable || isChromium || isSharedLib || isShellScript || hasNoExtension) {
                            targetPath.toFile().setExecutable(true)
                        }
                    }
                }

                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        logger.debug(LogCategory.BROWSER, "Extraction complete")
    }

    /**
     * Clean up old JxBrowser default Chromium directory to save disk space.
     * This removes the unbranded Chromium that JxBrowser may have downloaded
     * before we switched to branded Chromium.
     */
    private fun cleanupOldChromium() {
        val oldDir = Paths.get(System.getProperty("user.home"), ".boss", "jxbrowser-chromium")
        if (oldDir.toFile().exists()) {
            logger.debug(LogCategory.BROWSER, "Cleaning up old JxBrowser Chromium", mapOf("path" to oldDir.toString()))
            try {
                oldDir.toFile().deleteRecursively()
                logger.info(LogCategory.BROWSER, "Old Chromium directory cleaned up (~500MB freed)")
            } catch (e: Exception) {
                logger.warn(LogCategory.BROWSER, "Could not clean up old Chromium", error = e)
                // Non-fatal - don't fail the download
            }
        }
    }
}
