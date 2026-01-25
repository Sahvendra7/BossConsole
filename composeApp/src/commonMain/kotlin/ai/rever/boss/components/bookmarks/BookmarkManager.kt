package ai.rever.boss.components.bookmarks

import ai.rever.boss.utils.logging.BossLogger
import ai.rever.boss.utils.logging.LogCategory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Manages bookmark collections and favorite workspaces
 *
 * Provides reactive state flows for UI and handles persistence.
 * Automatically creates the "Favorites" collection on first run.
 *
 * Usage:
 * ```kotlin
 * // Access global instance
 * val bookmarkManager = bookmarkManager
 *
 * // Observe collections
 * val collections by bookmarkManager.collections.collectAsState()
 *
 * // Add bookmark
 * bookmarkManager.addBookmark("Favorites", bookmark)
 * ```
 */
class BookmarkManager {
    private val logger = BossLogger.forComponent("BookmarkManager")
    private val fileManager = BookmarkFileManager()
    private val scope = CoroutineScope(Dispatchers.Default)

    private val _collections = MutableStateFlow<List<BookmarkCollection>>(emptyList())
    val collections: StateFlow<List<BookmarkCollection>> = _collections.asStateFlow()

    private val _favoriteWorkspaces = MutableStateFlow<List<FavoriteWorkspace>>(emptyList())
    val favoriteWorkspaces: StateFlow<List<FavoriteWorkspace>> = _favoriteWorkspaces.asStateFlow()

    init {
        // Load bookmarks from disk
        loadAllData()
    }

    /**
     * Load all bookmark data from disk
     */
    private fun loadAllData() {
        scope.launch {
            try {
                // Load collections
                val loaded = fileManager.loadCollections()

                // Ensure "Favorites" collection exists
                if (loaded.none { it.name == BookmarkCollection.FAVORITES_NAME }) {
                    val favorites = BookmarkCollection(
                        name = BookmarkCollection.FAVORITES_NAME,
                        isFavorite = true
                    )
                    _collections.value = listOf(favorites) + loaded
                    saveCollectionsToFile()
                } else {
                    _collections.value = loaded
                }

                // Load favorite workspaces
                _favoriteWorkspaces.value = fileManager.loadFavoriteWorkspaces()
            } catch (e: Exception) {
                logger.warn(LogCategory.UI, "Error loading bookmarks", error = e)
                // Initialize with default "Favorites" collection on error
                _collections.value = listOf(
                    BookmarkCollection(
                        name = BookmarkCollection.FAVORITES_NAME,
                        isFavorite = true
                    )
                )
            }
        }
    }

    // ==================== Bookmark Operations ====================

    /**
     * Add a bookmark to a collection
     */
    fun addBookmark(collectionName: String, bookmark: Bookmark) {
        val collections = _collections.value.toMutableList()
        val index = collections.indexOfFirst { it.name == collectionName }

        if (index >= 0) {
            collections[index] = collections[index].addBookmark(bookmark)
            _collections.value = collections
            saveCollectionsToFile()
        }
    }

    /**
     * Remove a bookmark from a collection
     */
    fun removeBookmark(collectionId: String, bookmarkId: String) {
        val collections = _collections.value.toMutableList()
        val index = collections.indexOfFirst { it.id == collectionId }

        if (index >= 0) {
            collections[index] = collections[index].removeBookmark(bookmarkId)
            _collections.value = collections
            saveCollectionsToFile()
        }
    }

    /**
     * Check if a tab is already bookmarked in any collection
     */
    fun isTabBookmarked(tabConfig: ai.rever.boss.components.workspaces.TabConfig): Boolean {
        return _collections.value.any { collection ->
            collection.bookmarks.any { bookmark ->
                bookmark.tabConfig.type == tabConfig.type &&
                bookmark.tabConfig.title == tabConfig.title &&
                bookmark.tabConfig.url == tabConfig.url &&
                bookmark.tabConfig.filePath == tabConfig.filePath
            }
        }
    }

    /**
     * Find which collection and bookmark ID contain this tab
     * Returns Pair(collectionId, bookmarkId) or null if not found
     */
    fun findBookmarkForTab(tabConfig: ai.rever.boss.components.workspaces.TabConfig): Pair<String, String>? {
        _collections.value.forEach { collection ->
            collection.bookmarks.firstOrNull { bookmark ->
                bookmark.tabConfig.type == tabConfig.type &&
                bookmark.tabConfig.title == tabConfig.title &&
                bookmark.tabConfig.url == tabConfig.url &&
                bookmark.tabConfig.filePath == tabConfig.filePath
            }?.let { bookmark ->
                return Pair(collection.id, bookmark.id)
            }
        }
        return null
    }

    /**
     * Update a bookmark in a collection
     */
    fun updateBookmark(collectionId: String, bookmark: Bookmark) {
        val collections = _collections.value.toMutableList()
        val index = collections.indexOfFirst { it.id == collectionId }

        if (index >= 0) {
            collections[index] = collections[index].updateBookmark(bookmark)
            _collections.value = collections
            saveCollectionsToFile()
        }
    }

    /**
     * Move a bookmark from one collection to another
     */
    fun moveBookmark(bookmarkId: String, fromCollectionId: String, toCollectionId: String) {
        val collections = _collections.value.toMutableList()
        val fromIndex = collections.indexOfFirst { it.id == fromCollectionId }
        val toIndex = collections.indexOfFirst { it.id == toCollectionId }

        if (fromIndex >= 0 && toIndex >= 0) {
            val bookmark = collections[fromIndex].findBookmark(bookmarkId)
            if (bookmark != null) {
                collections[fromIndex] = collections[fromIndex].removeBookmark(bookmarkId)
                collections[toIndex] = collections[toIndex].addBookmark(bookmark)
                _collections.value = collections
                saveCollectionsToFile()
            }
        }
    }

    /**
     * Mark a bookmark as accessed (updates lastAccessedAt timestamp)
     */
    fun markBookmarkAsAccessed(collectionId: String, bookmarkId: String) {
        val collections = _collections.value.toMutableList()
        val index = collections.indexOfFirst { it.id == collectionId }

        if (index >= 0) {
            val bookmark = collections[index].findBookmark(bookmarkId)
            if (bookmark != null) {
                collections[index] = collections[index].updateBookmark(bookmark.markAsAccessed())
                _collections.value = collections
                saveCollectionsToFile()
            }
        }
    }

    // ==================== Collection Operations ====================

    /**
     * Create a new bookmark collection
     */
    fun createCollection(name: String): BookmarkCollection {
        val collection = BookmarkCollection(name = name)
        _collections.value = _collections.value + collection
        saveCollectionsToFile()
        return collection
    }

    /**
     * Delete a bookmark collection
     *
     * Cannot delete the special "Favorites" collection.
     */
    fun deleteCollection(collectionId: String) {
        val collection = _collections.value.find { it.id == collectionId }

        // Cannot delete "Favorites" collection
        if (collection != null && !collection.isFavorite) {
            _collections.value = _collections.value.filter { it.id != collectionId }
            saveCollectionsToFile()
        }
    }

    /**
     * Rename a bookmark collection
     */
    fun renameCollection(collectionId: String, newName: String) {
        val collections = _collections.value.toMutableList()
        val index = collections.indexOfFirst { it.id == collectionId }

        if (index >= 0) {
            collections[index] = collections[index].copy(name = newName)
            _collections.value = collections
            saveCollectionsToFile()
        }
    }

    /**
     * Get the "Favorites" collection
     *
     * Guaranteed to always exist.
     */
    fun getFavoritesCollection(): BookmarkCollection {
        return _collections.value.find { it.isFavorite }
            ?: BookmarkCollection(
                name = BookmarkCollection.FAVORITES_NAME,
                isFavorite = true
            )
    }

    // ==================== Favorite Workspace Operations ====================

    /**
     * Add a workspace to favorites
     */
    fun addFavoriteWorkspace(workspaceId: String, workspaceName: String) {
        val current = _favoriteWorkspaces.value
        if (current.none { it.workspaceId == workspaceId }) {
            _favoriteWorkspaces.value = current + FavoriteWorkspace.create(workspaceId, workspaceName)
            saveFavoriteWorkspacesToFile()
        }
    }

    /**
     * Remove a workspace from favorites
     */
    fun removeFavoriteWorkspace(workspaceId: String) {
        _favoriteWorkspaces.value = _favoriteWorkspaces.value.filter { it.workspaceId != workspaceId }
        saveFavoriteWorkspacesToFile()
    }

    /**
     * Check if a workspace is favorited
     */
    fun isFavorite(workspaceId: String): Boolean {
        return _favoriteWorkspaces.value.any { it.workspaceId == workspaceId }
    }

    // ==================== Persistence ====================

    /**
     * Save collections to file
     */
    private fun saveCollectionsToFile() {
        scope.launch {
            fileManager.saveCollections(_collections.value)
        }
    }

    /**
     * Save favorite workspaces to file
     */
    private fun saveFavoriteWorkspacesToFile() {
        scope.launch {
            fileManager.saveFavoriteWorkspaces(_favoriteWorkspaces.value)
        }
    }
}

// Global instance
val bookmarkManager = BookmarkManager()
