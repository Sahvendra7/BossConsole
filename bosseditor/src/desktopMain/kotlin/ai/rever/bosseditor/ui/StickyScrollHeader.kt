package ai.rever.bosseditor.ui

import ai.rever.bosseditor.features.StickyHeader
import ai.rever.bosseditor.features.StickyHeaderKind
import ai.rever.bosseditor.theme.EditorColors
import ai.rever.bosseditor.theme.LocalEditorTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Sticky scroll header overlay that pins scope context at the top of the editor.
 *
 * Shows class/method/scope headers when scrolling, providing context for
 * the current position in the code (like VS Code's sticky scroll).
 *
 * ## Features
 * - Shows nested scope headers (class > method)
 * - Click to navigate to header line
 * - Subtle shadow to separate from content
 * - Kind-specific styling
 *
 * JVM-only: Desktop target only (see CLAUDE.md).
 */
@Composable
fun StickyScrollHeader(
    headers: List<StickyHeader>,
    gutterWidth: Float,
    lineHeight: Float,
    charWidth: Float,
    showLineNumbers: Boolean,
    onHeaderClick: (StickyHeader) -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalEditorTheme.current
    val colors = theme.colors

    if (headers.isEmpty()) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(2.dp)
            .background(colors.background)
    ) {
        headers.forEachIndexed { index, header ->
            StickyHeaderRow(
                header = header,
                gutterWidth = gutterWidth,
                lineHeight = lineHeight,
                charWidth = charWidth,
                showLineNumber = showLineNumbers,
                colors = colors,
                isLast = index == headers.size - 1,
                onClick = { onHeaderClick(header) }
            )
        }
    }
}

/**
 * Single sticky header row.
 */
@Composable
private fun StickyHeaderRow(
    header: StickyHeader,
    gutterWidth: Float,
    lineHeight: Float,
    charWidth: Float,
    showLineNumber: Boolean,
    colors: EditorColors,
    isLast: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(lineHeight.dp)
            .background(getStickyHeaderBackground(header.kind, colors, isLast))
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Gutter area (line number)
        Box(
            modifier = Modifier
                .width(gutterWidth.dp)
                .fillMaxHeight()
                .background(colors.gutterBackground),
            contentAlignment = Alignment.CenterEnd
        ) {
            if (showLineNumber) {
                Text(
                    text = (header.line + 1).toString(),
                    color = colors.lineNumber,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
        }

        // Indent based on depth
        Spacer(modifier = Modifier.width((header.depth * 2 * charWidth).dp))

        // Header text
        Text(
            text = header.text,
            color = colors.text.copy(alpha = 0.9f),
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * Gets background color for sticky header based on kind.
 */
private fun getStickyHeaderBackground(
    kind: StickyHeaderKind,
    colors: EditorColors,
    isLast: Boolean
): Color {
    val baseColor = colors.background
    val alpha = if (isLast) 0.98f else 0.95f

    // Subtle tint based on kind
    return when (kind) {
        StickyHeaderKind.CLASS -> baseColor.copy(alpha = alpha)
        StickyHeaderKind.FUNCTION -> baseColor.copy(alpha = alpha)
        StickyHeaderKind.CONTROL -> baseColor.copy(alpha = alpha * 0.97f)
        StickyHeaderKind.LAMBDA -> baseColor.copy(alpha = alpha * 0.97f)
        StickyHeaderKind.OTHER -> baseColor.copy(alpha = alpha * 0.95f)
    }
}

/**
 * Wrapper composable that positions sticky headers as an overlay.
 */
@Composable
fun StickyScrollOverlay(
    headers: List<StickyHeader>,
    gutterWidth: Float,
    lineHeight: Float,
    charWidth: Float,
    showLineNumbers: Boolean,
    onHeaderClick: (StickyHeader) -> Unit,
    content: @Composable () -> Unit
) {
    Box {
        content()

        if (headers.isNotEmpty()) {
            StickyScrollHeader(
                headers = headers,
                gutterWidth = gutterWidth,
                lineHeight = lineHeight,
                charWidth = charWidth,
                showLineNumbers = showLineNumbers,
                onHeaderClick = onHeaderClick,
                modifier = Modifier.align(Alignment.TopStart)
            )
        }
    }
}

/**
 * Configuration for sticky scroll header appearance.
 */
data class StickyScrollHeaderConfig(
    val enabled: Boolean = true,
    val maxHeaders: Int = 3,
    val showLineNumbers: Boolean = true,
    val shadowElevation: Dp = 2.dp
)
