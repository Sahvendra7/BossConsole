package ai.rever.bosseditor.ui

import ai.rever.bosseditor.features.CompletionItem
import ai.rever.bosseditor.features.CompletionKind
import ai.rever.bosseditor.features.CompletionState
import ai.rever.bosseditor.theme.EditorColors
import ai.rever.bosseditor.theme.LocalEditorTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties

/**
 * Completion popup for code completion suggestions.
 *
 * Displays a list of completion items with icons, labels, and details.
 * Supports keyboard navigation and filtering.
 *
 * ## Keyboard Shortcuts
 * - Up/Down: Navigate items
 * - Page Up/Down: Navigate by page
 * - Enter/Tab: Accept selected item
 * - Escape: Dismiss popup
 */
@Composable
fun CompletionPopup(
    state: CompletionState,
    position: IntOffset,
    onItemSelected: (CompletionItem) -> Unit,
    onDismiss: () -> Unit,
    onNavigateUp: () -> Unit,
    onNavigateDown: () -> Unit,
    onPageUp: () -> Unit,
    onPageDown: () -> Unit,
    maxVisibleItems: Int = 10,
    modifier: Modifier = Modifier
) {
    val theme = LocalEditorTheme.current
    val colors = theme.colors
    val listState = rememberLazyListState()

    // Scroll to selected item when selection changes
    LaunchedEffect(state.selectedIndex) {
        if (state.filteredItems.isNotEmpty()) {
            listState.animateScrollToItem(state.selectedIndex)
        }
    }

    if (!state.hasItems) return

    Popup(
        offset = position,
        onDismissRequest = onDismiss,
        properties = PopupProperties(
            focusable = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Column(
            modifier = modifier
                .width(350.dp)
                .heightIn(max = (maxVisibleItems * 24 + 16).dp)
                .background(colors.gutterBackground, RoundedCornerShape(4.dp))
                .border(1.dp, colors.gutterBorder, RoundedCornerShape(4.dp))
                .onKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown) {
                        when (event.key) {
                            Key.DirectionUp -> {
                                onNavigateUp()
                                true
                            }
                            Key.DirectionDown -> {
                                onNavigateDown()
                                true
                            }
                            Key.PageUp -> {
                                onPageUp()
                                true
                            }
                            Key.PageDown -> {
                                onPageDown()
                                true
                            }
                            Key.Enter, Key.Tab -> {
                                state.selectedItem?.let { onItemSelected(it) }
                                true
                            }
                            Key.Escape -> {
                                onDismiss()
                                true
                            }
                            else -> false
                        }
                    } else false
                }
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp)
            ) {
                itemsIndexed(state.filteredItems) { index, item ->
                    CompletionItemRow(
                        item = item,
                        isSelected = index == state.selectedIndex,
                        colors = colors,
                        onClick = { onItemSelected(item) }
                    )
                }
            }
        }
    }
}

/**
 * A single row in the completion popup.
 */
@Composable
private fun CompletionItemRow(
    item: CompletionItem,
    isSelected: Boolean,
    colors: EditorColors,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(
                if (isSelected) colors.selectionBackground
                else Color.Transparent
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Kind icon
        CompletionKindIcon(
            kind = item.kind,
            colors = colors,
            modifier = Modifier.size(16.dp)
        )

        Spacer(modifier = Modifier.width(6.dp))

        // Label
        Text(
            text = item.label,
            color = if (item.deprecated) colors.text.copy(alpha = 0.5f) else colors.text,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Normal,
            textDecoration = if (item.deprecated) TextDecoration.LineThrough else TextDecoration.None,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        // Detail (type signature, etc.)
        item.detail?.let { detail ->
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = detail,
                color = colors.text.copy(alpha = 0.5f),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontStyle = FontStyle.Italic,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 150.dp)
            )
        }
    }
}

/**
 * Icon representing the completion item kind.
 */
@Composable
private fun CompletionKindIcon(
    kind: CompletionKind,
    colors: EditorColors,
    modifier: Modifier = Modifier
) {
    val (letter, bgColor) = when (kind) {
        CompletionKind.TEXT -> "T" to colors.text.copy(alpha = 0.3f)
        CompletionKind.METHOD, CompletionKind.FUNCTION -> "M" to Color(0xFF56A8F5) // Blue
        CompletionKind.CONSTRUCTOR -> "C" to Color(0xFF56A8F5)
        CompletionKind.FIELD -> "F" to Color(0xFFC77DBB) // Purple
        CompletionKind.VARIABLE -> "V" to Color(0xFFC77DBB)
        CompletionKind.CLASS -> "C" to Color(0xFFCF8E6D) // Orange
        CompletionKind.INTERFACE -> "I" to Color(0xFF6AAB73) // Green
        CompletionKind.MODULE -> "M" to Color(0xFFB3AE60) // Yellow
        CompletionKind.PROPERTY -> "P" to Color(0xFFC77DBB)
        CompletionKind.UNIT -> "U" to colors.text.copy(alpha = 0.3f)
        CompletionKind.VALUE -> "V" to Color(0xFF2AACB8) // Cyan
        CompletionKind.ENUM -> "E" to Color(0xFFCF8E6D)
        CompletionKind.KEYWORD -> "K" to Color(0xFFCF8E6D)
        CompletionKind.SNIPPET -> "S" to Color(0xFF6AAB73)
        CompletionKind.COLOR -> "#" to Color(0xFFFFB86C) // Orange
        CompletionKind.FILE -> "F" to colors.text.copy(alpha = 0.3f)
        CompletionKind.REFERENCE -> "R" to colors.text.copy(alpha = 0.3f)
        CompletionKind.FOLDER -> "D" to colors.text.copy(alpha = 0.3f)
        CompletionKind.ENUM_MEMBER -> "E" to Color(0xFF2AACB8)
        CompletionKind.CONSTANT -> "C" to Color(0xFF2AACB8)
        CompletionKind.STRUCT -> "S" to Color(0xFFCF8E6D)
        CompletionKind.EVENT -> "E" to Color(0xFFFFB86C)
        CompletionKind.OPERATOR -> "O" to colors.text.copy(alpha = 0.3f)
        CompletionKind.TYPE_PARAMETER -> "T" to Color(0xFF6AAB73)
    }

    Box(
        modifier = modifier
            .background(bgColor, RoundedCornerShape(2.dp))
            .padding(2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = letter,
            color = colors.background,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

/**
 * Documentation tooltip for completion items.
 * Shows when hovering or when documentation is available.
 */
@Composable
fun CompletionDocumentation(
    item: CompletionItem,
    position: IntOffset,
    modifier: Modifier = Modifier
) {
    if (item.documentation == null) return

    val theme = LocalEditorTheme.current
    val colors = theme.colors

    Popup(
        offset = position,
        properties = PopupProperties(
            focusable = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Box(
            modifier = modifier
                .widthIn(max = 400.dp)
                .heightIn(max = 200.dp)
                .background(colors.gutterBackground, RoundedCornerShape(4.dp))
                .border(1.dp, colors.gutterBorder, RoundedCornerShape(4.dp))
                .padding(8.dp)
        ) {
            Text(
                text = item.documentation,
                color = colors.text,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
        }
    }
}

/**
 * Simplified completion popup that manages its own state.
 * Useful for simple use cases where external state management isn't needed.
 */
@Composable
fun SimpleCompletionPopup(
    items: List<CompletionItem>,
    prefix: String,
    position: IntOffset,
    onItemSelected: (CompletionItem) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var state by remember(items, prefix) {
        mutableStateOf(
            CompletionState(
                triggerPosition = ai.rever.bosseditor.core.EditorPosition(0, 0),
                prefix = prefix,
                allItems = items
            )
        )
    }

    CompletionPopup(
        state = state,
        position = position,
        onItemSelected = onItemSelected,
        onDismiss = onDismiss,
        onNavigateUp = { state = state.moveUp() },
        onNavigateDown = { state = state.moveDown() },
        onPageUp = { state = state.pageUp() },
        onPageDown = { state = state.pageDown() },
        modifier = modifier
    )
}
