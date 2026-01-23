package ai.rever.boss.components.plugin.panels.bottom.git

import BossDarkAccent
import BossDarkBackground
import BossDarkBorder
import BossDarkTextPrimary
import BossDarkTextSecondary
import ai.rever.boss.components.model.Panel.Companion.bottom
import ai.rever.boss.components.model.Panel.Companion.left
import ai.rever.boss.components.plugin.DefaultPlugin
import ai.rever.boss.components.registery.PanelComponentWithUI
import ai.rever.boss.components.registery.PanelId
import ai.rever.boss.components.registery.PanelInfo
import ai.rever.boss.git.GitCommitInfo
import ai.rever.boss.git.GitOperationResult
import ai.rever.boss.git.GitService
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboard
import ai.rever.boss.utils.createTextClipEntry
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arkivanov.decompose.ComponentContext
import compose.icons.FeatherIcons
import compose.icons.feathericons.GitBranch
import compose.icons.feathericons.GitCommit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * Git Log panel info
 * Displays commit history with graph visualization
 */
object GitLogInfo : PanelInfo {
    override val id = PanelId("git-log", 15) // After Git Status (14), before Console (16)
    override val displayName = "Git Log"
    override val icon = FeatherIcons.GitBranch
    override val defaultSlotPosition = left.bottom
}

/**
 * Git Log panel component
 * Shows commit history
 */
class GitLogComponent(
    ctx: ComponentContext,
    override val panelInfo: PanelInfo
) : PanelComponentWithUI, ComponentContext by ctx {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    @Composable
    override fun Content() {
        GitLogView(scope)
    }
}

@Composable
private fun GitLogView(scope: CoroutineScope) {
    val commitLog by GitService.commitLog.collectAsState()
    val isGitRepository by GitService.isGitRepository.collectAsState()
    val isLoading by GitService.isLoading.collectAsState()
    var expandedCommit by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }

    // Refresh log when panel opens
    LaunchedEffect(Unit) {
        GitService.getLog()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BossDarkBackground)
    ) {
        // Toolbar
        GitLogToolbar(
            scope = scope,
            isLoading = isLoading,
            commitCount = commitLog.size
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
        } else if (commitLog.isEmpty() && !isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No commits yet",
                    color = BossDarkTextSecondary,
                    fontSize = 14.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                items(commitLog, key = { it.hash }) { commit ->
                    CommitRow(
                        commit = commit,
                        isExpanded = expandedCommit == commit.hash,
                        onClick = {
                            expandedCommit = if (expandedCommit == commit.hash) null else commit.hash
                        },
                        scope = scope,
                        onError = { errorMessage = it },
                        onSuccess = { successMessage = it }
                    )
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

    // Messages
    (errorMessage ?: successMessage)?.let { msg ->
        val isError = errorMessage != null
        LaunchedEffect(msg) {
            kotlinx.coroutines.delay(3000)
            errorMessage = null
            successMessage = null
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                color = if (isError) Color(0xFFB00020) else Color(0xFF2E7D32),
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
private fun GitLogToolbar(
    scope: CoroutineScope,
    isLoading: Boolean,
    commitCount: Int
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
                text = "Commit History",
                color = BossDarkTextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            if (commitCount > 0) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "(${commitCount})",
                    color = BossDarkTextSecondary,
                    fontSize = 11.sp
                )
            }
        }

        IconButton(
            onClick = {
                scope.launch { GitService.getLog() }
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

@Composable
private fun CommitRow(
    commit: GitCommitInfo,
    isExpanded: Boolean,
    onClick: () -> Unit,
    scope: CoroutineScope,
    onError: (String) -> Unit,
    onSuccess: (String) -> Unit
) {
    val clipboard = LocalClipboard.current
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(if (isExpanded) BossDarkBackground.copy(alpha = 0.7f) else Color.Transparent)
    ) {
        // Main row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Commit hash
            Text(
                text = commit.shortHash,
                color = BossDarkAccent,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.width(62.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Subject
            Text(
                text = commit.subject,
                color = BossDarkTextPrimary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Refs (branches/tags)
            if (commit.refs.isNotEmpty()) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    commit.refs.take(2).forEach { ref ->
                        val (bgColor, textColor) = getRefColors(ref)
                        Surface(
                            color = bgColor,
                            shape = RoundedCornerShape(3.dp)
                        ) {
                            Text(
                                text = ref.removePrefix("HEAD -> "),
                                color = textColor,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
            }

            // Author
            Text(
                text = commit.author,
                color = BossDarkTextSecondary,
                fontSize = 11.sp,
                maxLines = 1,
                modifier = Modifier.widthIn(max = 100.dp),
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Date
            Text(
                text = dateFormat.format(Date(commit.date * 1000)),
                color = BossDarkTextSecondary,
                fontSize = 10.sp
            )
        }

        // Expanded details
        if (isExpanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 76.dp, end = 12.dp, bottom = 8.dp)
            ) {
                // Full hash
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Hash: ",
                        color = BossDarkTextSecondary,
                        fontSize = 11.sp
                    )
                    Text(
                        text = commit.hash,
                        color = BossDarkTextPrimary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    IconButton(
                        onClick = {
                            scope.launch {
                                clipboard.setClipEntry(createTextClipEntry(commit.hash))
                            }
                            onSuccess("Copied commit hash")
                        },
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ContentCopy,
                            contentDescription = "Copy hash",
                            tint = BossDarkTextSecondary,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }

                // Author email
                Text(
                    text = "Author: ${commit.author} <${commit.authorEmail}>",
                    color = BossDarkTextSecondary,
                    fontSize = 11.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Actions
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                val result = GitService.cherryPick(commit.hash)
                                when (result) {
                                    is GitOperationResult.Success -> onSuccess("Cherry-picked ${commit.shortHash}")
                                    is GitOperationResult.Error -> onError(result.message)
                                }
                            }
                        },
                        modifier = Modifier.height(24.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Text("Cherry-pick", fontSize = 10.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                val result = GitService.revert(commit.hash)
                                when (result) {
                                    is GitOperationResult.Success -> onSuccess("Reverted ${commit.shortHash}")
                                    is GitOperationResult.Error -> onError(result.message)
                                }
                            }
                        },
                        modifier = Modifier.height(24.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Text("Revert", fontSize = 10.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                val result = GitService.checkout(commit.hash)
                                when (result) {
                                    is GitOperationResult.Success -> onSuccess("Checked out ${commit.shortHash}")
                                    is GitOperationResult.Error -> onError(result.message)
                                }
                            }
                        },
                        modifier = Modifier.height(24.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Text("Checkout", fontSize = 10.sp)
                    }
                }
            }
        }

        Divider(color = BossDarkBorder.copy(alpha = 0.5f), thickness = 0.5.dp)
    }
}

private fun getRefColors(ref: String): Pair<Color, Color> {
    return when {
        ref.contains("HEAD") -> Color(0xFF6B9BFA) to Color.White
        ref.startsWith("tag:") -> Color(0xFFFDD663) to Color.Black
        ref.startsWith("origin/") -> Color(0xFF9AA0A6) to Color.White
        else -> Color(0xFF73C991) to Color.White
    }
}

/**
 * Register Git Log panel with the plugin system (desktop implementation)
 *
 * Dynamically registers/unregisters based on:
 * 1. A project being selected (path is not empty)
 * 2. The project being a Git repository (GitService.isGitRepository)
 *
 * Follows the SecretManagerPanel pattern for dynamic panel registration.
 */
actual fun DefaultPlugin.registerGitLog() {
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
                    panelRegistry.registerPanel(GitLogInfo) { ctx, panelInfo ->
                        GitLogComponent(ctx, panelInfo)
                    }
                } else {
                    panelRegistry.unregisterPanel(GitLogInfo.id)
                }
            }
    }
}
