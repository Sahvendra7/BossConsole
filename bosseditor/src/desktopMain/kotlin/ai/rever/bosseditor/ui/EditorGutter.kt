package ai.rever.bosseditor.ui

import ai.rever.bosseditor.fold.FoldRegion
import ai.rever.bosseditor.fold.VisualLineMapper
import ai.rever.bosseditor.theme.LocalEditorTheme
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max

/**
 * Editor gutter composable showing line numbers and fold indicators.
 *
 * Features:
 * - Line numbers with configurable width
 * - Current line highlighting
 * - Fold collapse/expand indicators
 * - Clickable fold regions
 * - Support for visual line mapping (folded regions)
 *
 * ## Usage
 * ```kotlin
 * EditorGutter(
 *     lineCount = document.lineCount,
 *     currentLine = caretLine,
 *     visualLineMapper = mapper,
 *     onFoldToggle = { region -> foldingModel.toggleFold(region) },
 *     modifier = Modifier.width(60.dp)
 * )
 * ```
 */
@Composable
fun EditorGutter(
    lineCount: Int,
    currentLine: Int,
    firstVisibleLine: Int = 0,
    visibleLineCount: Int = 50,
    lineHeight: Float,
    visualLineMapper: VisualLineMapper = VisualLineMapper.noFolds(lineCount),
    onFoldToggle: (FoldRegion) -> Unit = {},
    onLineClick: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val theme = LocalEditorTheme.current
    val colors = theme.colors
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current

    // Calculate gutter width based on line count
    val maxLineDigits = remember(lineCount) {
        max(3, lineCount.toString().length)
    }
    val gutterWidth = remember(maxLineDigits) {
        (maxLineDigits * 9 + 36).dp // ~9dp per digit + left padding + fold icon space + right padding
    }

    val textStyle = remember {
        TextStyle(
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace
        )
    }

    Box(
        modifier = modifier
            .width(gutterWidth)
            .fillMaxHeight()
            .background(colors.gutterBackground)
    ) {
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            val lineHeightPx = lineHeight
            val leftPadding = 8.dp.toPx()           // Space for run gutter icons on left
            val foldIconSize = 12.dp.toPx()
            val foldIconRightPadding = 8.dp.toPx()  // Space after fold icon to right edge
            val foldIconLeftPadding = 4.dp.toPx()   // Space before fold icon
            // Line numbers positioned between left padding and fold icon area
            val lineNumberEndX = size.width - foldIconSize - foldIconLeftPadding - foldIconRightPadding
            val lineNumberStartX = leftPadding      // Minimum X for line numbers

            // Draw visible lines
            val lastVisibleLine = (firstVisibleLine + visibleLineCount).coerceAtMost(
                visualLineMapper.visibleLineCount - 1
            )

            for (visualLine in firstVisibleLine..lastVisibleLine) {
                val documentLine = visualLineMapper.visualToDocument(visualLine)
                if (documentLine < 0) continue

                val yOffset = (visualLine - firstVisibleLine) * lineHeightPx

                // Highlight current line
                if (documentLine == currentLine) {
                    drawRect(
                        color = colors.currentLineHighlight,
                        topLeft = Offset(0f, yOffset),
                        size = Size(size.width, lineHeightPx)
                    )
                }

                // Draw line number (right-aligned)
                val lineNumberText = (documentLine + 1).toString()
                val textColor = if (documentLine == currentLine) {
                    colors.lineNumberActive
                } else {
                    colors.lineNumber
                }

                drawLineNumber(
                    textMeasurer = textMeasurer,
                    lineNumber = lineNumberText,
                    x = lineNumberEndX,
                    y = yOffset,
                    lineHeight = lineHeightPx,
                    textStyle = textStyle.copy(color = textColor)
                )

                // Draw fold indicator if this is a fold start
                val fold = visualLineMapper.getCollapsedFoldAt(visualLine)
                if (fold != null) {
                    // Collapsed fold - draw expand icon (▶)
                    drawFoldIcon(
                        x = lineNumberEndX + foldIconLeftPadding,
                        y = yOffset + (lineHeightPx - foldIconSize) / 2,
                        size = foldIconSize,
                        isCollapsed = true,
                        color = colors.foldIndicator
                    )
                } else {
                    // Check if this line starts an expandable fold region
                    // (This requires access to the full fold list, not just collapsed ones)
                }
            }

            // Draw right border
            drawLine(
                color = colors.gutterBorder,
                start = Offset(size.width - 1, 0f),
                end = Offset(size.width - 1, size.height),
                strokeWidth = 1f
            )
        }
    }
}

/**
 * Draws a line number right-aligned.
 */
private fun DrawScope.drawLineNumber(
    textMeasurer: TextMeasurer,
    lineNumber: String,
    x: Float,
    y: Float,
    lineHeight: Float,
    textStyle: TextStyle
) {
    val layoutResult = textMeasurer.measure(lineNumber, textStyle)
    val textWidth = layoutResult.size.width
    val textHeight = layoutResult.size.height

    // Right-align the text
    val textX = x - textWidth - 8.dp.toPx()
    val textY = y + (lineHeight - textHeight) / 2

    drawText(
        textLayoutResult = layoutResult,
        topLeft = Offset(textX, textY)
    )
}

/**
 * Draws a fold collapse/expand icon.
 */
private fun DrawScope.drawFoldIcon(
    x: Float,
    y: Float,
    size: Float,
    isCollapsed: Boolean,
    color: Color
) {
    val path = Path()

    if (isCollapsed) {
        // Draw right-pointing triangle (▶)
        path.moveTo(x, y)
        path.lineTo(x + size, y + size / 2)
        path.lineTo(x, y + size)
        path.close()
    } else {
        // Draw down-pointing triangle (▼)
        path.moveTo(x, y)
        path.lineTo(x + size, y)
        path.lineTo(x + size / 2, y + size)
        path.close()
    }

    drawPath(
        path = path,
        color = color,
        style = Fill
    )
}

/**
 * Configuration for gutter appearance.
 */
data class GutterConfig(
    val showLineNumbers: Boolean = true,
    val showFoldIndicators: Boolean = true,
    val minWidth: Dp = 40.dp,
    val lineNumberPadding: Dp = 8.dp,
    val foldIconSize: Dp = 12.dp
)

/**
 * A separate fold gutter column that can be placed beside the main gutter.
 * Shows fold indicators for expanded folds.
 */
@Composable
fun FoldGutter(
    foldRegions: List<FoldRegion>,
    firstVisibleLine: Int,
    visibleLineCount: Int,
    lineHeight: Float,
    onFoldToggle: (FoldRegion) -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalEditorTheme.current
    val colors = theme.colors

    Canvas(
        modifier = modifier
            .width(16.dp)
            .fillMaxHeight()
            .background(colors.gutterBackground)
    ) {
        val foldIconSize = 10.dp.toPx()
        val centerX = size.width / 2

        // Draw fold indicators for visible lines
        for (fold in foldRegions) {
            if (fold.startLine >= firstVisibleLine &&
                fold.startLine < firstVisibleLine + visibleLineCount
            ) {
                val visualLine = fold.startLine - firstVisibleLine
                val y = visualLine * lineHeight + (lineHeight - foldIconSize) / 2

                drawFoldIcon(
                    x = centerX - foldIconSize / 2,
                    y = y,
                    size = foldIconSize,
                    isCollapsed = fold.isCollapsed,
                    color = colors.foldIndicator
                )
            }
        }
    }
}

/**
 * Breakpoint gutter for debugging support.
 */
@Composable
fun BreakpointGutter(
    breakpointLines: Set<Int>,
    firstVisibleLine: Int,
    visibleLineCount: Int,
    lineHeight: Float,
    onBreakpointToggle: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalEditorTheme.current
    val colors = theme.colors

    Canvas(
        modifier = modifier
            .width(16.dp)
            .fillMaxHeight()
            .background(colors.gutterBackground)
    ) {
        val breakpointRadius = 5.dp.toPx()
        val centerX = size.width / 2

        // Draw breakpoint indicators
        for (line in breakpointLines) {
            if (line >= firstVisibleLine && line < firstVisibleLine + visibleLineCount) {
                val visualLine = line - firstVisibleLine
                val y = visualLine * lineHeight + lineHeight / 2

                // Draw red circle for breakpoint
                drawCircle(
                    color = colors.error,
                    radius = breakpointRadius,
                    center = Offset(centerX, y)
                )
            }
        }
    }
}

/**
 * Combined gutter with all features.
 */
@Composable
fun FullEditorGutter(
    lineCount: Int,
    currentLine: Int,
    firstVisibleLine: Int,
    visibleLineCount: Int,
    lineHeight: Float,
    visualLineMapper: VisualLineMapper,
    foldRegions: List<FoldRegion>,
    breakpointLines: Set<Int> = emptySet(),
    showBreakpoints: Boolean = false,
    onFoldToggle: (FoldRegion) -> Unit = {},
    onLineClick: (Int) -> Unit = {},
    onBreakpointToggle: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier) {
        // Breakpoint gutter (optional)
        if (showBreakpoints) {
            BreakpointGutter(
                breakpointLines = breakpointLines,
                firstVisibleLine = firstVisibleLine,
                visibleLineCount = visibleLineCount,
                lineHeight = lineHeight,
                onBreakpointToggle = onBreakpointToggle
            )
        }

        // Main gutter with line numbers and fold icons
        EditorGutter(
            lineCount = lineCount,
            currentLine = currentLine,
            firstVisibleLine = firstVisibleLine,
            visibleLineCount = visibleLineCount,
            lineHeight = lineHeight,
            visualLineMapper = visualLineMapper,
            onFoldToggle = onFoldToggle,
            onLineClick = onLineClick
        )
    }
}
