package ai.rever.bosseditor.ui

import ai.rever.bosseditor.fold.FoldRegion
import ai.rever.bosseditor.theme.LocalEditorTheme
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Inline fold placeholder widget shown when a fold is collapsed.
 *
 * Displays the placeholder text (e.g., "{ ... }" or "import ...") inline
 * with the code. Clicking expands the fold.
 *
 * ## Usage
 * ```kotlin
 * FoldPlaceholder(
 *     fold = foldRegion,
 *     onClick = { foldingModel.expandFold(foldRegion) }
 * )
 * ```
 */
@Composable
fun FoldPlaceholder(
    fold: FoldRegion,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalEditorTheme.current
    val colors = theme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Box(
        modifier = modifier
            .height(IntrinsicSize.Min)
            .hoverable(interactionSource)
            .clickable(onClick = onClick)
            .background(
                color = if (isHovered) colors.foldPlaceholderHover else colors.foldPlaceholderBackground,
                shape = RoundedCornerShape(2.dp)
            )
            .border(
                width = 1.dp,
                color = colors.foldPlaceholderBorder,
                shape = RoundedCornerShape(2.dp)
            )
            .padding(horizontal = 4.dp, vertical = 1.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = fold.placeholder,
            color = colors.foldPlaceholderText,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Fold indicator icon in the gutter.
 *
 * Shows a collapsible/expandable arrow icon:
 * - ▼ when expanded (click to collapse)
 * - ▶ when collapsed (click to expand)
 */
@Composable
fun FoldIndicatorIcon(
    isCollapsed: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalEditorTheme.current
    val colors = theme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Box(
        modifier = modifier
            .size(16.dp)
            .hoverable(interactionSource)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (isCollapsed) "▶" else "▼",
            color = if (isHovered) colors.text else colors.foldIndicator,
            fontSize = 8.sp
        )
    }
}

/**
 * Fold guide line drawn in the editor area.
 *
 * Shows a vertical dashed line from the fold start to fold end
 * to visualize the fold scope.
 */
@Composable
fun FoldGuideLine(
    startY: Float,
    endY: Float,
    x: Float,
    modifier: Modifier = Modifier
) {
    val theme = LocalEditorTheme.current
    val colors = theme.colors

    Canvas(modifier = modifier.fillMaxSize()) {
        drawFoldGuide(
            startY = startY,
            endY = endY,
            x = x,
            color = colors.foldGuide
        )
    }
}

/**
 * Draws a fold guide line (vertical dashed line).
 */
private fun DrawScope.drawFoldGuide(
    startY: Float,
    endY: Float,
    x: Float,
    color: Color
) {
    drawLine(
        color = color,
        start = Offset(x, startY),
        end = Offset(x, endY),
        strokeWidth = 1f,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f))
    )
}

/**
 * Draws multiple fold guides for visible folds.
 */
@Composable
fun FoldGuides(
    foldRegions: List<FoldRegion>,
    firstVisibleLine: Int,
    visibleLineCount: Int,
    lineHeight: Float,
    indentWidth: Float,
    modifier: Modifier = Modifier
) {
    val theme = LocalEditorTheme.current
    val colors = theme.colors

    Canvas(modifier = modifier.fillMaxSize()) {
        for (fold in foldRegions) {
            // Only draw guides for expanded folds
            if (fold.isCollapsed) continue

            // Check if fold is visible
            val foldStartVisible = fold.startLine >= firstVisibleLine &&
                    fold.startLine < firstVisibleLine + visibleLineCount
            val foldEndVisible = fold.endLine >= firstVisibleLine &&
                    fold.endLine < firstVisibleLine + visibleLineCount
            val foldSpansVisible = fold.startLine < firstVisibleLine &&
                    fold.endLine >= firstVisibleLine + visibleLineCount

            if (foldStartVisible || foldEndVisible || foldSpansVisible) {
                // Calculate visible portion of the fold
                val startLine = (fold.startLine - firstVisibleLine).coerceAtLeast(0)
                val endLine = (fold.endLine - firstVisibleLine).coerceAtMost(visibleLineCount)

                val startY = startLine * lineHeight + lineHeight / 2
                val endY = endLine * lineHeight + lineHeight / 2

                // X position - use a fixed position relative to indent width
                // Note: Using fixed indent for consistent fold guide positioning
                val x = indentWidth

                drawFoldGuide(
                    startY = startY,
                    endY = endY,
                    x = x,
                    color = colors.foldGuide
                )
            }
        }
    }
}

/**
 * Indent guide lines for visual indentation tracking.
 */
@Composable
fun IndentGuides(
    maxIndentLevel: Int,
    firstVisibleLine: Int,
    visibleLineCount: Int,
    lineHeight: Float,
    indentWidth: Float,
    lineIndents: (Int) -> Int, // Line -> indent level
    modifier: Modifier = Modifier
) {
    val theme = LocalEditorTheme.current
    val colors = theme.colors

    Canvas(modifier = modifier.fillMaxSize()) {
        // Track indent levels across visible lines
        for (level in 1..maxIndentLevel) {
            val x = level * indentWidth

            // Find continuous runs of lines with at least this indent
            var runStart: Int? = null

            for (visualLine in 0 until visibleLineCount) {
                val documentLine = firstVisibleLine + visualLine
                val indent = lineIndents(documentLine)

                if (indent >= level) {
                    if (runStart == null) {
                        runStart = visualLine
                    }
                } else {
                    if (runStart != null) {
                        // Draw guide for completed run
                        drawLine(
                            color = colors.indentGuide,
                            start = Offset(x, runStart * lineHeight),
                            end = Offset(x, visualLine * lineHeight),
                            strokeWidth = 1f
                        )
                        runStart = null
                    }
                }
            }

            // Draw remaining run
            if (runStart != null) {
                drawLine(
                    color = colors.indentGuide,
                    start = Offset(x, runStart * lineHeight),
                    end = Offset(x, visibleLineCount * lineHeight),
                    strokeWidth = 1f
                )
            }
        }
    }
}

/**
 * Active indent guide (highlighted when cursor is in that scope).
 */
@Composable
fun ActiveIndentGuide(
    indentLevel: Int,
    startLine: Int,
    endLine: Int,
    firstVisibleLine: Int,
    lineHeight: Float,
    indentWidth: Float,
    modifier: Modifier = Modifier
) {
    val theme = LocalEditorTheme.current
    val colors = theme.colors

    if (indentLevel <= 0) return

    Canvas(modifier = modifier.fillMaxSize()) {
        val x = indentLevel * indentWidth

        val visibleStart = (startLine - firstVisibleLine).coerceAtLeast(0)
        val visibleEnd = endLine - firstVisibleLine

        if (visibleEnd >= 0) {
            drawLine(
                color = colors.activeIndentGuide,
                start = Offset(x, visibleStart * lineHeight),
                end = Offset(x, visibleEnd * lineHeight + lineHeight),
                strokeWidth = 2f
            )
        }
    }
}

/**
 * Configuration for fold and indent guides.
 */
data class GuideConfig(
    val showFoldGuides: Boolean = true,
    val showIndentGuides: Boolean = true,
    val highlightActiveIndent: Boolean = true,
    val guideOpacity: Float = 0.3f
)
