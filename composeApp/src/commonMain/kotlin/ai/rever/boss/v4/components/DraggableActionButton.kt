package ai.rever.boss.v4.components

import BossDarkTextPrimary
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
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
    sidebarModel: DraggableSidebarModel,
    modifier: Modifier = Modifier
) {
    val currentItem by rememberUpdatedState(item)
    val currentSlot by rememberUpdatedState(slot)
    val isBeingDragged = sidebarModel.draggingItem?.first?.id == item.id

    var componentPositionInWindow by remember { mutableStateOf<Offset?>(null) } // Nullable
    var pendingDragStartOffset by remember { mutableStateOf<Offset?>(null) }

    // Log recomposition state
    println("--- DraggableActionButton Recomposing: Item=${item.id}, Slot=${slot}, isDragged=${isBeingDragged}, Pos=${componentPositionInWindow}, PendingOffset=${pendingDragStartOffset}")

    LaunchedEffect(componentPositionInWindow, pendingDragStartOffset) {
        val startOffset = pendingDragStartOffset
        val currentPos = componentPositionInWindow

        println(">>> LaunchedEffect Check: Item=${item.id}, Pos=${currentPos}, PendingOffset=${startOffset}")

        if (startOffset != null && currentPos != null) {
            println(">>> LaunchedEffect Firing DRAG START: Item=${item.id}, Pos=${currentPos}, Offset=${startOffset}")
            val startPosition = currentPos + startOffset
            sidebarModel.startDragging(currentItem, currentSlot, startPosition)
            // Reset pending offset AFTER starting the drag
            pendingDragStartOffset = null
        } else {
            if (startOffset != null && currentPos == null){
                println(">>> LaunchedEffect Condition NOT MET (Position Null): Item=${item.id}, PendingOffset=${startOffset}")
            } else if (startOffset == null && currentPos != null) {
                // This case is normal on layout changes without pending drag
                // println(">>> LaunchedEffect Condition NOT MET (No Pending Drag): Item=${item.id}, Pos=${currentPos}")
            }
        }
    }

    IconButton(
        onClick = {
            println("--- IconButton Click: Item=${item.id}")
            if (sidebarModel.draggingItem == null) item.onClick()
        },
        modifier = modifier
            .onGloballyPositioned { layoutCoordinates ->
                val newPos = layoutCoordinates.positionInWindow()
                println("+++ onGloballyPositioned Update: Item=${item.id}, NewPos=${newPos}")
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
                        println("===> onDragStart Received: Item=${item.id}, TouchOffset=${touchOffset}")
                        // Just record the intention to drag
                        pendingDragStartOffset = touchOffset
                    },
                    onDragEnd = {
                        println("===> onDragEnd: Item=${item.id}, PendingOffsetBeforeReset=${pendingDragStartOffset}")
                        if (pendingDragStartOffset != null) {
                            pendingDragStartOffset = null
                        }
                        if (sidebarModel.draggingItem != null) {
                            println("===> onDragEnd Stopping Model Drag: Item=${item.id}")
                            sidebarModel.stopDragging()
                        }
                    },
                    onDragCancel = {
                        println("===> onDragCancel: Item=${item.id}, PendingOffsetBeforeReset=${pendingDragStartOffset}")
                        pendingDragStartOffset = null
                        if (sidebarModel.draggingItem != null) {
                            println("===> onDragCancel Stopping Model Drag: Item=${item.id}")
                            sidebarModel.stopDragging()
                        }
                    },
                    onDrag = { change: PointerInputChange, dragAmount: Offset ->
                        // Check model state directly to see if drag has officially started
                        if (sidebarModel.draggingItem?.first?.id == item.id) {
                            // println("===> onDrag Update: Item=${item.id}, Delta=${dragAmount}") // Can be noisy
                            change.consume()
                            sidebarModel.updateDragDelta(dragAmount)
                        } else {
                            // This might happen if the drag hasn't started yet due to timing
                            // println("===> onDrag Update IGNORED (Not dragging this item / drag not started): Item=${item.id}")
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