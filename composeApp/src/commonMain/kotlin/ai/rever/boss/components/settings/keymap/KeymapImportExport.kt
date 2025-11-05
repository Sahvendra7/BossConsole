package ai.rever.boss.components.settings.keymap

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import ai.rever.boss.keymap.KeymapSettingsManager
import kotlinx.coroutines.launch

/**
 * Component for importing and exporting keymap settings.
 */
@Composable
fun KeymapImportExport(
    onImport: suspend (String) -> Boolean,
    modifier: Modifier = Modifier
) {
    var showExportDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var importError by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    Column(modifier = modifier) {
        Text(
            text = "Import / Export",
            style = MaterialTheme.typography.subtitle1,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Backup your keymap or share it with others",
            style = MaterialTheme.typography.caption,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Export button
            OutlinedButton(
                onClick = { showExportDialog = true },
                modifier = Modifier.weight(1f).height(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.FileDownload,
                    contentDescription = "Export",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Export Keymap")
            }

            // Import button
            OutlinedButton(
                onClick = { showImportDialog = true },
                modifier = Modifier.weight(1f).height(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.FileUpload,
                    contentDescription = "Import",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Import Keymap")
            }
        }

        // Show import error if any
        importError?.let { error ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "⚠️ $error",
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.error
            )
        }
    }

    // Export dialog
    if (showExportDialog) {
        ExportDialog(
            onDismiss = { showExportDialog = false }
        )
    }

    // Import dialog
    if (showImportDialog) {
        ImportDialog(
            onImport = { jsonString ->
                coroutineScope.launch {
                    val success = onImport(jsonString)
                    if (success) {
                        showImportDialog = false
                        importError = null
                    } else {
                        importError = "Failed to import keymap. Check JSON format."
                    }
                }
            },
            onDismiss = {
                showImportDialog = false
                importError = null
            }
        )
    }
}

/**
 * Dialog for exporting keymap to JSON.
 */
@Composable
private fun ExportDialog(
    onDismiss: () -> Unit
) {
    val exportedJson = KeymapSettingsManager.exportToJson()
    var copied by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .width(600.dp)
                .wrapContentHeight(),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colors.surface,
            elevation = 8.dp
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = "Export Keymap",
                    style = MaterialTheme.typography.h6,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Copy the JSON below to backup or share your keymap:",
                    style = MaterialTheme.typography.body2,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // JSON text area
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colors.background
                ) {
                    Box(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = exportedJson,
                            style = MaterialTheme.typography.caption.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                            color = MaterialTheme.colors.onBackground
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Close")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            // TODO: Copy to clipboard
                            // For now, user must manually select and copy
                            copied = true
                        }
                    ) {
                        Text(if (copied) "Copied!" else "Copy to Clipboard")
                    }
                }

                if (copied) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "💡 Tip: Save this JSON to a file for backup",
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.colors.primary.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

/**
 * Dialog for importing keymap from JSON.
 */
@Composable
private fun ImportDialog(
    onImport: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var jsonInput by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .width(600.dp)
                .wrapContentHeight(),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colors.surface,
            elevation = 8.dp
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = "Import Keymap",
                    style = MaterialTheme.typography.h6,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Paste keymap JSON below:",
                    style = MaterialTheme.typography.body2,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // JSON input area
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colors.background
                ) {
                    TextField(
                        value = jsonInput,
                        onValueChange = {
                            jsonInput = it
                            showError = false
                        },
                        modifier = Modifier.fillMaxSize(),
                        textStyle = MaterialTheme.typography.caption.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                        placeholder = { Text("Paste JSON here...") },
                        colors = TextFieldDefaults.textFieldColors(
                            backgroundColor = MaterialTheme.colors.background,
                            focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                            unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent
                        )
                    )
                }

                if (showError) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "⚠️ Invalid JSON format. Please check and try again.",
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.colors.error
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

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
                            if (jsonInput.isBlank()) {
                                showError = true
                            } else {
                                onImport(jsonInput)
                            }
                        },
                        enabled = jsonInput.isNotBlank()
                    ) {
                        Text("Import")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "⚠️ Warning: Importing will replace your current keymap",
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.error.copy(alpha = 0.7f)
                )
            }
        }
    }
}
