package ai.rever.boss.plugin

import ai.rever.boss.components.plugin.PluginBuildInfo
import ai.rever.boss.plugin.loader.PluginSignatureSidecar
import ai.rever.boss.utils.logging.BossLogger
import ai.rever.boss.utils.logging.LogCategory
import java.io.File

/** What a previous load recorded about a plugin's bytes, or null when nothing has. */
data class RecordedBuild(
    val jarPath: String,
    val buildStamp: Long?,
    val buildTag: String?,
)

/**
 * Everything the probe needs from outside itself, with production defaults.
 *
 * Parameters rather than a filesystem override, for the reason `PersistCrashDisableTest` spells out:
 * [PluginPersistence] resolves its file from `PluginStoreSetup.getPluginDir()`, so a test that let it
 * do so would rewrite the developer's real `installed.json`.
 */
class BuildProbeHooks(
    val mtimeOf: (jarPath: String) -> Long? = { path ->
        runCatching { File(path).lastModified() }.getOrDefault(0L).takeIf { it > 0L }
    },
    val sidecarPresent: (jarPath: String) -> Boolean = { path ->
        runCatching { PluginSignatureSidecar.read(path) != null }.getOrDefault(false)
    },
    val recordedBuild: (pluginId: String) -> RecordedBuild? = { id ->
        PluginPersistence.getInstalledPlugin(id)?.let { RecordedBuild(it.jarPath, it.buildStamp, it.buildTag) }
    },
    val record: (pluginId: String, jarPath: String, buildStamp: Long?, buildTag: String?, version: String) -> Unit =
        { id, jarPath, buildStamp, buildTag, version ->
            PluginPersistence.recordBuild(
                pluginId = id,
                jarPath = jarPath,
                buildStamp = buildStamp,
                buildTag = buildTag,
                installedVersion = version,
            )
        },
)

/**
 * Decides which build of a plugin just loaded, and persists the verdict.
 *
 * Runs on every successful install, which is the one choke point cold start, update, reload and the
 * evolver's hot reload all share.
 *
 * Two signals, both already on disk - nothing new has to be tracked at runtime:
 *
 * - **A `<jar>.sig` sidecar means the store vetted these exact bytes.** A sidecar that is present
 *   but invalid hard-fails the load, so on a plugin that is *running*, presence implies verified.
 *   Locally built jars simply have none.
 * - **A jar whose mtime is newer than the stamp recorded at its last load was overwritten in
 *   place**, which is exactly what an in-place hot reload does. The stamp is persisted, so the
 *   verdict survives a restart - after one, the file is not modified again, so only the recorded tag
 *   can still say what happened.
 */
object PluginBuildProbe {
    private val logger = BossLogger.forComponent("PluginBuildProbe")

    const val TAG_DEBUG = "debug"
    const val TAG_HOT = "hot"

    fun probe(
        pluginId: String,
        displayName: String,
        version: String,
        jarPath: String,
        hooks: BuildProbeHooks = BuildProbeHooks(),
    ): PluginBuildInfo {
        val mtime = hooks.mtimeOf(jarPath)
        val storeVetted = hooks.sidecarPresent(jarPath)
        // Only a row for the SAME file can say anything about these bytes; a different jar is a
        // different install.
        val previous = hooks.recordedBuild(pluginId)?.takeIf { it.jarPath == jarPath }

        val reloadStamp =
            resolveReloadStamp(
                storeVetted = storeVetted,
                jarMtime = mtime,
                recordedStamp = previous?.buildStamp,
                recordedTag = previous?.buildTag,
            )

        val info =
            PluginBuildInfo(
                pluginId = pluginId,
                displayName = displayName,
                version = version,
                storeVetted = storeVetted,
                reloadStamp = reloadStamp,
            )

        hooks.record(pluginId, jarPath, mtime ?: previous?.buildStamp, tagFor(info), version)

        if (info.isTagged) {
            logger.info(
                LogCategory.SYSTEM,
                "Plugin is not running the store build",
                mapOf(
                    "pluginId" to pluginId,
                    "version" to info.displayVersion,
                    "reason" to info.description,
                ),
            )
        }
        return info
    }

    /** The persisted form of a verdict. Null means a released build, which carries no tag. */
    fun tagFor(info: PluginBuildInfo): String? =
        when {
            info.reloadStamp != null -> TAG_HOT
            !info.storeVetted -> TAG_DEBUG
            else -> null
        }

    /**
     * When these bytes replaced the ones a previous load recorded, and what to stamp it with.
     *
     * Pure, so the verdict is testable without a filesystem or an `installed.json` - the same shape
     * as `resolveReloadJarPath`.
     */
    fun resolveReloadStamp(
        storeVetted: Boolean,
        jarMtime: Long?,
        recordedStamp: Long?,
        recordedTag: String?,
    ): Long? =
        when {
            // A store-signed jar is a released build by construction: the signature covers this exact
            // hash, and a locally rebuilt jar cannot carry a valid one (a stale sidecar hard-fails
            // the load instead). So it can never be a hot reload - which is also what makes
            // installing the store version clear the tag.
            storeVetted -> null

            jarMtime == null || recordedStamp == null -> null

            // Overwritten since we last loaded it.
            jarMtime > recordedStamp -> jarMtime

            // The same bytes we already judged hot, seen again across a restart.
            recordedTag == TAG_HOT -> recordedStamp

            else -> null
        }
}
