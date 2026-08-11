package ai.rever.boss.components.plugin

import ai.rever.boss.plugin.PluginPersistence
import ai.rever.boss.plugin.PluginStoreSetup
import ai.rever.boss.plugin.api.PluginState
import ai.rever.boss.plugin.loader.PluginManifestReader
import ai.rever.boss.plugin.loader.PluginSignatureSidecar
import ai.rever.boss.utils.atomicMoveFrom
import ai.rever.boss.utils.logging.BossLogger
import ai.rever.boss.utils.logging.LogCategory
import java.io.File

/**
 * Desktop implementation of the store-version bridge.
 *
 * Goes to the REMOTE repository directly, never through [PluginStoreSetup.repositoryManager]. The
 * manager is local-first and its [ai.rever.boss.plugin.repository.LocalPluginRepository] synthesises
 * a row from the installed jar's own manifest, so asking it about an installed plugin answers with
 * the very local build we are trying to replace - and `downloadPlugin` resolves its source the same
 * way, so it would "download" the local file onto itself.
 *
 * The download and swap follow the rules [ai.rever.boss.plugin.StoreMissingDependencyInstaller]
 * documents at length, because the same traps apply: stream into a `.part` sibling rather than onto
 * the target (an opened output stream truncates, so a dying download would destroy the jar that is
 * running), move the jar and its `.sig` together (the signature is written beside the path the
 * download was given, so promoting the jar alone loads as unsigned), vet the declared manifest
 * before loading, and leave nothing half-installed at a name the next directory scan would pick up.
 */
actual object PluginStoreVersionBridge {
    private val logger = BossLogger.forComponent("PluginStoreVersionBridge")

    actual suspend fun lookup(pluginId: String): StoreVersionLookup {
        val store =
            PluginStoreSetup.remoteRepository
                ?: return StoreVersionLookup.Unavailable(
                    "The plugin store is not available. Check your connection and try again.",
                )
        val result = runCatching { store.getPlugin(pluginId) }
        val info =
            result.getOrNull()?.getOrNull()
                ?: return if (result.isFailure) {
                    StoreVersionLookup.Unavailable(
                        "Could not reach the plugin store: ${result.exceptionOrNull()?.message ?: "unknown error"}",
                    )
                } else {
                    // A successful lookup that found nothing is the ordinary case for a plugin that
                    // was built locally and never published, so it is reported as absence, not error.
                    StoreVersionLookup.NotPublished
                }
        val version = info.version.takeIf { it.isNotBlank() } ?: return StoreVersionLookup.NotPublished
        return StoreVersionLookup.Available(displayName = info.displayName, version = version)
    }

    actual suspend fun installStoreVersion(
        pluginId: String,
        version: String,
        manager: DynamicPluginManager,
    ): Result<String> {
        val store =
            PluginStoreSetup.remoteRepository
                ?: return failure("The plugin store is not available. Check your connection and try again.")

        // A distinct filename from the local build's, so the running jar is never the download target
        // and a failure leaves the current install untouched. Both halves are sanitised because both
        // can carry store data.
        val target =
            File(PluginStoreSetup.getPluginDir(), "${safe(pluginId.replace('.', '_'))}_${safe(version)}.jar")

        return download(store, pluginId, version, target)
            .mapCatching { swapIn(pluginId, version, target, manager).getOrThrow() }
    }

    /** Fetch and promote the store's jar, leaving it at [target] ready to load. */
    private suspend fun download(
        store: ai.rever.boss.plugin.repository.PluginRepository,
        pluginId: String,
        version: String,
        target: File,
    ): Result<Unit> {
        val part = File("${target.absolutePath}.part")
        val downloaded =
            store
                .downloadPlugin(pluginId, version, part.absolutePath)
                .getOrElse { error ->
                    discard(part.absolutePath)
                    logger.error(LogCategory.SYSTEM, "Could not download the store version", error = error)
                    return failureUnit("Could not download v$version: ${error.message ?: "unknown error"}")
                }

        val moved = runCatching { promote(downloaded, target) }
        if (moved.isFailure) {
            // Both paths, because the move may already have succeeded: a sidecar step that then threw
            // would leave an unvetted, never-loaded jar at a scannable name for the next launch.
            discard(downloaded)
            discard(target.absolutePath)
            logger.error(
                LogCategory.SYSTEM,
                "Could not move the downloaded store version into place",
                error = moved.exceptionOrNull(),
            )
            return failureUnit("Downloaded v$version but could not put it in place.")
        }
        return Result.success(Unit)
    }

    /** Vet the downloaded jar, drop the running build and load the store one. */
    private suspend fun swapIn(
        pluginId: String,
        version: String,
        target: File,
        manager: DynamicPluginManager,
    ): Result<String> {
        // Vet before loading, for the same reason the dependency installer does: nothing binds a
        // store row to the plugin id its jar declares, and `installPlugin` acts on the incoming
        // manifest (a newer api jar triggers a whole api hot swap).
        val declared = runCatching { PluginManifestReader.readFromJar(target.absolutePath) }.getOrNull()
        if (declared?.pluginId != pluginId) {
            discard(target.absolutePath)
            logger.warn(
                LogCategory.SYSTEM,
                "Refusing a store jar that declares a different plugin",
                mapOf("expected" to pluginId, "declared" to (declared?.pluginId ?: "unreadable")),
            )
            return failure("The store copy did not install as $pluginId. The store entry may be wrong.")
        }

        // Force, because this is a deliberate replacement: the point is to drop the local build.
        val unloaded = manager.uninstallPlugin(pluginId, force = true)
        if (unloaded.isFailure) {
            discard(target.absolutePath)
            return failure(
                "Could not unload the running build: ${unloaded.exceptionOrNull()?.message ?: "unknown error"}",
            )
        }

        val info =
            manager.installPlugin(target.absolutePath, enabled = true).getOrElse { error ->
                logger.error(LogCategory.SYSTEM, "Store version failed to load", error = error)
                return failure("Installed v$version but it could not load: ${error.message ?: "unknown error"}")
            }
        if (info.state != PluginState.LOADED) {
            return failure("v$version downloaded but did not start. It may not be compatible with this BOSS.")
        }

        // Record the new jar so the next launch loads it. The version comes from the jar's own
        // manifest, matching every other install path, because update checks compare against it.
        runCatching {
            PluginPersistence.addInstalledPlugin(
                pluginId = pluginId,
                jarPath = target.absolutePath,
                enabled = true,
                installedVersion = declared.version,
            )
        }.onFailure { error ->
            logger.warn(
                LogCategory.SYSTEM,
                "Installed the store version but could not record it",
                mapOf("pluginId" to pluginId, "error" to (error.message ?: "unknown")),
            )
        }

        // The local build's jar is deliberately left on disk. It is what the user was working on, and
        // PluginJarReconciler keeps one jar per plugin id at the next launch anyway; deleting
        // someone's build as a side effect of "show me the released one" would be its own bug.
        logger.info(
            LogCategory.SYSTEM,
            "Installed the store version of a plugin",
            mapOf("pluginId" to pluginId, "version" to declared.version),
        )
        return Result.success(declared.version)
    }

    /**
     * `persist` rather than `write`: the target name can already exist, and persist is the call that
     * clears a stale sidecar when the new download is unsigned. A leftover `.sig` beside fresh bytes
     * is a hard load failure, which is worse than being unsigned.
     */
    private fun promote(
        downloaded: String,
        target: File,
    ) {
        target.atomicMoveFrom(File(downloaded))
        PluginSignatureSidecar.persist(target.absolutePath, PluginSignatureSidecar.read(downloaded))
        PluginSignatureSidecar.delete(downloaded)
    }

    private fun discard(jarPath: String) {
        runCatching { File(jarPath).delete() }
        runCatching { PluginSignatureSidecar.delete(jarPath) }
    }

    private fun failure(message: String): Result<String> = Result.failure(IllegalStateException(message))

    private fun failureUnit(message: String): Result<Unit> = Result.failure(IllegalStateException(message))

    /** Keeps only what a jar name needs, so no store value can name a path. */
    private fun safe(part: String) = part.replace(Regex("[^A-Za-z0-9.-]"), "_")
}
