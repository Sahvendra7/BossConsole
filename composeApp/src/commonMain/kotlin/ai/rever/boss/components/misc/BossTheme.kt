@file:Suppress("UNUSED")

/**
 * Re-exports from plugin-ui-core module for backward compatibility.
 * New code should import directly from ai.rever.boss.plugin.ui
 */

import ai.rever.boss.plugin.ui.BossTheme as PluginBossTheme
import ai.rever.boss.plugin.ui.bossTypography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import org.jetbrains.compose.resources.Font
import boss_kotlin.composeapp.generated.resources.Res
import boss_kotlin.composeapp.generated.resources.meslolgs_nf_regular
import boss_kotlin.composeapp.generated.resources.meslolgs_nf_bold
import boss_kotlin.composeapp.generated.resources.meslolgs_nf_italic
import boss_kotlin.composeapp.generated.resources.meslolgs_nf_bold_italic

/**
 * BOSS theme for the host app.
 *
 * Builds the design system's mono brand voice from the bundled MesloLGS Nerd
 * Font and injects it into [PluginBossTheme], so `BossTheme.type.*` (and any
 * component that reads it) renders in the real face instead of the generic
 * platform monospace fallback. All host theme roots route through here.
 */
@Composable
fun BossTheme(content: @Composable () -> Unit) {
    val mono = FontFamily(
        Font(Res.font.meslolgs_nf_regular, FontWeight.Normal, FontStyle.Normal),
        Font(Res.font.meslolgs_nf_bold, FontWeight.Bold, FontStyle.Normal),
        Font(Res.font.meslolgs_nf_italic, FontWeight.Normal, FontStyle.Italic),
        Font(Res.font.meslolgs_nf_bold_italic, FontWeight.Bold, FontStyle.Italic),
    )
    PluginBossTheme(
        typography = bossTypography(mono = mono),
        content = content,
    )
}

// Re-export color values for backward compatibility.
// Imported as top-level so existing code using "import BossDarkAccent" still works.
// Getters (not stored vals) so they stay reactive to the active theme.
val BossDarkBackground get() = ai.rever.boss.plugin.ui.BossDarkBackground
val BossDarkSurface get() = ai.rever.boss.plugin.ui.BossDarkSurface
val BossDarkContentBackground get() = ai.rever.boss.plugin.ui.BossDarkContentBackground
val BossDarkBorder get() = ai.rever.boss.plugin.ui.BossDarkBorder
val BossDarkTextPrimary get() = ai.rever.boss.plugin.ui.BossDarkTextPrimary
val BossDarkTextSecondary get() = ai.rever.boss.plugin.ui.BossDarkTextSecondary
val BossDarkTextMuted get() = ai.rever.boss.plugin.ui.BossDarkTextMuted
val BossDarkAccent get() = ai.rever.boss.plugin.ui.BossDarkAccent
val BossDarkSecondary get() = ai.rever.boss.plugin.ui.BossDarkSecondary
val BossDarkError get() = ai.rever.boss.plugin.ui.BossDarkError
val BossDarkSuccess get() = ai.rever.boss.plugin.ui.BossDarkSuccess
val BossDarkWarning get() = ai.rever.boss.plugin.ui.BossDarkWarning
val ContextMenuBackground get() = ai.rever.boss.plugin.ui.ContextMenuBackground
val ContextMenuBorder get() = ai.rever.boss.plugin.ui.ContextMenuBorder
val ContextMenuHover get() = ai.rever.boss.plugin.ui.ContextMenuHover
