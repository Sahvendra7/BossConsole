package ai.rever.boss.components.bars.horizontal

import BossDarkAccent
import BossDarkBorder
import ai.rever.boss.components.buttons.BossActionButton
import ai.rever.boss.components.overlays.ContextMenuItem
import ai.rever.boss.components.overlays.contextMenu
import ai.rever.boss.components.plugin.panels.left_top.ProjectState
import ai.rever.boss.components.plugin.panels.left_top.Project
import ai.rever.boss.window.LocalWindowProjectState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Divider
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import compose.icons.FeatherIcons
import compose.icons.feathericons.GitBranch
import ai.rever.boss.platform.rememberDirectoryPicker
import ai.rever.boss.components.dialogs.ProjectSelectionDialog
import ai.rever.boss.components.windows.SettingsWindow
import ai.rever.boss.components.model.BossDraggableComponent
import ai.rever.boss.components.workspaces.WorkspaceButton
import ai.rever.boss.components.workspaces.WorkspaceManager
import ai.rever.boss.components.workspaces.LayoutWorkspace
import ai.rever.boss.components.dialogs.LogoutConfirmationDialog
import ai.rever.boss.components.dialogs.ProjectOpenModeDialog
import ai.rever.boss.services.supabase.AuthService
import ai.rever.boss.components.events.PanelEventBus
import ai.rever.boss.components.plugin.panels.left_top.CodeBaseInfo
import ai.rever.boss.components.plugin.panels.left_bottom.RunConfigurationsInfo
import ai.rever.boss.window.WindowOperations
import kotlinx.coroutines.launch


@Composable
fun BossDraggableComponent.BossTopBar(
    workspaceManager: WorkspaceManager? = null,
    onApplyWorkspace: ((LayoutWorkspace) -> Unit)? = null,
    getCurrentWorkspace: (() -> LayoutWorkspace)? = null,
    onShowTopOfMind: (() -> Unit)? = null,
    onShowSettings: (() -> Unit)? = null,
    onNewProject: (() -> Unit)? = null
) {

    val items = listOf(
        ContextMenuItem(
            text = "Edit",
            icon = Icons.Outlined.Edit,
            onClick = { /* Handle edit action */ }
        ),
        ContextMenuItem(isDivider = true),
        ContextMenuItem(
            text = "Save",
            icon = Icons.Outlined.Save,
            onClick = { /* Handle save action */ }
        )
    )


    HorizontalBar(modifier = Modifier.contextMenu(items = items), height = 40.dp) {
        HorizontalBarRow(modifier = Modifier.fillMaxHeight().padding(start = 36.dp)) {
            BossTopLeftBar(workspaceManager, onApplyWorkspace, getCurrentWorkspace, onShowTopOfMind, onNewProject)
            Spacer(modifier = Modifier.weight(1f))
            // Run/debug controls (Issue #91 / #321)
            BossTopRunBar()
            Spacer(modifier = Modifier.weight(0.1f))
            BossTopRightBar(onShowSettings = onShowSettings)
        }
    }
    Divider(color = BossDarkBorder)
}

@Composable
fun Logo(name: String) {
    Surface(
        modifier = Modifier
            .padding(2.dp)
            .height(22.dp)
            .width(22.dp)
        ,
        shape = RoundedCornerShape(4.dp),
        color = BossDarkAccent,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Handle names with < 2 characters gracefully
            val initials = when {
                name.length >= 2 -> name.substring(0, 2)
                name.isNotEmpty() -> name[0].toString()
                else -> "?"  // Fallback for empty names
            }
            Text(text = initials.uppercase(),
                fontSize = 11.sp,
                modifier = Modifier
                    .align(Alignment.Center)
            )
        }
    }
}

@Composable
fun BossActionButtonWithLogo(
    text: String, 
    contextMenuItems: List<ContextMenuItem>,
    hintText: String? = null,
    onClick: () -> Unit = {}
) {
    BossActionButton(
        leftLogo = { Logo(text) },
        text = text,
        contextMenuItems = contextMenuItems,
        hintText = hintText,
        onClick = onClick
    )
}

@Composable
fun BossDraggableComponent.getProjectSelectContextMenuItems(
    showProjectDialog: () -> Unit,
    showNewProjectDialog: () -> Unit,
    onProjectSelected: (Project) -> Unit
): List<ContextMenuItem> {
    val recentProjects by ProjectState.recentProjects.collectAsState()

    return buildList {
        // Recent projects with remove button
        addAll(recentProjects.map { project ->
            ContextMenuItem(
                text = project.name,
                icon = Icons.Outlined.Folder,
                trailingIcon = Icons.Outlined.Close,
                trailingIconColor = androidx.compose.ui.graphics.Color.Gray,
                onTrailingClick = { ProjectState.removeRecentProject(project.path) },
                onClick = { onProjectSelected(project) }
            )
        })

        if (recentProjects.isNotEmpty()) {
            add(ContextMenuItem(isDivider = true))
        }

        // Add option to create a new project
        add(ContextMenuItem(
            text = "New Project...",
            icon = Icons.Outlined.CreateNewFolder,
            onClick = showNewProjectDialog
        ))

        // Add option to open an existing project
        add(ContextMenuItem(
            text = "Open Project...",
            icon = Icons.Filled.Add,
            onClick = showProjectDialog
        ))
    }
}

// TODO: #90 - Git integration helper (currently disabled)
// See https://github.com/risa-labs-inc/BOSS-Kotlin/issues/90
// val gitContextMenuItems get() = listOf(
//     ContextMenuItem(
//         text = "dev",
//         onClick = { /* Handle branch 1 action */ }
//     )
// )

@Composable
fun BossDraggableComponent.BossTopLeftBar(
    workspaceManager: WorkspaceManager? = null,
    onApplyWorkspace: ((LayoutWorkspace) -> Unit)? = null,
    getCurrentWorkspace: (() -> LayoutWorkspace)? = null,
    onShowTopOfMind: (() -> Unit)? = null,
    onNewProject: (() -> Unit)? = null
) {
    // Use per-window project state for independent project per window
    val windowProjectState = LocalWindowProjectState.current
    val selectedProject by windowProjectState?.selectedProject?.collectAsState()
        ?: ProjectState.selectedProject.collectAsState() // Fallback to global if not provided
    var showProjectDialog by remember { mutableStateOf(false) }
    var projectToOpen by remember { mutableStateOf<Project?>(null) }
    val scope = rememberCoroutineScope()

    // Helper function to open project in current window
    fun openProjectInCurrentWindow(project: Project) {
        // Use window-specific project state if available, otherwise fallback to global
        windowProjectState?.selectProject(project) ?: ProjectState.selectProject(project)
        // Show CodeBase and Run Configurations panels when project is selected
        scope.launch {
            PanelEventBus.openPanel(CodeBaseInfo.id)
            PanelEventBus.openPanel(RunConfigurationsInfo.id)
        }
    }

    BossActionButtonWithLogo(
        text = if (selectedProject.path.isEmpty()) "Open Project" else selectedProject.name,
        contextMenuItems = getProjectSelectContextMenuItems(
            showProjectDialog = { showProjectDialog = true },
            showNewProjectDialog = { onNewProject?.invoke() },
            onProjectSelected = { project ->
                // Only show dialog if a project is already selected
                if (selectedProject.path.isNotEmpty()) {
                    projectToOpen = project
                } else {
                    // No project selected, open directly in current window
                    openProjectInCurrentWindow(project)
                }
            }
        ),
        hintText = if (selectedProject.path.isEmpty()) "Click to open a project" else "Current Project: ${selectedProject.path}"
    )
    // TODO: #90 - Implement Git integration
    // See https://github.com/risa-labs-inc/BOSS-Kotlin/issues/90
    // BossActionButton(
    //     leftIcon = FeatherIcons.GitBranch,
    //     text = "main",
    //     contextMenuItems = gitContextMenuItems,
    //     hintText = "Current Git Branch: main"
    // )

    // Workspace button
    if (workspaceManager != null && onApplyWorkspace != null) {
        WorkspaceButton(
            onOpenWorkspace = onApplyWorkspace,
            workspaceManager = workspaceManager,
            getCurrentWorkspace = getCurrentWorkspace,
            onShowTopOfMind = onShowTopOfMind
        )
    }

    // Directory picker for native file selection
    val directoryPicker = rememberDirectoryPicker { path ->
        path?.let {
            val projectName = it.substringAfterLast('/').ifEmpty { "Unknown" }
            val project = Project(name = projectName, path = it)
            // Close the selection dialog
            showProjectDialog = false
            // Only show dialog if a project is already selected
            if (selectedProject.path.isNotEmpty()) {
                projectToOpen = project
            } else {
                // No project selected, open directly in current window
                openProjectInCurrentWindow(project)
            }
        }
    }

    // Project selection dialog
    // Note: Dialog handles empty recentProjects case internally by opening directory picker directly
    if (showProjectDialog) {
        ProjectSelectionDialog(
            onDismiss = { showProjectDialog = false },
            onOpenDirectoryPicker = {
                showProjectDialog = false
                directoryPicker.pickDirectory()
            }
        )
    }

    // Project open mode dialog
    projectToOpen?.let { project ->
        ProjectOpenModeDialog(
            project = project,
            onDismiss = { projectToOpen = null },
            onOpenInCurrentWindow = { selectedProj ->
                openProjectInCurrentWindow(selectedProj)
                projectToOpen = null
            },
            onOpenInNewWindow = { selectedProj ->
                // Create new window with the project - each window has independent project state
                WindowOperations.createNewWindowWithProject(selectedProj)
                projectToOpen = null
            }
        )
    }
}

// TODO: #93 - Lanager functionality (currently disabled - plugin removed)
// See https://github.com/risa-labs-inc/BOSS-Kotlin/issues/93
// val lanagerContextMenuItems get() = listOf(
//     ContextMenuItem(
//         text = "Start Lanager",
//         icon = Icons.Outlined.PlayArrow,
//         onClick = { /* Handle start lanager action */ }
//     ),
//     ContextMenuItem(
//         text = "View Agents",
//         icon = Icons.Outlined.People,
//         onClick = { /* Handle view agents action */ }
//     ),
//     ContextMenuItem(isDivider = true),
//     ContextMenuItem(
//         text = "Configure Lanager",
//         icon = Icons.Outlined.Settings,
//         onClick = { /* Handle configure action */ }
//     ),
//     ContextMenuItem(isDivider = true),
//     ContextMenuItem(
//         text = "Restart Lanager",
//         icon = Icons.Outlined.Refresh,
//         onClick = { /* Handle restart action */ }
//     ),
//     ContextMenuItem(
//         text = "Stop Lanager",
//         icon = Icons.Outlined.Stop,
//         onClick = { /* Handle stop action */ }
//     )
// )

// TODO: #91 - BossTopRunBar function (currently disabled)
// See https://github.com/risa-labs-inc/BOSS-Kotlin/issues/91
// @Composable
// fun BossTopRunBar() {
//     BossActionButton(
//         leftIcon = Icons.Outlined.Diversity2,
//         text = "lanager [boss]",
//         contextMenuItems = lanagerContextMenuItems,
//         hintText = "Lanager: Manage AI agent swarm for collaborative tasks"
//     )
//
//     BossActionButton(
//         imageVector = Icons.Outlined.PlayArrow,
//         text = "Run",
//         hintText = "Run the current workspace"
//     ) {}
//
//     BossActionButton(
//         imageVector = Icons.Outlined.BugReport,
//         text = "Bug",
//         hintText = "Debug the current execution"
//     ) {}
//
//     BossActionButton(
//         imageVector = Icons.Outlined.Stop,
//         text = "Stop",
//         hintText = "Stop all running processes"
//     ) {}
//
//     BossActionButton(
//         imageVector = Icons.Outlined.MoreVert,
//         text = "More",
//         hintText = "Additional actions and settings"
//     ) {}
// }

@Composable
fun BossTopRightBar(
    onShowSettings: (() -> Unit)? = null
) {
    var showLogoutDialog by remember { mutableStateOf(false) }
    val currentUser by AuthService.currentUser.collectAsState()
    
    // Show user email if logged in
    currentUser?.let { user ->
        Text(
            text = user.email,
            style = androidx.compose.material.MaterialTheme.typography.caption,
            modifier = Modifier.padding(end = 8.dp),
            color = androidx.compose.ui.graphics.Color.Gray
        )
    }
    
    BossActionButton(
        imageVector = Icons.AutoMirrored.Outlined.Logout,
        text = "Sign Out",
        hintText = "Sign out of your account"
    ) {
        showLogoutDialog = true
    }
    
    // TODO: #92 - Implement global search functionality
    // See https://github.com/risa-labs-inc/BOSS-Kotlin/issues/92
    // BossActionButton(
    //     imageVector = Icons.Outlined.Search,
    //     text = "Search",
    //     hintText = "Search for files, commands, or actions"
    // ) {}

    BossActionButton(
        imageVector = Icons.Outlined.Settings,
        text = "Settings",
        hintText = "Configure application settings"
    ) {
        onShowSettings?.invoke()
    }
    
    // Logout confirmation dialog
    if (showLogoutDialog) {
        LogoutConfirmationDialog(
            onDismiss = { showLogoutDialog = false }
        )
    }
}
