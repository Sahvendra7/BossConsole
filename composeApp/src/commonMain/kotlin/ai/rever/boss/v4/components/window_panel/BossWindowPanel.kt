package ai.rever.boss.v4.components.window_panel

import ai.rever.boss.v4.components.dividers.VDivider
import ai.rever.boss.v4.components.model.BossWindowPanelModel
import ai.rever.boss.v4.components.model.Panel
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
fun BossWindowPanel(
    modifier: Modifier = Modifier,
    bossConsoleComponent: BossConsoleComponent,
    windowPanelModel: BossWindowPanelModel) {

    Box(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top area with left, center, and right panels
            Row(modifier = Modifier.weight(1f)) {
                // Left panel

                BossSideWindowPanel(windowPanelModel, Panel.LEFT) {}

                BossMainWindowPanel(
                    modifier = Modifier
                        .weight(1f),
                    bossConsoleComponent = bossConsoleComponent
                )

                // Right panel
                BossSideWindowPanel(windowPanelModel, Panel.RIGHT) {}
            }

            // Bottom panel
            BossSideWindowPanel(windowPanelModel, Panel.BOTTOM) {}
        }

        ResizeOverlay(windowPanelModel)
    }

    VDivider()
}

