package ai.rever.boss.components.plugin.tab_types

import ai.rever.boss.editor.SemanticAdapterFactory
import ai.rever.boss.psi.NavigationEvent
import ai.rever.boss.run.DetectedMainFunction
import ai.rever.boss.run.Language
import ai.rever.boss.run.MainFunctionDetectorProvider
import ai.rever.bosseditor.compose.BossEditor
import ai.rever.bosseditor.core.EditorPosition
import ai.rever.bosseditor.core.EditorRange
import ai.rever.bosseditor.core.EditorState
import ai.rever.bosseditor.highlight.LexerState
import ai.rever.bosseditor.highlight.Token
import ai.rever.bosseditor.highlight.TokenType
import ai.rever.bosseditor.highlight.lexers.KotlinLexer
import ai.rever.bosseditor.rendering.EditorToken
import ai.rever.bosseditor.theme.EditorTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * BossEditor integration layer for BOSS application.
 *
 * This composable provides a drop-in replacement for RSyntaxEditorWithGutter,
 * using the native Compose Canvas-based BossEditor instead of RSyntaxTextArea.
 *
 * ## Features
 * - Native Compose rendering (no Swing interop)
 * - Syntax highlighting via BossEditor lexers
 * - Semantic highlighting for Kotlin (via PSI integration)
 * - Run gutter icons for detected main functions
 * - Theme integration with BOSS themes
 *
 * @param content The file content to display
 * @param onContentChange Callback when content changes
 * @param language The programming language for syntax highlighting
 * @param filePath The path to the file being edited
 * @param projectPath The project root path (for running detected functions)
 * @param modifier Modifier for the editor
 * @param isReadOnly Whether the editor is read-only
 * @param fontSize Font size in pixels
 * @param fontFamily Font family name
 * @param theme BOSS theme name
 * @param onCursorPositionChange Callback for cursor position changes (line, column)
 * @param onModifiedStateChange Callback when modification state changes
 * @param onRun Callback when a run icon is clicked
 * @param onNavigate Callback when Cmd+Click navigation is triggered (go-to-definition)
 */
@Composable
fun BossEditorIntegration(
    content: String,
    onContentChange: (String) -> Unit,
    language: String,
    filePath: String,
    projectPath: String = "",
    modifier: Modifier = Modifier,
    isReadOnly: Boolean = false,
    fontSize: Int = CodeEditorSettings.fontSize,
    fontFamily: String = CodeEditorSettings.fontFamily,
    theme: String = CodeEditorSettings.theme,
    onCursorPositionChange: (line: Int, column: Int) -> Unit = { _, _ -> },
    onModifiedStateChange: (Boolean) -> Unit = { },
    onRun: (DetectedMainFunction) -> Unit = { },
    onNavigate: (NavigationEvent) -> Unit = { }
) {
    val coroutineScope = rememberCoroutineScope()

    // Create editor state that persists across recompositions
    val editorState = remember(filePath) {
        EditorState(content)
    }

    // Track original content for modification detection
    var originalContent by remember(filePath) { mutableStateOf(content) }

    // State for detected main functions
    var detectedMainFunctions by remember { mutableStateOf<List<DetectedMainFunction>>(emptyList()) }

    // Create lexer based on language (currently only Kotlin is supported)
    val lexer = remember(language) {
        when (language.lowercase()) {
            "kotlin", "kt", "kts" -> KotlinLexer()
            // Add more lexers as they are implemented
            else -> null
        }
    }

    // Create semantic adapter for Kotlin files
    val semanticAdapter = remember(filePath, editorState.document) {
        SemanticAdapterFactory.create(editorState.document, filePath)
    }

    // Map theme name to EditorTheme
    val editorTheme = remember(theme) {
        mapBossThemeToEditorTheme(theme)
    }

    // Map font family name to FontFamily
    val composeFontFamily = remember(fontFamily) {
        when (fontFamily.lowercase()) {
            "jetbrains mono", "jetbrainsmono" -> FontFamily.Monospace
            "fira code", "firacode" -> FontFamily.Monospace
            "source code pro" -> FontFamily.Monospace
            else -> FontFamily.Monospace
        }
    }

    // Update content from external source
    LaunchedEffect(content) {
        if (editorState.document.getText() != content) {
            editorState.document.setText(content)
        }
    }

    // Detect main functions when content changes
    LaunchedEffect(content, filePath) {
        if (filePath.isNotEmpty() && content.isNotEmpty()) {
            val currentFilePath = filePath
            withContext(Dispatchers.IO) {
                try {
                    val detector = MainFunctionDetectorProvider.get()
                    val langEnum = Language.fromFileName(currentFilePath)
                    val detected = detector.detectInFile(currentFilePath, content, langEnum)
                    withContext(Dispatchers.Main) {
                        if (filePath == currentFilePath) {
                            detectedMainFunctions = detected
                        }
                    }
                } catch (e: Exception) {
                    println("[BossEditorIntegration] Error detecting main functions: ${e.message}")
                    withContext(Dispatchers.Main) {
                        if (filePath == currentFilePath) {
                            detectedMainFunctions = emptyList()
                        }
                    }
                }
            }
        } else {
            detectedMainFunctions = emptyList()
        }
    }

    // Token provider combining lexer and semantic highlighting
    val tokenProvider: (Int) -> List<EditorToken> = remember(lexer, semanticAdapter) {
        { lineNumber ->
            // First try semantic tokens (higher priority)
            val semanticTokens = semanticAdapter?.getLineTokens(lineNumber)

            if (!semanticTokens.isNullOrEmpty()) {
                EditorToken.fromTokens(semanticTokens)
            } else {
                // Fall back to lexer-based highlighting
                val lexerTokens: List<Token> = lexer?.let { lex ->
                    val lineText = if (lineNumber < editorState.document.lineCount) {
                        editorState.document.getLineText(lineNumber)
                    } else ""
                    // TODO: Track state across lines for multi-line constructs
                    val lineTokens = lex.tokenizeLine(lineText, lineNumber, LexerState.NORMAL)
                    lineTokens.tokens
                } ?: emptyList()
                EditorToken.fromTokens(lexerTokens)
            }
        }
    }

    // Layout: Run gutter | Editor
    Row(modifier = modifier.fillMaxSize()) {
        // Run gutter (for detected main functions)
        if (detectedMainFunctions.isNotEmpty()) {
            BossEditorRunGutter(
                detectedMainFunctions = detectedMainFunctions,
                editorState = editorState,
                fontSize = fontSize.toFloat(),
                onRun = onRun,
                modifier = Modifier
                    .width(24.dp)
                    .fillMaxHeight()
                    .background(editorTheme.colors.gutterBackground)
            )
        }

        // Main editor
        BossEditor(
            state = editorState,
            modifier = Modifier.fillMaxSize(),
            theme = editorTheme,
            fontFamily = composeFontFamily,
            fontSize = fontSize.toFloat(),
            showLineNumbers = true,
            highlightCurrentLine = true,
            readOnly = isReadOnly,
            tokenProvider = tokenProvider,
            onTextChanged = {
                val newContent = editorState.document.getText()
                onContentChange(newContent)
                val isModified = newContent != originalContent
                onModifiedStateChange(isModified)
            },
            onCaretPositionChanged = { position ->
                // Convert to 1-based line/column for compatibility
                onCursorPositionChange(position.line + 1, position.column + 1)
            },
            onSelectionChanged = { _ ->
                // Selection changed - could integrate with mark occurrences
            }
        )
    }
}

/**
 * Maps BOSS theme name to BossEditor EditorTheme.
 */
private fun mapBossThemeToEditorTheme(themeName: String): EditorTheme {
    return when (themeName.lowercase()) {
        "dark", "boss dark" -> EditorTheme.Dark
        "light", "boss light" -> EditorTheme.Light
        "dracula" -> EditorTheme.Dracula
        "monokai" -> EditorTheme.Monokai
        "solarized dark" -> EditorTheme.SolarizedDark
        "solarized light" -> EditorTheme.SolarizedLight
        else -> EditorTheme.Dark
    }
}

/**
 * Run gutter for BossEditor showing detected main functions.
 */
@Composable
private fun BossEditorRunGutter(
    detectedMainFunctions: List<DetectedMainFunction>,
    editorState: EditorState,
    fontSize: Float,
    onRun: (DetectedMainFunction) -> Unit,
    modifier: Modifier = Modifier
) {
    // Calculate line height (approximate)
    val lineHeight = fontSize * 1.5f // Common line height ratio

    Box(modifier = modifier) {
        // For each detected main function, place a run icon at its line
        for (detected in detectedMainFunctions) {
            val yOffset = (detected.lineNumber - 1) * lineHeight

            // TODO: Add actual run icon button at yOffset position
            // For now, this is a placeholder for the run gutter
            // The full implementation would include:
            // - Scroll synchronization with editor
            // - Clickable run icons
            // - Visual feedback on hover
        }
    }
}
