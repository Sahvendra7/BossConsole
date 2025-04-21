package ai.rever.boss.v4.components.misc

import ai.rever.boss.v4.components.buttons.DraggableActionButton
import ai.rever.boss.v4.components.model.BossWindowPanelModel
import ai.rever.boss.v4.components.model.Panel
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp

// A container for a specific sidebar slot, handling hover feedback
@Composable
fun SidebarSlotContainer(
    slot: Panel,
    sidebarModel: BossWindowPanelModel,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    // Determine hover state based on the model's dropTargetSlot
    val isHovered = sidebarModel.dropTargetSlot == slot && sidebarModel.draggingItem != null
    val borderColor = if (isHovered) Color.Black else Color.Transparent
    // Use a semi-transparent black background for hover indication ("black slot" on hover)
    val backgroundColor = if (isHovered) Color.Black.copy(alpha = 0.3f) else Color.Transparent

    // Use rememberUpdatedState for slot to ensure DisposableEffect cleans up correctly if slot changes
    val currentSlot by rememberUpdatedState(slot)

    DisposableEffect(currentSlot, sidebarModel) {
        onDispose {
            // Remove bounds when the composable leaves the composition
            sidebarModel.slotBounds.remove(currentSlot)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .border(width = 1.dp, color = borderColor)
            .padding(vertical = 4.dp) // Padding inside the slot
            .onGloballyPositioned { coordinates ->
                // Register this slot's bounds (in window coordinates) with the model
                sidebarModel.slotBounds[currentSlot] = coordinates.boundsInWindow()
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        content()
    }
}

// Renders a specific section of the sidebar using the DraggableSidebarModel
@Composable
fun DraggableSidebarSection(
    slot: Panel,
    sidebarModel: BossWindowPanelModel,
    modifier: Modifier = Modifier
) {
    SidebarSlotContainer(
        slot = slot,
        sidebarModel = sidebarModel,
        modifier = modifier
    ) {
        val items = sidebarModel.getItemsForSlot(slot)
        items.forEach { item ->

            key (item.id) {
                DraggableActionButton(
                    item = item,
                    slot = slot,
                    sidebarModel = sidebarModel
                )
            }
        }
        // Add a minimum height to the slot even when empty to ensure it's a valid drop target
        if (items.isEmpty()) {
            Spacer(Modifier.height(40.dp)) // Height approx one button
        }
    }
}