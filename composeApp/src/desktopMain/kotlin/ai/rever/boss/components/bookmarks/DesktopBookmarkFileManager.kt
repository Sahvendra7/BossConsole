package ai.rever.boss.components.bookmarks

import ai.rever.boss.utils.logging.BossLogger
import ai.rever.boss.utils.logging.LogCategory
import ai.rever.boss.utils.SystemUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Paths

/**
 * Desktop implementation of BookmarkFileManager
 *
 * Stores bookmark data in ~/Documents/BOSS/bookmarks/:
 * - collections.json: All bookmark collections
 * - favorite-workspaces.json: Favorite workspace IDs
 */
actual class BookmarkFileManager {
    private val logger = BossLogger.forComponent("BookmarkFileManager")

    actual fun getBookmarksDirectory(): String {
        val userHome = SystemUtils.getUserHome()
        return Paths.get(
            userHome,
            "Documents",
            BookmarkFileManagerCommon.getDefaultBookmarksDirectoryName()
        ).toString()
    }

    actual suspend fun ensureBookmarksDirectory(): Boolean = withContext(Dispatchers.IO) {
        try {
            val dir = File(getBookmarksDirectory())
            if (!dir.exists()) {
                dir.mkdirs()
            }
            dir.exists() && dir.isDirectory
        } catch (e: Exception) {
            logger.warn(LogCategory.FILE, "Error ensuring bookmarks directory", error = e)
            false
        }
    }

    actual suspend fun saveCollections(collections: List<BookmarkCollection>): Boolean =
        withContext(Dispatchers.IO) {
            try {
                ensureBookmarksDirectory()

                val filePath = Paths.get(
                    getBookmarksDirectory(),
                    BookmarkFileManagerCommon.COLLECTIONS_FILE
                ).toString()

                val file = File(filePath)

                // Serialize collections
                val json = BookmarkSerializer.serializeCollections(collections)

                // Write to file
                file.writeText(json)

                true
            } catch (e: Exception) {
                logger.warn(LogCategory.FILE, "Error saving collections", error = e)
                false
            }
        }

    actual suspend fun loadCollections(): List<BookmarkCollection> =
        withContext(Dispatchers.IO) {
            try {
                val filePath = Paths.get(
                    getBookmarksDirectory(),
                    BookmarkFileManagerCommon.COLLECTIONS_FILE
                ).toString()

                val file = File(filePath)

                if (!file.exists()) {
                    return@withContext emptyList()
                }

                val json = file.readText()
                BookmarkSerializer.deserializeCollections(json)
            } catch (e: Exception) {
                logger.warn(LogCategory.FILE, "Error loading collections", error = e)
                emptyList()
            }
        }

    actual suspend fun saveFavoriteWorkspaces(favorites: List<FavoriteWorkspace>): Boolean =
        withContext(Dispatchers.IO) {
            try {
                ensureBookmarksDirectory()

                val filePath = Paths.get(
                    getBookmarksDirectory(),
                    BookmarkFileManagerCommon.FAVORITE_WORKSPACES_FILE
                ).toString()

                val file = File(filePath)

                // Serialize favorite workspaces
                val json = BookmarkSerializer.serializeFavoriteWorkspaces(favorites)

                // Write to file
                file.writeText(json)

                true
            } catch (e: Exception) {
                logger.warn(LogCategory.FILE, "Error saving favorite workspaces", error = e)
                false
            }
        }

    actual suspend fun loadFavoriteWorkspaces(): List<FavoriteWorkspace> =
        withContext(Dispatchers.IO) {
            try {
                val filePath = Paths.get(
                    getBookmarksDirectory(),
                    BookmarkFileManagerCommon.FAVORITE_WORKSPACES_FILE
                ).toString()

                val file = File(filePath)

                if (!file.exists()) {
                    return@withContext emptyList()
                }

                val json = file.readText()
                BookmarkSerializer.deserializeFavoriteWorkspaces(json)
            } catch (e: Exception) {
                logger.warn(LogCategory.FILE, "Error loading favorite workspaces", error = e)
                emptyList()
            }
        }
}
