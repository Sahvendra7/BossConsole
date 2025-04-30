package ai.rever.boss.v4.components.window_panel.components

import ai.rever.boss.v4.components.overlays.ContextMenu
import ai.rever.boss.v4.components.overlays.ContextMenuItem
import ai.rever.boss.platform.ContextMenuHandler
import ai.rever.boss.v4.components.overlays.contextMenu
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Demo component that shows how to use the context menu.
 */
@Composable
fun ContextMenuDemo() {
    // Get platform-specific instruction text
    val contextMenuHandler = remember { ContextMenuHandler() }
    val instructionText = contextMenuHandler.getInstructionText()
    
    var actionMessage by remember { mutableStateOf(instructionText) }
    
    val contextMenuItems = listOf(
        ContextMenuItem(
            text = "New File",
            icon = Icons.Outlined.InsertDriveFile,
            onClick = { actionMessage = "New File clicked" }
        ),
        ContextMenuItem(
            text = "New Folder",
            icon = Icons.Outlined.CreateNewFolder,
            onClick = { actionMessage = "New Folder clicked" }
        ),
        ContextMenuItem(isDivider = true),
        ContextMenuItem(
            text = "Copy",
            icon = Icons.Outlined.ContentCopy,
            onClick = { actionMessage = "Copy clicked" }
        ),
        ContextMenuItem(
            text = "Paste",
            icon = Icons.Outlined.ContentPaste,
            onClick = { actionMessage = "Paste clicked" }
        ),
        ContextMenuItem(isDivider = true),
        ContextMenuItem(
            text = "Delete",
            icon = Icons.Outlined.Delete,
            onClick = { actionMessage = "Delete clicked" }
        )
    )
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1E1E1E))
            .contextMenu(items = contextMenuItems),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Context Menu Demo",
                color = Color.White,
                fontSize = 24.sp
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = actionMessage,
                color = Color.White,
                fontSize = 16.sp
            )
        }
    }
} 