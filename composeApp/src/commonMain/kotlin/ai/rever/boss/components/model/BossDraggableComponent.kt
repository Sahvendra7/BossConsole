package ai.rever.boss.components.model

import ai.rever.boss.plugin.api.Panel
import ai.rever.boss.plugin.api.Panel.Companion.bottom
import ai.rever.boss.plugin.api.Panel.Companion.left
import ai.rever.boss.plugin.api.Panel.Companion.right
import ai.rever.boss.plugin.api.Panel.Companion.top
import ai.rever.boss.plugin.api.PanelId
import ai.rever.boss.plugin.api.PanelRegistry
import ai.rever.boss.plugin.api.SidebarItem
import ai.rever.boss.utils.logging.BossLogger
import ai.rever.boss.utils.logging.LogCategory
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlinx.coroutines.delay
import kotlin.math.max

data class PanelData(
    val sidebarItem: SidebarItem? = null,
    val visibility: Boolean,
)


// Holds the state and logic for the draggable sidebar system
@Stable
class BossDraggableComponent(val panelRegistry: PanelRegistry) {
    private val logger = BossLogger.forComponent("BossDraggableComponent")

    companion object {
        /**
         * Delay in milliseconds for two-phase panel transitions.
         * This gives Compose enough time to process visibility changes and dispose
         * the old BrowserView's DisposableEffect before the new panel opens.
         * 100ms is sufficient for typical recomposition cycles.
         */
        private const val PANEL_DISPOSAL_DELAY_MS = 100L
    }

    init {
        // Register listener to update sidebar when panels are dynamically added/removed
        panelRegistry.addChangeListener {
            logger.debug(LogCategory.UI, "Panel registry changed, updating sidebar")
            update()
        }
    }

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

    // Pending panel open for two-phase transitions (used for JxBrowser-based plugins)
    // This ensures the old BrowserView is disposed before the new one is created
    var pendingPanelOpen by mutableStateOf<Pair<Panel, SidebarItem>?>(null)
        private set

    fun update() {
        // Only update itemsBySlot - do NOT update panelsData here
        // panelsData should only be modified by toggleVisibility when panels are explicitly opened
        // Note: getDefaultSidebarMap() returns SLOT keys (like left.bottom, left.top.bottom)
        // which overlap with PANEL keys in panelsData, causing incorrect overwrites
        panelRegistry.getDefaultSidebarMap().forEach {
            itemsBySlot[it.key] = it.value
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

    // Maps sidebar slots to their corresponding panel display areas
    private fun slotToPanel(slot: Panel): Panel? {
        return when(slot) {
            left.bottom -> bottom
            left.top.top -> left.top
            right.top.top -> right.top
            left.top.bottom -> left.bottom
            right.top.bottom -> right.bottom
            else -> null
        }
    }

    fun getPanelContentId(panel: Panel): PanelId? {
        return panelsData[panel]?.sidebarItem?.pluginContentId
    }

    val SidebarItem.slot: Panel
        get() = itemsBySlot
            .filter { it.value.find { sideItem -> id == sideItem.id } != null }
            .keys.first()


    /**
     * Items in [slot], with any panel id in [hidden] filtered out.
     *
     * Callers must supply [hidden] from an observed source
     * (typically a `collectAsState` on
     * [SidebarVisibilitySettingsManager.currentSettings]) so the
     * resulting list invalidates the composition when the hide-set
     * changes. Reading the StateFlow's `.value` inside this method would
     * not register a snapshot observation, leaving stale lists in the
     * sidebar until something else triggered recomposition.
     *
     * No default for [hidden]: omitting it would silently produce an
     * unfiltered list — identical to calling [getItemsForSlotUnfiltered]
     * — and the caller would only notice the bug when hide-toggles
     * stopped taking effect. If you genuinely want the unfiltered list,
     * call [getItemsForSlotUnfiltered] explicitly.
     */
    fun getItemsForSlot(slot: Panel, hidden: Set<String>): List<SidebarItem> {
        val items = itemsBySlot[slot] ?: return emptyList()
        if (hidden.isEmpty()) return items
        return items.filter { it.id !in hidden }
    }

    /**
     * Unfiltered items for [slot]. Like [getItemsForSlot] but ignores
     * the user's hidden-set so the customize menu can list panels the
     * user has hidden (and let them un-hide).
     */
    fun getItemsForSlotUnfiltered(slot: Panel): List<SidebarItem> =
        itemsBySlot[slot].orEmpty()

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

    /**
     * Cancel an in-flight drag without mutating slot membership. Used by
     * synthetic / non-panel sidebar items (e.g. the sidebar customize
     * three-dot menu) whose icons participate in the drag overlay only
     * for visual consistency — letting [stopDragging] run would inject
     * the synthetic id into [itemsBySlot] when a drop target was set.
     */
    fun cancelDragSnapBack() {
        draggingItem = null
        dragStartPosition = null
        dragDelta = Offset.Zero
        dropTargetSlot = null
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

                // Transfer panel state when dragging an open plugin to a different slot
                if (currentDropTarget != sourceSlot) {
                    val oldPanel = slotToPanel(sourceSlot)
                    val newPanel = slotToPanel(currentDropTarget)

                    if (oldPanel != null && newPanel != null) {
                        // Check if this item was open in its previous panel
                        val wasOpen = panelsData[oldPanel]?.let { data ->
                            data.sidebarItem?.id == item.id && data.visibility
                        } ?: false

                        if (wasOpen) {
                            // Close the old panel first
                            panelsData[oldPanel]?.let {
                                panelsData[oldPanel] = it.copy(
                                    visibility = false,
                                    sidebarItem = null
                                )
                            }

                            // Use two-phase transition for panel moves
                            // This is critical for JxBrowser-based plugins where BrowserViewState
                            // can only be attached to one Compose view at a time.
                            // By deferring the new panel open, we ensure the old BrowserView
                            // is fully disposed before the new one tries to use the same BrowserViewState.
                            pendingPanelOpen = newPanel to item
                        }
                    }
                }
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

    /**
     * If any panel area is currently displaying the [SidebarItem] with the
     * given id, close it. Called by the customize menu when a panel's icon
     * is hidden, so the user doesn't end up with an open panel content area
     * but no icon left to dismiss it.
     */
    fun closePanelForItem(itemId: String) {
        panelsData.keys.toList().forEach { panel ->
            if (panelsData[panel]?.sidebarItem?.id == itemId) {
                setPanelVisible(panel, false)
            }
        }
    }

    /**
     * Programmatically activate a plugin by its ID.
     * Replicates the behavior of clicking a plugin icon in the sidebar.
     * 
     * @param pluginId The panel ID of the plugin to activate (e.g., "codebase", "terminal")
     */
    fun activatePlugin(pluginId: String) {
        // Find the SidebarItem with matching pluginContentId
        val slotEntry = itemsBySlot.entries.find { (_, items) ->
            items.any { it.pluginContentId.panelId == pluginId }
        }
        
        slotEntry?.let { (slot, items) ->
            val sidebarItem = items.find { it.pluginContentId.panelId == pluginId }
            
            sidebarItem?.let { item ->
                // Determine target panel based on the slot (same logic as onClick)
                val targetPanel = when(slot) {
                    left.bottom -> bottom
                    left.top.top -> left.top
                    right.top.top -> right.top
                    left.top.bottom -> left.bottom
                    right.top.bottom -> right.bottom
                    else -> null
                }
                
                targetPanel?.let { panel ->
                    // Apply the same logic as toggleVisibility
                    if (panelsData[panel]?.sidebarItem?.id == item.id) {
                        // Same plugin - toggle visibility
                        setPanelVisible(panel, panelsData[panel]?.visibility != true)
                    } else {
                        // Different plugin - show panel
                        setPanelVisible(panel, true)
                    }
                    // Update active plugin for this panel
                    panelsData[panel]?.let {
                        panelsData[panel] = it.copy(sidebarItem = item)
                    }
                }
            }
        }
    }

    fun isSelected(item: SidebarItem): Boolean {
        return panelsData.values.any { (it.sidebarItem?.id == item.id) && it.visibility }
    }

    /**
     * Composable effect that processes pending panel opens with a delay.
     * This enables two-phase transitions for JxBrowser-based plugins where
     * the BrowserViewState can only be attached to one Compose view at a time.
     *
     * Call this from the main UI composable to ensure pending opens are processed.
     *
     * Note: If a new drag operation starts before the delay completes, the previous
     * pending open is cancelled and only the latest one will execute. This is intentional -
     * the user's most recent action takes priority.
     */
    @Composable
    fun ProcessPendingPanelOpen() {
        val pending = pendingPanelOpen
        LaunchedEffect(pending) {
            if (pending != null) {
                val (panel, item) = pending
                // Wait for the old panel to be disposed
                delay(PANEL_DISPOSAL_DELAY_MS)

                // Open the new panel
                panelsData[panel]?.let {
                    panelsData[panel] = it.copy(
                        visibility = true,
                        sidebarItem = item
                    )
                }

                // Clear the pending state
                pendingPanelOpen = null
            }
        }
    }
}
