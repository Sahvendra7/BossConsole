package ai.rever.boss.components.plugin.tab_types.fluck

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
actual fun FluckView(fileId: String, content: String, onContentChange: (String) -> Unit) {
    JxBrowserCompose(
        modifier = Modifier,
        initialUrl = if (content.isNotBlank()) content else "https://www.google.com"
    )
}