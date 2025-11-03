package ai.rever.boss.updater

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ai.rever.boss.utils.Version

/**
 * Dialog for selecting a specific version to install
 */
@Composable
fun VersionSelectionDialog(
    currentVersion: Version,
    versions: List<VersionInfo>,
    isLoading: Boolean,
    error: String? = null,
    onVersionSelected: (VersionInfo) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var showStableOnly by remember { mutableStateOf(true) }

    // Calculate latest stable version (first non-prerelease in sorted list)
    val latestStableVersion = remember(versions) {
        versions.firstOrNull { !it.isPrerelease }?.version
    }

    val filteredVersions = remember(versions, searchQuery, showStableOnly) {
        versions
            .filter { if (showStableOnly) !it.isPrerelease else true }
            .filter {
                searchQuery.isEmpty() ||
                it.version.toString().contains(searchQuery, ignoreCase = true)
            }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .width(600.dp)
                .heightIn(max = 700.dp),
            backgroundColor = Color(0xFF2D2D2D),
            shape = RoundedCornerShape(8.dp),
            elevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Select Version",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Default.Close,
                            "Close",
                            tint = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Search bar
                TextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search versions...") },
                    leadingIcon = {
                        Icon(Icons.Default.Search, null)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.textFieldColors(
                        backgroundColor = Color(0xFF1E1E1E),
                        textColor = Color.White,
                        placeholderColor = Color.Gray
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Filter toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = showStableOnly,
                        onCheckedChange = { showStableOnly = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = Color(0xFF4CAF50)
                        )
                    )
                    Text(
                        text = "Stable releases only",
                        color = Color.White,
                        fontSize = 14.sp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Error display
                if (error != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        backgroundColor = Color(0xFFF44336).copy(alpha = 0.2f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color(0xFFF44336)
                            )
                            Text(
                                text = error,
                                color = Color(0xFFF44336),
                                fontSize = 14.sp
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Version list
                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(400.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Color(0xFF2196F3))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Loading versions...",
                                color = Color.Gray,
                                fontSize = 14.sp
                            )
                        }
                    }
                } else if (filteredVersions.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(400.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No versions found",
                            color = Color.Gray,
                            fontSize = 14.sp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredVersions) { versionInfo ->
                            VersionItem(
                                versionInfo = versionInfo,
                                isCurrent = versionInfo.version == currentVersion,
                                isLatest = versionInfo.version == latestStableVersion,
                                onClick = { onVersionSelected(versionInfo) }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Individual version item in the list
 */
@Composable
private fun VersionItem(
    versionInfo: VersionInfo,
    isCurrent: Boolean,
    isLatest: Boolean = false,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        backgroundColor = if (isCurrent)
            Color(0xFF2196F3).copy(alpha = 0.3f)
        else
            Color(0xFF1E1E1E),
        shape = RoundedCornerShape(8.dp),
        elevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "v${versionInfo.version}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    if (isCurrent) {
                        Card(
                            backgroundColor = Color(0xFF4CAF50),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                "Current",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }

                    if (isLatest && !isCurrent) {
                        Card(
                            backgroundColor = Color(0xFF2196F3),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                "Latest",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }

                    if (versionInfo.isPrerelease) {
                        Card(
                            backgroundColor = Color(0xFFFFC107),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                "Beta",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Released: ${formatReleaseDate(versionInfo.releaseDate)} • ${formatFileSize(versionInfo.downloadSize)}",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }

            Icon(
                imageVector = Icons.Default.CloudDownload,
                contentDescription = "Download",
                tint = Color(0xFF2196F3)
            )
        }
    }
}

/**
 * Format file size in MB
 */
private fun formatFileSize(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return "%.1f MB".format(mb)
}

/**
 * Format release date to simple format
 */
private fun formatReleaseDate(dateString: String): String {
    return try {
        // GitHub returns dates like "2024-01-15T10:30:00Z"
        // Extract just the date part
        dateString.substringBefore("T")
    } catch (e: Exception) {
        dateString
    }
}
