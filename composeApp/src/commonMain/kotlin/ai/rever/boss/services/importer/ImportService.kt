package ai.rever.boss.services.importer

import ai.rever.boss.plugin.api.BookmarkDataProvider
import ai.rever.boss.plugin.bookmark.Bookmark
import ai.rever.boss.plugin.workspace.TabConfig
import ai.rever.boss.services.supabase.SecretService
import ai.rever.boss.services.supabase.models.CreateSecretRequest
import ai.rever.boss.utils.WebsiteMatchingUtil
import ai.rever.boss.utils.logging.BossLogger
import ai.rever.boss.utils.logging.LogCategory
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.io.File

/**
 * Reads password and bookmark exports and writes them into BOSS.
 *
 * Everything credential-shaped passes through here, so the logging rule is
 * absolute: counts and reasons only, never a password, username or full URL.
 */
object ImportService {
    private val logger = BossLogger.forComponent("ImportService")

    /** Page size when reading existing secrets for de-duplication. */
    private const val SECRET_PAGE_SIZE = 100

    /** Upper bound on the de-duplication scan, so a huge vault can't hang the dialog. */
    private const val SECRET_SCAN_CAP = 5_000

    /**
     * Pause between per-item bookmark inserts on the fallback path.
     *
     * Only used when the installed bookmarks plugin has no bulk implementation,
     * where each insert triggers its own full rewrite of collections.json.
     */
    private const val FALLBACK_INSERT_DELAY_MS = 5L

    /** Above this, the fallback path is slow enough to be worth warning about. */
    const val FALLBACK_WARNING_THRESHOLD = 50

    // ==================== Passwords ====================

    /**
     * Create a vault entry per credential, skipping ones already present.
     *
     * There is no bulk secret endpoint, so this is one RPC per password and can
     * take a while; it checks for cancellation between items and reports
     * progress as it goes.
     */
    suspend fun importPasswords(
        passwords: List<ImportedPassword>,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): ImportResult {
        val existing = loadExistingSecretKeys()
        val skipped = mutableListOf<SkippedRow>()
        val failures = mutableListOf<String>()
        var imported = 0

        for ((index, entry) in passwords.withIndex()) {
            // A cancelled import must stop rather than race through the rest.
            if (!currentCoroutineContext().isActive) break

            val website = normaliseWebsite(entry.website)
            val key = website.lowercase() to entry.username.lowercase()

            val blocked =
                when {
                    website.isEmpty() -> SkipReason.MISSING_URL
                    key in existing -> SkipReason.ALREADY_EXISTS
                    else -> null
                }

            if (blocked != null) {
                skipped.add(SkippedRow(index + 1, blocked, displayLabel(website, entry.username)))
            } else {
                SecretService
                    .createSecret(
                        CreateSecretRequest(
                            website = website,
                            username = entry.username,
                            password = entry.password,
                            notes = entry.notes,
                        ),
                    ).fold(
                        onSuccess = {
                            imported++
                            // Guards against duplicates inside the same file.
                            existing.add(key)
                        },
                        onFailure = { error ->
                            // The message is ours, never the credential's.
                            failures.add("${displayLabel(website, entry.username)}: ${error.message ?: "failed"}")
                        },
                    )
            }

            onProgress(index + 1, passwords.size)
        }

        logger.info(
            LogCategory.AUTH,
            "Password import finished",
            mapOf("imported" to imported, "skipped" to skipped.size, "failed" to failures.size),
        )
        return ImportResult(imported = imported, skipped = skipped, failures = failures)
    }

    /** Every (website, username) already in the vault, lowercased. */
    private suspend fun loadExistingSecretKeys(): MutableSet<Pair<String, String>> {
        val keys = mutableSetOf<Pair<String, String>>()
        var offset = 0

        while (offset < SECRET_SCAN_CAP) {
            val page =
                SecretService
                    .getUserSecrets(limit = SECRET_PAGE_SIZE, offset = offset)
                    .getOrElse { error ->
                        // A failed scan only costs de-duplication, so carry on and
                        // let the RPC reject genuine duplicates.
                        logger.warn(
                            LogCategory.AUTH,
                            "Could not read existing secrets for de-duplication",
                            mapOf("reason" to (error.message ?: "unknown")),
                        )
                        return keys
                    }

            page.data.forEach { secret ->
                keys.add(normaliseWebsite(secret.website).lowercase() to secret.username.lowercase())
            }

            if (!page.hasMore || page.data.isEmpty()) break
            offset += SECRET_PAGE_SIZE
        }
        return keys
    }

    private fun normaliseWebsite(raw: String): String = WebsiteMatchingUtil.extractMainDomain(raw) ?: raw.trim()

    // ==================== Bookmarks ====================

    /**
     * True when [provider]'s plugin implements bulk insert itself.
     *
     * `addBookmarks` is an interface method with a default body, so calling it
     * always succeeds — an older plugin silently inherits the default, which
     * loops the single-item path and rewrites collections.json once per
     * bookmark. Reflection is the only way to tell the two apart, and the
     * difference matters enough to the user (speed, and on plugins older than
     * the atomic-write fix, safety) to be worth surfacing.
     */
    fun supportsBulkBookmarkInsert(provider: BookmarkDataProvider): Boolean =
        runCatching {
            val declared =
                provider.javaClass
                    .getMethod("addBookmarks", String::class.java, List::class.java)
            !declared.declaringClass.isInterface
        }.getOrDefault(false)

    /**
     * Write [bookmarks] into collections, one collection per source folder.
     *
     * @param provider null when the bookmarks plugin failed to load
     */
    suspend fun importBookmarks(
        bookmarks: List<ImportedBookmark>,
        provider: BookmarkDataProvider?,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): ImportResult {
        if (provider == null) {
            return ImportResult(
                failures = listOf("The Bookmarks tool isn't available, so bookmarks couldn't be imported."),
            )
        }

        val bulk = supportsBulkBookmarkInsert(provider)
        val byFolder = bookmarks.groupBy { it.folder?.takeIf { name -> name.isNotBlank() } ?: DEFAULT_COLLECTION }
        val failures = mutableListOf<String>()
        var imported = 0
        var done = 0

        for ((collectionName, entries) in byFolder) {
            if (!currentCoroutineContext().isActive) break

            val models = entries.mapIndexed { index, entry -> entry.toBookmark(collectionName, index) }

            runCatching {
                if (bulk) {
                    provider.addBookmarks(collectionName, models)
                } else {
                    insertOneByOne(provider, collectionName, models)
                }
            }.fold(
                onSuccess = { imported += models.size },
                onFailure = { error ->
                    failures.add("$collectionName: ${error.message ?: "failed"}")
                },
            )

            done += models.size
            onProgress(done, bookmarks.size)
        }

        logger.info(
            LogCategory.GENERAL,
            "Bookmark import finished",
            mapOf(
                "imported" to imported,
                "collections" to byFolder.size,
                "failed" to failures.size,
                "bulkPath" to bulk,
            ),
        )
        return ImportResult(imported = imported, failures = failures)
    }

    /**
     * Fallback for plugins with no bulk implementation.
     *
     * `addBookmark` no-ops when the named collection is absent, so the
     * collection has to exist first — and `createCollection` appends
     * unconditionally rather than get-or-create, so check before creating or a
     * duplicate empty collection is left behind.
     *
     * The pause between inserts spaces out the per-item file rewrites this path
     * triggers.
     */
    private suspend fun insertOneByOne(
        provider: BookmarkDataProvider,
        collectionName: String,
        models: List<Bookmark>,
    ) {
        if (provider.collections.value.none { it.name == collectionName }) {
            provider.createCollection(collectionName)
        }
        models.forEach { bookmark ->
            if (!currentCoroutineContext().isActive) return
            provider.addBookmark(collectionName, bookmark)
            delay(FALLBACK_INSERT_DELAY_MS)
        }
    }

    /** Collection used for bookmarks the export had at the top level. */
    const val DEFAULT_COLLECTION = "Imported"

    private fun ImportedBookmark.toBookmark(
        collectionName: String,
        index: Int,
    ): Bookmark =
        Bookmark(
            // Bookmark.generateId() is a bare millisecond timestamp, so a bulk
            // insert would hand hundreds of entries the same id — and
            // removeBookmark filters by id, so deleting one would delete them
            // all. Build something unique per entry instead.
            id = "imported-${collectionName.hashCode()}-$index-${url.hashCode()}",
            tabConfig = TabConfig(type = "browser", title = title, url = url),
            workspaceName = "",
        )
}
