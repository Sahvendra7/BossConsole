package ai.rever.boss.v4.components.window_panel.components.side_window_panel

import BossDarkBackground
import BossDarkBorder
import ai.rever.boss.v4.components.model.BossWindowPanelModel
import ai.rever.boss.v4.components.model.Panel
import ai.rever.boss.v4.components.window_panel.components.BossPanelTopBar
import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Divider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

@Composable
fun BossWindowPanelModel.SideWindowPanel(panel: Panel) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BossDarkBackground)
            .hoverable(interactionSource)
    ) {
        BossPanelTopBar(
            title = getPanelTitle(panel),
            isHovered = isHovered,
            onMinimize = {
                setPanelVisible(panel, false)
            }
        )
        Divider(color = BossDarkBorder)
    }
}