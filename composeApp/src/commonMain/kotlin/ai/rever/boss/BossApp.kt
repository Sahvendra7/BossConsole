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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
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
import ai.rever.boss.components.workspaces.WorkspaceManager
import ai.rever.boss.components.workspaces.LayoutWorkspace
import ai.rever.boss.components.workspaces.applyWorkspace
import ai.rever.boss.components.workspaces.extractCurrentWorkspace
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import ai.rever.boss.components.plugin.panels.left_bottom.TopOfMind.LocalSplitViewState
import ai.rever.boss.components.plugin.panels.left_bottom.TopOfMind.LocalWorkspaceManager
import ai.rever.boss.components.plugin.panels.left_bottom.TopOfMind.TabTreeState
import ai.rever.boss.components.dialogs.TopOfMindDialog
import androidx.compose.runtime.CompositionLocalProvider
import ai.rever.boss.components.plugin.panels.right_top.LLMSettingsManager
import ai.rever.boss.updater.UpdateManager
import ai.rever.boss.updater.UpdateBanner
import androidx.compose.runtime.collectAsState
import kotlin.time.Clock


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
    
    // Workspace manager
    val workspaceManager = remember { WorkspaceManager() }
    val coroutineScope = rememberCoroutineScope()
    
    // Set up workspace deletion callback to cleanup tabs
    LaunchedEffect(workspaceManager, splitViewState) {
        workspaceManager.setOnWorkspaceDeleted { deletedWorkspaceId ->
            // Clean up preserved states for the deleted workspace
            splitViewState.cleanupDeletedWorkspace(deletedWorkspaceId)
        }
    }
    
    // State for showing new tab dialog
    var showNewTabDialog by remember { mutableStateOf(false) }
    var showTopOfMindDialog by remember { mutableStateOf(false) }
    
    // State for save feedback
    var saveMessage by remember { mutableStateOf<String?>(null) }

    DisposableEffect(panelRegistry, tabRegistry) {
        val plugin = DefaultPlugin(panelRegistry, tabRegistry)
        draggablePanelComponent.update()

        onDispose {
            // Save current workspace as "Last Session" when app closes
            coroutineScope.launch {
                val currentLayout = extractCurrentWorkspace(splitViewState)
                val lastSessionConfig = currentLayout.copy(
                    id = "last-session",
                    name = "Last Session",
                    description = "Automatically saved session"
                )
                workspaceManager.updateCurrentWorkspace(lastSessionConfig)
                workspaceManager.saveCurrentWorkspace("Last Session")
            }

            // Cleanup plugin coroutines
            plugin.dispose()

            // Cleanup update manager
            UpdateManager.instance.cleanup()
        }
    }
    
    // Load LLM settings on startup
    LaunchedEffect(Unit) {
        try {
            LLMSettingsManager.loadSettings()
        } catch (e: Exception) {
            // Ignore errors during settings load to prevent app crash
            println("Warning: Failed to load LLM settings: ${e.message}")
        }
    }
    
    // Initialize update manager and start periodic checks
    LaunchedEffect(Unit) {
        try {
            UpdateManager.instance.startPeriodicChecks()
            // Check for updates on startup if enough time has passed
            if (UpdateManager.instance.shouldCheckForUpdates()) {
                UpdateManager.instance.checkForUpdates()
            }
        } catch (e: Exception) {
            println("Warning: Failed to initialize update manager: ${e.message}")
        }
    }
    
    // Load last used workspace on startup
    LaunchedEffect(workspaceManager, splitViewState) {
        // Wait for workspaces to be loaded
        workspaceManager.workspaces
            .onEach { configs ->
                // Clean up orphaned workspace states
                val existingWorkspaceIds = configs.map { it.id }.toSet()
                splitViewState.cleanupDeletedWorkspaces(existingWorkspaceIds)
                
                // Only load on first emission when configs are available
                if (configs.isNotEmpty() && workspaceManager.currentWorkspace.value == null) {
                    // Check if there's a saved "last-session" workspace
                    val lastSessionConfig = configs.find { it.name == "Last Session" }
                    
                    if (lastSessionConfig != null) {
                        // Ensure it has the correct ID
                        val configWithId = if (lastSessionConfig.id != "last-session") {
                            lastSessionConfig.copy(id = "last-session")
                        } else {
                            lastSessionConfig
                        }
                        // Apply the last session workspace
                        workspaceManager.loadWorkspace(configWithId)
                        applyWorkspace(configWithId, splitViewState)
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
    
    // Monitor for layout changes to mark workspace as dirty and auto-save
    LaunchedEffect(splitViewState, workspaceManager) {
        var lastWorkspaceSnapshot: LayoutWorkspace? = null
        var saveJob: Job? = null
        
        // Monitor the entire layout structure for changes
        snapshotFlow { 
            // Extract current layout workspace
            extractCurrentWorkspace(splitViewState)
        }
        .onEach { currentLayout ->
            // Check if we have a loaded workspace
            val loadedConfig = workspaceManager.currentWorkspace.value
            
            if (loadedConfig != null) {
                // Compare with the last known workspace state
                if (lastWorkspaceSnapshot == null) {
                    // First snapshot after loading
                    lastWorkspaceSnapshot = currentLayout
                } else if (currentLayout != lastWorkspaceSnapshot) {
                    // Layout has changed (splits, tabs added/removed, etc.)
                    lastWorkspaceSnapshot = currentLayout
                    
                    // Mark the current workspace as modified (if it's not "Last Session")
                    if (loadedConfig.name != "Last Session") {
                        TabTreeState.markWorkspaceAsModified(loadedConfig.id)
                    }
                    
                    // Cancel previous save job if any
                    saveJob?.cancel()
                    
                    // Auto-save to current workspace or "Last Session" after a short delay
                    saveJob = launch {
                        delay(2000) // Wait 2 seconds before saving
                        
                        if (loadedConfig.name == "Last Session") {
                            // If we're already in "Last Session", update it
                            val lastSessionConfig = currentLayout.copy(
                                id = "last-session",
                                name = "Last Session",
                                description = "Automatically saved session"
                            )
                            workspaceManager.updateCurrentWorkspace(lastSessionConfig)
                            workspaceManager.saveCurrentWorkspace("Last Session")
                        } else {
                            // Update the current loaded workspace with changes
                            val updatedConfig = loadedConfig.copy(
                                layout = currentLayout.layout,
                                timestamp = Clock.System.now().toEpochMilliseconds()
                            )
                            workspaceManager.updateCurrentWorkspace(updatedConfig)
                            workspaceManager.saveCurrentWorkspace()
                            
                            // Clear the modified state since we just auto-saved
                            TabTreeState.markWorkspaceAsSaved(loadedConfig.id)
                        }
                    }
                }
            } else {
                // No workspace loaded, but still save as "Last Session"
                if (currentLayout != lastWorkspaceSnapshot) {
                    lastWorkspaceSnapshot = currentLayout
                    
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
                        workspaceManager.updateCurrentWorkspace(lastSessionConfig)
                        workspaceManager.saveCurrentWorkspace("Last Session")
                    }
                }
            }
        }
        .launchIn(this)
        
        // Reset snapshot when workspace changes
        workspaceManager.currentWorkspace
            .onEach { config ->
                if (config != null && config.name != "Last Session") {
                    // Workspace loaded (but not Last Session), reset tracking
                    lastWorkspaceSnapshot = null
                    // Clear modified status when loading a workspace
                    TabTreeState.markWorkspaceAsSaved(config.id)
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
                LocalWorkspaceManager provides workspaceManager
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
                                // Open terminal tab in the active panel
                                val activeTabsComponent = splitViewState.getPanelTabsComponent(splitViewState.activePanelId)
                                if (activeTabsComponent != null) {
                                    val terminalTab = TerminalTabInfo(
                                        id = "terminal-${Random.nextLong()}",
                                        typeId = TerminalTab.typeId,
                                        title = "Terminal"
                                    )
                                    activeTabsComponent.addTab(terminalTab)
                                } else {
                                    // Fallback to main tabs component
                                    val terminalTab = TerminalTabInfo(
                                        id = "terminal-${Random.nextLong()}",
                                        typeId = TerminalTab.typeId,
                                        title = "Terminal"
                                    )
                                    tabsComponent.addTab(terminalTab)
                                }
                                true
                            }
                            event.isMetaPressed && event.key == Key.W -> {
                                // Close current tab in the active panel
                                val activeTabsComponent = splitViewState.getPanelTabsComponent(splitViewState.activePanelId)
                                if (activeTabsComponent != null) {
                                    val tabs = activeTabsComponent.tabsState.value.tabs
                                    val activeIndex = activeTabsComponent.tabsState.value.activeIndex
                                    if (activeIndex >= 0 && activeIndex < tabs.size) {
                                        activeTabsComponent.removeTab(activeIndex)
                                    }
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
                                // Reload current browser tab if it's a Fluck tab in the active panel
                                val activeTabsComponent = splitViewState.getPanelTabsComponent(splitViewState.activePanelId)
                                if (activeTabsComponent != null) {
                                    val activeTab = activeTabsComponent.tabsState.value.activeTab
                                    if (activeTab is FluckTabInfo) {
                                        // Trigger reload event for the browser
                                        val activeTabComponent = activeTabsComponent.getActiveComponent()
                                        if (activeTabComponent is ai.rever.boss.components.plugin.tab_types.fluck.FluckTabComponent) {
                                            activeTabComponent.reload()
                                        }
                                    }
                                }
                                true
                            }
                            event.isCtrlPressed && event.key == Key.Spacebar -> {
                                // Open Boss Active Tabs quick switcher
                                showTopOfMindDialog = true
                                true
                            }
                            event.isMetaPressed && event.isShiftPressed && event.key == Key.S -> {
                                // Save current workspace (Cmd+Shift+S)
                                coroutineScope.launch {
                                    val currentConfig = workspaceManager.currentWorkspace.value
                                    if (currentConfig != null) {
                                        // Extract current layout state
                                        val currentLayout = extractCurrentWorkspace(splitViewState)
                                        
                                        // Update the workspace with current layout
                                        val updatedConfig = currentConfig.copy(
                                            layout = currentLayout.layout,
                                            timestamp = kotlin.time.Clock.System.now().toEpochMilliseconds()
                                        )
                                        
                                        // Save the updated workspace
                                        workspaceManager.updateCurrentWorkspace(updatedConfig)
                                        workspaceManager.saveCurrentWorkspace()
                                        
                                        // Mark as saved (remove from modified list)
                                        TabTreeState.markWorkspaceAsSaved(currentConfig.id)
                                        
                                        // Show feedback
                                        saveMessage = "Workspace '${currentConfig.name}' saved successfully"
                                        delay(3000)
                                        saveMessage = null
                                    } else {
                                        // No workspace loaded, create new one
                                        val currentLayout = extractCurrentWorkspace(splitViewState)
                                        val newConfig = currentLayout.copy(
                                            name = "Workspace ${kotlin.time.Clock.System.now().toEpochMilliseconds() / 1000}",
                                            description = "Saved workspace"
                                        )
                                        workspaceManager.updateCurrentWorkspace(newConfig)
                                        workspaceManager.saveCurrentWorkspace()
                                        
                                        // Show feedback
                                        saveMessage = "New workspace '${newConfig.name}' saved successfully"
                                        delay(3000)
                                        saveMessage = null
                                    }
                                }
                                true
                            }
                            // Navigate between panels with Cmd+Arrow keys
                            event.isMetaPressed && event.key == Key.DirectionLeft -> {
                                // Switch to left/previous panel
                                val panels = splitViewState.getAllPanels()
                                val currentIndex = panels.indexOfFirst { it.id == splitViewState.activePanelId }
                                if (currentIndex > 0) {
                                    splitViewState.setActivePanel(panels[currentIndex - 1].id)
                                }
                                true
                            }
                            event.isMetaPressed && event.key == Key.DirectionRight -> {
                                // Switch to right/next panel
                                val panels = splitViewState.getAllPanels()
                                val currentIndex = panels.indexOfFirst { it.id == splitViewState.activePanelId }
                                if (currentIndex >= 0 && currentIndex < panels.size - 1) {
                                    splitViewState.setActivePanel(panels[currentIndex + 1].id)
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
                    
                    // Update banner
                    val updateState by UpdateManager.instance.updateState.collectAsState()
                    UpdateBanner(
                        updateState = updateState,
                        onCheckForUpdates = {
                            coroutineScope.launch {
                                UpdateManager.instance.checkForUpdates()
                            }
                        },
                        onDownloadUpdate = { updateInfo ->
                            coroutineScope.launch {
                                UpdateManager.instance.downloadUpdate(updateInfo)
                            }
                        },
                        onInstallUpdate = { downloadPath ->
                            coroutineScope.launch {
                                val success = UpdateManager.instance.installUpdate(downloadPath)
                                if (success) {
                                    // Optionally restart the application here
                                    // ApplicationRestarter.restart()
                                }
                            }
                        },
                        onDismiss = {
                            UpdateManager.instance.resetState()
                        }
                    )
                    
                    BossTopBar(
                        workspaceManager = workspaceManager,
                        onApplyWorkspace = { workspace ->
                            coroutineScope.launch {
                                // Preserve current state before switching
                                val currentWorkspace = workspaceManager.currentWorkspace.value
                                if (currentWorkspace != null && currentWorkspace.id.isNotEmpty()) {
                                    splitViewState.preserveCurrentState(currentWorkspace.id, currentWorkspace.name)
                                }
                                
                                // First load the workspace to reset dirty state
                                workspaceManager.loadWorkspace(workspace)
                                // Then apply it to the UI (which will try to restore preserved state)
                                applyWorkspace(workspace, splitViewState)
                            }
                        },
                        getCurrentWorkspace = {
                            extractCurrentWorkspace(splitViewState)
                        },
                        onShowTopOfMind = {
                            showTopOfMindDialog = true
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
                        // Get the active panel component first, fallback to last interacted, then original
                        val targetComponent = splitViewState.getActiveTabsComponent() 
                            ?: splitViewState.getLastInteractedTabComponent() 
                            ?: tabsComponent
                        
                        when (type) {
                            TabType.URL -> {
                                val tab = FluckTabInfo(
                                    id = "browser-${Random.nextLong()}",
                                    typeId = TabTypeId("fluck"),
                                    _title = "Loading...",
                                    url = path
                                )
                                targetComponent.addTab(tab)
                            }
                            TabType.FILE -> {
                                val fileName = path.substringAfterLast('/')
                                val tab = EditorTabInfo(
                                    id = "editor-${Random.nextLong()}",
                                    typeId = TabTypeId("editor"),
                                    title = fileName,
                                    filePath = path
                                )
                                targetComponent.addTab(tab)
                            }
                            TabType.TERMINAL -> {
                                val tab = TerminalTabInfo(
                                    id = "terminal-${Random.nextLong()}",
                                    typeId = TerminalTab.typeId,
                                    title = "Terminal"
                                )
                                targetComponent.addTab(tab)
                            }
                        }
                    }
                )
            }
            
            // Top of mind quick switcher dialog
            if (showTopOfMindDialog) {
                TopOfMindDialog(
                    splitViewState = splitViewState,
                    workspaceManager = workspaceManager,
                    onDismiss = { showTopOfMindDialog = false },
                    onTabSelect = { activeTab ->
                        showTopOfMindDialog = false
                        coroutineScope.launch {
                            // Preserve current state before switching
                            val currentWorkspace = workspaceManager.currentWorkspace.value
                            if (currentWorkspace != null && currentWorkspace.id.isNotEmpty()) {
                                splitViewState.preserveCurrentState(currentWorkspace.id, currentWorkspace.name)
                            }
                            
                            // Find the workspace containing this tab
                            val targetWorkspace = workspaceManager.workspaces.value.find { 
                                it.id == activeTab.workspaceId 
                            }
                            
                            if (targetWorkspace != null) {
                                // Load and apply the target workspace
                                workspaceManager.loadWorkspace(targetWorkspace)
                                applyWorkspace(targetWorkspace, splitViewState)
                                
                                // Focus the specific tab after a short delay to ensure workspace is applied
                                delay(100)
                                splitViewState.selectTabInPanel(activeTab.tabInfo.id, activeTab.panelId)
                            }
                        }
                    }
                )
            }
            
            // Save feedback snackbar
            saveMessage?.let { message ->
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Surface(
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colors.primary,
                        shape = RoundedCornerShape(8.dp),
                        elevation = 8.dp
                    ) {
                        Text(
                            text = message,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            color = MaterialTheme.colors.onPrimary,
                            style = MaterialTheme.typography.body2
                        )
                    }
                }
            }
            }
        }
    }
}







