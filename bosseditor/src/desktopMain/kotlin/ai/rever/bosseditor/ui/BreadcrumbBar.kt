package ai.rever.bosseditor.ui

import ai.rever.bosseditor.features.BreadcrumbItem
import ai.rever.bosseditor.features.BreadcrumbKind
import ai.rever.bosseditor.theme.EditorColors
import ai.rever.bosseditor.theme.LocalEditorTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Breadcrumb navigation bar showing the current scope hierarchy.
 *
 * Displays the path from file root to current scope (File > Class > Method).
 * Clicking a breadcrumb navigates to that scope.
 *
 * ## Features
 * - Shows file, class, method hierarchy
 * - Kind-specific icons/colors
 * - Click to navigate
 * - Horizontal scrolling for long paths
 *
 * JVM-only: Desktop target only (see CLAUDE.md).
 */
@Composable
fun BreadcrumbBar(
    items: List<BreadcrumbItem>,
    onItemClick: (BreadcrumbItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalEditorTheme.current
    val colors = theme.colors
    val scrollState = rememberScrollState()

    if (items.isEmpty()) return

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(24.dp)
            .background(colors.gutterBackground)
            .padding(horizontal = 8.dp)
            .horizontalScroll(scrollState),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEachIndexed { index, item ->
            BreadcrumbItemChip(
                item = item,
                colors = colors,
                onClick = { onItemClick(item) }
            )

            // Separator between items
            if (index < items.size - 1) {
                BreadcrumbSeparator(colors)
            }
        }
    }
}

/**
 * Single breadcrumb item chip.
 */
@Composable
private fun BreadcrumbItemChip(
    item: BreadcrumbItem,
    colors: EditorColors,
    onClick: () -> Unit
) {
    val (iconText, iconColor) = getBreadcrumbIcon(item.kind, colors)

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(2.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Kind icon
        Box(
            modifier = Modifier
                .size(14.dp)
                .background(iconColor, RoundedCornerShape(2.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = iconText,
                color = colors.background,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }

        Spacer(modifier = Modifier.width(4.dp))

        // Name
        Text(
            text = item.name,
            color = colors.text,
            fontSize = 11.sp,
            fontFamily = FontFamily.Default,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Separator between breadcrumb items.
 */
@Composable
private fun BreadcrumbSeparator(colors: EditorColors) {
    Text(
        text = ">",
        color = colors.text.copy(alpha = 0.5f),
        fontSize = 10.sp,
        modifier = Modifier.padding(horizontal = 4.dp)
    )
}

/**
 * Gets the icon letter and color for a breadcrumb kind.
 */
private fun getBreadcrumbIcon(kind: BreadcrumbKind, colors: EditorColors): Pair<String, Color> {
    return when (kind) {
        BreadcrumbKind.FILE -> "F" to colors.text.copy(alpha = 0.5f)
        BreadcrumbKind.PACKAGE -> "P" to Color(0xFFB3AE60)      // Yellow
        BreadcrumbKind.MODULE -> "M" to Color(0xFFB3AE60)       // Yellow
        BreadcrumbKind.CLASS -> "C" to Color(0xFFCF8E6D)        // Orange
        BreadcrumbKind.INTERFACE -> "I" to Color(0xFF6AAB73)    // Green
        BreadcrumbKind.ENUM -> "E" to Color(0xFFCF8E6D)         // Orange
        BreadcrumbKind.FUNCTION -> "F" to Color(0xFF56A8F5)     // Blue
        BreadcrumbKind.PROPERTY -> "P" to Color(0xFFC77DBB)     // Purple
        BreadcrumbKind.VARIABLE -> "V" to Color(0xFFC77DBB)     // Purple
        BreadcrumbKind.CONSTRUCTOR -> "C" to Color(0xFF56A8F5)  // Blue
        BreadcrumbKind.OBJECT -> "O" to Color(0xFFCF8E6D)       // Orange
        BreadcrumbKind.COMPANION -> "O" to Color(0xFFCF8E6D)    // Orange
        BreadcrumbKind.LAMBDA -> "L" to Color(0xFF56A8F5)       // Blue
        BreadcrumbKind.BLOCK -> "B" to colors.text.copy(alpha = 0.4f)
        BreadcrumbKind.OTHER -> "?" to colors.text.copy(alpha = 0.4f)
    }
}

/**
 * Configuration for breadcrumb bar appearance.
 */
data class BreadcrumbConfig(
    val showIcons: Boolean = true,
    val showFile: Boolean = true,
    val maxItems: Int = 10,
    val height: Int = 24
)

/**
 * Breadcrumb bar with dropdown menus for each item.
 * Shows sibling items when clicking a breadcrumb.
 */
@Composable
fun BreadcrumbBarWithDropdowns(
    items: List<BreadcrumbItem>,
    siblingProvider: (BreadcrumbItem) -> List<BreadcrumbItem>,
    onItemClick: (BreadcrumbItem) -> Unit,
    modifier: Modifier = Modifier
) {
    // TODO: Implement dropdown menus for sibling navigation
    // For now, use basic breadcrumb bar
    BreadcrumbBar(
        items = items,
        onItemClick = onItemClick,
        modifier = modifier
    )
}
