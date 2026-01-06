package ai.rever.boss.components.window_panel

import BossDarkAccent
import ai.rever.boss.components.model.Panel
import ai.rever.boss.components.model.PanelDropZones
import ai.rever.boss.components.model.TabDraggableComponent
import ai.rever.boss.components.model.TabDropResult
import ai.rever.boss.components.model.TabDropTarget
import ai.rever.boss.components.plugin.panels.left_bottom.TopOfMind.ActiveTab
import ai.rever.boss.components.registery.TabIcon
import ai.rever.boss.components.registery.TabInfo
import ai.rever.boss.icons.FileIcons
import ai.rever.boss.components.registery.TabRegistry
import ai.rever.boss.components.window_panel.components.BossResizablePanel
import ai.rever.boss.components.window_panel.components.main_window_panels.BossMainPanel
import ai.rever.boss.components.window_panel.components.main_window_panels.BossTabsComponent
import ai.rever.boss.components.window_panel.components.main_window_panels.createBossAppContext
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import kotlin.random.Random
import androidx.compose.material.icons.outlined.Code
import kotlinx.coroutines.delay
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import ai.rever.boss.components.plugin.tab_types.fluck.FluckTabInfo
import ai.rever.boss.components.plugin.tab_types.EditorTabInfo
import ai.rever.boss.components.plugin.tab_types.TerminalTabInfo
import ai.rever.boss.components.registery.TabTypeId

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

/**
 * Represents the screen bounds of a panel in global coordinates.
 */
data class PanelBounds(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float
) {
    val left: Float get() = x
    val right: Float get() = x + width
    val top: Float get() = y
    val bottom: Float get() = y + height

    val centerX: Float get() = x + width / 2
    val centerY: Float get() = y + height / 2

    /** Check if this bounds overlaps with another vertically */
    fun hasVerticalOverlapWith(other: PanelBounds): Boolean {
        return !(bottom <= other.top || top >= other.bottom)
    }

    /** Check if this bounds overlaps with another horizontally */
    fun hasHorizontalOverlapWith(other: PanelBounds): Boolean {
        return !(right <= other.left || left >= other.right)
    }
}

/**
 * Navigation direction for spatial panel navigation.
 */
enum class NavigationDirection {
    LEFT, RIGHT, UP, DOWN
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
    val activePanelIdState: State<String> get() = _activePanelId
    
    // Track last interacted tab for Cmd+R, Cmd+N operations
    private var _lastInteractedTabPanelId = mutableStateOf("main")
    private var _lastInteractedTabId: String? = null
    val lastInteractedTabPanelId: String get() = _lastInteractedTabPanelId.value

    // Track panel activation history for MOST_RECENT_ACTIVE mode in terminal link handling
    // Maintains order of recently activated panels (most recent first, limited to last 10)
    private val _panelActivationHistory = mutableListOf("main")

    // Track preserved workspace states
    private val preservedWorkspaceStates = mutableMapOf<String, PreservedWorkspaceState>()
    private var _currentWorkspaceId: String? = null
    val currentWorkspaceId: String? get() = _currentWorkspaceId

    // Data class to hold preserved state
    data class PreservedWorkspaceState(
        val rootNode: SplitNode,
        val activePanelId: String,
        val workspaceName: String = ""
    )

    // Track panel positions for spatial navigation
    /**
     * Maps panel IDs to their screen bounds.
     * Updated by RenderSplitNode via onGloballyPositioned callbacks.
     */
    private val _panelBounds = mutableStateMapOf<String, PanelBounds>()

    /**
     * Update the bounds for a specific panel.
     * Called from Compose layout during positioning.
     */
    fun updatePanelBounds(panelId: String, bounds: PanelBounds) {
        _panelBounds[panelId] = bounds
    }

    /**
     * Get the current bounds for a panel, or null if not yet positioned.
     */
    fun getPanelBounds(panelId: String): PanelBounds? {
        return _panelBounds[panelId]
    }

    /**
     * Clear bounds for a specific panel (e.g., when removed).
     */
    fun clearPanelBounds(panelId: String) {
        _panelBounds.remove(panelId)
    }

    fun setActivePanel(panelId: String) {
        _activePanelId.value = panelId
        // Record in activation history for MOST_RECENT_ACTIVE mode
        recordPanelActivation(panelId)
    }

    /**
     * Records a panel activation in the history.
     * Moves the panel to the front of the list (most recent), removes duplicates,
     * and limits history to last 10 panels.
     */
    private fun recordPanelActivation(panelId: String) {
        _panelActivationHistory.remove(panelId)
        _panelActivationHistory.add(0, panelId)
        // Limit to last 10 panels to avoid unbounded growth
        while (_panelActivationHistory.size > 10) {
            _panelActivationHistory.removeAt(_panelActivationHistory.size - 1)
        }
    }
    
    fun trackTabInteraction(panelId: String, tabId: String) {
        _lastInteractedTabPanelId.value = panelId
        _lastInteractedTabId = tabId
        // Also set as active panel (which also records in activation history)
        setActivePanel(panelId)
    }
    
    fun getLastInteractedTabComponent(): BossTabsComponent? {
        return findPanel(_lastInteractedTabPanelId.value)?.tabsComponent
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
                    tab is EditorTabInfo && tab.filePath == filePath
                }
                .takeIf { it >= 0 }
                ?.let { tabIndex ->
                    component.selectTab(tabIndex)
                    setActivePanel(panelId)
                }
            return
        }

        // File not open, create new tab in active panel
        val fileIconInfo = FileIcons.forFile(fileName)
        val editorTab = EditorTabInfo(
            id = "editor-${Random.nextLong()}",
            typeId = ai.rever.boss.components.registery.TabTypeId("editor"),
            title = fileName,
            icon = fileIconInfo.icon,
            tabIcon = TabIcon.Vector(fileIconInfo.icon, fileIconInfo.color),
            filePath = filePath
        )
        activeComponent.addTab(editorTab).takeIf { it >= 0 }?.let {
            activeComponent.selectTab(it)
        }
    }

    /**
     * Open a URL in the active panel
     *
     * If the URL is already open in any panel, switches to that tab.
     * Otherwise, creates a new Fluck browser tab in the active panel.
     * If no active panel exists (app just started), uses the first available panel.
     *
     * @param url The URL to open
     * @param title Initial title for the tab
     */
    fun openUrlInActivePanel(url: String, title: String, forceNewTab: Boolean = false) {
        val activeComponent = getActiveTabsComponent()

        // If no active component, this is likely the first URL on app startup
        // Find any available panel to add the tab to
        if (activeComponent == null) {
            // Try to get first available panel
            val firstPanel = getAllPanels().firstOrNull()
            if (firstPanel == null) {
                println("SplitView: ERROR - No panels available to create tab")
                return
            }

            val component = firstPanel.tabsComponent

            // Create tab in first available panel
            val fluckTab = FluckTabInfo(
                id = "fluck-${Random.nextLong()}",
                typeId = TabTypeId("fluck"),
                _title = title,
                url = url
            )

            val tabIndex = component.addTab(fluckTab)
            if (tabIndex >= 0) {
                component.selectTab(tabIndex)
                setActivePanel(firstPanel.id)
            } else {
                println("SplitView: ERROR - Failed to add tab to panel")
            }
            return
        }

        // Check if URL is already open in any panel (skip if forceNewTab is true)
        if (!forceNewTab) {
            findPanelWithUrl(url)?.let { (panelId, component) ->
                component.tabsState.value.tabs
                    .indexOfFirst { tab ->
                        tab is FluckTabInfo &&
                        tab.currentUrl == url  // Only check current URL to avoid focusing tabs that navigated away
                    }
                    .takeIf { it >= 0 }
                    ?.let { tabIndex ->
                        component.selectTab(tabIndex)
                        setActivePanel(panelId)
                    }
                return
            }
        }

        // URL not open, create new Fluck tab in active panel
        val fluckTab = FluckTabInfo(
            id = "fluck-${Random.nextLong()}",
            typeId = TabTypeId("fluck"),
            _title = title,
            url = url
        )
        activeComponent.addTab(fluckTab).takeIf { it >= 0 }?.let {
            activeComponent.selectTab(it)
        }
    }

    /**
     * Open a terminal tab in the active panel
     *
     * Creates a new terminal tab in the active panel.
     * If no active panel exists (app just started), uses the first available panel.
     *
     * @param command Optional initial command to run in the terminal
     */
    fun openTerminalInActivePanel(command: String? = null) {
        val activeComponent = getActiveTabsComponent()

        // If no active component, this is likely the first terminal on app startup
        // Find any available panel to add the tab to
        if (activeComponent == null) {
            // Try to get first available panel
            val firstPanel = getAllPanels().firstOrNull()
            if (firstPanel == null) {
                println("SplitView: ERROR - No panels available to create terminal tab")
                return
            }

            val component = firstPanel.tabsComponent

            // Get current project path for terminal working directory
            val projectPath = ai.rever.boss.components.plugin.panels.left_top.ProjectState.selectedProject.value.path

            // Create terminal tab in first available panel
            val terminalTab = TerminalTabInfo(
                id = "terminal-${System.currentTimeMillis()}",
                typeId = TabTypeId("terminal"),
                title = if (command != null) "Terminal: $command" else "Terminal",
                initialCommand = command,
                workingDirectory = projectPath.ifEmpty { null }
            )

            val tabIndex = component.addTab(terminalTab)
            if (tabIndex >= 0) {
                component.selectTab(tabIndex)
                setActivePanel(firstPanel.id)
                println("SplitView: Terminal tab created in first panel${if (command != null) " with command: $command" else ""}")
            } else {
                println("SplitView: ERROR - Failed to add terminal tab to panel")
            }
            return
        }

        // Get current project path for terminal working directory
        val projectPath = ai.rever.boss.components.plugin.panels.left_top.ProjectState.selectedProject.value.path

        // Create new terminal tab in active panel
        val terminalTab = TerminalTabInfo(
            id = "terminal-${System.currentTimeMillis()}",
            typeId = TabTypeId("terminal"),
            title = if (command != null) "Terminal: $command" else "Terminal",
            initialCommand = command,
            workingDirectory = projectPath.ifEmpty { null }
        )

        val tabIndex = activeComponent.addTab(terminalTab)
        if (tabIndex >= 0) {
            activeComponent.selectTab(tabIndex)
            println("SplitView: Terminal tab created${if (command != null) " with command: $command" else ""}")
        } else {
            println("SplitView: ERROR - Failed to create terminal tab")
        }
    }

    fun splitPanel(
        panelId: String,
        orientation: SplitOrientation,
        tabToMove: TabInfo? = null
    ): String {
        findPanel(panelId) ?: return panelId
        
        // Create new panel with copied tab
        val newPanelId = "split-${Random.nextLong()}"
        val newComponent = BossTabsComponent(createBossAppContext, tabRegistry)
        
        // Copy tab if specified
        tabToMove?.let { tab ->
            val copiedTab = when (tab) {
                is EditorTabInfo -> 
                    tab.copy(id = "editor-${Random.nextLong()}")
                is FluckTabInfo ->
                    tab.copy(
                        id = "fluck-${Random.nextLong()}",
                        _currentUrl = tab.currentUrl, // Preserve the current URL (not initial URL)
                        navigationHistory = tab.navigationHistory.toMutableList() // Deep copy the history
                    )
                is TerminalTabInfo -> 
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

        // First, dispose all tabs in the panel being closed
        findPanel(panelId)?.let { panel ->
            panel.tabsComponent.clearAllTabs()
        }

        _rootNode.value = removePanel(_rootNode.value, panelId)

        // Clean up activation history to prevent accumulation of deleted panel IDs
        _panelActivationHistory.remove(panelId)

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
    
    /**
     * Find a panel by its ID.
     * Returns null if no panel with the given ID exists.
     */
    internal fun findPanel(panelId: String): SplitNode.Panel? {
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

    /**
     * Find the panel that contains a tab with the given URL
     *
     * @param url The URL to search for
     * @return Pair of panel ID and BossTabsComponent if found, null otherwise
     */
    private fun findPanelWithUrl(url: String): Pair<String, BossTabsComponent>? {
        getAllPanels().forEach { panel ->
            if (panel.tabsComponent.tabsState.value.tabs.any { tab ->
                tab is FluckTabInfo &&
                tab.currentUrl == url  // Only check current URL to avoid focusing tabs that navigated away
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


    /**
     * Check if any splits exist (more than one panel).
     */
    fun hasSplits(): Boolean = getAllPanels().size > 1

    /**
     * Get the first panel that is not the currently active panel.
     * Useful for opening content in an existing split.
     */
    fun getOtherPanel(): SplitNode.Panel? {
        val allPanels = getAllPanels()
        return allPanels.firstOrNull { it.id != activePanelId }
    }


    /**
     * Get the most recently active panel that is not the specified panel.
     * Uses panel activation history to prefer panels the user recently interacted with.
     * Useful for opening content in a split other than where the action originated.
     *
     * @param excludePanelId The panel ID to exclude from the search
     * @return The most recently active panel with a different ID, or null if only one panel exists
     */
    fun getOtherPanelExcluding(excludePanelId: String): SplitNode.Panel? {
        val allPanels = getAllPanels()
        val allPanelIds = allPanels.map { it.id }.toSet()

        // Find the most recently activated panel (excluding the specified one) that still exists
        for (panelId in _panelActivationHistory) {
            if (panelId != excludePanelId && panelId in allPanelIds) {
                return allPanels.firstOrNull { it.id == panelId }
            }
        }

        // Fallback: return the first available panel that isn't the excluded one
        return allPanels.firstOrNull { it.id != excludePanelId }
    }

    /**
     * Get the first panel that is not the specified panel (FIRST_AVAILABLE mode).
     * Unlike getOtherPanelExcluding which uses activation history, this simply
     * returns the first panel in the tree traversal order.
     *
     * @param excludePanelId The panel ID to exclude from the search
     * @return The first available panel with a different ID, or null if only one panel exists
     */
    fun getFirstOtherPanelExcluding(excludePanelId: String): SplitNode.Panel? {
        return getAllPanels().firstOrNull { it.id != excludePanelId }
    }

    /**
     * Find the panel that contains a tab with the given ID.
     * Issue #347: Used for runner terminal management.
     *
     * @param tabId The tab ID to search for
     * @return The panel containing the tab, or null if not found
     */
    fun findPanelWithTab(tabId: String): SplitNode.Panel? {
        return getAllPanels().find { panel ->
            panel.tabsComponent.tabsState.value.tabs.any { it.id == tabId }
        }
    }

    // Spatial Navigation Methods

    /**
     * Find the best panel to navigate to in the given direction from the active panel.
     * Returns null if no suitable panel exists in that direction.
     */
    fun findPanelInDirection(direction: NavigationDirection): SplitNode.Panel? {
        val currentBounds = getPanelBounds(activePanelId) ?: return null
        val allPanels = getAllPanels().filter { it.id != activePanelId }

        return when (direction) {
            NavigationDirection.LEFT -> findClosestPanelToLeft(currentBounds, allPanels)
            NavigationDirection.RIGHT -> findClosestPanelToRight(currentBounds, allPanels)
            NavigationDirection.UP -> findClosestPanelAbove(currentBounds, allPanels)
            NavigationDirection.DOWN -> findClosestPanelBelow(currentBounds, allPanels)
        }
    }

    /**
     * Find the closest panel to the left of the current bounds.
     * Prioritizes panels with maximum vertical overlap.
     */
    private fun findClosestPanelToLeft(
        currentBounds: PanelBounds,
        allPanels: List<SplitNode.Panel>
    ): SplitNode.Panel? {
        data class Candidate(
            val panel: SplitNode.Panel,
            val bounds: PanelBounds,
            val overlap: Float,
            val distance: Float
        )

        val candidates = allPanels.mapNotNull { panel ->
            val bounds = getPanelBounds(panel.id) ?: return@mapNotNull null

            // Panel must be to the left (right edge <= current left edge, with small tolerance)
            if (bounds.right > currentBounds.left + 1f) return@mapNotNull null

            // Calculate vertical overlap
            val overlapTop = maxOf(currentBounds.top, bounds.top)
            val overlapBottom = minOf(currentBounds.bottom, bounds.bottom)
            val overlap = maxOf(0f, overlapBottom - overlapTop)

            // Must have some vertical overlap to be reachable
            if (overlap <= 0f) return@mapNotNull null

            // Calculate horizontal distance (gap between panels)
            val distance = currentBounds.left - bounds.right

            Candidate(panel, bounds, overlap, distance)
        }

        if (candidates.isEmpty()) return null

        // Sort by overlap (descending), then by distance (ascending)
        val best = candidates.maxByOrNull { candidate ->
            candidate.overlap * 1000f - candidate.distance
        }!!

        return best.panel
    }

    /**
     * Find the closest panel to the right of the current bounds.
     */
    private fun findClosestPanelToRight(
        currentBounds: PanelBounds,
        allPanels: List<SplitNode.Panel>
    ): SplitNode.Panel? {
        data class Candidate(
            val panel: SplitNode.Panel,
            val bounds: PanelBounds,
            val overlap: Float,
            val distance: Float
        )

        val candidates = allPanels.mapNotNull { panel ->
            val bounds = getPanelBounds(panel.id) ?: return@mapNotNull null

            // Panel must be to the right (left edge >= current right edge)
            if (bounds.left < currentBounds.right - 1f) return@mapNotNull null

            // Calculate vertical overlap
            val overlapTop = maxOf(currentBounds.top, bounds.top)
            val overlapBottom = minOf(currentBounds.bottom, bounds.bottom)
            val overlap = maxOf(0f, overlapBottom - overlapTop)

            if (overlap <= 0f) return@mapNotNull null

            // Calculate horizontal distance
            val distance = bounds.left - currentBounds.right

            Candidate(panel, bounds, overlap, distance)
        }

        if (candidates.isEmpty()) return null

        val best = candidates.maxByOrNull { candidate ->
            candidate.overlap * 1000f - candidate.distance
        }!!

        return best.panel
    }

    /**
     * Find the closest panel above the current bounds.
     * Prioritizes panels with maximum horizontal overlap.
     */
    private fun findClosestPanelAbove(
        currentBounds: PanelBounds,
        allPanels: List<SplitNode.Panel>
    ): SplitNode.Panel? {
        data class Candidate(
            val panel: SplitNode.Panel,
            val bounds: PanelBounds,
            val overlap: Float,
            val distance: Float
        )

        val candidates = allPanels.mapNotNull { panel ->
            val bounds = getPanelBounds(panel.id) ?: return@mapNotNull null

            // Panel must be above (bottom edge <= current top edge)
            if (bounds.bottom > currentBounds.top + 1f) return@mapNotNull null

            // Calculate horizontal overlap
            val overlapLeft = maxOf(currentBounds.left, bounds.left)
            val overlapRight = minOf(currentBounds.right, bounds.right)
            val overlap = maxOf(0f, overlapRight - overlapLeft)

            if (overlap <= 0f) return@mapNotNull null

            // Calculate vertical distance
            val distance = currentBounds.top - bounds.bottom

            Candidate(panel, bounds, overlap, distance)
        }

        if (candidates.isEmpty()) return null

        val best = candidates.maxByOrNull { candidate ->
            candidate.overlap * 1000f - candidate.distance
        }!!

        return best.panel
    }

    /**
     * Find the closest panel below the current bounds.
     */
    private fun findClosestPanelBelow(
        currentBounds: PanelBounds,
        allPanels: List<SplitNode.Panel>
    ): SplitNode.Panel? {
        data class Candidate(
            val panel: SplitNode.Panel,
            val bounds: PanelBounds,
            val overlap: Float,
            val distance: Float
        )

        val candidates = allPanels.mapNotNull { panel ->
            val bounds = getPanelBounds(panel.id) ?: return@mapNotNull null

            // Panel must be below (top edge >= current bottom edge)
            if (bounds.top < currentBounds.bottom - 1f) return@mapNotNull null

            // Calculate horizontal overlap
            val overlapLeft = maxOf(currentBounds.left, bounds.left)
            val overlapRight = minOf(currentBounds.right, bounds.right)
            val overlap = maxOf(0f, overlapRight - overlapLeft)

            if (overlap <= 0f) return@mapNotNull null

            // Calculate vertical distance
            val distance = bounds.top - currentBounds.bottom

            Candidate(panel, bounds, overlap, distance)
        }

        if (candidates.isEmpty()) return null

        val best = candidates.maxByOrNull { candidate ->
            candidate.overlap * 1000f - candidate.distance
        }!!

        return best.panel
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
    
    fun clearAllPanels() {
        // Reset to single main panel
        val mainComponent = BossTabsComponent(createBossAppContext, tabRegistry)
        _rootNode.value = SplitNode.Panel(
            id = "main",
            tabsComponent = mainComponent
        )
        _activePanelId.value = "main"
    }
    
    fun preserveCurrentState(workspaceId: String, workspaceName: String = "") {
        // Save current state before switching
        _currentWorkspaceId?.let { currentId ->
            preservedWorkspaceStates[currentId] = PreservedWorkspaceState(
                rootNode = _rootNode.value,
                activePanelId = _activePanelId.value,
                workspaceName = workspaceName
            )
        }
        _currentWorkspaceId = workspaceId
    }
    
    fun restorePreservedState(workspaceId: String): Boolean {
        // Check if we have a preserved state for this workspace
        val preservedState = preservedWorkspaceStates[workspaceId]
        return if (preservedState != null) {
            // Restore the preserved state
            _rootNode.value = preservedState.rootNode
            _activePanelId.value = preservedState.activePanelId
            _currentWorkspaceId = workspaceId
            true
        } else {
            _currentWorkspaceId = workspaceId
            false
        }
    }
    
    fun getPanelTabsComponent(panelId: String): BossTabsComponent? {
        return findPanel(panelId)?.tabsComponent
    }

    /**
     * Get a panel by its ID.
     */
    fun getPanel(panelId: String): SplitNode.Panel? {
        return findPanel(panelId)
    }
    
    fun selectTabInPanel(tabId: String, panelId: String) {
        val panel = findPanel(panelId)
        if (panel != null) {
            // Set the panel as active
            setActivePanel(panelId)
            
            // Find the tab index and select it
            val tabsComponent = panel.tabsComponent
            val tabs = tabsComponent.tabsState.value.tabs
            val tabIndex = tabs.indexOfFirst { it.id == tabId }
            
            if (tabIndex >= 0) {
                tabsComponent.selectTab(tabIndex)
            }
        }
    }
    
    fun collectAllActiveFluckTabs(windowId: String = "unknown"): List<ActiveTab> {
        val result = mutableListOf<ActiveTab>()
        val seenTabIds = mutableSetOf<String>()

        // Collect from current state
        _currentWorkspaceId?.let { workspaceId ->
            // Get the actual workspace name from preserved states or use a default
            val workspaceName = preservedWorkspaceStates[workspaceId]?.workspaceName
                ?: when (workspaceId) {
                    "last-session" -> "Last Session"
                    else -> "Current Workspace"
                }

            getAllPanels().forEach { panel ->
                panel.tabsComponent.tabsState.value.tabs.forEach { tab ->
                    if (tab is FluckTabInfo && !seenTabIds.contains(tab.id)) {
                        result.add(
                            ActiveTab(
                                tabInfo = tab,
                                workspaceId = workspaceId,
                                workspaceName = workspaceName,
                                panelId = panel.id,
                                windowId = windowId
                            )
                        )
                        seenTabIds.add(tab.id)
                    }
                }
            }
        }

        // Collect from preserved states (only if not already in current state)
        preservedWorkspaceStates.forEach { (workspaceId, state) ->
            if (workspaceId != _currentWorkspaceId) {
                collectFluckTabsFromNode(state.rootNode, workspaceId, state.workspaceName, windowId, result, seenTabIds)
            }
        }

        return result
    }
    
    /**
     * Cleanup preserved state for a deleted workspace
     */
    fun cleanupDeletedWorkspace(workspaceId: String) {
        preservedWorkspaceStates.remove(workspaceId)
    }
    
    /**
     * Cleanup preserved states for workspaces that no longer exist
     */
    fun cleanupDeletedWorkspaces(existingWorkspaceIds: Set<String>) {
        val idsToRemove = preservedWorkspaceStates.keys.filter { workspaceId ->
            // Keep special workspaces like "last-session" and only remove user workspaces
            !existingWorkspaceIds.contains(workspaceId) && workspaceId != "last-session"
        }
        
        idsToRemove.forEach { workspaceId ->
            preservedWorkspaceStates.remove(workspaceId)
        }
    }
    
    fun collectAllActiveTabs(workspaceManager: ai.rever.boss.components.workspaces.WorkspaceManager? = null, windowId: String = "unknown"): List<ActiveTab> {
        val result = mutableListOf<ActiveTab>()
        val seenTabIds = mutableSetOf<String>()
        val seenConfigIds = mutableSetOf<String>()
        
        // Helper function to get proper workspace name
        fun getWorkspaceName(workspaceId: String): String {
            return workspaceManager?.workspaces?.value?.find { it.id == workspaceId }?.name
                ?: preservedWorkspaceStates[workspaceId]?.workspaceName
                ?: when (workspaceId) {
                    "last-session" -> "Last Session"
                    else -> "Workspace $workspaceId"
                }
        }
        
        // Collect from current state (only if it has tabs)
        _currentWorkspaceId?.let { workspaceId ->
            val currentTabs = mutableListOf<ActiveTab>()
            
            getAllPanels().forEach { panel ->
                panel.tabsComponent.tabsState.value.tabs.forEach { tab ->
                    if (!seenTabIds.contains(tab.id)) {
                        currentTabs.add(
                            ActiveTab(
                                tabInfo = tab,
                                workspaceId = workspaceId,
                                workspaceName = getWorkspaceName(workspaceId),
                                panelId = panel.id,
                                windowId = windowId
                            )
                        )
                        seenTabIds.add(tab.id)
                    }
                }
            }
            
            // Only add current workspace if it has tabs
            if (currentTabs.isNotEmpty()) {
                result.addAll(currentTabs)
                seenConfigIds.add(workspaceId)
            }
        }
        
        // Collect from preserved states (only if not already added)
        preservedWorkspaceStates.forEach { (workspaceId, state) ->
            if (!seenConfigIds.contains(workspaceId)) {
                collectAllTabsFromNode(state.rootNode, workspaceId, getWorkspaceName(workspaceId), windowId, result, seenTabIds)
                if (result.any { it.workspaceId == workspaceId }) {
                    seenConfigIds.add(workspaceId)
                }
            }
        }
        
        return result
    }
    
    private fun collectFluckTabsFromNode(
        node: SplitNode,
        workspaceId: String,
        workspaceName: String,
        windowId: String,
        result: MutableList<ActiveTab>,
        seenTabIds: MutableSet<String>
    ) {
        when (node) {
            is SplitNode.Panel -> {
                node.tabsComponent.tabsState.value.tabs.forEach { tab ->
                    if (tab is FluckTabInfo && !seenTabIds.contains(tab.id)) {
                        result.add(
                            ActiveTab(
                                tabInfo = tab,
                                workspaceId = workspaceId,
                                workspaceName = workspaceName,
                                panelId = node.id,
                                windowId = windowId
                            )
                        )
                        seenTabIds.add(tab.id)
                    }
                }
            }
            is SplitNode.VerticalSplit -> {
                collectFluckTabsFromNode(node.left, workspaceId, workspaceName, windowId, result, seenTabIds)
                collectFluckTabsFromNode(node.right, workspaceId, workspaceName, windowId, result, seenTabIds)
            }
            is SplitNode.HorizontalSplit -> {
                collectFluckTabsFromNode(node.top, workspaceId, workspaceName, windowId, result, seenTabIds)
                collectFluckTabsFromNode(node.bottom, workspaceId, workspaceName, windowId, result, seenTabIds)
            }
        }
    }
    
    private fun collectAllTabsFromNode(
        node: SplitNode,
        workspaceId: String,
        workspaceName: String,
        windowId: String,
        result: MutableList<ActiveTab>,
        seenTabIds: MutableSet<String>
    ) {
        when (node) {
            is SplitNode.Panel -> {
                node.tabsComponent.tabsState.value.tabs.forEach { tab ->
                    if (!seenTabIds.contains(tab.id)) {
                        result.add(
                            ActiveTab(
                                tabInfo = tab,
                                workspaceId = workspaceId,
                                workspaceName = workspaceName,
                                panelId = node.id,
                                windowId = windowId
                            )
                        )
                        seenTabIds.add(tab.id)
                    }
                }
            }
            is SplitNode.VerticalSplit -> {
                collectAllTabsFromNode(node.left, workspaceId, workspaceName, windowId, result, seenTabIds)
                collectAllTabsFromNode(node.right, workspaceId, workspaceName, windowId, result, seenTabIds)
            }
            is SplitNode.HorizontalSplit -> {
                collectAllTabsFromNode(node.top, workspaceId, workspaceName, windowId, result, seenTabIds)
                collectAllTabsFromNode(node.bottom, workspaceId, workspaceName, windowId, result, seenTabIds)
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
    modifier: Modifier = Modifier,
    tabDragComponent: TabDraggableComponent? = null,
    onTabDropResult: (TabDropResult) -> Unit = {},
    onShowSettings: (() -> Unit)? = null,
    onOpenProjectDialog: (() -> Unit)? = null,
    onNewProject: (() -> Unit)? = null
) {
    Box(modifier = modifier.fillMaxSize()) {
        RenderSplitNode(
            node = splitViewState.rootNode,
            splitViewState = splitViewState,
            tabDragComponent = tabDragComponent,
            onTabDropResult = onTabDropResult,
            onShowSettings = onShowSettings,
            onOpenProjectDialog = onOpenProjectDialog,
            onNewProject = onNewProject
        )
    }
}

@Composable
private fun RenderSplitNode(
    node: SplitNode,
    splitViewState: SplitViewState,
    tabDragComponent: TabDraggableComponent? = null,
    onTabDropResult: (TabDropResult) -> Unit = {},
    onShowSettings: (() -> Unit)? = null,
    onOpenProjectDialog: (() -> Unit)? = null,
    onNewProject: (() -> Unit)? = null
) {
    when (node) {
        is SplitNode.Panel -> {
            // key() preserves panel composition identity when split tree restructures
            key(node.id) {
                // Cleanup panel bounds when panel is removed from composition
                // This prevents memory leaks in tabDragComponent's bound maps
                DisposableEffect(node.id, tabDragComponent) {
                    onDispose {
                        splitViewState.clearPanelBounds(node.id)
                        tabDragComponent?.unregisterPanel(node.id)
                    }
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

                // Track drop target for panel drop zone highlights
                val dropTarget = tabDragComponent?.dropTarget
                val isDragging = tabDragComponent?.isDragging == true
                val draggingTab = tabDragComponent?.draggingTab

                // Capture panel position for spatial navigation and drop zones
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .onGloballyPositioned { coordinates ->
                            val bounds = coordinates.boundsInRoot()
                            splitViewState.updatePanelBounds(
                                panelId = node.id,
                                bounds = PanelBounds(
                                    x = bounds.left,
                                    y = bounds.top,
                                    width = bounds.width,
                                    height = bounds.height
                                )
                            )
                            // Register panel drop zones for drag system
                            if (tabDragComponent != null) {
                                val windowBounds = coordinates.boundsInWindow()
                                tabDragComponent.registerPanelDropZones(node.id, windowBounds)
                            }
                        }
                ) {
                    node.tabsComponent.BossMainPanel(
                        splitViewState = splitViewState,
                        currentPanelId = node.id,
                        tabDragComponent = tabDragComponent,
                        onTabDropResult = onTabDropResult,
                        onShowSettings = onShowSettings,
                        onOpenProjectDialog = onOpenProjectDialog,
                        onNewProject = onNewProject
                    )

                    // Show drop zone highlights when dragging over this panel
                    if (isDragging && draggingTab != null && draggingTab.sourcePanelId != node.id) {
                        PanelDropZoneOverlay(
                            panelId = node.id,
                            dropTarget = dropTarget
                        )
                    }
                }
            }
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
                        splitViewState = splitViewState,
                        tabDragComponent = tabDragComponent,
                        onTabDropResult = onTabDropResult,
                        onShowSettings = onShowSettings,
                        onOpenProjectDialog = onOpenProjectDialog,
                        onNewProject = onNewProject
                    )
                },
                sideContent = {
                    RenderSplitNode(
                        node = node.right,
                        splitViewState = splitViewState,
                        tabDragComponent = tabDragComponent,
                        onTabDropResult = onTabDropResult,
                        onShowSettings = onShowSettings,
                        onOpenProjectDialog = onOpenProjectDialog,
                        onNewProject = onNewProject
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
                        splitViewState = splitViewState,
                        tabDragComponent = tabDragComponent,
                        onTabDropResult = onTabDropResult,
                        onShowSettings = onShowSettings,
                        onOpenProjectDialog = onOpenProjectDialog,
                        onNewProject = onNewProject
                    )
                },
                sideContent = {
                    RenderSplitNode(
                        node = node.bottom,
                        splitViewState = splitViewState,
                        tabDragComponent = tabDragComponent,
                        onTabDropResult = onTabDropResult,
                        onShowSettings = onShowSettings,
                        onOpenProjectDialog = onOpenProjectDialog,
                        onNewProject = onNewProject
                    )
                }
            )
        }
    }
}

/**
 * Overlay that shows drop zone highlights on panel edges during drag operations.
 */
@Composable
private fun PanelDropZoneOverlay(
    panelId: String,
    dropTarget: TabDropTarget?
) {
    // Check which zone is highlighted
    val isLeftHighlighted = dropTarget is TabDropTarget.SplitPanel &&
        dropTarget.panelId == panelId &&
        dropTarget.orientation == SplitOrientation.VERTICAL

    val isRightHighlighted = isLeftHighlighted // Same condition for vertical split

    val isTopHighlighted = dropTarget is TabDropTarget.SplitPanel &&
        dropTarget.panelId == panelId &&
        dropTarget.orientation == SplitOrientation.HORIZONTAL

    val isBottomHighlighted = isTopHighlighted // Same condition for horizontal split

    val isCenterHighlighted = dropTarget is TabDropTarget.ExistingPanel &&
        dropTarget.panelId == panelId

    Box(modifier = Modifier.fillMaxSize()) {
        // Left edge highlight
        if (isLeftHighlighted) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(60.dp)
                    .fillMaxHeight()
                    .alpha(0.3f)
                    .background(BossDarkAccent)
            )
        }

        // Right edge highlight
        if (isRightHighlighted) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(60.dp)
                    .fillMaxHeight()
                    .alpha(0.3f)
                    .background(BossDarkAccent)
            )
        }

        // Top edge highlight
        if (isTopHighlighted) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(60.dp)
                    .alpha(0.3f)
                    .background(BossDarkAccent)
            )
        }

        // Bottom edge highlight
        if (isBottomHighlighted) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(60.dp)
                    .alpha(0.3f)
                    .background(BossDarkAccent)
            )
        }

        // Center highlight (add to existing panel)
        if (isCenterHighlighted) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(0.15f)
                    .background(BossDarkAccent)
            )
        }
    }
}
