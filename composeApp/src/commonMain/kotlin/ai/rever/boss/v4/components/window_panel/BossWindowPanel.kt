package ai.rever.boss.v4.components.window_panel

import ai.rever.boss.v4.components.dividers.VDivider
import ai.rever.boss.v4.components.model.rememberResizeBossPanelModel
import ai.rever.boss.v4.components.overlays.ResizeOverlay
import ai.rever.boss.v4.components.window_panel.components.*
import ai.rever.boss.v4.components.window_panel.components.main_window_panel.BossConsoleComponent
import ai.rever.boss.v4.components.window_panel.components.main_window_panel.BossMainWindowPanel
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier

@Composable
fun BossWindowPanel(modifier: Modifier = Modifier, bossConsoleComponent: BossConsoleComponent) {
    // State to control panel visibility
    val resizeBossPanelModel = rememberResizeBossPanelModel()

    val leftPanelWidth by derivedStateOf { resizeBossPanelModel.leftPanelWidth }
    val rightPanelWidth by derivedStateOf { resizeBossPanelModel.rightPanelWidth }
    val bottomPanelHeight by derivedStateOf { resizeBossPanelModel.bottomPanelHeight }
    val isLeftPanelVisible by derivedStateOf { resizeBossPanelModel.isLeftPanelVisible }
    val isRightPanelVisible by derivedStateOf { resizeBossPanelModel.isRightPanelVisible }
    val isBottomPanelVisible by derivedStateOf { resizeBossPanelModel.isBottomPanelVisible }

    Box(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top area with left, center, and right panels
            Row(modifier = Modifier.weight(1f)) {
                // Left panel
                BossLeftWindowPanel(isLeftPanelVisible, leftPanelWidth)

                BossMainWindowPanel(
                    modifier = Modifier
                        .weight(1f),
                    bossConsoleComponent = bossConsoleComponent
                )

                // Right panel
                BossRightWindowPanel(isRightPanelVisible, rightPanelWidth)
            }

            // Bottom panel
            BossBottomWindowPanel(isBottomPanelVisible, bottomPanelHeight)
        }

        ResizeOverlay(resizeBossPanelModel)
    }

    VDivider()
}

