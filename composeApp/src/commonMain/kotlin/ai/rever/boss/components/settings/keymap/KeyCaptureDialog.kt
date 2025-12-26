package ai.rever.boss.components.settings.keymap

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import ai.rever.boss.keymap.model.KeyBinding
import ai.rever.boss.keymap.model.ShortcutContext
import ai.rever.boss.utils.SystemUtils

/**
 * Dialog for capturing keyboard shortcuts.
 * Displays a modal that captures the next key combination pressed by the user.
 */
@Composable
fun KeyCaptureDialog(
    actionId: String,
    actionDescription: String,
    context: ShortcutContext,
    category: String,
    currentBinding: KeyBinding?,
    onKeyCaptured: (KeyBinding) -> Unit,
    onDismiss: () -> Unit
) {
    var capturedKey by remember { mutableStateOf<Key?>(null) }
    var capturedModifiers by remember { mutableStateOf<List<String>>(emptyList()) }
    var hasCapture by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .width(500.dp)
                .wrapContentHeight(),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colors.surface,
            elevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Capture Keyboard Shortcut",
                        style = MaterialTheme.typography.h6,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action description
                Text(
                    text = "Action: $actionDescription",
                    style = MaterialTheme.typography.body1,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Context: ${context.displayName}",
                    style = MaterialTheme.typography.body2,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f)
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Current binding display
                if (currentBinding != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Current:",
                            style = MaterialTheme.typography.body2,
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
                        )
                        KeyDisplay(currentBinding.displayString())
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Capture area
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .background(
                            color = MaterialTheme.colors.background,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .border(
                            width = 2.dp,
                            color = if (hasCapture) MaterialTheme.colors.primary else MaterialTheme.colors.onSurface.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .focusRequester(focusRequester)
                        .onPreviewKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown) {
                                // Capture the key and modifiers
                                // Platform-aware modifier capture:
                                // - macOS: Meta (Command) → "Cmd", Ctrl → "Ctrl"
                                // - Linux/Windows: Ctrl → "Cmd" (primary modifier), Meta → "Ctrl"
                                capturedKey = event.key
                                val mods = mutableListOf<String>()
                                val isMacOS = SystemUtils.isMacOS
                                if (isMacOS) {
                                    if (event.isMetaPressed) mods.add("Cmd")
                                    if (event.isCtrlPressed) mods.add("Ctrl")
                                } else {
                                    // On Linux/Windows: Ctrl is the primary modifier (equivalent to Cmd)
                                    if (event.isCtrlPressed) mods.add("Cmd")
                                    if (event.isMetaPressed) mods.add("Ctrl")
                                }
                                if (event.isShiftPressed) mods.add("Shift")
                                if (event.isAltPressed) mods.add("Alt")
                                capturedModifiers = mods
                                hasCapture = true
                                true
                            } else {
                                false
                            }
                        }
                        .focusable(),
                    contentAlignment = Alignment.Center
                ) {
                    if (hasCapture && capturedKey != null) {
                        val displayStr = buildDisplayString(capturedKey!!, capturedModifiers)
                        KeyDisplay(displayStr, large = true)
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Press any key combination...",
                                style = MaterialTheme.typography.h6,
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.4f)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "The dialog is focused and ready to capture",
                                style = MaterialTheme.typography.caption,
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.3f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (hasCapture && capturedKey != null) {
                                val binding = KeyBinding(
                                    actionId = actionId,
                                    key = capturedKey!!.keyCode.toString(),
                                    modifiers = capturedModifiers,
                                    context = context,
                                    category = category,
                                    description = actionDescription,
                                    enabled = true
                                )
                                onKeyCaptured(binding)
                            }
                        },
                        enabled = hasCapture && capturedKey != null
                    ) {
                        Text("Apply")
                    }
                }
            }
        }
    }
}

/**
 * Displays a keyboard shortcut with styled keycap badges.
 */
@Composable
private fun KeyDisplay(shortcutText: String, large: Boolean = false) {
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = shortcutText,
            style = if (large) MaterialTheme.typography.h5 else MaterialTheme.typography.body1,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colors.primary
        )
    }
}

/**
 * Builds a display string for captured keys.
 */
private fun buildDisplayString(key: Key, modifiers: List<String>): String {
    val isMac = System.getProperty("os.name").contains("Mac", ignoreCase = true)

    val modifierStrings = modifiers.map { modifier ->
        when (modifier.lowercase()) {
            "cmd", "meta" -> if (isMac) "⌘" else "Ctrl"
            "ctrl", "control" -> if (isMac) "⌃" else "Ctrl"
            "shift" -> if (isMac) "⇧" else "Shift"
            "alt", "option" -> if (isMac) "⌥" else "Alt"
            else -> modifier
        }
    }

    val keyString = formatKeyDisplay(key.keyCode.toString())

    return (modifierStrings + keyString).joinToString(if (isMac) "" else "+")
}

/**
 * Formats the key name for display.
 */
private fun formatKeyDisplay(keyName: String): String {
    return when (keyName.lowercase()) {
        "space", "spacebar" -> "Space"
        "arrowleft", "directionleft" -> "←"
        "arrowright", "directionright" -> "→"
        "arrowup", "directionup" -> "↑"
        "arrowdown", "directiondown" -> "↓"
        "enter", "return" -> "↩"
        "backspace" -> "⌫"
        "delete" -> "⌦"
        "escape", "esc" -> "Esc"
        "tab" -> "Tab"
        else -> keyName.uppercase()
    }
}
