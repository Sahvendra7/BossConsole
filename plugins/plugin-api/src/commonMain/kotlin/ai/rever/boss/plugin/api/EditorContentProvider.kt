package ai.rever.boss.plugin.api

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Provider interface for code editor content.
 * This allows the code editor tab to be loaded as a dynamic plugin.
 */
interface EditorContentProvider {
    /**
     * Display code editor content with syntax highlighting and editing capabilities.
     *
     * @param content The file content to display
     * @param onContentChange Callback when content changes
     * @param language The programming language for syntax highlighting
     * @param filePath The path to the file being edited
     * @param projectPath The project root path
     * @param modifier Modifier for the editor
     * @param onModifiedStateChange Callback when modification state changes
     * @param onSaveRequested Callback when save is requested (returns success)
     */
    @Composable
    fun CodeEditorContent(
        content: String,
        onContentChange: (String) -> Unit,
        language: String,
        filePath: String,
        projectPath: String,
        modifier: Modifier,
        onModifiedStateChange: (Boolean) -> Unit,
        onSaveRequested: suspend () -> Boolean
    )

    /**
     * Read file content with size validation.
     *
     * @param filePath Path to the file
     * @param maxSize Maximum allowed file size in bytes
     * @return FileReadResult indicating success, size limit exceeded, or error
     */
    fun readFileContent(filePath: String, maxSize: Long = 100_000_000): FileReadResult

    /**
     * Write content to a file.
     *
     * @param filePath Path to the file
     * @param content Content to write
     * @return true if successful, false otherwise
     */
    fun writeFileContent(filePath: String, content: String): Boolean

    /**
     * Detect the programming language based on file path.
     *
     * @param filePath Path to the file
     * @return Language identifier string (e.g., "kotlin", "java", "python")
     */
    fun detectLanguage(filePath: String): String
}

/**
 * Result of attempting to read a file with size validation.
 */
sealed class FileReadResult {
    /**
     * File was read successfully.
     */
    data class Success(val content: String) : FileReadResult()

    /**
     * File exceeds the maximum allowed size.
     */
    data class FileTooLarge(val sizeBytes: Long, val maxSizeBytes: Long) : FileReadResult()

    /**
     * An error occurred reading the file.
     */
    data class Error(val message: String) : FileReadResult()

    /**
     * The file does not exist.
     */
    data object FileNotFound : FileReadResult()
}
