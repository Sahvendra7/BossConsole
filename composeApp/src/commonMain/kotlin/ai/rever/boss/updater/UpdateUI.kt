package ai.rever.boss.updater

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.rever.boss.utils.Version
import kotlinx.coroutines.launch

/**
 * Update notification banner that appears at the top of the application
 */
@Composable
fun UpdateBanner(
    updateState: UpdateState,
    onCheckForUpdates: () -> Unit = {},
    onDownloadUpdate: (UpdateInfo) -> Unit = {},
    onInstallUpdate: (String) -> Unit = {},
    onDismiss: () -> Unit = {}
) {
    when (updateState) {
        is UpdateState.UpdateAvailable -> {
            UpdateAvailableBanner(
                updateInfo = updateState.updateInfo,
                onDownload = { onDownloadUpdate(updateState.updateInfo) },
                onDismiss = onDismiss
            )
        }
        is UpdateState.Downloading -> {
            DownloadProgressBanner(progress = updateState.progress)
        }
        is UpdateState.ReadyToInstall -> {
            ReadyToInstallBanner(
                onInstall = { onInstallUpdate(updateState.downloadPath) }
            )
        }
        is UpdateState.RestartRequired -> {
            RestartRequiredBanner()
        }
        is UpdateState.Error -> {
            ErrorBanner(
                message = updateState.message,
                onRetry = onCheckForUpdates,
                onDismiss = onDismiss
            )
        }
        else -> { /* No banner for other states */ }
    }
}

@Composable
private fun UpdateAvailableBanner(
    updateInfo: UpdateInfo,
    onDownload: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        backgroundColor = Color(0xFF2196F3),
        shape = RoundedCornerShape(8.dp),
        elevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.CloudDownload,
                    contentDescription = "Update Available",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        "Update Available: v${updateInfo.latestVersion}",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Current version: v${updateInfo.currentVersion}",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 12.sp
                    )
                }
            }
            
            Row {
                TextButton(
                    onClick = onDownload,
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.White)
                ) {
                    Text("Download")
                }
                TextButton(
                    onClick = onDismiss,
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.White.copy(alpha = 0.7f))
                ) {
                    Text("Dismiss")
                }
            }
        }
    }
}

@Composable
private fun DownloadProgressBanner(progress: Float) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        backgroundColor = Color(0xFF4CAF50),
        shape = RoundedCornerShape(8.dp),
        elevation = 4.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.CloudDownload,
                    contentDescription = "Downloading",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    "Downloading update... ${(progress * 100).toInt()}%",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier.fillMaxWidth(),
                color = Color.White,
                backgroundColor = Color.White.copy(alpha = 0.3f)
            )
        }
    }
}

@Composable
private fun ReadyToInstallBanner(
    onInstall: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        backgroundColor = Color(0xFFFF9800),
        shape = RoundedCornerShape(8.dp),
        elevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = "Ready to Install",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    "Update ready to install",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Button(
                onClick = onInstall,
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = Color.White,
                    contentColor = Color(0xFFFF9800)
                )
            ) {
                Text("Install Now")
            }
        }
    }
}

@Composable
private fun RestartRequiredBanner() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        backgroundColor = Color(0xFF9C27B0),
        shape = RoundedCornerShape(8.dp),
        elevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Refresh,
                contentDescription = "Restart Required",
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                "Update installed successfully. Please restart the application.",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun ErrorBanner(
    message: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        backgroundColor = Color(0xFFF44336),
        shape = RoundedCornerShape(8.dp),
        elevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = "Error",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        "Update Error",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        message,
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 12.sp
                    )
                }
            }
            
            Row {
                TextButton(
                    onClick = onRetry,
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.White)
                ) {
                    Text("Retry")
                }
                TextButton(
                    onClick = onDismiss,
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.White.copy(alpha = 0.7f))
                ) {
                    Text("Dismiss")
                }
            }
        }
    }
}

/**
 * Update settings section for the Settings window
 */
@Composable
fun UpdateSettingsSection(
    updateManager: UpdateManager = UpdateManager.instance
) {
    val updateState by updateManager.updateState.collectAsState()
    val lastCheckTime by updateManager.lastCheckTime.collectAsState()
    val currentVersion = updateManager.getCurrentVersion()
    val coroutineScope = rememberCoroutineScope()
    
    Column {
        // Version Information
        Card(
            modifier = Modifier.fillMaxWidth(),
            backgroundColor = Color(0xFF2D2D2D),
            shape = RoundedCornerShape(8.dp),
            elevation = 2.dp
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    "Version Information",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(12.dp))
                
                Text(
                    "Current Version: v$currentVersion",
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.8f)
                )
                
                lastCheckTime?.let { checkTime ->
                    Text(
                        "Last checked: ${formatTime(checkTime)}",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Update Controls
        Card(
            modifier = Modifier.fillMaxWidth(),
            backgroundColor = Color(0xFF2D2D2D),
            shape = RoundedCornerShape(8.dp),
            elevation = 2.dp
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    "Update Settings",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(12.dp))
                
                // Check for Updates Button
                Button(
                    onClick = {
                        coroutineScope.launch {
                            updateManager.checkForUpdates()
                        }
                    },
                    enabled = updateState !is UpdateState.CheckingForUpdates,
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF2196F3)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (updateState is UpdateState.CheckingForUpdates) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        if (updateState is UpdateState.CheckingForUpdates) "Checking..." else "Check for Updates",
                        color = Color.White
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Update Status
                when (val currentState = updateState) {
                    is UpdateState.UpToDate -> {
                        Text(
                            "✓ You're running the latest version",
                            color = Color(0xFF4CAF50),
                            fontSize = 14.sp
                        )
                    }
                    is UpdateState.UpdateAvailable -> {
                        Column {
                            Text(
                                "🔄 Update available: v${currentState.updateInfo.latestVersion}",
                                color = Color(0xFFFF9800),
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        updateManager.downloadUpdate(currentState.updateInfo)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF4CAF50)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Download Update", color = Color.White)
                            }
                        }
                    }
                    is UpdateState.Downloading -> {
                        Column {
                            Text(
                                "📥 Downloading update...",
                                color = Color(0xFF2196F3),
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = currentState.progress,
                                modifier = Modifier.fillMaxWidth(),
                                color = Color(0xFF2196F3)
                            )
                            Text(
                                "${(currentState.progress * 100).toInt()}%",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 12.sp,
                                modifier = Modifier.align(Alignment.End)
                            )
                        }
                    }
                    is UpdateState.ReadyToInstall -> {
                        Column {
                            Text(
                                "✅ Update downloaded successfully",
                                color = Color(0xFF4CAF50),
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        updateManager.installUpdate(currentState.downloadPath)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFFFF5722)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Install Now", color = Color.White)
                            }
                        }
                    }
                    is UpdateState.Installing -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color(0xFFFF5722),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "🔧 Installing update...",
                                color = Color(0xFFFF5722),
                                fontSize = 14.sp
                            )
                        }
                    }
                    is UpdateState.RestartRequired -> {
                        Column {
                            Text(
                                "🔄 Update installed! Restart required.",
                                color = Color(0xFF4CAF50),
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        restartApplication()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF9C27B0)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Restart Application", color = Color.White)
                            }
                        }
                    }
                    is UpdateState.Error -> {
                        Text(
                            "⚠️ ${currentState.message}",
                            color = Color(0xFFF44336),
                            fontSize = 14.sp
                        )
                    }
                    else -> { /* Show nothing for other states */ }
                }
            }
        }
    }
}

// Helper function to format time (you might want to use a proper date formatting library)
private fun formatTime(instant: kotlin.time.Instant): String {
    val millis = instant.toEpochMilliseconds()
    val seconds = millis / 1000
    val days = seconds / (24 * 3600)
    return if (days > 0) "$days days ago" else "Today"
}

// Platform-specific restart function
expect fun restartApplication()
