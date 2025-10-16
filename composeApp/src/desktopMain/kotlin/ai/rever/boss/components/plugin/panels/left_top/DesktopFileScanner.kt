package ai.rever.boss.components.plugin.panels.left_top

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

private fun scanFileRecursively(file: File, currentDepth: Int = 0, maxDepth: Int = 5): FileNode {
    val isDirectory = file.isDirectory
    val shouldLoadChildren = isDirectory && currentDepth < maxDepth
    
    val children = if (shouldLoadChildren) {
        file.listFiles()
            ?.filter { !it.name.startsWith(".") && it.name != "build" && it.name != "node_modules" }
            ?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            ?.map { childFile ->
                // For directories at the edge of our scan depth, just create a placeholder
                if (childFile.isDirectory && currentDepth + 1 >= maxDepth) {
                    FileNode(
                        name = childFile.name,
                        path = childFile.absolutePath,
                        isDirectory = true,
                        children = mutableListOf(),
                        isLoaded = false,
                        loadDepth = currentDepth + 1
                    )
                } else {
                    scanFileRecursively(childFile, currentDepth + 1, maxDepth)
                }
            }
            ?.toMutableList()
            ?: mutableListOf()
    } else {
        mutableListOf()
    }
    
    return FileNode(
        name = file.name,
        path = file.absolutePath,
        isDirectory = isDirectory,
        children = children,
        isLoaded = !isDirectory || currentDepth >= maxDepth - 1,
        loadDepth = currentDepth
    )
}
