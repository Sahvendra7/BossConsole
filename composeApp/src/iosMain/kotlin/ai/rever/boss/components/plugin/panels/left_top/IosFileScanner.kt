package ai.rever.boss.components.plugin.panels.left_top

// For iOS, return mock data as file system access is restricted
actual fun scanDirectory(path: String): FileNode? {
    // iOS has restricted file system access
    // Return mock data for demonstration
    return FileNode(
        name = path.substringAfterLast('/'),
        path = path,
        isDirectory = true,
        children = listOf(
            FileNode(
                name = "README.md",
                path = "$path/README.md",
                isDirectory = false,
                hasChildren = false,
                loadingState = NodeLoadingState.LOADED
            ),
            FileNode(
                name = "src",
                path = "$path/src",
                isDirectory = true,
                children = listOf(
                    FileNode(
                        name = "main.kt",
                        path = "$path/src/main.kt",
                        isDirectory = false,
                        hasChildren = false,
                        loadingState = NodeLoadingState.LOADED
                    )
                ),
                hasChildren = true,
                loadingState = NodeLoadingState.LOADED
            )
        ),
        hasChildren = true,
        loadingState = NodeLoadingState.LOADED
    )
}

actual suspend fun scanDirectoryWithDepth(path: String, maxDepth: Int, startDepth: Int): FileNode? {
    // iOS has restricted file system access
    // Return the same mock data
    return scanDirectory(path)
}

/**
 * iOS mock implementation - always returns true for directories
 */
actual fun directoryHasChildren(path: String): Boolean {
    // Mock - assume directories have children
    return true
}
