package ai.rever.bosseditor.rendering

import ai.rever.bosseditor.core.EditorPosition
import ai.rever.bosseditor.core.EditorRange
import ai.rever.bosseditor.core.EditorState
import ai.rever.bosseditor.theme.EditorColors
import ai.rever.bosseditor.theme.EditorTheme
import ai.rever.bosseditor.theme.LocalEditorTheme
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
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
 * - Handles focus and basic mouse input
 */
@Composable
fun EditorCanvas(
    editorState: EditorState,
    modifier: Modifier = Modifier,
    fontFamily: FontFamily = FontFamily.Monospace,
    fontSize: Float = 14f,
    showLineNumbers: Boolean = true,
    highlightCurrentLine: Boolean = true,
    searchQuery: String? = null,
    searchMatches: List<EditorRange> = emptyList(),
    currentSearchMatchIndex: Int = -1,
    getLineTokens: (Int) -> List<EditorToken> = { emptyList() },
    onCaretPositionChanged: (EditorPosition) -> Unit = {},
    onSelectionChanged: (EditorRange?) -> Unit = {}
) {
    val theme = LocalEditorTheme.current
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()

    // Focus management
    val focusRequester = remember { FocusRequester() }
    var isFocused by remember { mutableStateOf(false) }

    // Caret blink state
    var caretBlinkVisible by remember { mutableStateOf(true) }

    // Measure character dimensions using a monospace reference character
    val (charWidth, lineHeight, baselineOffset) = remember(fontFamily, fontSize, density) {
        measureCharacterDimensions(textMeasurer, fontFamily, fontSize)
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

    // Track caret position changes
    LaunchedEffect(caretPosition) {
        onCaretPositionChanged(caretPosition)
    }

    // Track selection changes
    LaunchedEffect(selection) {
        onSelectionChanged(selection)
    }

    // Caret blink timer
    LaunchedEffect(isFocused, caretPosition) {
        if (isFocused) {
            caretBlinkVisible = true
            while (true) {
                kotlinx.coroutines.delay(530) // Standard cursor blink rate
                caretBlinkVisible = !caretBlinkVisible
            }
        } else {
            caretBlinkVisible = false
        }
    }

    Box(
        modifier = modifier
            .background(theme.colors.background)
            .focusRequester(focusRequester)
            .focusable()
            .onFocusChanged { focusState ->
                isFocused = focusState.isFocused
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { offset ->
                        // Request focus on tap
                        focusRequester.requestFocus()

                        // Calculate position from tap
                        val position = offsetToPosition(
                            offset = offset,
                            charWidth = charWidth,
                            lineHeight = lineHeight,
                            gutterWidth = gutterWidth,
                            scrollOffsetX = scrollOffset.x.toFloat(),
                            scrollOffsetY = scrollOffset.y.toFloat(),
                            lineCount = editorState.document.lineCount,
                            getLineLength = { editorState.document.getLineLength(it) }
                        )
                        editorState.moveCaret(position)
                    },
                    onDoubleTap = { offset ->
                        // Select word on double tap
                        focusRequester.requestFocus()
                        editorState.selectWord()
                    }
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
                caretVisible = isFocused,
                caretBlinkVisible = caretBlinkVisible,
                highlightCurrentLine = highlightCurrentLine,
                searchQuery = searchQuery,
                searchMatches = searchMatches,
                currentSearchMatchIndex = currentSearchMatchIndex,
                showLineNumbers = showLineNumbers,
                gutterWidth = gutterWidth,
                getLineTokens = getLineTokens
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

/**
 * Measures character dimensions for a monospace font.
 * Returns (charWidth, lineHeight, baselineOffset).
 */
private fun measureCharacterDimensions(
    textMeasurer: TextMeasurer,
    fontFamily: FontFamily,
    fontSize: Float
): Triple<Float, Float, Float> {
    val style = TextStyle(
        fontFamily = fontFamily,
        fontSize = fontSize.sp
    )

    // Use 'M' as reference character (widest in most fonts)
    val measurement = textMeasurer.measure("M", style)

    val charWidth = measurement.size.width.toFloat()
    val lineHeight = measurement.size.height.toFloat()
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
    offset: androidx.compose.ui.geometry.Offset,
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
