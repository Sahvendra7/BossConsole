package ai.rever.boss.components.plugin.panels.left_top

import BossDarkBackground
import BossDarkBorder
import BossDarkTextSecondary
import ai.rever.boss.components.events.FileEventBus
import ai.rever.boss.utils.SystemUtils
import ai.rever.boss.components.model.Panel.Companion.left
import ai.rever.boss.components.model.Panel.Companion.top
import ai.rever.boss.components.plugin.DefaultPlugin
import ai.rever.boss.components.registery.PanelComponentWithUI
import ai.rever.boss.components.registery.PanelId
import ai.rever.boss.components.registery.PanelInfo
import ai.rever.boss.platform.rememberDirectoryPicker
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arkivanov.decompose.ComponentContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object CodeBaseInfo : PanelInfo {
    override val id = PanelId("codebase", 2)
    override val displayName = "Codebase"
    override val icon = Icons.Outlined.Code
    override val defaultSlotPosition = left.top.top
}

/**
 * Loading state for directory nodes (IntelliJ pattern).
 * Separates "checking if has children" from "loading children".
 */
enum class NodeLoadingState {
    /** Initial state - don't know if directory has children */
    UNKNOWN,
    /** Checking if directory has children (quick check) */
    CHECKING,
    /** Children have been fully loaded */
    LOADED
}

/**
 * File system node representation with IntelliJ-style lazy loading.
 *
 * Key patterns from IntelliJ:
 * - `hasChildren`: Quick check to show expand indicator (isAlwaysShowPlus pattern)
 * - `loadingState`: Separate "checking" from "loading" states
 * - Immutable data class for proper Compose recomposition
 */
data class FileNode(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val children: List<FileNode> = emptyList(),
    /** Quick check result - does this directory have any children? (null = unknown) */
    val hasChildren: Boolean? = null,
    /** Current loading state for this node */
    val loadingState: NodeLoadingState = NodeLoadingState.UNKNOWN,
    val loadDepth: Int = 0
) {
    /** Convenience property - is this node fully loaded? */
    val isLoaded: Boolean get() = loadingState == NodeLoadingState.LOADED

    /**
     * IntelliJ's isAlwaysShowPlus() pattern:
     * Should we show the expand indicator before children are loaded?
     * Returns true if:
     * - Directory with unknown children status (show + optimistically)
     * - Directory that we know has children
     */
    fun shouldShowExpandIndicator(): Boolean {
        if (!isDirectory) return false
        // If we know it has no children, don't show indicator
        if (hasChildren == false) return false
        // If we have loaded children, show based on actual count
        if (isLoaded) return children.isNotEmpty()
        // Unknown or known to have children - show indicator
        return true
    }

    /**
     * IntelliJ's smart expand pattern:
     * Should this folder be auto-expanded because it contains only one subfolder?
     * Returns true if the node has exactly one child and that child is a directory.
     */
    fun shouldSmartExpand(): Boolean {
        if (!isDirectory) return false
        if (!isLoaded) return false
        // Smart expand only if there's exactly one child and it's a directory
        return children.size == 1 && children[0].isDirectory
    }

    /**
     * IntelliJ's compact middle packages pattern:
     * Gets the chain of single-child directories starting from this node.
     * Returns a list of nodes that should be displayed as one compacted entry.
     * Example: src -> main -> kotlin becomes ["src", "main", "kotlin"]
     */
    fun getCompactChain(): List<FileNode> {
        val chain = mutableListOf(this)
        var current = this

        while (current.isLoaded &&
               current.children.size == 1 &&
               current.children[0].isDirectory) {
            current = current.children[0]
            chain.add(current)
        }

        return chain
    }

    /**
     * Gets the display name for compact middle packages.
     * Returns names joined with "/" like "src/main/kotlin"
     */
    fun getCompactDisplayName(): String {
        val chain = getCompactChain()
        return chain.joinToString("/") { it.name }
    }

    /**
     * Gets the final node in a compact chain (the one with actual children to display).
     */
    fun getCompactEndNode(): FileNode {
        var current = this
        while (current.isLoaded &&
               current.children.size == 1 &&
               current.children[0].isDirectory) {
            current = current.children[0]
        }
        return current
    }

    /**
     * Creates a deep copy of this node and all its children.
     * Used for immutable state updates in Compose.
     */
    fun deepCopy(): FileNode = FileNode(
        name = name,
        path = path,
        isDirectory = isDirectory,
        children = children.map { it.deepCopy() },
        hasChildren = hasChildren,
        loadingState = loadingState,
        loadDepth = loadDepth
    )
}

data class Project(
    val name: String,
    val path: String,
    val lastOpened: Long = 0L
)

// Global project state with LRU cache
object ProjectState {
    private const val MAX_RECENT_PROJECTS = 10

    // Derive project name from path
    private fun getProjectNameFromPath(path: String): String {
        return path.trimEnd('/').substringAfterLast('/').ifEmpty { "Project" }
    }

    // Start with no project selected - user should choose
    private val _selectedProject = MutableStateFlow(
        Project(
            name = "No Project",
            path = "",
            lastOpened = 0L
        )
    )
    val selectedProject: StateFlow<Project> = _selectedProject.asStateFlow()

    // Start with empty recent projects list
    private val _recentProjects = MutableStateFlow<List<Project>>(emptyList())
    val recentProjects: StateFlow<List<Project>> = _recentProjects.asStateFlow()
    
    fun selectProject(project: Project) {
        _selectedProject.value = project
        
        // Update recent projects list with LRU behavior
        val updated = _recentProjects.value.toMutableList()
        
        // Remove if already exists
        updated.removeAll { it.path == project.path }
        
        // Add to front - being at position 0 means most recently used
        updated.add(0, project)
        
        // Keep only MAX_RECENT_PROJECTS
        while (updated.size > MAX_RECENT_PROJECTS) {
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

    private val fileCache = FileIndexCache(
        maxSize = 1000,
        maxDepthInitial = 2
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
            children = listOf(
                FileNode(
                    name = "composeApp",
                    path = "$rootPath/composeApp",
                    isDirectory = true,
                    children = listOf(
                        FileNode(
                            name = "src",
                            path = "$rootPath/composeApp/src",
                            isDirectory = true,
                            children = listOf(
                                FileNode(
                                    name = "commonMain",
                                    path = "$rootPath/composeApp/src/commonMain",
                                    isDirectory = true,
                                    children = listOf(
                                        FileNode(
                                            name = "kotlin",
                                            path = "$rootPath/composeApp/src/commonMain/kotlin",
                                            isDirectory = true,
                                            hasChildren = true,
                                            loadingState = NodeLoadingState.UNKNOWN
                                        )
                                    ),
                                    hasChildren = true,
                                    loadingState = NodeLoadingState.LOADED
                                )
                            ),
                            hasChildren = true,
                            loadingState = NodeLoadingState.LOADED
                        ),
                        FileNode(
                            name = "build.gradle.kts",
                            path = "$rootPath/composeApp/build.gradle.kts",
                            isDirectory = false,
                            hasChildren = false,
                            loadingState = NodeLoadingState.LOADED
                        )
                    ),
                    hasChildren = true,
                    loadingState = NodeLoadingState.LOADED
                ),
                FileNode(
                    name = "README.md",
                    path = "$rootPath/README.md",
                    isDirectory = false,
                    hasChildren = false,
                    loadingState = NodeLoadingState.LOADED
                ),
                FileNode(
                    name = "build.gradle.kts",
                    path = "$rootPath/build.gradle.kts",
                    isDirectory = false,
                    hasChildren = false,
                    loadingState = NodeLoadingState.LOADED
                ),
                FileNode(
                    name = "settings.gradle.kts",
                    path = "$rootPath/settings.gradle.kts",
                    isDirectory = false,
                    hasChildren = false,
                    loadingState = NodeLoadingState.LOADED
                )
            ),
            hasChildren = true,
            loadingState = NodeLoadingState.LOADED
        )
    }
    
    private fun toggleExpanded(path: String) {
        val expanded = _expandedPaths.value.toMutableSet()

        if (expanded.contains(path)) {
            // Collapsing - just remove from expanded set
            expanded.remove(path)
            _expandedPaths.value = expanded
        } else {
            // Expanding - add to expanded set first, then load content
            expanded.add(path)
            _expandedPaths.value = expanded

            // Load children using IntelliJ pattern
            scope.launch {
                loadNodeChildren(path)
            }
        }
    }

    /**
     * IntelliJ-style async loading of node children.
     * Uses background thread for I/O, updates UI on main thread.
     * Handles compact middle packages: loads the END node's children.
     */
    private suspend fun loadNodeChildren(path: String) {
        val currentTree = _fileTree.value ?: return
        val node = findNodeByPath(currentTree, path)

        if (node?.isDirectory != true) return

        // For compact middle packages: find the end node that needs loading
        val endNode = node.getCompactEndNode()
        val targetPath = endNode.path

        // If end node is already loaded with children, nothing to do
        if (endNode.isLoaded && endNode.children.isNotEmpty()) return

        // Step 1: Mark the end node as CHECKING state (shows loading indicator)
        _fileTree.value = updateNodeAtPath(currentTree, targetPath) { existingNode ->
            existingNode.copy(loadingState = NodeLoadingState.CHECKING)
        }

        try {
            // Step 2: Load children on IO dispatcher (IntelliJ pattern - background thread)
            val scannedNode = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                scanDirectoryWithDepth(targetPath, maxDepth = 1, startDepth = 0)
            }

            // Step 3: Update tree with loaded children
            val latestTree = _fileTree.value ?: return
            if (scannedNode != null) {
                val loadedChildren = scannedNode.children.map { child ->
                    // For each child directory, do a quick hasChildren check
                    if (child.isDirectory) {
                        val hasKids = try {
                            directoryHasChildren(child.path)
                        } catch (e: Exception) {
                            false // Assume no children on error
                        }
                        child.copy(hasChildren = hasKids)
                    } else {
                        child
                    }
                }

                _fileTree.value = updateNodeAtPath(latestTree, targetPath) { existingNode ->
                    existingNode.copy(
                        children = loadedChildren,
                        hasChildren = loadedChildren.isNotEmpty(),
                        loadingState = NodeLoadingState.LOADED,
                        loadDepth = 1
                    )
                }

                // Compact middle packages: if only one child directory, keep loading deeper
                // This ensures the compact chain is fully loaded for display
                // Limit depth to prevent excessive I/O on deep hierarchies
                compactLoadIfNeeded(loadedChildren, currentDepth = 0)
            } else {
                // No children found - mark as loaded with empty
                _fileTree.value = updateNodeAtPath(latestTree, targetPath) { existingNode ->
                    existingNode.copy(
                        children = emptyList(),
                        hasChildren = false,
                        loadingState = NodeLoadingState.LOADED
                    )
                }
            }
        } catch (e: Exception) {
            // Handle I/O errors gracefully - mark as loaded with error state
            println("Error loading children for $targetPath: ${e.message}")
            val latestTree = _fileTree.value ?: return
            _fileTree.value = updateNodeAtPath(latestTree, targetPath) { existingNode ->
                existingNode.copy(
                    children = emptyList(),
                    hasChildren = false,
                    loadingState = NodeLoadingState.LOADED
                )
            }
        }
    }

    /**
     * IntelliJ's compact middle packages pattern:
     * If a directory has exactly one child that is also a directory,
     * automatically load that child's contents (recursively) so the
     * compact display name can be calculated properly.
     *
     * @param children The children to check for compact loading
     * @param currentDepth Current recursion depth to prevent excessive I/O
     * @param maxDepth Maximum depth to recurse (default 10 levels)
     */
    private suspend fun compactLoadIfNeeded(
        children: List<FileNode>,
        currentDepth: Int,
        maxDepth: Int = 10
    ) {
        // Prevent excessive recursion on deep hierarchies (e.g., node_modules)
        if (currentDepth >= maxDepth) {
            println("Compact load depth limit reached ($maxDepth levels)")
            return
        }

        // Check if we should continue loading: exactly one child and it's a directory
        if (children.size == 1 && children[0].isDirectory) {
            val singleChild = children[0]

            // Don't add to expanded paths - just load for compact display calculation
            // Load this child's children (which will recursively compact-load if needed)
            loadNodeChildrenForCompact(singleChild.path, currentDepth + 1, maxDepth)
        }
    }

    /**
     * Load children specifically for compact path calculation.
     * Does not modify expanded paths.
     *
     * @param path Path to load children for
     * @param currentDepth Current recursion depth
     * @param maxDepth Maximum depth to recurse
     */
    private suspend fun loadNodeChildrenForCompact(
        path: String,
        currentDepth: Int = 0,
        maxDepth: Int = 10
    ) {
        val currentTree = _fileTree.value ?: return
        val node = findNodeByPath(currentTree, path)

        if (node?.isDirectory != true) return
        if (node.isLoaded) return

        try {
            // Load children on IO dispatcher
            val scannedNode = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                scanDirectoryWithDepth(path, maxDepth = 1, startDepth = 0)
            }

            val latestTree = _fileTree.value ?: return
            if (scannedNode != null) {
                val loadedChildren = scannedNode.children.map { child ->
                    if (child.isDirectory) {
                        val hasKids = try {
                            directoryHasChildren(child.path)
                        } catch (e: Exception) {
                            false // Assume no children on error
                        }
                        child.copy(hasChildren = hasKids)
                    } else {
                        child
                    }
                }

                _fileTree.value = updateNodeAtPath(latestTree, path) { existingNode ->
                    existingNode.copy(
                        children = loadedChildren,
                        hasChildren = loadedChildren.isNotEmpty(),
                        loadingState = NodeLoadingState.LOADED,
                        loadDepth = 1
                    )
                }

                // Continue loading for compact calculation if single directory child
                compactLoadIfNeeded(loadedChildren, currentDepth, maxDepth)
            } else {
                _fileTree.value = updateNodeAtPath(latestTree, path) { existingNode ->
                    existingNode.copy(
                        children = emptyList(),
                        hasChildren = false,
                        loadingState = NodeLoadingState.LOADED
                    )
                }
            }
        } catch (e: Exception) {
            // Handle I/O errors gracefully
            println("Error loading children for compact path $path: ${e.message}")
            val latestTree = _fileTree.value ?: return
            _fileTree.value = updateNodeAtPath(latestTree, path) { existingNode ->
                existingNode.copy(
                    children = emptyList(),
                    hasChildren = false,
                    loadingState = NodeLoadingState.LOADED
                )
            }
        }
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

    /**
     * Creates a new tree with the node at targetPath updated using the provided transform.
     * This ensures immutable state updates for proper Compose recomposition.
     */
    private fun updateNodeAtPath(
        root: FileNode,
        targetPath: String,
        update: (FileNode) -> FileNode
    ): FileNode {
        if (root.path == targetPath) {
            return update(root)
        }

        // Recursively update, creating new nodes along the path to the target
        return root.copy(
            children = root.children.map { child ->
                if (targetPath.startsWith(child.path + "/") || targetPath == child.path) {
                    updateNodeAtPath(child, targetPath, update)
                } else {
                    child
                }
            }
        )
    }

    @Composable
    override fun Content() {
        val selectedProject by ProjectState.selectedProject.collectAsState()
        val tree by fileTree.collectAsState()
        val expandedPaths by _expandedPaths.asStateFlow().collectAsState()

        // Directory picker - same as top bar
        val directoryPicker = rememberDirectoryPicker { path ->
            path?.let {
                val projectName = it.substringAfterLast('/').ifEmpty { "Unknown" }
                ProjectState.selectProject(
                    Project(
                        name = projectName,
                        path = it
                    )
                )
            }
        }

        // Reload tree when project changes (only if path is not empty)
        LaunchedEffect(selectedProject) {
            if (selectedProject.path.isNotEmpty()) {
                fileCache.clearCache()
                loadFileTree(selectedProject.path)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(BossDarkBackground)
        ) {
            // Show "Open Folder" UI when no project is selected
            if (selectedProject.path.isEmpty()) {
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
                            onClick = { directoryPicker.pickDirectory() },
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
    val scope = rememberCoroutineScope()

    // IntelliJ's compact middle packages pattern:
    // Get the end node of any single-child directory chain
    val endNode = node.getCompactEndNode()
    val compactDisplayName = node.getCompactDisplayName()
    val isCompacted = endNode.path != node.path

    // Use the START node's path for expansion state (user clicks on the visible row)
    val isExpanded = expandedPaths.contains(node.path)

    // IntelliJ pattern: Use shouldShowExpandIndicator() for the expand arrow
    // Check the END node since that's where the actual children are
    val showExpandIndicator = endNode.shouldShowExpandIndicator()
    val isLoading = endNode.loadingState == NodeLoadingState.CHECKING

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
            // Expand/collapse icon for directories (IntelliJ's isAlwaysShowPlus pattern)
            when {
                node.isDirectory && showExpandIndicator -> {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        tint = BossDarkTextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
                node.isDirectory -> {
                    // Directory with no children - show empty space
                    Spacer(modifier = Modifier.width(16.dp))
                }
                else -> {
                    // File - no expand indicator needed
                    Spacer(modifier = Modifier.width(16.dp))
                }
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

            // File/folder name - use compact display name for directories
            Text(
                text = if (node.isDirectory) compactDisplayName else node.name,
                fontSize = 13.sp,
                color = Color(0xFFCCCCCC)
            )
        }

        // Show children if expanded - use END node's children for compacted paths
        if (node.isDirectory && isExpanded) {
            val childrenToShow = endNode.children
            val endNodeLoading = endNode.loadingState == NodeLoadingState.CHECKING

            when {
                // Show loading indicator when checking/loading
                endNodeLoading || (childrenToShow.isEmpty() && !endNode.isLoaded) -> {
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
                // Show "Empty folder" message if loaded but no children
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
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        )
                    }
                }
                // Show children from the end node
                else -> {
                    childrenToShow.forEach { child ->
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
}

fun DefaultPlugin.registerCodeBase() = panelRegistry.registerPanel(CodeBaseInfo) {
        ctx, panelInfo -> CodeBaseComponent(ctx, panelInfo)
}

// Platform-specific file scanning
expect fun scanDirectory(path: String): FileNode?

/**
 * IntelliJ's isAlwaysShowPlus() pattern implementation.
 * Quick check if a directory has any children without loading them all.
 * This is much faster than scanning the full directory.
 */
expect fun directoryHasChildren(path: String): Boolean

