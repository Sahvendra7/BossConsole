package ai.rever.bosseditor.ui

import ai.rever.bosseditor.features.SearchOptions
import ai.rever.bosseditor.theme.LocalEditorTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Search bar composable for the editor.
 *
 * Features:
 * - Find text field with keyboard shortcuts
 * - Replace text field (expandable)
 * - Match count display
 * - Navigation (next/previous)
 * - Search options (case sensitive, whole word, regex)
 *
 * ## Keyboard Shortcuts
 * - Enter: Find next
 * - Shift+Enter: Find previous
 * - Escape: Close search bar
 * - Ctrl/Cmd+Shift+H: Toggle replace mode
 */
@Composable
fun SearchBar(
    modifier: Modifier = Modifier,
    initialQuery: String = "",
    showReplace: Boolean = false,
    matchCount: Int = 0,
    currentMatchIndex: Int = -1,
    onSearch: (String, SearchOptions) -> Unit = { _, _ -> },
    onFindNext: () -> Unit = {},
    onFindPrevious: () -> Unit = {},
    onReplace: (String) -> Unit = {},
    onReplaceAll: (String) -> Unit = {},
    onClose: () -> Unit = {}
) {
    val theme = LocalEditorTheme.current
    val colors = theme.colors

    var query by remember { mutableStateOf(initialQuery) }
    var replacement by remember { mutableStateOf("") }
    var caseSensitive by remember { mutableStateOf(false) }
    var wholeWord by remember { mutableStateOf(false) }
    var useRegex by remember { mutableStateOf(false) }
    var isReplaceExpanded by remember { mutableStateOf(showReplace) }

    val searchFocusRequester = remember { FocusRequester() }

    // Focus search field on show
    LaunchedEffect(Unit) {
        searchFocusRequester.requestFocus()
    }

    // Trigger search when query or options change
    LaunchedEffect(query, caseSensitive, wholeWord, useRegex) {
        onSearch(query, SearchOptions(caseSensitive, wholeWord, useRegex))
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.gutterBackground)
            .padding(8.dp)
    ) {
        // Find row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Expand/collapse replace
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clickable { isReplaceExpanded = !isReplaceExpanded },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isReplaceExpanded) "▼" else "▶",
                    color = colors.text.copy(alpha = 0.7f),
                    fontSize = 10.sp
                )
            }

            // Search input
            SearchTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = "Find",
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(searchFocusRequester)
                    .onKeyEvent { event ->
                        when {
                            event.type == KeyEventType.KeyDown && event.key == Key.Enter -> {
                                if (event.isShiftPressed) {
                                    onFindPrevious()
                                } else {
                                    onFindNext()
                                }
                                true
                            }
                            event.type == KeyEventType.KeyDown && event.key == Key.Escape -> {
                                onClose()
                                true
                            }
                            else -> false
                        }
                    },
                colors = colors
            )

            // Match count
            if (query.isNotEmpty()) {
                Text(
                    text = if (matchCount > 0) {
                        "${currentMatchIndex + 1}/$matchCount"
                    } else {
                        "No results"
                    },
                    color = if (matchCount > 0) colors.text.copy(alpha = 0.7f) else colors.error,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }

            // Navigation buttons
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clickable(enabled = matchCount > 0) { onFindPrevious() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "▲",
                    color = if (matchCount > 0) colors.text else colors.text.copy(alpha = 0.3f),
                    fontSize = 10.sp
                )
            }

            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clickable(enabled = matchCount > 0) { onFindNext() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "▼",
                    color = if (matchCount > 0) colors.text else colors.text.copy(alpha = 0.3f),
                    fontSize = 10.sp
                )
            }

            // Search options
            SearchOptionToggle(
                label = "Aa",
                tooltip = "Case sensitive",
                isActive = caseSensitive,
                onClick = { caseSensitive = !caseSensitive },
                colors = colors
            )

            SearchOptionToggle(
                label = "W",
                tooltip = "Whole word",
                isActive = wholeWord,
                onClick = { wholeWord = !wholeWord },
                colors = colors
            )

            SearchOptionToggle(
                label = ".*",
                tooltip = "Regular expression",
                isActive = useRegex,
                onClick = { useRegex = !useRegex },
                colors = colors
            )

            // Close button
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clickable { onClose() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "✕",
                    color = colors.text.copy(alpha = 0.7f),
                    fontSize = 12.sp
                )
            }
        }

        // Replace row (expandable)
        if (isReplaceExpanded) {
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Spacer to align with find field
                Spacer(modifier = Modifier.size(24.dp))

                // Replace input
                SearchTextField(
                    value = replacement,
                    onValueChange = { replacement = it },
                    placeholder = "Replace",
                    modifier = Modifier
                        .weight(1f)
                        .onKeyEvent { event ->
                            when {
                                event.type == KeyEventType.KeyDown && event.key == Key.Enter -> {
                                    onReplace(replacement)
                                    true
                                }
                                event.type == KeyEventType.KeyDown && event.key == Key.Escape -> {
                                    onClose()
                                    true
                                }
                                else -> false
                            }
                        },
                    colors = colors
                )

                // Replace button
                SmallTextButton(
                    text = "Replace",
                    onClick = { onReplace(replacement) },
                    enabled = matchCount > 0,
                    colors = colors
                )

                // Replace all button
                SmallTextButton(
                    text = "All",
                    onClick = { onReplaceAll(replacement) },
                    enabled = matchCount > 0,
                    colors = colors
                )

                // Spacer to balance the row
                Spacer(modifier = Modifier.width(24.dp))
            }
        }
    }
}

@Composable
private fun SearchTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    colors: ai.rever.bosseditor.theme.EditorColors
) {
    Box(
        modifier = modifier
            .height(28.dp)
            .background(colors.background, RoundedCornerShape(4.dp))
            .border(1.dp, colors.gutterBorder, RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        if (value.isEmpty()) {
            Text(
                text = placeholder,
                color = colors.text.copy(alpha = 0.5f),
                fontSize = 13.sp,
                fontFamily = FontFamily.SansSerif
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = TextStyle(
                color = colors.text,
                fontSize = 13.sp,
                fontFamily = FontFamily.SansSerif
            ),
            singleLine = true,
            cursorBrush = SolidColor(colors.caret),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun SearchOptionToggle(
    label: String,
    tooltip: String,
    isActive: Boolean,
    onClick: () -> Unit,
    colors: ai.rever.bosseditor.theme.EditorColors
) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .background(
                if (isActive) colors.selectionBackground else Color.Transparent,
                RoundedCornerShape(4.dp)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (isActive) colors.text else colors.text.copy(alpha = 0.5f),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun SmallTextButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean,
    colors: ai.rever.bosseditor.theme.EditorColors
) {
    Box(
        modifier = Modifier
            .height(24.dp)
            .background(
                if (enabled) colors.gutterBorder else Color.Transparent,
                RoundedCornerShape(4.dp)
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (enabled) colors.text else colors.text.copy(alpha = 0.3f),
            fontSize = 12.sp
        )
    }
}
