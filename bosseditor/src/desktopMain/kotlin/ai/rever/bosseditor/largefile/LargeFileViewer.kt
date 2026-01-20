package ai.rever.bosseditor.largefile

import ai.rever.bosseditor.core.EditorPosition
import ai.rever.bosseditor.core.EditorRange
import ai.rever.bosseditor.core.ScrollOffset
import ai.rever.bosseditor.scrollbar.EditorScrollbar
import ai.rever.bosseditor.scrollbar.rememberEditorScrollbarAdapter
import ai.rever.bosseditor.theme.EditorTheme
import ai.rever.bosseditor.theme.LocalEditorTheme
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import java.io.File

/**
 * Read-only viewer for large files using page-based loading.
 *
 * This composable provides a simplified view of large files without loading
 * the entire content into memory. Features:
 * - Viewport-based rendering (only visible lines loaded)
 * - Mouse wheel scrolling
 * - Line numbers
 * - Basic text selection (copy only)
 * - Search with Ctrl+F (or Cmd+F on macOS)
 *
 * Not supported (read-only):
 * - Text editing
 * - Syntax highlighting
 * - Code folding
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun LargeFileViewer(
    state: LargeFileEditorState,
    modifier: Modifier = Modifier,
    theme: EditorTheme = EditorTheme.Dark,
    fontFamily: FontFamily = FontFamily.Monospace,
    fontSize: Float = 14f,
    lineSpacing: Float = 1.2f,
    showLineNumbers: Boolean = true,
    onCaretPositionChanged: (EditorPosition) -> Unit = {}
) {
    CompositionLocalProvider(LocalEditorTheme provides theme) {
        val colors = theme.colors
        val density = LocalDensity.current
        val textMeasurer = rememberTextMeasurer()
        val coroutineScope = rememberCoroutineScope()

        // Focus requester for the viewer to receive key events
        val viewerFocusRequester = remember { FocusRequester() }

        // Search bar visibility
        var showSearchBar by remember { mutableStateOf(false) }
        var searchQuery by remember { mutableStateOf("") }
        var caseSensitive by remember { mutableStateOf(false) }
        val searchFocusRequester = remember { FocusRequester() }

        // Collect search state
        val searchResults by state.searcher.results.collectAsState()
        val searchProgress by state.searcher.progress.collectAsState()
        val isSearching by state.searcher.isSearching.collectAsState()
        val currentSearchIndex by state.searcher.currentResultIndex.collectAsState()

        // Measure line height
        val textStyle = remember(fontFamily, fontSize) {
            TextStyle(
                fontFamily = fontFamily,
                fontSize = fontSize.sp,
                color = colors.text
            )
        }

        val lineHeightPx = remember(textStyle, lineSpacing) {
            textMeasurer.measure("Mg", textStyle).size.height.toFloat() * lineSpacing
        }

        // Measure a representative string to get accurate average advance width
        // Using alphanumeric characters provides better accuracy for proportional fonts
        val charWidthPx = remember(textStyle) {
            val sampleString = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
            textMeasurer.measure(sampleString, textStyle).size.width.toFloat() / sampleString.length
        }

        // Gutter width calculation
        val gutterWidth = remember(state.document.lineCount, charWidthPx) {
            val digitCount = state.document.lineCount.toString().length
            (digitCount + 2) * charWidthPx
        }

        // Collect state
        val scrollOffset by state.scrollOffset.collectAsState()
        val caretPosition by state.caretPosition.collectAsState()
        val selection by state.selection.collectAsState()

        // Notify caret changes
        LaunchedEffect(caretPosition) {
            onCaretPositionChanged(caretPosition)
        }

        // Request focus for the viewer when first composed
        LaunchedEffect(Unit) {
            viewerFocusRequester.requestFocus()
        }

        // Focus search field when shown
        LaunchedEffect(showSearchBar) {
            if (showSearchBar) {
                searchFocusRequester.requestFocus()
            }
        }

        // Combined search effect - triggers when query or case sensitivity changes
        // This prevents race conditions from rapid toggling or typing
        LaunchedEffect(searchQuery, caseSensitive) {
            if (searchQuery.isNotEmpty()) {
                state.searcher.search(searchQuery, ignoreCase = !caseSensitive, scope = coroutineScope)
            } else {
                state.searcher.clear()
            }
        }

        // Viewport height for scrollbar adapter
        var viewportHeight by remember { mutableStateOf(0f) }

        // Scroll trigger to show scrollbar on user scroll
        var scrollTrigger by remember { mutableStateOf(0) }

        // Create scrollbar adapter
        val scrollbarAdapter = rememberEditorScrollbarAdapter(
            editorScrollOffset = rememberUpdatedState(scrollOffset.y),
            totalLines = { state.document.lineCount },
            viewportHeight = { viewportHeight },
            lineHeight = { lineHeightPx },
            onScroll = { newScrollY ->
                state.setScrollOffset(ScrollOffset(scrollOffset.x, newScrollY))
            }
        )

        // Helper to navigate to a search result
        fun navigateToResult(result: LargeFileSearchResult?) {
            result?.let {
                state.moveCaret(it.range.start)
                state.scrollToLine(it.range.start.line, lineHeightPx, viewportHeight)
            }
        }

        Column(
            modifier = modifier
                .background(colors.background)
                .focusRequester(viewerFocusRequester)
                .focusable()
                .onPreviewKeyEvent { keyEvent ->
                    if (keyEvent.type == KeyEventType.KeyDown) {
                        val isMacOs = System.getProperty("os.name").lowercase().contains("mac")
                        val isModifierPressed = if (isMacOs) keyEvent.isMetaPressed else keyEvent.isCtrlPressed

                        when {
                            // Ctrl+F / Cmd+F - Show search bar
                            isModifierPressed && keyEvent.key == Key.F -> {
                                showSearchBar = true
                                true
                            }
                            // Escape - Close search bar
                            keyEvent.key == Key.Escape && showSearchBar -> {
                                showSearchBar = false
                                state.searcher.clear()
                                searchQuery = ""
                                true
                            }
                            // F3 / Shift+F3 - Next/Previous result
                            keyEvent.key == Key.F3 && showSearchBar -> {
                                if (keyEvent.isShiftPressed) {
                                    navigateToResult(state.searcher.previousResult())
                                } else {
                                    navigateToResult(state.searcher.nextResult())
                                }
                                true
                            }
                            else -> false
                        }
                    } else {
                        false
                    }
                }
        ) {
            // Search bar
            if (showSearchBar) {
                LargeFileSearchBar(
                    query = searchQuery,
                    onQueryChange = { newQuery -> searchQuery = newQuery },
                    caseSensitive = caseSensitive,
                    onCaseSensitiveToggle = { caseSensitive = !caseSensitive },
                    resultCount = searchResults.size,
                    currentIndex = currentSearchIndex,
                    isSearching = isSearching,
                    progress = searchProgress,
                    onNext = { navigateToResult(state.searcher.nextResult()) },
                    onPrevious = { navigateToResult(state.searcher.previousResult()) },
                    onClose = {
                        showSearchBar = false
                        state.searcher.clear()
                        searchQuery = ""
                    },
                    focusRequester = searchFocusRequester,
                    theme = theme,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxSize().clipToBounds()) {
                    // Line number gutter
                    if (showLineNumbers) {
                        LargeFileGutter(
                            state = state,
                            lineHeight = lineHeightPx,
                            gutterWidth = gutterWidth,
                            textMeasurer = textMeasurer,
                            textStyle = textStyle.copy(color = colors.lineNumber),
                            backgroundColor = colors.gutterBackground,
                            modifier = Modifier.width(with(density) { gutterWidth.toDp() }).fillMaxHeight()
                        )
                    }

                    // Main content area
                    LargeFileCanvas(
                        state = state,
                        lineHeight = lineHeightPx,
                        charWidth = charWidthPx,
                        textMeasurer = textMeasurer,
                        textStyle = textStyle,
                        colors = colors,
                        searchResults = if (showSearchBar) searchResults else emptyList(),
                        currentSearchIndex = currentSearchIndex,
                        modifier = Modifier.fillMaxSize(),
                        onViewportHeightChanged = { height -> viewportHeight = height },
                        onUserScroll = { scrollTrigger++ }
                    )
                }

                // Vertical scrollbar
                EditorScrollbar(
                    adapter = scrollbarAdapter,
                    modifier = Modifier.align(Alignment.CenterEnd),
                    thumbColor = if (theme.isDark) Color.White.copy(alpha = 0.5f) else Color.Black.copy(alpha = 0.4f),
                    trackColor = Color.Transparent,
                    userScrollTrigger = rememberUpdatedState(scrollTrigger)
                )
            }
        }
    }
}

/**
 * Search bar for large file viewer.
 */
@Composable
private fun LargeFileSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    caseSensitive: Boolean,
    onCaseSensitiveToggle: () -> Unit,
    resultCount: Int,
    currentIndex: Int,
    isSearching: Boolean,
    progress: Float,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onClose: () -> Unit,
    focusRequester: FocusRequester,
    theme: EditorTheme,
    modifier: Modifier = Modifier
) {
    val colors = theme.colors

    Row(
        modifier = modifier
            .background(colors.gutterBackground)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Search input field
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = TextStyle(
                color = colors.text,
                fontSize = 13.sp
            ),
            cursorBrush = SolidColor(colors.caret),
            modifier = Modifier
                .weight(1f)
                .height(28.dp)
                .background(colors.background, RoundedCornerShape(4.dp))
                .border(1.dp, colors.gutterBorder, RoundedCornerShape(4.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .focusRequester(focusRequester)
                .onPreviewKeyEvent { keyEvent ->
                    if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.Enter) {
                        if (keyEvent.isShiftPressed) {
                            onPrevious()
                        } else {
                            onNext()
                        }
                        true
                    } else {
                        false
                    }
                },
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (query.isEmpty()) {
                        Text(
                            "Search (Ctrl+F)",
                            color = colors.lineNumber,
                            fontSize = 13.sp
                        )
                    }
                    innerTextField()
                }
            }
        )

        Spacer(Modifier.width(4.dp))

        // Case sensitivity toggle
        IconButton(
            onClick = onCaseSensitiveToggle,
            modifier = Modifier.size(28.dp)
        ) {
            Text(
                "Aa",
                color = if (caseSensitive) Color(0xFF4A90E2) else colors.lineNumber,
                fontSize = 12.sp,
                fontWeight = if (caseSensitive) FontWeight.Bold else FontWeight.Normal
            )
        }

        Spacer(Modifier.width(4.dp))

        // Result count / progress
        if (isSearching && progress < 1f) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    progress = progress,
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = colors.hyperlink
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    "${(progress * 100).toInt()}%",
                    color = colors.lineNumber,
                    fontSize = 12.sp
                )
            }
        } else if (query.isNotEmpty()) {
            Text(
                if (resultCount == 0) "No results"
                else "${currentIndex + 1} of $resultCount",
                color = if (resultCount == 0) colors.error else colors.lineNumber,
                fontSize = 12.sp
            )
        }

        Spacer(Modifier.width(8.dp))

        // Navigation buttons
        IconButton(
            onClick = onPrevious,
            enabled = resultCount > 0,
            modifier = Modifier.size(24.dp)
        ) {
            Icon(
                Icons.Default.KeyboardArrowUp,
                contentDescription = "Previous result (Shift+F3)",
                tint = if (resultCount > 0) colors.text else colors.lineNumber,
                modifier = Modifier.size(16.dp)
            )
        }

        IconButton(
            onClick = onNext,
            enabled = resultCount > 0,
            modifier = Modifier.size(24.dp)
        ) {
            Icon(
                Icons.Default.KeyboardArrowDown,
                contentDescription = "Next result (F3)",
                tint = if (resultCount > 0) colors.text else colors.lineNumber,
                modifier = Modifier.size(16.dp)
            )
        }

        Spacer(Modifier.width(4.dp))

        // Close button
        IconButton(
            onClick = onClose,
            modifier = Modifier.size(24.dp)
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Close search (Escape)",
                tint = colors.text,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun LargeFileGutter(
    state: LargeFileEditorState,
    lineHeight: Float,
    gutterWidth: Float,
    textMeasurer: TextMeasurer,
    textStyle: TextStyle,
    backgroundColor: Color,
    modifier: Modifier = Modifier
) {
    val scrollOffset by state.scrollOffset.collectAsState()

    Canvas(modifier = modifier.background(backgroundColor)) {
        val firstVisibleLine = ((scrollOffset.y / lineHeight).toInt() - 1).coerceAtLeast(0)
        val visibleLineCount = (size.height / lineHeight).toInt() + 2
        val lastVisibleLine = (firstVisibleLine + visibleLineCount).coerceAtMost(state.document.lineCount - 1)

        for (line in firstVisibleLine..lastVisibleLine) {
            val lineNumber = (line + 1).toString()
            val y = (line * lineHeight) - scrollOffset.y + lineHeight * 0.75f

            val textLayoutResult = textMeasurer.measure(lineNumber, textStyle)
            val x = gutterWidth - textLayoutResult.size.width - 8f

            drawText(
                textLayoutResult = textLayoutResult,
                topLeft = Offset(x, y - textLayoutResult.size.height * 0.75f)
            )
        }
    }
}

@Composable
private fun LargeFileCanvas(
    state: LargeFileEditorState,
    lineHeight: Float,
    charWidth: Float,
    textMeasurer: TextMeasurer,
    textStyle: TextStyle,
    colors: ai.rever.bosseditor.theme.EditorColors,
    searchResults: List<LargeFileSearchResult> = emptyList(),
    currentSearchIndex: Int = -1,
    modifier: Modifier = Modifier,
    onViewportHeightChanged: (Float) -> Unit = {},
    onUserScroll: () -> Unit = {}
) {
    val scrollOffset by state.scrollOffset.collectAsState()
    val caretPosition by state.caretPosition.collectAsState()
    val selection by state.selection.collectAsState()

    var viewportSize by remember { mutableStateOf(Size.Zero) }

    // Multi-click detection state
    var lastClickTime by remember { mutableStateOf(0L) }
    var lastClickPosition by remember { mutableStateOf<Offset?>(null) }
    var clickCount by remember { mutableStateOf(0) }
    var isDragging by remember { mutableStateOf(false) }
    var dragStartPosition by remember { mutableStateOf<EditorPosition?>(null) }

    // Constants for multi-click detection
    val multiClickTimeout = 400L
    val multiClickDistance = 10f

    // Helper to convert screen offset to editor position
    fun offsetToEditorPosition(offset: Offset): EditorPosition {
        val clickedLine = ((offset.y + scrollOffset.y) / lineHeight).toInt()
            .coerceIn(0, (state.document.lineCount - 1).coerceAtLeast(0))
        val clickedColumn = ((offset.x + scrollOffset.x) / charWidth).toInt()
            .coerceAtLeast(0)
            .coerceAtMost(state.document.getLineLength(clickedLine))
        return EditorPosition(clickedLine, clickedColumn)
    }

    // Helper to extend selection from anchor to position
    fun extendSelectionTo(position: EditorPosition) {
        val currentSel = state.selection.value
        val caretPos = state.caretPosition.value

        val anchor = currentSel?.let {
            if (caretPos == it.start) it.end else it.start
        } ?: caretPos

        val newRange = if (anchor <= position) {
            EditorRange(anchor, position)
        } else {
            EditorRange(position, anchor)
        }

        state.setSelection(newRange)
        state.moveCaret(position)
    }

    // Report viewport height changes to parent
    LaunchedEffect(viewportSize.height) {
        if (viewportSize.height > 0f) {
            onViewportHeightChanged(viewportSize.height)
        }
    }

    // Update visible line range when scroll or viewport changes (outside draw phase)
    LaunchedEffect(scrollOffset, viewportSize, lineHeight, charWidth) {
        if (viewportSize.height > 0 && lineHeight > 0) {
            val firstVisibleLine = (scrollOffset.y / lineHeight).toInt().coerceAtLeast(0)
            val visibleLineCount = (viewportSize.height / lineHeight).toInt() + 2

            state.updateVisibleLineRange(
                firstLine = firstVisibleLine,
                lineCount = visibleLineCount,
                lineHeight = lineHeight,
                viewportHeight = viewportSize.height,
                viewportWidth = viewportSize.width,
                charWidth = charWidth
            )
        }
    }

    Canvas(
        modifier = modifier
            .clipToBounds()
            .onSizeChanged { size ->
                viewportSize = size.toSize()
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    // Wait for initial press
                    val downEvent = awaitPointerEvent()
                    val down = downEvent.changes.firstOrNull() ?: return@awaitEachGesture
                    if (!down.pressed) return@awaitEachGesture

                    val currentTime = System.currentTimeMillis()
                    val position = down.position

                    // Multi-click detection
                    val isMultiClick = lastClickPosition?.let { lastPos ->
                        val timeDiff = currentTime - lastClickTime
                        val dx = position.x - lastPos.x
                        val dy = position.y - lastPos.y
                        val distance = kotlin.math.sqrt(dx * dx + dy * dy)
                        timeDiff < multiClickTimeout && distance < multiClickDistance
                    } ?: false

                    clickCount = if (isMultiClick) (clickCount % 3) + 1 else 1
                    lastClickTime = currentTime
                    lastClickPosition = position

                    val clickedPosition = offsetToEditorPosition(position)
                    val isShiftPressed = (downEvent.nativeEvent as? MouseEvent)?.isShiftDown ?: false

                    when (clickCount) {
                        1 -> {
                            if (isShiftPressed) {
                                extendSelectionTo(clickedPosition)
                                isDragging = false
                            } else {
                                state.moveCaret(clickedPosition)
                                state.clearSelection()
                                isDragging = true
                                dragStartPosition = clickedPosition
                            }
                        }
                        2 -> {
                            state.moveCaret(clickedPosition)
                            state.selectWord()
                            isDragging = false
                        }
                        3 -> {
                            state.moveCaret(clickedPosition)
                            state.selectLine()
                            isDragging = false
                        }
                    }

                    // NESTED inner loop for drag sequence - this is the key fix
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break

                        when (event.type) {
                            PointerEventType.Move -> {
                                if (isDragging && dragStartPosition != null) {
                                    val currentPos = offsetToEditorPosition(change.position)
                                    // Use moveCaret with extendSelection=true - it will calculate
                                    // the selection from the anchor (previous caret position) correctly
                                    state.moveCaret(currentPos, extendSelection = true)
                                }
                            }
                            PointerEventType.Release -> {
                                if (change.changedToUp()) {
                                    isDragging = false
                                    dragStartPosition = null
                                    break  // Exit inner loop, return to awaitEachGesture
                                }
                            }
                        }

                        // Consume position changes to prevent other handlers
                        if (change.positionChange() != Offset.Zero) {
                            change.consume()
                        }
                    }
                }
            }
            // Separate pointerInput for mouse wheel scrolling
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val nativeEvent = event.nativeEvent
                        if (nativeEvent is MouseWheelEvent) {
                            val scrollAmount = nativeEvent.wheelRotation * lineHeight * 3
                            val newY = (scrollOffset.y + scrollAmount).coerceIn(
                                0f,
                                (state.document.lineCount * lineHeight - viewportSize.height).coerceAtLeast(0f)
                            )
                            state.setScrollOffset(ScrollOffset(scrollOffset.x, newY.toInt()))
                            onUserScroll()
                        }
                    }
                }
            }
    ) {
        val firstVisibleLine = ((scrollOffset.y / lineHeight).toInt() - 1).coerceAtLeast(0)
        val visibleLineCount = (size.height / lineHeight).toInt() + 2
        val lastVisibleLine = (firstVisibleLine + visibleLineCount).coerceAtMost(state.document.lineCount - 1)

        // Draw current line highlight
        if (caretPosition.line in firstVisibleLine..lastVisibleLine) {
            val highlightY = (caretPosition.line * lineHeight) - scrollOffset.y
            drawRect(
                color = colors.currentLineHighlight,
                topLeft = Offset(0f, highlightY),
                size = Size(size.width, lineHeight)
            )
        }

        // Draw search highlights
        if (searchResults.isNotEmpty()) {
            drawSearchHighlights(
                searchResults = searchResults,
                currentSearchIndex = currentSearchIndex,
                scrollOffset = scrollOffset,
                lineHeight = lineHeight,
                charWidth = charWidth,
                firstVisibleLine = firstVisibleLine,
                lastVisibleLine = lastVisibleLine,
                matchColor = colors.searchMatchBackground,
                currentMatchColor = colors.currentSearchMatchBackground
            )
        }

        // Draw selection
        selection?.let { sel ->
            drawSelection(
                selection = sel,
                state = state,
                scrollOffset = scrollOffset,
                lineHeight = lineHeight,
                charWidth = charWidth,
                firstVisibleLine = firstVisibleLine,
                lastVisibleLine = lastVisibleLine,
                selectionColor = colors.selectionBackground
            )
        }

        // Draw text lines
        for (line in firstVisibleLine..lastVisibleLine) {
            val y = (line * lineHeight) - scrollOffset.y
            try {
                val lineText = state.document.getLineText(line)
                // Truncate very long lines to prevent rendering issues
                val displayText = if (lineText.length > 1000) {
                    lineText.substring(0, 1000) + "..."
                } else {
                    lineText
                }

                if (displayText.isNotEmpty()) {
                    val textLayoutResult = textMeasurer.measure(displayText, textStyle)
                    drawText(
                        textLayoutResult = textLayoutResult,
                        topLeft = Offset(-scrollOffset.x.toFloat(), y)
                    )
                }
            } catch (e: Exception) {
                println("LargeFileViewer: Failed to render line $line: ${e.message}")
            }
        }

        // Draw caret
        if (caretPosition.line in firstVisibleLine..lastVisibleLine) {
            val caretX = caretPosition.column * charWidth - scrollOffset.x
            val caretY = (caretPosition.line * lineHeight) - scrollOffset.y

            drawRect(
                color = colors.caret,
                topLeft = Offset(caretX, caretY),
                size = Size(2f, lineHeight)
            )
        }
    }
}

private fun DrawScope.drawSelection(
    selection: ai.rever.bosseditor.core.EditorRange,
    state: LargeFileEditorState,
    scrollOffset: ScrollOffset,
    lineHeight: Float,
    charWidth: Float,
    firstVisibleLine: Int,
    lastVisibleLine: Int,
    selectionColor: Color
) {
    val startLine = selection.start.line
    val endLine = selection.end.line

    for (line in maxOf(startLine, firstVisibleLine)..minOf(endLine, lastVisibleLine)) {
        val y = (line * lineHeight) - scrollOffset.y
        val lineLength = state.document.getLineLength(line)

        val startCol = if (line == startLine) selection.start.column else 0
        val endCol = if (line == endLine) selection.end.column else lineLength

        val x = startCol * charWidth - scrollOffset.x
        val width = (endCol - startCol) * charWidth

        if (width > 0) {
            drawRect(
                color = selectionColor,
                topLeft = Offset(x, y),
                size = Size(width, lineHeight)
            )
        }
    }
}

private fun DrawScope.drawSearchHighlights(
    searchResults: List<LargeFileSearchResult>,
    currentSearchIndex: Int,
    scrollOffset: ScrollOffset,
    lineHeight: Float,
    charWidth: Float,
    firstVisibleLine: Int,
    lastVisibleLine: Int,
    matchColor: Color,
    currentMatchColor: Color
) {
    // Filter to only visible results for efficiency
    val visibleResults = searchResults.withIndex().filter { (_, result) ->
        result.range.start.line in firstVisibleLine..lastVisibleLine
    }

    for ((index, result) in visibleResults) {
        val range = result.range
        val line = range.start.line
        val y = (line * lineHeight) - scrollOffset.y

        val startCol = range.start.column
        val endCol = range.end.column

        val x = startCol * charWidth - scrollOffset.x
        val width = (endCol - startCol) * charWidth

        if (width > 0) {
            // Use different color for current result
            val color = if (index == currentSearchIndex) currentMatchColor else matchColor
            drawRect(
                color = color,
                topLeft = Offset(x, y),
                size = Size(width, lineHeight)
            )
        }
    }
}

/**
 * Wrapper composable that automatically detects large files and uses the appropriate viewer.
 */
@Composable
fun LargeFileViewerFromPath(
    filePath: String,
    modifier: Modifier = Modifier,
    theme: EditorTheme = EditorTheme.Dark,
    fontFamily: FontFamily = FontFamily.Monospace,
    fontSize: Float = 14f,
    lineSpacing: Float = 1.2f,
    showLineNumbers: Boolean = true,
    onCaretPositionChanged: (EditorPosition) -> Unit = {},
    onClose: () -> Unit = {}
) {
    val file = remember(filePath) { File(filePath) }

    val state = remember(filePath) {
        LargeFileEditorState(file)
    }

    DisposableEffect(state) {
        onDispose {
            state.close()
        }
    }

    LargeFileViewer(
        state = state,
        modifier = modifier,
        theme = theme,
        fontFamily = fontFamily,
        fontSize = fontSize,
        lineSpacing = lineSpacing,
        showLineNumbers = showLineNumbers,
        onCaretPositionChanged = onCaretPositionChanged
    )
}
