package ai.rever.bosseditor.ui

import ai.rever.bosseditor.refactoring.SymbolKind
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.delay
import java.awt.KeyboardFocusManager
import java.awt.MouseInfo
import java.awt.Window

/**
 * Inline rename dialog that appears at the symbol position.
 *
 * This is a lightweight popup that appears directly at the symbol location,
 * allowing the user to type the new name with real-time validation feedback.
 *
 * ## Keyboard Shortcuts
 * - Enter: Confirm rename
 * - Escape: Cancel
 * - Tab: Preview changes (if supported)
 *
 * @param currentName The current name of the symbol
 * @param symbolKind The kind of symbol being renamed (for display)
 * @param anchorOffset The position offset where the dialog should appear (relative to parent)
 * @param onRename Callback when rename is confirmed with the new name
 * @param onCancel Callback when rename is cancelled
 * @param onValidate Callback to validate the new name (returns error message or null)
 */
@Composable
fun RenameDialog(
    currentName: String,
    symbolKind: SymbolKind?,
    anchorOffset: IntOffset = IntOffset.Zero,
    onRename: (String) -> Unit,
    onCancel: () -> Unit,
    onValidate: suspend (String) -> String? = { null }
) {
    val theme = LocalEditorTheme.current
    val colors = theme.colors

    var textFieldValue by remember {
        mutableStateOf(
            TextFieldValue(
                text = currentName,
                selection = TextRange(0, currentName.length)
            )
        )
    }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isValidating by remember { mutableStateOf(false) }

    val focusRequester = remember { FocusRequester() }

    // Focus input on show
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    // Validate with debounce when text changes
    LaunchedEffect(textFieldValue.text) {
        if (textFieldValue.text != currentName) {
            isValidating = true
            delay(200) // Debounce validation
            errorMessage = onValidate(textFieldValue.text)
            isValidating = false
        } else {
            errorMessage = null
        }
    }

    // Calculate position relative to window (same logic as context menu)
    val relativePosition = remember {
        val mouseInfo = MouseInfo.getPointerInfo()
        if (mouseInfo != null) {
            val screenX = mouseInfo.location.x
            val screenY = mouseInfo.location.y

            // Get the focused window (same as context menu)
            var targetWindow: Window? = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusedWindow
            if (targetWindow == null) {
                val mousePoint = java.awt.Point(screenX, screenY)
                targetWindow = Window.getWindows()
                    .filter { it.isVisible && it.bounds.contains(mousePoint) }
                    .maxByOrNull { it.bounds.width * it.bounds.height }
            }

            if (targetWindow != null) {
                val windowLocation = targetWindow.locationOnScreen
                IntOffset(screenX - windowLocation.x, screenY - windowLocation.y)
            } else {
                anchorOffset
            }
        } else {
            anchorOffset
        }
    }

    Popup(
        offset = relativePosition,
        onDismissRequest = onCancel,
        properties = PopupProperties(
            focusable = true,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Column(
            modifier = Modifier
                .width(300.dp)
                .background(colors.gutterBackground, RoundedCornerShape(6.dp))
                .border(1.dp, colors.gutterBorder, RoundedCornerShape(6.dp))
                .padding(12.dp)
        ) {
            // Title
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Rename",
                    color = colors.text,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f)
                )
                if (symbolKind != null) {
                    Text(
                        text = symbolKind.name.lowercase().replaceFirstChar { it.uppercase() },
                        color = colors.text.copy(alpha = 0.5f),
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Input field
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp)
                    .background(colors.background, RoundedCornerShape(4.dp))
                    .border(
                        width = 1.dp,
                        color = when {
                            errorMessage != null -> colors.error
                            textFieldValue.text != currentName && !isValidating -> colors.string
                            else -> colors.gutterBorder
                        },
                        shape = RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                BasicTextField(
                    value = textFieldValue,
                    onValueChange = { newValue ->
                        textFieldValue = newValue
                    },
                    textStyle = TextStyle(
                        color = colors.text,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace
                    ),
                    singleLine = true,
                    cursorBrush = SolidColor(colors.caret),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .onKeyEvent { event ->
                            when {
                                event.type == KeyEventType.KeyDown && event.key == Key.Enter -> {
                                    if (errorMessage == null && textFieldValue.text != currentName) {
                                        onRename(textFieldValue.text)
                                    }
                                    true
                                }
                                event.type == KeyEventType.KeyDown && event.key == Key.Escape -> {
                                    onCancel()
                                    true
                                }
                                else -> false
                            }
                        }
                )
            }

            // Error message or validation status
            if (errorMessage != null) {
                Text(
                    text = errorMessage!!,
                    color = colors.error,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            } else if (isValidating) {
                Text(
                    text = "Validating...",
                    color = colors.text.copy(alpha = 0.5f),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            // Help text
            Text(
                text = "Enter to rename • Escape to cancel",
                color = colors.text.copy(alpha = 0.4f),
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

/**
 * Modal rename dialog with more options.
 *
 * This dialog provides additional options like:
 * - Preview changes
 * - Search scope selection
 * - Comment/string update options
 *
 * @param currentName The current name of the symbol
 * @param symbolKind The kind of symbol being renamed
 * @param onRename Callback when rename is confirmed
 * @param onCancel Callback when cancelled
 * @param onPreview Callback to preview changes (optional)
 * @param onValidate Callback to validate the new name
 */
@Composable
fun RenameModalDialog(
    currentName: String,
    symbolKind: SymbolKind?,
    onRename: (String) -> Unit,
    onCancel: () -> Unit,
    onPreview: ((String) -> Unit)? = null,
    onValidate: suspend (String) -> String? = { null }
) {
    val theme = LocalEditorTheme.current
    val colors = theme.colors

    var textFieldValue by remember {
        mutableStateOf(
            TextFieldValue(
                text = currentName,
                selection = TextRange(0, currentName.length)
            )
        )
    }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isValidating by remember { mutableStateOf(false) }

    val focusRequester = remember { FocusRequester() }

    // Focus input on show
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    // Validate with debounce when text changes
    LaunchedEffect(textFieldValue.text) {
        if (textFieldValue.text != currentName) {
            isValidating = true
            delay(200)
            errorMessage = onValidate(textFieldValue.text)
            isValidating = false
        } else {
            errorMessage = null
        }
    }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onCancel,
        properties = androidx.compose.ui.window.DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Column(
            modifier = Modifier
                .width(400.dp)
                .background(colors.gutterBackground, RoundedCornerShape(8.dp))
                .border(1.dp, colors.gutterBorder, RoundedCornerShape(8.dp))
                .padding(16.dp)
        ) {
            // Title
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Rename ${symbolKind?.name?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "Symbol"}",
                    color = colors.text,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Current name label
            Text(
                text = "Current name: $currentName",
                color = colors.text.copy(alpha = 0.7f),
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            // New name input
            Text(
                text = "New name:",
                color = colors.text,
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .background(colors.background, RoundedCornerShape(4.dp))
                    .border(
                        width = 1.dp,
                        color = when {
                            errorMessage != null -> colors.error
                            textFieldValue.text != currentName && !isValidating -> colors.string
                            else -> colors.gutterBorder
                        },
                        shape = RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                BasicTextField(
                    value = textFieldValue,
                    onValueChange = { newValue ->
                        textFieldValue = newValue
                    },
                    textStyle = TextStyle(
                        color = colors.text,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace
                    ),
                    singleLine = true,
                    cursorBrush = SolidColor(colors.caret),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .onKeyEvent { event ->
                            when {
                                event.type == KeyEventType.KeyDown && event.key == Key.Enter -> {
                                    if (errorMessage == null && textFieldValue.text != currentName) {
                                        onRename(textFieldValue.text)
                                    }
                                    true
                                }
                                event.type == KeyEventType.KeyDown && event.key == Key.Escape -> {
                                    onCancel()
                                    true
                                }
                                else -> false
                            }
                        }
                )
            }

            // Error message
            if (errorMessage != null) {
                Text(
                    text = errorMessage!!,
                    color = colors.error,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            } else if (isValidating) {
                Text(
                    text = "Validating...",
                    color = colors.text.copy(alpha = 0.5f),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                // Preview button (if available)
                if (onPreview != null) {
                    TextButton(
                        text = "Preview",
                        onClick = { onPreview(textFieldValue.text) },
                        enabled = errorMessage == null && textFieldValue.text != currentName,
                        colors = colors
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }

                // Cancel button
                TextButton(
                    text = "Cancel",
                    onClick = onCancel,
                    colors = colors
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Rename button
                TextButton(
                    text = "Rename",
                    onClick = { onRename(textFieldValue.text) },
                    enabled = errorMessage == null && textFieldValue.text != currentName,
                    primary = true,
                    colors = colors
                )
            }
        }
    }
}

@Composable
private fun TextButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    primary: Boolean = false,
    colors: ai.rever.bosseditor.theme.EditorColors
) {
    val backgroundColor = when {
        !enabled -> colors.gutterBackground.copy(alpha = 0.5f)
        primary -> colors.selectionBackground
        else -> colors.gutterBackground
    }

    Box(
        modifier = Modifier
            .background(backgroundColor, RoundedCornerShape(4.dp))
            .border(1.dp, colors.gutterBorder, RoundedCornerShape(4.dp))
            .then(
                if (enabled) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (enabled) colors.text else colors.text.copy(alpha = 0.5f),
            fontSize = 12.sp
        )
    }
}
