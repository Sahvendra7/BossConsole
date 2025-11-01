package ai.rever.boss.components.bookmarks

/**
 * Manages file-based bookmark storage
 *
 * Stores bookmarks in two files:
 * - collections.json: All bookmark collections (including "Favorites")
 * - favorite-workspaces.json: List of favorite workspace IDs
 *
 * Storage location: ~/Documents/BOSS/bookmarks/
 */
expect class BookmarkFileManager() {
    /**
     * Get the bookmarks directory path
     *
     * @return Full path to bookmarks directory (e.g., ~/Documents/BOSS/bookmarks/)
     */
    fun getBookmarksDirectory(): String

    /**
     * Ensure the bookmarks directory exists
     *
     * @return true if directory exists or was created successfully
     */
    suspend fun ensureBookmarksDirectory(): Boolean

    /**
     * Save bookmark collections to file
     *
     * Saves all collections to collections.json
     *
     * @param collections List of bookmark collections to save
     * @return true if saved successfully
     */
    suspend fun saveCollections(collections: List<BookmarkCollection>): Boolean

    /**
     * Load bookmark collections from file
     *
     * Loads from collections.json
     *
     * @return List of bookmark collections, empty list if file doesn't exist
     */
    suspend fun loadCollections(): List<BookmarkCollection>

    /**
     * Save favorite workspaces to file
     *
     * Saves to favorite-workspaces.json
     *
     * @param favorites List of favorite workspaces to save
     * @return true if saved successfully
     */
    suspend fun saveFavoriteWorkspaces(favorites: List<FavoriteWorkspace>): Boolean

    /**
     * Load favorite workspaces from file
     *
     * Loads from favorite-workspaces.json
     *
     * @return List of favorite workspaces, empty list if file doesn't exist
     */
    suspend fun loadFavoriteWorkspaces(): List<FavoriteWorkspace>
}

/**
 * Common bookmark file manager functionality
 */
object BookmarkFileManagerCommon {
    /**
     * Get the default bookmarks directory name
     */
    fun getDefaultBookmarksDirectoryName(): String = "BOSS/bookmarks"

    /**
     * Bookmark collections file name
     */
    const val COLLECTIONS_FILE = "collections.json"

    /**
     * Favorite workspaces file name
     */
    const val FAVORITE_WORKSPACES_FILE = "favorite-workspaces.json"
}
