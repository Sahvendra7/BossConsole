package ai.rever.bosseditor.ui

import ai.rever.bosseditor.features.QuickFixKind
import ai.rever.bosseditor.features.QuickFixLine
import ai.rever.bosseditor.fold.VisualLineMapper
import ai.rever.bosseditor.theme.EditorColors
import ai.rever.bosseditor.theme.LocalEditorTheme
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/**
 * Constants for lightbulb gutter dimensions and colors.
 */
private object LightbulbGutterConstants {
    /** Width of the lightbulb gutter column */
    const val GUTTER_WIDTH_DP = 20

    /** Size of the lightbulb icon in dp */
    const val ICON_SIZE_DP = 14

    /** Color for yellow/gold lightbulb (standard fixes) */
    val LIGHTBULB_YELLOW = Color(0xFFFFD700)

    /** Color for blue lightbulb (spelling fixes) */
    val LIGHTBULB_BLUE = Color(0xFF548AF7)

    /** Color for gray base of lightbulb */
    val LIGHTBULB_BASE_GRAY = Color(0xFF808080)
}

/**
 * Lightbulb gutter showing quick fix indicators.
 *
 * Displays a yellow lightbulb icon on lines that have available quick fixes.
 * Clicking the lightbulb opens the quick fix popup.
 *
 * ## Colors
 * - Yellow: Standard quick fix (spelling, intentions)
 * - Red: Error fix available
 * - Orange: Warning fix available
 *
 * JVM-only: Desktop target only (see CLAUDE.md).
 */
@Composable
fun LightbulbGutter(
    quickFixLines: List<QuickFixLine>,
    firstVisibleLine: Int,
    visibleLineCount: Int,
    lineHeight: Float,
    visualLineMapper: VisualLineMapper,
    onLightbulbClick: (QuickFixLine) -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalEditorTheme.current
    val colors = theme.colors

    Canvas(
        modifier = modifier
            .width(LightbulbGutterConstants.GUTTER_WIDTH_DP.dp)
            .fillMaxHeight()
            .background(colors.gutterBackground)
            .pointerInput(quickFixLines) {
                detectTapGestures { offset ->
                    // Find which line was clicked
                    val visualLine = (offset.y / lineHeight).toInt() + firstVisibleLine
                    val documentLine = visualLineMapper.visualToDocument(visualLine)

                    // Check if there's a quick fix on this line
                    quickFixLines.find { it.line == documentLine }?.let { fixLine ->
                        onLightbulbClick(fixLine)
                    }
                }
            }
    ) {
        val iconSize = LightbulbGutterConstants.ICON_SIZE_DP.dp.toPx()
        val centerX = size.width / 2

        for (fixLine in quickFixLines) {
            // Convert document line to visual line
            val visualLine = visualLineMapper.documentToVisual(fixLine.line)
            if (visualLine < 0) continue // Line is folded

            // Check if line is visible
            if (visualLine < firstVisibleLine || visualLine >= firstVisibleLine + visibleLineCount) {
                continue
            }

            val relativeVisualLine = visualLine - firstVisibleLine
            val y = relativeVisualLine * lineHeight + (lineHeight - iconSize) / 2

            // Determine lightbulb color based on fix type
            val lightbulbColor = when {
                fixLine.hasErrorFix -> colors.gutterError
                fixLine.hasWarningFix -> colors.gutterWarning
                else -> LightbulbGutterConstants.LIGHTBULB_YELLOW
            }

            drawLightbulb(
                centerX = centerX,
                y = y,
                size = iconSize,
                color = lightbulbColor,
                backgroundColor = colors.gutterBackground
            )
        }
    }
}

/**
 * Draws a lightbulb icon.
 *
 * The lightbulb design:
 * - Round bulb at top (filled with color)
 * - Narrowing neck
 * - Small base at bottom
 */
private fun DrawScope.drawLightbulb(
    centerX: Float,
    y: Float,
    size: Float,
    color: Color,
    backgroundColor: Color
) {
    val bulbRadius = size * 0.35f
    val bulbCenterY = y + bulbRadius + 1

    // Draw bulb (circle)
    drawCircle(
        color = color,
        radius = bulbRadius,
        center = Offset(centerX, bulbCenterY)
    )

    // Draw glow effect (outer circle with lower opacity)
    drawCircle(
        color = color.copy(alpha = 0.3f),
        radius = bulbRadius * 1.3f,
        center = Offset(centerX, bulbCenterY)
    )

    // Draw neck (trapezoid)
    val neckTop = bulbCenterY + bulbRadius * 0.7f
    val neckBottom = neckTop + size * 0.2f
    val neckTopWidth = bulbRadius * 0.8f
    val neckBottomWidth = bulbRadius * 0.5f

    val neckPath = Path().apply {
        moveTo(centerX - neckTopWidth, neckTop)
        lineTo(centerX + neckTopWidth, neckTop)
        lineTo(centerX + neckBottomWidth, neckBottom)
        lineTo(centerX - neckBottomWidth, neckBottom)
        close()
    }
    drawPath(
        path = neckPath,
        color = color.copy(alpha = 0.8f),
        style = Fill
    )

    // Draw base (small rounded rect)
    val baseTop = neckBottom
    val baseHeight = size * 0.15f
    val baseWidth = bulbRadius * 0.6f

    drawRoundRect(
        color = LightbulbGutterConstants.LIGHTBULB_BASE_GRAY,
        topLeft = Offset(centerX - baseWidth, baseTop),
        size = Size(baseWidth * 2, baseHeight),
        cornerRadius = CornerRadius(2f)
    )
}

/**
 * Gets the lightbulb color for a quick fix kind.
 */
fun getLightbulbColor(kind: QuickFixKind, colors: EditorColors): Color = when (kind) {
    QuickFixKind.ERROR_FIX -> colors.gutterError
    QuickFixKind.WARNING_FIX -> colors.gutterWarning
    QuickFixKind.SPELLING -> LightbulbGutterConstants.LIGHTBULB_BLUE
    else -> LightbulbGutterConstants.LIGHTBULB_YELLOW
}
