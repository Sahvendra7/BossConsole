package ai.rever.boss.components.events

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class FileOpenEvent(
    val filePath: String,
    val fileName: String,
    val line: Int = 0,    // 1-based line to navigate to (0 = don't navigate)
    val column: Int = 0   // 1-based column to navigate to (0 = don't navigate)
)

/**
 * Strips the file protocol prefix from a path if present.
 * Handles various file URI formats:
 * - file:///path (Unix absolute, most common)
 * - file://path (some systems)
 * - file:/path (shorthand)
 * - file:path (minimal)
 *
 * Path sanitization note: This function only strips the prefix. Callers should
 * validate the resulting path using [validateFilePath] before opening files.
 *
 * Special character handling: File paths with spaces, Unicode characters, or other
 * special characters are preserved as-is after stripping the prefix. The underlying
 * file system APIs handle these correctly.
 */
fun stripFilePrefix(path: String): String {
    return when {
        path.startsWith("file:///") -> path.removePrefix("file://")  // Keep leading / for absolute path
        path.startsWith("file://") -> path.removePrefix("file://")
        path.startsWith("file:/") -> path.removePrefix("file:")
        path.startsWith("file:") -> path.removePrefix("file:")
        else -> path
    }
}

/**
 * Result of file path validation.
 */
sealed class FileValidationResult {
    data class Valid(val canonicalPath: String) : FileValidationResult()
    data class Invalid(val reason: String) : FileValidationResult()
}

/**
 * Validates a file path for safety and existence.
 *
 * Checks performed:
 * 1. Path is not empty
 * 2. File exists on disk
 * 3. Path points to a file (not a directory)
 * 4. File is readable
 *
 * Note on path traversal: We use canonicalFile to resolve ".." sequences,
 * but we don't restrict which directories can be accessed since the editor
 * operates in user context and should be able to open any file the user can access.
 * The main protection is against non-existent files and directories.
 *
 * @param filePath The file path to validate (should have file: prefix already stripped)
 * @return FileValidationResult.Valid with canonical path, or FileValidationResult.Invalid with reason
 */
fun validateFilePath(filePath: String): FileValidationResult {
    if (filePath.isBlank()) {
        return FileValidationResult.Invalid("Empty file path")
    }

    return try {
        val file = java.io.File(filePath).canonicalFile

        when {
            !file.exists() -> FileValidationResult.Invalid("File does not exist: ${file.absolutePath}")
            !file.isFile -> FileValidationResult.Invalid("Not a file (may be a directory): ${file.absolutePath}")
            !file.canRead() -> FileValidationResult.Invalid("File is not readable: ${file.absolutePath}")
            else -> FileValidationResult.Valid(file.absolutePath)
        }
    } catch (e: java.io.IOException) {
        FileValidationResult.Invalid("Invalid file path: ${e.message}")
    } catch (e: SecurityException) {
        FileValidationResult.Invalid("Access denied: ${e.message}")
    }
}

object FileEventBus {
    private val _fileOpenEvents = MutableSharedFlow<FileOpenEvent>(
        replay = 0,  // Don't replay past events to new subscribers (new windows)
        extraBufferCapacity = 10  // Buffer up to 10 events if collector not ready yet
    )
    val fileOpenEvents: SharedFlow<FileOpenEvent> = _fileOpenEvents.asSharedFlow()

    /**
     * Opens a file in the editor.
     *
     * @param filePath The file path to open. May include "file:" prefix (will be stripped).
     * @param line 1-based line number to navigate to (0 = don't navigate)
     * @param column 1-based column number to navigate to (0 = don't navigate)
     */
    suspend fun openFile(filePath: String, line: Int = 0, column: Int = 0) {
        // Strip file: prefix if present (may come from terminal hyperlinks)
        val cleanPath = stripFilePrefix(filePath)
        val fileName = cleanPath.substringAfterLast('/').ifEmpty { "untitled" }
        println("[FileEventBus] openFile: $cleanPath:$line:$column")
        _fileOpenEvents.emit(FileOpenEvent(cleanPath, fileName, line, column))
    }
}
