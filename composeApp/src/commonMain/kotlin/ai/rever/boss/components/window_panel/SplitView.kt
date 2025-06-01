package ai.rever.boss.components.window_panel

import ai.rever.boss.components.model.Panel
import ai.rever.boss.components.registery.TabInfo
import ai.rever.boss.components.registery.TabRegistry
import ai.rever.boss.components.window_panel.components.BossResizablePanel
import ai.rever.boss.components.window_panel.components.main_window_panels.BossMainPanel
import ai.rever.boss.components.window_panel.components.main_window_panels.BossTabsComponent
import ai.rever.boss.components.window_panel.components.main_window_panels.createBossAppContext
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import kotlin.random.Random
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import kotlinx.coroutines.delay
import com.arkivanov.decompose.extensions.compose.subscribeAsState

// Sealed class representing the split tree structure
sealed class SplitNode {
    data class Panel(
        val id: String,
        val tabsComponent: BossTabsComponent
    ) : SplitNode()
    
    data class VerticalSplit(
        val left: SplitNode,
        val right: SplitNode
    ) : SplitNode()
    
    data class HorizontalSplit(
        val top: SplitNode,
        val bottom: SplitNode
    ) : SplitNode()
}

enum class SplitOrientation {
    HORIZONTAL, // Split top/bottom
    VERTICAL    // Split left/right
}

@Stable
class SplitViewState(
    private val tabRegistry: TabRegistry,
    initialTabsComponent: BossTabsComponent? = null
) {
    // Root node of the split tree
    private var _rootNode = mutableStateOf<SplitNode>(
        SplitNode.Panel(
            id = "main",
            tabsComponent = initialTabsComponent ?: BossTabsComponent(createBossAppContext, tabRegistry)
        )
    )
    val rootNode: SplitNode get() = _rootNode.value
    
    // Track active panel for file operations
    private var _activePanelId = mutableStateOf("main")
    val activePanelId: String get() = _activePanelId.value
    
    fun setActivePanel(panelId: String) {
        _activePanelId.value = panelId
    }
    
    fun getActiveTabsComponent(): BossTabsComponent? {
        return findPanel(_activePanelId.value)?.tabsComponent
    }
    
    fun openFileInActivePanel(filePath: String, fileName: String) {
        val activeComponent = getActiveTabsComponent() ?: return
        
        // Check if file is already open in any panel
        findPanelWithFile(filePath)?.let { (panelId, component) ->
            component.tabsState.value.tabs
                .indexOfFirst { tab ->
                    tab is ai.rever.boss.components.plugin.tab_types.EditorTabInfo && tab.filePath == filePath
                }
                .takeIf { it >= 0 }
                ?.let { tabIndex ->
                    component.selectTab(tabIndex)
                    setActivePanel(panelId)
                }
            return
        }
        
        // File not open, create new tab in active panel
        val editorTab = ai.rever.boss.components.plugin.tab_types.EditorTabInfo(
            id = "editor-${Random.nextLong()}",
            typeId = ai.rever.boss.components.registery.TabTypeId("editor"),
            title = fileName,
            icon = androidx.compose.material.icons.Icons.Outlined.Code,
            filePath = filePath
        )
        activeComponent.addTab(editorTab).takeIf { it >= 0 }?.let {
            activeComponent.selectTab(it)
        }
    }
    
    fun splitPanel(
        panelId: String,
        orientation: SplitOrientation,
        tabToMove: TabInfo? = null
    ): String {
        val panel = findPanel(panelId) ?: return panelId
        
        // Create new panel with copied tab
        val newPanelId = "split-${Random.nextLong()}"
        val newComponent = BossTabsComponent(createBossAppContext, tabRegistry)
        
        // Copy tab if specified
        tabToMove?.let { tab ->
            val copiedTab = when (tab) {
                is ai.rever.boss.components.plugin.tab_types.EditorTabInfo -> 
                    tab.copy(id = "editor-${Random.nextLong()}")
                is ai.rever.boss.components.plugin.tab_types.fluck.FluckTabInfo -> 
                    tab.copy(id = "fluck-${Random.nextLong()}")
                is ai.rever.boss.components.plugin.tab_types.TerminalTabInfo -> 
                    tab.copy(id = "terminal-${Random.nextLong()}")
                else -> tab
            }
            
            newComponent.addTab(copiedTab).takeIf { it >= 0 }?.let(newComponent::selectTab)
        }
        
        // Create new panel node
        val newPanelNode = SplitNode.Panel(newPanelId, newComponent)
        
        // Replace the panel node with a split node
        _rootNode.value = replacePanelWithSplit(
            _rootNode.value,
            panelId,
            orientation,
            newPanelNode
        )
        
        return newPanelId
    }
    
    private fun replacePanelWithSplit(
        node: SplitNode,
        targetPanelId: String,
        orientation: SplitOrientation,
        newPanel: SplitNode.Panel
    ): SplitNode {
        return when (node) {
            is SplitNode.Panel -> {
                if (node.id == targetPanelId) {
                    // Replace this panel with a split
                    when (orientation) {
                        SplitOrientation.VERTICAL -> SplitNode.VerticalSplit(
                            left = node,  // Original panel keeps all tabs
                            right = newPanel
                        )
                        SplitOrientation.HORIZONTAL -> SplitNode.HorizontalSplit(
                            top = node,   // Original panel keeps all tabs
                            bottom = newPanel
                        )
                    }
                } else {
                    node
                }
            }
            is SplitNode.VerticalSplit -> {
                SplitNode.VerticalSplit(
                    left = replacePanelWithSplit(node.left, targetPanelId, orientation, newPanel),
                    right = replacePanelWithSplit(node.right, targetPanelId, orientation, newPanel)
                )
            }
            is SplitNode.HorizontalSplit -> {
                SplitNode.HorizontalSplit(
                    top = replacePanelWithSplit(node.top, targetPanelId, orientation, newPanel),
                    bottom = replacePanelWithSplit(node.bottom, targetPanelId, orientation, newPanel)
                )
            }
        }
    }
    
    fun closePanel(panelId: String) {
        // Don't close the main panel if it's the only one
        if (panelId == "main" && getAllPanels().size == 1) return
        
        _rootNode.value = removePanel(_rootNode.value, panelId)
        
        // If active panel was closed, switch to first available
        if (_activePanelId.value == panelId) {
            getAllPanels().firstOrNull()?.let {
                _activePanelId.value = it.id
            }
        }
    }
    
    private fun removePanel(node: SplitNode, targetPanelId: String): SplitNode {
        return when (node) {
            is SplitNode.Panel -> {
                // If this is the panel to remove, return a marker that it should be removed
                if (node.id == targetPanelId) {
                    // Return a special marker - we'll handle this in the parent
                    node // For now, return the node and let parent handle it
                } else {
                    node
                }
            }
            is SplitNode.VerticalSplit -> {
                // Check if the target panel is in left subtree
                if (node.left is SplitNode.Panel && node.left.id == targetPanelId) {
                    // Left panel should be removed, return right
                    node.right
                } else if (node.right is SplitNode.Panel && node.right.id == targetPanelId) {
                    // Right panel should be removed, return left
                    node.left
                } else {
                    // Recursively check deeper in the tree
                    val newLeft = if (isPanelInNode(node.left, targetPanelId)) {
                        removePanel(node.left, targetPanelId)
                    } else {
                        node.left
                    }
                    val newRight = if (isPanelInNode(node.right, targetPanelId)) {
                        removePanel(node.right, targetPanelId)
                    } else {
                        node.right
                    }
                    
                    // If either side is now empty, promote the other side
                    when {
                        newLeft === node.left && newRight === node.right -> node // No change
                        else -> SplitNode.VerticalSplit(newLeft, newRight)
                    }
                }
            }
            is SplitNode.HorizontalSplit -> {
                // Check if the target panel is in top subtree
                if (node.top is SplitNode.Panel && node.top.id == targetPanelId) {
                    // Top panel should be removed, return bottom
                    node.bottom
                } else if (node.bottom is SplitNode.Panel && node.bottom.id == targetPanelId) {
                    // Bottom panel should be removed, return top
                    node.top
                } else {
                    // Recursively check deeper in the tree
                    val newTop = if (isPanelInNode(node.top, targetPanelId)) {
                        removePanel(node.top, targetPanelId)
                    } else {
                        node.top
                    }
                    val newBottom = if (isPanelInNode(node.bottom, targetPanelId)) {
                        removePanel(node.bottom, targetPanelId)
                    } else {
                        node.bottom
                    }
                    
                    // If either side is now empty, promote the other side
                    when {
                        newTop === node.top && newBottom === node.bottom -> node // No change
                        else -> SplitNode.HorizontalSplit(newTop, newBottom)
                    }
                }
            }
        }
    }
    
    private fun isPanelInNode(node: SplitNode, panelId: String): Boolean {
        return when (node) {
            is SplitNode.Panel -> node.id == panelId
            is SplitNode.VerticalSplit -> isPanelInNode(node.left, panelId) || isPanelInNode(node.right, panelId)
            is SplitNode.HorizontalSplit -> isPanelInNode(node.top, panelId) || isPanelInNode(node.bottom, panelId)
        }
    }
    
    private fun findPanel(panelId: String): SplitNode.Panel? {
        return findPanelInNode(_rootNode.value, panelId)
    }
    
    private fun findPanelInNode(node: SplitNode, panelId: String): SplitNode.Panel? {
        return when (node) {
            is SplitNode.Panel -> if (node.id == panelId) node else null
            is SplitNode.VerticalSplit -> 
                findPanelInNode(node.left, panelId) ?: findPanelInNode(node.right, panelId)
            is SplitNode.HorizontalSplit -> 
                findPanelInNode(node.top, panelId) ?: findPanelInNode(node.bottom, panelId)
        }
    }
    
    private fun findPanelWithFile(filePath: String): Pair<String, BossTabsComponent>? {
        getAllPanels().forEach { panel ->
            if (panel.tabsComponent.tabsState.value.tabs.any { tab ->
                tab is ai.rever.boss.components.plugin.tab_types.EditorTabInfo && tab.filePath == filePath
            }) {
                return panel.id to panel.tabsComponent
            }
        }
        return null
    }
    
    fun getAllPanels(): List<SplitNode.Panel> {
        return getAllPanelsInNode(_rootNode.value)
    }
    
    private fun getAllPanelsInNode(node: SplitNode): List<SplitNode.Panel> {
        return when (node) {
            is SplitNode.Panel -> listOf(node)
            is SplitNode.VerticalSplit -> 
                getAllPanelsInNode(node.left) + getAllPanelsInNode(node.right)
            is SplitNode.HorizontalSplit -> 
                getAllPanelsInNode(node.top) + getAllPanelsInNode(node.bottom)
        }
    }
    
    fun checkAndCloseEmptyPanels() {
        // First, count how many panels we have in total
        val allPanels = getAllPanels()
        
        // If we only have one panel, don't close it regardless of tabs
        if (allPanels.size <= 1) return
        
        // Find all empty panels
        val emptyPanels = allPanels.filter { panel ->
            panel.tabsComponent.tabsState.value.tabs.isEmpty()
        }
        
        // If all panels are empty, keep the main one
        if (emptyPanels.size == allPanels.size) {
            emptyPanels.filter { it.id != "main" }.forEach { panel ->
                closePanel(panel.id)
            }
        } else {
            // Close all empty panels
            emptyPanels.forEach { panel ->
                closePanel(panel.id)
            }
        }
    }
}

@Composable
fun rememberSplitViewState(
    tabRegistry: TabRegistry,
    initialTabsComponent: BossTabsComponent? = null
): SplitViewState {
    return remember { SplitViewState(tabRegistry, initialTabsComponent) }
}

@Composable
fun SplitViewPanel(
    splitViewState: SplitViewState,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        RenderSplitNode(
            node = splitViewState.rootNode,
            splitViewState = splitViewState
        )
    }
}

@Composable
private fun RenderSplitNode(
    node: SplitNode,
    splitViewState: SplitViewState
) {
    when (node) {
        is SplitNode.Panel -> {
            // Set this panel as active when clicked
            LaunchedEffect(node.id) {
                splitViewState.setActivePanel(node.id)
            }
            
            // Monitor this specific panel's tab count
            val tabsState = node.tabsComponent.tabsState.subscribeAsState()
            LaunchedEffect(node.id, tabsState.value.tabs.size) {
                if (tabsState.value.tabs.isEmpty()) {
                    // Small delay to ensure state is fully updated
                    delay(50)
                    splitViewState.checkAndCloseEmptyPanels()
                }
            }
            
            node.tabsComponent.BossMainPanel(
                splitViewState = splitViewState,
                currentPanelId = node.id
            )
        }
        is SplitNode.VerticalSplit -> {
            BossResizablePanel(
                modifier = Modifier.fillMaxSize(),
                panel = Panel.right,
                isPanelVisible = true,
                isMainVisible = true,
                isRelative = true,
                defaultWeight = 1f,
                mainContent = {
                    RenderSplitNode(
                        node = node.left,
                        splitViewState = splitViewState
                    )
                },
                sideContent = {
                    RenderSplitNode(
                        node = node.right,
                        splitViewState = splitViewState
                    )
                }
            )
        }
        is SplitNode.HorizontalSplit -> {
            BossResizablePanel(
                modifier = Modifier.fillMaxSize(),
                panel = Panel.bottom,
                isPanelVisible = true,
                isMainVisible = true,
                isRelative = true,
                defaultWeight = 1f,
                mainContent = {
                    RenderSplitNode(
                        node = node.top,
                        splitViewState = splitViewState
                    )
                },
                sideContent = {
                    RenderSplitNode(
                        node = node.bottom,
                        splitViewState = splitViewState
                    )
                }
            )
        }
    }
}