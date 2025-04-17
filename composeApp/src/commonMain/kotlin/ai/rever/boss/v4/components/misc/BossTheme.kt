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

@Composable
fun BossTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colors = darkColors(
            primary = BossDarkAccent,
            primaryVariant = BossDarkAccent,
            background = BossDarkBackground,
            surface = BossDarkSurface,
            onPrimary = BossDarkTextPrimary,
            onSecondary = BossDarkTextPrimary,
            onBackground = BossDarkTextPrimary,
            onSurface = BossDarkTextPrimary,
        )
    ) {
        content()
    }
}