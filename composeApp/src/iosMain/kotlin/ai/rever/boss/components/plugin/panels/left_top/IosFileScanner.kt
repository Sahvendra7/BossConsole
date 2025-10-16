package ai.rever.boss.components.plugin.panels.left_top

// For iOS, return mock data as file system access is restricted
actual fun scanDirectory(path: String): FileNode? {
    // iOS has restricted file system access
    // Return mock data for demonstration
    return FileNode(
        name = path.substringAfterLast('/'),
        path = path,
        isDirectory = true,
        children = mutableListOf(
            FileNode(
                name = "README.md",
                path = "$path/README.md",
                isDirectory = false
            ),
            FileNode(
                name = "src",
                path = "$path/src",
                isDirectory = true,
                children = mutableListOf(
                    FileNode(
                        name = "main.kt",
                        path = "$path/src/main.kt",
                        isDirectory = false
                    )
                ),
                isLoaded = true
            )
        ),
        isLoaded = true
    )
}

actual suspend fun scanDirectoryWithDepth(path: String, maxDepth: Int, startDepth: Int): FileNode? {
    // iOS has restricted file system access
    // Return the same mock data
    return scanDirectory(path)
}
