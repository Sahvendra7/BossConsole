package ai.rever.boss.components.plugin.tab_types.fluck

import ai.rever.boss.platform.FileSystemUtils
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Download panel UI component that displays active and recent downloads.
 * Located below the bookmarks bar in the Fluck browser.
 */
@Composable
fun DownloadPanel(downloadManager: DownloadManager) {
    val downloads by downloadManager.downloads.collectAsState()
    var isExpanded by remember { mutableStateOf(true) }

    // Only show panel if there are downloads
    if (downloads.isNotEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF2D2D2D))
        ) {
            // Header with controls
            DownloadPanelHeader(
                activeCount = downloads.count { it.isActive },
                completedCount = downloads.count { it.status == DownloadStatus.COMPLETED },
                isExpanded = isExpanded,
                onToggleExpanded = { isExpanded = !isExpanded },
                onClearCompleted = {
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch {
                        downloadManager.clearCompleted()
                    }
                }
            )

            // Downloads list
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                ) {
                    items(downloads, key = { it.id }) { download ->
                        DownloadItemRow(download, downloadManager)
                        Divider(color = Color(0xFF3D3D3D), thickness = 1.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadPanelHeader(
    activeCount: Int,
    completedCount: Int,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit,
    onClearCompleted: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Downloads icon and title
        Icon(
            imageVector = Icons.Default.Download,
            contentDescription = "Downloads",
            tint = Color.White,
            modifier = Modifier.size(20.dp)
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = "Downloads",
            color = Color.White,
            style = MaterialTheme.typography.subtitle2
        )

        // Active count badge
        if (activeCount > 0) {
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                color = Color(0xFF4CAF50),
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = "$activeCount active",
                    color = Color.White,
                    style = MaterialTheme.typography.caption,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Clear completed button
        if (completedCount > 0) {
            TextButton(onClick = onClearCompleted) {
                Text("Clear Completed", color = Color(0xFF90CAF9))
            }
            Spacer(modifier = Modifier.width(4.dp))
        }

        // Expand/collapse button
        IconButton(onClick = onToggleExpanded) {
            Icon(
                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (isExpanded) "Collapse" else "Expand",
                tint = Color.White
            )
        }
    }
}

@Composable
private fun DownloadItemRow(download: DownloadItem, downloadManager: DownloadManager) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // File type icon
        Icon(
            imageVector = when {
                download.status == DownloadStatus.COMPLETED -> Icons.Default.CheckCircle
                download.status == DownloadStatus.FAILED -> Icons.Default.Error
                download.status == DownloadStatus.DOWNLOADING -> Icons.Default.CloudDownload
                download.status == DownloadStatus.PAUSED -> Icons.Default.Pause
                else -> Icons.AutoMirrored.Default.InsertDriveFile
            },
            contentDescription = download.status.name,
            tint = when {
                download.status == DownloadStatus.COMPLETED -> Color(0xFF4CAF50)
                download.status == DownloadStatus.FAILED -> Color(0xFFF44336)
                download.status == DownloadStatus.DOWNLOADING -> Color(0xFF2196F3)
                download.status == DownloadStatus.PAUSED -> Color(0xFFFF9800)
                else -> Color.Gray
            },
            modifier = Modifier.size(24.dp)
        )

        Spacer(modifier = Modifier.width(12.dp))

        // File info and progress
        Column(modifier = Modifier.weight(1f)) {
            // File name
            Text(
                text = download.fileName,
                color = Color.White,
                style = MaterialTheme.typography.body2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Progress bar for active and paused downloads
            if ((download.status == DownloadStatus.DOWNLOADING || download.status == DownloadStatus.PAUSED) && download.totalBytes != null) {
                LinearProgressIndicator(
                    progress = download.progress,
                    modifier = Modifier.fillMaxWidth(),
                    color = if (download.status == DownloadStatus.PAUSED) Color(0xFFFF9800) else Color(0xFF2196F3),
                    backgroundColor = Color(0xFF424242)
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            // Status text
            Text(
                text = buildStatusText(download),
                color = Color.Gray,
                style = MaterialTheme.typography.caption
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Action buttons
        Row {
            when (download.status) {
                DownloadStatus.DOWNLOADING -> {
                    // Pause button (if supported by server)
                    if (download.canPause) {
                        IconButton(
                            onClick = {
                                FluckEngine.pauseDownload(download.id)
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Pause,
                                contentDescription = "Pause",
                                tint = Color.Gray
                            )
                        }
                    }
                    // Cancel button
                    IconButton(
                        onClick = {
                            FluckEngine.cancelDownload(download.id)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cancel",
                            tint = Color.Gray
                        )
                    }
                }
                DownloadStatus.PAUSED -> {
                    // Resume button (if supported by server)
                    if (download.canResume) {
                        IconButton(
                            onClick = {
                                FluckEngine.resumeDownload(download.id)
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Resume",
                                tint = Color.Gray
                            )
                        }
                    }
                    // Delete button - removes paused download from list
                    IconButton(
                        onClick = {
                            CoroutineScope(Dispatchers.Default).launch {
                                try {
                                    // Clean up partial file if it exists
                                    FileSystemUtils.cleanupPartialFile(download.destinationPath)
                                    // Remove from download list
                                    downloadManager.removeDownload(download.id)
                                    println("Deleted paused download: ${download.id}")
                                } catch (e: Exception) {
                                    println("Error deleting paused download: ${e.message}")
                                }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = Color(0xFFF44336)
                        )
                    }
                }
                DownloadStatus.COMPLETED -> {
                    // Show in folder
                    IconButton(
                        onClick = {
                            FileSystemUtils.revealInFolder(download.destinationPath)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = "Show in Folder",
                            tint = Color.White
                        )
                    }
                    // Open file
                    IconButton(
                        onClick = {
                            FileSystemUtils.openFile(download.destinationPath)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Default.OpenInNew,
                            contentDescription = "Open",
                            tint = Color.White
                        )
                    }
                }
                DownloadStatus.FAILED -> {
                    // Show error info
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Error: ${download.errorReason}",
                        tint = Color(0xFFF44336),
                        modifier = Modifier.size(20.dp)
                    )
                }
                else -> {
                    // No actions for other states
                }
            }
        }
    }
}

private fun buildStatusText(download: DownloadItem): String {
    return when (download.status) {
        DownloadStatus.DOWNLOADING -> {
            DownloadFormatters.formatProgressInfo(download)
        }
        DownloadStatus.COMPLETED -> {
            val size = download.totalBytes ?: download.receivedBytes
            "Completed • ${DownloadFormatters.formatBytes(size)}"
        }
        DownloadStatus.FAILED -> {
            download.errorReason ?: "Download failed"
        }
        DownloadStatus.CANCELLED -> {
            "Cancelled"
        }
        DownloadStatus.PAUSED -> {
            "Paused • ${DownloadFormatters.formatBytes(download.receivedBytes)}"
        }
        DownloadStatus.QUEUED -> {
            "Queued..."
        }
    }
}
