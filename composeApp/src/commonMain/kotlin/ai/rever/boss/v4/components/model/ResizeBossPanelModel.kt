package ai.rever.boss.v4.components.model

import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp

@Stable
class ResizeBossPanelModel {
    var leftPanelWidth by mutableStateOf(250.dp)
    var rightPanelWidth by mutableStateOf(250.dp)
    var bottomPanelHeight by mutableStateOf(200.dp)
}

@Composable
fun rememberResizeBossPanelModel(): ResizeBossPanelModel {
    return remember { ResizeBossPanelModel() }
}