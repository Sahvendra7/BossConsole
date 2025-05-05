package ai.rever.boss.v4.components.window_panel

import BossDarkBackground
import BossDarkBorder
import ai.rever.boss.v4.components.model.BossWindowPanelModel
import ai.rever.boss.v4.components.model.Panel
import ai.rever.boss.v4.components.model.Panel.Companion.bottom
import ai.rever.boss.v4.components.model.Panel.Companion.left
import ai.rever.boss.v4.components.model.Panel.Companion.right
import ai.rever.boss.v4.components.model.Panel.Companion.top
import ai.rever.boss.v4.components.window_panel.components.BossPanelTopBar
import ai.rever.boss.v4.components.window_panel.components.BossResizablePanel
import ai.rever.boss.v4.components.window_panel.components.main_window_panels.BossMainPanel
import ai.rever.boss.v4.components.window_panel.components.main_window_panels.BossTabsComponent
import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Divider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

@Composable
fun BossWindow(
    modifier: Modifier = Modifier,
    tabsComponent: BossTabsComponent,
    windowPanelModel: BossWindowPanelModel) {

    @Composable
    fun Panel(panel: Panel) {
        val interactionSource = remember { MutableInteractionSource() }
        val isHovered by interactionSource.collectIsHoveredAsState()
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(BossDarkBackground)
                .hoverable(interactionSource)
        ) {
            BossPanelTopBar(
                title = windowPanelModel.getPanelTitle(panel),
                isHovered = isHovered,
                onMinimize = {
                    windowPanelModel.setPanelVisible(panel, false)
                }
            )
            Divider(color = BossDarkBorder)
        }
    }

    @Composable
    fun WithPanel(panel: Panel,
                  isPanelVisible: Boolean = windowPanelModel.isVisible(panel),
                  isMainVisible: Boolean = true,
                  isRelative: Boolean = false,
                  panelContent: @Composable BoxScope.() -> Unit = { Panel(panel) },
                  mainContent: (@Composable BoxScope.() -> Unit)? = null) {
        BossResizablePanel(
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
                    bossTabsComponent = tabsComponent
                )
            }
        }
    }
}

