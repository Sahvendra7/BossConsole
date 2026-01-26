package ai.rever.boss.services.bookmarks

import ai.rever.boss.components.bookmarks.BookmarkManager
import ai.rever.boss.components.bookmarks.bookmarkManager
import ai.rever.boss.plugin.api.BookmarkDataProvider
import ai.rever.boss.plugin.bookmark.Bookmark
import ai.rever.boss.plugin.bookmark.BookmarkCollection
import ai.rever.boss.plugin.bookmark.FavoriteWorkspace
import ai.rever.boss.plugin.workspace.TabConfig
import kotlinx.coroutines.flow.StateFlow

/**
 * Adapter implementation that wraps BookmarkManager for the plugin API.
 */
class BookmarkDataProviderImpl(
    private val manager: BookmarkManager = bookmarkManager
) : BookmarkDataProvider {

    override val collections: StateFlow<List<BookmarkCollection>> = manager.collections

    override val favoriteWorkspaces: StateFlow<List<FavoriteWorkspace>> = manager.favoriteWorkspaces

    // ==================== Bookmark Operations ====================

    override fun addBookmark(collectionName: String, bookmark: Bookmark) {
        manager.addBookmark(collectionName, bookmark)
    }

    override fun removeBookmark(collectionId: String, bookmarkId: String) {
        manager.removeBookmark(collectionId, bookmarkId)
    }

    override fun updateBookmark(collectionId: String, bookmark: Bookmark) {
        manager.updateBookmark(collectionId, bookmark)
    }

    override fun moveBookmark(bookmarkId: String, fromCollectionId: String, toCollectionId: String) {
        manager.moveBookmark(bookmarkId, fromCollectionId, toCollectionId)
    }

    override fun markBookmarkAsAccessed(collectionId: String, bookmarkId: String) {
        manager.markBookmarkAsAccessed(collectionId, bookmarkId)
    }

    override fun isTabBookmarked(tabConfig: TabConfig): Boolean {
        // Convert plugin TabConfig to workspace TabConfig (they're actually the same type via typealias)
        return manager.isTabBookmarked(tabConfig)
    }

    override fun findBookmarkForTab(tabConfig: TabConfig): Pair<String, String>? {
        return manager.findBookmarkForTab(tabConfig)
    }

    // ==================== Collection Operations ====================

    override fun createCollection(name: String): BookmarkCollection {
        return manager.createCollection(name)
    }

    override fun deleteCollection(collectionId: String) {
        manager.deleteCollection(collectionId)
    }

    override fun renameCollection(collectionId: String, newName: String) {
        manager.renameCollection(collectionId, newName)
    }

    // ==================== Favorite Workspace Operations ====================

    override fun addFavoriteWorkspace(workspaceId: String, workspaceName: String) {
        manager.addFavoriteWorkspace(workspaceId, workspaceName)
    }

    override fun removeFavoriteWorkspace(workspaceId: String) {
        manager.removeFavoriteWorkspace(workspaceId)
    }

    override fun isFavorite(workspaceId: String): Boolean {
        return manager.isFavorite(workspaceId)
    }
}
