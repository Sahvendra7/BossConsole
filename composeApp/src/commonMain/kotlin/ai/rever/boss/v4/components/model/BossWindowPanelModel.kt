package ai.rever.boss.v4.components.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import kotlin.math.max

enum class Panel {
    LEFT, RIGHT, BOTTOM
}

// Defines the possible drop locations for sidebar items
enum class SidebarSlot {
    LEFT_TOP_TOP,
    LEFT_TOP_BOTTOM,
    LEFT_BOTTOM,
    RIGHT_TOP_TOP,
    RIGHT_TOP_BOTTOM
}

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
    var draggingItem by mutableStateOf<Pair<SidebarItem, SidebarSlot>?>(null)
        private set

    // Absolute position where the drag started
    var dragStartPosition by mutableStateOf<Offset?>(null)
        private set

    // Accumulated delta since the drag started
    var dragDelta by mutableStateOf(Offset.Zero)
        private set

    // The slot currently being hovered over by the dragged item, or null
    var dropTargetSlot by mutableStateOf<SidebarSlot?>(null)
        private set

    // Internal state to track drop target bounds for hover calculation
    internal val slotBounds = mutableMapOf<SidebarSlot, Rect>()

    // A map holding the list of items for each slot, backed by mutable state
    private val itemsBySlot = mutableStateMapOf<SidebarSlot, List<SidebarItem>>()


    var leftPanelWidth by mutableStateOf(250.dp)
    var rightPanelWidth by mutableStateOf(250.dp)
    var bottomPanelHeight by mutableStateOf(200.dp)

    var isLeftPanelVisible by mutableStateOf(true)
    var isRightPanelVisible by mutableStateOf(true)
    var isBottomPanelVisible by mutableStateOf(true)

    val title = mapOf(
        Panel.LEFT to "Project",
        Panel.RIGHT to "Structure",
        Panel.BOTTOM to "Terminal"
    )

    init {
        // Initialize with default items in their respective slots
        itemsBySlot[SidebarSlot.LEFT_TOP_TOP] = listOf(
            SidebarItem("folder", Icons.Outlined.Folder, "Folder") {
                isLeftPanelVisible = !isLeftPanelVisible
            },
            SidebarItem("phone", Icons.Outlined.PhoneIphone, "Phone") {
                isLeftPanelVisible = !isLeftPanelVisible
            },
            SidebarItem("shapes", Icons.Outlined.FormatShapes, "Shapes") {
                isLeftPanelVisible = !isLeftPanelVisible
            }
        )
        itemsBySlot[SidebarSlot.LEFT_TOP_BOTTOM] = listOf(
            SidebarItem("build", Icons.Outlined.Build, "Build") {
                isLeftPanelVisible = !isLeftPanelVisible
            },
            SidebarItem("more", Icons.Outlined.MoreHoriz, "More") {
                isLeftPanelVisible = !isLeftPanelVisible
            }
        )
        itemsBySlot[SidebarSlot.LEFT_BOTTOM] = listOf(
            SidebarItem("run", Icons.Outlined.RunCircle, "Run") {
                isBottomPanelVisible = !isBottomPanelVisible
            },
            SidebarItem("code", Icons.Outlined.Code, "Code") {
                isBottomPanelVisible = !isBottomPanelVisible
            }
        )
        itemsBySlot[SidebarSlot.RIGHT_TOP_TOP] = listOf(
            SidebarItem("attach", Icons.Outlined.AttachFile, "Attach") {
                isRightPanelVisible = !isRightPanelVisible
            },
            SidebarItem("audio", Icons.Outlined.Audiotrack, "Audio") {
                isRightPanelVisible = !isRightPanelVisible
            },
            SidebarItem("video", Icons.Outlined.VideoFile, "Video") {
                isRightPanelVisible = !isRightPanelVisible
            }
        )
        itemsBySlot[SidebarSlot.RIGHT_TOP_BOTTOM] = listOf(
            SidebarItem("replay", Icons.Outlined.Replay, "Replay") {
                isRightPanelVisible = !isRightPanelVisible
            },
            SidebarItem("cast", Icons.Outlined.Cast, "Cast") {
                isRightPanelVisible = !isRightPanelVisible
            },
            SidebarItem("anchor", Icons.Outlined.Anchor, "Anchor") {
                isRightPanelVisible = !isRightPanelVisible
            },
            SidebarItem("android", Icons.Outlined.Android, "Android") {
                isRightPanelVisible = !isRightPanelVisible
            }
        )
    }

    // Get items for a specific slot, returning an empty list if the slot is unknown
    fun getItemsForSlot(slot: SidebarSlot): List<SidebarItem> {
        return itemsBySlot[slot] ?: emptyList()
    }

    // Called when dragging starts
    fun startDragging(item: SidebarItem, sourceSlot: SidebarSlot, startPosition: Offset) {
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

        var newTarget: SidebarSlot? = null
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
}

// Composable function to remember the DraggableSidebarModel instance
@Composable
fun rememberBossWindowPanelModel(): BossWindowPanelModel {
    return remember { BossWindowPanelModel() }
} 