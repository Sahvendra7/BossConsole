package ai.rever.boss.platform

import java.io.File
import java.io.IOException

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
        try {
            val osName = System.getProperty("os.name").lowercase()
            val file = File(filePath)

            when {
                osName.contains("mac") -> {
                    // macOS: Use 'open -R' to reveal in Finder
                    Runtime.getRuntime().exec(arrayOf("open", "-R", file.absolutePath))
                }

                osName.contains("windows") -> {
                    // Windows: Use 'explorer /select,' to select in Explorer
                    Runtime.getRuntime().exec(arrayOf("explorer.exe", "/select,", file.absolutePath))
                }

                osName.contains("linux") -> {
                    // Linux: Open parent directory (can't select specific file universally)
                    val parentDir = file.parentFile?.absolutePath ?: return
                    Runtime.getRuntime().exec(arrayOf("xdg-open", parentDir))
                }

                else -> {
                    println("Reveal in folder not supported on this OS: $osName")
                }
            }
        } catch (e: IOException) {
            println("Failed to reveal file in folder: ${e.message}")
        }
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
                println("Cannot open file - does not exist: $filePath")
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
                    println("Open file not supported on this OS: $osName")
                }
            }
        } catch (e: IOException) {
            println("Failed to open file: ${e.message}")
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
    fun hasSufficientDiskSpace(destinationPath: String, requiredBytes: Long): Boolean {
        return try {
            val file = File(destinationPath)
            val parentDir = file.parentFile ?: return true // Can't check, assume OK

            val usableSpace = parentDir.usableSpace
            val safetyBuffer = 100 * 1024 * 1024L // 100MB buffer

            usableSpace >= (requiredBytes + safetyBuffer)
        } catch (e: Exception) {
            println("Error checking disk space: ${e.message}")
            true // Assume OK if we can't check
        }
    }

    /**
     * Generates a unique file path by appending (1), (2), etc. if file already exists.
     * Example: "file.txt" -> "file (1).txt" -> "file (2).txt"
     *
     * @param directory Directory where file will be saved
     * @param fileName Original file name
     * @return Absolute path to a unique file (may be the original if no collision)
     */
    fun generateUniqueFilePath(directory: String, fileName: String): String {
        val dir = File(directory)

        // Ensure directory exists
        if (!dir.exists()) {
            dir.mkdirs()
        }

        var file = File(dir, fileName)

        // If no collision, return original path
        if (!file.exists()) {
            return file.absolutePath
        }

        // Handle collision with incrementing counter
        val extension = file.extension
        val nameWithoutExtension = file.nameWithoutExtension
        var counter = 1

        do {
            val newName = if (extension.isNotEmpty()) {
                "$nameWithoutExtension ($counter).$extension"
            } else {
                "$nameWithoutExtension ($counter)"
            }
            file = File(dir, newName)
            counter++
        } while (file.exists() && counter < 1000) // Prevent infinite loop

        return file.absolutePath
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
            println("Failed to create parent directory: ${e.message}")
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
                println("Cleaned up partial download: $filePath")
            }
        } catch (e: Exception) {
            println("Failed to clean up partial file: ${e.message}")
        }
    }

    /**
     * Checks if a directory is writable.
     *
     * @param directoryPath Path to the directory
     * @return true if directory is writable, false otherwise
     */
    fun isDirectoryWritable(directoryPath: String): Boolean {
        return try {
            val dir = File(directoryPath)
            dir.isDirectory && dir.canWrite()
        } catch (e: Exception) {
            false
        }
    }
}
