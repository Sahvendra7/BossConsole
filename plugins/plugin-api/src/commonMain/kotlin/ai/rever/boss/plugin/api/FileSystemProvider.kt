package ai.rever.boss.plugin.api

import kotlinx.serialization.Serializable

/**
 * Interface for file system data providers.
 *
 * This interface allows the CodeBase panel to be extracted to a separate module
 * while keeping the platform-specific file scanning infrastructure in composeApp.
 *
 * Usage:
 * - composeApp implements this interface with platform-specific file scanning
 * - plugin-panel-codebase depends only on this interface
 * - At registration time, composeApp provides the implementation
 */
interface FileSystemDataProvider {
    /**
     * Scan a directory and return its file tree.
     *
     * @param path Path to the directory to scan
     * @return The scanned file node, or null if the path doesn't exist
     */
    suspend fun scanDirectory(path: String): FileNodeData?

    /**
     * Scan a directory with depth control.
     *
     * @param path Path to the directory to scan
     * @param maxDepth Maximum depth to scan
     * @param startDepth Starting depth (for incremental loading)
     * @return The scanned file node, or null if the path doesn't exist
     */
    suspend fun scanDirectoryWithDepth(path: String, maxDepth: Int, startDepth: Int): FileNodeData?

    /**
     * Check if a directory has any visible children without loading them all.
     * This is a quick O(1) check for the expand indicator.
     *
     * @param path Path to the directory
     * @return True if the directory has visible children
     */
    fun directoryHasChildren(path: String): Boolean

    /**
     * Open a file in the editor.
     *
     * @param path Full path to the file
     * @param windowId The window that initiated the open request
     */
    fun openFile(path: String, windowId: String)
}

/**
 * Data class representing a file or directory node in the tree.
 */
@Serializable
data class FileNodeData(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val children: List<FileNodeData> = emptyList(),
    val hasChildren: Boolean? = null,
    val loadingState: NodeLoadingStateData = NodeLoadingStateData.UNKNOWN,
    val loadDepth: Int = 0
)

/**
 * Loading state for lazy-loaded directory nodes.
 */
@Serializable
enum class NodeLoadingStateData {
    /**
     * Haven't checked if node has children yet
     */
    UNKNOWN,

    /**
     * Currently checking if node has children
     */
    CHECKING,

    /**
     * Children have been fully loaded
     */
    LOADED
}
