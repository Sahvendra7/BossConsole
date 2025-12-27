package ai.rever.boss.components.plugin.panels.left_top

import ai.rever.boss.components.plugin.tab_types.fluck.DownloadManager
import ai.rever.boss.components.plugin.tab_types.fluck.DownloadStatus
import ai.rever.boss.components.plugin.tab_types.fluck.FluckEngine
import ai.rever.boss.components.registery.PanelComponentWithUI
import ai.rever.boss.components.registery.PanelInfo
import ai.rever.boss.platform.FileSystemUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arkivanov.decompose.ComponentContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Downloads sidebar panel component
 *
 * Displays active and completed downloads in a compact sidebar format.
 * Positioned below the Bookmarks panel in the left sidebar.
 */
class DownloadsPanel(
    ctx: ComponentContext,
    override val panelInfo: PanelInfo
) : PanelComponentWithUI, ComponentContext by ctx {

    private val downloadManager: DownloadManager = FluckEngine.downloadManager

    @Composable
    override fun Content() {
        val downloads by downloadManager.downloads.collectAsState()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF1E1E1E))
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

                val activeCount = downloads.count { it.isActive }
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

            Divider(color = Color(0xFF3D3D3D), thickness = 1.dp)

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
                            tint = Color.Gray,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No downloads",
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(downloads, key = { it.id }) { download ->
                        SidebarDownloadItem(download, downloadManager)
                    }
                }
            }
        }
    }
}

@Composable
private fun SidebarDownloadItem(
    download: ai.rever.boss.components.plugin.tab_types.fluck.DownloadItem,
    downloadManager: DownloadManager
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
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmation = false
                        CoroutineScope(Dispatchers.Default).launch {
                            try {
                                // Delete the file from disk
                                val file = java.io.File(download.destinationPath)
                                if (file.exists()) {
                                    val deleted = file.delete()
                                    if (deleted) {
                                        println("Deleted file: ${download.destinationPath}")
                                        // Remove from download list after successful deletion
                                        downloadManager.removeDownload(download.id)
                                    } else {
                                        println("Failed to delete file: ${download.destinationPath}")
                                    }
                                } else {
                                    // File doesn't exist, just remove from list
                                    println("File not found, removing from list: ${download.destinationPath}")
                                    downloadManager.removeDownload(download.id)
                                }
                            } catch (e: Exception) {
                                println("Error deleting file: ${e.message}")
                            }
                        }
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
                    imageVector = when {
                        download.status == DownloadStatus.COMPLETED -> Icons.Default.CheckCircle
                        download.status == DownloadStatus.FAILED -> Icons.Default.Error
                        download.status == DownloadStatus.DOWNLOADING -> Icons.Default.CloudDownload
                        download.status == DownloadStatus.PAUSED -> Icons.Default.Pause
                        else -> Icons.Default.InsertDriveFile
                    },
                    contentDescription = download.status.name,
                    tint = when {
                        download.status == DownloadStatus.COMPLETED -> Color(0xFF4CAF50)
                        download.status == DownloadStatus.FAILED -> Color(0xFFF44336)
                        download.status == DownloadStatus.DOWNLOADING -> Color(0xFF2196F3)
                        download.status == DownloadStatus.PAUSED -> Color(0xFFFF9800)
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
            if ((download.status == DownloadStatus.DOWNLOADING || download.status == DownloadStatus.PAUSED) && download.totalBytes != null) {
                LinearProgressIndicator(
                    progress = download.progress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                    color = if (download.status == DownloadStatus.PAUSED) Color(0xFFFF9800) else Color(0xFF2196F3),
                    backgroundColor = Color(0xFF424242)
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            // Status text
            Text(
                text = buildCompactStatusText(download),
                color = Color.Gray,
                fontSize = 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Action buttons based on download status
            when (download.status) {
                DownloadStatus.DOWNLOADING -> {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        // Pause button (if supported by server)
                        if (download.canPause) {
                            IconButton(
                                onClick = {
                                    FluckEngine.pauseDownload(download.id)
                                },
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
                        // Cancel button
                        IconButton(
                            onClick = {
                                FluckEngine.cancelDownload(download.id)
                            },
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
                DownloadStatus.PAUSED -> {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        // Resume button (if supported by server)
                        if (download.canResume) {
                            IconButton(
                                onClick = {
                                    FluckEngine.resumeDownload(download.id)
                                },
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
                            },
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
                DownloadStatus.COMPLETED -> {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        // Show in folder button
                        IconButton(
                            onClick = {
                                FileSystemUtils.revealInFolder(download.destinationPath)
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = "Show in Folder",
                                tint = Color(0xFF90CAF9),
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        // Open file button
                        IconButton(
                            onClick = {
                                FileSystemUtils.openFile(download.destinationPath)
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.OpenInNew,
                                contentDescription = "Open",
                                tint = Color(0xFF90CAF9),
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        // Delete file button
                        IconButton(
                            onClick = {
                                showDeleteConfirmation = true
                            },
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

private fun buildCompactStatusText(download: ai.rever.boss.components.plugin.tab_types.fluck.DownloadItem): String {
    return when (download.status) {
        DownloadStatus.DOWNLOADING -> {
            val received = ai.rever.boss.components.plugin.tab_types.fluck.DownloadFormatters.formatBytes(download.receivedBytes)
            val total = download.totalBytes?.let {
                ai.rever.boss.components.plugin.tab_types.fluck.DownloadFormatters.formatBytes(it)
            } ?: "?"
            val speed = ai.rever.boss.components.plugin.tab_types.fluck.DownloadFormatters.formatSpeed(download.speed)
            "$received/$total • $speed"
        }
        DownloadStatus.COMPLETED -> {
            val size = download.totalBytes ?: download.receivedBytes
            ai.rever.boss.components.plugin.tab_types.fluck.DownloadFormatters.formatBytes(size)
        }
        DownloadStatus.FAILED -> {
            download.errorReason ?: "Failed"
        }
        DownloadStatus.CANCELLED -> "Cancelled"
        DownloadStatus.PAUSED -> "Paused"
        DownloadStatus.QUEUED -> "Queued..."
    }
}
