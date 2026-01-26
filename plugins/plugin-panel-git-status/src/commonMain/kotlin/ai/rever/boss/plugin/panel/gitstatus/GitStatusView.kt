package ai.rever.boss.plugin.panel.gitstatus

import ai.rever.boss.plugin.api.GitFileStatusData
import ai.rever.boss.plugin.api.GitFileStatusTypeData
import ai.rever.boss.plugin.scrollbar.getPanelScrollbarConfig
import ai.rever.boss.plugin.scrollbar.lazyListScrollbar
import ai.rever.boss.plugin.ui.BossDarkAccent
import ai.rever.boss.plugin.ui.BossDarkBackground
import ai.rever.boss.plugin.ui.BossDarkBorder
import ai.rever.boss.plugin.ui.BossDarkTextPrimary
import ai.rever.boss.plugin.ui.BossDarkTextSecondary
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun GitStatusView(
    viewModel: GitStatusViewModel,
    windowId: String?
) {
    val fileStatus by viewModel.fileStatus.collectAsState()
    val isGitRepository by viewModel.isGitRepository.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val listState = rememberLazyListState()

    // Refresh status when panel opens or when git repository status changes
    LaunchedEffect(isGitRepository) {
        if (isGitRepository) {
            viewModel.refreshStatus()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BossDarkBackground)
    ) {
        // Toolbar
        GitStatusToolbar(
            viewModel = viewModel,
            fileStatus = fileStatus,
            isLoading = isLoading
        )

        Divider(color = BossDarkBorder, thickness = 1.dp)

        if (!isGitRepository) {
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
            val stagedFiles = fileStatus.filter { it.isStaged }
            val unstagedFiles = fileStatus.filter {
                it.isUnstaged || it.indexStatus == GitFileStatusTypeData.UNTRACKED
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .lazyListScrollbar(
                        listState = listState,
                        direction = Orientation.Vertical,
                        config = getPanelScrollbarConfig()
                    )
            ) {
                // Staged changes section
                if (stagedFiles.isNotEmpty()) {
                    item {
                        SectionHeader(
                            title = "Staged Changes",
                            count = stagedFiles.size,
                            onActionClick = { viewModel.unstageAll() },
                            actionIcon = Icons.Outlined.RemoveCircleOutline,
                            actionTooltip = "Unstage All"
                        )
                    }
                    items(stagedFiles, key = { "staged-${it.path}" }) { file ->
                        FileStatusRow(
                            file = file,
                            isStaged = true,
                            onStage = { viewModel.stage(file.path) },
                            onUnstage = { viewModel.unstage(file.path) },
                            onDiscard = { viewModel.discardChanges(file.path) },
                            onFileClick = {
                                windowId?.let { wid -> viewModel.openFile(file.path, wid) }
                            }
                        )
                    }
                }

                // Unstaged changes section
                if (unstagedFiles.isNotEmpty()) {
                    item {
                        SectionHeader(
                            title = "Changes",
                            count = unstagedFiles.size,
                            onActionClick = { viewModel.stageAll() },
                            actionIcon = Icons.Outlined.AddCircleOutline,
                            actionTooltip = "Stage All"
                        )
                    }
                    items(unstagedFiles, key = { "unstaged-${it.path}" }) { file ->
                        FileStatusRow(
                            file = file,
                            isStaged = false,
                            onStage = { viewModel.stage(file.path) },
                            onUnstage = { viewModel.unstage(file.path) },
                            onDiscard = { viewModel.discardChanges(file.path) },
                            onFileClick = {
                                windowId?.let { wid -> viewModel.openFile(file.path, wid) }
                            }
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
            viewModel.clearError()
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
    viewModel: GitStatusViewModel,
    fileStatus: List<GitFileStatusData>,
    isLoading: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
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

        IconButton(
            onClick = { viewModel.refreshStatus() },
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

@Composable
private fun SectionHeader(
    title: String,
    count: Int,
    onActionClick: () -> Unit,
    actionIcon: ImageVector,
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
    file: GitFileStatusData,
    isStaged: Boolean,
    onStage: () -> Unit,
    onUnstage: () -> Unit,
    onDiscard: () -> Unit,
    onFileClick: () -> Unit
) {
    val status = if (isStaged) file.indexStatus else file.workTreeStatus
    val statusChar = getStatusChar(status)
    val statusColor = getStatusColor(status)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onFileClick() }
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
                IconButton(
                    onClick = onUnstage,
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
                IconButton(
                    onClick = onStage,
                    modifier = Modifier.size(20.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = "Stage",
                        tint = BossDarkTextSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                }

                if (file.indexStatus != GitFileStatusTypeData.UNTRACKED) {
                    IconButton(
                        onClick = onDiscard,
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

private fun getStatusChar(status: GitFileStatusTypeData?): String {
    return when (status) {
        GitFileStatusTypeData.MODIFIED -> "M"
        GitFileStatusTypeData.ADDED -> "A"
        GitFileStatusTypeData.DELETED -> "D"
        GitFileStatusTypeData.RENAMED -> "R"
        GitFileStatusTypeData.COPIED -> "C"
        GitFileStatusTypeData.UNTRACKED -> "?"
        GitFileStatusTypeData.IGNORED -> "!"
        GitFileStatusTypeData.UNMERGED -> "U"
        null -> " "
    }
}

private fun getStatusColor(status: GitFileStatusTypeData?): Color {
    return when (status) {
        GitFileStatusTypeData.MODIFIED -> Color(0xFF6B9BFA)
        GitFileStatusTypeData.ADDED -> Color(0xFF73C991)
        GitFileStatusTypeData.DELETED -> Color(0xFFF28B82)
        GitFileStatusTypeData.RENAMED -> Color(0xFFFDD663)
        GitFileStatusTypeData.COPIED -> Color(0xFFFDD663)
        GitFileStatusTypeData.UNTRACKED -> Color(0xFF9AA0A6)
        GitFileStatusTypeData.IGNORED -> Color(0xFF5F6368)
        GitFileStatusTypeData.UNMERGED -> Color(0xFFFF6B6B)
        null -> Color.Transparent
    }
}
