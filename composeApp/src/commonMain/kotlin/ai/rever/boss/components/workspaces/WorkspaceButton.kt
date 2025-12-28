package ai.rever.boss.components.workspaces

import ai.rever.boss.components.buttons.BossActionButton
import ai.rever.boss.components.overlays.ContextMenuItem
import androidx.compose.foundation.layout.Box
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
import compose.icons.feathericons.Layout

/**
 * Platform-specific function to open workspace directory
 */
expect fun openWorkspaceDirectory(path: String)

/**
 * Workspace button with dropdown menu
 */
@Composable
fun WorkspaceButton(
    onOpenWorkspace: (LayoutWorkspace) -> Unit,
    workspaceManager: WorkspaceManager = remember { WorkspaceManager() },
    getCurrentWorkspace: (() -> LayoutWorkspace)? = null,
    onShowTopOfMind: (() -> Unit)? = null
) {
    val currentWorkspace by workspaceManager.currentWorkspace.collectAsState()
    val workspaces by workspaceManager.workspaces.collectAsState()

    var showSaveDialog by remember { mutableStateOf(false) }
    var showOpenDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    // Build context menu items
    val contextMenuItems = buildList {
        // Predefined workspaces
        workspaces.forEach { workspace ->
            add(ContextMenuItem(
                text = workspace.name,
                icon = null,
                onClick = {
                    workspaceManager.loadWorkspace(workspace)
                    onOpenWorkspace(workspace)
                }
            ))
        }

        add(ContextMenuItem(isDivider = true))

        // Delete workspace section
        val deletableWorkspaces = workspaces.filter { workspace ->
            !PredefinedWorkspaces.allWorkspaces.any { it.name == workspace.name }
        }

        if (deletableWorkspaces.isNotEmpty()) {
            add(ContextMenuItem(
                text = "Delete Workspace",
                icon = Icons.Outlined.Delete,
                onClick = {
                    showDeleteDialog = true
                }
            ))

            add(ContextMenuItem(isDivider = true))
        }

        // Open from file
        add(ContextMenuItem(
            text = "Open from File...",
            icon = Icons.Outlined.Upload,
            onClick = { showOpenDialog = true }
        ))

        add(ContextMenuItem(isDivider = true))

        // Save workspace - always ask for name
        add(ContextMenuItem(
            text = "Save Workspace...",
            icon = Icons.Outlined.Save,
            onClick = { showSaveDialog = true }
        ))

        add(ContextMenuItem(isDivider = true))

        // Open workspace directory
        add(ContextMenuItem(
            text = "Open Workspace Folder",
            icon = Icons.Outlined.FolderOpen,
            onClick = {
                // This will be platform-specific implementation
                openWorkspaceDirectory(workspaceManager.getWorkspaceDirectory())
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
                workspaceManager.resetToDefault()
                onOpenWorkspace(LayoutWorkspace(
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
                leftIcon = FeatherIcons.Layout,
                text = currentWorkspace?.let { workspace ->
                if (workspace.name != "Current") workspace.name else "Default"
            } ?: "Default",
                contextMenuItems = contextMenuItems,
                hintText = buildString {
                    append("Layout Workspace: ${currentWorkspace?.description ?: "Default layout"}")
                    append("\nWorkspaces saved to: ${workspaceManager.getWorkspaceDirectory()}")
                }
            )
        }
    }

    // Save dialog
    if (showSaveDialog) {
        SaveWorkspaceDialog(
            onDismiss = { showSaveDialog = false },
            onSave = { name ->
                // Get current layout and save it with the provided name
                getCurrentWorkspace?.invoke()?.let { currentLayout ->
                    workspaceManager.updateCurrentWorkspace(currentLayout)
                    workspaceManager.saveCurrentWorkspace(name)
                }
                showSaveDialog = false
            }
        )
    }

    // Open dialog
    if (showOpenDialog) {
        OpenWorkspaceDialog(
            onDismiss = { showOpenDialog = false },
            onOpen = { jsonString ->
                workspaceManager.importWorkspace(jsonString)?.let { workspace ->
                    workspaceManager.loadWorkspace(workspace)
                    onOpenWorkspace(workspace)
                }
                showOpenDialog = false
            }
        )
    }

    // Delete dialog
    if (showDeleteDialog) {
        DeleteWorkspaceDialog(
            workspaces = workspaces.filter { workspace ->
                !PredefinedWorkspaces.allWorkspaces.any { it.name == workspace.name }
            },
            onDismiss = { showDeleteDialog = false },
            onDelete = { workspaceName ->
                workspaceManager.deleteWorkspace(workspaceName)
                showDeleteDialog = false
            }
        )
    }
}
