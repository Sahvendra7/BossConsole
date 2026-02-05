package ai.rever.boss.components.plugin.panels.left_top

import ai.rever.boss.plugin.api.FileNodeData
import ai.rever.boss.plugin.api.NodeLoadingStateData
import java.io.File

actual fun scanDirectory(path: String): FileNodeData? {
    val file = File(path)
    if (!file.exists()) return null

    // Initial scan is shallow - only immediate children
    return scanFileRecursively(file, maxDepth = 1)
}

actual suspend fun scanDirectoryWithDepth(path: String, maxDepth: Int, startDepth: Int): FileNodeData? {
    val file = File(path)
    if (!file.exists()) return null

    return scanFileRecursively(file, currentDepth = startDepth, maxDepth = maxDepth)
}

/**
 * IntelliJ's isAlwaysShowPlus() pattern implementation.
 * Quick check if a directory has any visible children without loading them all.
 * This is O(1) - just checks if directory has any files, much faster than full scan.
 */
actual fun directoryHasChildren(path: String): Boolean {
    val file = File(path)
    if (!file.exists() || !file.isDirectory) return false

    // Quick check - just see if there are any visible files
    val children = file.listFiles() ?: return false
    return children.any { child ->
        // Apply same filter as scanFileRecursively
        !child.name.startsWith(".") &&
        child.name != "build" &&
        child.name != "node_modules"
    }
}

private fun scanFileRecursively(file: File, currentDepth: Int = 0, maxDepth: Int = 5): FileNodeData {
    val isDirectory = file.isDirectory
    val shouldLoadChildren = isDirectory && currentDepth < maxDepth

    val children: List<FileNodeData> = if (shouldLoadChildren) {
        file.listFiles()
            ?.filter { !it.name.startsWith(".") && it.name != "build" && it.name != "node_modules" }
            ?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            ?.map { childFile ->
                // For directories at the edge of our scan depth, just create a placeholder
                if (childFile.isDirectory && currentDepth + 1 >= maxDepth) {
                    // Quick check if this directory has children (isAlwaysShowPlus pattern)
                    val hasKids = directoryHasChildren(childFile.absolutePath)
                    FileNodeData(
                        name = childFile.name,
                        path = childFile.absolutePath,
                        isDirectory = true,
                        children = emptyList(),
                        hasChildren = hasKids,
                        loadingState = NodeLoadingStateData.UNKNOWN,
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

    // Determine loading state
    val loadingState = when {
        !isDirectory -> NodeLoadingStateData.LOADED
        currentDepth >= maxDepth - 1 -> NodeLoadingStateData.UNKNOWN
        else -> NodeLoadingStateData.LOADED
    }

    return FileNodeData(
        name = file.name,
        path = file.absolutePath,
        isDirectory = isDirectory,
        children = children,
        hasChildren = if (isDirectory) children.isNotEmpty() || directoryHasChildren(file.absolutePath) else false,
        loadingState = loadingState,
        loadDepth = currentDepth
    )
}
