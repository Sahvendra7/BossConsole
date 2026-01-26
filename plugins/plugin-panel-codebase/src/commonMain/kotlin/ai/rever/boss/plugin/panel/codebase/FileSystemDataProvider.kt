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
