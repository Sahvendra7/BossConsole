package ai.rever.boss.components.plugin.providers

import ai.rever.boss.components.plugin.tab_types.DesktopCodeEditorUI
import ai.rever.boss.components.plugin.tab_types.readFileContentSafe
import ai.rever.boss.components.plugin.tab_types.writeFileContent
import ai.rever.boss.plugin.api.EditorContentProvider
import ai.rever.boss.plugin.api.FileReadResult
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ai.rever.boss.components.plugin.tab_types.FileReadResult as InternalFileReadResult

/**
 * Desktop implementation of EditorContentProvider.
 *
 * This provider wraps the existing PlatformCodeEditorUI (DesktopCodeEditorUI) and
 * file I/O functions to enable dynamic editor plugins to access editor functionality.
 */
class EditorContentProviderImpl : EditorContentProvider {

    @Composable
    override fun CodeEditorContent(
        content: String,
        onContentChange: (String) -> Unit,
        language: String,
        filePath: String,
        projectPath: String,
        modifier: Modifier,
        onModifiedStateChange: (Boolean) -> Unit,
        onSaveRequested: suspend () -> Boolean
    ) {
        DesktopCodeEditorUI(
            content = content,
            onContentChange = onContentChange,
            language = language,
            filePath = filePath,
            projectPath = projectPath,
            modifier = modifier,
            onModifiedStateChange = onModifiedStateChange,
            onSaveRequested = onSaveRequested
        )
    }

    override fun readFileContent(filePath: String, maxSize: Long): FileReadResult {
        return when (val result = readFileContentSafe(filePath, maxSize)) {
            is InternalFileReadResult.Success -> FileReadResult.Success(result.content)
            is InternalFileReadResult.FileTooLarge -> FileReadResult.FileTooLarge(result.sizeBytes, result.maxSizeBytes)
            is InternalFileReadResult.Error -> FileReadResult.Error(result.message)
            is InternalFileReadResult.FileNotFound -> FileReadResult.FileNotFound
        }
    }

    override fun writeFileContent(filePath: String, content: String): Boolean {
        return writeFileContent(filePath, content)
    }

    override fun detectLanguage(filePath: String): String {
        val extension = filePath.substringAfterLast('.', "").lowercase()
        return when (extension) {
            "kt", "kts" -> "kotlin"
            "java" -> "java"
            "js", "jsx" -> "javascript"
            "ts", "tsx" -> "typescript"
            "py" -> "python"
            "json" -> "json"
            "xml" -> "xml"
            "html", "htm" -> "html"
            "css" -> "css"
            "md" -> "markdown"
            "toml" -> "toml"
            "gradle" -> "groovy"
            "swift" -> "swift"
            "c", "h" -> "c"
            "cpp", "cc", "cxx", "hpp" -> "cpp"
            "rs" -> "rust"
            "go" -> "go"
            "rb" -> "ruby"
            "php" -> "php"
            "sh", "bash" -> "bash"
            "yml", "yaml" -> "yaml"
            "sql" -> "sql"
            "r" -> "r"
            "scala" -> "scala"
            else -> "text"
        }
    }
}
