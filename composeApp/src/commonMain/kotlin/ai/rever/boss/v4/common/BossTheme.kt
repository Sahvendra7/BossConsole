import androidx.compose.material.MaterialTheme
import androidx.compose.material.darkColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// GitHub Dark Mode Theme Colors
val GitHubDarkBackground = Color(0xFF0D1117)
val GitHubDarkSurface = Color(0xFF161B22)
val GitHubDarkBorder = Color(0xFF3F4448)
val GitHubDarkTextPrimary = Color(0xFFF0F6FC)
val GitHubDarkTextSecondary = Color(0xFF8B949E)
val GitHubDarkAccent = Color(0xFF58A6FF)

@Composable
fun BossTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colors = darkColors(
            primary = GitHubDarkAccent,
            primaryVariant = GitHubDarkAccent,
            background = GitHubDarkBackground,
            surface = GitHubDarkSurface,
            onPrimary = GitHubDarkTextPrimary,
            onSecondary = GitHubDarkTextPrimary,
            onBackground = GitHubDarkTextPrimary,
            onSurface = GitHubDarkTextPrimary,
        )
    ) {
        content()
    }
}