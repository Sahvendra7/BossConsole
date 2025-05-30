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
import ai.rever.boss.components.plugin.tab_types.fluck.FluckTabInfo
import ai.rever.boss.components.plugin.tab_types.EditorTabInfo
import ai.rever.boss.components.registery.*
import ai.rever.boss.components.dialogs.NewTabDialog
import ai.rever.boss.components.dialogs.TabType
import ai.rever.boss.components.window_panel.BossWindow
import ai.rever.boss.components.window_panel.components.main_window_panels.BossTabsComponent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import ai.rever.boss.components.events.FileEventBus
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import com.arkivanov.decompose.ComponentContext


@Composable
fun ComponentContext.BossApp() {

    val panelRegistry = remember { PanelRegistry() }
    val tabRegistry = remember { TabRegistry() }

    val panelComponentStore = remember { PanelComponentStore(this, panelRegistry) }

    val draggablePanelComponent = remember { BossDraggableComponent(panelRegistry) }
    val tabsComponent = remember { BossTabsComponent(this, tabRegistry) }
    
    // State for showing new tab dialog
    var showNewTabDialog by remember { mutableStateOf(false) }

    DisposableEffect(panelRegistry, tabRegistry) {
        DefaultPlugin(panelRegistry, tabRegistry)
        draggablePanelComponent.update()

        onDispose {  }
    }
    
    // Listen for file open events
    LaunchedEffect(tabsComponent) {
        FileEventBus.fileOpenEvents
            .onEach { event ->
                // Check if file is already open
                val existingTab = tabsComponent.tabsState.value.tabs.find { tab ->
                    tab is EditorTabInfo && tab.filePath == event.filePath
                }
                
                if (existingTab == null) {
                    // Create new editor tab
                    val editorTab = EditorTabInfo(
                        id = "editor-${kotlin.random.Random.nextLong()}",
                        typeId = TabTypeId("editor"),
                        title = event.fileName,
                        icon = Icons.Outlined.Code,
                        filePath = event.filePath
                    )
                    val index = tabsComponent.addTab(editorTab)
                    if (index >= 0) {
                        tabsComponent.selectTab(index)
                    }
                } else {
                    // Select existing tab
                    val index = tabsComponent.tabsState.value.tabs.indexOf(existingTab)
                    if (index >= 0) {
                        tabsComponent.selectTab(index)
                    }
                }
            }
            .launchIn(this)
    }

    // Create example tab (could be triggered by user action)
    DisposableEffect(tabsComponent) {
        // Open BossApp.kt by default
        val bossAppTab = EditorTabInfo(
            id = "bossapp",
            typeId = TabTypeId("editor"),
            title = "BossApp.kt",
            icon = Icons.Outlined.Code,
            filePath = "/Users/kshivang/Development/BOSS-Kotlin/composeApp/src/commonMain/kotlin/ai/rever/boss/BossApp.kt"
        )
        
        // Add a Fluck browser tab with dynamic title support
        val fluckTab = FluckTabInfo(
            id = "browser1",
            typeId = TabTypeId("fluck"),
            _title = "New Tab",
            url = "https://www.risalabs.ai"
        )

        tabsComponent.addTab(bossAppTab)
        tabsComponent.addTab(fluckTab)

        onDispose { /* cleanup */ }
    }

    with(draggablePanelComponent) {
        BossTheme {
            Box(modifier = Modifier
                .fillMaxSize()
                .onKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown) {
                        when {
                            event.isMetaPressed && event.key == Key.N -> {
                                showNewTabDialog = true
                                true
                            }
                            event.isMetaPressed && event.key == Key.W -> {
                                // Close current tab
                                val tabs = tabsComponent.tabsState.value.tabs
                                val activeIndex = tabsComponent.tabsState.value.activeIndex
                                if (activeIndex >= 0 && activeIndex < tabs.size) {
                                    tabsComponent.removeTab(activeIndex)
                                }
                                true
                            }
                            else -> false
                        }
                    } else {
                        false
                    }
                }
            ) { // Use Box to allow overlaying the drag ghost
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
            
            // Show new tab dialog
            if (showNewTabDialog) {
                NewTabDialog(
                    onDismiss = { showNewTabDialog = false },
                    onCreateTab = { type, path ->
                        when (type) {
                            TabType.URL -> {
                                val tab = FluckTabInfo(
                                    id = "browser-${kotlin.random.Random.nextLong()}",
                                    typeId = TabTypeId("fluck"),
                                    _title = "Loading...",
                                    url = path
                                )
                                tabsComponent.addTab(tab)
                            }
                            TabType.FILE -> {
                                val fileName = path.substringAfterLast('/')
                                val tab = EditorTabInfo(
                                    id = "editor-${kotlin.random.Random.nextLong()}",
                                    typeId = TabTypeId("editor"),
                                    title = fileName,
                                    filePath = path
                                )
                                tabsComponent.addTab(tab)
                            }
                        }
                    }
                )
            }
        }
    }
}







