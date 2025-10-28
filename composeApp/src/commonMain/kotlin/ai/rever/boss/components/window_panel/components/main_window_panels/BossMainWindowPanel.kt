package ai.rever.boss.components.window_panel.components.main_window_panels

import BossDarkBackground
import BossDarkBorder
import BossDarkTextSecondary
import ai.rever.boss.components.bars.ScrollbarConfig
import ai.rever.boss.components.bars.horizontal.HorizontalBar
import ai.rever.boss.components.bars.horizontal.HorizontalBarRow
import ai.rever.boss.components.bars.horizontalScrollWithScrollbar
import ai.rever.boss.components.buttons.BossTabButton
import ai.rever.boss.components.registery.TabComponentWithUI
import ai.rever.boss.components.registery.TabInfo
import ai.rever.boss.components.registery.TabRegistry
import ai.rever.boss.components.tabs_navigation.TabsNavigation
import ai.rever.boss.components.dialogs.NewTabDialog
import ai.rever.boss.components.dialogs.TabType
import ai.rever.boss.components.overlays.ContextMenuItem
import ai.rever.boss.components.overlays.contextMenu
import ai.rever.boss.components.plugin.tab_types.CodeEditor
import ai.rever.boss.components.plugin.tab_types.fluck.Fluck
import ai.rever.boss.components.window_panel.SplitOrientation
import ai.rever.boss.window.WindowOperations
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.size
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.ViewColumn
import androidx.compose.material.icons.outlined.Splitscreen
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import kotlin.time.Clock

@Composable
fun BossTabsComponent.BossMainTabBar(
    splitViewState: ai.rever.boss.components.window_panel.SplitViewState? = null,
    currentPanelId: String? = null
) {
    val tabsState = tabsState.subscribeAsState()
    var showNewTabDialog by remember { mutableStateOf(false) }
    var selectedTabType by remember { mutableStateOf<TabType?>(null) }

    HorizontalBar(
        height = 42.dp, 
        backgroundColor = BossDarkBackground
    ) {
        HorizontalBarRow {
            BossLeftTabBar {
                tabsState.value.tabs.forEachIndexed { index, config ->
                    val isSelected = index == tabsState.value.activeIndex
                    val totalTabs = tabsState.value.tabs.size
                    
                    BossTabButton(
                        fileName = config.title,
                        icon = config.icon,
                        tabIcon = config.tabIcon,
                        isSelected = isSelected,
                        onClick = { 
                            selectTab(index)
                            // Track this tab interaction for Cmd+R/Cmd+N
                            if (splitViewState != null && currentPanelId != null) {
                                splitViewState.trackTabInteraction(currentPanelId, config.id)
                            }
                        },
                        onClose = { 
                            removeTab(index)
                            // Tab removal is handled, cleanup will happen via LaunchedEffect
                        },
                        contextMenuItems = buildList {
                            // Track interaction when context menu is opened
                            if (splitViewState != null && currentPanelId != null) {
                                // Track this tab interaction when right-clicking
                                splitViewState.trackTabInteraction(currentPanelId, config.id)
                            }
                            
                            // Split operations (if split state is available)
                            if (splitViewState != null && currentPanelId != null) {
                                add(ContextMenuItem("Split Right", Icons.Outlined.ViewColumn) {
                                    splitViewState.splitPanel(
                                        panelId = currentPanelId,
                                        orientation = ai.rever.boss.components.window_panel.SplitOrientation.VERTICAL,
                                        tabToMove = config
                                    )
                                })
                                add(ContextMenuItem("Split Down", Icons.Outlined.Splitscreen) {
                                    splitViewState.splitPanel(
                                        panelId = currentPanelId,
                                        orientation = ai.rever.boss.components.window_panel.SplitOrientation.HORIZONTAL,
                                        tabToMove = config
                                    )
                                })
                                add(ContextMenuItem(isDivider = true))
                            }

                            // Open in New Window (if multi-window is supported)
                            if (ai.rever.boss.window.WindowOperations.isMultiWindowSupported()) {
                                add(ContextMenuItem("Open in New Window", Icons.AutoMirrored.Outlined.OpenInNew) {
                                    ai.rever.boss.window.WindowOperations.openTabInNewWindow(config)
                                    // Remove tab from current window after opening in new window
                                    removeTab(index)
                                })
                                add(ContextMenuItem(isDivider = true))
                            }

                            // Close current tab
                            add(ContextMenuItem("Close Tab", Icons.Outlined.Close) {
                                removeTab(index)
                            })
                            
                            // Close other tabs (only show if there are other tabs)
                            if (totalTabs > 1) {
                                add(ContextMenuItem("Close Other Tabs", Icons.Outlined.Clear) {
                                    closeOtherTabs(index)
                                })
                            }
                            
                            // Close tabs to the right (only show if there are tabs to the right)
                            if (index < totalTabs - 1) {
                                add(ContextMenuItem("Close Tabs to the Right", Icons.Outlined.ChevronRight) {
                                    closeTabsToRight(index)
                                })
                            }
                            
                            // Close tabs to the left (only show if there are tabs to the left)
                            if (index > 0) {
                                add(ContextMenuItem("Close Tabs to the Left", Icons.Outlined.ChevronLeft) {
                                    closeTabsToLeft(index)
                                })
                            }
                        }
                    )
                }
                
                // Plus button for new tab
                Box(
                    modifier = Modifier
                        .height(32.dp)
                        .width(32.dp)
                        .padding(4.dp)
                        .background(
                            color = Color(0xFF3C3F41),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                        )
                        .clickable { 
                            showNewTabDialog = true
                            // Track panel interaction when plus button is clicked
                            if (splitViewState != null && currentPanelId != null) {
                                splitViewState.setActivePanel(currentPanelId)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "New Tab",
                        tint = Color(0xFF999999),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Spacer(
                modifier = Modifier
                    .weight(0.1f)
                    .contextMenu(
                        items = buildList {
                            add(ContextMenuItem("New Tab", Icons.Default.Add) {
                                showNewTabDialog = true
                                // Track panel interaction when context menu is used
                                if (splitViewState != null && currentPanelId != null) {
                                    splitViewState.setActivePanel(currentPanelId)
                                }
                            })
                        }
                    )
            )
        }
    }
    
    // New Tab Dialog
    if (showNewTabDialog) {
        NewTabDialog(
            onDismiss = {
                showNewTabDialog = false
                selectedTabType = null
            },
            initialTabType = selectedTabType,
            onCreateTab = { type, path ->
                when (type) {
                    TabType.URL -> {
                        val timestamp = Clock.System.now().toEpochMilliseconds()
                        val fluckTab = ai.rever.boss.components.plugin.tab_types.fluck.FluckTabInfo(
                            id = "fluck-$timestamp",
                            typeId = Fluck.typeId,
                            _title = "Loading...",
                            url = path
                        )
                        val tabIndex = addTab(fluckTab)
                        if (tabIndex >= 0) {
                            selectTab(tabIndex)
                        }
                    }
                    TabType.FILE -> {
                        val timestamp = Clock.System.now().toEpochMilliseconds()
                        val fileName = path.substringAfterLast('/').ifEmpty { "untitled.txt" }
                        val editorTab = ai.rever.boss.components.plugin.tab_types.EditorTabInfo(
                            id = "editor-$timestamp",
                            title = fileName,
                            typeId = CodeEditor.typeId,
                            icon = CodeEditor.icon,
                            filePath = path
                        )
                        val tabIndex = addTab(editorTab)
                        if (tabIndex >= 0) {
                            selectTab(tabIndex)
                        }
                    }
                    TabType.TERMINAL -> {
                        val timestamp = Clock.System.now().toEpochMilliseconds()
                        val terminalTab = ai.rever.boss.components.plugin.tab_types.TerminalTabInfo(
                            id = "terminal-$timestamp",
                            typeId = ai.rever.boss.components.plugin.tab_types.TerminalTab.typeId,
                            title = "Terminal",
                            icon = ai.rever.boss.components.plugin.tab_types.TerminalTab.icon
                        )
                        val tabIndex = addTab(terminalTab)
                        if (tabIndex >= 0) {
                            selectTab(tabIndex)
                        }
                    }
                }
            }
        )
    }
}

@Composable
fun BossTabsComponent.BossMainPanel(
    modifier: Modifier = Modifier,
    splitViewState: ai.rever.boss.components.window_panel.SplitViewState? = null,
    currentPanelId: String? = null
) {
    val focusRequester = remember { FocusRequester() }
    val isFocused = remember { mutableStateOf(false) }
    
    // Track the active panel state to force recomposition
    val activePanelId by splitViewState?.activePanelIdState ?: remember { mutableStateOf("") }
    val isActivePanel = activePanelId == currentPanelId
    
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .onFocusChanged { focusState ->
                isFocused.value = focusState.isFocused || focusState.hasFocus
                if ((focusState.isFocused || focusState.hasFocus) && currentPanelId != null) {
                    splitViewState?.setActivePanel(currentPanelId)
                }
            }
            .focusable()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                // Set focus when clicked
                focusRequester.requestFocus()
                if (currentPanelId != null) {
                    splitViewState?.setActivePanel(currentPanelId)
                }
            }
            .then(
                if (isActivePanel) {
                    Modifier.border(2.dp, MaterialTheme.colors.primary.copy(alpha = 0.5f))
                } else {
                    Modifier
                }
            )
    ) {
        BossMainTabBar(
            splitViewState = splitViewState,
            currentPanelId = currentPanelId
        )
        Divider(color = BossDarkBorder)
        BossMainPanelContent(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            splitViewState = splitViewState,
            currentPanelId = currentPanelId
        )
    }
}

/**
 * Main UI composable that displays the root component
 */
@Composable
fun BossTabsComponent.BossMainPanelContent(
    modifier: Modifier,
    splitViewState: ai.rever.boss.components.window_panel.SplitViewState? = null,
    currentPanelId: String? = null
) {
    // Subscribe to tab state changes to trigger recomposition
    val tabsState = tabsState.subscribeAsState()

    // State for new tab dialog (needed for EmptyContent callbacks)
    var showNewTabDialog by remember { mutableStateOf(false) }
    var selectedTabType by remember { mutableStateOf<TabType?>(null) }

    Box(
        modifier = modifier
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                // Set panel as active when content area is clicked
                if (currentPanelId != null) {
                    splitViewState?.setActivePanel(currentPanelId)
                    // Also track tab interaction if there's an active tab
                    val activeTab = tabsState.value.activeTab
                    if (activeTab != null) {
                        splitViewState?.trackTabInteraction(currentPanelId, activeTab.id)
                    }
                }
            }
    ) {
        // Force recomposition when tab changes by reading the state
        tabsState.value.activeIndex
        val activeComponent = getActiveComponent()

        activeComponent?.Content() ?: EmptyContent(
            onOpenFile = {
                selectedTabType = TabType.FILE
                showNewTabDialog = true
            },
            onNewTab = {
                selectedTabType = null
                showNewTabDialog = true
            },
            onSplitPanel = if (splitViewState != null && currentPanelId != null && tabsState.value.tabs.isNotEmpty()) {
                {
                    splitViewState.splitPanel(
                        panelId = currentPanelId,
                        orientation = SplitOrientation.HORIZONTAL,
                        tabToMove = null
                    )
                }
            } else null,
            onSwitchPanel = if (splitViewState != null) {
                val allPanels = splitViewState.getAllPanels()
                if (allPanels.size > 1) {
                    {
                        val currentIndex = allPanels.indexOfFirst { it.id == currentPanelId }
                        if (currentIndex >= 0) {
                            val nextIndex = (currentIndex + 1) % allPanels.size
                            splitViewState.setActivePanel(allPanels[nextIndex].id)
                        }
                    }
                } else null
            } else null,
            onNewWindow = {
                WindowOperations.createNewWindow()
            }
        )
    }

    // New Tab Dialog (for EmptyContent interactions)
    if (showNewTabDialog) {
        NewTabDialog(
            onDismiss = {
                showNewTabDialog = false
                selectedTabType = null
            },
            initialTabType = selectedTabType,
            onCreateTab = { type, path ->
                when (type) {
                    TabType.URL -> {
                        val timestamp = Clock.System.now().toEpochMilliseconds()
                        val fluckTab = ai.rever.boss.components.plugin.tab_types.fluck.FluckTabInfo(
                            id = "fluck-$timestamp",
                            typeId = Fluck.typeId,
                            _title = "Loading...",
                            url = path
                        )
                        val tabIndex = addTab(fluckTab)
                        if (tabIndex >= 0) {
                            selectTab(tabIndex)
                        }
                    }
                    TabType.FILE -> {
                        val timestamp = Clock.System.now().toEpochMilliseconds()
                        val fileName = path.substringAfterLast('/').ifEmpty { "untitled.txt" }
                        val editorTab = ai.rever.boss.components.plugin.tab_types.EditorTabInfo(
                            id = "editor-$timestamp",
                            title = fileName,
                            typeId = CodeEditor.typeId,
                            icon = CodeEditor.icon,
                            filePath = path
                        )
                        val tabIndex = addTab(editorTab)
                        if (tabIndex >= 0) {
                            selectTab(tabIndex)
                        }
                    }
                    TabType.TERMINAL -> {
                        val timestamp = Clock.System.now().toEpochMilliseconds()
                        val terminalTab = ai.rever.boss.components.plugin.tab_types.TerminalTabInfo(
                            id = "terminal-$timestamp",
                            typeId = ai.rever.boss.components.plugin.tab_types.TerminalTab.typeId,
                            title = "Terminal",
                            icon = ai.rever.boss.components.plugin.tab_types.TerminalTab.icon
                        )
                        val tabIndex = addTab(terminalTab)
                        if (tabIndex >= 0) {
                            selectTab(tabIndex)
                        }
                    }
                }
            }
        )
    }
}

val createBossAppContext get() = DefaultComponentContext(LifecycleRegistry())

/**
 * Root component for the BOSS app using Decompose for navigation
 */
class BossTabsComponent(
    componentContext: ComponentContext,
    val tabRegistry: TabRegistry
) : ComponentContext by componentContext {

    private val tabComponents = mutableStateMapOf<String, TabComponentWithUI>()
    private val tabsNavigation = TabsNavigation<TabInfo>()

    // Expose tab state for UI
    val tabsState: Value<TabsNavigation.TabsState<TabInfo>> = tabsNavigation.state

    // Add a new tab
    fun addTab(config: TabInfo): Int {
        // Create component for this tab
        val component = tabRegistry.createTabComponent(config, this)
        
        if (component != null) {
            // Store component
            tabComponents[config.id] = component
            
            // Add to navigation
            return tabsNavigation.addTab(config)
        }
        
        return -1 // Failed to create component
    }

    // Remove a tab
    fun removeTab(index: Int) {
        val config = tabsState.value.tabs.getOrNull(index)
        config?.let { 
            // Dispose the component if it has a dispose method
            val component = tabComponents.remove(it.id)
            if (component is ai.rever.boss.components.plugin.tab_types.fluck.FluckTabComponent) {
                component.dispose()
            }
        }
        tabsNavigation.removeTab(index)
    }

    // Select a tab
    fun selectTab(index: Int) {
        tabsNavigation.selectTab(index)
    }
    
    // Update a tab
    fun updateTab(index: Int, config: TabInfo) {
        tabsNavigation.updateTab(index, config)
    }

    // Get active tab component
    fun getActiveComponent(): TabComponentWithUI? {
        val activeTab = tabsState.value.activeTab ?: return null
        return tabComponents[activeTab.id]
    }
    
    // Get tab component by ID
    fun getComponentById(tabId: String): TabComponentWithUI? {
        return tabComponents[tabId]
    }
    
    // Clear all tabs safely
    fun clearAllTabs() {
        // Remove tabs in reverse order to avoid index issues
        val tabCount = tabsState.value.tabs.size
        for (i in tabCount - 1 downTo 0) {
            removeTab(i)
        }
    }
    
    // Close other tabs (keep only the specified tab)
    fun closeOtherTabs(keepIndex: Int) {
        val tabs = tabsState.value.tabs
        if (keepIndex < 0 || keepIndex >= tabs.size) return
        
        // Remove tabs in reverse order to avoid index issues
        for (i in tabs.size - 1 downTo 0) {
            if (i != keepIndex) {
                removeTab(i)
            }
        }
    }
    
    // Close tabs to the right of the specified index
    fun closeTabsToRight(fromIndex: Int) {
        val tabs = tabsState.value.tabs
        if (fromIndex < 0 || fromIndex >= tabs.size - 1) return
        
        // Remove tabs from right to left to avoid index issues
        for (i in tabs.size - 1 downTo fromIndex + 1) {
            removeTab(i)
        }
    }
    
    // Close tabs to the left of the specified index
    fun closeTabsToLeft(fromIndex: Int) {
        if (fromIndex <= 0) return
        
        // Remove tabs from right to left to avoid index issues
        for (i in fromIndex - 1 downTo 0) {
            removeTab(i)
        }
    }
}

