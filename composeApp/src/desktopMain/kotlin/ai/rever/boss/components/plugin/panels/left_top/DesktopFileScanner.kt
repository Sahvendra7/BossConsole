package ai.rever.boss.components.plugin.panels.left_top

import ai.rever.boss.plugin.api.FileNodeData
import ai.rever.boss.plugin.api.NodeLoadingStateData
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

private val skippedDirectoryNames = setOf("build", "node_modules")

private fun isVisibleEntry(name: String): Boolean =
    !name.startsWith(".") && name !in skippedDirectoryNames

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
 * Streams directory entries and stops at the first visible one, so huge
 * directories don't get fully listed just to answer yes/no.
 */
actual fun directoryHasChildren(path: String): Boolean {
    return try {
        val dir = Paths.get(path)
        if (!Files.isDirectory(dir)) return false

        Files.newDirectoryStream(dir).use { stream ->
            // Apply same filter as scanFileRecursively
            stream.any { isVisibleEntry(it.fileName.toString()) }
        }
    } catch (e: Exception) {
        // Covers InvalidPathException (malformed input, e.g. a NUL byte) as well as
        // I/O errors -- File(path) never threw here, so callers rely on false-on-error.
        false
    }
}

private fun scanFileRecursively(file: File, currentDepth: Int = 0, maxDepth: Int = 5): FileNodeData {
    val isDirectory = file.isDirectory
    val shouldLoadChildren = isDirectory && currentDepth < maxDepth

    val children: List<FileNodeData> = if (shouldLoadChildren) {
        file.listFiles()
            ?.filter { isVisibleEntry(it.name) }
            // Stat each entry once up front — sort comparators re-invoke their
            // selectors, and File.isDirectory is a syscall per call.
            ?.map { it to it.isDirectory }
            ?.sortedWith(compareBy({ !it.second }, { it.first.name.lowercase() }))
            ?.map { (childFile, childIsDirectory) ->
                // For directories at the edge of our scan depth, just create a placeholder
                if (childIsDirectory && currentDepth + 1 >= maxDepth) {
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

    // When we already listed this directory (shouldLoadChildren), the filtered
    // children list is the authoritative answer -- re-streaming via
    // directoryHasChildren would just repeat the same filter over the same
    // listing. Only fall back to it when we skipped listing (depth limit).
    val hasChildren = when {
        !isDirectory -> false
        children.isNotEmpty() -> true
        shouldLoadChildren -> false
        else -> directoryHasChildren(file.absolutePath)
    }

    return FileNodeData(
        name = file.name,
        path = file.absolutePath,
        isDirectory = isDirectory,
        children = children,
        hasChildren = hasChildren,
        loadingState = loadingState,
        loadDepth = currentDepth
    )
}
