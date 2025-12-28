package ai.rever.boss.components.model

import ai.rever.boss.components.registery.TabIcon
import ai.rever.boss.components.registery.TabInfo
import ai.rever.boss.components.window_panel.SplitOrientation
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import java.util.concurrent.atomic.AtomicLong

/**
 * Information about a tab being dragged.
 */
data class DraggingTabInfo(
    val tabInfo: TabInfo,
    val sourcePanelId: String,
    val sourceIndex: Int,
    val title: String,
    val icon: TabIcon?
)

/**
 * Represents the target location where a tab can be dropped.
 */
sealed class TabDropTarget {
    /**
     * Reorder within the same tab bar.
     */
    data class Reorder(
        val panelId: String,
        val targetIndex: Int
    ) : TabDropTarget()

    /**
     * Create a new split panel.
     */
    data class SplitPanel(
        val panelId: String,
        val orientation: SplitOrientation
    ) : TabDropTarget()

    /**
     * Move to an existing panel's tab bar.
     */
    data class ExistingPanel(
        val panelId: String
    ) : TabDropTarget()
}

/**
 * Result of a tab drop operation.
 */
sealed class TabDropResult {
    data class Reorder(
        val panelId: String,
        val fromIndex: Int,
        val toIndex: Int
    ) : TabDropResult()

    data class MoveToPanel(
        val tabInfo: TabInfo,
        val sourcePanelId: String,
        val sourceIndex: Int,
        val targetPanelId: String
    ) : TabDropResult()

    data class CreateSplit(
        val tabInfo: TabInfo,
        val sourcePanelId: String,
        val sourceIndex: Int,
        val targetPanelId: String,
        val orientation: SplitOrientation
    ) : TabDropResult()
}

/**
 * Drop zone positions for a panel (edges for split creation).
 */
data class PanelDropZones(
    val panelBounds: Rect,
    val leftZone: Rect,
    val rightZone: Rect,
    val topZone: Rect,
    val bottomZone: Rect,
    val centerZone: Rect
) {
    companion object {
        fun fromBounds(bounds: Rect, edgeSize: Float = 60f): PanelDropZones {
            val effectiveEdgeSize = minOf(edgeSize, bounds.width / 4, bounds.height / 4)

            return PanelDropZones(
                panelBounds = bounds,
                leftZone = Rect(
                    left = bounds.left,
                    top = bounds.top + effectiveEdgeSize,
                    right = bounds.left + effectiveEdgeSize,
                    bottom = bounds.bottom - effectiveEdgeSize
                ),
                rightZone = Rect(
                    left = bounds.right - effectiveEdgeSize,
                    top = bounds.top + effectiveEdgeSize,
                    right = bounds.right,
                    bottom = bounds.bottom - effectiveEdgeSize
                ),
                topZone = Rect(
                    left = bounds.left + effectiveEdgeSize,
                    top = bounds.top,
                    right = bounds.right - effectiveEdgeSize,
                    bottom = bounds.top + effectiveEdgeSize
                ),
                bottomZone = Rect(
                    left = bounds.left + effectiveEdgeSize,
                    top = bounds.bottom - effectiveEdgeSize,
                    right = bounds.right - effectiveEdgeSize,
                    bottom = bounds.bottom
                ),
                centerZone = Rect(
                    left = bounds.left + effectiveEdgeSize,
                    top = bounds.top + effectiveEdgeSize,
                    right = bounds.right - effectiveEdgeSize,
                    bottom = bounds.bottom - effectiveEdgeSize
                )
            )
        }
    }
}

/**
 * Holds the state and logic for the tab drag-and-drop system.
 */
@Stable
class TabDraggableComponent {

    /**
     * The tab currently being dragged, or null if no drag is in progress.
     */
    var draggingTab by mutableStateOf<DraggingTabInfo?>(null)
        private set

    /**
     * Absolute position where the drag started.
     */
    var dragStartPosition by mutableStateOf<Offset?>(null)
        private set

    /**
     * Accumulated delta since the drag started.
     */
    var dragDelta by mutableStateOf(Offset.Zero)
        private set

    /**
     * The current drop target being hovered over, or null.
     */
    var dropTarget by mutableStateOf<TabDropTarget?>(null)
        private set

    /**
     * Track tab bounds for reorder detection within a tab bar.
     * Key: tabId (format: "panelId:tabId"), Value: bounds in window coordinates
     */
    val tabBounds = mutableStateMapOf<String, Rect>()

    /**
     * Track tab bar bounds for each panel.
     * Key: panelId, Value: bounds of the tab bar area
     */
    val tabBarBounds = mutableStateMapOf<String, Rect>()

    /**
     * Track panel drop zones for split creation.
     * Key: panelId, Value: drop zone bounds
     */
    val panelDropZones = mutableStateMapOf<String, PanelDropZones>()

    /**
     * Whether a drag operation is currently in progress.
     */
    val isDragging: Boolean
        get() = draggingTab != null

    /**
     * Timestamp of last drop target update for throttling.
     * Prevents excessive calculations during drag (every pixel movement).
     * Uses AtomicLong for thread safety even though primarily accessed from UI thread.
     */
    private val lastDropTargetUpdateTime = AtomicLong(0L)

    /**
     * Minimum interval between drop target updates in milliseconds.
     * ~60fps = 16ms between updates
     */
    private val dropTargetUpdateInterval = 16L

    /**
     * Start dragging a tab.
     */
    fun startDragging(
        tabInfo: TabInfo,
        panelId: String,
        index: Int,
        startPosition: Offset
    ) {
        if (draggingTab != null) return

        draggingTab = DraggingTabInfo(
            tabInfo = tabInfo,
            sourcePanelId = panelId,
            sourceIndex = index,
            title = tabInfo.title,
            icon = tabInfo.tabIcon
        )
        dragStartPosition = startPosition
        dragDelta = Offset.Zero
        updateDropTarget()
    }

    /**
     * Update the drag delta during a drag gesture.
     * Throttled to ~60fps to avoid excessive drop target calculations.
     */
    fun updateDrag(delta: Offset) {
        if (draggingTab == null || dragStartPosition == null) return
        dragDelta += delta

        // Throttle drop target updates to avoid excessive calculations
        val now = System.currentTimeMillis()
        val lastUpdate = lastDropTargetUpdateTime.get()
        if (now - lastUpdate >= dropTargetUpdateInterval) {
            // Use compareAndSet to avoid race conditions
            if (lastDropTargetUpdateTime.compareAndSet(lastUpdate, now)) {
                updateDropTarget()
            }
        }
    }

    /**
     * Calculate the current absolute position of the drag.
     */
    fun getCurrentPosition(): Offset? {
        val start = dragStartPosition ?: return null
        return start + dragDelta
    }

    /**
     * Update the drop target based on current position.
     */
    private fun updateDropTarget() {
        val currentPosition = getCurrentPosition() ?: return
        val dragging = draggingTab ?: return

        // First, check if we're over any tab bar (for reorder or move to panel)
        for ((panelId, barBounds) in tabBarBounds) {
            if (barBounds.contains(currentPosition)) {
                // We're over a tab bar
                if (panelId == dragging.sourcePanelId) {
                    // Same panel - calculate reorder position
                    val reorderIndex = calculateReorderIndex(panelId, currentPosition)
                    dropTarget = TabDropTarget.Reorder(panelId, reorderIndex)
                } else {
                    // Different panel - move to that panel
                    dropTarget = TabDropTarget.ExistingPanel(panelId)
                }
                return
            }
        }

        // Check panel drop zones for split creation
        for ((panelId, zones) in panelDropZones) {
            when {
                zones.leftZone.contains(currentPosition) -> {
                    dropTarget = TabDropTarget.SplitPanel(panelId, SplitOrientation.VERTICAL)
                    return
                }
                zones.rightZone.contains(currentPosition) -> {
                    dropTarget = TabDropTarget.SplitPanel(panelId, SplitOrientation.VERTICAL)
                    return
                }
                zones.topZone.contains(currentPosition) -> {
                    dropTarget = TabDropTarget.SplitPanel(panelId, SplitOrientation.HORIZONTAL)
                    return
                }
                zones.bottomZone.contains(currentPosition) -> {
                    dropTarget = TabDropTarget.SplitPanel(panelId, SplitOrientation.HORIZONTAL)
                    return
                }
                zones.centerZone.contains(currentPosition) -> {
                    // Center means add to existing panel
                    if (panelId != dragging.sourcePanelId) {
                        dropTarget = TabDropTarget.ExistingPanel(panelId)
                        return
                    }
                }
            }
        }

        // No valid drop target
        dropTarget = null
    }

    /**
     * Calculate the index where a tab would be inserted during reorder.
     */
    private fun calculateReorderIndex(panelId: String, position: Offset): Int {
        val relevantTabs = tabBounds.entries
            .filter { (tabId, _) -> tabId.startsWith("$panelId:") }
            .sortedBy { it.value.left }

        if (relevantTabs.isEmpty()) return 0

        for ((index, entry) in relevantTabs.withIndex()) {
            val bounds = entry.value
            val midpoint = bounds.left + bounds.width / 2

            if (position.x < midpoint) {
                return index
            }
        }

        return relevantTabs.size
    }

    /**
     * End the drag and return the result, or null if cancelled.
     */
    fun endDrag(): TabDropResult? {
        val dragging = draggingTab
        val target = dropTarget

        // Reset state
        draggingTab = null
        dragStartPosition = null
        dragDelta = Offset.Zero
        dropTarget = null

        if (dragging == null) return null

        return when (target) {
            is TabDropTarget.Reorder -> {
                val toIndex = if (target.targetIndex > dragging.sourceIndex) {
                    target.targetIndex - 1
                } else {
                    target.targetIndex
                }
                if (toIndex != dragging.sourceIndex) {
                    TabDropResult.Reorder(
                        panelId = target.panelId,
                        fromIndex = dragging.sourceIndex,
                        toIndex = toIndex
                    )
                } else {
                    null
                }
            }
            is TabDropTarget.ExistingPanel -> {
                if (target.panelId != dragging.sourcePanelId) {
                    TabDropResult.MoveToPanel(
                        tabInfo = dragging.tabInfo,
                        sourcePanelId = dragging.sourcePanelId,
                        sourceIndex = dragging.sourceIndex,
                        targetPanelId = target.panelId
                    )
                } else {
                    null
                }
            }
            is TabDropTarget.SplitPanel -> {
                TabDropResult.CreateSplit(
                    tabInfo = dragging.tabInfo,
                    sourcePanelId = dragging.sourcePanelId,
                    sourceIndex = dragging.sourceIndex,
                    targetPanelId = target.panelId,
                    orientation = target.orientation
                )
            }
            null -> null
        }
    }

    /**
     * Cancel the drag without performing any action.
     */
    fun cancelDrag() {
        draggingTab = null
        dragStartPosition = null
        dragDelta = Offset.Zero
        dropTarget = null
    }

    /**
     * Register tab bounds for a specific tab.
     * The tabId should be in the format "panelId:tabId" for proper grouping.
     */
    fun registerTabBounds(compositeTabId: String, bounds: Rect) {
        tabBounds[compositeTabId] = bounds
    }

    /**
     * Unregister tab bounds when a tab is removed.
     */
    fun unregisterTabBounds(compositeTabId: String) {
        tabBounds.remove(compositeTabId)
    }

    /**
     * Register tab bar bounds for a panel.
     */
    fun registerTabBarBounds(panelId: String, bounds: Rect) {
        tabBarBounds[panelId] = bounds
    }

    /**
     * Register panel drop zones for split creation.
     */
    fun registerPanelDropZones(panelId: String, bounds: Rect) {
        panelDropZones[panelId] = PanelDropZones.fromBounds(bounds)
    }

    /**
     * Clear all registered bounds (e.g., when layout changes significantly).
     */
    fun clearBounds() {
        tabBounds.clear()
        tabBarBounds.clear()
        panelDropZones.clear()
    }

    /**
     * Unregister all bounds for a specific panel.
     * Should be called when a panel is destroyed to prevent memory leaks.
     */
    fun unregisterPanel(panelId: String) {
        // Remove tab bar bounds for this panel
        tabBarBounds.remove(panelId)

        // Remove panel drop zones
        panelDropZones.remove(panelId)

        // Remove all tab bounds for this panel (format: "panelId:tabId")
        val tabsToRemove = tabBounds.keys.filter { it.startsWith("$panelId:") }
        tabsToRemove.forEach { tabBounds.remove(it) }
    }
}
