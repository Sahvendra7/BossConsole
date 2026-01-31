package ai.rever.boss.plugin.sandbox.ui

import ai.rever.boss.plugin.sandbox.PluginException
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Dialog showing detailed plugin error information.
 *
 * Similar to IntelliJ's plugin error dialog, this provides:
 * - Plugin name and error message
 * - Expandable stack trace
 * - Actions: Disable Plugin, Restart, Report Issue, Close
 *
 * @param pluginId The ID of the plugin that crashed
 * @param error The error that occurred
 * @param onDismiss Called when the dialog should be closed
 * @param onDisable Called when the user clicks "Disable Plugin"
 * @param onRestart Called when the user clicks "Restart Plugin"
 * @param onReport Optional callback for "Report Issue" action
 */
@Composable
fun PluginErrorDialog(
    pluginId: String,
    error: Throwable,
    onDismiss: () -> Unit,
    onDisable: () -> Unit,
    onRestart: () -> Unit,
    onReport: (() -> Unit)? = null
) {
    var showStackTrace by remember { mutableStateOf(false) }

    // Extract plugin info if it's a PluginException
    val pluginException = error as? PluginException
    val displayName = pluginException?.displayName ?: pluginId

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.width(8.dp))
                Text("Plugin Error")
            }
        },
        text = {
            Column {
                // Plugin name
                Text(
                    text = "Plugin: $displayName",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )

                if (pluginException?.pluginName != null) {
                    Text(
                        text = "ID: $pluginId",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(12.dp))

                // Error type
                Text(
                    text = error.javaClass.simpleName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error
                )

                Spacer(Modifier.height(4.dp))

                // Error message
                Text(
                    text = error.message ?: "Unknown error occurred",
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(Modifier.height(16.dp))

                // Expandable stack trace
                TextButton(
                    onClick = { showStackTrace = !showStackTrace }
                ) {
                    Text(if (showStackTrace) "Hide Details" else "Show Details")
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = if (showStackTrace) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        contentDescription = null
                    )
                }

                if (showStackTrace) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        SelectionContainer {
                            Text(
                                text = error.stackTraceToString(),
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier
                                    .padding(8.dp)
                                    .verticalScroll(rememberScrollState())
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row {
                // Report button (if provided)
                if (onReport != null) {
                    OutlinedButton(onClick = onReport) {
                        Icon(
                            imageVector = Icons.Outlined.BugReport,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 4.dp)
                        )
                        Text("Report Issue")
                    }
                    Spacer(Modifier.width(8.dp))
                }

                // Restart button
                Button(onClick = {
                    onRestart()
                    onDismiss()
                }) {
                    Text("Restart Plugin")
                }
            }
        },
        dismissButton = {
            Row {
                // Close button
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
                Spacer(Modifier.width(8.dp))
                // Disable button
                TextButton(onClick = {
                    onDisable()
                    onDismiss()
                }) {
                    Text(
                        text = "Disable Plugin",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    )
}

/**
 * Simplified error dialog for non-critical errors.
 *
 * Shows just the error message with an OK button.
 *
 * @param pluginId The ID of the plugin
 * @param error The error that occurred
 * @param onDismiss Called when the dialog should be closed
 */
@Composable
fun SimplePluginErrorDialog(
    pluginId: String,
    error: Throwable,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.width(8.dp))
                Text("Plugin Error")
            }
        },
        text = {
            Column {
                Text(
                    text = "Plugin '$pluginId' encountered an error:",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = error.message ?: "Unknown error",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("OK")
            }
        }
    )
}
