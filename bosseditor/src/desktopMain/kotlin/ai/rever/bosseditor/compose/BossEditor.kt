package ai.rever.bosseditor.compose

import ai.rever.bosseditor.core.EditorPosition
import ai.rever.bosseditor.core.EditorRange
import ai.rever.bosseditor.core.EditorState
import ai.rever.bosseditor.core.OffsetRange
import androidx.compose.ui.geometry.Offset
import ai.rever.bosseditor.features.MinimapCanvas
import ai.rever.bosseditor.features.MinimapConfig
import ai.rever.bosseditor.features.NavigationFailureReason
import ai.rever.bosseditor.features.NavigationManager
import ai.rever.bosseditor.features.NavigationOutcome
import ai.rever.bosseditor.psi.DefinitionInfo
import ai.rever.bosseditor.psi.ReferenceLocation
import ai.rever.bosseditor.fold.KotlinFoldParser
import ai.rever.bosseditor.fold.VisualLineMapper
import ai.rever.bosseditor.highlight.lexers.KotlinLexer
import ai.rever.bosseditor.highlight.LexerState
import ai.rever.bosseditor.highlight.TokenProvider
import ai.rever.bosseditor.input.EditorInputHandler
import ai.rever.bosseditor.rendering.EditorCanvas
import ai.rever.bosseditor.rendering.EditorToken
import ai.rever.bosseditor.scrollbar.EditorScrollbar
import ai.rever.bosseditor.scrollbar.HorizontalEditorScrollbar
import ai.rever.bosseditor.scrollbar.rememberEditorScrollbarAdapter
import ai.rever.bosseditor.scrollbar.rememberHorizontalEditorScrollbarAdapter
import ai.rever.bosseditor.theme.EditorColors
import ai.rever.bosseditor.theme.EditorTheme
import ai.rever.bosseditor.theme.LocalEditorTheme
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Navigation resolver result.
 */
sealed class NavigationResolveResult {
    data class Found(val filePath: String, val line: Int, val column: Int) : NavigationResolveResult()
    data object NotFound : NavigationResolveResult()
}

/**
 * Main composable entry point for BossEditor.
 *
 * A full-featured code editor built with Compose Canvas, designed to replace
 * RSyntaxTextArea with native Compose rendering.
 *
 * ## Features
 * - Syntax highlighting (via token provider)
 * - Code folding (planned)
 * - Search and replace
 * - Multiple themes
 * - Undo/redo with typing coalescing
 * - Selection (mouse and keyboard)
 * - Clipboard operations (copy, cut, paste)
 *
 * ## Usage
 * ```kotlin
 * val editorState = remember { EditorState("fun main() {\n    println(\"Hello\")\n}") }
 *
 * BossEditor(
 *     state = editorState,
 *     theme = EditorTheme.Dark,
 *     modifier = Modifier.fillMaxSize()
 * )
 * ```
 *
 * @param state The editor state containing document, caret, selection
 * @param theme The visual theme (Dark, Light, Dracula, etc.)
 * @param modifier Modifier for the root composable
 * @param fontFamily Font family (should be monospace)
 * @param fontSize Font size in scaled pixels
 * @param lineSpacing Line height multiplier (1.0 = tight, 1.2 = comfortable, 1.5 = spacious)
 * @param showLineNumbers Whether to show line number gutter
 * @param highlightCurrentLine Whether to highlight current line
 * @param readOnly If true, editing operations are disabled
 * @param searchQuery Current search query (null if not searching)
 * @param searchMatches List of search match ranges
 * @param currentSearchMatchIndex Index of current search match
 * @param filePath Current file path (enables PSI-based features like navigation)
 * @param projectPath Project root path (enables cross-file navigation)
 * @param tokenProvider Function to get tokens for a line (for syntax highlighting)
 * @param onTextChanged Callback when text changes
 * @param onCaretPositionChanged Callback when caret position changes
 * @param onSelectionChanged Callback when selection changes
 * @param navigationResolver Custom navigation resolver (if provided, uses this instead of internal PSI).
 *                           Takes file content, file path, and click offset; returns resolved target.
 * @param onNavigate Callback for code navigation (Cmd+Click go-to-definition)
 * @param onShowUsages Callback when clicking on a definition to show all usages
 * @param showMinimap Whether to show the minimap (code overview)
 * @param minimapWidth Width of the minimap in pixels
 */
@Composable
fun BossEditor(
    state: EditorState,
    modifier: Modifier = Modifier,
    isActiveEditor: Boolean = true,
    theme: EditorTheme = EditorTheme.Dark,
    fontFamily: FontFamily = FontFamily.Monospace,
    fontSize: Float = 14f,
    lineSpacing: Float = 1.2f,
    showLineNumbers: Boolean = true,
    highlightCurrentLine: Boolean = true,
    readOnly: Boolean = false,
    searchQuery: String? = null,
    searchMatches: List<EditorRange> = emptyList(),
    currentSearchMatchIndex: Int = -1,
    foldingEnabled: Boolean = true,
    scrollSpeed: Float = 1.5f,
    filePath: String? = null,
    projectPath: String? = null,
    showMinimap: Boolean = true,
    showScrollbar: Boolean = true,
    minimapWidth: Int = 80,
    minimapUseEditorColors: Boolean = true,
    minimapBackgroundColor: Color? = null,
    minimapForegroundColor: Color? = null,
    tokenProvider: (Int) -> List<EditorToken> = { emptyList() },
    onTextChanged: () -> Unit = {},
    onCaretPositionChanged: (EditorPosition) -> Unit = {},
    onSelectionChanged: (EditorRange?) -> Unit = {},
    navigationResolver: (suspend (content: String, filePath: String, offset: Int) -> NavigationResolveResult)? = null,
    onNavigate: ((filePath: String, line: Int, column: Int) -> Unit)? = null,
    onShowUsages: ((references: List<ReferenceLocation>, definition: DefinitionInfo, clickPosition: Offset) -> Unit)? = null,
    onNavigationFailed: ((reason: NavigationFailureReason, clickPosition: Offset) -> Unit)? = null
) {
    // Create input handler
    val inputHandler = remember(state) {
        EditorInputHandler(
            state = state,
            onTextChanged = onTextChanged
        )
    }

    // Create navigation manager for semantic highlighting (always created for Kotlin files)
    // Navigation resolution can be overridden via navigationResolver parameter
    val navigationManager = remember(state.document, filePath) {
        NavigationManager(state.document, filePath)
    }

    // Update project path when it changes
    LaunchedEffect(projectPath) {
        navigationManager.setProjectPath(projectPath)
    }

    // Trigger semantic analysis when content changes (for semantic highlighting)
    LaunchedEffect(state.document.documentVersion) {
        navigationManager.analyzeContent()
    }

    // Cleanup navigation manager
    DisposableEffect(navigationManager) {
        onDispose {
            navigationManager.dispose()
        }
    }

    // Set up fold parser based on file type
    LaunchedEffect(filePath, foldingEnabled) {
        if (foldingEnabled && filePath != null) {
            val parser = when {
                filePath.endsWith(".kt") || filePath.endsWith(".kts") -> KotlinFoldParser()
                // Add more parsers for other languages here
                // filePath.endsWith(".java") -> JavaFoldParser()
                // filePath.endsWith(".js") || filePath.endsWith(".ts") -> JavaScriptFoldParser()
                else -> null
            }
            state.setFoldParser(parser)
        } else {
            state.setFoldParser(null)
        }
    }

    // Handle fold toggle
    val handleFoldToggle: (Int) -> Unit = remember(state) {
        { documentLine ->
            state.toggleFoldAt(documentLine)
        }
    }

    // Coroutine scope for navigation
    val coroutineScope = rememberCoroutineScope()

    // Handle navigation request from EditorCanvas
    val handleNavigationRequest: (EditorPosition, Offset) -> Unit = remember(navigationResolver, navigationManager, onNavigate, onShowUsages, onNavigationFailed, filePath, coroutineScope) {
        { position, clickPosition ->
            if (onNavigate != null) {
                coroutineScope.launch {
                    // Use custom resolver if provided, otherwise fall back to internal NavigationManager
                    if (navigationResolver != null && filePath != null) {
                        val content = state.document.getText()
                        val offset = state.document.positionToOffset(position)
                        when (val result = navigationResolver(content, filePath, offset)) {
                            is NavigationResolveResult.Found -> {
                                onNavigate.invoke(result.filePath, result.line, result.column)
                            }
                            is NavigationResolveResult.NotFound -> {
                                onNavigationFailed?.invoke(NavigationFailureReason.NOT_FOUND, clickPosition)
                            }
                        }
                    } else {
                        // Fall back to internal NavigationManager
                        when (val result = navigationManager.resolveNavigation(position)) {
                            is NavigationOutcome.Found -> {
                                onNavigate.invoke(result.filePath, result.line, result.column)
                            }
                            is NavigationOutcome.ShowUsages -> {
                                onShowUsages?.invoke(result.references, result.definition, clickPosition)
                            }
                            is NavigationOutcome.NotFound -> {
                                onNavigationFailed?.invoke(NavigationFailureReason.NOT_FOUND, clickPosition)
                            }
                            is NavigationOutcome.Unavailable -> {
                                onNavigationFailed?.invoke(NavigationFailureReason.UNAVAILABLE, clickPosition)
                            }
                        }
                    }
                }
            }
        }
    }

    // Track viewport information for minimap (synced from EditorCanvas via EditorState)
    // Uses actual measured values from EditorCanvas for accurate scroll calculation
    val visibleViewport by state.visibleViewport.collectAsState()
    val firstVisibleLine = visibleViewport.firstVisibleLine
    val visibleLineCount = visibleViewport.visibleLineCount
    // Use actual measured line height from EditorCanvas (not fontSize * lineSpacing which may differ)
    val actualLineHeight = visibleViewport.lineHeight.takeIf { it > 0 } ?: (fontSize * lineSpacing)
    val actualViewportHeight = visibleViewport.viewportHeight.takeIf { it > 0 } ?: 0f

    val density = LocalDensity.current

    // Get visual line mapper for fold-aware minimap rendering and scrollbar
    val visualLineMapper by state.visualLineMapper.collectAsState()

    // Collect scroll offset for scrollbar
    val scrollOffset by state.scrollOffset.collectAsState()

    // Track scroll activity for auto-show scrollbars
    var verticalScrollTrigger by remember { mutableStateOf(0) }
    var horizontalScrollTrigger by remember { mutableStateOf(0) }
    LaunchedEffect(scrollOffset.y) {
        verticalScrollTrigger++
    }
    LaunchedEffect(scrollOffset.x) {
        horizontalScrollTrigger++
    }

    // Get horizontal viewport info
    val actualViewportWidth = visibleViewport.viewportWidth.takeIf { it > 0 } ?: 0f
    val actualContentWidth = visibleViewport.contentWidth.takeIf { it > 0 } ?: 0f

    // Create vertical scrollbar adapter
    val scrollbarAdapter = rememberEditorScrollbarAdapter(
        editorScrollOffset = rememberUpdatedState(scrollOffset.y),
        totalLines = { visualLineMapper.visibleLineCount },
        viewportHeight = { actualViewportHeight },
        lineHeight = { actualLineHeight },
        onScroll = { newScrollY ->
            state.setScrollOffset(ai.rever.bosseditor.core.ScrollOffset(
                x = scrollOffset.x,
                y = newScrollY
            ))
        }
    )

    // Create horizontal scrollbar adapter
    val horizontalScrollbarAdapter = rememberHorizontalEditorScrollbarAdapter(
        editorScrollOffset = rememberUpdatedState(scrollOffset.x),
        contentWidth = { actualContentWidth },
        viewportWidth = { actualViewportWidth },
        onScroll = { newScrollX ->
            state.setScrollOffset(ai.rever.bosseditor.core.ScrollOffset(
                x = newScrollX,
                y = scrollOffset.y
            ))
        }
    )

    // Get current caret line for minimap current line indicator
    val caretPosition by state.caretPosition.collectAsState()
    val minimapCurrentLine = caretPosition.line

    // Convert selection to OffsetRange for minimap
    val selectionValue by state.selection.collectAsState()
    val minimapSelection = remember(selectionValue) {
        selectionValue?.let { selection ->
            val startOffset = state.document.positionToOffset(selection.start)
            val endOffset = state.document.positionToOffset(selection.end)
            OffsetRange(startOffset, endOffset)
        }
    }

    // Create token provider for minimap (reusing the lexer)
    val minimapTokenProvider: TokenProvider? = remember(filePath) {
        if (filePath?.endsWith(".kt") == true || filePath?.endsWith(".kts") == true) {
            object : TokenProvider {
                override val languageId = "kotlin"
                override val fileExtensions = listOf("kt", "kts")
                private val lexer = KotlinLexer()
                override fun tokenizeLine(text: String, lineNumber: Int, startState: LexerState) =
                    lexer.tokenizeLine(text, lineNumber, startState)
            }
        } else {
            null
        }
    }

    // Handle scroll to line from minimap (receives visual line)
    // Centers the clicked visual line in the viewport
    val handleMinimapScrollToLine: (Int) -> Unit = remember(state, actualLineHeight, actualViewportHeight, visualLineMapper) {
        { visualLine ->
            // Calculate scroll offset to CENTER the visual line in the viewport
            val clampedVisualLine = visualLine.coerceIn(0, (visualLineMapper.visibleLineCount - 1).coerceAtLeast(0))

            // Calculate content height and max scroll using actual measured values
            val contentHeight = visualLineMapper.visibleLineCount * actualLineHeight
            val maxScrollY = (contentHeight - actualViewportHeight).coerceAtLeast(0f).toInt()

            // Calculate scroll Y to center the clicked line
            // lineY is where the line starts, subtract half viewport to center it
            val lineY = clampedVisualLine * actualLineHeight
            val centeredScrollY = (lineY - actualViewportHeight / 2).toInt()

            // Clamp to valid range (handles edge cases at top and bottom)
            val newScrollY = centeredScrollY.coerceIn(0, maxScrollY)

            // Preserve current X offset
            val currentScrollOffset = state.scrollOffset.value
            state.setScrollOffset(ai.rever.bosseditor.core.ScrollOffset(
                x = currentScrollOffset.x,
                y = newScrollY
            ))
        }
    }

    // Minimap configuration
    val minimapConfig = remember(minimapWidth) {
        MinimapConfig(
            maxWidth = minimapWidth.toFloat(),
            minWidth = 50f,
            enabled = true,
            renderCharacters = true  // Enable syntax-highlighted colorful rendering
        )
    }

    // Provide theme via CompositionLocal
    CompositionLocalProvider(LocalEditorTheme provides theme) {
        Column(
            modifier = modifier
                .onKeyEvent { event ->
                    if (!readOnly) {
                        inputHandler.handleKeyEvent(event)
                    } else {
                        // In read-only mode, still allow navigation but not editing
                        handleReadOnlyKeyEvent(state, event)
                    }
                }
        ) {
            // Main content row (editor + minimap)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
            // Main editor canvas
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                EditorCanvas(
                    editorState = state,
                    modifier = Modifier.fillMaxSize(),
                    isActiveEditor = isActiveEditor,
                    fontFamily = fontFamily,
                    fontSize = fontSize,
                    lineSpacing = lineSpacing,
                    showLineNumbers = showLineNumbers,
                    highlightCurrentLine = highlightCurrentLine,
                    searchQuery = searchQuery,
                    searchMatches = searchMatches,
                    currentSearchMatchIndex = currentSearchMatchIndex,
                    foldingEnabled = foldingEnabled,
                    scrollSpeed = scrollSpeed,
                    getLineTokens = tokenProvider,
                    onCaretPositionChanged = onCaretPositionChanged,
                    onSelectionChanged = onSelectionChanged,
                    onNavigationRequest = if (onNavigate != null) handleNavigationRequest else null,
                    onFoldToggle = if (foldingEnabled) handleFoldToggle else null
                )

                // Vertical Scrollbar (right edge of editor canvas)
                if (showScrollbar) {
                    EditorScrollbar(
                        adapter = scrollbarAdapter,
                        modifier = Modifier.align(Alignment.CenterEnd),
                        thumbColor = if (theme.isDark) {
                            Color.White.copy(alpha = 0.5f)
                        } else {
                            Color.Black.copy(alpha = 0.4f)
                        },
                        trackColor = Color.Transparent,
                        errorMarkerColor = theme.colors.error,
                        warningMarkerColor = theme.colors.warningSquiggle,
                        userScrollTrigger = rememberUpdatedState(verticalScrollTrigger)
                    )
                }

                // Horizontal Scrollbar (bottom edge of editor canvas, overlays content)
                if (showScrollbar && actualContentWidth > actualViewportWidth) {
                    HorizontalEditorScrollbar(
                        adapter = horizontalScrollbarAdapter,
                        modifier = Modifier.align(Alignment.BottomCenter),
                        thumbColor = if (theme.isDark) {
                            Color.White.copy(alpha = 0.5f)
                        } else {
                            Color.Black.copy(alpha = 0.4f)
                        },
                        trackColor = Color.Transparent,
                        userScrollTrigger = rememberUpdatedState(horizontalScrollTrigger)
                    )
                }
            }

            // Minimap (right side)
            if (showMinimap) {
                // Compute minimap colors - use custom colors if specified, otherwise theme colors
                val minimapColors = remember(
                    minimapUseEditorColors,
                    minimapBackgroundColor,
                    minimapForegroundColor,
                    theme.colors
                ) {
                    if (minimapUseEditorColors) {
                        // Use editor theme colors
                        theme.colors
                    } else {
                        // Use custom colors with fallback to theme colors
                        theme.colors.copy(
                            background = minimapBackgroundColor ?: theme.colors.background,
                            text = minimapForegroundColor ?: theme.colors.text,
                            minimapBackground = minimapBackgroundColor ?: theme.colors.minimapBackground,
                            minimapForeground = minimapForegroundColor ?: theme.colors.minimapForeground
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(theme.colors.gutterBackground)
                )

                // Convert EditorRange (line/column) to OffsetRange (absolute offsets) for minimap
                val searchResultsAsOffsets = remember(searchMatches, state.document.documentVersion) {
                    searchMatches.map { range ->
                        OffsetRange(
                            start = state.document.positionToOffset(range.start),
                            end = state.document.positionToOffset(range.end)
                        )
                    }
                }

                MinimapCanvas(
                    document = state.document,
                    tokenProvider = minimapTokenProvider,
                    colors = minimapColors,
                    visualLineMapper = visualLineMapper,
                    firstVisibleLine = firstVisibleLine,
                    visibleLineCount = visibleLineCount,
                    currentLine = minimapCurrentLine,
                    selection = minimapSelection,
                    searchResults = searchResultsAsOffsets,
                    occurrences = emptyList(),
                    diagnostics = emptyList(),
                    config = minimapConfig,
                    onLineClicked = handleMinimapScrollToLine,
                    onDragToLine = handleMinimapScrollToLine,
                    modifier = Modifier.fillMaxHeight()
                )
            }
            } // End Row
        } // End Column
    }
}

/**
 * Handles key events in read-only mode (navigation only).
 */
private fun handleReadOnlyKeyEvent(state: EditorState, event: KeyEvent): Boolean {
    if (event.type != KeyEventType.KeyDown) return false

    val isShift = event.isShiftPressed
    val isCtrl = event.isCtrlPressed
    val isMeta = event.isMetaPressed
    val isCmdOrCtrl = isMeta || isCtrl

    return when {
        // Navigation
        event.key == Key.DirectionLeft -> {
            if (isCmdOrCtrl) {
                state.moveCaretToLineStart(isShift)
            } else {
                state.moveCaretBy(0, -1, isShift)
            }
            true
        }
        event.key == Key.DirectionRight -> {
            if (isCmdOrCtrl) {
                state.moveCaretToLineEnd(isShift)
            } else {
                state.moveCaretBy(0, 1, isShift)
            }
            true
        }
        event.key == Key.DirectionUp -> {
            if (isCmdOrCtrl) {
                state.moveCaretToStart(isShift)
            } else {
                state.moveCaretBy(-1, 0, isShift)
            }
            true
        }
        event.key == Key.DirectionDown -> {
            if (isCmdOrCtrl) {
                state.moveCaretToEnd(isShift)
            } else {
                state.moveCaretBy(1, 0, isShift)
            }
            true
        }
        event.key == Key.Home -> {
            if (isCmdOrCtrl) {
                state.moveCaretToStart(isShift)
            } else {
                state.moveCaretToLineStart(isShift)
            }
            true
        }
        event.key == Key.MoveEnd -> {
            if (isCmdOrCtrl) {
                state.moveCaretToEnd(isShift)
            } else {
                state.moveCaretToLineEnd(isShift)
            }
            true
        }

        // Select all (Cmd/Ctrl + A)
        isCmdOrCtrl && event.key == Key.A -> {
            state.selectAll()
            true
        }

        // Copy (Cmd/Ctrl + C) - allowed in read-only mode
        isCmdOrCtrl && event.key == Key.C -> {
            copyToClipboard(state)
            true
        }

        else -> false
    }
}

/**
 * Copies selected text to clipboard.
 */
private fun copyToClipboard(state: EditorState) {
    val text = state.selectedText
    if (text.isNotEmpty()) {
        try {
            val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
            clipboard.setContents(java.awt.datatransfer.StringSelection(text), null)
        } catch (e: Exception) {
            // Ignore clipboard errors
        }
    }
}

/**
 * Creates and remembers an EditorState.
 *
 * @param initialText The initial text content
 * @param filePath Optional file path for the document
 */
@Composable
fun rememberEditorState(
    initialText: String = "",
    filePath: String? = null
): EditorState {
    return remember(filePath) {
        EditorState(initialText, filePath)
    }
}
