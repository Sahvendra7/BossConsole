package ai.rever.boss.components.bookmarks

import ai.rever.boss.components.workspaces.TabConfig
import kotlinx.serialization.Serializable
import kotlin.time.Clock

/**
 * Represents a target workspace and panel for opening a bookmark
 */
@Serializable
data class WorkspacePanelTarget(
    val workspaceName: String,
    val panelId: String? = null  // null = use active panel
)

/**
 * Represents a single bookmark (a saved tab)
 *
 * A bookmark is essentially a TabConfig with additional metadata.
 * When clicked, it opens the tab in the specified workspaces and panels,
 * or in the current workspace's active panel if not specified.
 */
@Serializable
data class Bookmark(
    val id: String = generateId(),
    val tabConfig: TabConfig,       // The tab configuration to open
    val workspaceName: String,       // Which workspace this bookmark originated from
    @Deprecated("Use targetWorkspaces instead")
    val targetWorkspaceName: String? = null, // Legacy: Target workspace to open tab in
    @Deprecated("Use targetWorkspaces instead")
    val targetPanelId: String? = null,      // Legacy: Target panel ID within workspace
    val targetWorkspaces: List<WorkspacePanelTarget> = emptyList(), // Target workspaces and panels (empty = use current)
    val notes: String = "",          // Optional user notes about the bookmark
    val tags: List<String> = emptyList(), // Tags for future filtering/organization
    val createdAt: Long = Clock.System.now().toEpochMilliseconds(),
    val lastAccessedAt: Long = 0L    // Updated when bookmark is clicked
) {
    companion object {
        fun generateId(): String = "bookmark-${Clock.System.now().toEpochMilliseconds()}"
    }

    /**
     * Update last accessed time
     */
    fun markAsAccessed(): Bookmark {
        return copy(lastAccessedAt = Clock.System.now().toEpochMilliseconds())
    }
}

/**
 * Represents a collection of bookmarks
 *
 * Collections allow users to organize bookmarks into groups.
 * The special "Favorites" collection is automatically created.
 *
 * Examples:
 * - "Favorites" (special, isFavorite = true)
 * - "Work"
 * - "Research"
 * - "Daily Sites"
 */
@Serializable
data class BookmarkCollection(
    val id: String = generateId(),
    val name: String,                // Display name
    val bookmarks: List<Bookmark> = emptyList(),
    val isFavorite: Boolean = false, // Is this the special "Favorites" collection?
    val createdAt: Long = Clock.System.now().toEpochMilliseconds()
) {
    companion object {
        fun generateId(): String = "collection-${Clock.System.now().toEpochMilliseconds()}"

        // Special collection names
        const val FAVORITES_NAME = "Favorites"
    }

    /**
     * Add a bookmark to this collection
     */
    fun addBookmark(bookmark: Bookmark): BookmarkCollection {
        return copy(bookmarks = bookmarks + bookmark)
    }

    /**
     * Remove a bookmark from this collection
     */
    fun removeBookmark(bookmarkId: String): BookmarkCollection {
        return copy(bookmarks = bookmarks.filter { it.id != bookmarkId })
    }

    /**
     * Update a bookmark in this collection
     */
    fun updateBookmark(bookmark: Bookmark): BookmarkCollection {
        return copy(bookmarks = bookmarks.map {
            if (it.id == bookmark.id) bookmark else it
        })
    }

    /**
     * Find a bookmark by ID
     */
    fun findBookmark(bookmarkId: String): Bookmark? {
        return bookmarks.find { it.id == bookmarkId }
    }
}

/**
 * Marks a workspace as favorite
 *
 * Favorite workspaces appear in the "Favorite Workspaces" section
 * of the bookmarks sidebar for quick access.
 */
@Serializable
data class FavoriteWorkspace(
    val workspaceId: String,
    val workspaceName: String,
    val markedAt: Long = Clock.System.now().toEpochMilliseconds()
) {
    companion object {
        fun create(workspaceId: String, workspaceName: String): FavoriteWorkspace {
            return FavoriteWorkspace(
                workspaceId = workspaceId,
                workspaceName = workspaceName,
                markedAt = Clock.System.now().toEpochMilliseconds()
            )
        }
    }
}
