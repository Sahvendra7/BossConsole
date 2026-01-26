package ai.rever.boss.components.plugin.panels.left_top

import ai.rever.boss.plugin.panel.codebase.FileNode
import ai.rever.boss.plugin.panel.codebase.NodeLoadingState
import java.io.File

actual fun scanDirectory(path: String): FileNode? {
    val file = File(path)
    if (!file.exists()) return null

    // Initial scan is shallow - only immediate children
    return scanFileRecursively(file, maxDepth = 1)
}

actual suspend fun scanDirectoryWithDepth(path: String, maxDepth: Int, startDepth: Int): FileNode? {
    val file = File(path)
    if (!file.exists()) return null

    return scanFileRecursively(file, currentDepth = startDepth, maxDepth = maxDepth)
}

/**
 * IntelliJ's isAlwaysShowPlus() pattern implementation.
 * Quick check if a directory has any visible children without loading them all.
 */
actual fun directoryHasChildren(path: String): Boolean {
    val file = File(path)
    if (!file.exists() || !file.isDirectory) return false

    val children = file.listFiles() ?: return false
    return children.any { child ->
        !child.name.startsWith(".") &&
        child.name != "build" &&
        child.name != "node_modules"
    }
}

private fun scanFileRecursively(file: File, currentDepth: Int = 0, maxDepth: Int = 5): FileNode {
    val isDirectory = file.isDirectory
    val shouldLoadChildren = isDirectory && currentDepth < maxDepth

    val children: List<FileNode> = if (shouldLoadChildren) {
        file.listFiles()
            ?.filter { !it.name.startsWith(".") && it.name != "build" && it.name != "node_modules" }
            ?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            ?.map { childFile ->
                if (childFile.isDirectory && currentDepth + 1 >= maxDepth) {
                    val hasKids = directoryHasChildren(childFile.absolutePath)
                    FileNode(
                        name = childFile.name,
                        path = childFile.absolutePath,
                        isDirectory = true,
                        children = emptyList(),
                        hasChildren = hasKids,
                        loadingState = NodeLoadingState.UNKNOWN,
                        loadDepth = currentDepth + 1
                    )
                } else {
                    scanFileRecursively(childFile, currentDepth + 1, maxDepth)
                }
            }
            ?: emptyList()
    } else {
        emptyList()
    }

    val loadingState = when {
        !isDirectory -> NodeLoadingState.LOADED
        currentDepth >= maxDepth - 1 -> NodeLoadingState.UNKNOWN
        else -> NodeLoadingState.LOADED
    }

    return FileNode(
        name = file.name,
        path = file.absolutePath,
        isDirectory = isDirectory,
        children = children,
        hasChildren = if (isDirectory) children.isNotEmpty() || directoryHasChildren(file.absolutePath) else false,
        loadingState = loadingState,
        loadDepth = currentDepth
    )
}
