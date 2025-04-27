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
import ai.rever.boss.v4.components.window_panel.components.main_window_panel.BossMainPanel
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier

@Composable
fun BossWindow(
    modifier: Modifier = Modifier,
    bossConsoleComponent: BossConsoleComponent,
    windowPanelModel: BossWindowPanelModel) {

    @Composable
    fun Panel(panel: Panel) {
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
    fun WithPanel(panel: Panel,
                  isPanelVisible: Boolean = windowPanelModel.isVisible(panel),
                  isMainVisible: Boolean = true,
                  isRelative: Boolean = false,
                  panelContent: @Composable BoxScope.() -> Unit = { Panel(panel) },
                  mainContent: (@Composable BoxScope.() -> Unit)? = null) {
        BossPanel(
            modifier = modifier,
            panel = panel,
            isPanelVisible = isPanelVisible,
            isMainVisible = isMainVisible,
            isRelative = isRelative,
            panelContent = panelContent,
            mainContent = mainContent
        )
    }

    @Composable
    fun WithNestedPanel(panel: Panel,
                        secondaryPanel: Panel = bottom,
                        isFirstPanelVisible: Boolean = windowPanelModel.isVisible(panel.bottom),
                        isLastPanelVisible: Boolean = windowPanelModel.isVisible(panel.top),
                        isNestedRelative: Boolean = true,
                        firstPanel: @Composable BoxScope.() -> Unit = { Panel(panel.bottom) },
                        lastPanel: @Composable BoxScope.() -> Unit = { Panel(panel.top) },
                        mainContent: @Composable BoxScope.() -> Unit) {
        WithPanel(panel,
            panelContent = {
                WithPanel(secondaryPanel,
                    isPanelVisible = isFirstPanelVisible,
                    isMainVisible = isLastPanelVisible,
                    isRelative = isNestedRelative,
                    panelContent = firstPanel,
                    mainContent = lastPanel
                )},
            mainContent = mainContent)
    }

    WithPanel(bottom) {
        WithNestedPanel(left) {
            WithNestedPanel(right) {
                BossMainPanel(
                    modifier = Modifier.fillMaxSize(),
                    bossConsoleComponent = bossConsoleComponent
                )
            }
        }
    }
}

