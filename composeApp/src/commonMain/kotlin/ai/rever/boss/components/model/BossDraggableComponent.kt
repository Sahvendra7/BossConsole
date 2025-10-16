package ai.rever.boss.components.model

import ai.rever.boss.components.model.Panel.Companion.bottom
import ai.rever.boss.components.model.Panel.Companion.left
import ai.rever.boss.components.model.Panel.Companion.right
import ai.rever.boss.components.model.Panel.Companion.top
import ai.rever.boss.components.registery.PanelId
import ai.rever.boss.components.registery.PanelRegistry
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.vector.ImageVector
import kotlin.math.max

data class PanelData(
    val sidebarItem: SidebarItem? = null,
    val visibility: Boolean,
)

// Represents a single draggable item in the sidebar
data class SidebarItem(
    val pluginContentId: PanelId, // Unique identifier for the item
    val icon: ImageVector,
    val label: String,
    val onClick: (() -> Unit)? = null,
) {
    val id: String get() = pluginContentId.panelId
}


// Holds the state and logic for the draggable sidebar system
@Stable
class BossDraggableComponent(val panelRegistry: PanelRegistry) {
    // The item currently being dragged, and its original slot
    var draggingItem by mutableStateOf<Pair<SidebarItem, Panel>?>(null)
        private set

    // Absolute position where the drag started
    var dragStartPosition by mutableStateOf<Offset?>(null)
        private set

    // Accumulated delta since the drag started
    var dragDelta by mutableStateOf(Offset.Zero)
        private set

    // The slot currently being hovered over by the dragged item, or null
    var dropTargetSlot by mutableStateOf<Panel?>(null)
        private set

    // Internal state to track drop target bounds for hover calculation
    internal val slotBounds = mutableMapOf<Panel, Rect>()

    // A map holding the list of items for each slot, backed by mutable state
    private val itemsBySlot by lazy {
        panelRegistry
            .getDefaultSidebarMap()
            .map { it.key to it.value }
            .toMutableStateMap()
    }


    private val panelsData by lazy {
        mutableStateMapOf(
            bottom to PanelData(visibility =  false),
            left.top to PanelData(visibility =  false),
            right.top to PanelData(visibility =  false),
            left.bottom to PanelData(visibility =  false),
            right.bottom to PanelData(visibility =  false),
        )
    }

    fun update() {
        panelRegistry.getDefaultSidebarMap().forEach {
            itemsBySlot[it.key] = it.value

            panelsData[it.key] = panelsData[it.key]
                ?.copy(sidebarItem = it.value.firstOrNull())
                ?: PanelData(visibility = false)
        }
    }

    val onClick: SidebarItem.() -> Unit = {
        when(slot) {
            left.bottom -> toggleVisibility(bottom)
            left.top.top -> toggleVisibility(left.top)
            right.top.top -> toggleVisibility(right.top)
            left.top.bottom -> toggleVisibility(left.bottom)
            right.top.bottom -> toggleVisibility(right.bottom)
        }
    }


    fun getPanelContentId(panel: Panel): PanelId? {
        return panelsData[panel]?.sidebarItem?.pluginContentId
    }

    val SidebarItem.slot: Panel
        get() = itemsBySlot
            .filter { it.value.find { sideItem -> id == sideItem.id } != null }
            .keys.first()


    // Get items for a specific slot, returning an empty list if the slot is unknown
    fun getItemsForSlot(slot: Panel): List<SidebarItem> {
        return itemsBySlot[slot] ?: emptyList()
    }

    // Called when dragging starts
    fun startDragging(item: SidebarItem, sourceSlot: Panel, startPosition: Offset) {
        if (draggingItem != null) return
        draggingItem = item to sourceSlot
        dragStartPosition = startPosition
        dragDelta = Offset.Zero
        updateHoverTarget()
    }

    // Called repeatedly during a drag gesture to update the delta
    fun updateDragDelta(delta: Offset) {
        if (draggingItem == null || dragStartPosition == null) return
        dragDelta += delta
        updateHoverTarget()
    }

    // Recalculates the dropTargetSlot based on the current calculated absolute position
    private fun updateHoverTarget() {
        val start = dragStartPosition ?: return
        val currentPosition = start + dragDelta // Calculate current absolute position

        var newTarget: Panel? = null
        for ((slot, bounds) in slotBounds) {
            if (bounds.contains(currentPosition)) {
                newTarget = slot
                break
            }
        }
        if (dropTargetSlot != newTarget) {
            dropTargetSlot = newTarget
        }
    }

    // Called when dragging ends
    fun stopDragging() {
        val currentDraggingItem = draggingItem
        val currentDropTarget = dropTargetSlot

        val finalDropPosition = if (dragStartPosition != null) dragStartPosition!! + dragDelta else null

        // Reset dragging state immediately
        draggingItem = null
        dragStartPosition = null // Reset start position
        dragDelta = Offset.Zero // Reset delta
        dropTargetSlot = null

        if (currentDraggingItem != null) {
            val (item, sourceSlot) = currentDraggingItem

            val sourceList = itemsBySlot[sourceSlot]?.toMutableList() ?: mutableListOf()
            val removed = sourceList.removeAll { it.id == item.id }
            if (removed) {
                itemsBySlot[sourceSlot] = sourceList // Update source list only if removed
            }

            if (currentDropTarget != null) {
                // Move item to the target slot if it's different from the source
                val targetSlotBounds = slotBounds[currentDropTarget]
                val currentTargetItems = itemsBySlot[currentDropTarget]?.toList() ?: emptyList() // Use current items in target
                var targetIndex = currentTargetItems.size // Default to end
                // Add the item (simple add to end for now)
                if (targetSlotBounds != null && finalDropPosition != null && currentTargetItems.isNotEmpty()) {
                    // Estimate item height based on slot bounds and item count
                    // Add small epsilon to height to avoid division by zero if bounds are tiny
                    val totalSlotHeight = max(1f, targetSlotBounds.height) // Ensure positive height
                    val itemHeightEstimate = totalSlotHeight / currentTargetItems.size

                    for (i in currentTargetItems.indices) {
                        // Calculate the Y-coordinate of the midpoint of the i-th item's estimated area
                        val itemMidpointY = targetSlotBounds.top + (i * itemHeightEstimate) + (itemHeightEstimate / 2f)

                        // If the drop position is above this item's midpoint, insert before it
                        if (finalDropPosition.y < itemMidpointY) {
                            targetIndex = i
                            break // Found the insertion point
                        }
                    }
                    // If loop completes, targetIndex remains currentTargetItems.size (append)
                } else if (currentTargetItems.isEmpty()) {
                    // If the target slot is empty, index is 0
                    targetIndex = 0
                }

                // Add item to the target slot at the calculated index
                val targetList = itemsBySlot[currentDropTarget]?.toMutableList() ?: mutableListOf()
                if (!targetList.any { it.id == item.id }) {
                    // Ensure index is within bounds before adding
                    val safeIndex = targetIndex.coerceIn(0, targetList.size)
                    targetList.add(safeIndex, item)
                }
                itemsBySlot[currentDropTarget] = targetList
            } else {
                // No valid target OR dropped back onto the source slot, return item to its original slot
                val updatedSourceList = itemsBySlot[sourceSlot]?.toMutableList() ?: mutableListOf()
                // Add the item back (simple add to end for now)
                 if (!updatedSourceList.any { it.id == item.id }) { // Avoid duplicates
                     updatedSourceList.add(item)
                 }
                itemsBySlot[sourceSlot] = updatedSourceList
            }
        }
    }

    fun isVisible(panel: Panel): Boolean {
        if (panel == right) {
            return isVisible(right.top) || isVisible(right.bottom)
        } else if (panel == left) {
            return isVisible(left.top) || isVisible(left.bottom)
        }
        return panelsData[panel]?.visibility == true
    }

    private fun SidebarItem.toggleVisibility(panel: Panel) {
        if (panelsData[panel]?.sidebarItem?.id == id) {
            setPanelVisible(panel, panelsData[panel]?.visibility != true)
        } else {
            setPanelVisible(panel, true)
        }
        panelsData[panel]?.let {
            panelsData[panel] = it.copy(sidebarItem = this)
        }
    }

    fun setPanelVisible(panel: Panel, isVisible: Boolean) {
        panelsData[panel]?.let {
            panelsData[panel] = it.copy(visibility = isVisible)
        }
    }

    fun isSelected(item: SidebarItem): Boolean {
        return panelsData.values.any { (it.sidebarItem?.id == item.id) && it.visibility }
    }
}
