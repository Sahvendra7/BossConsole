package ai.rever.boss.v4.components.model

import ai.rever.boss.v4.components.window_panel.components.Panel
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp

@Stable
class WindowPanelModel {
    var leftPanelWidth by mutableStateOf(250.dp)
    var rightPanelWidth by mutableStateOf(250.dp)
    var bottomPanelHeight by mutableStateOf(200.dp)

    var isLeftPanelVisible by mutableStateOf(true)
    var isRightPanelVisible by mutableStateOf(true)
    var isBottomPanelVisible by mutableStateOf(true)

    val title = mapOf(
        Panel.LEFT to "Project",
        Panel.RIGHT to "Structure",
        Panel.BOTTOM to "Terminal"
    )
}

@Composable
fun rememberWindowPanelModel(): WindowPanelModel {
    return remember { WindowPanelModel() }
}