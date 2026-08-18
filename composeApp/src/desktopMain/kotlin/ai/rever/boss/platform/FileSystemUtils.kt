package ai.rever.boss.platform

import ai.rever.boss.utils.logging.BossLogger
import ai.rever.boss.utils.logging.LogCategory
import ai.rever.boss.utils.revealInFileManager
import java.io.File
import java.io.IOException

private val logger = BossLogger.forComponent("FileSystemUtils")

/**
 * Cross-platform file system utilities for downloads.
 */
object FileSystemUtils {
    /**
     * Opens the file manager and reveals the specified file.
     * - macOS: Uses 'open -R' to reveal in Finder
     * - Windows: Uses 'explorer /select,' to select in Explorer
     * - Linux: Opens the parent directory in file manager (xdg-open)
     *
     * @param filePath Absolute path to the file to reveal
     */
    fun revealInFolder(filePath: String) {
        revealInFileManager(filePath)
    }

    /**
     * Opens the specified file with the system default application.
     *
     * @param filePath Absolute path to the file to open
     */
    fun openFile(filePath: String) {
        try {
            val osName = System.getProperty("os.name").lowercase()
            val file = File(filePath)

            if (!file.exists()) {
                logger.warn(LogCategory.FILE, "Cannot open file - does not exist", mapOf("path" to filePath))
                return
            }

            when {
                osName.contains("mac") -> {
                    Runtime.getRuntime().exec(arrayOf("open", file.absolutePath))
                }

                osName.contains("windows") -> {
                    Runtime.getRuntime().exec(arrayOf("cmd", "/c", "start", "", file.absolutePath))
                }

                osName.contains("linux") -> {
                    Runtime.getRuntime().exec(arrayOf("xdg-open", file.absolutePath))
                }

                else -> {
                    logger.warn(LogCategory.FILE, "Open file not supported on this OS", mapOf("os" to osName))
                }
            }
        } catch (e: IOException) {
            logger.warn(LogCategory.FILE, "Failed to open file", error = e)
        }
    }

    /**
     * Checks if there is sufficient disk space available for a download.
     * Includes a 100MB safety buffer.
     *
     * @param destinationPath Path where the file will be saved
     * @param requiredBytes Number of bytes needed for the download
     * @return true if sufficient space is available, false otherwise
     */
    fun hasSufficientDiskSpace(
        destinationPath: String,
        requiredBytes: Long,
    ): Boolean {
        return try {
            val file = File(destinationPath)
            val parentDir = file.parentFile ?: return true // Can't check, assume OK

            val usableSpace = parentDir.usableSpace
            val safetyBuffer = 100 * 1024 * 1024L // 100MB buffer

            usableSpace >= (requiredBytes + safetyBuffer)
        } catch (e: Exception) {
            logger.warn(LogCategory.FILE, "Error checking disk space", error = e)
            true // Assume OK if we can't check
        }
    }

    /**
     * Paths handed out by [generateUniqueFilePath] whose file does not exist yet.
     *
     * "Is the name free?" cannot be answered by [File.exists] alone. A download's file is not
     * created until bytes arrive, so two downloads of `report.pdf` started before either lands
     * both saw a free name and both resolved to the same path - the second silently truncating
     * the first. A claimed path is treated as taken until [releaseFilePath] is called, which
     * the download's terminal handlers do.
     */
    private val claimedPaths =
        java.util.concurrent.ConcurrentHashMap
            .newKeySet<String>()

    /** Highest `(n)` suffix tried before falling back to a name that cannot collide. */
    private const val MAX_COLLISION_SUFFIX = 1000

    /**
     * Generates a unique file path by appending (1), (2), etc. if the name is taken.
     * Example: "file.txt" -> "file (1).txt" -> "file (2).txt"
     *
     * The returned path is **claimed** until [releaseFilePath] is called for it, so a caller
     * that has not written its file yet still blocks a concurrent caller from the same name.
     *
     * @param directory Directory where file will be saved
     * @param fileName Original file name
     * @return Absolute path to a unique file (may be the original if no collision)
     */
    fun generateUniqueFilePath(
        directory: String,
        fileName: String,
    ): String {
        val dir = File(directory)

        // Ensure directory exists
        if (!dir.exists()) {
            dir.mkdirs()
        }

        val original = File(dir, fileName)
        val extension = original.extension
        val stem = original.nameWithoutExtension

        fun suffixed(suffix: String): File {
            val name = if (extension.isEmpty()) "$stem ($suffix)" else "$stem ($suffix).$extension"
            return File(dir, name)
        }

        val claimed =
            if (claim(original)) {
                original
            } else {
                (1 until MAX_COLLISION_SUFFIX)
                    .asSequence()
                    .map { suffixed(it.toString()) }
                    .firstOrNull { claim(it) }
                    ?: run {
                        // A thousand collisions is not a real directory, but the old loop
                        // *returned the colliding path* once it gave up, overwriting the very
                        // file the counter exists to protect. Fall back to a name nothing else
                        // holds instead.
                        logger.warn(
                            LogCategory.FILE,
                            "Exhausted numbered suffixes for a download name - using a unique suffix",
                            mapOf("fileName" to fileName),
                        )
                        suffixed(System.nanoTime().toString(radix = 16)).also { claim(it) }
                    }
            }
        return claimed.absolutePath
    }

    /** Take [file]'s path if nothing holds it and it is not already on disk. */
    private fun claim(file: File): Boolean {
        val path = file.absolutePath
        // add() before exists() so two threads cannot both pass the exists() check; the loser
        // gives the path back and moves on to the next suffix.
        if (!claimedPaths.add(path)) return false
        val free = !file.exists()
        if (!free) claimedPaths.remove(path)
        return free
    }

    /**
     * Give back a path claimed by [generateUniqueFilePath].
     *
     * Call this once the file exists (so [File.exists] takes over) or once the transfer that
     * claimed it has failed. Not calling it leaks one string per download for the life of the
     * process and pushes a later download of the same name onto a suffix it did not need.
     */
    fun releaseFilePath(path: String) {
        claimedPaths.remove(path)
    }

    /**
     * Ensures the parent directory of a file path exists, creating it if necessary.
     *
     * @param filePath Absolute path to a file
     * @return true if directory exists or was created, false on failure
     */
    fun ensureParentDirectoryExists(filePath: String): Boolean {
        return try {
            val file = File(filePath)
            val parentDir = file.parentFile ?: return true

            if (!parentDir.exists()) {
                parentDir.mkdirs()
            } else {
                true
            }
        } catch (e: Exception) {
            logger.warn(LogCategory.FILE, "Failed to create parent directory", error = e)
            false
        }
    }

    /**
     * Attempts to delete a partial/failed download file.
     * Silently fails if file doesn't exist or can't be deleted.
     *
     * @param filePath Path to the file to clean up
     */
    fun cleanupPartialFile(filePath: String) {
        try {
            val file = File(filePath)
            if (file.exists()) {
                file.delete()
                logger.debug(LogCategory.FILE, "Cleaned up partial download", mapOf("path" to filePath))
            }
        } catch (e: Exception) {
            logger.warn(LogCategory.FILE, "Failed to clean up partial file", error = e)
        }
    }

    /**
     * Checks if a directory is writable.
     *
     * @param directoryPath Path to the directory
     * @return true if directory is writable, false otherwise
     */
    fun isDirectoryWritable(directoryPath: String): Boolean =
        try {
            val dir = File(directoryPath)
            dir.isDirectory && dir.canWrite()
        } catch (e: Exception) {
            logger.debug(
                LogCategory.FILE,
                "Writability check failed - treating directory as not writable",
                mapOf("path" to directoryPath, "error" to e.toString()),
            )
            false
        }
}
