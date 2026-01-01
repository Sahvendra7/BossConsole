package ai.rever.bosseditor.rendering

import ai.rever.bosseditor.core.EditorPosition
import ai.rever.bosseditor.core.EditorRange
import ai.rever.bosseditor.core.EditorState
import ai.rever.bosseditor.core.OffsetRange
import ai.rever.bosseditor.features.BracketMatch
import ai.rever.bosseditor.features.BracketMatcher
import ai.rever.bosseditor.features.IndentGuide
import ai.rever.bosseditor.features.IndentGuides
import ai.rever.bosseditor.features.MarkOccurrences
import ai.rever.bosseditor.features.RainbowBracket
import ai.rever.bosseditor.features.RainbowBrackets
import ai.rever.bosseditor.theme.LocalEditorTheme
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
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
    isActiveEditor: Boolean = true,
    fontFamily: FontFamily = FontFamily.Monospace,
    fontSize: Float = 14f,
    lineSpacing: Float = 1.2f,
    showLineNumbers: Boolean = true,
    highlightCurrentLine: Boolean = true,
    searchQuery: String? = null,
    searchMatches: List<EditorRange> = emptyList(),
    currentSearchMatchIndex: Int = -1,
    rainbowBracketsEnabled: Boolean = true,
    indentGuidesEnabled: Boolean = true,
    tabSize: Int = 4,
    scrollSpeed: Float = 1.5f,
    foldingEnabled: Boolean = true,
    getLineTokens: (Int) -> List<EditorToken> = { emptyList() },
    onCaretPositionChanged: (EditorPosition) -> Unit = {},
    onSelectionChanged: (EditorRange?) -> Unit = {},
    onNavigationRequest: ((EditorPosition) -> Unit)? = null,
    onFoldToggle: ((Int) -> Unit)? = null // Document line number
) {
    val theme = LocalEditorTheme.current
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()

    // Focus management - use MutableInteractionSource for robust focus tracking
    val focusRequester = remember { FocusRequester() }
    val interactionSource = remember { MutableInteractionSource() }
    val composeFocused by interactionSource.collectIsFocusedAsState()

    // Effective focus = active editor AND Compose focused
    val isFocused = isActiveEditor && composeFocused

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

    // Calculate gutter width based on line count (includes fold indicator space when enabled)
    val gutterWidth = remember(editorState.document.lineCount, fontFamily, fontSize, showLineNumbers, foldingEnabled) {
        if (showLineNumbers) {
            val baseWidth = calculateGutterWidth(textMeasurer, editorState.document.lineCount, fontFamily, fontSize)
            // Add space for fold indicator: 16 (size) + 8 (paddingRight) + 8 (paddingLeft) = 32px
            val foldIndicatorSpace = if (foldingEnabled) 32f else 0f
            baseWidth + foldIndicatorSpace
        } else {
            0f
        }
    }

    // Collect state from EditorState
    val caretPosition by editorState.caretPosition.collectAsState()
    val selection by editorState.selection.collectAsState()
    val scrollOffset by editorState.scrollOffset.collectAsState()
    val allCarets by editorState.multiCaretModel.carets.collectAsState()
    val visualLineMapper by editorState.visualLineMapper.collectAsState()
    val foldingVersion by editorState.foldingVersion.collectAsState()

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

    // Create indent guides helper (reuse across recompositions)
    val indentGuidesHelper = remember(editorState.document, tabSize) {
        IndentGuides(editorState.document, tabSize)
    }

    // Compute indent guides (only when enabled)
    val indentGuides: List<IndentGuide> = remember(indentGuidesEnabled, editorState.document.documentVersion, tabSize) {
        if (indentGuidesEnabled) {
            indentGuidesHelper.calculateGuides()
        } else {
            emptyList()
        }
    }

    // Compute the active indent guide (guide containing the caret)
    val activeIndentGuide: IndentGuide? = remember(indentGuidesEnabled, caretPosition, indentGuides) {
        if (indentGuidesEnabled && indentGuides.isNotEmpty()) {
            indentGuidesHelper.getGuideAtCaret(caretPosition.line, caretPosition.column)
        } else {
            null
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

    // Calculate longest line length for horizontal scrollbar
    // Recalculate when document changes
    val maxLineLength = remember(editorState.document.documentVersion) {
        var maxLen = 0
        for (i in 0 until editorState.document.lineCount) {
            val len = editorState.document.getLineLength(i)
            if (len > maxLen) maxLen = len
        }
        maxLen
    }

    // Update visible line range in EditorState (for minimap sync and scrollbars)
    // Uses same calculation as EditorRenderingContext.from() for consistency
    // Passes actual measured lineHeight so minimap click scroll uses correct values
    LaunchedEffect(scrollOffset, viewportSize, visualLineMapper, lineHeight, charWidth, maxLineLength, gutterWidth) {
        if (viewportSize.height > 0 && lineHeight > 0) {
            val firstVisibleVisualLine = (scrollOffset.y.toFloat() / lineHeight).toInt().coerceAtLeast(0)
            val visibleLineCount = (viewportSize.height / lineHeight).toInt() + 2 // +2 for partial lines
            // Content width = longest line chars * charWidth + some padding
            val contentWidth = (maxLineLength * charWidth) + charWidth * 2 // +2 chars padding
            // Viewport width excludes gutter
            val effectiveViewportWidth = viewportSize.width - gutterWidth
            editorState.updateVisibleLineRange(
                firstLine = firstVisibleVisualLine,
                lineCount = visibleLineCount,
                lineHeight = lineHeight,
                viewportHeight = viewportSize.height,
                viewportWidth = effectiveViewportWidth,
                contentWidth = contentWidth,
                charWidth = charWidth
            )
        }
    }

    // Request focus when this editor becomes active (like BossTerm's isActiveTab)
    LaunchedEffect(isActiveEditor) {
        if (isActiveEditor) {
            kotlinx.coroutines.delay(50) // Allow UI to settle
            focusRequester.requestFocus()
        }
    }

    // Caret blink timer - restarts when focus changes (proper dependency)
    LaunchedEffect(isFocused) {
        caretBlinkVisible = true
        if (isFocused) {
            while (true) {
                kotlinx.coroutines.delay(530) // Standard cursor blink rate
                caretBlinkVisible = !caretBlinkVisible
            }
        }
    }

    // Reset caret to visible immediately when position changes
    LaunchedEffect(caretPosition) {
        caretBlinkVisible = true
    }

    // Helper to convert offset to position (uses visual line mapping for folding)
    fun offsetToEditorPosition(offset: Offset): EditorPosition {
        // Calculate visual line from screen position
        val visualLine = ((offset.y + scrollOffset.y.toFloat()) / lineHeight)
            .toInt()
            .coerceIn(0, (visualLineMapper.visibleLineCount - 1).coerceAtLeast(0))

        // Convert visual line to document line
        val documentLine = visualLineMapper.visualToDocument(visualLine)
            .coerceIn(0, (editorState.document.lineCount - 1).coerceAtLeast(0))

        // Check if this visual line has a collapsed fold
        val collapsedFold = visualLineMapper.getCollapsedFoldAt(visualLine)

        // Calculate column based on document line length
        val maxColumn = if (editorState.document.lineCount > 0) {
            // For imports/doc comments folds, the line content is hidden (only placeholder shown)
            // So limit column to 0 to place cursor at start of line
            if (collapsedFold != null &&
                (collapsedFold.type == ai.rever.bosseditor.fold.FoldType.IMPORTS ||
                 collapsedFold.type == ai.rever.bosseditor.fold.FoldType.DOC_COMMENT)) {
                0
            } else if (collapsedFold != null && collapsedFold.type == ai.rever.bosseditor.fold.FoldType.CODE) {
                // For code folds, limit to the visible part (before the '{')
                val lineText = editorState.document.getLineText(documentLine)
                val trimmedEnd = lineText.trimEnd()
                if (trimmedEnd.endsWith("{")) {
                    trimmedEnd.dropLast(1).trimEnd().length
                } else {
                    editorState.document.getLineLength(documentLine)
                }
            } else {
                editorState.document.getLineLength(documentLine)
            }
        } else 0

        val column = ((offset.x - gutterWidth + scrollOffset.x.toFloat()) / charWidth)
            .toInt()
            .coerceIn(0, maxColumn)

        return EditorPosition(documentLine, column)
    }

    Box(
        modifier = modifier
            .background(theme.colors.background)
            .onSizeChanged { size ->
                viewportSize = size.toSize()
            }
            .focusRequester(focusRequester)
            .focusable(interactionSource = interactionSource)
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

                    // Check for fold indicator click (in the gutter area, right side)
                    val isFoldIndicatorClick = foldingEnabled &&
                        showLineNumbers &&
                        position.x >= gutterWidth - 20f &&
                        position.x < gutterWidth

                    when (clickCount) {
                        1 -> {
                            // Check for fold indicator click first
                            if (isFoldIndicatorClick && onFoldToggle != null) {
                                // Calculate which visual line was clicked
                                val visualLine = ((position.y + scrollOffset.y.toFloat()) / lineHeight).toInt()
                                val documentLine = visualLineMapper.visualToDocument(visualLine)
                                if (documentLine >= 0 && editorState.isFoldStart(documentLine)) {
                                    onFoldToggle.invoke(documentLine)
                                    isDragging = false
                                    // Consume the event - don't continue to other handlers
                                    return@awaitEachGesture
                                }
                            }

                            // Check for fold placeholder click (in the text area)
                            if (foldingEnabled && onFoldToggle != null && position.x >= gutterWidth) {
                                val visualLine = ((position.y + scrollOffset.y.toFloat()) / lineHeight).toInt()
                                val collapsedFold = visualLineMapper.getCollapsedFoldAt(visualLine)
                                if (collapsedFold != null) {
                                    // Calculate placeholder X position
                                    val lineText = editorState.document.getLineText(collapsedFold.startLine)
                                    val foldType = collapsedFold.type

                                    // For imports/doc comments, placeholder starts near gutter
                                    // For code, placeholder starts after line text (minus trailing brace)
                                    val placeholderStartX = if (foldType == ai.rever.bosseditor.fold.FoldType.IMPORTS ||
                                        foldType == ai.rever.bosseditor.fold.FoldType.DOC_COMMENT) {
                                        gutterWidth + charWidth * 1.5f
                                    } else {
                                        // Strip trailing '{' for code folds
                                        val trimmedEnd = lineText.trimEnd()
                                        val effectiveLength = if (trimmedEnd.endsWith("{")) {
                                            trimmedEnd.dropLast(1).trimEnd().length
                                        } else {
                                            lineText.length
                                        }
                                        gutterWidth + effectiveLength * charWidth - scrollOffset.x.toFloat() + charWidth * 0.5f
                                    }

                                    // Check if click is on or after placeholder start
                                    val clickX = position.x
                                    if (clickX >= placeholderStartX) {
                                        onFoldToggle.invoke(collapsedFold.startLine)
                                        isDragging = false
                                        return@awaitEachGesture
                                    }
                                }
                            }

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

                // Calculate max vertical scroll based on content (in pixels)
                val contentHeight = editorState.document.lineCount * lineHeight
                val maxScrollY = (contentHeight - viewportSize.height).coerceAtLeast(0f).toInt()

                // Calculate max horizontal scroll based on longest line
                val contentWidth = maxLineLength * charWidth + charWidth * 2 // +2 chars padding
                val effectiveViewportWidth = viewportSize.width - gutterWidth
                val maxScrollX = (contentWidth - effectiveViewportWidth).coerceAtLeast(0f).toInt()

                // delta.y is typically -1 or 1 per scroll tick
                // Scroll by scrollSpeed lines * line height (pixels)
                val scrollAmountY = (delta.y * scrollSpeed * lineHeight).toInt()
                val newScrollY = (scrollOffset.y + scrollAmountY).coerceIn(0, maxScrollY)

                // Horizontal scroll (trackpad horizontal swipe or shift+scroll)
                val scrollAmountX = (delta.x * scrollSpeed * charWidth).toInt()
                val newScrollX = (scrollOffset.x + scrollAmountX).coerceIn(0, maxScrollX)

                editorState.setScrollOffset(
                    ai.rever.bosseditor.core.ScrollOffset(
                        x = newScrollX,
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
                caretVisible = true, // Always show caret (blink only when focused)
                caretBlinkVisible = caretBlinkVisible,
                isFocused = isFocused,
                highlightCurrentLine = highlightCurrentLine,
                searchQuery = searchQuery,
                searchMatches = searchMatches,
                currentSearchMatchIndex = currentSearchMatchIndex,
                showLineNumbers = showLineNumbers,
                gutterWidth = gutterWidth,
                visualLineMapper = visualLineMapper,
                allFoldRegions = editorState.getAllFoldRegions(),
                foldingEnabled = foldingEnabled,
                getLineTokens = getLineTokens,
                bracketMatch = bracketMatch,
                markOccurrences = markOccurrences,
                allCarets = allCarets,
                rainbowBrackets = rainbowBrackets,
                rainbowBracketsEnabled = rainbowBracketsEnabled,
                indentGuides = indentGuides,
                activeIndentGuide = activeIndentGuide,
                indentGuidesEnabled = indentGuidesEnabled,
                tabSize = tabSize
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

    // Measure 100 characters and average to prevent cumulative rounding errors on long lines
    // This matches BossTerm's approach for accurate click-to-column mapping
    val sampleString = "W".repeat(100)
    val measurement = textMeasurer.measure(sampleString, style)

    val charWidth = measurement.size.width.toFloat() / sampleString.length
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
