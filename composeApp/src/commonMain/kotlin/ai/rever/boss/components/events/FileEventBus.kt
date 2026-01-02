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
 * Strips the "file:" prefix from a path if present.
 * This handles paths from terminal hyperlinks which may include the protocol prefix.
 *
 * Path sanitization note: This function only strips the "file:" prefix and does not
 * perform directory traversal prevention. File paths are trusted as they come from:
 * 1. Terminal output (user's own shell)
 * 2. BossTerm's hyperlink detection (validates file existence)
 * Directory traversal is not a concern since the editor operates in user context
 * and can legitimately access any file the user can access.
 *
 * Special character handling: File paths with spaces, Unicode characters, or other
 * special characters are preserved as-is after stripping the prefix. The underlying
 * file system APIs handle these correctly.
 */
fun stripFilePrefix(path: String): String = path.removePrefix("file:")

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
