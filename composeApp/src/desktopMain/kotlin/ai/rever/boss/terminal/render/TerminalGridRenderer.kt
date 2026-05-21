package ai.rever.boss.terminal.render

import ai.rever.boss.ipc.proto.services.CursorShape
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

/**
 * Pure-Compose renderer for an out-of-process terminal session.
 *
 * No `bossterm-compose` imports. Reads cell state from [state],
 * draws via [Canvas] with [TextMeasurer], and routes keyboard input
 * through [KeyEventEncoder] into [source]. Cell dimensions are
 * derived from the monospace font at the supplied [fontSize].
 *
 * This composable does not own the terminal session; the caller is
 * expected to feed [state] from a [TerminalGridSource] (real or stub).
 */
@OptIn(ExperimentalTextApi::class)
@Composable
fun TerminalGridRenderer(
    sessionId: String,
    state: GridState,
    source: TerminalGridSource,
    palette: ThemePalette = ThemePalette.Default,
    fontSize: androidx.compose.ui.unit.TextUnit = 14.sp,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val focusRequester = remember { FocusRequester() }
    val coroutineScope: CoroutineScope = rememberCoroutineScope()

    val cellMetrics = remember(fontSize, density) {
        computeCellMetrics(measurer, fontSize, density)
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Box(
        modifier = modifier
            .background(palette.defaultBackground)
            .focusRequester(focusRequester)
            .focusable(interactionSource = remember { MutableInteractionSource() })
            .onPreviewKeyEvent { event ->
                val request = KeyEventEncoder.encode(sessionId, event) ?: return@onPreviewKeyEvent false
                coroutineScope.launch { source.sendKey(request) }
                true
            },
    ) {
        val frame = state.frame
        val cursor = state.cursor

        Canvas(
            modifier = Modifier.size(
                DpSize(
                    width = with(density) { (cellMetrics.cellWidthPx * frame.cols).toDp() },
                    height = with(density) { (cellMetrics.cellHeightPx * frame.rows).toDp() },
                ),
            ),
        ) {
            drawCells(frame, palette, measurer, cellMetrics, state)
            drawCursor(cursor, frame, palette, cellMetrics)
        }
    }
}

private data class CellMetrics(
    val cellWidthPx: Float,
    val cellHeightPx: Float,
    val baselineOffsetPx: Float,
    val textStyle: TextStyle,
)

@OptIn(ExperimentalTextApi::class)
private fun computeCellMetrics(
    measurer: TextMeasurer,
    fontSize: androidx.compose.ui.unit.TextUnit,
    density: Density,
): CellMetrics {
    val style = TextStyle(
        fontSize = fontSize,
        fontFamily = FontFamily.Monospace,
    )
    val sample = measurer.measure(text = "M", style = style)
    val width = sample.size.width.toFloat()
    val height = sample.size.height.toFloat()
    return CellMetrics(
        cellWidthPx = width,
        cellHeightPx = height,
        baselineOffsetPx = sample.firstBaseline,
        textStyle = style,
    )
}

@OptIn(ExperimentalTextApi::class)
private fun DrawScope.drawCells(
    frame: GridState.Frame,
    palette: ThemePalette,
    measurer: TextMeasurer,
    metrics: CellMetrics,
    state: GridState,
) {
    // Coalesce contiguous same-style cells into runs so each row issues one
    // drawText per style segment instead of one per cell. For 80×24 this cuts
    // the per-frame measure/draw count from 1920 to a handful in steady state.
    val text = StringBuilder()
    for (row in 0 until frame.rows) {
        var col = 0
        while (col < frame.cols) {
            val first = frame.cellAt(row, col)
            val style = state.styleOf(first)
            text.setLength(0)
            text.append(first.text.ifEmpty { " " })
            var end = col + 1
            while (end < frame.cols) {
                val next = frame.cellAt(row, end)
                if (next.styleIndex != first.styleIndex) break
                text.append(next.text.ifEmpty { " " })
                end++
            }
            drawRun(
                row = row,
                col = col,
                length = end - col,
                text = text.toString(),
                style = style,
                palette = palette,
                measurer = measurer,
                metrics = metrics,
            )
            col = end
        }
    }
}

@OptIn(ExperimentalTextApi::class)
private fun DrawScope.drawRun(
    row: Int,
    col: Int,
    length: Int,
    text: String,
    style: ResolvedStyle,
    palette: ThemePalette,
    measurer: TextMeasurer,
    metrics: CellMetrics,
) {
    val (fg, bg) = palette.resolvedFgBg(style)
    val x = col * metrics.cellWidthPx
    val y = row * metrics.cellHeightPx
    val w = length * metrics.cellWidthPx

    if (bg != palette.defaultBackground) {
        drawRect(
            color = bg,
            topLeft = Offset(x, y),
            size = Size(w, metrics.cellHeightPx),
        )
    }
    if (text.isBlank() || palette.isInvisible(style)) return

    val runStyle = metrics.textStyle.copy(
        color = fg,
        fontWeight = palette.fontWeightFor(style),
        fontStyle = palette.fontStyleFor(style),
        textDecoration = palette.textDecorationFor(style),
    )
    val layout = measurer.measure(text = text, style = runStyle)
    drawText(textLayoutResult = layout, topLeft = Offset(x, y))
}

private fun DrawScope.drawCursor(
    cursor: GridState.CursorFrame,
    frame: GridState.Frame,
    palette: ThemePalette,
    metrics: CellMetrics,
) {
    if (!cursor.visible) return
    if (cursor.row < 0 || cursor.row >= frame.rows) return
    if (cursor.col < 0 || cursor.col >= frame.cols) return

    val x = cursor.col * metrics.cellWidthPx
    val y = cursor.row * metrics.cellHeightPx
    val w = metrics.cellWidthPx
    val h = metrics.cellHeightPx
    val color: Color = palette.cursorBackground

    when (cursor.shape) {
        CursorShape.CURSOR_SHAPE_BAR -> drawRect(
            color = color,
            topLeft = Offset(x, y),
            size = Size(width = 2f.coerceAtMost(w), height = h),
        )
        CursorShape.CURSOR_SHAPE_UNDERLINE -> drawRect(
            color = color,
            topLeft = Offset(x, y + h - 2f.coerceAtMost(h)),
            size = Size(width = w, height = 2f.coerceAtMost(h)),
        )
        // BLOCK is the safe default for UNSPECIFIED.
        CursorShape.CURSOR_SHAPE_BLOCK,
        CursorShape.CURSOR_SHAPE_UNSPECIFIED,
        CursorShape.UNRECOGNIZED,
        -> drawRect(
            color = color,
            topLeft = Offset(x, y),
            size = Size(width = w, height = h),
        )
    }
}
