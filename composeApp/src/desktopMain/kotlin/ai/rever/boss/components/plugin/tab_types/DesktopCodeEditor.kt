package ai.rever.boss.components.plugin.tab_types

import ai.rever.boss.utils.logging.BossLogger
import ai.rever.boss.utils.logging.LogCategory
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import java.io.File

private val codeEditorLogger = BossLogger.forComponent("DesktopCodeEditor")

actual fun readFileContent(filePath: String): String? {
    return try {
        val file = File(filePath)
        if (file.exists() && file.isFile) {
            file.readText()
        } else {
            null
        }
    } catch (e: Exception) {
        null
    }
}

/**
 * Reads file content with size validation.
 * Files larger than maxSize will return FileTooLarge instead of loading.
 */
actual fun readFileContentSafe(filePath: String, maxSize: Long): FileReadResult {
    return try {
        val file = File(filePath)
        when {
            !file.exists() || !file.isFile -> FileReadResult.FileNotFound
            file.length() > maxSize -> FileReadResult.FileTooLarge(file.length(), maxSize)
            else -> {
                try {
                    FileReadResult.Success(file.readText())
                } catch (e: OutOfMemoryError) {
                    FileReadResult.Error("File too large to load into memory: ${e.message}")
                }
            }
        }
    } catch (e: Exception) {
        FileReadResult.Error(e.message ?: "Unknown error reading file")
    }
}

actual fun writeFileContent(filePath: String, content: String): Boolean {
    return try {
        val file = File(filePath)
        // Create parent directories if they don't exist
        file.parentFile?.mkdirs()
        file.writeText(content)
        true
    } catch (e: Exception) {
        codeEditorLogger.warn(LogCategory.EDITOR, "Error writing file", error = e)
        false
    }
}

// Actual implementations that use the settings
actual fun getCodeEditorFontSize(): Int = CodeEditorSettings.fontSize
actual fun getCodeEditorFontFamily(): FontFamily = CodeEditorSettings.getFontFamily()
actual fun getCodeEditorBackgroundColor(): Color = CodeEditorSettings.getBackgroundColor()
actual fun getCodeEditorTextColor(): Color = CodeEditorSettings.getTextColor()
actual fun getCodeEditorLineNumberColor(): Color = CodeEditorSettings.getLineNumberColor()
actual fun getCodeEditorLineNumberBgColor(): Color = CodeEditorSettings.getLineNumberBgColor()
actual fun getCodeEditorKeywordColor(): Color = CodeEditorSettings.getKeywordColor()
actual fun getCodeEditorCommentColor(): Color = CodeEditorSettings.getCommentColor()

/**
 * Desktop implementation uses RSyntaxTextArea via DesktopCodeEditorUI.
 */
@Composable
actual fun PlatformCodeEditorUI(
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
