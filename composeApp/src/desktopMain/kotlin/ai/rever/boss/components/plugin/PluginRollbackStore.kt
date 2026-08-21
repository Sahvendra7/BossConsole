package ai.rever.boss.components.plugin

import ai.rever.boss.plugin.loader.PluginManifestReader
import ai.rever.boss.plugin.loader.PluginSignatureSidecar
import ai.rever.boss.utils.logging.BossLogger
import ai.rever.boss.utils.logging.LogCategory
import java.io.File

/**
 * Keeps the jar an install replaced, so there is a way back from a version that will not load.
 *
 * **Why this is needed at all.** Every install path here promotes a downloaded jar over the target
 * with `atomicMoveFrom`, and `discardFiles` deletes what it rejects - so before this, replacing a
 * plugin destroyed the only copy of the version that worked. That is fine while the new version
 * loads. It is not fine when the new version declares a floor this build cannot meet: the loader
 * refuses it, the plugin is gone, and nothing on disk can bring it back.
 *
 * That is not hypothetical. fluck-browser 1.2.22 shipped requiring BOSS 9.4.23 against a current
 * release of 9.4.22, so hosts that took the update lost their browser tab, and the recovery was to
 * know the jar had been overwritten in place, find the previous release on GitHub, and put it back
 * by hand. One kept file makes that a button.
 *
 * **Deliberately one generation deep.** A history would be a directory of stale jars nobody prunes,
 * each a full copy of a plugin (fluck-browser's is ~5 MB). One is what recovery needs: the state
 * immediately before the change that broke it.
 *
 * The copy sits beside the jar as `<name>.jar.rollback` with its signature sidecar as
 * `<name>.jar.rollback.sig`. The suffix deliberately does not end in `.jar`, so the plugin directory
 * scan ignores it - the same reason `StoreMissingDependencyInstaller` streams to `.jar.part`.
 */
internal object PluginRollbackStore {
    private val logger = BossLogger.forComponent("PluginRollbackStore")

    private const val SUFFIX = ".rollback"

    private fun rollbackFor(jarPath: String) = File(jarPath + SUFFIX)

    /**
     * Where the kept version number is written, next to the copy.
     *
     * The version is recorded at snapshot time rather than read back out of the copy, because
     * `PluginManifestReader.readFromJar` refuses any path not ending in `.jar` - "File is not a
     * JAR" - and the whole point of the `.rollback` suffix is that this file does NOT look like a
     * jar to a directory scan. The two requirements are in direct conflict, and a text file settles
     * it more cheaply than opening a zip would anyway.
     */
    private fun versionFileFor(jarPath: String) = File(jarPath + SUFFIX + ".version")

    /**
     * Snapshot [jarPath] before something replaces it. No-op when there is nothing there yet.
     *
     * Copy rather than move: the caller is about to promote over this path and must not be handed a
     * missing file if the snapshot fails. A failure here is logged and swallowed for the same
     * reason - losing the ability to roll back is worse than an install that does not happen, but
     * only just, and an install that fails because its backup failed helps nobody.
     */
    fun snapshot(jarPath: String) {
        val jar = File(jarPath)
        if (!jar.isFile) return
        val rollback = rollbackFor(jarPath)
        // Read from the LIVE jar, whose name ends in .jar, before it is replaced. See
        // versionFileFor for why it cannot be read back from the copy.
        val version = runCatching { PluginManifestReader.readFromJar(jarPath).version }.getOrNull()
        runCatching {
            jar.copyTo(rollback, overwrite = true)
            if (version != null) {
                versionFileFor(jarPath).writeText(version)
            } else {
                // No version means the button cannot name what it will restore, so the copy is not
                // offered at all rather than offered blind.
                runCatching { versionFileFor(jarPath).delete() }
            }
            // The sidecar travels with it or the restore is unverifiable: a jar whose signature file
            // belongs to different bytes hard-fails the load, which is worse than being unsigned.
            PluginSignatureSidecar.read(jarPath)?.let { sig ->
                PluginSignatureSidecar.persist(rollback.absolutePath, sig)
            } ?: PluginSignatureSidecar.delete(rollback.absolutePath)
        }.onFailure { e ->
            logger.warn(
                LogCategory.SYSTEM,
                "Could not keep a rollback copy; this install will not be reversible",
                mapOf("jarPath" to jarPath),
                error = e,
            )
        }
    }

    /** The version held in the rollback copy, or null when there is none or it cannot be named. */
    fun availableVersion(jarPath: String): String? {
        // Both files, because either alone is not a usable offer: bytes with no recorded version
        // cannot label a button, and a version with no bytes cannot be restored.
        val hasBoth = rollbackFor(jarPath).isFile && versionFileFor(jarPath).isFile
        return if (!hasBoth) {
            null
        } else {
            runCatching { versionFileFor(jarPath).readText().trim().takeIf { it.isNotEmpty() } }.getOrNull()
        }
    }

    /**
     * Put the rollback copy back at [jarPath].
     *
     * The copy is KEPT, not moved, so a restore that is itself interrupted has not consumed the only
     * good jar - and so a second attempt after a failed load is possible. Returns the version now in
     * place, or null when there was nothing to restore.
     */
    fun restore(jarPath: String): String? {
        val rollback = rollbackFor(jarPath)
        if (!rollback.isFile) return null
        val version = availableVersion(jarPath)
        return runCatching {
            rollback.copyTo(File(jarPath), overwrite = true)
            PluginSignatureSidecar
                .read(rollback.absolutePath)
                ?.let { PluginSignatureSidecar.persist(jarPath, it) }
                // No signature for the restored bytes is better than the WRONG one: the sidecar
                // still on disk belongs to the version being rolled back FROM, and leaving it there
                // fails the load outright.
                ?: PluginSignatureSidecar.delete(jarPath)
            version
        }.onFailure { e ->
            logger.error(
                LogCategory.SYSTEM,
                "Could not restore the rollback copy",
                mapOf("jarPath" to jarPath),
                error = e,
            )
        }.getOrNull()
    }

    /** Drop the copy. For an uninstall, where the plugin itself is going away. */
    fun discard(jarPath: String) {
        runCatching { rollbackFor(jarPath).delete() }
        runCatching { versionFileFor(jarPath).delete() }
        runCatching { PluginSignatureSidecar.delete(rollbackFor(jarPath).absolutePath) }
    }
}
