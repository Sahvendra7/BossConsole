package ai.rever.boss.terminal.render

import ai.rever.boss.config.ConfigLoader

/**
 * Phase B feature flag for the host-side OOP terminal renderer.
 *
 * Source precedence (delegated to [ConfigLoader.getConfig]):
 *   1. `boss.terminal.oopRenderer` environment variable
 *   2. `boss.terminal.oopRenderer` system property
 *   3. `boss.terminal.oopRenderer` in local.properties
 *   4. Default: false
 *
 * "true", "1", and "on" (case-insensitive) all enable the flag; anything
 * else disables it.
 */
object TerminalRenderFlag {
    private const val KEY = "boss.terminal.oopRenderer"

    fun isOopRendererEnabled(): Boolean {
        val raw = ConfigLoader.getConfig(KEY) ?: return false
        return when (raw.trim().lowercase()) {
            "true", "1", "on", "yes" -> true
            else -> false
        }
    }
}
