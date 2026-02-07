package ai.rever.boss.components.plugin.providers

import ai.rever.boss.components.events.FileEventBus
import ai.rever.boss.components.plugin.tab_types.CodeEditorSettings
import ai.rever.boss.components.plugin.tab_types.CodeEditorSettingsManager
import ai.rever.boss.components.plugin.tab_types.DesktopCodeEditorUI
import ai.rever.boss.components.plugin.tab_types.EditorSearchEventBus
import ai.rever.boss.components.plugin.tab_types.readFileContentSafe
import ai.rever.boss.components.plugin.tab_types.writeFileContent
import ai.rever.boss.plugin.api.EditorContentProvider
import ai.rever.boss.plugin.api.FileReadResult
import ai.rever.boss.plugin.api.MainFunctionInfo
import ai.rever.boss.plugin.run.DetectedMainFunction
import ai.rever.boss.plugin.run.Language
import ai.rever.boss.plugin.run.RunConfigurationType
import ai.rever.boss.plugin.run.RunConfiguration
import ai.rever.boss.components.events.RunEventBus
import ai.rever.boss.run.MainFunctionDetectorProvider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
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
        onSaveRequested: suspend () -> Boolean,
        onCursorPositionChange: ((line: Int, column: Int) -> Unit)?,
        onRunFunction: ((MainFunctionInfo) -> Unit)?,
        onNavigate: ((filePath: String, line: Int, column: Int) -> Unit)?,
        showRunGutter: Boolean
    ) {
        DesktopCodeEditorUI(
            content = content,
            onContentChange = onContentChange,
            language = language,
            filePath = filePath,
            projectPath = projectPath,
            modifier = modifier,
            onModifiedStateChange = onModifiedStateChange,
            onSaveRequested = onSaveRequested,
            onCursorPositionChange = onCursorPositionChange,
            onRunFunction = onRunFunction,
            onNavigate = onNavigate,
            showRunGutter = showRunGutter
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

    // ============ Phase 1: Find/Replace and Navigation APIs ============

    override fun showFindDialog() {
        EditorSearchEventBus.triggerFind()
    }

    override fun showReplaceDialog() {
        EditorSearchEventBus.triggerReplace()
    }

    override fun goToLine(line: Int) {
        EditorSearchEventBus.triggerGoToLine()
    }

    override fun findNext() {
        EditorSearchEventBus.triggerFindNext()
    }

    override fun findPrevious() {
        EditorSearchEventBus.triggerFindPrevious()
    }

    // ============ Phase 1: Editor Feature Toggles ============

    override fun isCodeFoldingEnabled(): Boolean = codeFoldingEnabled

    override fun setCodeFoldingEnabled(enabled: Boolean) {
        codeFoldingEnabled = enabled
    }

    override fun isBracketMatchingEnabled(): Boolean = bracketMatchingEnabled

    override fun setBracketMatchingEnabled(enabled: Boolean) {
        bracketMatchingEnabled = enabled
    }

    // ============ Phase 1: Advanced Editor Toggles ============

    override fun isMarkOccurrencesEnabled(): Boolean = CodeEditorSettings.markOccurrences

    override fun setMarkOccurrencesEnabled(enabled: Boolean) {
        CodeEditorSettings.markOccurrences = enabled
        GlobalScope.launch {
            CodeEditorSettingsManager.saveSettings()
        }
    }

    override fun isCurrentLineHighlightEnabled(): Boolean = CodeEditorSettings.highlightCurrentLine

    override fun setCurrentLineHighlightEnabled(enabled: Boolean) {
        CodeEditorSettings.highlightCurrentLine = enabled
        GlobalScope.launch {
            CodeEditorSettingsManager.saveSettings()
        }
    }

    override fun isAutoIndentEnabled(): Boolean = CodeEditorSettings.autoIndent

    override fun setAutoIndentEnabled(enabled: Boolean) {
        CodeEditorSettings.autoIndent = enabled
        GlobalScope.launch {
            CodeEditorSettingsManager.saveSettings()
        }
    }

    // ============ Phase 1: PSI Navigation APIs ============

    override fun isNavigationEnabled(): Boolean = CodeEditorSettings.navigationEnabled

    override fun setNavigationEnabled(enabled: Boolean) {
        CodeEditorSettings.navigationEnabled = enabled
        GlobalScope.launch {
            CodeEditorSettingsManager.saveSettings()
        }
    }

    override fun navigateToDefinition(filePath: String, line: Int, column: Int) {
        GlobalScope.launch(Dispatchers.Main) {
            // Use empty string as sourceWindowId for plugin API calls where windowId is unknown
            // The event handler will use the active window in this case
            FileEventBus.openFile(filePath, line, column, sourceWindowId = "")
        }
    }

    // ============ Phase 2: Main Function Detection ============

    override fun detectMainFunctions(filePath: String, content: String): List<MainFunctionInfo> {
        return try {
            val detector = MainFunctionDetectorProvider.get()
            val langEnum = Language.fromFileName(filePath)
            val detected = detector.detectInFile(filePath, content, langEnum)
            detected.map { it.toMainFunctionInfo() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override fun executeMainFunction(mainFunction: MainFunctionInfo, projectPath: String, windowId: String?) {
        if (windowId == null) return

        GlobalScope.launch(Dispatchers.Main) {
            try {
                val detector = MainFunctionDetectorProvider.get()
                val actualProjectRoot = detector.findProjectRoot(mainFunction.filePath)
                val langEnum = Language.fromExtension(mainFunction.language)

                // Create a DetectedMainFunction from MainFunctionInfo
                val detected = DetectedMainFunction(
                    lineNumber = mainFunction.lineNumber,
                    functionName = mainFunction.functionName,
                    className = mainFunction.className,
                    packageName = null,
                    language = langEnum,
                    filePath = mainFunction.filePath
                )

                val command = detector.generateCommand(detected, actualProjectRoot)
                val configName = detected.toShortNameWithProject(actualProjectRoot)

                val config = RunConfiguration(
                    id = java.util.UUID.randomUUID().toString(),
                    name = configName,
                    type = RunConfigurationType.MAIN_FUNCTION,
                    filePath = mainFunction.filePath,
                    lineNumber = mainFunction.lineNumber,
                    language = langEnum,
                    command = command,
                    workingDirectory = actualProjectRoot,
                    isAutoDetected = true
                )

                RunEventBus.execute(config, sourceWindowId = windowId)
            } catch (e: Exception) {
                // Log error but don't crash
            }
        }
    }

    // ============ Phase 2: Theme Integration ============

    override fun getAvailableThemes(): List<String> = listOf(
        "Dark",
        "Light",
        "Dracula",
        "Monokai",
        "Solarized Dark",
        "Solarized Light"
    )

    override fun getCurrentTheme(): String = CodeEditorSettings.theme

    override fun setTheme(theme: String) {
        if (theme in getAvailableThemes()) {
            CodeEditorSettings.theme = theme
            // Settings will be persisted via CodeEditorSettingsManager
            GlobalScope.launch {
                CodeEditorSettingsManager.saveSettings()
            }
        }
    }

    // ============ Phase 3: Font Customization ============

    override fun getFontSize(): Int = CodeEditorSettings.fontSize

    override fun setFontSize(size: Int) {
        if (size in 8..72) {
            CodeEditorSettings.fontSize = size
            GlobalScope.launch {
                CodeEditorSettingsManager.saveSettings()
            }
        }
    }

    override fun getFontFamily(): String = CodeEditorSettings.fontFamily

    override fun setFontFamily(family: String) {
        CodeEditorSettings.fontFamily = family
        GlobalScope.launch {
            CodeEditorSettingsManager.saveSettings()
        }
    }

    override fun getAvailableFonts(): List<String> = CodeEditorSettings.getAvailableFonts()

    companion object {
        // Feature toggles (global state for now, could be per-editor in future)
        private var codeFoldingEnabled: Boolean = true
        private var bracketMatchingEnabled: Boolean = true
    }
}

/**
 * Extension function to convert DetectedMainFunction to MainFunctionInfo.
 */
private fun DetectedMainFunction.toMainFunctionInfo(): MainFunctionInfo {
    return MainFunctionInfo(
        filePath = this.filePath,
        lineNumber = this.lineNumber,
        functionName = this.functionName,
        language = this.language.name.lowercase(),
        className = this.className,
        metadata = mapOf(
            "packageName" to (this.packageName ?: "")
        )
    )
}
