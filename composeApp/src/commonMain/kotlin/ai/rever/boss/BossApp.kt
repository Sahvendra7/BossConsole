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
import ai.rever.boss.components.configuration.ConfigurationManager
import ai.rever.boss.components.configuration.LayoutConfiguration
import ai.rever.boss.components.configuration.applyConfiguration
import ai.rever.boss.components.configuration.extractCurrentConfiguration
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow


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
    
    // Configuration manager
    val configurationManager = remember { ConfigurationManager() }
    val coroutineScope = rememberCoroutineScope()
    
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
    
    // Monitor for layout changes to mark configuration as dirty
    LaunchedEffect(splitViewState, configurationManager) {
        var lastConfigurationSnapshot: LayoutConfiguration? = null
        
        // Monitor the entire layout structure for changes
        snapshotFlow { 
            // Extract current layout configuration
            extractCurrentConfiguration(splitViewState)
        }
        .onEach { currentLayout ->
            // Check if we have a loaded configuration
            val loadedConfig = configurationManager.currentConfiguration.value
            
            if (loadedConfig != null) {
                // Compare with the last known configuration state
                if (lastConfigurationSnapshot == null) {
                    // First snapshot after loading
                    lastConfigurationSnapshot = currentLayout
                } else if (currentLayout != lastConfigurationSnapshot) {
                    // Layout has changed (splits, tabs added/removed, etc.)
                    // Configuration is automatically saved when needed
                    lastConfigurationSnapshot = currentLayout
                }
            } else {
                // No configuration loaded, reset snapshot
                lastConfigurationSnapshot = null
            }
        }
        .launchIn(this)
        
        // Reset snapshot when configuration changes
        configurationManager.currentConfiguration
            .onEach { config ->
                if (config != null) {
                    // Configuration loaded, reset tracking
                    lastConfigurationSnapshot = null
                }
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

    // Add default tabs and create split layout
    DisposableEffect(splitViewState, tabsComponent) {
        // Create default tabs
        val risaTab = FluckTabInfo(
            id = "browser-${Random.nextLong()}",
            typeId = TabTypeId("fluck"),
            _title = "RISA Labs",
            url = "https://www.risalabs.ai"
        )
        val oncoTab = FluckTabInfo(
            id = "browser-${Random.nextLong()}",
            typeId = TabTypeId("fluck"),
            _title = "OncoEMR",
            url = "https://secure15.oncoemr.com/"
        )
        val dashboardTab = FluckTabInfo(
            id = "browser-${Random.nextLong()}",
            typeId = TabTypeId("fluck"),
            _title = "PA Dashboard",
            url = "https://pa-dashboard-dev.web.app/"
        )
        val terminalTab = TerminalTabInfo(
            id = "terminal-${Random.nextLong()}",
            typeId = TerminalTab.typeId,
            title = "Terminal"
        )
        
        // Add first tab to main panel (which is the initial tabsComponent)
        tabsComponent.addTab(risaTab)
        tabsComponent.selectTab(0)
        
        // Create vertical split (left/right) with OncoEMR
        val rightPanelId = splitViewState.splitPanel(
            panelId = "main",
            orientation = ai.rever.boss.components.window_panel.SplitOrientation.VERTICAL,
            tabToMove = oncoTab
        )
        
        // Split the left panel horizontally (top/bottom) with PA Dashboard
        splitViewState.splitPanel(
            panelId = "main",
            orientation = ai.rever.boss.components.window_panel.SplitOrientation.HORIZONTAL,
            tabToMove = dashboardTab
        )
        
        // Split the right panel horizontally (top/bottom) with Terminal
        splitViewState.splitPanel(
            panelId = rightPanelId,
            orientation = ai.rever.boss.components.window_panel.SplitOrientation.HORIZONTAL,
            tabToMove = terminalTab
        )
        
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
                                // Open CodeBase panel (left.top.top)
                                // Find the CodeBase item in the sidebar
                                val codebaseItems = getItemsForSlot(left.top.top)
                                val codebaseItem = codebaseItems.firstOrNull { 
                                    it.pluginContentId == CodeBaseInfo.id 
                                }
                                if (codebaseItem != null) {
                                    // Make left.top visible first
                                    if (!isVisible(left.top)) {
                                        setPanelVisible(left.top, true)
                                    }
                                    // Then invoke the onClick to select CodeBase
                                    codebaseItem.onClick?.invoke()
                                }
                                true
                            }
                            event.isMetaPressed && event.key == Key.R -> {
                                // Reload current browser tab if it's a Fluck tab
                                val activeComponent = splitViewState.getActiveTabsComponent()
                                val activeTab = activeComponent?.tabsState?.value?.activeTab
                                if (activeTab is FluckTabInfo) {
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
                    BossTopBar(
                        configurationManager = configurationManager,
                        onApplyConfiguration = { config ->
                            coroutineScope.launch {
                                // First load the configuration to reset dirty state
                                configurationManager.loadConfiguration(config)
                                // Then apply it to the UI
                                applyConfiguration(config, splitViewState)
                            }
                        },
                        getCurrentConfiguration = {
                            extractCurrentConfiguration(splitViewState)
                        }
                    )
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







