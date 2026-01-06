package ai.rever.boss.aiassistant

import BossDarkAccent
import BossDarkSurface
import androidx.compose.foundation.layout.*
import androidx.compose.material.AlertDialog
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Dialog shown when a workspace requires an AI assistant that is not installed.
 * Offers options to install, skip (apply workspace anyway), or cancel.
 *
 * Issue #445: Auto-install AI assistants for workspaces
 */
@Composable
fun AIAssistantInstallDialog(
    assistant: AIAssistant,
    workspaceName: String,
    onInstall: () -> Unit,
    onSkip: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = {
            Text(
                "${assistant.displayName} Not Installed",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    "The \"$workspaceName\" workspace requires ${assistant.displayName} to be installed.",
                    color = Color.Gray,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "Would you like to install it now?",
                    color = Color.Gray,
                    fontSize = 14.sp
                )
            }
        },
        buttons = {
            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                TextButton(onClick = onCancel) {
                    Text("Cancel", color = Color.Gray)
                }
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(onClick = onSkip) {
                    Text("Skip", color = Color.Gray)
                }
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(onClick = onInstall) {
                    Text("Install", color = BossDarkAccent, fontWeight = FontWeight.Bold)
                }
            }
        },
        backgroundColor = BossDarkSurface,
        contentColor = Color.White
    )
}
