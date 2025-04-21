package ai.rever.boss.v4.components.overlays

import ai.rever.boss.platform.CursorUtil.cursorForHorizontalResize
import ai.rever.boss.platform.CursorUtil.cursorForVerticalResize
import ai.rever.boss.v4.components.model.BossWindowPanelModel
import ai.rever.boss.v4.components.model.Panel
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
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
    ResizeHandle(windowPanelModel, panel = Panel.left)
    ResizeHandle(windowPanelModel, panel = Panel.right)
    ResizeHandle(windowPanelModel, panel = Panel.bottom)
}



@Composable
private fun BoxScope.ResizeHandle(windowPanelModel: BossWindowPanelModel, panel: Panel) {

    // Min and max constraints for panel sizes
    val minPanelWidth = 150.dp
    val maxPanelWidth = 500.dp
    val minPanelHeight = 100.dp
    val maxPanelHeight = 500.dp


    val leftPanelWidth by derivedStateOf { windowPanelModel.leftPanelWidth }
    val rightPanelWidth by derivedStateOf { windowPanelModel.rightPanelWidth }
    val bottomPanelHeight by derivedStateOf { windowPanelModel.bottomPanelHeight }

    val isBottomPanelVisible by derivedStateOf { windowPanelModel.isVisible(Panel.BOTTOM()) }

    val isVisible by derivedStateOf { windowPanelModel.isVisible(panel) }

    if (!isVisible) {
        return
    }

    fun Modifier.offset() = offset {
        val x = when (panel) {
            Panel.left -> (leftPanelWidth - 8.dp)
            Panel.right -> (-rightPanelWidth - 1.dp + 8.dp)
            else -> { 0.dp }
        }.roundToPx()
        val y = when (panel) {
            Panel.left, Panel.RIGHT() -> 0.dp
            Panel.bottom -> -bottomPanelHeight - 1.dp + 8.dp
            else -> { 0.dp }
        }.roundToPx()
        IntOffset(x, y)
    }

    fun Modifier.resizable() = run {
        when (panel) {
            Panel.LEFT(),
            Panel.RIGHT() -> {
                width(16.dp)
                    .fillMaxHeight(if (isBottomPanelVisible) 1f - (bottomPanelHeight / 1000.dp) else 1f)
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
            Panel.LEFT()-> {
                val newWidth = leftPanelWidth + dragAmount.x.toDp()
                windowPanelModel.leftPanelWidth = newWidth.coerceIn(minPanelWidth, maxPanelWidth)
            }
            Panel.RIGHT() -> {
                val newWidth = rightPanelWidth - dragAmount.x.toDp()
                windowPanelModel.rightPanelWidth = newWidth.coerceIn(minPanelWidth, maxPanelWidth)
            }
            else -> {
                val newHeight = bottomPanelHeight - dragAmount.y.toDp()
                windowPanelModel.bottomPanelHeight = newHeight.coerceIn(minPanelHeight, maxPanelHeight)
            }
        }
    }

    val alignDirection by derivedStateOf {
        when (panel) {
            Panel.LEFT()-> Alignment.TopStart
            Panel.RIGHT()-> Alignment.TopEnd
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



