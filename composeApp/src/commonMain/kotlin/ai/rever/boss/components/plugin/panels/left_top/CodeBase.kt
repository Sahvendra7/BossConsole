package ai.rever.boss.components.plugin.panels.left_top

import BossDarkBackground
import BossDarkBorder
import BossDarkTextSecondary
import ai.rever.boss.components.events.FileEventBus
import ai.rever.boss.components.model.Panel.Companion.left
import ai.rever.boss.components.model.Panel.Companion.top
import ai.rever.boss.components.plugin.DefaultPlugin
import ai.rever.boss.components.registery.PanelComponentWithUI
import ai.rever.boss.components.registery.PanelId
import ai.rever.boss.components.registery.PanelInfo
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arkivanov.decompose.ComponentContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object CodeBaseInfo : PanelInfo {
    override val id = PanelId("codebase", -1)
    override val displayName = "Codebase"
    override val icon = Icons.Outlined.Code
    override val defaultSlotPosition = left.top.top
}

// Data classes for file system representation
data class FileNode(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val children: MutableList<FileNode> = mutableListOf(),
    val isExpanded: Boolean = false,
    var isLoaded: Boolean = false,
    var loadDepth: Int = 0
) {
    fun updateChildren(newChildren: List<FileNode>) {
        children.clear()
        children.addAll(newChildren)
        isLoaded = true
    }
}

data class Project(
    val name: String,
    val path: String,
    val lastOpened: Long = 0L
)

// Global project state
object ProjectState {
    private val _selectedProject = MutableStateFlow(
        Project(
            name = "BOSS-Kotlin",
            path = "/Users/kshivang/Development/BOSS-Kotlin"
        )
    )
    val selectedProject: StateFlow<Project> = _selectedProject.asStateFlow()
    
    private val _recentProjects = MutableStateFlow(
        listOf(
            Project("BOSS-Kotlin", "/Users/kshivang/Development/BOSS-Kotlin"),
            Project("OneOncology", "/Users/kshivang/Development/OneOncology"),
            Project("Mayo", "/Users/kshivang/Development/Mayo"),
            Project("Atlantis", "/Users/kshivang/Development/Atlantis")
        )
    )
    val recentProjects: StateFlow<List<Project>> = _recentProjects.asStateFlow()
    
    fun selectProject(project: Project) {
        _selectedProject.value = project
        // Update recent projects list
        val updated = _recentProjects.value.toMutableList()
        updated.removeAll { it.path == project.path }
        updated.add(0, project.copy(lastOpened = kotlin.random.Random.nextLong()))
        if (updated.size > 10) {
            updated.removeLast()
        }
        _recentProjects.value = updated
    }
}

class CodeBaseComponent(
    ctx: ComponentContext,
    override val panelInfo: PanelInfo
) : PanelComponentWithUI, ComponentContext by ctx {
    
    private val _fileTree = MutableStateFlow<FileNode?>(null)
    private val fileTree: StateFlow<FileNode?> = _fileTree.asStateFlow()
    
    private val _expandedPaths = MutableStateFlow(setOf<String>())
    private val expandedPaths: StateFlow<Set<String>> = _expandedPaths.asStateFlow()
    
    private val fileCache = FileIndexCache(
        maxSize = 1000,
        maxDepthInitial = 2,
        maxDepthExpanded = 5
    )
    
    private val scope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main
    )
    
    init {
        // Load initial file tree
        scope.launch {
            loadFileTree(ProjectState.selectedProject.value.path)
        }
    }
    
    private suspend fun loadFileTree(rootPath: String) {
        // Use cached file scanner
        _fileTree.value = fileCache.getNode(rootPath) ?: createMockFileTree(rootPath)
    }
    
    private fun createMockFileTree(rootPath: String): FileNode {
        return FileNode(
            name = rootPath.substringAfterLast('/'),
            path = rootPath,
            isDirectory = true,
            children = mutableListOf(
                FileNode(
                    name = "composeApp",
                    path = "$rootPath/composeApp",
                    isDirectory = true,
                    children = mutableListOf(
                        FileNode(
                            name = "src",
                            path = "$rootPath/composeApp/src",
                            isDirectory = true,
                            children = mutableListOf(
                                FileNode(
                                    name = "commonMain",
                                    path = "$rootPath/composeApp/src/commonMain",
                                    isDirectory = true,
                                    children = mutableListOf(
                                        FileNode(
                                            name = "kotlin",
                                            path = "$rootPath/composeApp/src/commonMain/kotlin",
                                            isDirectory = true
                                        )
                                    )
                                )
                            )
                        ),
                        FileNode(
                            name = "build.gradle.kts",
                            path = "$rootPath/composeApp/build.gradle.kts",
                            isDirectory = false
                        )
                    )
                ),
                FileNode(
                    name = "README.md",
                    path = "$rootPath/README.md",
                    isDirectory = false
                ),
                FileNode(
                    name = "build.gradle.kts",
                    path = "$rootPath/build.gradle.kts",
                    isDirectory = false
                ),
                FileNode(
                    name = "settings.gradle.kts",
                    path = "$rootPath/settings.gradle.kts",
                    isDirectory = false
                )
            )
        )
    }
    
    private fun toggleExpanded(path: String) {
        val expanded = _expandedPaths.value.toMutableSet()
        if (expanded.contains(path)) {
            expanded.remove(path)
        } else {
            expanded.add(path)
            // Load deeper content when expanding
            scope.launch {
                val node = findNodeByPath(_fileTree.value, path)
                if (node?.isDirectory == true && (!node.isLoaded || node.children.isEmpty())) {
                    // Scan the directory directly for immediate results
                    val expandedNode = scanDirectoryWithDepth(path, maxDepth = 5, startDepth = 0)
                    if (expandedNode != null && expandedNode.children.isNotEmpty()) {
                        // Update the node's children
                        node.updateChildren(expandedNode.children)
                        node.isLoaded = true
                        node.loadDepth = 5
                        // Trigger recomposition
                        _fileTree.value = _fileTree.value?.copy()
                    }
                }
            }
        }
        _expandedPaths.value = expanded
    }
    
    private fun findNodeByPath(root: FileNode?, targetPath: String): FileNode? {
        if (root == null) return null
        if (root.path == targetPath) return root
        
        for (child in root.children) {
            val found = findNodeByPath(child, targetPath)
            if (found != null) return found
        }
        return null
    }

    @Composable
    override fun Content() {
        val selectedProject by ProjectState.selectedProject.collectAsState()
        val tree by fileTree.collectAsState()
        val expandedPaths by _expandedPaths.asStateFlow().collectAsState()
        
        // Reload tree when project changes
        LaunchedEffect(selectedProject) {
            fileCache.clearCache()
            loadFileTree(selectedProject.path)
        }
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(BossDarkBackground)
        ) {
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
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 4.dp)
            ) {
                tree?.let { rootNode ->
                    items(rootNode.children) { node ->
                        FileTreeItem(
                            node = node,
                            level = 0,
                            expandedPaths = expandedPaths,
                            onToggleExpanded = ::toggleExpanded,
                            onFileClick = { file ->
                                if (!file.isDirectory) {
                                    // TODO: Open file in editor
                                    // For now, just log the file path
                                    // In a real implementation, this would communicate with the main tab component
                                    println("Open file: ${file.path}")
                                    
                                    // You could emit an event or use a callback passed from the parent
                                    // Example: onOpenFile?.invoke(file.path)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileTreeItem(
    node: FileNode,
    level: Int,
    expandedPaths: Set<String>,
    onToggleExpanded: (String) -> Unit,
    onFileClick: (FileNode) -> Unit
) {
    val isExpanded = expandedPaths.contains(node.path)
    val scope = rememberCoroutineScope()
    
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(26.dp)
                .combinedClickable(
                    onClick = {
                        if (node.isDirectory) {
                            onToggleExpanded(node.path)
                        }
                    },
                    onDoubleClick = {
                        if (!node.isDirectory) {
                            onFileClick(node)
                            scope.launch {
                                FileEventBus.openFile(node.path)
                            }
                        }
                    }
                )
                .padding(start = (16 + level * 16).dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Expand/collapse icon for directories
            if (node.isDirectory) {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = BossDarkTextSecondary,
                    modifier = Modifier.size(16.dp)
                )
            } else {
                Spacer(modifier = Modifier.width(16.dp))
            }
            
            Spacer(modifier = Modifier.width(4.dp))
            
            // File/folder icon
            Icon(
                imageVector = when {
                    node.isDirectory -> if (isExpanded) Icons.Outlined.FolderOpen else Icons.Outlined.Folder
                    node.name.endsWith(".kt") || node.name.endsWith(".kts") -> Icons.Outlined.Code
                    node.name.endsWith(".md") -> Icons.Outlined.Description
                    node.name.endsWith(".gradle") || node.name.endsWith(".xml") -> Icons.Outlined.Settings
                    else -> Icons.AutoMirrored.Outlined.InsertDriveFile
                },
                contentDescription = if (node.isDirectory) "Folder" else "File",
                tint = when {
                    node.isDirectory -> Color(0xFF90A4AE)
                    node.name.endsWith(".kt") || node.name.endsWith(".kts") -> Color(0xFFE57373)
                    else -> Color(0xFF90A4AE)
                },
                modifier = Modifier.size(16.dp)
            )
            
            Spacer(modifier = Modifier.width(6.dp))
            
            // File/folder name
            Text(
                text = node.name,
                fontSize = 13.sp,
                color = Color(0xFFCCCCCC)
            )
        }
        
        // Show children if expanded
        if (node.isDirectory && isExpanded) {
            if (node.children.isEmpty() && !node.isLoaded) {
                // Show loading indicator
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
            } else {
                node.children.forEach { child ->
                    FileTreeItem(
                        node = child,
                        level = level + 1,
                        expandedPaths = expandedPaths,
                        onToggleExpanded = onToggleExpanded,
                        onFileClick = onFileClick
                    )
                }
            }
        }
    }
}

fun DefaultPlugin.registerCodeBase() = panelRegistry.registerPanel(CodeBaseInfo) {
        ctx, panelInfo -> CodeBaseComponent(ctx, panelInfo)
}

// Platform-specific file scanning
expect fun scanDirectory(path: String): FileNode?

// Helper function to check if file is supported by editor
fun isFileSupported(fileName: String): Boolean {
    val supportedExtensions = setOf(
        "kt", "kts", "java", "js", "jsx", "ts", "tsx", 
        "py", "json", "xml", "html", "htm", "css", "md", 
        "toml", "gradle", "txt", "yml", "yaml", "properties",
        "sh", "bat", "cmd"
    )
    val extension = fileName.substringAfterLast('.', "").lowercase()
    return extension in supportedExtensions
}
