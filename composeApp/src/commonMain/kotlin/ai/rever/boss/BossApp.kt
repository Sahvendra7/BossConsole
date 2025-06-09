package ai.rever.boss

import BossTheme
import ai.rever.boss.components.bars.horizontal.BossBottomBar
import ai.rever.boss.components.bars.horizontal.BossTitleBar
import ai.rever.boss.components.bars.horizontal.BossTopBar
import ai.rever.boss.components.bars.vertical.BossLeftSideBar
import ai.rever.boss.components.bars.vertical.BossRightSideBar
import ai.rever.boss.components.model.BossDraggableComponent
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import ai.rever.boss.components.plugin.panels.left_top.BossActiveTabs.LocalSplitViewState
import ai.rever.boss.components.plugin.panels.left_top.BossActiveTabs.LocalConfigurationManager
import ai.rever.boss.components.dialogs.BossActiveTabsDialog
import androidx.compose.runtime.CompositionLocalProvider


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
    var showBossActiveTabsDialog by remember { mutableStateOf(false) }

    DisposableEffect(panelRegistry, tabRegistry) {
        DefaultPlugin(panelRegistry, tabRegistry)
        draggablePanelComponent.update()

        onDispose { 
            // Save current configuration as "Last Session" when app closes
            coroutineScope.launch {
                val currentLayout = extractCurrentConfiguration(splitViewState)
                val lastSessionConfig = currentLayout.copy(
                    id = "last-session",
                    name = "Last Session",
                    description = "Automatically saved session"
                )
                configurationManager.updateCurrentConfiguration(lastSessionConfig)
                configurationManager.saveCurrentConfiguration("Last Session")
            }
        }
    }
    
    // Load last used configuration on startup
    LaunchedEffect(configurationManager, splitViewState) {
        // Wait for configurations to be loaded
        configurationManager.configurations
            .onEach { configs ->
                // Only load on first emission when configs are available
                if (configs.isNotEmpty() && configurationManager.currentConfiguration.value == null) {
                    // Check if there's a saved "last-session" configuration
                    val lastSessionConfig = configs.find { it.name == "Last Session" }
                    
                    if (lastSessionConfig != null) {
                        // Ensure it has the correct ID
                        val configWithId = if (lastSessionConfig.id != "last-session") {
                            lastSessionConfig.copy(id = "last-session")
                        } else {
                            lastSessionConfig
                        }
                        // Apply the last session configuration
                        configurationManager.loadConfiguration(configWithId)
                        applyConfiguration(configWithId, splitViewState)
                    }
                }
            }
            .launchIn(this)
    }
    
    // Listen for file open events - now handled by split state
    LaunchedEffect(splitViewState) {
        FileEventBus.fileOpenEvents
            .onEach { event ->
                splitViewState.openFileInActivePanel(event.filePath, event.fileName)
            }
            .launchIn(this)
    }
    
    // Monitor for layout changes to mark configuration as dirty and auto-save
    LaunchedEffect(splitViewState, configurationManager) {
        var lastConfigurationSnapshot: LayoutConfiguration? = null
        var saveJob: Job? = null
        
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
                    lastConfigurationSnapshot = currentLayout
                    
                    // Cancel previous save job if any
                    saveJob?.cancel()
                    
                    // Auto-save as "Last Session" after a short delay
                    saveJob = launch {
                        delay(2000) // Wait 2 seconds before saving
                        val lastSessionConfig = currentLayout.copy(
                            id = "last-session",
                            name = "Last Session",
                            description = "Automatically saved session"
                        )
                        configurationManager.updateCurrentConfiguration(lastSessionConfig)
                        configurationManager.saveCurrentConfiguration("Last Session")
                    }
                }
            } else {
                // No configuration loaded, but still save as "Last Session"
                if (currentLayout != lastConfigurationSnapshot) {
                    lastConfigurationSnapshot = currentLayout
                    
                    // Cancel previous save job if any
                    saveJob?.cancel()
                    
                    // Auto-save as "Last Session" after a short delay
                    saveJob = launch {
                        delay(2000) // Wait 2 seconds before saving
                        val lastSessionConfig = currentLayout.copy(
                            id = "last-session",
                            name = "Last Session",
                            description = "Automatically saved session"
                        )
                        configurationManager.updateCurrentConfiguration(lastSessionConfig)
                        configurationManager.saveCurrentConfiguration("Last Session")
                    }
                }
            }
        }
        .launchIn(this)
        
        // Reset snapshot when configuration changes
        configurationManager.currentConfiguration
            .onEach { config ->
                if (config != null && config.name != "Last Session") {
                    // Configuration loaded (but not Last Session), reset tracking
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

    with(draggablePanelComponent) {
        BossTheme {
            CompositionLocalProvider(
                LocalSplitViewState provides splitViewState,
                LocalConfigurationManager provides configurationManager
            ) {
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
                            event.isCtrlPressed && event.key == Key.Spacebar -> {
                                // Open Boss Active Tabs quick switcher
                                showBossActiveTabsDialog = true
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
                                // Preserve current state before switching
                                val currentConfig = configurationManager.currentConfiguration.value
                                if (currentConfig != null && currentConfig.id.isNotEmpty()) {
                                    splitViewState.preserveCurrentState(currentConfig.id, currentConfig.name)
                                }
                                
                                // First load the configuration to reset dirty state
                                configurationManager.loadConfiguration(config)
                                // Then apply it to the UI (which will try to restore preserved state)
                                applyConfiguration(config, splitViewState)
                            }
                        },
                        getCurrentConfiguration = {
                            extractCurrentConfiguration(splitViewState)
                        },
                        onShowBossActiveTabs = {
                            showBossActiveTabsDialog = true
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
            
            // Boss Active Tabs quick switcher dialog
            if (showBossActiveTabsDialog) {
                BossActiveTabsDialog(
                    splitViewState = splitViewState,
                    configurationManager = configurationManager,
                    onDismiss = { showBossActiveTabsDialog = false },
                    onTabSelect = { activeTab ->
                        showBossActiveTabsDialog = false
                        coroutineScope.launch {
                            // Preserve current state before switching
                            val currentConfig = configurationManager.currentConfiguration.value
                            if (currentConfig != null && currentConfig.id.isNotEmpty()) {
                                splitViewState.preserveCurrentState(currentConfig.id, currentConfig.name)
                            }
                            
                            // Find the configuration containing this tab
                            val targetConfig = configurationManager.configurations.value.find { 
                                it.id == activeTab.configurationId 
                            }
                            
                            if (targetConfig != null) {
                                // Load and apply the target configuration
                                configurationManager.loadConfiguration(targetConfig)
                                applyConfiguration(targetConfig, splitViewState)
                                
                                // Focus the specific tab after a short delay to ensure configuration is applied
                                delay(100)
                                splitViewState.selectTabInPanel(activeTab.tabInfo.id, activeTab.panelId)
                            }
                        }
                    }
                )
            }
            }
        }
    }
}







