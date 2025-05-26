package ai.rever.boss.components.plugin.tab_types.fluck

import androidx.compose.runtime.Composable

@Composable
expect fun FluckView(fileId: String, content: String = "", onContentChange: (String) -> Unit = {})

