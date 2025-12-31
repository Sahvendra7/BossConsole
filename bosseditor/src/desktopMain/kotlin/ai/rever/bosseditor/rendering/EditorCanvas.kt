package ai.rever.bosseditor.rendering

import ai.rever.bosseditor.core.EditorPosition
import ai.rever.bosseditor.core.EditorRange
import ai.rever.bosseditor.core.EditorState
import ai.rever.bosseditor.core.OffsetRange
import ai.rever.bosseditor.features.BracketMatch
import ai.rever.bosseditor.features.BracketMatcher
import ai.rever.bosseditor.features.MarkOccurrences
import ai.rever.bosseditor.features.RainbowBracket
import ai.rever.bosseditor.features.RainbowBrackets
import ai.rever.bosseditor.theme.LocalEditorTheme
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp

/**
 * Canvas composable for rendering editor content.
 *
 * This is the core visual component that:
 * - Measures text dimensions using TextMeasurer
 * - Creates rendering contexts for each frame
 * - Delegates rendering to EditorCanvasRenderer
 * - Handles focus and mouse input (click, double-click, triple-click, drag)
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun EditorCanvas(
    editorState: EditorState,
    modifier: Modifier = Modifier,
    fontFamily: FontFamily = FontFamily.Monospace,
    fontSize: Float = 14f,
    lineSpacing: Float = 1.2f,
    showLineNumbers: Boolean = true,
    highlightCurrentLine: Boolean = true,
    searchQuery: String? = null,
    searchMatches: List<EditorRange> = emptyList(),
    currentSearchMatchIndex: Int = -1,
    rainbowBracketsEnabled: Boolean = true,
    getLineTokens: (Int) -> List<EditorToken> = { emptyList() },
    onCaretPositionChanged: (EditorPosition) -> Unit = {},
    onSelectionChanged: (EditorRange?) -> Unit = {},
    onNavigationRequest: ((EditorPosition) -> Unit)? = null
) {
    val theme = LocalEditorTheme.current
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()

    // Focus management
    val focusRequester = remember { FocusRequester() }
    var isFocused by remember { mutableStateOf(false) }

    // Caret blink state
    var caretBlinkVisible by remember { mutableStateOf(true) }

    // Mouse state for drag selection
    var isDragging by remember { mutableStateOf(false) }
    var dragStartPosition by remember { mutableStateOf<EditorPosition?>(null) }

    // Click tracking for double/triple click
    var lastClickTime by remember { mutableStateOf(0L) }
    var lastClickPosition by remember { mutableStateOf<Offset?>(null) }
    var clickCount by remember { mutableStateOf(0) }

    // Viewport size for scroll calculations
    var viewportSize by remember { mutableStateOf(Size.Zero) }

    // Track Cmd/Ctrl modifier key state for navigation
    var isNavigationModifierHeld by remember { mutableStateOf(false) }

    // Measure character dimensions using a monospace reference character
    val (charWidth, lineHeight, baselineOffset) = remember(fontFamily, fontSize, lineSpacing, density) {
        measureCharacterDimensions(textMeasurer, fontFamily, fontSize, lineSpacing)
    }

    // Calculate gutter width based on line count
    val gutterWidth = remember(editorState.document.lineCount, fontFamily, fontSize, showLineNumbers) {
        if (showLineNumbers) {
            calculateGutterWidth(textMeasurer, editorState.document.lineCount, fontFamily, fontSize)
        } else {
            0f
        }
    }

    // Collect state from EditorState
    val caretPosition by editorState.caretPosition.collectAsState()
    val selection by editorState.selection.collectAsState()
    val scrollOffset by editorState.scrollOffset.collectAsState()
    val allCarets by editorState.multiCaretModel.carets.collectAsState()

    // Create bracket matcher and mark occurrences (reuse across recompositions)
    val bracketMatcher = remember(editorState.document) {
        BracketMatcher(editorState.document)
    }
    val markOccurrencesHelper = remember(editorState.document) {
        MarkOccurrences(editorState.document)
    }

    // Compute bracket match based on caret position
    val bracketMatch: BracketMatch? = remember(caretPosition, editorState.document.documentVersion) {
        val caretOffset = editorState.document.positionToOffset(caretPosition)
        bracketMatcher.findMatchingBracket(caretOffset)
    }

    // Compute mark occurrences based on caret position (only when no selection)
    val markOccurrences: List<OffsetRange> = remember(caretPosition, selection, editorState.document.documentVersion) {
        if (selection == null || selection!!.isEmpty) {
            val caretOffset = editorState.document.positionToOffset(caretPosition)
            markOccurrencesHelper.findOccurrences(caretOffset)
        } else {
            emptyList()
        }
    }

    // Create rainbow brackets helper (reuse across recompositions)
    val rainbowBracketsHelper = remember(editorState.document) {
        RainbowBrackets(editorState.document)
    }

    // Compute rainbow brackets (only when enabled)
    val rainbowBrackets: List<RainbowBracket> = remember(rainbowBracketsEnabled, editorState.document.documentVersion) {
        if (rainbowBracketsEnabled) {
            rainbowBracketsHelper.getRainbowBrackets()
        } else {
            emptyList()
        }
    }

    // Track caret position changes
    LaunchedEffect(caretPosition) {
        onCaretPositionChanged(caretPosition)
    }

    // Track selection changes
    LaunchedEffect(selection) {
        onSelectionChanged(selection)
    }

    // Caret blink timer - always run since caret is always visible
    // Reset to visible when caret position changes (so cursor shows immediately after moving)
    LaunchedEffect(caretPosition) {
        caretBlinkVisible = true
        while (true) {
            kotlinx.coroutines.delay(530) // Standard cursor blink rate
            caretBlinkVisible = !caretBlinkVisible
        }
    }

    // Helper to convert offset to position
    fun offsetToEditorPosition(offset: Offset): EditorPosition {
        return offsetToPosition(
            offset = offset,
            charWidth = charWidth,
            lineHeight = lineHeight,
            gutterWidth = gutterWidth,
            scrollOffsetX = scrollOffset.x.toFloat(),
            scrollOffsetY = scrollOffset.y.toFloat(),
            lineCount = editorState.document.lineCount,
            getLineLength = { editorState.document.getLineLength(it) }
        )
    }

    Box(
        modifier = modifier
            .background(theme.colors.background)
            .onSizeChanged { size ->
                viewportSize = size.toSize()
            }
            .focusRequester(focusRequester)
            .focusable()
            .onFocusChanged { focusState ->
                isFocused = focusState.isFocused
            }
            .onPreviewKeyEvent { keyEvent ->
                // Track Cmd/Ctrl modifier key state for navigation
                val isMac = System.getProperty("os.name").lowercase().contains("mac")
                val isNavigationKey = if (isMac) {
                    keyEvent.key == Key.MetaLeft || keyEvent.key == Key.MetaRight
                } else {
                    keyEvent.key == Key.CtrlLeft || keyEvent.key == Key.CtrlRight
                }
                if (isNavigationKey) {
                    isNavigationModifierHeld = keyEvent.type == KeyEventType.KeyDown
                }
                false // Don't consume the event
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    // Use awaitPointerEvent first to get access to keyboard modifiers
                    val downEvent = awaitPointerEvent()
                    val down = downEvent.changes.firstOrNull() ?: return@awaitEachGesture
                    if (!down.pressed) return@awaitEachGesture

                    focusRequester.requestFocus()

                    val currentTime = System.currentTimeMillis()
                    val position = down.position

                    // Check keyboard modifiers from the pointer event
                    val isMac = System.getProperty("os.name").lowercase().contains("mac")
                    val isNavigationModifier = if (isMac) {
                        downEvent.keyboardModifiers.isMetaPressed
                    } else {
                        downEvent.keyboardModifiers.isCtrlPressed
                    }

                    // Detect multi-clicks (double, triple)
                    val isMultiClick = lastClickPosition?.let { lastPos ->
                        val timeDiff = currentTime - lastClickTime
                        val distance = (position - lastPos).getDistance()
                        timeDiff < MULTI_CLICK_TIMEOUT && distance < MULTI_CLICK_DISTANCE
                    } ?: false

                    if (isMultiClick) {
                        clickCount = (clickCount % 3) + 1
                    } else {
                        clickCount = 1
                    }

                    lastClickTime = currentTime
                    lastClickPosition = position

                    val editorPosition = offsetToEditorPosition(position)

                    when (clickCount) {
                        1 -> {
                            // Check for Cmd/Ctrl+Click navigation
                            if (isNavigationModifier && onNavigationRequest != null) {
                                // Navigation click - don't position caret, invoke callback
                                onNavigationRequest.invoke(editorPosition)
                                isDragging = false
                            } else {
                                // Single click - position caret
                                val isShift = down.isShiftPressed()
                                if (isShift) {
                                    // Extend selection
                                    extendSelectionTo(editorState, editorPosition)
                                } else {
                                    editorState.moveCaret(editorPosition)
                                    editorState.clearSelection()
                                }
                                // Start drag
                                isDragging = true
                                dragStartPosition = editorPosition
                            }
                        }
                        2 -> {
                            // Double click - select word
                            editorState.selectWord()
                            isDragging = false
                        }
                        3 -> {
                            // Triple click - select line
                            editorState.selectLine()
                            isDragging = false
                        }
                    }

                    // Handle drag and release
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break

                        when (event.type) {
                            PointerEventType.Move -> {
                                if (isDragging && dragStartPosition != null) {
                                    val currentPos = offsetToEditorPosition(change.position)
                                    val startPos = dragStartPosition!!

                                    // Create selection from drag start to current position
                                    val range = if (startPos <= currentPos) {
                                        EditorRange(startPos, currentPos)
                                    } else {
                                        EditorRange(currentPos, startPos)
                                    }

                                    editorState.setSelection(range)
                                    editorState.moveCaret(currentPos, extendSelection = true)
                                }
                            }
                            PointerEventType.Release -> {
                                if (change.changedToUp()) {
                                    isDragging = false
                                    dragStartPosition = null
                                    break
                                }
                            }
                        }

                        if (change.positionChange() != Offset.Zero) {
                            change.consume()
                        }
                    }
                }
            }
            .onPointerEvent(PointerEventType.Scroll) { event ->
                val change = event.changes.first()
                if (change.isConsumed) return@onPointerEvent

                val delta = change.scrollDelta
                val scrollLines = 3 // Lines to scroll per wheel tick

                // Calculate max scroll based on content (in pixels)
                val contentHeight = editorState.document.lineCount * lineHeight
                val maxScrollY = (contentHeight - viewportSize.height).coerceAtLeast(0f).toInt()

                // delta.y is typically -1 or 1 per scroll tick
                // Scroll by number of lines * line height (pixels)
                val scrollAmount = (delta.y * scrollLines * lineHeight).toInt()
                val newScrollY = (scrollOffset.y + scrollAmount).coerceIn(0, maxScrollY)

                editorState.setScrollOffset(
                    ai.rever.bosseditor.core.ScrollOffset(
                        x = scrollOffset.x,
                        y = newScrollY
                    )
                )
            }
    ) {
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            // Create rendering context
            val context = EditorRenderingContext.from(
                document = editorState.document,
                caretPosition = caretPosition,
                selection = selection,
                charWidth = charWidth,
                lineHeight = lineHeight,
                fontSize = fontSize,
                baselineOffset = baselineOffset,
                viewportWidth = size.width,
                viewportHeight = size.height,
                scrollOffsetX = scrollOffset.x.toFloat(),
                scrollOffsetY = scrollOffset.y.toFloat(),
                textMeasurer = textMeasurer,
                fontFamily = fontFamily,
                colors = theme.colors,
                caretVisible = true, // Always show caret - focus handled at BossEditor level
                caretBlinkVisible = caretBlinkVisible,
                highlightCurrentLine = highlightCurrentLine,
                searchQuery = searchQuery,
                searchMatches = searchMatches,
                currentSearchMatchIndex = currentSearchMatchIndex,
                showLineNumbers = showLineNumbers,
                gutterWidth = gutterWidth,
                getLineTokens = getLineTokens,
                bracketMatch = bracketMatch,
                markOccurrences = markOccurrences,
                allCarets = allCarets,
                rainbowBrackets = rainbowBrackets,
                rainbowBracketsEnabled = rainbowBracketsEnabled
            )

            // Clip to canvas bounds and render
            clipRect {
                with(EditorCanvasRenderer) {
                    renderEditor(context)
                }
            }
        }
    }
}

// Constants for multi-click detection
private const val MULTI_CLICK_TIMEOUT = 400L // ms
private const val MULTI_CLICK_DISTANCE = 10f // pixels

/**
 * Extension to check if shift is pressed during pointer event.
 */
private fun androidx.compose.ui.input.pointer.PointerInputChange.isShiftPressed(): Boolean {
    // Note: In Compose, modifier key detection during pointer events requires
    // accessing keyboard modifiers which isn't directly available here.
    // For now, we'll handle shift+click at a higher level or through key events.
    // This is a limitation that can be addressed with AWT event inspection.
    return false
}

/**
 * Extends the selection from the current anchor to the new position.
 */
private fun extendSelectionTo(editorState: EditorState, position: EditorPosition) {
    val currentSel = editorState.selection.value
    val caretPos = editorState.caretPosition.value

    val anchor = currentSel?.let {
        if (caretPos == it.start) it.end else it.start
    } ?: caretPos

    val newRange = if (anchor <= position) {
        EditorRange(anchor, position)
    } else {
        EditorRange(position, anchor)
    }

    editorState.setSelection(newRange)
    editorState.moveCaret(position, extendSelection = true)
}

/**
 * Measures character dimensions for a monospace font.
 * Returns (charWidth, lineHeight, baselineOffset).
 *
 * @param lineSpacing Line height multiplier (1.0 = tight, 1.2 = comfortable, 1.5 = spacious)
 */
private fun measureCharacterDimensions(
    textMeasurer: TextMeasurer,
    fontFamily: FontFamily,
    fontSize: Float,
    lineSpacing: Float = 1.2f
): Triple<Float, Float, Float> {
    val style = TextStyle(
        fontFamily = fontFamily,
        fontSize = fontSize.sp
    )

    // Use 'M' as reference character (widest in most fonts)
    val measurement = textMeasurer.measure("M", style)

    val charWidth = measurement.size.width.toFloat()
    // Apply line spacing multiplier to the natural line height
    val naturalLineHeight = measurement.size.height.toFloat()
    val lineHeight = naturalLineHeight * lineSpacing
    val baselineOffset = measurement.firstBaseline

    return Triple(charWidth, lineHeight, baselineOffset)
}

/**
 * Calculates gutter width based on the number of digits needed.
 */
private fun calculateGutterWidth(
    textMeasurer: TextMeasurer,
    lineCount: Int,
    fontFamily: FontFamily,
    fontSize: Float
): Float {
    val style = TextStyle(
        fontFamily = fontFamily,
        fontSize = fontSize.sp
    )

    // Calculate digits needed (minimum 2 digits)
    val digits = maxOf(2, lineCount.toString().length)

    // Measure the widest digit (9 or 8 depending on font)
    val digitWidth = textMeasurer.measure("9", style).size.width.toFloat()

    // Gutter width: digits + padding (8px left + 8px right + 8px margin)
    return digitWidth * digits + 24f
}

/**
 * Converts a canvas offset to an editor position.
 */
private fun offsetToPosition(
    offset: Offset,
    charWidth: Float,
    lineHeight: Float,
    gutterWidth: Float,
    scrollOffsetX: Float,
    scrollOffsetY: Float,
    lineCount: Int,
    getLineLength: (Int) -> Int
): EditorPosition {
    // Calculate line number
    val line = ((offset.y + scrollOffsetY) / lineHeight)
        .toInt()
        .coerceIn(0, (lineCount - 1).coerceAtLeast(0))

    // Calculate column
    val maxColumn = if (lineCount > 0) getLineLength(line) else 0
    val column = ((offset.x - gutterWidth + scrollOffsetX) / charWidth)
        .toInt()
        .coerceIn(0, maxColumn)

    return EditorPosition(line, column)
}
