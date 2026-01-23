package ai.rever.boss.components.plugin.panels.bottom.git

import BossDarkAccent
import BossDarkBackground
import BossDarkBorder
import BossDarkTextPrimary
import BossDarkTextSecondary
import ai.rever.boss.components.events.FileEventBus
import ai.rever.boss.window.LocalWindowId
import ai.rever.boss.components.model.Panel.Companion.bottom
import ai.rever.boss.components.model.Panel.Companion.left
import ai.rever.boss.components.plugin.DefaultPlugin
import ai.rever.boss.components.registery.PanelComponentWithUI
import ai.rever.boss.components.registery.PanelId
import ai.rever.boss.components.registery.PanelInfo
import ai.rever.boss.git.GitFileStatus
import ai.rever.boss.git.GitFileStatusType
import ai.rever.boss.git.GitOperationResult
import ai.rever.boss.git.GitService
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arkivanov.decompose.ComponentContext
import compose.icons.FeatherIcons
import compose.icons.feathericons.GitCommit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Git Status panel info
 * Displays changed, staged, and untracked files with staging controls
 */
object GitStatusInfo : PanelInfo {
    override val id = PanelId("git-status", 14) // After Terminal (13), before Git Log (15)
    override val displayName = "Git Changes"
    override val icon = FeatherIcons.GitCommit
    override val defaultSlotPosition = left.bottom
}

/**
 * Git Status panel component
 * Shows file changes and provides staging controls
 */
class GitStatusComponent(
    ctx: ComponentContext,
    override val panelInfo: PanelInfo
) : PanelComponentWithUI, ComponentContext by ctx {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    @Composable
    override fun Content() {
        GitStatusView(scope)
    }
}

@Composable
private fun GitStatusView(scope: CoroutineScope) {
    val fileStatus by GitService.fileStatus.collectAsState()
    val isGitRepository by GitService.isGitRepository.collectAsState()
    val isLoading by GitService.isLoading.collectAsState()
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val windowId = LocalWindowId.current

    // Refresh status when panel opens
    LaunchedEffect(Unit) {
        GitService.getStatus()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BossDarkBackground)
    ) {
        // Toolbar
        GitStatusToolbar(
            scope = scope,
            fileStatus = fileStatus,
            isLoading = isLoading,
            onError = { errorMessage = it }
        )

        Divider(color = BossDarkBorder, thickness = 1.dp)

        if (!isGitRepository) {
            // Not a Git repository
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Not a Git repository",
                    color = BossDarkTextSecondary,
                    fontSize = 14.sp
                )
            }
        } else if (fileStatus.isEmpty() && !isLoading) {
            // No changes
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No changes",
                    color = BossDarkTextSecondary,
                    fontSize = 14.sp
                )
            }
        } else {
            // File list
            val stagedFiles = fileStatus.filter { it.isStaged }
            val unstagedFiles = fileStatus.filter { it.isUnstaged || it.indexStatus == GitFileStatusType.UNTRACKED }

            // Handler to open file in editor (window-scoped for multi-window support)
            val openFileInEditor: (String) -> Unit = { relativePath ->
                val projectPath = GitService.getCurrentProjectPath()
                if (projectPath != null && windowId != null) {
                    val fullPath = "$projectPath/$relativePath"
                    scope.launch {
                        FileEventBus.openFile(fullPath, sourceWindowId = windowId)
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                // Staged changes section
                if (stagedFiles.isNotEmpty()) {
                    item {
                        SectionHeader(
                            title = "Staged Changes",
                            count = stagedFiles.size,
                            onActionClick = {
                                scope.launch {
                                    val result = GitService.unstageAll()
                                    if (result is GitOperationResult.Error) {
                                        errorMessage = result.message
                                    }
                                }
                            },
                            actionIcon = Icons.Outlined.RemoveCircleOutline,
                            actionTooltip = "Unstage All"
                        )
                    }
                    items(stagedFiles, key = { "staged-${it.path}" }) { file ->
                        FileStatusRow(
                            file = file,
                            isStaged = true,
                            scope = scope,
                            onError = { errorMessage = it },
                            onFileClick = openFileInEditor
                        )
                    }
                }

                // Unstaged changes section
                if (unstagedFiles.isNotEmpty()) {
                    item {
                        SectionHeader(
                            title = "Changes",
                            count = unstagedFiles.size,
                            onActionClick = {
                                scope.launch {
                                    val result = GitService.stageAll()
                                    if (result is GitOperationResult.Error) {
                                        errorMessage = result.message
                                    }
                                }
                            },
                            actionIcon = Icons.Outlined.AddCircleOutline,
                            actionTooltip = "Stage All"
                        )
                    }
                    items(unstagedFiles, key = { "unstaged-${it.path}" }) { file ->
                        FileStatusRow(
                            file = file,
                            isStaged = false,
                            scope = scope,
                            onError = { errorMessage = it },
                            onFileClick = openFileInEditor
                        )
                    }
                }
            }
        }

        // Loading indicator
        if (isLoading) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = BossDarkAccent
            )
        }
    }

    // Error snackbar
    errorMessage?.let { msg ->
        LaunchedEffect(msg) {
            kotlinx.coroutines.delay(3000)
            errorMessage = null
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                color = Color(0xFFB00020),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = msg,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun GitStatusToolbar(
    scope: CoroutineScope,
    fileStatus: List<GitFileStatus>,
    isLoading: Boolean,
    onError: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left side - title and count
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Changes",
                color = BossDarkTextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            if (fileStatus.isNotEmpty()) {
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    color = BossDarkAccent,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = "${fileStatus.size}",
                        color = BossDarkTextPrimary,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }

        // Right side - actions
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = {
                    scope.launch { GitService.getStatus() }
                },
                enabled = !isLoading,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Refresh,
                    contentDescription = "Refresh",
                    tint = BossDarkTextSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    count: Int,
    onActionClick: () -> Unit,
    actionIcon: androidx.compose.ui.graphics.vector.ImageVector,
    actionTooltip: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BossDarkBackground.copy(alpha = 0.5f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                color = BossDarkTextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "($count)",
                color = BossDarkTextSecondary.copy(alpha = 0.7f),
                fontSize = 11.sp
            )
        }
        IconButton(
            onClick = onActionClick,
            modifier = Modifier.size(20.dp)
        ) {
            Icon(
                imageVector = actionIcon,
                contentDescription = actionTooltip,
                tint = BossDarkTextSecondary,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
private fun FileStatusRow(
    file: GitFileStatus,
    isStaged: Boolean,
    scope: CoroutineScope,
    onError: (String) -> Unit,
    onFileClick: (String) -> Unit
) {
    val statusChar = getStatusChar(if (isStaged) file.indexStatus else file.workTreeStatus)
    val statusColor = getStatusColor(if (isStaged) file.indexStatus else file.workTreeStatus)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onFileClick(file.path) }
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Status indicator
        Text(
            text = statusChar,
            color = statusColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(16.dp)
        )

        Spacer(modifier = Modifier.width(8.dp))

        // File path
        Text(
            text = file.path,
            color = BossDarkTextPrimary,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        // Action buttons
        Row {
            if (isStaged) {
                // Unstage button
                IconButton(
                    onClick = {
                        scope.launch {
                            val result = GitService.unstage(file.path)
                            if (result is GitOperationResult.Error) {
                                onError(result.message)
                            }
                        }
                    },
                    modifier = Modifier.size(20.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Remove,
                        contentDescription = "Unstage",
                        tint = BossDarkTextSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            } else {
                // Stage button
                IconButton(
                    onClick = {
                        scope.launch {
                            val result = GitService.stage(file.path)
                            if (result is GitOperationResult.Error) {
                                onError(result.message)
                            }
                        }
                    },
                    modifier = Modifier.size(20.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = "Stage",
                        tint = BossDarkTextSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                }

                // Discard button (only for tracked files)
                if (file.indexStatus != GitFileStatusType.UNTRACKED) {
                    IconButton(
                        onClick = {
                            scope.launch {
                                val result = GitService.discardChanges(file.path)
                                if (result is GitOperationResult.Error) {
                                    onError(result.message)
                                }
                            }
                        },
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.Undo,
                            contentDescription = "Discard Changes",
                            tint = BossDarkTextSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun getStatusChar(status: GitFileStatusType?): String {
    return when (status) {
        GitFileStatusType.MODIFIED -> "M"
        GitFileStatusType.ADDED -> "A"
        GitFileStatusType.DELETED -> "D"
        GitFileStatusType.RENAMED -> "R"
        GitFileStatusType.COPIED -> "C"
        GitFileStatusType.UNTRACKED -> "?"
        GitFileStatusType.IGNORED -> "!"
        GitFileStatusType.UNMERGED -> "U"
        null -> " "
    }
}

private fun getStatusColor(status: GitFileStatusType?): Color {
    return when (status) {
        GitFileStatusType.MODIFIED -> Color(0xFF6B9BFA) // Blue
        GitFileStatusType.ADDED -> Color(0xFF73C991) // Green
        GitFileStatusType.DELETED -> Color(0xFFF28B82) // Red
        GitFileStatusType.RENAMED -> Color(0xFFFDD663) // Yellow
        GitFileStatusType.COPIED -> Color(0xFFFDD663) // Yellow
        GitFileStatusType.UNTRACKED -> Color(0xFF9AA0A6) // Gray
        GitFileStatusType.IGNORED -> Color(0xFF5F6368) // Dark gray
        GitFileStatusType.UNMERGED -> Color(0xFFFF6B6B) // Bright red
        null -> Color.Transparent
    }
}

/**
 * Register Git Status panel with the plugin system (desktop implementation)
 *
 * Dynamically registers/unregisters based on:
 * 1. A project being selected (path is not empty)
 * 2. The project being a Git repository (GitService.isGitRepository)
 *
 * Follows the SecretManagerPanel pattern for dynamic panel registration.
 */
actual fun DefaultPlugin.registerGitStatus() {
    val projectState = windowProjectState ?: return

    pluginScope.launch(Dispatchers.Main) {
        combine(
            projectState.selectedProject,
            GitService.isGitRepository
        ) { project, isGitRepo ->
            // Show panel only when a project is selected AND it's a git repository
            project.path.isNotEmpty() && isGitRepo
        }
            .distinctUntilChanged()
            .collect { shouldShow ->
                if (shouldShow) {
                    panelRegistry.registerPanel(GitStatusInfo) { ctx, panelInfo ->
                        GitStatusComponent(ctx, panelInfo)
                    }
                } else {
                    panelRegistry.unregisterPanel(GitStatusInfo.id)
                }
            }
    }
}
