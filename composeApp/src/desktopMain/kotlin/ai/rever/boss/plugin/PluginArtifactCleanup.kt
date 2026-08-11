package ai.rever.boss.plugin

import ai.rever.boss.plugin.loader.PluginSignatureSidecar
import ai.rever.boss.utils.logging.BossLogger
import ai.rever.boss.utils.logging.LogCategory
import java.io.File

/**
 * Removes what an uninstalled plugin leaves behind.
 *
 * Three things, and until now nothing in the host did any of them - only the Toolbox plugin deleted
 * jars, and no code anywhere called [PluginPersistence.removeInstalledPlugin], so every uninstall
 * left a row pointing at a file that may since have been deleted:
 *
 * - **The jar**, or the plugin comes straight back on the next directory scan.
 * - **The `.sig` sidecar**, because a signature left beside a filename that is later reused by a
 *   different download hard-fails that load - worse than being unsigned.
 * - **The `installed.json` row**, or the persisted-load pass keeps trying to load a missing file.
 */
object PluginArtifactCleanup {
    private val logger = BossLogger.forComponent("PluginArtifactCleanup")

    fun remove(
        pluginId: String,
        jarPath: String,
    ) {
        val jarDeleted =
            if (jarPath.isBlank()) {
                false
            } else {
                runCatching { File(jarPath).takeIf { it.exists() }?.delete() == true }.getOrDefault(false)
            }
        if (jarPath.isNotBlank()) {
            runCatching { PluginSignatureSidecar.delete(jarPath) }
        }
        runCatching { PluginPersistence.removeInstalledPlugin(pluginId) }
            .onFailure { error ->
                logger.warn(
                    LogCategory.SYSTEM,
                    "Uninstalled a plugin but could not update installed.json",
                    mapOf("pluginId" to pluginId, "error" to (error.message ?: "unknown")),
                )
            }
        logger.info(
            LogCategory.SYSTEM,
            "Removed plugin artifacts",
            mapOf("pluginId" to pluginId, "jarPath" to jarPath, "jarDeleted" to jarDeleted),
        )
    }
}
