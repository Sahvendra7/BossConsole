package ai.rever.boss.components.settings.shared

import BossDarkAccent
import BossDarkBackground
import BossDarkBorder
import BossDarkContentBackground
import BossDarkTextMuted
import BossDarkTextPrimary
import BossDarkTextSecondary
import androidx.compose.ui.graphics.Color

/**
 * Shared UI constants for settings panel - aligned with BossTerm's SettingsTheme.
 */
object SettingsTheme {
    val SurfaceColor: Color = BossDarkBackground      // Sidebar background (#2B2B2B)
    val BackgroundColor: Color = BossDarkContentBackground // Content area background (#1E1E1E)
    val AccentColor: Color = BossDarkAccent           // Selection/highlight color
    val BorderColor: Color = BossDarkBorder           // Border/divider color (#4D4D4D)
    val TextPrimary: Color = BossDarkTextPrimary      // Primary text color
    val TextSecondary: Color = BossDarkTextSecondary  // Secondary text color
    val TextMuted: Color = BossDarkTextMuted          // Muted text color (#707070)
}
