package ai.rever.boss.plugin.panel.codebase

import ai.rever.boss.plugin.api.PanelComponentWithUI
import ai.rever.boss.plugin.api.PanelInfo
import ai.rever.boss.plugin.logging.BossLogger
import ai.rever.boss.plugin.logging.LogCategory
import ai.rever.boss.plugin.ui.ContextMenuItemData
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.arkivanov.decompose.ComponentContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

/**
 * CodeBase panel component.
 *
 * This component provides file tree browsing with:
 * - IntelliJ-style lazy loading
 * - Compact middle packages display
 * - LRU caching for file system nodes
 */
class CodeBaseComponent(
    ctx: ComponentContext,
    override val panelInfo: PanelInfo,
    private val fileSystemProvider: FileSystemDataProvider,
    private val projectDataProvider: ProjectDataProvider,
    private val getWindowId: () -> String?,
    private val getSelectedProject: () -> ProjectData?,
    private val onSelectProject: (ProjectData) -> Unit,
    private val directoryPickerProvider: DirectoryPickerProvider,
    val contextMenuProvider: @Composable (Modifier, List<ContextMenuItemData>) -> Modifier,
    private val openTerminalTab: (workingDirectory: String) -> Unit
) : PanelComponentWithUI, ComponentContext by ctx {
    private val logger = BossLogger.forComponent("CodeBaseComponent")

    private val _fileTree = MutableStateFlow<FileNode?>(null)
    val fileTree: StateFlow<FileNode?> = _fileTree.asStateFlow()

    private val _expandedPaths = MutableStateFlow(setOf<String>())
    val expandedPaths: StateFlow<Set<String>> = _expandedPaths.asStateFlow()

    private val fileCache = FileIndexCache(
        maxSize = 1000,
        maxDepthInitial = 2,
        fileSystemProvider = fileSystemProvider
    )

    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main
    )

    // Mutex to prevent race conditions during tree updates
    private val treeUpdateMutex = Mutex()

    /**
     * Directories that should not be compactly loaded due to deep hierarchies.
     */
    private val excludedDirectories = setOf(
        "node_modules",
        ".git",
        ".gradle",
        ".idea",
        "__pycache__",
        "target",
        "build",
        ".next",
        "dist",
        "vendor"
    )

    /**
     * Load file tree for the given root path.
     */
    suspend fun loadFileTree(rootPath: String) {
        if (rootPath.isEmpty()) {
            _fileTree.value = null
            return
        }

        _fileTree.value = fileCache.getNode(rootPath)
    }

    /**
     * Toggle expansion state for a directory path.
     */
    fun toggleExpanded(path: String) {
        val expanded = _expandedPaths.value.toMutableSet()

        if (expanded.contains(path)) {
            expanded.remove(path)
            _expandedPaths.value = expanded
        } else {
            expanded.add(path)
            _expandedPaths.value = expanded

            scope.launch {
                loadNodeChildren(path)
            }
        }
    }

    /**
     * Load children for a node asynchronously.
     */
    private suspend fun loadNodeChildren(path: String) {
        val currentTree = _fileTree.value ?: return
        val node = FileTreeUtils.findNodeByPath(currentTree, path)
        if (node?.isDirectory != true) return

        val endNode = node.getCompactEndNode()
        var targetPath = endNode.path
        if (endNode.isLoaded && endNode.children.isNotEmpty()) return

        // Mark as CHECKING state
        treeUpdateMutex.lock()
        try {
            val treeForUpdate = _fileTree.value ?: return
            val nodeAfterLock = FileTreeUtils.findNodeByPath(treeForUpdate, path)
            if (nodeAfterLock?.isDirectory != true) return

            val endNodeAfterLock = nodeAfterLock.getCompactEndNode()
            if (endNodeAfterLock.isLoaded && endNodeAfterLock.children.isNotEmpty()) return

            targetPath = endNodeAfterLock.path

            _fileTree.value = FileTreeUtils.updateNodeAtPath(treeForUpdate, targetPath) { existingNode ->
                existingNode.copy(loadingState = NodeLoadingState.CHECKING)
            }
        } finally {
            treeUpdateMutex.unlock()
        }

        // Load children
        val scannedNode = try {
            kotlinx.coroutines.withContext(Dispatchers.IO) {
                fileSystemProvider.scanDirectoryWithDepth(targetPath, maxDepth = 1, startDepth = 0)
            }
        } catch (e: Exception) {
            logger.warn(LogCategory.FILE, "Error loading children", mapOf("path" to targetPath), error = e)
            null
        }

        val loadedChildren = scannedNode?.children?.map { child ->
            if (child.isDirectory) {
                val hasKids = try {
                    fileSystemProvider.directoryHasChildren(child.path)
                } catch (e: Exception) {
                    false
                }
                child.copy(hasChildren = hasKids)
            } else {
                child
            }
        }

        // Update tree with loaded children
        treeUpdateMutex.lock()
        try {
            val latestTree = _fileTree.value ?: return

            if (loadedChildren != null) {
                _fileTree.value = FileTreeUtils.updateNodeAtPath(latestTree, targetPath) { existingNode ->
                    existingNode.copy(
                        children = loadedChildren,
                        hasChildren = loadedChildren.isNotEmpty(),
                        loadingState = NodeLoadingState.LOADED,
                        loadDepth = 1
                    )
                }
            } else {
                _fileTree.value = FileTreeUtils.updateNodeAtPath(latestTree, targetPath) { existingNode ->
                    existingNode.copy(
                        children = emptyList(),
                        hasChildren = false,
                        loadingState = NodeLoadingState.LOADED
                    )
                }
            }
        } finally {
            treeUpdateMutex.unlock()
        }

        // Compact loading for single-child directories
        if (loadedChildren != null) {
            compactLoadIfNeeded(loadedChildren, currentDepth = 0)
        }
    }

    private suspend fun compactLoadIfNeeded(
        children: List<FileNode>,
        currentDepth: Int,
        maxDepth: Int = 10
    ) {
        if (currentDepth >= maxDepth) return

        if (children.size == 1 && children[0].isDirectory) {
            val singleChild = children[0]

            if (excludedDirectories.contains(singleChild.name)) {
                return
            }

            loadNodeChildrenForCompact(singleChild.path, currentDepth + 1, maxDepth)
        }
    }

    private suspend fun loadNodeChildrenForCompact(
        path: String,
        currentDepth: Int = 0,
        maxDepth: Int = 10
    ) {
        val currentTree = _fileTree.value ?: return
        val node = FileTreeUtils.findNodeByPath(currentTree, path)
        if (node?.isDirectory != true) return
        if (node.isLoaded) return

        val scannedNode = try {
            kotlinx.coroutines.withContext(Dispatchers.IO) {
                fileSystemProvider.scanDirectoryWithDepth(path, maxDepth = 1, startDepth = 0)
            }
        } catch (e: Exception) {
            logger.warn(LogCategory.FILE, "Error loading children for compact path", mapOf("path" to path), error = e)
            null
        }

        val loadedChildren = scannedNode?.children?.map { child ->
            if (child.isDirectory) {
                val hasKids = try {
                    fileSystemProvider.directoryHasChildren(child.path)
                } catch (e: Exception) {
                    false
                }
                child.copy(hasChildren = hasKids)
            } else {
                child
            }
        }

        treeUpdateMutex.lock()
        try {
            val latestTree = _fileTree.value ?: return
            val nodeAfterLock = FileTreeUtils.findNodeByPath(latestTree, path)
            if (nodeAfterLock?.isDirectory != true) return
            if (nodeAfterLock.isLoaded) return

            if (loadedChildren != null) {
                _fileTree.value = FileTreeUtils.updateNodeAtPath(latestTree, path) { existingNode ->
                    existingNode.copy(
                        children = loadedChildren,
                        hasChildren = loadedChildren.isNotEmpty(),
                        loadingState = NodeLoadingState.LOADED,
                        loadDepth = 1
                    )
                }
            } else {
                _fileTree.value = FileTreeUtils.updateNodeAtPath(latestTree, path) { existingNode ->
                    existingNode.copy(
                        children = emptyList(),
                        hasChildren = false,
                        loadingState = NodeLoadingState.LOADED
                    )
                }
            }
        } finally {
            treeUpdateMutex.unlock()
        }

        if (loadedChildren != null) {
            compactLoadIfNeeded(loadedChildren, currentDepth, maxDepth)
        }
    }

    /**
     * Clear the file cache.
     */
    suspend fun clearCache() {
        fileCache.clearCache()
    }

    /**
     * Clear the tree state.
     */
    fun clearTree() {
        _fileTree.value = null
        _expandedPaths.value = emptySet()
    }

    /**
     * Open a file in the editor.
     */
    fun openFile(path: String) {
        val windowId = getWindowId()
        if (windowId != null) {
            fileSystemProvider.openFile(path, windowId)
        }
    }

    /**
     * Pick a directory and select it as the project.
     */
    fun pickDirectory() {
        directoryPickerProvider.pickDirectory { path ->
            path?.let {
                val projectName = it.substringAfterLast('/').ifEmpty { "Unknown" }
                onSelectProject(ProjectData(
                    name = projectName,
                    path = it
                ))
            }
        }
    }

    /**
     * Create a new file in the specified directory.
     *
     * @param parentPath The parent directory path
     * @param fileName The name of the file to create
     * @param onResult Callback with the result (success path or error message)
     */
    fun createFile(parentPath: String, fileName: String, onResult: (Result<String>) -> Unit) {
        scope.launch {
            val result = fileSystemProvider.createFile(parentPath, fileName)
            onResult(result)
            if (result.isSuccess) {
                refreshNode(parentPath)
            }
        }
    }

    /**
     * Create a new folder in the specified directory.
     *
     * @param parentPath The parent directory path
     * @param folderName The name of the folder to create
     * @param onResult Callback with the result (success path or error message)
     */
    fun createFolder(parentPath: String, folderName: String, onResult: (Result<String>) -> Unit) {
        scope.launch {
            val result = fileSystemProvider.createFolder(parentPath, folderName)
            onResult(result)
            if (result.isSuccess) {
                refreshNode(parentPath)
            }
        }
    }

    /**
     * Refresh a specific node in the tree after creation/deletion.
     */
    fun refreshNode(path: String) {
        scope.launch {
            // Mark as CHECKING state - check node validity inside the lock to prevent race conditions
            treeUpdateMutex.lock()
            try {
                val treeForUpdate = _fileTree.value ?: return@launch
                val node = FileTreeUtils.findNodeByPath(treeForUpdate, path)
                if (node?.isDirectory != true) return@launch

                _fileTree.value = FileTreeUtils.updateNodeAtPath(treeForUpdate, path) { existingNode ->
                    existingNode.copy(loadingState = NodeLoadingState.CHECKING)
                }
            } finally {
                treeUpdateMutex.unlock()
            }

            // Reload children on IO dispatcher
            val loadedChildren = try {
                kotlinx.coroutines.withContext(Dispatchers.IO) {
                    val scannedNode = fileSystemProvider.scanDirectoryWithDepth(path, maxDepth = 1, startDepth = 0)
                    scannedNode?.children?.map { child ->
                        if (child.isDirectory) {
                            val hasKids = try {
                                fileSystemProvider.directoryHasChildren(child.path)
                            } catch (e: Exception) {
                                false
                            }
                            child.copy(hasChildren = hasKids)
                        } else {
                            child
                        }
                    }
                }
            } catch (e: Exception) {
                logger.warn(LogCategory.FILE, "Error refreshing node", mapOf("path" to path), error = e)
                null
            }

            // Update tree with refreshed children
            treeUpdateMutex.lock()
            try {
                val latestTree = _fileTree.value ?: return@launch

                if (loadedChildren != null) {
                    _fileTree.value = FileTreeUtils.updateNodeAtPath(latestTree, path) { existingNode ->
                        existingNode.copy(
                            children = loadedChildren,
                            hasChildren = loadedChildren.isNotEmpty(),
                            loadingState = NodeLoadingState.LOADED,
                            loadDepth = 1
                        )
                    }
                } else {
                    _fileTree.value = FileTreeUtils.updateNodeAtPath(latestTree, path) { existingNode ->
                        existingNode.copy(
                            children = emptyList(),
                            hasChildren = false,
                            loadingState = NodeLoadingState.LOADED
                        )
                    }
                }
            } finally {
                treeUpdateMutex.unlock()
            }

            // Make sure the node is expanded
            val expanded = _expandedPaths.value.toMutableSet()
            if (!expanded.contains(path)) {
                expanded.add(path)
                _expandedPaths.value = expanded
            }
        }
    }

    /**
     * Delete a file or folder.
     *
     * @param path The path to delete
     * @param onResult Callback with the result
     */
    fun deleteItem(path: String, onResult: (Result<Unit>) -> Unit) {
        scope.launch {
            val result = fileSystemProvider.delete(path)
            onResult(result)
            if (result.isSuccess) {
                // Refresh parent directory
                val parentPath = path.substringBeforeLast('/')
                if (parentPath.isNotEmpty()) {
                    refreshNode(parentPath)
                }
            }
        }
    }

    /**
     * Rename a file or folder.
     *
     * @param path The current path
     * @param newName The new name
     * @param onResult Callback with the result (new path or error)
     */
    fun renameItem(path: String, newName: String, onResult: (Result<String>) -> Unit) {
        scope.launch {
            val result = fileSystemProvider.rename(path, newName)
            onResult(result)
            if (result.isSuccess) {
                // Refresh parent directory
                val parentPath = path.substringBeforeLast('/')
                if (parentPath.isNotEmpty()) {
                    refreshNode(parentPath)
                }
            }
        }
    }

    /**
     * Reveal file or folder in system file manager.
     */
    fun revealInFileManager(path: String) {
        fileSystemProvider.revealInFileManager(path).onFailure { error ->
            logger.warn(LogCategory.FILE, "Failed to reveal in file manager", mapOf("path" to path), error = error)
        }
    }

    /**
     * Open terminal at directory.
     */
    fun openInTerminal(path: String) {
        // Get the directory path (if file, use parent directory)
        val file = java.io.File(path)
        val directory = if (file.isDirectory) file.absolutePath else file.parent ?: return
        openTerminalTab(directory)
    }

    /**
     * Copy absolute path to clipboard.
     */
    fun copyPath(path: String) {
        fileSystemProvider.copyToClipboard(path).onFailure { error ->
            logger.warn(LogCategory.FILE, "Failed to copy path to clipboard", mapOf("path" to path), error = error)
        }
    }

    /**
     * Copy relative path (from project root) to clipboard.
     */
    fun copyRelativePath(path: String) {
        val projectPath = getSelectedProject()?.path ?: ""
        val relativePath = if (projectPath.isNotEmpty() && path.startsWith(projectPath)) {
            path.removePrefix(projectPath).removePrefix("/")
        } else {
            path
        }
        fileSystemProvider.copyToClipboard(relativePath).onFailure { error ->
            logger.warn(LogCategory.FILE, "Failed to copy relative path to clipboard", mapOf("path" to relativePath), error = error)
        }
    }

    @Composable
    override fun Content() {
        CodeBaseContent(
            component = this,
            getSelectedProject = getSelectedProject
        )
    }
}
