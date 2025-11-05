package ai.rever.boss.components.settings.keymap

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import ai.rever.boss.keymap.model.KeymapSettings
import ai.rever.boss.keymap.presets.KeymapPresets
import kotlinx.coroutines.launch

/**
 * Preset selector component for choosing keyboard shortcut presets.
 */
@Composable
fun PresetSelector(
    currentSettings: KeymapSettings,
    onPresetSelected: suspend (String) -> Unit,
    onResetToDefault: suspend () -> Unit,
    modifier: Modifier = Modifier
) {
    var showPresetMenu by remember { mutableStateOf(false) }
    var showResetConfirmation by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    Column(modifier = modifier) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Keymap Preset",
                    style = MaterialTheme.typography.subtitle1,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Choose a predefined keyboard shortcut scheme",
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Preset selector button and reset button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Preset dropdown
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clickable { showPresetMenu = true },
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colors.surface,
                elevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = currentSettings.presetName,
                            style = MaterialTheme.typography.body1,
                            fontWeight = FontWeight.Medium
                        )
                        if (currentSettings.customized) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colors.primary.copy(alpha = 0.1f)
                            ) {
                                Text(
                                    text = "CUSTOMIZED",
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colors.primary
                                )
                            }
                        }
                    }
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = "Select preset",
                        tint = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                    )
                }
            }

            // Reset button
            Button(
                onClick = { showResetConfirmation = true },
                modifier = Modifier.height(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Reset to default",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Reset to Default")
            }
        }

        // Preset description
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = getPresetDescription(currentSettings.presetName),
            style = MaterialTheme.typography.caption,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f),
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }

    // Preset menu dialog
    if (showPresetMenu) {
        PresetMenuDialog(
            currentPreset = currentSettings.presetName,
            onPresetSelected = { presetName ->
                showPresetMenu = false
                coroutineScope.launch {
                    onPresetSelected(presetName)
                }
            },
            onDismiss = { showPresetMenu = false }
        )
    }

    // Reset confirmation dialog
    if (showResetConfirmation) {
        ResetConfirmationDialog(
            onConfirm = {
                showResetConfirmation = false
                coroutineScope.launch {
                    onResetToDefault()
                }
            },
            onDismiss = { showResetConfirmation = false }
        )
    }
}

/**
 * Dialog showing available presets.
 */
@Composable
private fun PresetMenuDialog(
    currentPreset: String,
    onPresetSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val presets = KeymapPresets.getAvailablePresets()

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.width(400.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colors.surface,
            elevation = 8.dp
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Select Keymap Preset",
                    style = MaterialTheme.typography.h6,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(16.dp))

                presets.forEach { presetName ->
                    PresetMenuItem(
                        presetName = presetName,
                        isSelected = presetName == currentPreset,
                        onClick = { onPresetSelected(presetName) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Spacer(modifier = Modifier.height(8.dp))

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Cancel")
                }
            }
        }
    }
}

/**
 * Individual preset menu item.
 */
@Composable
private fun PresetMenuItem(
    presetName: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) MaterialTheme.colors.primary.copy(alpha = 0.1f) else Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = presetName,
                    style = MaterialTheme.typography.body1,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
                Text(
                    text = getPresetDescription(presetName),
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                )
            }
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colors.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * Confirmation dialog for resetting to default.
 */
@Composable
private fun ResetConfirmationDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reset to Default Keymap?") },
        text = {
            Text("This will restore all keyboard shortcuts to the BOSS default keymap. Any customizations will be lost.")
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("Reset")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Gets a description for a preset.
 */
private fun getPresetDescription(presetName: String): String {
    return when (presetName) {
        "BOSS Default" -> "Standard browser-style shortcuts with Cmd-based bindings"
        "VS Code" -> "Visual Studio Code inspired shortcuts (Cmd+P, Cmd+Shift+E, Cmd+Alt+Arrow)"
        "IntelliJ IDEA" -> "IntelliJ IDEA inspired shortcuts (Cmd+E, Cmd+1, Cmd+Alt+Arrow)"
        "Emacs" -> "Emacs-inspired Ctrl-based shortcuts (Ctrl+F, Ctrl+K, Alt+X)"
        else -> "Custom keyboard shortcut configuration"
    }
}
