package ai.rever.boss.components.plugin.tab_types

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

// For iOS, file reading would need proper file access
actual fun readFileContent(filePath: String): String? {
    // TODO: Implement iOS file reading
    return null
}

// iOS implementations - using default values since settings persistence would be different on iOS
actual fun getCodeEditorFontSize(): Int = 14
actual fun getCodeEditorFontFamily(): FontFamily = FontFamily.Monospace
actual fun getCodeEditorBackgroundColor(): Color = Color(0xFF_1E1E1E)
actual fun getCodeEditorTextColor(): Color = Color(0xFF_D4D4D4)
actual fun getCodeEditorLineNumberColor(): Color = Color(0xFF_858585)
actual fun getCodeEditorLineNumberBgColor(): Color = Color(0xFF_2D2D30)
actual fun getCodeEditorKeywordColor(): Color = Color(0xFF_569CD6)
actual fun getCodeEditorStringColor(): Color = Color(0xFF_CE9178)
actual fun getCodeEditorCommentColor(): Color = Color(0xFF_6A9955)