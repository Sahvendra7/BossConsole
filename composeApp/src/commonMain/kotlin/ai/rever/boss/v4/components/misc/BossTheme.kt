import androidx.compose.material.MaterialTheme
import androidx.compose.material.darkColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// JetBrains Dark Theme Colors
val BossDarkBackground = Color(0xFF2B2B2B) // JetBrains dark background
val BossDarkSurface = Color(0xFF3C3F41)    // JetBrains secondary background
val BossDarkBorder = Color(0xFF4D4D4D)     // JetBrains border color
val BossDarkTextPrimary = Color(0xFFF2F2F2) // JetBrains text color - brightened
val BossDarkTextSecondary = Color(0xFFAAAAAA) // JetBrains secondary text - brightened
val BossDarkAccent = Color(0xFF3592C4)      // JetBrains blue accent
val BossDarkSecondary = Color(0xFF43A047)   // Secondary color - green
val BossDarkError = Color(0xFFE53935)       // Error color - red

/**
 * BOSS application theme
 * 
 * This theme only supports dark mode and ignores system theme settings.
 * The dark theme is mandatory for all screens and components in the app.
 * 
 * @param content The content to be styled with this theme
 */
@Composable
fun BossTheme(content: @Composable () -> Unit) {
    // Always use dark colors, regardless of system settings
    val darkColorPalette = darkColors(
        primary = BossDarkAccent,
        primaryVariant = BossDarkAccent.copy(alpha = 0.8f),
        secondary = BossDarkSecondary,
        secondaryVariant = BossDarkSecondary.copy(alpha = 0.8f),
        background = BossDarkBackground,
        surface = BossDarkSurface,
        error = BossDarkError,
        onPrimary = BossDarkTextPrimary,
        onSecondary = BossDarkTextPrimary,
        onBackground = BossDarkTextPrimary,
        onSurface = BossDarkTextPrimary,
        onError = BossDarkTextPrimary
    )
    
    MaterialTheme(
        colors = darkColorPalette
    ) {
        content()
    }
}