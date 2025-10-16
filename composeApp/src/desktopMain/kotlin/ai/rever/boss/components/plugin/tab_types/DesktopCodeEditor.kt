package ai.rever.boss.components.plugin.tab_types

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

// Actual implementations that use the settings
actual fun getCodeEditorFontSize(): Int = CodeEditorSettings.fontSize
actual fun getCodeEditorFontFamily(): FontFamily = CodeEditorSettings.getFontFamily()
actual fun getCodeEditorBackgroundColor(): Color = CodeEditorSettings.getBackgroundColor()
actual fun getCodeEditorTextColor(): Color = CodeEditorSettings.getTextColor()
actual fun getCodeEditorLineNumberColor(): Color = CodeEditorSettings.getLineNumberColor()
actual fun getCodeEditorLineNumberBgColor(): Color = CodeEditorSettings.getLineNumberBgColor()
actual fun getCodeEditorKeywordColor(): Color = CodeEditorSettings.getKeywordColor()
actual fun getCodeEditorCommentColor(): Color = CodeEditorSettings.getCommentColor()
