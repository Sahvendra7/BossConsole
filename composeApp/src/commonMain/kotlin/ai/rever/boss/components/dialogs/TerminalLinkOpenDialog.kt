package ai.rever.boss.components.dialogs

import ai.rever.boss.terminal.TerminalLinkOpenMode
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Tab
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material.icons.outlined.ViewColumn
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Dialog shown when user clicks a link in terminal.
 * Offers options for how to open the link with "Remember my choice" option.
 *
 * Issue #346: Terminal link click prompt with remember preference
 *
 * @param url The URL that was clicked
 * @param onDismiss Called when dialog is dismissed without selection
 * @param onOpenLink Called when user selects an option. Receives the mode and whether to remember the choice.
 */
@Composable
fun TerminalLinkOpenDialog(
    url: String,
    onDismiss: () -> Unit,
    onOpenLink: (mode: TerminalLinkOpenMode, rememberChoice: Boolean) -> Unit
) {
    var rememberChoice by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Card(
            modifier = Modifier
                .width(380.dp)
                .onKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                        onDismiss()
                        true
                    } else {
                        false
                    }
                },
            shape = RoundedCornerShape(8.dp),
            backgroundColor = Color(0xFF2B2D30),
            elevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                // Title
                Text(
                    text = "Open Link",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(8.dp))

                // URL preview (truncated)
                Text(
                    text = url,
                    fontSize = 12.sp,
                    color = Color(0xFF999999),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Options
                LinkOpenOption(
                    icon = Icons.Outlined.ViewColumn,
                    title = "Vertical Split",
                    description = "Open alongside terminal",
                    onClick = { onOpenLink(TerminalLinkOpenMode.VERTICAL_SPLIT, rememberChoice) }
                )

                Spacer(modifier = Modifier.height(8.dp))

                LinkOpenOption(
                    icon = Icons.Outlined.ViewAgenda,
                    title = "Horizontal Split",
                    description = "Open below terminal",
                    onClick = { onOpenLink(TerminalLinkOpenMode.HORIZONTAL_SPLIT, rememberChoice) }
                )

                Spacer(modifier = Modifier.height(8.dp))

                LinkOpenOption(
                    icon = Icons.Outlined.Tab,
                    title = "New Tab",
                    description = "Open in browser tab",
                    onClick = { onOpenLink(TerminalLinkOpenMode.NEW_TAB, rememberChoice) }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Remember checkbox - Row handles click, so Checkbox uses null for onCheckedChange
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { rememberChoice = !rememberChoice }
                        .padding(vertical = 4.dp)
                ) {
                    Checkbox(
                        checked = rememberChoice,
                        onCheckedChange = null, // Row handles click
                        colors = CheckboxDefaults.colors(
                            checkedColor = Color(0xFF4A9EFF),
                            uncheckedColor = Color(0xFF666666),
                            checkmarkColor = Color.White
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Remember my choice",
                        fontSize = 14.sp,
                        color = Color(0xFFCCCCCC)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Cancel button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onDismiss,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = Color(0xFF999999)
                        )
                    ) {
                        Text("Cancel")
                    }
                }
            }
        }
    }
}

/**
 * Individual option card in the link open dialog.
 */
@Composable
private fun LinkOpenOption(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        backgroundColor = Color(0xFF3C3F41),
        shape = RoundedCornerShape(6.dp),
        elevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = Color(0xFF4A9EFF),
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
                Text(
                    text = description,
                    fontSize = 12.sp,
                    color = Color(0xFF999999)
                )
            }
        }
    }
}
