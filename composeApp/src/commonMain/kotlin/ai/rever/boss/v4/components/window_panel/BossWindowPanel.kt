package ai.rever.boss.v4.components.window_panel

import ai.rever.boss.v4.components.model.rememberResizeBossPanelModel
import ai.rever.boss.v4.components.overlays.ResizeOverlay
import ai.rever.boss.v4.components.window_panel.components.*
import ai.rever.boss.v4.components.window_panel.components.main_window_panel.BossConsoleComponent
import ai.rever.boss.v4.components.window_panel.components.main_window_panel.BossMainWindowPanel
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

@Composable
fun BossWindowPanel(modifier: Modifier = Modifier, bossConsoleComponent: BossConsoleComponent) {
    // State to control panel visibility
    val isLeftPanelVisible by remember { mutableStateOf(true) }
    val isRightPanelVisible by remember { mutableStateOf(true) }
    val isBottomPanelVisible by remember { mutableStateOf(true) }

    val resizeBossPanelModel = rememberResizeBossPanelModel()

    Box(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top area with left, center, and right panels
            Row(modifier = Modifier.weight(1f)) {
                // Left panel
                BossLeftWindowPanel(isBottomPanelVisible, resizeBossPanelModel.leftPanelWidth)

                BossMainWindowPanel(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize(),
                    bossConsoleComponent = bossConsoleComponent
                )

                // Right panel
                BossRightWindowPanel(isRightPanelVisible, resizeBossPanelModel.rightPanelWidth)
            }

            // Bottom panel
            BossBottomWindowPanel(isBottomPanelVisible, resizeBossPanelModel.bottomPanelHeight)
        }

        ResizeOverlay(isLeftPanelVisible, isRightPanelVisible, isBottomPanelVisible, resizeBossPanelModel)
    }
}

