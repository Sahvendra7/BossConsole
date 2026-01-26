package ai.rever.boss.plugin.ui

import androidx.compose.material.MaterialTheme
import androidx.compose.material.darkColors
import androidx.compose.runtime.Composable

/**
 * BOSS application theme.
 *
 * This theme only supports dark mode and ignores system theme settings.
 * The dark theme is mandatory for all screens and components in the app.
 *
 * @param content The content to be styled with this theme
 */
@Composable
fun BossTheme(content: @Composable () -> Unit) {
    val darkColorPalette = darkColors(
        primary = BossColors.darkAccent,
        primaryVariant = BossColors.darkAccent.copy(alpha = 0.8f),
        secondary = BossColors.darkSecondary,
        secondaryVariant = BossColors.darkSecondary.copy(alpha = 0.8f),
        background = BossColors.darkBackground,
        surface = BossColors.darkSurface,
        error = BossColors.darkError,
        onPrimary = BossColors.darkTextPrimary,
        onSecondary = BossColors.darkTextPrimary,
        onBackground = BossColors.darkTextPrimary,
        onSurface = BossColors.darkTextPrimary,
        onError = BossColors.darkTextPrimary
    )

    MaterialTheme(
        colors = darkColorPalette
    ) {
        content()
    }
}
