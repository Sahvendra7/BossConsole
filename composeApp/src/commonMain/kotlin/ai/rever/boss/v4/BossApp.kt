package ai.rever.boss.v4

import BossTheme
import ai.rever.boss.v4.components.bars.horizontal.BossBottomBar
import ai.rever.boss.v4.components.bars.horizontal.BossTitleBar
import ai.rever.boss.v4.components.bars.horizontal.BossTopBar
import ai.rever.boss.v4.components.bars.vertical.BossLeftSideBar
import ai.rever.boss.v4.components.bars.vertical.BossRightSideBar
import ai.rever.boss.v4.components.model.rememberBossWindowPanelModel
import ai.rever.boss.v4.components.overlays.DraggingItemOverlay
import ai.rever.boss.v4.components.window_panel.BossWindowPanel
import ai.rever.boss.v4.components.window_panel.components.main_window_panel.BossConsoleComponent
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier

@Composable
fun BossApp(bossConsoleComponent: BossConsoleComponent) {

    // Create and remember the model here to share state across sidebars
    val bossWindowPanelModel = rememberBossWindowPanelModel()

    BossTheme {
        Box(modifier = Modifier.fillMaxSize()) { // Use Box to allow overlaying the drag ghost
            Column(modifier = Modifier.fillMaxSize()) {
                BossTitleBar()
                BossTopBar()
                Row(modifier = Modifier.weight(1f)) {
                    // Pass the shared model down to both sidebars
                    BossLeftSideBar(bossWindowPanelModel)
                    BossWindowPanel(
                        modifier = Modifier.weight(1f),
                        bossConsoleComponent = bossConsoleComponent,
                        windowPanelModel = bossWindowPanelModel)
                    BossRightSideBar(bossWindowPanelModel)
                }
                BossBottomBar()
            }
            // Draw the dragging item overlay (ghost) if an item is being dragged
            DraggingItemOverlay(bossWindowPanelModel)
        }
    }
}







