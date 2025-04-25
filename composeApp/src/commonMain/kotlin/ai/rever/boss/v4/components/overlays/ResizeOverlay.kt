package ai.rever.boss.v4.components.overlays

import ai.rever.boss.platform.CursorUtil.cursorForHorizontalResize
import ai.rever.boss.platform.CursorUtil.cursorForVerticalResize
import ai.rever.boss.v4.components.model.BossWindowPanelModel
import ai.rever.boss.v4.components.model.Panel
import ai.rever.boss.v4.components.model.Panel.Companion.bottom
import ai.rever.boss.v4.components.model.Panel.Companion.left
import ai.rever.boss.v4.components.model.Panel.Companion.right
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

@Composable
fun BoxScope.ResizeOverlay(windowPanelModel: BossWindowPanelModel) {

    // Transparent overlays for resizing - positioned in fixed locations
    ResizeHandle(windowPanelModel, left)
    ResizeHandle(windowPanelModel, right)
    ResizeHandle(windowPanelModel, bottom)
}



@Composable
private fun BoxScope.ResizeHandle(windowPanelModel: BossWindowPanelModel, panel: Panel) {

    // Min and max constraints for panel sizes
    val minPanelWidth = 150.dp
    val maxPanelWidth = 500.dp
    val minPanelHeight = 100.dp
    val maxPanelHeight = 500.dp

    if (!windowPanelModel.isVisible(panel)) {
        return
    }

    fun Modifier.offset() = offset {
        val x = when (panel) {
            left -> windowPanelModel.getSize(left) - 8.dp
            right -> -windowPanelModel.getSize(right) - 1.dp + 8.dp
            else -> { 0.dp }
        }.roundToPx()
        val y = when (panel) {
            left, right -> 0.dp
            bottom -> -windowPanelModel.getSize(bottom) - 1.dp + 8.dp
            else -> { 0.dp }
        }.roundToPx()
        IntOffset(x, y)
    }

    fun Modifier.resizable() = run {
        when (panel) {
            left,
            right -> {
                width(16.dp)
                    .fillMaxHeight(
                        if (windowPanelModel.isVisible(bottom))
                            1f - (windowPanelModel.getSize(bottom) / 1000.dp)
                        else 1f)
                    .cursorForHorizontalResize()
            }
            else -> {
                height(16.dp)
                    .fillMaxWidth()
                    .cursorForVerticalResize()
            }
        }
    }

    fun PointerInputScope.onDrag(dragAmount: Offset) {
        when (panel) {
            left-> {
                val newWidth = windowPanelModel.getSize(left) + dragAmount.x.toDp()
                windowPanelModel.setSize(left, newWidth.coerceIn(minPanelWidth, maxPanelWidth))
            }
            right -> {
                val newWidth = windowPanelModel.getSize(right) - dragAmount.x.toDp()
                windowPanelModel.setSize(right, newWidth.coerceIn(minPanelWidth, maxPanelWidth))
            }
            else -> {
                val newHeight = windowPanelModel.getSize(bottom) - dragAmount.y.toDp()
                windowPanelModel.setSize(bottom, newHeight.coerceIn(minPanelHeight, maxPanelHeight))
            }
        }
    }

    val alignDirection = run {
        when (panel) {
            left-> Alignment.TopStart
            right-> Alignment.TopEnd
            else -> Alignment.BottomCenter
        }
    }

    Box(
        modifier = Modifier
            .align(alignDirection)
            .offset()
            .resizable()
            .alpha(0f)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount)
                }
            }
    )
}



