package ai.rever.boss.plugin.pathutils

import java.io.File

/**
 * Single source of truth for the BOSS data directory.
 *
 * Normal mode  → ~/.boss
 * Dev mode     → ~/.boss_debug  (set boss.dev.mode=true or BOSS_DEV_MODE=true)
 *
 * This prevents debug runs from clobbering production data and vice versa.
 */
object BossDirectories {
    val isDevMode: Boolean =
        System.getProperty("boss.dev.mode")?.toBoolean() == true ||
        System.getenv("BOSS_DEV_MODE")?.toBoolean() == true

    private val rootDirName: String = if (isDevMode) ".boss_debug" else ".boss"

    val rootDir: File = File(System.getProperty("user.home"), rootDirName)

    fun resolve(relativePath: String): File = File(rootDir, relativePath)
}
