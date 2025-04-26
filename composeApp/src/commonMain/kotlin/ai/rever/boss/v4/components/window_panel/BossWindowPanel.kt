package ai.rever.boss.v4.components.window_panel

import BossDarkBackground
import ai.rever.boss.v4.components.model.BossWindowPanelModel
import ai.rever.boss.v4.components.model.Panel
import ai.rever.boss.v4.components.model.Panel.Companion.bottom
import ai.rever.boss.v4.components.model.Panel.Companion.left
import ai.rever.boss.v4.components.model.Panel.Companion.right
import ai.rever.boss.v4.components.window_panel.components.*
import ai.rever.boss.v4.components.window_panel.components.main_window_panel.BossConsoleComponent
import ai.rever.boss.v4.components.window_panel.components.main_window_panel.BossMainWindowPanel
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier

@Composable
fun BossWindowPanel(
    modifier: Modifier = Modifier,
    bossConsoleComponent: BossConsoleComponent,
    windowPanelModel: BossWindowPanelModel) {


    @Composable
    fun BossSidePan(panel: Panel, content: (@Composable BoxScope.() -> Unit)? = null) {
        BossWinPanel(
            modifier = modifier,
            panel = panel,
            isVisible = windowPanelModel.isVisible(panel),
            panelContent = {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(BossDarkBackground)
                ) {
                    BossSideWindowPanelTopBar(
                        title = windowPanelModel.getPanelTitle(panel),
                        onMinimize = {
                            windowPanelModel.setPanelVisible(panel, false)
                        }
                    )
                }
            },
            content = content
        )
    }

    BossSidePan(bottom) {
        BossSidePan(left) {
            BossSidePan(right) {
                BossMainWindowPanel(
                    modifier = Modifier.fillMaxSize(),
                    bossConsoleComponent = bossConsoleComponent
                )
            }
        }
    }


//    BoxWithConstraints(modifier = modifier) {
//        val windowHeight = maxHeight
//        val windowWidth = maxWidth
//
//        Column(modifier = Modifier.fillMaxSize()) {
//            // Top area with left, center, and right panels
//            Row(modifier = Modifier.weight(1f)) {
//                // Left panel
//                BossSideWindowPanel(windowPanelModel, left) {}
//
//                BossMainWindowPanel(
//                    modifier = Modifier
//                        .weight(1f),
//                    bossConsoleComponent = bossConsoleComponent
//                )
//
//                // Right panel
//                BossSideWindowPanel(windowPanelModel, right) {}
//            }
//
//            // Bottom panel
//            BossSideWindowPanel(windowPanelModel, bottom) {}
//        }
//
//        ResizeOverlay(windowPanelModel, windowHeight, windowWidth)
//    }
//
//    VDivider()
}

