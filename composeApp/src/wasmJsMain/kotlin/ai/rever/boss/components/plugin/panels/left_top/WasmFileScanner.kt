package ai.rever.boss.components.plugin.panels.left_top

// For WebAssembly, return mock data as file system access is not available
actual fun scanDirectory(path: String): FileNode? {
    // Browser doesn't have direct file system access
    // Return mock data for demonstration
    return FileNode(
        name = path.substringAfterLast('/'),
        path = path,
        isDirectory = true,
        children = mutableListOf(
            FileNode(
                name = "index.html",
                path = "$path/index.html",
                isDirectory = false
            ),
            FileNode(
                name = "js",
                path = "$path/js",
                isDirectory = true,
                children = mutableListOf(
                    FileNode(
                        name = "app.js",
                        path = "$path/js/app.js",
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
    // Browser doesn't have direct file system access
    // Return the same mock data
    return scanDirectory(path)
}
