package ai.rever.boss.components.plugin.tab_types.fluck

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.TextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun FluckView(fileId: String, content: String = "", onContentChange: (String) -> Unit = {}) {
    // Your editor implementation goes here
    // This could use a code editor library or custom implementation
    TextField(
        value = content,
        onValueChange = onContentChange,
        modifier = Modifier.fillMaxSize()
    )
}

