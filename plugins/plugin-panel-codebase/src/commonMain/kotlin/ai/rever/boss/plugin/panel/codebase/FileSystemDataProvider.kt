package ai.rever.boss.plugin.panel.codebase

import kotlinx.coroutines.flow.StateFlow

/**
 * Provider interface for file system operations.
 *
 * This interface abstracts platform-specific file system functionality
 * to allow the CodeBase panel to be extracted to a separate module.
 */
interface FileSystemDataProvider {
    /**
     * Scan a directory and return its file tree structure.
     *
     * @param path The directory path to scan
     * @return FileNode representing the directory tree, or null if not found
     */
    suspend fun scanDirectory(path: String): FileNode?

    /**
     * Scan a directory with depth control.
     *
     * @param path The directory path to scan
     * @param maxDepth Maximum depth to scan
     * @param startDepth Starting depth for the scan
     * @return FileNode representing the directory tree, or null if not found
     */
    suspend fun scanDirectoryWithDepth(path: String, maxDepth: Int, startDepth: Int): FileNode?

    /**
     * Quick check if a directory has any children without loading them all.
     * This is IntelliJ's isAlwaysShowPlus() pattern.
     *
     * @param path The directory path to check
     * @return true if the directory has children
     */
    fun directoryHasChildren(path: String): Boolean

    /**
     * Open a file in the editor.
     *
     * @param path The file path to open
     * @param windowId The window ID to open the file in
     */
    fun openFile(path: String, windowId: String)

    /**
     * Create a new file.
     *
     * @param parentPath The parent directory path
     * @param fileName The name of the file to create
     * @return Result containing the absolute path of the created file, or failure
     */
    suspend fun createFile(parentPath: String, fileName: String): Result<String>

    /**
     * Create a new folder.
     *
     * @param parentPath The parent directory path
     * @param folderName The name of the folder to create
     * @return Result containing the absolute path of the created folder, or failure
     */
    suspend fun createFolder(parentPath: String, folderName: String): Result<String>

    /**
     * Delete a file or folder.
     *
     * @param path The path to delete
     * @return Result indicating success or failure
     */
    suspend fun delete(path: String): Result<Unit>

    /**
     * Rename a file or folder.
     *
     * @param path The current path
     * @param newName The new name (not full path, just the name)
     * @return Result containing the new absolute path, or failure
     */
    suspend fun rename(path: String, newName: String): Result<String>

    /**
     * Reveal a file or folder in the system file manager.
     *
     * @param path The path to reveal
     * @return Result indicating success or failure
     */
    fun revealInFileManager(path: String): Result<Unit>

    /**
     * Copy text to system clipboard.
     *
     * @param text The text to copy
     * @return Result indicating success or failure
     */
    fun copyToClipboard(text: String): Result<Unit>
}

/**
 * Provider interface for project management operations.
 */
interface ProjectDataProvider {
    /**
     * Recent projects list.
     */
    val recentProjects: StateFlow<List<ProjectData>>

    /**
     * Update recent projects list with a new project.
     *
     * @param project The project to add/update
     */
    fun updateRecentProjects(project: ProjectData)

    /**
     * Remove a project from the recent projects list.
     *
     * @param projectPath The project path to remove
     */
    fun removeRecentProject(projectPath: String)
}

/**
 * Data class representing a project.
 */
data class ProjectData(
    val name: String,
    val path: String,
    val lastOpened: Long = 0L
)
