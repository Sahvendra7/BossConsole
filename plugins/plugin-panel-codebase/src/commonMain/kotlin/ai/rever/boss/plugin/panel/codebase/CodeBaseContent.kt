package ai.rever.boss.plugin.panel.codebase

import ai.rever.boss.plugin.scrollbar.getPanelScrollbarConfig
import ai.rever.boss.plugin.scrollbar.lazyListScrollbar
import ai.rever.boss.plugin.ui.BossDarkBackground
import ai.rever.boss.plugin.ui.BossDarkBorder
import ai.rever.boss.plugin.ui.BossDarkTextSecondary
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * Main content composable for the CodeBase panel.
 */
@Composable
fun CodeBaseContent(
    component: CodeBaseComponent,
    getSelectedProject: () -> ProjectData?
) {
    val selectedProject = getSelectedProject()
    val tree by component.fileTree.collectAsState()
    val expandedPaths by component.expandedPaths.collectAsState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Reload tree when project changes
    LaunchedEffect(selectedProject) {
        if (selectedProject != null && selectedProject.path.isNotEmpty()) {
            component.clearCache()
            component.loadFileTree(selectedProject.path)
        } else {
            component.clearTree()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BossDarkBackground)
    ) {
        if (selectedProject == null || selectedProject.path.isEmpty()) {
            // Empty state - show Open Folder button
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.FolderOpen,
                        contentDescription = "No project open",
                        tint = BossDarkTextSecondary,
                        modifier = Modifier.size(48.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "No project opened",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Open a project to browse files",
                        fontSize = 12.sp,
                        color = BossDarkTextSecondary,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = { component.pickDirectory() },
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = Color(0xFF365880),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.FolderOpen,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Open Project",
                            fontSize = 13.sp
                        )
                    }
                }
            }
        } else {
            // Header with project info
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF2B2D30),
                elevation = 1.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.FolderOpen,
                        contentDescription = "Project",
                        tint = Color(0xFF6B9EFF),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = selectedProject.name,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White
                    )
                }
            }

            Divider(color = BossDarkBorder)

            // File tree
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 4.dp)
                    .lazyListScrollbar(
                        listState = listState,
                        direction = Orientation.Vertical,
                        config = getPanelScrollbarConfig()
                    )
            ) {
                tree?.let { rootNode ->
                    items(rootNode.children) { node ->
                        FileTreeItem(
                            node = node,
                            level = 0,
                            expandedPaths = expandedPaths,
                            onToggleExpanded = component::toggleExpanded,
                            onFileDoubleClick = { file ->
                                if (!file.isDirectory) {
                                    component.openFile(file.path)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * File tree item composable with IntelliJ-style compact paths.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileTreeItem(
    node: FileNode,
    level: Int,
    expandedPaths: Set<String>,
    onToggleExpanded: (String) -> Unit,
    onFileDoubleClick: (FileNode) -> Unit
) {
    // IntelliJ's compact middle packages pattern
    val endNode = node.getCompactEndNode()
    val compactDisplayName = node.getCompactDisplayName()
    val isExpanded = expandedPaths.contains(node.path)
    val showExpandIndicator = endNode.shouldShowExpandIndicator()

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(26.dp)
                .combinedClickable(
                    onClick = {
                        if (node.isDirectory && showExpandIndicator) {
                            onToggleExpanded(node.path)
                        }
                    },
                    onDoubleClick = {
                        if (!node.isDirectory) {
                            onFileDoubleClick(node)
                        }
                    }
                )
                .padding(start = (16 + level * 16).dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Expand/collapse icon for directories
            when {
                node.isDirectory && showExpandIndicator -> {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        tint = BossDarkTextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
                else -> {
                    Spacer(modifier = Modifier.width(16.dp))
                }
            }

            Spacer(modifier = Modifier.width(4.dp))

            // File/folder icon
            val iconInfo = if (node.isDirectory) {
                FileIconInfo(
                    if (isExpanded) Icons.Outlined.FolderOpen else Icons.Outlined.Folder,
                    Color(0xFF90A4AE)  // Match main branch folder color (gray-blue)
                )
            } else {
                getFileIcon(node.name)
            }

            Icon(
                imageVector = iconInfo.icon,
                contentDescription = if (node.isDirectory) "Folder" else "File",
                tint = iconInfo.color,
                modifier = Modifier.size(16.dp)
            )

            Spacer(modifier = Modifier.width(6.dp))

            // File/folder name
            Text(
                text = if (node.isDirectory) compactDisplayName else node.name,
                fontSize = 13.sp,
                color = Color(0xFFCCCCCC)
            )
        }

        // Show children if expanded
        if (node.isDirectory && isExpanded) {
            val childrenToShow = endNode.children
            val isLoading = endNode.loadingState == NodeLoadingState.CHECKING

            when {
                isLoading || (childrenToShow.isEmpty() && !endNode.isLoaded) -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(26.dp)
                            .padding(start = (32 + level * 16).dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            strokeWidth = 1.dp,
                            color = BossDarkTextSecondary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Loading...",
                            fontSize = 12.sp,
                            color = BossDarkTextSecondary
                        )
                    }
                }
                endNode.isLoaded && childrenToShow.isEmpty() -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(26.dp)
                            .padding(start = (32 + level * 16).dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "(empty)",
                            fontSize = 12.sp,
                            color = BossDarkTextSecondary.copy(alpha = 0.6f),
                            fontStyle = FontStyle.Italic
                        )
                    }
                }
                else -> {
                    childrenToShow.forEach { child ->
                        FileTreeItem(
                            node = child,
                            level = level + 1,
                            expandedPaths = expandedPaths,
                            onToggleExpanded = onToggleExpanded,
                            onFileDoubleClick = onFileDoubleClick
                        )
                    }
                }
            }
        }
    }
}

/**
 * File icon information.
 */
data class FileIconInfo(
    val icon: ImageVector,
    val color: Color
)

/**
 * Get file icon based on file extension.
 */
private fun getFileIcon(fileName: String): FileIconInfo {
    val extension = fileName.substringAfterLast('.', "").lowercase()
    return when (extension) {
        "kt", "kts" -> FileIconInfo(Icons.Outlined.Code, Color(0xFF7F52FF))
        "java" -> FileIconInfo(Icons.Outlined.Code, Color(0xFFE76F00))
        "xml" -> FileIconInfo(Icons.AutoMirrored.Outlined.InsertDriveFile, Color(0xFFE37933))
        "json" -> FileIconInfo(Icons.AutoMirrored.Outlined.InsertDriveFile, Color(0xFFCBCB41))
        "md" -> FileIconInfo(Icons.AutoMirrored.Outlined.InsertDriveFile, Color(0xFF519ABA))
        "gradle" -> FileIconInfo(Icons.AutoMirrored.Outlined.InsertDriveFile, Color(0xFF02303A))
        "properties" -> FileIconInfo(Icons.AutoMirrored.Outlined.InsertDriveFile, Color(0xFF9E9E9E))
        "yaml", "yml" -> FileIconInfo(Icons.AutoMirrored.Outlined.InsertDriveFile, Color(0xFFCB171E))
        "js", "ts" -> FileIconInfo(Icons.Outlined.Code, Color(0xFFF7DF1E))
        "py" -> FileIconInfo(Icons.Outlined.Code, Color(0xFF3776AB))
        "rs" -> FileIconInfo(Icons.Outlined.Code, Color(0xFFDEA584))
        "go" -> FileIconInfo(Icons.Outlined.Code, Color(0xFF00ADD8))
        "swift" -> FileIconInfo(Icons.Outlined.Code, Color(0xFFFA7343))
        else -> FileIconInfo(Icons.AutoMirrored.Outlined.InsertDriveFile, Color(0xFF9E9E9E))
    }
}
