package ai.rever.bosseditor.ui

import ai.rever.bosseditor.features.BlameAge
import ai.rever.bosseditor.features.BlameInfo
import ai.rever.bosseditor.fold.VisualLineMapper
import ai.rever.bosseditor.theme.EditorColors
import ai.rever.bosseditor.theme.LocalEditorTheme
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Git blame gutter showing author and commit info for each line.
 *
 * Displays blame annotations with:
 * - Author name (truncated if needed)
 * - Short commit hash
 * - Color-coded by commit age
 *
 * Click on a line to show full commit details.
 *
 * JVM-only: Desktop target only (see CLAUDE.md).
 */
@Composable
fun BlameGutter(
    getBlameForLine: (Int) -> BlameInfo?,
    firstVisibleLine: Int,
    visibleLineCount: Int,
    lineHeight: Float,
    visualLineMapper: VisualLineMapper,
    width: Float = 180f,
    showAuthor: Boolean = true,
    showHash: Boolean = true,
    colorByAge: Boolean = true,
    onLineClick: (Int, BlameInfo?) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val theme = LocalEditorTheme.current
    val colors = theme.colors
    val textMeasurer = rememberTextMeasurer()
    val nowTimestamp = remember { System.currentTimeMillis() / 1000 }

    val textStyle = remember {
        TextStyle(
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )
    }

    Canvas(
        modifier = modifier
            .width(width.dp)
            .fillMaxHeight()
            .background(colors.gutterBackground)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val visualLine = (offset.y / lineHeight).toInt() + firstVisibleLine
                    val documentLine = visualLineMapper.visualToDocument(visualLine)
                    if (documentLine >= 0) {
                        val blame = getBlameForLine(documentLine)
                        onLineClick(documentLine, blame)
                    }
                }
            }
    ) {
        val lastVisibleLine = (firstVisibleLine + visibleLineCount)
            .coerceAtMost(visualLineMapper.visibleLineCount - 1)

        var lastCommitHash: String? = null

        for (visualLine in firstVisibleLine..lastVisibleLine) {
            val documentLine = visualLineMapper.visualToDocument(visualLine)
            if (documentLine < 0) continue

            val blame = getBlameForLine(documentLine)
            val yOffset = (visualLine - firstVisibleLine) * lineHeight

            if (blame != null) {
                // Determine color based on age
                val textColor = if (colorByAge) {
                    val age = BlameAge.fromDays(blame.ageInDays(nowTimestamp))
                    getBlameAgeColor(age, colors)
                } else {
                    colors.lineNumber
                }

                // Only show full info if this is a different commit than the previous line
                val showFullInfo = blame.commitHash != lastCommitHash
                lastCommitHash = blame.commitHash

                if (showFullInfo) {
                    // Draw author name
                    var xOffset = 8f
                    if (showAuthor) {
                        val authorText = truncateAuthor(blame.author, 12)
                        drawBlameText(
                            textMeasurer = textMeasurer,
                            text = authorText,
                            x = xOffset,
                            y = yOffset,
                            lineHeight = lineHeight,
                            textStyle = textStyle.copy(color = textColor),
                            maxWidth = 100f
                        )
                        xOffset += 105f
                    }

                    // Draw commit hash
                    if (showHash) {
                        drawBlameText(
                            textMeasurer = textMeasurer,
                            text = blame.shortHash,
                            x = xOffset,
                            y = yOffset,
                            lineHeight = lineHeight,
                            textStyle = textStyle.copy(color = textColor.copy(alpha = 0.7f)),
                            maxWidth = 60f
                        )
                    }
                } else {
                    // Draw continuation indicator (subtle line or dots)
                    drawRect(
                        color = textColor.copy(alpha = 0.2f),
                        topLeft = Offset(8f, yOffset + lineHeight / 2 - 0.5f),
                        size = Size(width - 16f, 1f)
                    )
                }
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

/**
 * Draws blame text at the specified position.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBlameText(
    textMeasurer: TextMeasurer,
    text: String,
    x: Float,
    y: Float,
    lineHeight: Float,
    textStyle: TextStyle,
    maxWidth: Float
) {
    val layoutResult = textMeasurer.measure(
        text = text,
        style = textStyle,
        overflow = TextOverflow.Ellipsis,
        maxLines = 1
    )

    val textHeight = layoutResult.size.height
    val textY = y + (lineHeight - textHeight) / 2

    drawText(
        textLayoutResult = layoutResult,
        topLeft = Offset(x, textY)
    )
}

/**
 * Gets the color for a blame age category.
 */
fun getBlameAgeColor(age: BlameAge, colors: EditorColors): Color = when (age) {
    BlameAge.UNCOMMITTED -> Color(0xFF6AAB73)  // Green
    BlameAge.VERY_RECENT -> Color(0xFF6AAB73)  // Green
    BlameAge.RECENT -> Color(0xFF7A9E7A)       // Light green
    BlameAge.MODERATE -> colors.lineNumber
    BlameAge.OLD -> colors.lineNumber.copy(alpha = 0.7f)
    BlameAge.VERY_OLD -> colors.lineNumber.copy(alpha = 0.5f)
}

/**
 * Truncates author name to fit in the available space.
 */
private fun truncateAuthor(author: String, maxLength: Int): String {
    return if (author.length > maxLength) {
        author.substring(0, maxLength - 1) + "…"
    } else {
        author
    }
}

/**
 * Configuration for blame gutter appearance.
 */
data class BlameGutterConfig(
    val enabled: Boolean = false,
    val width: Float = 180f,
    val showAuthor: Boolean = true,
    val showHash: Boolean = true,
    val colorByAge: Boolean = true
)
