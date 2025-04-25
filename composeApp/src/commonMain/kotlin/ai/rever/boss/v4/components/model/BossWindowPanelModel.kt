package ai.rever.boss.v4.components.model

import ai.rever.boss.v4.components.model.Panel.Companion.bottom
import ai.rever.boss.v4.components.model.Panel.Companion.left
import ai.rever.boss.v4.components.model.Panel.Companion.right
import ai.rever.boss.v4.components.model.Panel.Companion.top
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.to

data class PanelData(val title: String,
                     val visibility: Boolean)

// Represents a single draggable item in the sidebar
data class SidebarItem(
    val id: String, // Unique identifier for the item
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit // Action to perform on click (when not dragging)
)

// Holds the state and logic for the draggable sidebar system
@Stable
class BossWindowPanelModel {
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
    private val itemsBySlot = mutableStateMapOf<Panel, List<SidebarItem>>()


    private val size = mutableStateMapOf<Panel, Dp> (
        left to 250.dp,
        right to 250.dp,
        bottom to 200.dp
    )

    private val panelsData = mutableStateMapOf<Panel, PanelData>(
        left.top to PanelData("Project", true),
        left.bottom to PanelData("Editor", false),
        right.top to PanelData("Structure", true),
        right.bottom to PanelData("Diagram", false),
        bottom to PanelData("Terminal", true)
    )


    init {
        // Initialize with default items in their respective slots
        itemsBySlot[left.top.top] = listOf(
            SidebarItem("folder", Icons.Outlined.Folder, "Folder") {
                toggleVisibility(left.top)
            },
            SidebarItem("phone", Icons.Outlined.PhoneIphone, "Phone") {
                toggleVisibility(left.top)
            },
            SidebarItem("shapes", Icons.Outlined.FormatShapes, "Shapes") {
                toggleVisibility(left.top)
            }
        )
        itemsBySlot[left.top.bottom] = listOf(
            SidebarItem("build", Icons.Outlined.Build, "Build") {
                toggleVisibility(left.bottom)
            },
            SidebarItem("more", Icons.Outlined.MoreHoriz, "More") {
                toggleVisibility(left.bottom)
            }
        )
        itemsBySlot[left.bottom] = listOf(
            SidebarItem("run", Icons.Outlined.RunCircle, "Run") {
                toggleVisibility(bottom)
            },
            SidebarItem("code", Icons.Outlined.Code, "Code") {
                toggleVisibility(bottom)
            }
        )
        itemsBySlot[right.top.top] = listOf(
            SidebarItem("attach", Icons.Outlined.AttachFile, "Attach") {
                toggleVisibility(right.top)
            },
            SidebarItem("audio", Icons.Outlined.Audiotrack, "Audio") {
                toggleVisibility(right.top)
            },
            SidebarItem("video", Icons.Outlined.VideoFile, "Video") {
                toggleVisibility(right.top)
            }
        )
        itemsBySlot[right.top.bottom] = listOf(
            SidebarItem("replay", Icons.Outlined.Replay, "Replay") {
                toggleVisibility(right.bottom)
            },
            SidebarItem("cast", Icons.Outlined.Cast, "Cast") {
                toggleVisibility(right.bottom)
            },
            SidebarItem("anchor", Icons.Outlined.Anchor, "Anchor") {
                toggleVisibility(right.bottom)
            },
            SidebarItem("android", Icons.Outlined.Android, "Android") {
                toggleVisibility(right.bottom)
            }
        )
    }

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

    fun getSize(panel: Panel) = size[panel] ?: 0.dp

    fun setSize(panel: Panel, size: Dp) {
        this.size[panel] = size
    }

    fun isVisible(panel: Panel): Boolean {
        if (panel == right) {
            return isVisible(right.top) || isVisible(right.bottom)
        } else if (panel == left) {
            return isVisible(left.top) || isVisible(left.bottom)
        }
        return panelsData[panel]?.visibility == true
    }

    private fun toggleVisibility(panel: Panel) {
        setPanelVisible(panel, panelsData[panel]?.visibility != true)
    }

    fun setPanelVisible(panel: Panel, isVisible: Boolean) {
        panelsData[panel]?.let {
            panelsData[panel] = it.copy(visibility = isVisible)
        }
    }

    fun getPanelTitle(panel: Panel) = panelsData[panel]?.title ?: ""
}

// Composable function to remember the DraggableSidebarModel instance
@Composable
fun rememberBossWindowPanelModel(): BossWindowPanelModel {
    return remember { BossWindowPanelModel() }
} 