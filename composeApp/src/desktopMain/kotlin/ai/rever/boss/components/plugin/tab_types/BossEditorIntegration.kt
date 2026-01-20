package ai.rever.boss.components.plugin.tab_types

import ai.rever.boss.components.events.NavigationTargetBus
import ai.rever.boss.font.FontManager
import ai.rever.boss.psi.NavigationEvent
import ai.rever.boss.psi.NavigationResult
import ai.rever.boss.psi.NavigationService
import ai.rever.boss.psi.PSIBootstrap
import ai.rever.boss.psi.PSIThreadBridge
import ai.rever.boss.psi.ProjectIndexer
import ai.rever.boss.run.DetectedMainFunction
import ai.rever.boss.run.Language
import ai.rever.boss.run.MainFunctionDetectorProvider
import ai.rever.bosseditor.compose.BossEditor
import ai.rever.bosseditor.compose.NavigationResolveResult
import ai.rever.bosseditor.largefile.LargeFileConstants
import ai.rever.bosseditor.largefile.LargeFileDocument
import ai.rever.bosseditor.largefile.LargeFileEditorState
import ai.rever.bosseditor.largefile.LargeFileLimitationsDialog
import ai.rever.bosseditor.largefile.LargeFileViewer
import java.io.File
import ai.rever.bosseditor.features.NavigationFailureReason
import ai.rever.bosseditor.features.NavigationFeedbackPopup
import ai.rever.bosseditor.features.NavigationFeedbackState
import ai.rever.bosseditor.features.UsagesPopup
import ai.rever.bosseditor.features.UsagesPopupState
import ai.rever.bosseditor.psi.DefinitionInfo
import ai.rever.bosseditor.psi.ReferenceLocation
import ai.rever.bosseditor.core.EditorPosition
import ai.rever.bosseditor.core.EditorRange
import ai.rever.bosseditor.core.EditorState
import ai.rever.bosseditor.highlight.Token
import ai.rever.bosseditor.highlight.TokenCache
import ai.rever.bosseditor.highlight.TokenType
import ai.rever.bosseditor.highlight.lexers.*
import ai.rever.bosseditor.psi.SemanticCache
import ai.rever.bosseditor.psi.SemanticType
import ai.rever.bosseditor.rendering.EditorToken
import ai.rever.bosseditor.settings.EditorSettingsManager
import ai.rever.bosseditor.theme.EditorTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
 * @param fontFamily Font family name (uses FontManager for proper loading)
 * @param lineSpacing Line height multiplier (1.0 = tight, 1.2 = comfortable, 1.5 = spacious)
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
    onCursorPositionChange: (line: Int, column: Int) -> Unit = { _, _ -> },
    onModifiedStateChange: (Boolean) -> Unit = { },
    onRun: (DetectedMainFunction) -> Unit = { },
    onNavigate: (NavigationEvent) -> Unit = { }
) {
    // Check if file is large and should use the large file viewer
    val isLargeFile = remember(filePath) {
        filePath.isNotEmpty() && LargeFileDocument.shouldUseLargeFileAdapter(filePath)
    }

    // Use BossEditor's unified settings system
    val editorSettings by EditorSettingsManager.instance.settings.collectAsState()

    // For large files, use the specialized viewer
    if (isLargeFile) {
        LargeFileEditorIntegration(
            filePath = filePath,
            modifier = modifier,
            onCursorPositionChange = onCursorPositionChange,
            editorSettings = editorSettings,
            language = language,
            projectPath = projectPath,
            onContentChange = onContentChange,
            onModifiedStateChange = onModifiedStateChange,
            onRun = onRun,
            onNavigate = onNavigate
        )
        return
    }

    val coroutineScope = rememberCoroutineScope()

    // Create editor state that persists across recompositions
    val editorState = remember(filePath) {
        EditorState(content)
    }

    // Track original content for modification detection
    var originalContent by remember(filePath) { mutableStateOf(content) }

    // State for detected main functions
    var detectedMainFunctions by remember { mutableStateOf<List<DetectedMainFunction>>(emptyList()) }

    // State for usages popup
    var usagesPopupState by remember { mutableStateOf(UsagesPopupState.Hidden) }

    // State for navigation feedback popup
    var navigationFeedbackState: NavigationFeedbackState by remember { mutableStateOf(NavigationFeedbackState.Hidden) }

    // Create lexer based on language
    val lexer = remember(language) {
        getLexerForLanguage(language.lowercase())
    }

    // Create token cache for multi-line state tracking (only if lexer is available)
    val tokenCache = remember(lexer, editorState.document) {
        lexer?.let { TokenCache(editorState.document, it) }
    }

    // Dispose token cache when composable is disposed
    DisposableEffect(tokenCache) {
        onDispose {
            tokenCache?.dispose()
        }
    }

    // Note: Semantic highlighting is now handled internally by BossEditor
    // via NavigationManager, which uses PSI-based SemanticHighlighter

    // Create navigation service for PSI-based navigation (uses composeApp's PSI)
    val navigationService = remember { NavigationService() }

    // Ensure project is indexed when projectPath is set
    LaunchedEffect(projectPath) {
        if (projectPath.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                ProjectIndexer.current?.ensureFileProjectIndexed(filePath)
            }
        }
    }

    // Navigation resolver using composeApp's PSI infrastructure
    val navigationResolver: suspend (String, String, Int) -> NavigationResolveResult = remember(navigationService) {
        { content, currentFilePath, offset ->
            if (!PSIBootstrap.isInitialized) {
                NavigationResolveResult.NotFound
            } else if (!currentFilePath.endsWith(".kt") && !currentFilePath.endsWith(".kts")) {
                NavigationResolveResult.NotFound
            } else {
                try {
                    val fileName = currentFilePath.substringAfterLast('/')
                    val ktFile = PSIThreadBridge.readAction {
                        PSIBootstrap.parseKotlinFile(fileName, content)
                    }
                    val result = PSIThreadBridge.readAction {
                        navigationService.goToDefinition(ktFile, offset, currentFilePath)
                    }
                    when (result) {
                        is NavigationResult.Found -> {
                            NavigationResolveResult.Found(
                                filePath = result.target.filePath,
                                line = result.target.line,
                                column = result.target.column
                            )
                        }
                        is NavigationResult.MultipleTargets -> {
                            result.targets.firstOrNull()?.let { target ->
                                NavigationResolveResult.Found(
                                    filePath = target.filePath,
                                    line = target.line,
                                    column = target.column
                                )
                            } ?: NavigationResolveResult.NotFound
                        }
                        else -> NavigationResolveResult.NotFound
                    }
                } catch (e: Exception) {
                    println("[BossEditorIntegration] Navigation error: ${e.message}")
                    NavigationResolveResult.NotFound
                }
            }
        }
    }

    // Map theme name to EditorTheme
    val editorTheme = remember(editorSettings.themeName) {
        mapBossThemeToEditorTheme(editorSettings.themeName)
    }

    // Map font family name to FontFamily using FontManager for proper font loading
    val composeFontFamily = remember(editorSettings.fontFamily) {
        FontManager.loadComposeFontFamily(editorSettings.fontFamily ?: FontManager.BUNDLED_JETBRAINS_MONO)
    }

    // Calculate line height for scrolling (same calculation as EditorCanvas)
    val textMeasurer = rememberTextMeasurer()
    val lineHeightPx = remember(editorSettings.fontSize, composeFontFamily, editorSettings.lineSpacing) {
        val style = TextStyle(
            fontFamily = composeFontFamily,
            fontSize = editorSettings.fontSize.sp
        )
        textMeasurer.measure("M", style).size.height.toFloat() * editorSettings.lineSpacing
    }

    // Listen for navigation targets (cursor positioning after navigation)
    LaunchedEffect(filePath, editorState, lineHeightPx) {
        NavigationTargetBus.targets
            .collect { target ->
                // Only process if this editor is showing the target file
                if (target.filePath == filePath && target.line > 0) {
                    try {
                        // Convert 1-based line/column to 0-based EditorPosition
                        val line = (target.line - 1).coerceAtLeast(0)
                        val column = (target.column - 1).coerceAtLeast(0)

                        // Position cursor
                        val position = EditorPosition(line, column)
                        editorState.moveCaret(position)
                        editorState.clearSelection()

                        // Scroll to make the line visible (estimate viewport as 600px)
                        editorState.scrollToLine(line, lineHeightPx, 600f)

                        // Clear replay cache after consumption to avoid re-triggering
                        NavigationTargetBus.clearCache()

                        println("[BossEditorIntegration] Positioned cursor at line ${target.line}, column ${target.column}")
                    } catch (e: Exception) {
                        println("[BossEditorIntegration] Error positioning cursor: ${e.message}")
                    }
                }
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

    // Token provider for lexer-based + semantic syntax highlighting
    // Uses TokenCache for proper multi-line state tracking (block comments, raw strings, etc.)
    // Merges with SemanticCache for PSI-based semantic highlighting (function calls, properties, etc.)
    val tokenProvider: (Int) -> List<EditorToken> = remember(tokenCache, filePath) {
        { lineNumber ->
            // Get lexer-based tokens (cached, handles multi-line state)
            val lexerTokens: List<Token> = tokenCache?.getLineTokens(lineNumber) ?: emptyList()

            // Get semantic tokens from PSI analysis (if available for this file)
            val semanticTokens = getSemanticTokensForLine(editorState.document, filePath, lineNumber)

            // Merge tokens (semantic takes precedence for overlapping ranges)
            val mergedTokens = if (semanticTokens.isNotEmpty()) {
                mergeTokens(lexerTokens, semanticTokens)
            } else {
                lexerTokens
            }

            EditorToken.fromTokens(mergedTokens)
        }
    }

    // Layout: Run gutter | Editor
    Row(modifier = modifier.fillMaxSize()) {
        // Run gutter (for detected main functions)
        if (detectedMainFunctions.isNotEmpty()) {
            BossEditorRunGutter(
                detectedMainFunctions = detectedMainFunctions,
                editorState = editorState,
                fontSize = editorSettings.fontSize,
                fontFamily = composeFontFamily,
                lineSpacing = editorSettings.lineSpacing,
                onRun = onRun,
                modifier = Modifier
                    .width(28.dp)
                    .fillMaxHeight()
                    .background(editorTheme.colors.gutterBackground)
            )
        }

        // Parse minimap custom colors from settings
        val minimapBgColor = remember(editorSettings.minimapBackgroundColor) {
            editorSettings.minimapBackgroundColor?.let { parseHexColor(it) }
        }
        val minimapFgColor = remember(editorSettings.minimapForegroundColor) {
            editorSettings.minimapForegroundColor?.let { parseHexColor(it) }
        }

        // Main editor
        BossEditor(
            state = editorState,
            modifier = Modifier.fillMaxSize(),
            theme = editorTheme,
            fontFamily = composeFontFamily,
            fontSize = editorSettings.fontSize,
            lineSpacing = editorSettings.lineSpacing,
            showLineNumbers = editorSettings.showLineNumbers,
            highlightCurrentLine = editorSettings.highlightCurrentLine,
            readOnly = isReadOnly,
            filePath = filePath,
            projectPath = projectPath,
            showMinimap = editorSettings.showMinimap,
            minimapWidth = editorSettings.minimapWidth,
            minimapUseEditorColors = editorSettings.minimapUseEditorColors,
            minimapBackgroundColor = minimapBgColor,
            minimapForegroundColor = minimapFgColor,
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
            },
            // Don't use custom navigationResolver - let BossEditor use internal NavigationManager
            // which has ShowUsages support for clicking on definitions
            navigationResolver = null,
            onNavigate = { navFilePath, line, column ->
                onNavigate(NavigationEvent(navFilePath, line, column))
            },
            onShowUsages = { references, definition, clickPosition ->
                usagesPopupState = UsagesPopupState(
                    isVisible = true,
                    references = references,
                    definition = definition,
                    anchorOffset = IntOffset(clickPosition.x.toInt(), clickPosition.y.toInt())
                )
            },
            onNavigationFailed = { reason, clickPosition ->
                navigationFeedbackState = NavigationFeedbackState.Visible(
                    reason = reason,
                    anchorOffset = IntOffset(clickPosition.x.toInt(), clickPosition.y.toInt())
                )
            }
        )

        // Render usages popup if visible
        if (usagesPopupState.isVisible && usagesPopupState.definition != null) {
            UsagesPopup(
                references = usagesPopupState.references,
                definition = usagesPopupState.definition!!,
                anchorOffset = usagesPopupState.anchorOffset,
                onNavigate = { navFilePath, line, column ->
                    // Navigate to the usage
                    onNavigate(NavigationEvent(navFilePath, line, column))
                },
                onDismiss = {
                    usagesPopupState = UsagesPopupState.Hidden
                },
                theme = editorTheme
            )
        }

        // Render navigation feedback popup if visible
        val feedbackState = navigationFeedbackState
        if (feedbackState is NavigationFeedbackState.Visible) {
            NavigationFeedbackPopup(
                reason = feedbackState.reason,
                anchorOffset = feedbackState.anchorOffset,
                onDismiss = {
                    navigationFeedbackState = NavigationFeedbackState.Hidden
                },
                theme = editorTheme
            )
        }
    }
}

/**
 * Internal BossEditor integration that takes editorSettings directly.
 * Used by LargeFileEditorIntegration when user chooses to open in full editor.
 */
@Composable
private fun BossEditorIntegrationInternal(
    content: String,
    onContentChange: (String) -> Unit,
    language: String,
    filePath: String,
    projectPath: String = "",
    modifier: Modifier = Modifier,
    isReadOnly: Boolean = false,
    onCursorPositionChange: (line: Int, column: Int) -> Unit = { _, _ -> },
    onModifiedStateChange: (Boolean) -> Unit = { },
    onRun: (DetectedMainFunction) -> Unit = { },
    onNavigate: (NavigationEvent) -> Unit = { },
    editorSettings: ai.rever.bosseditor.settings.EditorSettings
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

    // State for usages popup
    var usagesPopupState by remember { mutableStateOf(UsagesPopupState.Hidden) }

    // State for navigation feedback popup
    var navigationFeedbackState: NavigationFeedbackState by remember { mutableStateOf(NavigationFeedbackState.Hidden) }

    // Create lexer based on language
    val lexer = remember(language) {
        getLexerForLanguage(language.lowercase())
    }

    // Create token cache for multi-line state tracking (only if lexer is available)
    val tokenCache = remember(lexer, editorState.document) {
        lexer?.let { TokenCache(editorState.document, it) }
    }

    // Dispose token cache when composable is disposed
    DisposableEffect(tokenCache) {
        onDispose {
            tokenCache?.dispose()
        }
    }

    // Create navigation service for PSI-based navigation
    val navigationService = remember { NavigationService() }

    // Ensure project is indexed when projectPath is set
    LaunchedEffect(projectPath) {
        if (projectPath.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                ProjectIndexer.current?.ensureFileProjectIndexed(filePath)
            }
        }
    }

    // Navigation resolver using composeApp's PSI infrastructure
    val navigationResolver: suspend (String, String, Int) -> NavigationResolveResult = remember(navigationService) {
        { contentParam, currentFilePath, offset ->
            if (!PSIBootstrap.isInitialized) {
                NavigationResolveResult.NotFound
            } else if (!currentFilePath.endsWith(".kt") && !currentFilePath.endsWith(".kts")) {
                NavigationResolveResult.NotFound
            } else {
                try {
                    val fileName = currentFilePath.substringAfterLast('/')
                    val ktFile = PSIThreadBridge.readAction {
                        PSIBootstrap.parseKotlinFile(fileName, contentParam)
                    }
                    val result = PSIThreadBridge.readAction {
                        navigationService.goToDefinition(ktFile, offset, currentFilePath)
                    }
                    when (result) {
                        is NavigationResult.Found -> {
                            NavigationResolveResult.Found(
                                filePath = result.target.filePath,
                                line = result.target.line,
                                column = result.target.column
                            )
                        }
                        is NavigationResult.MultipleTargets -> {
                            result.targets.firstOrNull()?.let { target ->
                                NavigationResolveResult.Found(
                                    filePath = target.filePath,
                                    line = target.line,
                                    column = target.column
                                )
                            } ?: NavigationResolveResult.NotFound
                        }
                        else -> NavigationResolveResult.NotFound
                    }
                } catch (e: Exception) {
                    println("[BossEditorIntegrationInternal] Navigation error: ${e.message}")
                    NavigationResolveResult.NotFound
                }
            }
        }
    }

    // Map theme name to EditorTheme
    val editorTheme = remember(editorSettings.themeName) {
        mapBossThemeToEditorTheme(editorSettings.themeName)
    }

    // Map font family name to FontFamily
    val composeFontFamily = remember(editorSettings.fontFamily) {
        FontManager.loadComposeFontFamily(editorSettings.fontFamily ?: FontManager.BUNDLED_JETBRAINS_MONO)
    }

    // Calculate line height for scrolling
    val textMeasurer = rememberTextMeasurer()
    val lineHeightPx = remember(editorSettings.fontSize, composeFontFamily, editorSettings.lineSpacing) {
        val style = TextStyle(
            fontFamily = composeFontFamily,
            fontSize = editorSettings.fontSize.sp
        )
        textMeasurer.measure("M", style).size.height.toFloat() * editorSettings.lineSpacing
    }

    // Listen for navigation targets
    LaunchedEffect(filePath, editorState, lineHeightPx) {
        NavigationTargetBus.targets
            .collect { target ->
                if (target.filePath == filePath && target.line > 0) {
                    try {
                        val line = (target.line - 1).coerceAtLeast(0)
                        val column = (target.column - 1).coerceAtLeast(0)
                        val position = EditorPosition(line, column)
                        editorState.moveCaret(position)
                        editorState.clearSelection()
                        editorState.scrollToLine(line, lineHeightPx, 600f)
                        NavigationTargetBus.clearCache()
                    } catch (e: Exception) {
                        println("[BossEditorIntegrationInternal] Error positioning cursor: ${e.message}")
                    }
                }
            }
    }

    // Update content from external source
    LaunchedEffect(content) {
        if (editorState.document.getText() != content) {
            editorState.document.setText(content)
        }
    }

    // Detect main functions
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
                    println("[BossEditorIntegrationInternal] Error detecting main functions: ${e.message}")
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

    // Token provider for syntax highlighting
    val tokenProvider: (Int) -> List<EditorToken> = remember(tokenCache, filePath) {
        { lineNumber ->
            val lexerTokens: List<Token> = tokenCache?.getLineTokens(lineNumber) ?: emptyList()
            val semanticTokens = getSemanticTokensForLine(editorState.document, filePath, lineNumber)
            val mergedTokens = if (semanticTokens.isNotEmpty()) {
                mergeTokens(lexerTokens, semanticTokens)
            } else {
                lexerTokens
            }
            EditorToken.fromTokens(mergedTokens)
        }
    }

    // Layout: Run gutter | Editor
    Row(modifier = modifier.fillMaxSize()) {
        // Run gutter
        if (detectedMainFunctions.isNotEmpty()) {
            BossEditorRunGutter(
                detectedMainFunctions = detectedMainFunctions,
                editorState = editorState,
                fontSize = editorSettings.fontSize,
                fontFamily = composeFontFamily,
                lineSpacing = editorSettings.lineSpacing,
                onRun = onRun,
                modifier = Modifier
                    .width(28.dp)
                    .fillMaxHeight()
                    .background(editorTheme.colors.gutterBackground)
            )
        }

        // Parse minimap custom colors
        val minimapBgColor = remember(editorSettings.minimapBackgroundColor) {
            editorSettings.minimapBackgroundColor?.let { parseHexColor(it) }
        }
        val minimapFgColor = remember(editorSettings.minimapForegroundColor) {
            editorSettings.minimapForegroundColor?.let { parseHexColor(it) }
        }

        // Main editor
        BossEditor(
            state = editorState,
            modifier = Modifier.fillMaxSize(),
            theme = editorTheme,
            fontFamily = composeFontFamily,
            fontSize = editorSettings.fontSize,
            lineSpacing = editorSettings.lineSpacing,
            showLineNumbers = editorSettings.showLineNumbers,
            highlightCurrentLine = editorSettings.highlightCurrentLine,
            readOnly = isReadOnly,
            filePath = filePath,
            projectPath = projectPath,
            showMinimap = editorSettings.showMinimap,
            minimapWidth = editorSettings.minimapWidth,
            minimapUseEditorColors = editorSettings.minimapUseEditorColors,
            minimapBackgroundColor = minimapBgColor,
            minimapForegroundColor = minimapFgColor,
            tokenProvider = tokenProvider,
            onTextChanged = {
                val newContent = editorState.document.getText()
                onContentChange(newContent)
                val isModified = newContent != originalContent
                onModifiedStateChange(isModified)
            },
            onCaretPositionChanged = { position ->
                onCursorPositionChange(position.line + 1, position.column + 1)
            },
            onSelectionChanged = { _ -> },
            navigationResolver = null,
            onNavigate = { navFilePath, line, column ->
                onNavigate(NavigationEvent(navFilePath, line, column))
            },
            onShowUsages = { references, definition, clickPosition ->
                usagesPopupState = UsagesPopupState(
                    isVisible = true,
                    references = references,
                    definition = definition,
                    anchorOffset = IntOffset(clickPosition.x.toInt(), clickPosition.y.toInt())
                )
            },
            onNavigationFailed = { reason, clickPosition ->
                navigationFeedbackState = NavigationFeedbackState.Visible(
                    reason = reason,
                    anchorOffset = IntOffset(clickPosition.x.toInt(), clickPosition.y.toInt())
                )
            }
        )

        // Render usages popup
        if (usagesPopupState.isVisible && usagesPopupState.definition != null) {
            UsagesPopup(
                references = usagesPopupState.references,
                definition = usagesPopupState.definition!!,
                anchorOffset = usagesPopupState.anchorOffset,
                onNavigate = { navFilePath, line, column ->
                    onNavigate(NavigationEvent(navFilePath, line, column))
                },
                onDismiss = {
                    usagesPopupState = UsagesPopupState.Hidden
                },
                theme = editorTheme
            )
        }

        // Render navigation feedback popup
        val feedbackState = navigationFeedbackState
        if (feedbackState is NavigationFeedbackState.Visible) {
            NavigationFeedbackPopup(
                reason = feedbackState.reason,
                anchorOffset = feedbackState.anchorOffset,
                onDismiss = {
                    navigationFeedbackState = NavigationFeedbackState.Hidden
                },
                theme = editorTheme
            )
        }
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
 * Returns the appropriate lexer for the given language.
 * Supports language names, file extensions, and common aliases.
 */
private fun getLexerForLanguage(language: String): BaseLexer? {
    return when (language) {
        // Kotlin
        "kotlin", "kt", "kts" -> KotlinLexer()

        // Java
        "java" -> JavaLexer()

        // JavaScript
        "javascript", "js", "jsx", "mjs", "cjs" -> JavaScriptLexer()

        // TypeScript
        "typescript", "ts", "tsx", "mts", "cts" -> TypeScriptLexer()

        // Python
        "python", "py", "pyw", "pyi", "pyx" -> PythonLexer()

        // JSON
        "json", "jsonc", "json5" -> JsonLexer()

        // XML
        "xml", "xsd", "xsl", "xslt", "svg", "plist", "wsdl" -> XmlLexer()

        // HTML
        "html", "htm", "xhtml", "vue", "svelte" -> HtmlLexer()

        // CSS
        "css", "scss", "sass", "less" -> CssLexer()

        // Shell/Bash
        "shell", "sh", "bash", "zsh", "fish", "ksh", "csh", "tcsh" -> ShellLexer()

        // Markdown
        "markdown", "md", "mdx", "mkd", "mkdn" -> MarkdownLexer()

        // SQL
        "sql", "mysql", "pgsql", "plsql", "sqlite" -> SqlLexer()

        // YAML
        "yaml", "yml" -> YamlLexer()

        // Groovy
        "groovy", "gradle", "gvy", "gy", "gsh" -> GroovyLexer()

        // C/C++
        "c", "h", "cpp", "hpp", "cc", "hh", "cxx", "hxx", "c++", "h++", "ino" -> CLexer()

        // Rust
        "rust", "rs" -> RustLexer()

        // Go
        "go", "golang" -> GoLexer()

        // Swift
        "swift" -> SwiftLexer()

        // Ruby
        "ruby", "rb", "rake", "gemspec", "ru", "erb", "podspec" -> RubyLexer()

        // PHP
        "php", "phtml", "php3", "php4", "php5", "php7", "phps" -> PHPLexer()

        // Dockerfile
        "dockerfile", "docker" -> DockerfileLexer()

        // Makefile
        "makefile", "mk", "mak", "make" -> MakefileLexer()

        // TOML
        "toml" -> TomlLexer()

        // Properties/INI
        "properties", "env", "cfg", "conf", "ini" -> PropertiesLexer()

        // Scala
        "scala", "sc", "sbt" -> ScalaLexer()

        // Perl
        "perl", "pl", "pm", "pod", "t", "psgi" -> PerlLexer()

        // Lua
        "lua" -> LuaLexer()

        // C#
        "csharp", "cs", "csx" -> CSharpLexer()

        // Clojure
        "clojure", "clj", "cljs", "cljc", "edn" -> ClojureLexer()

        // LaTeX
        "latex", "tex", "sty", "cls", "bib", "bst", "ltx" -> LaTeXLexer()

        // Batch/CMD (Windows)
        "batch", "bat", "cmd" -> BatchLexer()

        // Visual Basic
        "vb", "vbs", "bas", "frm", "vba" -> VisualBasicLexer()

        // Tcl/Tk
        "tcl", "tk", "itcl", "itk" -> TclLexer()

        // Lisp/Scheme/Racket
        "lisp", "lsp", "cl", "el", "elc", "scm", "ss", "rkt", "scheme", "racket", "elisp", "emacs-lisp" -> LispLexer()

        // D
        "d", "di" -> DLexer()

        // Pascal/Delphi
        "pascal", "pas", "dpr", "dpk", "pp", "inc", "lpr", "lfm", "dfm", "delphi" -> DelphiLexer()

        // ActionScript
        "actionscript", "as", "mxml" -> ActionScriptLexer()

        // Fortran
        "fortran", "f", "for", "f77", "f90", "f95", "f03", "f08", "f18" -> FortranLexer()

        // JSP
        "jsp", "jspf", "jspx", "tag", "tagx", "tld" -> JspLexer()

        // Diff/Patch
        "diff", "patch", "rej" -> DiffLexer()

        // Default: no syntax highlighting
        else -> null
    }
}

/**
 * Run gutter for BossEditor showing detected main functions.
 *
 * This is a pure Compose implementation that directly uses EditorState's
 * scroll offset, avoiding the Swing synchronization issues in RSyntaxGutterOverlay.
 */
@Composable
private fun BossEditorRunGutter(
    detectedMainFunctions: List<DetectedMainFunction>,
    editorState: EditorState,
    fontSize: Float,
    fontFamily: FontFamily = FontFamily.Monospace,
    lineSpacing: Float = 1.2f,
    onRun: (DetectedMainFunction) -> Unit,
    modifier: Modifier = Modifier
) {
    // Collect scroll offset from editor state
    val scrollOffset by editorState.scrollOffset.collectAsState()
    // Collect visual line mapper for folding support
    val visualLineMapper by editorState.visualLineMapper.collectAsState()
    val density = LocalDensity.current

    // Measure line height to match EditorCanvas exactly
    // EditorCanvas uses textMeasurer.measure("M", style).size.height * lineSpacing
    val textMeasurer = rememberTextMeasurer()
    val lineHeightPx = remember(fontSize, fontFamily, lineSpacing) {
        val style = TextStyle(
            fontFamily = fontFamily,
            fontSize = fontSize.sp
        )
        textMeasurer.measure("M", style).size.height.toFloat() * lineSpacing
    }

    // Convert pixel height to dp for sizing
    val lineHeightDp = with(density) { lineHeightPx.toDp() }

    // Create a map for fast lookup
    val runnableLines = remember(detectedMainFunctions) {
        detectedMainFunctions.associateBy { it.lineNumber }
    }

    // Calculate visible range with buffer
    val firstVisibleLine = (scrollOffset.y / lineHeightPx).toInt().coerceAtLeast(0)
    val visibleLineCount = 50 // Generous buffer for smooth scrolling
    val visibleRange = remember(firstVisibleLine, visibleLineCount, editorState.document.lineCount) {
        val start = (firstVisibleLine - 2).coerceAtLeast(0)
        val end = (firstVisibleLine + visibleLineCount + 2).coerceAtMost(editorState.document.lineCount)
        start until end
    }

    Box(modifier = modifier) {
        // Render run icons for detected main functions in visible range
        detectedMainFunctions
            .filter { it.lineNumber in visibleRange }
            .forEach { detected ->
                // lineNumber from detector is 0-based document line
                val documentLine = detected.lineNumber

                // Convert document line to visual line (accounts for folding)
                val visualLine = visualLineMapper.documentToVisual(documentLine)

                // Skip if line is hidden (inside a collapsed fold)
                if (visualLine < 0) return@forEach

                // Calculate Y position using visual line
                val yOffsetPx = (visualLine * lineHeightPx) - scrollOffset.y.toFloat()

                // Only render if within viewport
                if (yOffsetPx >= -lineHeightPx && yOffsetPx < 2000f) {
                    Box(
                        modifier = Modifier
                            // Use pixel-based offset to match EditorCanvas rendering
                            .offset { IntOffset(0, yOffsetPx.toInt()) }
                            .height(lineHeightDp)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        GutterRunIcon(
                            detected = detected,
                            onRun = onRun,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
    }
}

/**
 * Gets semantic tokens for a specific line from the PSI-based SemanticCache.
 *
 * @param document The editor document
 * @param filePath The current file path
 * @param lineNumber The line number (0-based)
 * @return List of semantic tokens for the line, empty if not available
 */
private fun getSemanticTokensForLine(
    document: ai.rever.bosseditor.core.EditorDocument,
    filePath: String,
    lineNumber: Int
): List<Token> {
    // Only Kotlin files have semantic highlighting
    if (!filePath.endsWith(".kt") && !filePath.endsWith(".kts")) {
        return emptyList()
    }

    // Get all semantic elements for this file from the cache
    val allElements = SemanticCache.get(filePath) ?: return emptyList()
    if (allElements.isEmpty()) return emptyList()

    // Get the line range in the document
    if (lineNumber < 0 || lineNumber >= document.lineCount) {
        return emptyList()
    }
    val lineStart = document.getLineStartOffset(lineNumber)
    val lineEnd = document.getLineEndOffset(lineNumber)

    // Filter elements that fall within this line
    val lineElements = allElements.filter { element ->
        element.startOffset >= lineStart && element.endOffset <= lineEnd
    }

    if (lineElements.isEmpty()) return emptyList()

    // Convert SemanticElements to Tokens with line-relative offsets
    return lineElements.map { element ->
        Token(
            startOffset = element.startOffset - lineStart,
            endOffset = element.endOffset - lineStart,
            type = mapSemanticType(element.type)
        )
    }.sortedBy { it.startOffset }
}

/**
 * Maps PSI SemanticType to BossEditor TokenType.
 */
private fun mapSemanticType(type: SemanticType): TokenType = when (type) {
    SemanticType.FUNCTION_CALL -> TokenType.FUNCTION_CALL
    SemanticType.PROPERTY_ACCESS -> TokenType.PROPERTY
    SemanticType.CLASS_REFERENCE -> TokenType.TYPE
    SemanticType.OBJECT_REFERENCE -> TokenType.VARIABLE
    SemanticType.PARAMETER -> TokenType.PARAMETER
    SemanticType.LOCAL_VARIABLE -> TokenType.LOCAL_VARIABLE
    SemanticType.ANNOTATION -> TokenType.ANNOTATION
    SemanticType.LABEL -> TokenType.LABEL
    SemanticType.TYPE_PARAMETER -> TokenType.TYPE_PARAMETER
}

/**
 * Merges lexer tokens with semantic tokens, where semantic tokens take precedence.
 *
 * @param base Lexer-based tokens (always available)
 * @param overlay Semantic tokens from PSI analysis
 * @return Merged token list
 */
private fun mergeTokens(base: List<Token>, overlay: List<Token>): List<Token> {
    if (base.isEmpty()) return overlay
    if (overlay.isEmpty()) return base

    val result = mutableListOf<Token>()
    var baseIndex = 0
    var overlayIndex = 0

    while (baseIndex < base.size || overlayIndex < overlay.size) {
        // If no more overlay tokens, add remaining base tokens
        if (overlayIndex >= overlay.size) {
            result.addAll(base.subList(baseIndex, base.size))
            break
        }

        // If no more base tokens, add remaining overlay tokens
        if (baseIndex >= base.size) {
            result.addAll(overlay.subList(overlayIndex, overlay.size))
            break
        }

        val baseToken = base[baseIndex]
        val overlayToken = overlay[overlayIndex]

        when {
            // Base token comes completely before overlay - keep it
            baseToken.endOffset <= overlayToken.startOffset -> {
                result.add(baseToken)
                baseIndex++
            }

            // Overlay token comes completely before base - add it
            overlayToken.endOffset <= baseToken.startOffset -> {
                result.add(overlayToken)
                overlayIndex++
            }

            // Tokens overlap - overlay takes precedence
            else -> {
                // Add part of base before overlay (if any)
                if (baseToken.startOffset < overlayToken.startOffset) {
                    result.add(
                        Token(
                            baseToken.startOffset,
                            overlayToken.startOffset,
                            baseToken.type,
                            baseToken.modifiers
                        )
                    )
                }

                // Add overlay token
                result.add(overlayToken)

                // Handle remaining part of base token
                if (baseToken.endOffset > overlayToken.endOffset) {
                    // Create remaining part after overlay
                    val remaining = Token(
                        overlayToken.endOffset,
                        baseToken.endOffset,
                        baseToken.type,
                        baseToken.modifiers
                    )
                    overlayIndex++
                    // Check if remaining part overlaps with next overlay
                    if (overlayIndex < overlay.size &&
                        remaining.startOffset < overlay[overlayIndex].startOffset
                    ) {
                        val nextOverlay = overlay[overlayIndex]
                        if (remaining.endOffset <= nextOverlay.startOffset) {
                            result.add(remaining)
                        } else {
                            result.add(
                                Token(
                                    remaining.startOffset,
                                    nextOverlay.startOffset,
                                    remaining.type,
                                    remaining.modifiers
                                )
                            )
                        }
                    } else if (overlayIndex >= overlay.size) {
                        result.add(remaining)
                    }
                    baseIndex++
                } else {
                    // Base token completely covered by overlay
                    baseIndex++
                    if (overlayToken.endOffset >= base.getOrNull(baseIndex)?.startOffset ?: Int.MAX_VALUE) {
                        // Overlay covers next base token too
                    } else {
                        overlayIndex++
                    }
                }
            }
        }
    }

    return result.sortedBy { it.startOffset }
}

/**
 * Parses a hex color string (ARGB format like "FF1E1F22") to a Compose Color.
 * Returns null if the string is invalid.
 */
private fun parseHexColor(hex: String): Color? {
    return try {
        val cleanHex = hex.removePrefix("#").removePrefix("0x")
        when (cleanHex.length) {
            6 -> {
                // RGB format - add full alpha
                val color = cleanHex.toLong(16)
                Color(
                    red = ((color shr 16) and 0xFF).toInt() / 255f,
                    green = ((color shr 8) and 0xFF).toInt() / 255f,
                    blue = (color and 0xFF).toInt() / 255f,
                    alpha = 1f
                )
            }
            8 -> {
                // ARGB format
                val color = cleanHex.toLong(16)
                Color(
                    alpha = ((color shr 24) and 0xFF).toInt() / 255f,
                    red = ((color shr 16) and 0xFF).toInt() / 255f,
                    green = ((color shr 8) and 0xFF).toInt() / 255f,
                    blue = (color and 0xFF).toInt() / 255f
                )
            }
            else -> null
        }
    } catch (e: Exception) {
        null
    }
}

/**
 * Integration composable for large files (>10MB).
 *
 * Uses page-based lazy loading to efficiently view large files without
 * loading the entire content into memory.
 *
 * Features:
 * - Read-only viewing
 * - Scrolling and navigation
 * - Line numbers
 * - Search with Ctrl+F
 * - Option to open in full editor (loads entire file into memory)
 *
 * Limitations:
 * - No syntax highlighting
 * - No editing
 * - No code folding
 */
@Composable
private fun LargeFileEditorIntegration(
    filePath: String,
    modifier: Modifier,
    onCursorPositionChange: (line: Int, column: Int) -> Unit,
    editorSettings: ai.rever.bosseditor.settings.EditorSettings,
    language: String = "",
    projectPath: String = "",
    onContentChange: (String) -> Unit = {},
    onModifiedStateChange: (Boolean) -> Unit = {},
    onRun: (DetectedMainFunction) -> Unit = {},
    onNavigate: (NavigationEvent) -> Unit = {}
) {
    // Track whether user wants to open in full editor
    var openInFullEditor by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var fullContent by remember { mutableStateOf<String?>(null) }
    var showLimitationsDialog by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    // Map theme
    val editorTheme = remember(editorSettings.themeName) {
        mapBossThemeToEditorTheme(editorSettings.themeName)
    }

    // Map font family
    val composeFontFamily = remember(editorSettings.fontFamily) {
        FontManager.loadComposeFontFamily(editorSettings.fontFamily ?: FontManager.BUNDLED_JETBRAINS_MONO)
    }

    // If user chose to open in full editor and content is loaded, show BossEditor with banner
    if (openInFullEditor && fullContent != null) {
        Column(modifier = modifier) {
            // Header banner with "Open in Read-only Mode" option
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(editorTheme.colors.gutterBackground)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.compose.material.Text(
                    text = "Large file (${formatFileSize(File(filePath).length())})",
                    color = editorTheme.colors.lineNumber,
                    fontSize = 12.sp
                )

                androidx.compose.material.TextButton(
                    onClick = { openInFullEditor = false },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    androidx.compose.material.Text(
                        text = "Open in Read-only Mode",
                        color = Color(0xFF6B9BFA),
                        fontSize = 12.sp
                    )
                }
            }

            // Full editor
            BossEditorIntegrationInternal(
                content = fullContent!!,
                onContentChange = { newContent ->
                    fullContent = newContent
                    onContentChange(newContent)
                },
                language = language,
                filePath = filePath,
                projectPath = projectPath,
                modifier = Modifier.fillMaxSize(),
                isReadOnly = false,
                onCursorPositionChange = onCursorPositionChange,
                onModifiedStateChange = onModifiedStateChange,
                onRun = onRun,
                onNavigate = onNavigate,
                editorSettings = editorSettings
            )
        }
        return
    }

    // Create large file state
    val largeFileState = remember(filePath) {
        LargeFileEditorState.createIfLargeFile(filePath)
    }

    // Cleanup on dispose
    DisposableEffect(largeFileState) {
        onDispose {
            largeFileState?.close()
        }
    }

    // Extract file info
    val fileName = remember(filePath) { File(filePath).name }
    val fileSize = remember(filePath) { formatFileSize(File(filePath).length()) }

    // Show limitations dialog if triggered
    if (showLimitationsDialog) {
        LargeFileLimitationsDialog(
            fileName = fileName,
            fileSize = fileSize,
            onDismiss = { showLimitationsDialog = false },
            onOpenInEditor = {
                isLoading = true
                coroutineScope.launch(Dispatchers.IO) {
                    try {
                        val content = File(filePath).readText()
                        withContext(Dispatchers.Main) {
                            fullContent = content
                            openInFullEditor = true
                            isLoading = false
                        }
                    } catch (e: Exception) {
                        println("[LargeFileEditorIntegration] Error loading file: ${e.message}")
                        withContext(Dispatchers.Main) {
                            isLoading = false
                        }
                    }
                }
            },
            theme = editorTheme
        )
    }

    if (largeFileState != null) {
        Column(modifier = modifier) {
            // Show large file indicator with info icon and "Open in Editor" option
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(editorTheme.colors.gutterBackground)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material.Text(
                        text = "Large file ($fileSize) - Read-only view",
                        color = editorTheme.colors.lineNumber,
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.width(8.dp))
                    // Info icon to show limitations dialog
                    androidx.compose.material.IconButton(
                        onClick = { showLimitationsDialog = true },
                        modifier = Modifier.size(20.dp)
                    ) {
                        androidx.compose.material.Icon(
                            Icons.Default.Info,
                            contentDescription = "Large file mode info",
                            tint = editorTheme.colors.lineNumber,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                if (isLoading) {
                    androidx.compose.material.Text(
                        text = "Loading...",
                        color = editorTheme.colors.lineNumber,
                        fontSize = 12.sp
                    )
                } else {
                    androidx.compose.material.TextButton(
                        onClick = {
                            isLoading = true
                            coroutineScope.launch(Dispatchers.IO) {
                                try {
                                    val content = File(filePath).readText()
                                    withContext(Dispatchers.Main) {
                                        fullContent = content
                                        openInFullEditor = true
                                        isLoading = false
                                    }
                                } catch (e: Exception) {
                                    println("[LargeFileEditorIntegration] Error loading file: ${e.message}")
                                    withContext(Dispatchers.Main) {
                                        isLoading = false
                                    }
                                }
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        androidx.compose.material.Text(
                            text = "Open in Editor",
                            color = Color(0xFF6B9BFA),
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // Large file viewer
            LargeFileViewer(
                state = largeFileState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                theme = editorTheme,
                fontFamily = composeFontFamily,
                fontSize = editorSettings.fontSize,
                lineSpacing = editorSettings.lineSpacing,
                showLineNumbers = editorSettings.showLineNumbers,
                onCaretPositionChanged = { position ->
                    onCursorPositionChange(position.line + 1, position.column + 1)
                }
            )
        }
    } else {
        // Fallback: file isn't actually large or couldn't be opened
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.material.Text(
                text = "Unable to open file",
                color = editorTheme.colors.text
            )
        }
    }
}

/**
 * Formats file size in human-readable format.
 */
private fun formatFileSize(bytes: Long): String {
    return when {
        bytes >= 1_000_000_000 -> String.format("%.1f GB", bytes / 1_000_000_000.0)
        bytes >= 1_000_000 -> String.format("%.1f MB", bytes / 1_000_000.0)
        bytes >= 1_000 -> String.format("%.1f KB", bytes / 1_000.0)
        else -> "$bytes bytes"
    }
}
