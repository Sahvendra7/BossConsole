package ai.rever.boss.v4.components.window_panel

import ai.rever.boss.v4.components.model.BossWindowPanelModel
import ai.rever.boss.v4.components.model.Panel
import ai.rever.boss.v4.components.model.Panel.Companion.bottom
import ai.rever.boss.v4.components.model.Panel.Companion.left
import ai.rever.boss.v4.components.model.Panel.Companion.right
import ai.rever.boss.v4.components.model.Panel.Companion.top
import ai.rever.boss.v4.components.window_panel.components.BossResizablePanel
import ai.rever.boss.v4.components.window_panel.components.main_window_panels.BossMainPanel
import ai.rever.boss.v4.components.window_panel.components.main_window_panels.BossTabsComponent
import ai.rever.boss.v4.components.window_panel.components.side_window_panel.SidePanel
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun BossWindowPanelModel.BossWindow(
    modifier: Modifier = Modifier,
    tabsComponent: BossTabsComponent) {

    @Composable
    fun WithPanel(panel: Panel,
                  isPanelVisible: Boolean = isVisible(panel),
                  isMainVisible: Boolean = true,
                  isRelative: Boolean = false,
                  panelContent: @Composable BoxScope.() -> Unit = { SidePanel(panel) },
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
                        isFirstPanelVisible: Boolean = isVisible(panel.bottom),
                        isLastPanelVisible: Boolean = isVisible(panel.top),
                        isNestedRelative: Boolean = true,
                        firstPanel: @Composable BoxScope.() -> Unit = { SidePanel(panel.bottom) },
                        lastPanel: @Composable BoxScope.() -> Unit = { SidePanel(panel.top) },
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
                with (tabsComponent) {
                    BossMainPanel()
                }
            }
        }
    }
}

