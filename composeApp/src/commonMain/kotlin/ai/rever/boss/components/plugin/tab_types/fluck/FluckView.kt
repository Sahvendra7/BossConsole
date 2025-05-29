package ai.rever.boss.components.plugin.tab_types.fluck

import androidx.compose.runtime.Composable

// Platform-specific implementation
@Composable
expect fun FluckView(
    fileId: String,
    content: String,
    browser: Any? = null, // Browser instance (platform-specific type)
    browserViewState: Any? = null, // Browser view state (platform-specific type)
    onContentChange: (String) -> Unit,
    onTitleChange: (String) -> Unit,
    onOpenInNewTab: (String) -> Unit = {}
)

