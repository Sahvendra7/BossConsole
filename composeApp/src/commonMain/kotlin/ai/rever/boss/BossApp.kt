package ai.rever.boss

import BossTheme
import ai.rever.boss.components.bars.horizontal.BossBottomBar
import ai.rever.boss.components.bars.horizontal.BossTitleBar
import ai.rever.boss.components.bars.horizontal.BossTopBar
import ai.rever.boss.components.bars.vertical.BossLeftSideBar
import ai.rever.boss.components.bars.vertical.BossRightSideBar
import ai.rever.boss.components.model.BossDraggableComponent
import ai.rever.boss.components.overlays.DraggingItemOverlay
import ai.rever.boss.components.plugin.DefaultPlugin
import ai.rever.boss.components.registery.*
import ai.rever.boss.components.window_panel.BossWindow
import ai.rever.boss.components.window_panel.components.main_window_panels.BossTabsComponent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Language
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.arkivanov.decompose.ComponentContext


@Composable
fun ComponentContext.BossApp() {

    val panelRegistry = remember { PanelRegistry() }
    val tabRegistry = remember { TabRegistry() }

    val panelComponentStore = remember { PanelComponentStore(this, panelRegistry) }

    val draggablePanelComponent = remember { BossDraggableComponent(panelRegistry) }
    val tabsComponent = remember { BossTabsComponent(this, tabRegistry) }

    DisposableEffect(panelRegistry, tabRegistry) {
        DefaultPlugin(panelRegistry, tabRegistry)
        draggablePanelComponent.update()

        onDispose {  }
    }

    // Create example tab (could be triggered by user action)
    DisposableEffect(tabsComponent) {
        // Open an example editor tab
        val file1 = object : TabInfo {
            override val id = "file1"
            override val typeId = TabTypeId("editor")
            override val title = "Main.kt"
            override val icon = Icons.Outlined.Code
        }


        val file2 = object : TabInfo {
            override val id = "file2"
            override val typeId = TabTypeId("editor")
            override val title = "SomeFile.kt"
            override val icon = Icons.Outlined.Code
        }
        
        // Add a Fluck browser tab
        val fluckTab = object : TabInfo {
            override val id = "browser1"
            override val typeId = TabTypeId("fluck")
            override val title = "Web Browser"
            override val icon = Icons.Outlined.Language
        }

        tabsComponent.addTab(file1)
        tabsComponent.addTab(file2)
        tabsComponent.addTab(fluckTab)

        onDispose { /* cleanup */ }
    }

    with(draggablePanelComponent) {
        BossTheme {
            Box(modifier = Modifier.fillMaxSize()) { // Use Box to allow overlaying the drag ghost
                Column(modifier = Modifier.fillMaxSize()) {
                    BossTitleBar()
                    BossTopBar()
                    Row(modifier = Modifier.weight(1f)) {
                        // Pass the shared model down to both sidebars
                        BossLeftSideBar()
                        BossWindow(
                            modifier = Modifier.weight(1f),
                            tabsComponent = tabsComponent,
                            panelComponentStore = panelComponentStore
                        )
                        BossRightSideBar()
                    }
                    BossBottomBar()
                }
                // Draw the dragging item overlay (ghost) if an item is being dragged
                DraggingItemOverlay()
            }
        }
    }
}







