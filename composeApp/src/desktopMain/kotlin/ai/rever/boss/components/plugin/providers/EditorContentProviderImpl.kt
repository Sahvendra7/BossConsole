package ai.rever.boss.components.plugin.providers

import ai.rever.boss.components.events.FileEventBus
import ai.rever.boss.components.plugin.tab_types.DesktopCodeEditorUI
import ai.rever.boss.components.plugin.tab_types.EditorSearchEventBus
import ai.rever.boss.components.plugin.tab_types.readFileContentSafe
import ai.rever.boss.components.plugin.tab_types.writeFileContent
import ai.rever.boss.font.FontManager
import ai.rever.boss.plugin.api.EditorContentProvider
import ai.rever.boss.plugin.api.FileReadResult
import ai.rever.boss.plugin.api.MainFunctionInfo
import ai.rever.boss.plugin.run.DetectedMainFunction
import ai.rever.boss.plugin.run.Language
import ai.rever.boss.plugin.run.RunConfigurationType
import ai.rever.boss.plugin.run.RunConfiguration
import ai.rever.boss.components.events.RunEventBus
import ai.rever.boss.run.MainFunctionDetectorProvider
import ai.rever.bosseditor.settings.EditorSettings
import ai.rever.bosseditor.settings.EditorSettingsManager
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

    private val mgr get() = EditorSettingsManager.instance
    private val settings get() = mgr.settings.value

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

    override fun isCodeFoldingEnabled(): Boolean = settings.foldingEnabled

    override fun setCodeFoldingEnabled(enabled: Boolean) {
        mgr.updateSetting { it.copy(foldingEnabled = enabled) }
    }

    override fun isBracketMatchingEnabled(): Boolean = settings.bracketMatchingEnabled

    override fun setBracketMatchingEnabled(enabled: Boolean) {
        mgr.updateSetting { it.copy(bracketMatchingEnabled = enabled) }
    }

    // ============ Phase 1: Advanced Editor Toggles ============

    override fun isMarkOccurrencesEnabled(): Boolean = settings.markOccurrencesEnabled

    override fun setMarkOccurrencesEnabled(enabled: Boolean) {
        mgr.updateSetting { it.copy(markOccurrencesEnabled = enabled) }
    }

    override fun isCurrentLineHighlightEnabled(): Boolean = settings.highlightCurrentLine

    override fun setCurrentLineHighlightEnabled(enabled: Boolean) {
        mgr.updateSetting { it.copy(highlightCurrentLine = enabled) }
    }

    // ============ Phase 1: PSI Navigation APIs ============

    override fun isNavigationEnabled(): Boolean = navigationEnabled

    override fun setNavigationEnabled(enabled: Boolean) {
        navigationEnabled = enabled
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

    override fun getAvailableThemes(): List<String> = EditorSettings.availableThemes

    override fun getCurrentTheme(): String = settings.themeName

    override fun setTheme(theme: String) {
        if (theme in getAvailableThemes()) {
            mgr.updateSetting { it.copy(themeName = theme) }
        }
    }

    // ============ Phase 3: Font Customization ============

    override fun getFontSize(): Int = settings.fontSize.toInt()

    override fun setFontSize(size: Int) {
        if (size in 8..72) {
            mgr.updateSetting { it.copy(fontSize = size.toFloat()) }
        }
    }

    override fun getFontFamily(): String = settings.fontFamily ?: FontManager.BUNDLED_JETBRAINS_MONO

    override fun setFontFamily(family: String) {
        mgr.updateSetting { it.copy(fontFamily = family) }
    }

    override fun getAvailableFonts(): List<String> = FontManager.getAvailableMonospaceFonts()

    // ============ Phase 3: Minimap Settings ============

    override fun isMinimapVisible(): Boolean = settings.showMinimap

    override fun setMinimapVisible(visible: Boolean) {
        mgr.updateSetting { it.copy(showMinimap = visible) }
    }

    override fun getMinimapWidth(): Int = settings.minimapWidth

    override fun setMinimapWidth(width: Int) {
        if (width in 40..300) {
            mgr.updateSetting { it.copy(minimapWidth = width) }
        }
    }

    // ============ Phase 3: Line Spacing ============

    override fun getLineSpacing(): Float = settings.lineSpacing

    override fun setLineSpacing(spacing: Float) {
        if (spacing in 1.0f..3.0f) {
            mgr.updateSetting { it.copy(lineSpacing = spacing) }
        }
    }

    // ============ Phase 3: Undo/Redo ============

    override fun undo(): Boolean = EditorSearchEventBus.undo()

    override fun redo(): Boolean = EditorSearchEventBus.redo()

    override fun canUndo(): Boolean = EditorSearchEventBus.canUndo()

    override fun canRedo(): Boolean = EditorSearchEventBus.canRedo()

    // ============ Phase 3: Search State ============

    override fun getSearchQuery(): String? = EditorSearchEventBus.getSearchQuery()

    override fun getSearchMatchCount(): Int = EditorSearchEventBus.getSearchMatchCount()

    override fun getCurrentSearchMatchIndex(): Int = EditorSearchEventBus.getCurrentSearchMatchIndex()

    // ============ Phase 4: Code Completion (E13) ============

    override fun registerCompletionProvider(id: String, provider: Any) {
        if (provider is ai.rever.bosseditor.features.CompletionProvider) {
            ai.rever.bosseditor.features.CompletionProviderRegistry.register(id, provider)
        }
    }

    override fun unregisterCompletionProvider(id: String): Boolean {
        return ai.rever.bosseditor.features.CompletionProviderRegistry.unregister(id)
    }

    // ============ Phase 4: Custom Color Schemes (E15) ============

    override fun registerColorScheme(name: String, baseTheme: String, colorOverrides: Map<String, String>): Boolean {
        return try {
            val base = ai.rever.bosseditor.theme.EditorTheme.forName(baseTheme)
            val builder = ai.rever.bosseditor.theme.EditorThemeBuilder(name, base)

            for ((key, hexValue) in colorOverrides) {
                val color = try {
                    val hex = hexValue.removePrefix("#")
                    androidx.compose.ui.graphics.Color(hex.toLong(16) or 0xFF000000)
                } catch (e: Exception) { continue }

                when (key.lowercase()) {
                    "background" -> builder.background(color)
                    "text" -> builder.text(color)
                    "caret" -> builder.caret(color)
                    "keyword" -> builder.keyword(color)
                    "function" -> builder.function(color)
                    "string" -> builder.string(color)
                    "number" -> builder.number(color)
                    "comment" -> builder.comment(color)
                    "annotation" -> builder.annotation(color)
                    "variable" -> builder.variable(color)
                    "property" -> builder.property(color)
                    "operator" -> builder.operator(color)
                    "error" -> builder.error(color)
                    "datatype" -> builder.dataType(color)
                    "doccomment" -> builder.docComment(color)
                }
            }

            ai.rever.bosseditor.theme.EditorTheme.registerTheme(builder.build())
            true
        } catch (e: Exception) {
            false
        }
    }

    override fun unregisterColorScheme(name: String): Boolean {
        return ai.rever.bosseditor.theme.EditorTheme.unregisterTheme(name)
    }

    companion object {
        // Runtime toggle for PSI navigation (not a BossEditor setting)
        private var navigationEnabled: Boolean = true
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
