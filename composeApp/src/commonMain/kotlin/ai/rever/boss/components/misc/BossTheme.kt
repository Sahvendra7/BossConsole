@file:Suppress("UNUSED")

/**
 * Re-exports from plugin-ui-core module for backward compatibility.
 * New code should import directly from ai.rever.boss.plugin.ui
 */

// Re-export BossTheme composable
import ai.rever.boss.plugin.ui.BossTheme as PluginBossTheme
import androidx.compose.runtime.Composable

@Composable
fun BossTheme(content: @Composable () -> Unit) = PluginBossTheme(content)

// Re-export color values for backward compatibility
// These are imported as top-level so existing code using "import BossDarkAccent" still works
val BossDarkBackground = ai.rever.boss.plugin.ui.BossDarkBackground
val BossDarkSurface = ai.rever.boss.plugin.ui.BossDarkSurface
val BossDarkContentBackground = ai.rever.boss.plugin.ui.BossDarkContentBackground
val BossDarkBorder = ai.rever.boss.plugin.ui.BossDarkBorder
val BossDarkTextPrimary = ai.rever.boss.plugin.ui.BossDarkTextPrimary
val BossDarkTextSecondary = ai.rever.boss.plugin.ui.BossDarkTextSecondary
val BossDarkTextMuted = ai.rever.boss.plugin.ui.BossDarkTextMuted
val BossDarkAccent = ai.rever.boss.plugin.ui.BossDarkAccent
val BossDarkSecondary = ai.rever.boss.plugin.ui.BossDarkSecondary
val BossDarkError = ai.rever.boss.plugin.ui.BossDarkError
val BossDarkSuccess = ai.rever.boss.plugin.ui.BossDarkSuccess
val BossDarkWarning = ai.rever.boss.plugin.ui.BossDarkWarning
val ContextMenuBackground = ai.rever.boss.plugin.ui.ContextMenuBackground
val ContextMenuBorder = ai.rever.boss.plugin.ui.ContextMenuBorder
val ContextMenuHover = ai.rever.boss.plugin.ui.ContextMenuHover
