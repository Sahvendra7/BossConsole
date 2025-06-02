package ai.rever.boss.components.configuration

import ai.rever.boss.components.buttons.BossActionButton
import ai.rever.boss.components.overlays.ContextMenuItem
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.*
import androidx.compose.runtime.LaunchedEffect
import compose.icons.FeatherIcons
import compose.icons.feathericons.Settings
import ai.rever.boss.platform.rememberFilePicker

/**
 * Platform-specific function to open configuration directory
 */
expect fun openConfigurationDirectory(path: String)

/**
 * Configuration button with dropdown menu
 */
@Composable
fun ConfigurationButton(
    onOpenConfiguration: (LayoutConfiguration) -> Unit,
    configurationManager: ConfigurationManager = remember { ConfigurationManager() },
    getCurrentConfiguration: (() -> LayoutConfiguration)? = null
) {
    val currentConfiguration by configurationManager.currentConfiguration.collectAsState()
    val configurations by configurationManager.configurations.collectAsState()
    
    var showSaveDialog by remember { mutableStateOf(false) }
    var showOpenDialog by remember { mutableStateOf(false) }
    
    // Build context menu items
    val contextMenuItems = buildList {
        // Predefined configurations
        configurations.forEach { config ->
            add(ContextMenuItem(
                text = config.name,
                icon = null,
                onClick = { 
                    configurationManager.loadConfiguration(config)
                    onOpenConfiguration(config)
                }
            ))
        }
        
        add(ContextMenuItem(isDivider = true))
        
        // Open from file
        add(ContextMenuItem(
            text = "Open from File...",
            icon = Icons.Outlined.Upload,
            onClick = { showOpenDialog = true }
        ))
        
        add(ContextMenuItem(isDivider = true))
        
        // Save configuration - always ask for name
        add(ContextMenuItem(
            text = "Save Configuration...",
            icon = Icons.Outlined.Save,
            onClick = { showSaveDialog = true }
        ))
        
        add(ContextMenuItem(isDivider = true))
        
        // Open configuration directory
        add(ContextMenuItem(
            text = "Open Configuration Folder",
            icon = Icons.Outlined.FolderOpen,
            onClick = { 
                // This will be platform-specific implementation
                openConfigurationDirectory(configurationManager.getConfigurationDirectory())
            }
        ))
        
        add(ContextMenuItem(isDivider = true))
        
        // Reset to default
        add(ContextMenuItem(
            text = "Reset to Default",
            icon = Icons.Outlined.RestartAlt,
            onClick = { 
                configurationManager.resetToDefault()
                onOpenConfiguration(LayoutConfiguration(
                    name = "Default",
                    description = "Default layout",
                    layout = SplitConfig.SinglePanel(
                        PanelConfig(
                            id = "main",
                            tabs = emptyList()
                        )
                    )
                ))
            }
        ))
    }
    
    Box {
        Box {
            BossActionButton(
                leftIcon = FeatherIcons.Settings,
                text = currentConfiguration?.let { config ->
                if (config.name != "Current") config.name else "Default"
            } ?: "Default",
                contextMenuItems = contextMenuItems,
                hintText = buildString {
                    append("Layout Configuration: ${currentConfiguration?.description ?: "Default layout"}")
                    append("\nConfigurations saved to: ${configurationManager.getConfigurationDirectory()}")
                }
            )
            
        }
    }
    
    // Save dialog
    if (showSaveDialog) {
        SaveConfigurationDialog(
            onDismiss = { showSaveDialog = false },
            onSave = { name ->
                // Get current layout and save it with the provided name
                getCurrentConfiguration?.invoke()?.let { currentLayout ->
                    configurationManager.updateCurrentConfiguration(currentLayout)
                    configurationManager.saveCurrentConfiguration(name)
                }
                showSaveDialog = false
            }
        )
    }
    
    // Open dialog
    if (showOpenDialog) {
        OpenConfigurationDialog(
            onDismiss = { showOpenDialog = false },
            onOpen = { jsonString ->
                configurationManager.importConfiguration(jsonString)?.let { config ->
                    configurationManager.loadConfiguration(config)
                    onOpenConfiguration(config)
                }
                showOpenDialog = false
            }
        )
    }
}

/**
 * Save configuration dialog
 */
@Composable
private fun SaveConfigurationDialog(
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
private fun OpenConfigurationDialog(
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