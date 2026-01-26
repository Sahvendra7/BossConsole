package ai.rever.boss.plugin.panel.downloads

import ai.rever.boss.plugin.api.DownloadItemData
import ai.rever.boss.plugin.api.DownloadStatusData
import ai.rever.boss.plugin.scrollbar.getPanelScrollbarConfig
import ai.rever.boss.plugin.scrollbar.lazyListScrollbar
import ai.rever.boss.plugin.ui.BossDarkBackground
import ai.rever.boss.plugin.ui.BossDarkBorder
import ai.rever.boss.plugin.ui.BossDarkTextSecondary
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DownloadsView(viewModel: DownloadsViewModel) {
    val downloads by viewModel.downloads.collectAsState()
    val listState = rememberLazyListState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BossDarkBackground)
            .padding(8.dp)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Downloads",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )

            val activeCount = downloads.count {
                it.status == DownloadStatusData.DOWNLOADING ||
                it.status == DownloadStatusData.QUEUED
            }
            if (activeCount > 0) {
                Surface(
                    color = Color(0xFF4CAF50),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "$activeCount",
                        color = Color.White,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }

        Divider(color = BossDarkBorder, thickness = 1.dp)

        Spacer(modifier = Modifier.height(8.dp))

        // Downloads list
        if (downloads.isEmpty()) {
            // Empty state
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "No downloads",
                        tint = BossDarkTextSecondary,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No downloads",
                        color = BossDarkTextSecondary,
                        fontSize = 12.sp
                    )
                }
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
                    ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(downloads, key = { it.id }) { download ->
                    DownloadItem(
                        download = download,
                        onPause = { viewModel.pauseDownload(download.id) },
                        onResume = { viewModel.resumeDownload(download.id) },
                        onCancel = { viewModel.cancelDownload(download.id) },
                        onRemove = { viewModel.removeDownload(download.id) },
                        onRevealInFolder = { viewModel.revealInFolder(download.destinationPath) },
                        onOpenFile = { viewModel.openFile(download.destinationPath) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadItem(
    download: DownloadItemData,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRemove: () -> Unit,
    onRevealInFolder: () -> Unit,
    onOpenFile: () -> Unit
) {
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    // Delete confirmation dialog
    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = {
                Text(
                    text = "Delete File?",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = "Are you sure you want to delete this file?",
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = download.fileName,
                        color = Color(0xFF90CAF9),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "This action cannot be undone.",
                        color = Color(0xFFF44336),
                        fontSize = 11.sp,
                        fontStyle = FontStyle.Italic
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmation = false
                        onRemove()
                    }
                ) {
                    Text("Delete", color = Color(0xFFF44336))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteConfirmation = false }
                ) {
                    Text("Cancel", color = Color(0xFF90CAF9))
                }
            },
            backgroundColor = Color(0xFF2D2D2D),
            contentColor = Color.White
        )
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF2D2D2D),
        shape = RoundedCornerShape(4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            // File name and status icon
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = when (download.status) {
                        DownloadStatusData.COMPLETED -> Icons.Default.CheckCircle
                        DownloadStatusData.FAILED -> Icons.Default.Error
                        DownloadStatusData.DOWNLOADING -> Icons.Default.CloudDownload
                        DownloadStatusData.PAUSED -> Icons.Default.Pause
                        else -> Icons.AutoMirrored.Default.InsertDriveFile
                    },
                    contentDescription = download.status.name,
                    tint = when (download.status) {
                        DownloadStatusData.COMPLETED -> Color(0xFF4CAF50)
                        DownloadStatusData.FAILED -> Color(0xFFF44336)
                        DownloadStatusData.DOWNLOADING -> Color(0xFF2196F3)
                        DownloadStatusData.PAUSED -> Color(0xFFFF9800)
                        else -> Color.Gray
                    },
                    modifier = Modifier.size(16.dp)
                )

                Spacer(modifier = Modifier.width(6.dp))

                Text(
                    text = download.fileName,
                    color = Color.White,
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Progress bar for active and paused downloads
            val totalBytes = download.totalBytes
            if ((download.status == DownloadStatusData.DOWNLOADING || download.status == DownloadStatusData.PAUSED) && totalBytes != null) {
                val progress = if (totalBytes > 0) {
                    download.receivedBytes.toFloat() / totalBytes.toFloat()
                } else {
                    0f
                }
                LinearProgressIndicator(
                    progress = progress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                    color = if (download.status == DownloadStatusData.PAUSED) Color(0xFFFF9800) else Color(0xFF2196F3),
                    backgroundColor = Color(0xFF424242)
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            // Status text
            Text(
                text = buildStatusText(download),
                color = BossDarkTextSecondary,
                fontSize = 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Action buttons based on download status
            when (download.status) {
                DownloadStatusData.DOWNLOADING -> {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        if (download.canPause) {
                            IconButton(
                                onClick = onPause,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Pause,
                                    contentDescription = "Pause",
                                    tint = Color(0xFFFF9800),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        IconButton(
                            onClick = onCancel,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cancel",
                                tint = Color(0xFFF44336),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
                DownloadStatusData.PAUSED -> {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        if (download.canResume) {
                            IconButton(
                                onClick = onResume,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Resume",
                                    tint = Color(0xFF4CAF50),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        IconButton(
                            onClick = onRemove,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = Color(0xFFF44336),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
                DownloadStatusData.COMPLETED -> {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        IconButton(
                            onClick = onRevealInFolder,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = "Show in Folder",
                                tint = Color(0xFF90CAF9),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        IconButton(
                            onClick = onOpenFile,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Default.OpenInNew,
                                contentDescription = "Open",
                                tint = Color(0xFF90CAF9),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        IconButton(
                            onClick = { showDeleteConfirmation = true },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete File",
                                tint = Color(0xFFF44336),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
                else -> {
                    // No action buttons for FAILED, CANCELLED, QUEUED
                }
            }
        }
    }
}

private fun buildStatusText(download: DownloadItemData): String {
    return when (download.status) {
        DownloadStatusData.DOWNLOADING -> {
            val received = formatBytes(download.receivedBytes)
            val total = download.totalBytes?.let { formatBytes(it) } ?: "?"
            val speed = formatSpeed(download.speed)
            "$received/$total • $speed"
        }
        DownloadStatusData.COMPLETED -> {
            val size = download.totalBytes ?: download.receivedBytes
            formatBytes(size)
        }
        DownloadStatusData.FAILED -> download.errorReason ?: "Failed"
        DownloadStatusData.CANCELLED -> "Cancelled"
        DownloadStatusData.PAUSED -> "Paused"
        DownloadStatusData.QUEUED -> "Queued..."
    }
}

private fun formatBytes(bytes: Long): String {
    return when {
        bytes >= 1_073_741_824 -> String.format("%.1f GB", bytes / 1_073_741_824.0)
        bytes >= 1_048_576 -> String.format("%.1f MB", bytes / 1_048_576.0)
        bytes >= 1024 -> String.format("%.1f KB", bytes / 1024.0)
        else -> "$bytes B"
    }
}

private fun formatSpeed(bytesPerSecond: Double): String {
    return when {
        bytesPerSecond >= 1_073_741_824 -> String.format("%.1f GB/s", bytesPerSecond / 1_073_741_824.0)
        bytesPerSecond >= 1_048_576 -> String.format("%.1f MB/s", bytesPerSecond / 1_048_576.0)
        bytesPerSecond >= 1024 -> String.format("%.1f KB/s", bytesPerSecond / 1024.0)
        else -> String.format("%.0f B/s", bytesPerSecond)
    }
}
