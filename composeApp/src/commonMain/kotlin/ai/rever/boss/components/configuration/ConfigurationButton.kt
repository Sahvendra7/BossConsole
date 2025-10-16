package ai.rever.boss.components.configuration

import ai.rever.boss.components.buttons.BossActionButton
import ai.rever.boss.components.overlays.ContextMenuItem
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Tab
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import compose.icons.FeatherIcons
import compose.icons.feathericons.Settings

/**
 * Platform-specific function to open configuration directory
 */
expect fun openConfigurationDirectory(path: String)

/**
 * Builds context menu items for the configuration button
 */
private fun buildConfigurationContextMenu(
    configurations: List<LayoutConfiguration>,
    configurationManager: ConfigurationManager,
    onOpenConfiguration: (LayoutConfiguration) -> Unit,
    onShowSaveDialog: () -> Unit,
    onShowOpenDialog: () -> Unit,
    onShowDeleteDialog: () -> Unit,
    onShowTopOfMind: (() -> Unit)? = null
): List<ContextMenuItem> = buildList {
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
    
    // Delete configuration section
    val deletableConfigs = configurations.filter { config ->
        !PredefinedConfigurations.allConfigurations.any { it.name == config.name }
    }
    
    if (deletableConfigs.isNotEmpty()) {
        add(ContextMenuItem(
            text = "Delete Configuration",
            icon = Icons.Outlined.Delete,
            onClick = onShowDeleteDialog
        ))
        
        add(ContextMenuItem(isDivider = true))
    }
    
    // Open from file
    add(ContextMenuItem(
        text = "Open from File...",
        icon = Icons.Outlined.Upload,
        onClick = onShowOpenDialog
    ))
    
    add(ContextMenuItem(isDivider = true))
    
    // Save configuration - always ask for name
    add(ContextMenuItem(
        text = "Save Configuration...",
        icon = Icons.Outlined.Save,
        onClick = onShowSaveDialog
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
    
    // Top of mind option
    if (onShowTopOfMind != null) {
        add(ContextMenuItem(
            text = "Show Top of mind",
            icon = Icons.Outlined.Tab,
            onClick = onShowTopOfMind
        ))
        
        add(ContextMenuItem(isDivider = true))
    }
    
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

/**
 * Configuration button with dropdown menu
 */
@Composable
fun ConfigurationButton(
    onOpenConfiguration: (LayoutConfiguration) -> Unit,
    configurationManager: ConfigurationManager = remember { ConfigurationManager() },
    getCurrentConfiguration: (() -> LayoutConfiguration)? = null,
    onShowTopOfMind: (() -> Unit)? = null
) {
    val currentConfiguration by configurationManager.currentConfiguration.collectAsState()
    val configurations by configurationManager.configurations.collectAsState()
    
    var showSaveDialog by remember { mutableStateOf(false) }
    var showOpenDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    
    // Build context menu items using extracted helper function
    val contextMenuItems = buildConfigurationContextMenu(
        configurations = configurations,
        configurationManager = configurationManager,
        onOpenConfiguration = onOpenConfiguration,
        onShowSaveDialog = { showSaveDialog = true },
        onShowOpenDialog = { showOpenDialog = true },
        onShowDeleteDialog = { showDeleteDialog = true },
        onShowTopOfMind = onShowTopOfMind
    )
    
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
    
    // Delete dialog
    if (showDeleteDialog) {
        DeleteConfigurationDialog(
            configurations = configurations.filter { config ->
                !PredefinedConfigurations.allConfigurations.any { it.name == config.name }
            },
            onDismiss = { showDeleteDialog = false },
            onDelete = { configName ->
                configurationManager.deleteConfiguration(configName)
                showDeleteDialog = false
            }
        )
    }
}
