package ai.rever.boss.old_version.v1

import ai.rever.boss.getFileSelector
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch

@Composable
fun SourceSelectorDialog(
    onDismiss: () -> Unit,
    onSourceSelected: (SourceType, filePath: String?) -> Unit
) {
    val scope = rememberCoroutineScope()
    val fileSelector = remember { getFileSelector() }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            elevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Select Worklist Source",
                        style = MaterialTheme.typography.h6
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                SourceOption(
                    icon = Icons.Default.Cloud,
                    title = "API Integration",
                    description = "Connect through REST APIs",
                    onClick = { onSourceSelected(SourceType.API, null) }
                )

                SourceOption(
                    icon = Icons.Default.UploadFile,
                    title = "File Upload",
                    description = "Import from CSV, Excel, or other files",
                    onClick = {
                        scope.launch {
                            fileSelector.selectFile()?.let {
                                onSourceSelected(SourceType.FILE, it)
                            }
                        }
                    }
                )

                SourceOption(
                    icon = Icons.Default.Business,
                    title = "ERP/EHR Systems",
                    description = "Connect to enterprise systems",
                    onClick = { onSourceSelected(SourceType.ERP, null) }
                )
            }
        }
    }
}

@Composable
private fun SourceOption(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(32.dp),
            tint = MaterialTheme.colors.primary
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.subtitle1,
                color = MaterialTheme.colors.onSurface
            )
            Text(
                text = description,
                style = MaterialTheme.typography.body2,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
            )
        }
    }
}

enum class SourceType { API, FILE, ERP }