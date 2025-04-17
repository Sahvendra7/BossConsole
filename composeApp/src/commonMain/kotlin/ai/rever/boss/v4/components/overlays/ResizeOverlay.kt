package ai.rever.boss.v4.components.overlays

import ai.rever.boss.platform.CursorUtil.cursorForHorizontalResize
import ai.rever.boss.platform.CursorUtil.cursorForVerticalResize
import ai.rever.boss.v4.components.model.ResizeBossPanelModel
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

@Composable
fun BoxScope.ResizeOverlay(resizeBossPanelModel: ResizeBossPanelModel) {

    // Transparent overlays for resizing - positioned in fixed locations
    ResizeHandle(resizeBossPanelModel, ResizeDirection.LEFT)
    ResizeHandle(resizeBossPanelModel, ResizeDirection.RIGHT)
    ResizeHandle(resizeBossPanelModel, ResizeDirection.BOTTOM)

}

enum class ResizeDirection {
    LEFT, RIGHT, BOTTOM
}

@Composable
private fun BoxScope.ResizeHandle(resizeBossPanelModel: ResizeBossPanelModel, resizeDirection: ResizeDirection) {

    // Min and max constraints for panel sizes
    val minPanelWidth = 150.dp
    val maxPanelWidth = 500.dp
    val minPanelHeight = 100.dp
    val maxPanelHeight = 500.dp

    // Density for converting between dp and pixels
    val density = LocalDensity.current

    val isLeftPanelVisible by derivedStateOf {  resizeBossPanelModel.isLeftPanelVisible }
    val isRightPanelVisible by derivedStateOf {  resizeBossPanelModel.isRightPanelVisible }
    val isBottomPanelVisible by derivedStateOf {  resizeBossPanelModel.isBottomPanelVisible }

    val leftPanelWidth by derivedStateOf { resizeBossPanelModel.leftPanelWidth }
    val rightPanelWidth by derivedStateOf { resizeBossPanelModel.rightPanelWidth }
    val bottomPanelHeight by derivedStateOf {  resizeBossPanelModel.bottomPanelHeight }

    if (resizeDirection == ResizeDirection.LEFT && !isLeftPanelVisible
        || resizeDirection == ResizeDirection.RIGHT && !isRightPanelVisible
        || resizeDirection == ResizeDirection.BOTTOM && !isBottomPanelVisible) {
        return
    }

    Box(
        modifier = Modifier
            .run {
                when (resizeDirection) {
                    ResizeDirection.LEFT -> Modifier.align(Alignment.TopStart)
                    ResizeDirection.RIGHT -> Modifier.align(Alignment.TopEnd)
                    ResizeDirection.BOTTOM -> Modifier.align(Alignment.BottomCenter)
                }
            }
            .offset {
                when (resizeDirection) {
                    ResizeDirection.LEFT -> IntOffset(leftPanelWidth.roundToPx() - 8.dp.roundToPx(), 0)
                    ResizeDirection.RIGHT -> IntOffset(
                        -rightPanelWidth.roundToPx() - 1.dp.roundToPx() + 8.dp.roundToPx(),
                        0
                    )
                    ResizeDirection.BOTTOM -> IntOffset(
                        0,
                        -bottomPanelHeight.roundToPx() - 1.dp.roundToPx() + 8.dp.roundToPx()
                    )
                }
            }
            .run {
                when (resizeDirection) {
                    ResizeDirection.LEFT, ResizeDirection.RIGHT -> {
                        width(16.dp)
                            .fillMaxHeight(if (isBottomPanelVisible) 1f - (bottomPanelHeight / 1000.dp) else 1f)
                            .cursorForHorizontalResize()
                    }
                    ResizeDirection.BOTTOM -> {
                        height(16.dp)
                            .fillMaxWidth()
                            .cursorForVerticalResize()
                    }
                }
            }
            .alpha(0f)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    with(density) {
                        when (resizeDirection) {
                            ResizeDirection.LEFT -> {
                                val newWidth = rightPanelWidth - dragAmount.x.toDp()
                                resizeBossPanelModel.leftPanelWidth = newWidth.coerceIn(minPanelWidth, maxPanelWidth)
                            }
                            ResizeDirection.RIGHT -> {
                                val newWidth = leftPanelWidth - dragAmount.x.toDp()
                                resizeBossPanelModel.rightPanelWidth = newWidth.coerceIn(minPanelWidth, maxPanelWidth)
                            }
                            ResizeDirection.BOTTOM -> {
                                val newHeight = bottomPanelHeight - dragAmount.y.toDp()
                                resizeBossPanelModel.bottomPanelHeight = newHeight.coerceIn(minPanelHeight, maxPanelHeight)
                            }
                        }
                    }
                }
            }
    )
}

