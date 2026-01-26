package ai.rever.boss.plugin.panel.gitlog

import ai.rever.boss.plugin.api.GitCommitInfoData
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun GitLogView(viewModel: GitLogViewModel) {
    val commitLog by viewModel.commitLog.collectAsState()
    val isGitRepository by viewModel.isGitRepository.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val successMessage by viewModel.successMessage.collectAsState()
    var expandedCommit by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    @Suppress("DEPRECATION")
    val clipboardManager = LocalClipboardManager.current

    // Refresh log when panel opens or when git repository status changes
    LaunchedEffect(isGitRepository) {
        if (isGitRepository) {
            viewModel.refreshLog()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BossDarkBackground)
    ) {
        // Toolbar
        GitLogToolbar(
            viewModel = viewModel,
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
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .lazyListScrollbar(
                        listState = listState,
                        direction = Orientation.Vertical,
                        config = getPanelScrollbarConfig()
                    )
            ) {
                items(commitLog, key = { it.hash }) { commit ->
                    CommitRow(
                        commit = commit,
                        isExpanded = expandedCommit == commit.hash,
                        onClick = {
                            expandedCommit = if (expandedCommit == commit.hash) null else commit.hash
                        },
                        onCopyHash = {
                            clipboardManager.setText(AnnotatedString(commit.hash))
                            viewModel.showSuccess("Copied commit hash")
                        },
                        onCherryPick = { viewModel.cherryPick(commit.hash, commit.shortHash) },
                        onRevert = { viewModel.revert(commit.hash, commit.shortHash) },
                        onCheckout = { viewModel.checkout(commit.hash, commit.shortHash) }
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
    val msg = errorMessage ?: successMessage
    if (msg != null) {
        val isError = errorMessage != null
        LaunchedEffect(msg) {
            kotlinx.coroutines.delay(3000)
            viewModel.clearMessages()
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
    viewModel: GitLogViewModel,
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
                    text = "($commitCount)",
                    color = BossDarkTextSecondary,
                    fontSize = 11.sp
                )
            }
        }

        IconButton(
            onClick = { viewModel.refreshLog() },
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
    commit: GitCommitInfoData,
    isExpanded: Boolean,
    onClick: () -> Unit,
    onCopyHash: () -> Unit,
    onCherryPick: () -> Unit,
    onRevert: () -> Unit,
    onCheckout: () -> Unit
) {
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
                        onClick = onCopyHash,
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
                        onClick = onCherryPick,
                        modifier = Modifier.height(24.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Text("Cherry-pick", fontSize = 10.sp)
                    }

                    OutlinedButton(
                        onClick = onRevert,
                        modifier = Modifier.height(24.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Text("Revert", fontSize = 10.sp)
                    }

                    OutlinedButton(
                        onClick = onCheckout,
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
