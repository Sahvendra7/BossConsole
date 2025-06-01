package ai.rever.boss

import BossTheme
import ai.rever.boss.components.bars.horizontal.BossBottomBar
import ai.rever.boss.components.bars.horizontal.BossTitleBar
import ai.rever.boss.components.bars.horizontal.BossTopBar
import ai.rever.boss.components.bars.vertical.BossLeftSideBar
import ai.rever.boss.components.bars.vertical.BossRightSideBar
import ai.rever.boss.components.model.BossDraggableComponent
import ai.rever.boss.components.model.Panel
import ai.rever.boss.components.model.Panel.Companion.bottom
import ai.rever.boss.components.model.Panel.Companion.left
import ai.rever.boss.components.model.Panel.Companion.right
import ai.rever.boss.components.model.Panel.Companion.top
import ai.rever.boss.components.overlays.DraggingItemOverlay
import ai.rever.boss.components.plugin.DefaultPlugin
import ai.rever.boss.components.plugin.tab_types.fluck.FluckTabInfo
import ai.rever.boss.components.plugin.tab_types.EditorTabInfo
import ai.rever.boss.components.registery.*
import ai.rever.boss.components.dialogs.NewTabDialog
import ai.rever.boss.components.dialogs.TabType
import ai.rever.boss.components.window_panel.BossWindow
import ai.rever.boss.components.window_panel.components.main_window_panels.BossTabsComponent
import ai.rever.boss.components.window_panel.rememberSplitViewState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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
import ai.rever.boss.components.events.PanelEventBus
import ai.rever.boss.components.plugin.tab_types.TerminalTab
import ai.rever.boss.components.plugin.tab_types.TerminalTabInfo
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import com.arkivanov.decompose.ComponentContext
import kotlin.random.Random
import ai.rever.boss.components.plugin.panels.left_top.CodeBaseInfo


@Composable
fun ComponentContext.BossApp() {

    val panelRegistry = remember { PanelRegistry() }
    val tabRegistry = remember { TabRegistry() }

    val panelComponentStore = remember { PanelComponentStore(this, panelRegistry) }

    val draggablePanelComponent = remember { BossDraggableComponent(panelRegistry) }
    val tabsComponent = remember { BossTabsComponent(this, tabRegistry) }
    
    // Create split view state that manages all tab panels
    val splitViewState = rememberSplitViewState(
        tabRegistry = tabRegistry,
        initialTabsComponent = tabsComponent
    )
    
    // State for showing new tab dialog
    var showNewTabDialog by remember { mutableStateOf(false) }

    DisposableEffect(panelRegistry, tabRegistry) {
        DefaultPlugin(panelRegistry, tabRegistry)
        draggablePanelComponent.update()

        onDispose {  }
    }
    
    // Listen for file open events - now handled by split state
    LaunchedEffect(splitViewState) {
        FileEventBus.fileOpenEvents
            .onEach { event ->
                splitViewState.openFileInActivePanel(event.filePath, event.fileName)
            }
            .launchIn(this)
    }
    
    // Listen for panel close events
    LaunchedEffect(draggablePanelComponent) {
        PanelEventBus.panelCloseEvents
            .onEach { event ->
                // Find which panel contains this component
                val panels = listOf(
                    bottom,
                    left.top,
                    left.bottom,
                    right.top,
                    right.bottom
                )
                
                for (panel in panels) {
                    val panelContentId = draggablePanelComponent.getPanelContentId(panel)
                    if (panelContentId == event.panelId) {
                        draggablePanelComponent.setPanelVisible(panel, false)
                        // Remove the component from store to ensure fresh instance next time
                        panelComponentStore.removeComponent(event.panelId)
                        break
                    }
                }
            }
            .launchIn(this)
    }

    // Add default tabs
    DisposableEffect(tabsComponent) {
        // Create default web tabs
        val defaultUrls = listOf(
            "https://www.risalabs.ai",
            "https://secure15.oncoemr.com/",
            "https://pa-dashboard-dev.web.app/"
        )
        
        defaultUrls.forEach { url ->
            val tab = FluckTabInfo(
                id = "browser-${Random.nextLong()}",
                typeId = TabTypeId("fluck"),
                _title = "Loading...",
                url = url
            )
            tabsComponent.addTab(tab)
        }
        
        // Select the first tab
        if (tabsComponent.tabsState.value.tabs.isNotEmpty()) {
            tabsComponent.selectTab(0)
        }
        
        onDispose { /* cleanup */ }
    }

    with(draggablePanelComponent) {
        BossTheme {
            Box(modifier = Modifier
                .fillMaxSize()
                .onPreviewKeyEvent { event ->
                    // Use onPreviewKeyEvent to catch events before they reach children
                    if (event.type == KeyEventType.KeyDown) {
                        when {
                            event.isMetaPressed && event.key == Key.N -> {
                                showNewTabDialog = true
                                true
                            }
                            event.isMetaPressed && event.key == Key.T -> {
                                // Open terminal tab
                                val terminalTab = TerminalTabInfo(
                                    id = "terminal-${Random.nextLong()}",
                                    typeId = TerminalTab.typeId,
                                    title = "Terminal"
                                )
                                tabsComponent.addTab(terminalTab)
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
                            event.isMetaPressed && event.key == Key.O -> {
                                // Open CodeBase panel (left.top)
                                // Find the CodeBase item in the sidebar
                                val codebaseItems = getItemsForSlot(left.top)
                                val codebaseItem = codebaseItems.firstOrNull { 
                                    it.pluginContentId == CodeBaseInfo.id 
                                }
                                if (codebaseItem != null) {
                                    // Toggle the panel visibility
                                    val currentVisibility = isVisible(left.top)
                                    setPanelVisible(left.top, !currentVisibility)
                                    // If showing the panel, ensure CodeBase is selected
                                    if (!currentVisibility) {
                                        getPanelContentId(left.top)?.let { currentId ->
                                            if (currentId != CodeBaseInfo.id) {
                                                // Switch to CodeBase
                                                codebaseItem.onClick?.invoke()
                                            }
                                        }
                                    }
                                }
                                true
                            }
                            event.isMetaPressed && event.key == Key.R -> {
                                // Reload current browser tab if it's a Fluck tab
                                val activeComponent = splitViewState.getActiveTabsComponent()
                                val activeTab = activeComponent?.tabsState?.value?.activeTab
                                if (activeTab is ai.rever.boss.components.plugin.tab_types.fluck.FluckTabInfo) {
                                    // Trigger reload event for the browser
                                    val activeTabComponent = activeComponent.getActiveComponent()
                                    if (activeTabComponent is ai.rever.boss.components.plugin.tab_types.fluck.FluckTabComponent) {
                                        activeTabComponent.reload()
                                    }
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
                            panelComponentStore = panelComponentStore,
                            splitViewState = splitViewState
                        )
                        BossRightSideBar()
                    }
                    BossBottomBar(tabsComponent)
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
                                    id = "browser-${Random.nextLong()}",
                                    typeId = TabTypeId("fluck"),
                                    _title = "Loading...",
                                    url = path
                                )
                                tabsComponent.addTab(tab)
                            }
                            TabType.FILE -> {
                                val fileName = path.substringAfterLast('/')
                                val tab = EditorTabInfo(
                                    id = "editor-${Random.nextLong()}",
                                    typeId = TabTypeId("editor"),
                                    title = fileName,
                                    filePath = path
                                )
                                tabsComponent.addTab(tab)
                            }
                            TabType.TERMINAL -> {
                                val tab = TerminalTabInfo(
                                    id = "terminal-${Random.nextLong()}",
                                    typeId = TerminalTab.typeId,
                                    title = "Terminal"
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







