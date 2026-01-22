package ai.rever.boss.components.plugin.tab_types

import ai.rever.boss.font.FontManager
import ai.rever.boss.components.events.NavigationTargetBus
import ai.rever.boss.window.LocalWindowId
import ai.rever.boss.psi.NavigationEvent
import ai.rever.boss.psi.NavigationHandler
import ai.rever.boss.psi.PSIBootstrap
import ai.rever.boss.psi.SemanticHighlighter
import ai.rever.boss.run.DetectedMainFunction
import ai.rever.boss.run.Language
import ai.rever.boss.run.MainFunctionDetectorProvider
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea
import org.fife.ui.rsyntaxtextarea.SyntaxConstants
import org.fife.ui.rtextarea.RTextScrollPane
import java.awt.Font
import java.awt.event.AdjustmentListener
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.SwingUtilities
import javax.swing.event.CaretEvent
import javax.swing.event.CaretListener
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/**
 * A complete code editor combining RSyntaxTextArea with a Compose-based run gutter.
 *
 * This composable provides:
 * - Full RSyntaxTextArea functionality (syntax highlighting, code folding, etc.)
 * - Scroll-synchronized run icons for detected main functions
 * - Theme integration with BOSS themes
 * - Keyboard shortcut integration
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
fun RSyntaxEditorWithGutter(
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
    val windowId = LocalWindowId.current  // Issue #506: Get window ID for multi-window filtering

    // Thread-safe flag to prevent update loops between Compose and Swing
    // Uses AtomicBoolean for thread-safe compareAndSet operations across EDT and Main dispatcher
    val isInternalUpdate = remember { AtomicBoolean(false) }

    // Track original content for modification detection
    var originalContent by remember { mutableStateOf(content) }

    // State for detected main functions
    var detectedMainFunctions by remember { mutableStateOf<List<DetectedMainFunction>>(emptyList()) }

    // Gutter state synchronized with RSyntaxTextArea
    var gutterState by remember { mutableStateOf(RSyntaxGutterState()) }

    // Create and remember RSyntaxTextArea instance
    val editorState = remember(filePath) {
        RSyntaxEditorState(filePath, onNavigate).also { state ->
            // CRITICAL: ALL Swing component configuration must happen on EDT
            // This includes setting syntaxEditingStyle, text content, and theme
            // Previously, these were set off-EDT which caused tokenization issues
            val configureOnEdt = {
                state.textArea.apply {
                    // Configure editor features first
                    isCodeFoldingEnabled = true
                    isAutoIndentEnabled = true
                    tabSize = 4
                    isEditable = !isReadOnly
                    antiAliasingEnabled = true
                    markOccurrences = true
                    paintMatchedBracketPair = true
                    isBracketMatchingEnabled = true
                    highlightCurrentLine = true
                    fadeCurrentLineHighlight = true
                    isWhitespaceVisible = false
                    eolMarkersVisible = false
                    paintTabLines = true

                    // Set initial font
                    font = createRSyntaxEditorFont(fontFamily, fontSize)

                    // Configure keyboard bindings
                    configureBossKeyBindings()

                    // IMPORTANT ORDER: Set syntax style BEFORE content
                    // This ensures the correct TokenMaker is in place when text is set
                    syntaxEditingStyle = mapLanguageToSyntaxStyle(language)

                    // Set initial content (tokenization happens during this call)
                    text = content
                }

                // Apply theme (which also ensures TokenMaker is correctly attached)
                RSyntaxThemeMapper.applyTheme(state.textArea, theme)
                state.textArea.revalidate()
                state.textArea.repaint()
            }

            // Use invokeAndWait to ensure configuration is complete before returning
            // This prevents "flash of unstyled content" and tokenization issues
            if (SwingUtilities.isEventDispatchThread()) {
                configureOnEdt()
            } else {
                SwingUtilities.invokeAndWait {
                    configureOnEdt()
                }
            }
        }
    }

    val textArea = editorState.textArea
    val scrollPane = editorState.scrollPane

    // PSI is initialized at app startup in main.kt (no lazy init needed here)

    // Detect main functions when content changes
    LaunchedEffect(content, filePath) {
        if (filePath.isNotEmpty() && content.isNotEmpty()) {
            // Capture the current file path to verify after async detection
            val currentFilePath = filePath
            withContext(Dispatchers.IO) {
                try {
                    val detector = MainFunctionDetectorProvider.get()
                    val langEnum = Language.fromFileName(currentFilePath)
                    val detected = detector.detectInFile(currentFilePath, content, langEnum)
                    withContext(Dispatchers.Main) {
                        // Only update if the file hasn't changed during detection
                        if (filePath == currentFilePath) {
                            detectedMainFunctions = detected
                        }
                    }
                } catch (e: Exception) {
                    println("[RSyntaxEditor] Error detecting main functions: ${e.message}")
                    withContext(Dispatchers.Main) {
                        // Only clear if file hasn't changed
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

    // Update content when external changes occur
    LaunchedEffect(content) {
        // Thread-safe check: only proceed if we atomically set the flag
        if (textArea.text != content && isInternalUpdate.compareAndSet(false, true)) {
            SwingUtilities.invokeLater {
                try {
                    // Save caret position
                    val caretPos = textArea.caretPosition.coerceIn(0, content.length)
                    textArea.text = content
                    // Restore caret position
                    textArea.caretPosition = caretPos.coerceIn(0, textArea.document.length)
                    // Force re-tokenization after content change to ensure syntax highlighting
                    textArea.forceReparsing(0)
                    textArea.revalidate()
                    textArea.repaint()
                } finally {
                    // Post back to Main dispatcher to reset flag (avoid modifying from EDT)
                    coroutineScope.launch(Dispatchers.Main) {
                        isInternalUpdate.set(false)
                    }
                }
            }
        }
        // Notify navigation handler that content changed (invalidates cached PSI)
        editorState.updateContent(content)
    }

    // Update language syntax highlighting
    LaunchedEffect(language) {
        SwingUtilities.invokeLater {
            textArea.syntaxEditingStyle = mapLanguageToSyntaxStyle(language)
            // Force re-tokenization and repaint after language change
            textArea.forceReparsing(0)
            textArea.revalidate()
            textArea.repaint()
        }
    }

    // Update theme
    LaunchedEffect(theme) {
        SwingUtilities.invokeLater {
            RSyntaxThemeMapper.applyTheme(textArea, theme)
            textArea.revalidate()
            textArea.repaint()
        }
    }

    // Update font settings
    LaunchedEffect(fontSize, fontFamily) {
        SwingUtilities.invokeLater {
            textArea.font = createRSyntaxEditorFont(fontFamily, fontSize)
            textArea.revalidate()
            textArea.repaint()
        }
    }

    // Update read-only state
    LaunchedEffect(isReadOnly) {
        SwingUtilities.invokeLater {
            textArea.isEditable = !isReadOnly
        }
    }

    // Listen for navigation targets (PSI go-to-definition cursor positioning)
    // Issue #506: Filter by windowId for multi-window support
    LaunchedEffect(filePath, windowId) {
        NavigationTargetBus.targets
            .collect { target ->
                // Only process if this editor is showing the target file and event is for this window
                val isForThisWindow = target.sourceWindowId == windowId
                if (isForThisWindow && target.filePath == filePath && target.line > 0) {
                    SwingUtilities.invokeLater {
                        try {
                            val text = textArea.text ?: return@invokeLater
                            val lines = text.split("\n")

                            // Calculate offset from line/column (both are 1-based)
                            var offset = 0
                            for (i in 0 until (target.line - 1).coerceAtMost(lines.size)) {
                                offset += lines[i].length + 1  // +1 for newline
                            }
                            offset += (target.column - 1).coerceAtLeast(0)

                            // Position cursor and scroll into view
                            val safeOffset = offset.coerceIn(0, text.length)
                            textArea.caretPosition = safeOffset
                            textArea.requestFocusInWindow()

                            // Clear replay cache after consumption to avoid re-triggering
                            NavigationTargetBus.clearCache()
                        } catch (e: Exception) {
                            println("[RSyntaxEditor] Error positioning cursor: ${e.message}")
                        }
                    }
                }
            }
    }

    // Register with EditorSearchEventBus for search/replace functionality
    DisposableEffect(editorState) {
        val searchListener = object : EditorSearchEventBus.SearchActionListener {
            override fun onFind() = editorState.searchManager.showFind()
            override fun onReplace() = editorState.searchManager.showReplace()
            override fun onFindNext() = editorState.searchManager.findNext()
            override fun onFindPrevious() = editorState.searchManager.findPrevious()
            override fun onGoToLine() = editorState.searchManager.showGoToLine()
        }

        // Add focus listener to register/unregister with event bus
        val focusListener = object : java.awt.event.FocusListener {
            override fun focusGained(e: java.awt.event.FocusEvent?) {
                EditorSearchEventBus.registerListener(searchListener)
            }
            override fun focusLost(e: java.awt.event.FocusEvent?) {
                // Don't unregister immediately - dialogs cause focus loss
            }
        }
        textArea.addFocusListener(focusListener)

        // Register initially if already focused or as default
        EditorSearchEventBus.registerListener(searchListener)

        onDispose {
            textArea.removeFocusListener(focusListener)
            EditorSearchEventBus.unregisterListener(searchListener)
            editorState.dispose()
        }
    }

    // Set up document and scroll listeners
    DisposableEffect(textArea, scrollPane) {
        // Create a dedicated scope for listener coroutines with SupervisorJob
        // This ensures all coroutines launched from listeners are cancelled on dispose
        val listenerJob = SupervisorJob()
        val listenerScope = CoroutineScope(Dispatchers.Main + listenerJob)

        // Function to update gutter state
        fun updateGutterState() {
            SwingUtilities.invokeLater {
                try {
                    val fontMetrics = textArea.getFontMetrics(textArea.font)
                    val lineHeight = fontMetrics.height
                    val viewRect = scrollPane.viewport.viewRect
                    val scrollY = viewRect.y
                    val viewHeight = viewRect.height

                    val firstVisible = if (lineHeight > 0) scrollY / lineHeight else 0
                    val visibleCount = if (lineHeight > 0) (viewHeight / lineHeight) + 2 else 0
                    val gutter = scrollPane.gutter
                    val gutterWidth = gutter?.preferredSize?.width ?: 0

                    listenerScope.launch {
                        gutterState = RSyntaxGutterState(
                            scrollOffset = scrollY,
                            lineHeight = lineHeight,
                            totalLines = textArea.lineCount,
                            firstVisibleLine = firstVisible,
                            visibleLineCount = visibleCount,
                            gutterWidth = gutterWidth
                        )
                    }
                } catch (e: Exception) {
                    println("[RSyntaxEditor] Error updating gutter state: ${e.message}")
                }
            }
        }

        // Document listener for content changes
        val documentListener = object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) {
                notifyChange()
                updateGutterState()
            }
            override fun removeUpdate(e: DocumentEvent) {
                notifyChange()
                updateGutterState()
            }
            override fun changedUpdate(e: DocumentEvent) {
                notifyChange()
                updateGutterState()
            }

            private fun notifyChange() {
                // Thread-safe check: only proceed if we atomically set the flag
                if (isInternalUpdate.compareAndSet(false, true)) {
                    listenerScope.launch {
                        try {
                            val newContent = textArea.text
                            onContentChange(newContent)
                            val isModified = newContent != originalContent
                            onModifiedStateChange(isModified)
                        } finally {
                            isInternalUpdate.set(false)
                        }
                    }
                }
            }
        }
        textArea.document.addDocumentListener(documentListener)

        // Caret listener for cursor position updates
        val caretListener = CaretListener { e: CaretEvent ->
            try {
                val dot = e.dot
                val line = textArea.getLineOfOffset(dot) + 1
                val lineStart = textArea.getLineStartOffset(line - 1)
                val column = dot - lineStart + 1
                listenerScope.launch {
                    onCursorPositionChange(line, column)
                }
            } catch (ex: Exception) {
                // Log but don't crash - can happen during rapid updates or document changes
                println("[RSyntaxEditor] Error updating cursor position: ${ex.message}")
            }
        }
        textArea.addCaretListener(caretListener)

        // Scroll listener for gutter synchronization
        val scrollListener = AdjustmentListener { _ ->
            updateGutterState()
        }
        scrollPane.verticalScrollBar?.addAdjustmentListener(scrollListener)

        // Initial gutter state
        updateGutterState()

        onDispose {
            // Cancel all pending coroutines from listeners
            listenerJob.cancel()
            textArea.document.removeDocumentListener(documentListener)
            textArea.removeCaretListener(caretListener)
            scrollPane.verticalScrollBar?.removeAdjustmentListener(scrollListener)
        }
    }

    // Update original content reference when filePath changes (new file loaded)
    LaunchedEffect(filePath) {
        originalContent = content
        onModifiedStateChange(false)
    }

    // Layout: Run gutter | Editor
    Row(modifier = modifier.fillMaxSize()) {
        // Run gutter overlay (Compose)
        if (gutterState.lineHeight > 0 && detectedMainFunctions.isNotEmpty()) {
            RunGutterColumn(
                detectedMainFunctions = detectedMainFunctions,
                gutterState = gutterState,
                onRun = onRun,
                modifier = Modifier
                    .width(24.dp)
                    .fillMaxHeight()
                    .background(Color(0xFF2B2B2B)) // Match editor background
            )
        }

        // RSyntaxTextArea (Swing embedded via SwingPanel)
        SwingPanel(
            factory = { scrollPane },
            modifier = Modifier.fillMaxSize(),
            update = { pane ->
                pane.revalidate()
                pane.repaint()
            }
        )
    }
}

/**
 * Composable column showing run icons for detected main functions.
 */
@Composable
private fun RunGutterColumn(
    detectedMainFunctions: List<DetectedMainFunction>,
    gutterState: RSyntaxGutterState,
    onRun: (DetectedMainFunction) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current

    // Calculate visible range with buffer
    val visibleRange = remember(gutterState.firstVisibleLine, gutterState.visibleLineCount, gutterState.totalLines) {
        val start = (gutterState.firstVisibleLine - 2).coerceAtLeast(0)
        val end = (gutterState.firstVisibleLine + gutterState.visibleLineCount + 2)
            .coerceAtMost(gutterState.totalLines)
        start until end
    }

    // Convert line height from pixels to dp
    val lineHeightDp = with(density) { gutterState.lineHeight.toDp() }

    Box(modifier = modifier) {
        detectedMainFunctions
            .filter { it.lineNumber in visibleRange }
            .forEach { detected ->
                val lineY = (detected.lineNumber * gutterState.lineHeight) - gutterState.scrollOffset

                // Only render if reasonably visible
                if (lineY >= -gutterState.lineHeight && lineY < gutterState.lineHeight * (gutterState.visibleLineCount + 4)) {
                    // Convert pixel offset to dp
                    val yOffsetDp = with(density) { lineY.toDp() }

                    Box(
                        modifier = Modifier
                            .offset(y = yOffsetDp)
                            .height(lineHeightDp)
                            .fillMaxWidth(),
                        contentAlignment = androidx.compose.ui.Alignment.Center
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
 * Holds the RSyntaxTextArea, scroll pane, search manager, navigation handler, and semantic highlighter.
 */
private class RSyntaxEditorState(
    initialFilePath: String,
    onNavigate: (NavigationEvent) -> Unit
) {
    val textArea: RSyntaxTextArea = RSyntaxTextArea(40, 100)

    val scrollPane: RTextScrollPane = RTextScrollPane(textArea).apply {
        lineNumbersEnabled = true
        isFoldIndicatorEnabled = true
    }

    // Search manager for find/replace functionality
    val searchManager: RSyntaxSearchManager by lazy { RSyntaxSearchManager(textArea) }

    // Navigation handler for Cmd+Click go-to-definition (Kotlin files only for now)
    val navigationHandler: NavigationHandler? = if (isKotlinFile(initialFilePath) && PSIBootstrap.isInitialized) {
        NavigationHandler(textArea, initialFilePath, onNavigate)
    } else null

    // Semantic highlighter for PSI-based syntax highlighting (function calls, properties, etc.)
    val semanticHighlighter: SemanticHighlighter? = if (isKotlinFile(initialFilePath) && PSIBootstrap.isInitialized) {
        SemanticHighlighter(textArea).also {
            // Set the current file path for token maker semantic lookup
            FixedKotlinTokenMaker.currentFilePath = initialFilePath
            it.setFilePath(initialFilePath)
        }
    } else null

    fun updateFilePath(filePath: String) {
        navigationHandler?.updateFilePath(filePath)
        semanticHighlighter?.setFilePath(filePath)

        // Update token maker file path for semantic lookup
        if (isKotlinFile(filePath)) {
            FixedKotlinTokenMaker.currentFilePath = filePath
        } else {
            FixedKotlinTokenMaker.currentFilePath = ""
        }
    }

    fun updateContent(content: String) {
        navigationHandler?.updateContent(content)

        // Trigger semantic analysis when content changes
        if (semanticHighlighter != null && FixedKotlinTokenMaker.currentFilePath.isNotEmpty()) {
            semanticHighlighter.analyzeAndHighlight(FixedKotlinTokenMaker.currentFilePath)
        }
    }

    fun dispose() {
        searchManager.dispose()
        navigationHandler?.dispose()
        semanticHighlighter?.dispose()
        FixedKotlinTokenMaker.currentFilePath = ""
    }

    companion object {
        fun isKotlinFile(filePath: String): Boolean {
            return filePath.endsWith(".kt") || filePath.endsWith(".kts")
        }
    }
}

/**
 * Creates a font for the RSyntax editor with the specified family and size.
 * Uses FontManager for intelligent font loading with bundled fallbacks.
 */
private fun createRSyntaxEditorFont(fontFamily: String, fontSize: Int): Font {
    return FontManager.createEditorFont(fontFamily, fontSize)
}
