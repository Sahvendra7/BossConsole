package ai.rever.boss.components.plugin.tab_types

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import java.io.File

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

actual fun writeFileContent(filePath: String, content: String): Boolean {
    return try {
        val file = File(filePath)
        // Create parent directories if they don't exist
        file.parentFile?.mkdirs()
        file.writeText(content)
        true
    } catch (e: Exception) {
        println("[DesktopCodeEditor] Error writing file: ${e.message}")
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
