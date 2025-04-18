package ai.rever.boss.v4.components.buttons

import BossDarkTextPrimary
import ai.rever.boss.v4.components.model.BossWindowPanelModel
import ai.rever.boss.v4.components.model.SidebarItem
import ai.rever.boss.v4.components.model.SidebarSlot
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.size
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.dp

@Composable
fun DraggableActionButton(
    item: SidebarItem,
    slot: SidebarSlot,
    sidebarModel: BossWindowPanelModel,
    modifier: Modifier = Modifier
) {
    val currentItem by rememberUpdatedState(item)
    val currentSlot by rememberUpdatedState(slot)
    val isBeingDragged = sidebarModel.draggingItem?.first?.id == item.id

    var componentPositionInWindow by remember { mutableStateOf<Offset?>(null) }
    var pendingDragStartOffset by remember { mutableStateOf<Offset?>(null) }

    // Log recomposition state

    LaunchedEffect(componentPositionInWindow, pendingDragStartOffset) {
        val startOffset = pendingDragStartOffset
        val currentPos = componentPositionInWindow


        if (startOffset != null && currentPos != null) {
            val startPosition = currentPos + startOffset
            sidebarModel.startDragging(currentItem, currentSlot, startPosition)
            // Reset pending offset AFTER starting the drag
            pendingDragStartOffset = null
        }
    }

    IconButton(
        onClick = {
            if (sidebarModel.draggingItem == null) item.onClick()
        },
        modifier = modifier
            .onGloballyPositioned { layoutCoordinates ->
                val newPos = layoutCoordinates.positionInWindow()
                // Only update state if the position actually changed to avoid redundant recompositions/effect triggers
                if (componentPositionInWindow != newPos) {
                    componentPositionInWindow = newPos
                }
            }
            .size(40.dp)
            .alpha(if (isBeingDragged) 0f else 1f)
            .pointerInput(Unit) { // Keep Unit key
                detectDragGesturesAfterLongPress(
                    onDragStart = { touchOffset ->
                        // Just record the intention to drag
                        pendingDragStartOffset = touchOffset
                    },
                    onDragEnd = {
                        if (pendingDragStartOffset != null) {
                            pendingDragStartOffset = null
                        }
                        if (sidebarModel.draggingItem != null) {
                            sidebarModel.stopDragging()
                        }
                    },
                    onDragCancel = {
                        pendingDragStartOffset = null
                        if (sidebarModel.draggingItem != null) {
                            sidebarModel.stopDragging()
                        }
                    },
                    onDrag = { change: PointerInputChange, dragAmount: Offset ->
                        // Check model state directly to see if drag has officially started
                        if (sidebarModel.draggingItem?.first?.id == item.id) {
                            change.consume()
                            sidebarModel.updateDragDelta(dragAmount)
                        }
                    }
                )
            }
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = item.label,
            modifier = Modifier.size(22.dp),
            tint = BossDarkTextPrimary
        )
    }
}