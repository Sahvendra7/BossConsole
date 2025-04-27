package ai.rever.boss.v4.components.window_panel

import BossDarkBackground
import ai.rever.boss.v4.components.model.BossWindowPanelModel
import ai.rever.boss.v4.components.model.Panel
import ai.rever.boss.v4.components.model.Panel.Companion.bottom
import ai.rever.boss.v4.components.model.Panel.Companion.left
import ai.rever.boss.v4.components.model.Panel.Companion.right
import ai.rever.boss.v4.components.model.Panel.Companion.top
import ai.rever.boss.v4.components.window_panel.components.*
import ai.rever.boss.v4.components.window_panel.components.main_window_panel.BossConsoleComponent
import ai.rever.boss.v4.components.window_panel.components.main_window_panel.BossMainWindowPanel
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier

@Composable
fun BossWindowPanel(
    modifier: Modifier = Modifier,
    bossConsoleComponent: BossConsoleComponent,
    windowPanelModel: BossWindowPanelModel) {

    @Composable
    fun SidePanel(panel: Panel) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(BossDarkBackground)
        ) {
            BossPanelTopBar(
                title = windowPanelModel.getPanelTitle(panel),
                onMinimize = {
                    windowPanelModel.setPanelVisible(panel, false)
                }
            )
        }
    }

    @Composable
    fun WithSidePanel(panel: Panel,
                      isPanelVisible: Boolean = windowPanelModel.isVisible(panel),
                      isMainVisible: Boolean = true,
                      panelContent: @Composable BoxScope.() -> Unit = { SidePanel(panel) },
                      content: (@Composable BoxScope.() -> Unit)? = null) {
        BossWinPanel(
            modifier = modifier,
            panel = panel,
            isPanelVisible = isPanelVisible,
            isMainVisible = isMainVisible,
            panelContent = panelContent,
            content = content
        )
    }

    @Composable
    fun WithHorizontalPanel(panel: Panel,
                            content: @Composable BoxScope.() -> Unit) {
        WithSidePanel(panel, panelContent = {
            WithSidePanel(bottom,
                isPanelVisible = windowPanelModel.isVisible(panel.bottom),
                isMainVisible = windowPanelModel.isVisible(panel.top),
                panelContent = { SidePanel(panel.bottom) }) {
                SidePanel(panel.top)
            }
        }, content = content)
    }

    WithSidePanel(bottom) {
        WithHorizontalPanel(left) {
            WithHorizontalPanel(right) {
                BossMainWindowPanel(
                    modifier = Modifier.fillMaxSize(),
                    bossConsoleComponent = bossConsoleComponent
                )
            }
        }
    }
}

