package ai.rever.boss.components.bars.horizontal

import BossDarkAccent
import BossDarkBorder
import ai.rever.boss.components.buttons.BossActionButton
import ai.rever.boss.components.overlays.ContextMenuItem
import ai.rever.boss.components.overlays.contextMenu
import ai.rever.boss.components.plugin.panels.left_top.ProjectState
import ai.rever.boss.components.plugin.panels.left_top.Project
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Divider
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.outlined.Logout
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
import ai.rever.boss.components.model.Panel.Companion.left
import ai.rever.boss.components.model.Panel.Companion.top
import ai.rever.boss.components.configuration.ConfigurationButton
import ai.rever.boss.components.configuration.ConfigurationManager
import ai.rever.boss.components.configuration.LayoutConfiguration
import ai.rever.boss.components.dialogs.LogoutConfirmationDialog
import ai.rever.boss.services.supabase.AuthService


@Composable
fun BossDraggableComponent.BossTopBar(
    configurationManager: ConfigurationManager? = null,
    onApplyConfiguration: ((LayoutConfiguration) -> Unit)? = null,
    getCurrentConfiguration: (() -> LayoutConfiguration)? = null,
    onShowTopOfMind: (() -> Unit)? = null
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
            BossTopLeftBar(configurationManager, onApplyConfiguration, getCurrentConfiguration, onShowTopOfMind)
            Spacer(modifier = Modifier.weight(1f))
            BossTopRunBar()
            Spacer(modifier = Modifier.weight(0.1f))
            BossTopRightBar()
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
            Text(text = name.substring(0, 2).uppercase(),
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
    showProjectDialog: () -> Unit
): List<ContextMenuItem> {
    val recentProjects by ProjectState.recentProjects.collectAsState()
    
    return buildList {
        // Recent projects
        addAll(recentProjects.map { project ->
            ContextMenuItem(
                text = project.name,
                icon = Icons.Outlined.Folder,
                onClick = { 
                    ProjectState.selectProject(project)
                    // Show CodeBase panel when project is selected
                    setPanelVisible(left.top, true)
                }
            )
        })
        
        if (recentProjects.isNotEmpty()) {
            add(ContextMenuItem(isDivider = true))
        }
        
        // Add option to open a new project
        add(ContextMenuItem(
            text = "Open Project...",
            icon = Icons.Filled.Add,
            onClick = showProjectDialog
        ))
    }
}

val gitContextMenuItems get() = listOf(
    ContextMenuItem(
        text = "dev",
        onClick = { /* Handle branch 1 action */ }
    )
)

@Composable
fun BossDraggableComponent.BossTopLeftBar(
    configurationManager: ConfigurationManager? = null,
    onApplyConfiguration: ((LayoutConfiguration) -> Unit)? = null,
    getCurrentConfiguration: (() -> LayoutConfiguration)? = null,
    onShowTopOfMind: (() -> Unit)? = null
) {
    val selectedProject by ProjectState.selectedProject.collectAsState()
    var showProjectDialog by remember { mutableStateOf(false) }
    
    BossActionButtonWithLogo(
        text = selectedProject.name, 
        contextMenuItems = getProjectSelectContextMenuItems(
            showProjectDialog = { showProjectDialog = true }
        ),
        hintText = "Current Project: ${selectedProject.path}"
    )
    BossActionButton(
        leftIcon = FeatherIcons.GitBranch, 
        text = "main",
        contextMenuItems = gitContextMenuItems,
        hintText = "Current Git Branch: main"
    )
    
    // Configuration button
    if (configurationManager != null && onApplyConfiguration != null) {
        ConfigurationButton(
            onOpenConfiguration = onApplyConfiguration,
            configurationManager = configurationManager,
            getCurrentConfiguration = getCurrentConfiguration,
            onShowTopOfMind = onShowTopOfMind
        )
    }
    
    // Directory picker for native file selection
    val directoryPicker = rememberDirectoryPicker { path ->
        path?.let {
            val projectName = it.substringAfterLast('/').ifEmpty { "Unknown" }
            ProjectState.selectProject(
                Project(
                    name = projectName,
                    path = it
                )
            )
            // Show CodeBase panel when project is selected
            setPanelVisible(left.top, true)
            // Close the dialog after selection
            showProjectDialog = false
        }
    }
    
    // Project selection dialog
    if (showProjectDialog) {
        ProjectSelectionDialog(
            onDismiss = { showProjectDialog = false },
            onOpenDirectoryPicker = {
                showProjectDialog = false
                directoryPicker.pickDirectory()
            }
        )
    }
}

val lanagerContextMenuItems get() = listOf(
    ContextMenuItem(
        text = "Start Lanager",
        icon = Icons.Outlined.PlayArrow,
        onClick = { /* Handle start lanager action */ }
    ),
    ContextMenuItem(
        text = "View Agents",
        icon = Icons.Outlined.People,
        onClick = { /* Handle view agents action */ }
    ),
    ContextMenuItem(isDivider = true),
    ContextMenuItem(
        text = "Configure Lanager",
        icon = Icons.Outlined.Settings,
        onClick = { /* Handle configure action */ }
    ),
    ContextMenuItem(isDivider = true),
    ContextMenuItem(
        text = "Restart Lanager",
        icon = Icons.Outlined.Refresh,
        onClick = { /* Handle restart action */ }
    ),
    ContextMenuItem(
        text = "Stop Lanager",
        icon = Icons.Outlined.Stop,
        onClick = { /* Handle stop action */ }
    )
)

@Composable
fun BossTopRunBar() {
    BossActionButton(
        leftIcon = Icons.Outlined.Diversity2,
        text = "lanager [boss]",
        contextMenuItems = lanagerContextMenuItems,
        hintText = "Lanager: Manage AI agent swarm for collaborative tasks"
    )
    
    BossActionButton(
        imageVector = Icons.Outlined.PlayArrow,
        text = "Run",
        hintText = "Run the current configuration"
    ) {}
    
    BossActionButton(
        imageVector = Icons.Outlined.BugReport,
        text = "Bug",
        hintText = "Debug the current execution"
    ) {}
    
    BossActionButton(
        imageVector = Icons.Outlined.Stop,
        text = "Stop",
        hintText = "Stop all running processes"
    ) {}
    
    BossActionButton(
        imageVector = Icons.Outlined.MoreVert,
        text = "More",
        hintText = "Additional actions and settings"
    ) {}
}

@Composable
fun BossTopRightBar() {
    var showSettingsDialog by remember { mutableStateOf(false) }
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
        imageVector = Icons.Outlined.Logout,
        text = "Sign Out",
        hintText = "Sign out of your account"
    ) {
        showLogoutDialog = true
    }
    
    BossActionButton(
        imageVector = Icons.Outlined.Search,
        text = "Search",
        hintText = "Search for files, commands, or actions"
    ) {}
    
    BossActionButton(
        imageVector = Icons.Outlined.Settings,
        text = "Settings",
        hintText = "Configure application settings"
    ) {
        showSettingsDialog = true
    }
    
    // Logout confirmation dialog
    if (showLogoutDialog) {
        LogoutConfirmationDialog(
            onDismiss = { showLogoutDialog = false }
        )
    }
    
    // Settings Window
    if (showSettingsDialog) {
        SettingsWindow(
            onClose = { showSettingsDialog = false }
        )
    }
}