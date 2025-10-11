package ai.rever.boss.components.configuration

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.material.RadioButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ai.rever.boss.platform.rememberFilePicker

/**
 * Save configuration dialog
 */
@Composable
fun SaveConfigurationDialog(
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    
    androidx.compose.material.AlertDialog(
        onDismissRequest = onDismiss,
        title = { androidx.compose.material.Text("Save Configuration") },
        text = {
            androidx.compose.material.OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { androidx.compose.material.Text("Configuration Name") },
                singleLine = true
            )
        },
        confirmButton = {
            androidx.compose.material.TextButton(
                onClick = { onSave(name) },
                enabled = name.isNotBlank()
            ) {
                androidx.compose.material.Text("Save")
            }
        },
        dismissButton = {
            androidx.compose.material.TextButton(onClick = onDismiss) {
                androidx.compose.material.Text("Cancel")
            }
        }
    )
}

/**
 * Open configuration dialog with file picker
 */
@Composable
fun OpenConfigurationDialog(
    onDismiss: () -> Unit,
    onOpen: (String) -> Unit
) {
    val filePicker = rememberFilePicker(
        onFileSelected = { path, content ->
            if (content != null) {
                onOpen(content)
            }
            onDismiss()
        },
        fileExtensions = listOf("json")
    )
    
    // Immediately trigger file picker
    LaunchedEffect(Unit) {
        filePicker.pickFile()
    }
}

/**
 * Delete configuration dialog
 */
@Composable
fun DeleteConfigurationDialog(
    configurations: List<LayoutConfiguration>,
    onDismiss: () -> Unit,
    onDelete: (String) -> Unit
) {
    var selectedConfig by remember { mutableStateOf<String?>(null) }
    
    androidx.compose.material.AlertDialog(
        onDismissRequest = onDismiss,
        title = { androidx.compose.material.Text("Delete Configuration") },
        text = {
            Column {
                androidx.compose.material.Text(
                    "Select a configuration to delete:",
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                configurations.forEach { config ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedConfig = config.name }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedConfig == config.name,
                            onClick = { selectedConfig = config.name }
                        )
                        androidx.compose.material.Text(
                            text = config.name,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
                
                if (configurations.isEmpty()) {
                    androidx.compose.material.Text(
                        "No custom configurations to delete.",
                        color = androidx.compose.ui.graphics.Color.Gray
                    )
                }
            }
        },
        confirmButton = {
            androidx.compose.material.TextButton(
                onClick = { 
                    selectedConfig?.let { onDelete(it) }
                },
                enabled = selectedConfig != null
            ) {
                androidx.compose.material.Text("Delete", color = androidx.compose.ui.graphics.Color.Red)
            }
        },
        dismissButton = {
            androidx.compose.material.TextButton(onClick = onDismiss) {
                androidx.compose.material.Text("Cancel")
            }
        }
    )
}