package ai.rever.bosseditor.ui

import ai.rever.bosseditor.features.QuickFix
import ai.rever.bosseditor.features.QuickFixKind
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties

/**
 * Quick fix popup displaying available actions for the current position.
 *
 * Shows a list of quick fixes with icons and descriptions.
 * Triggered by Alt+Enter or clicking the lightbulb icon.
 *
 * ## Keyboard Shortcuts
 * - Up/Down: Navigate items
 * - Enter: Execute selected fix
 * - Escape: Dismiss popup
 *
 * JVM-only: Desktop target only (see CLAUDE.md).
 */
@Composable
fun QuickFixPopup(
    fixes: List<QuickFix>,
    position: IntOffset,
    selectedIndex: Int = 0,
    onFixSelected: (QuickFix) -> Unit,
    onDismiss: () -> Unit,
    onNavigateUp: () -> Unit,
    onNavigateDown: () -> Unit,
    maxVisibleItems: Int = 8,
    modifier: Modifier = Modifier
) {
    val theme = LocalEditorTheme.current
    val colors = theme.colors
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }

    // Scroll to selected item when selection changes
    LaunchedEffect(selectedIndex) {
        if (fixes.isNotEmpty() && selectedIndex in fixes.indices) {
            listState.animateScrollToItem(selectedIndex)
        }
    }

    // Request focus when popup appears for immediate keyboard navigation
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    if (fixes.isEmpty()) return

    Popup(
        offset = position,
        onDismissRequest = onDismiss,
        properties = PopupProperties(
            focusable = true,  // Enable focus for keyboard navigation
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Column(
            modifier = modifier
                .width(400.dp)
                .heightIn(max = (maxVisibleItems * 28 + 16).dp)
                .focusRequester(focusRequester)
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
                            Key.Enter -> {
                                if (selectedIndex in fixes.indices) {
                                    onFixSelected(fixes[selectedIndex])
                                }
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
            // Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.background.copy(alpha = 0.5f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "Quick Fixes",
                    color = colors.text.copy(alpha = 0.7f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp)
            ) {
                itemsIndexed(fixes) { index, fix ->
                    QuickFixItemRow(
                        fix = fix,
                        isSelected = index == selectedIndex,
                        colors = colors,
                        onClick = { onFixSelected(fix) }
                    )
                }
            }
        }
    }
}

/**
 * A single row in the quick fix popup.
 */
@Composable
private fun QuickFixItemRow(
    fix: QuickFix,
    isSelected: Boolean,
    colors: EditorColors,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(
                if (isSelected) colors.selectionBackground
                else Color.Transparent
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Kind icon
        QuickFixKindIcon(
            kind = fix.kind,
            colors = colors,
            modifier = Modifier.size(18.dp)
        )

        Spacer(modifier = Modifier.width(8.dp))

        // Title and description
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = fix.title,
                color = colors.text,
                fontSize = 12.sp,
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            fix.description?.let { description ->
                Text(
                    text = description,
                    color = colors.text.copy(alpha = 0.5f),
                    fontSize = 10.sp,
                    fontStyle = FontStyle.Italic,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Icon representing the quick fix kind.
 */
@Composable
private fun QuickFixKindIcon(
    kind: QuickFixKind,
    colors: EditorColors,
    modifier: Modifier = Modifier
) {
    val (symbol, bgColor) = when (kind) {
        QuickFixKind.SPELLING -> "Aa" to Color(0xFF548AF7)  // Blue
        QuickFixKind.IMPORT -> "I" to Color(0xFF6AAB73)     // Green
        QuickFixKind.REFACTOR -> "R" to Color(0xFFCF8E6D)   // Orange
        QuickFixKind.ERROR_FIX -> "!" to Color(0xFFF75464)  // Red
        QuickFixKind.WARNING_FIX -> "!" to Color(0xFFFFB848) // Yellow
        QuickFixKind.INTENTION -> "?" to Color(0xFF56A8F5)   // Light blue
        QuickFixKind.SUPPRESS -> "S" to Color(0xFF7A7E85)   // Gray
        QuickFixKind.OTHER -> "?" to colors.text.copy(alpha = 0.3f)
    }

    Box(
        modifier = modifier
            .background(bgColor, RoundedCornerShape(3.dp))
            .padding(2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = symbol,
            color = colors.background,
            fontSize = if (symbol.length > 1) 7.sp else 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

/**
 * State holder for quick fix popup.
 */
class QuickFixPopupState(
    initialFixes: List<QuickFix> = emptyList()
) {
    var fixes by mutableStateOf(initialFixes)
        private set
    var selectedIndex by mutableStateOf(0)
        private set
    var isVisible by mutableStateOf(false)
        private set
    var position by mutableStateOf(IntOffset.Zero)
        private set

    val selectedFix: QuickFix?
        get() = if (fixes.isEmpty()) null else fixes.getOrNull(selectedIndex.coerceIn(fixes.indices))

    val hasFixes: Boolean
        get() = fixes.isNotEmpty()

    fun show(fixes: List<QuickFix>, position: IntOffset) {
        this.fixes = fixes.sortedBy { it.priority }
        this.selectedIndex = 0
        this.position = position
        this.isVisible = true
    }

    fun hide() {
        isVisible = false
        fixes = emptyList()
        selectedIndex = 0
    }

    fun moveUp() {
        if (fixes.isNotEmpty()) {
            selectedIndex = (selectedIndex - 1).coerceAtLeast(0)
        }
    }

    fun moveDown() {
        if (fixes.isNotEmpty()) {
            selectedIndex = (selectedIndex + 1).coerceAtMost(fixes.size - 1)
        }
    }

    fun executeSelected(): Boolean {
        selectedFix?.let { fix ->
            fix.execute()
            hide()
            return true
        }
        return false
    }
}

/**
 * Creates and remembers a QuickFixPopupState.
 */
@Composable
fun rememberQuickFixPopupState(): QuickFixPopupState {
    return remember { QuickFixPopupState() }
}

/**
 * Simplified quick fix popup that uses remembered state.
 */
@Composable
fun QuickFixPopupWithState(
    state: QuickFixPopupState,
    onDismiss: () -> Unit = { state.hide() },
    modifier: Modifier = Modifier
) {
    if (!state.isVisible) return

    QuickFixPopup(
        fixes = state.fixes,
        position = state.position,
        selectedIndex = state.selectedIndex,
        onFixSelected = { fix ->
            fix.execute()
            state.hide()
        },
        onDismiss = onDismiss,
        onNavigateUp = { state.moveUp() },
        onNavigateDown = { state.moveDown() },
        modifier = modifier
    )
}
