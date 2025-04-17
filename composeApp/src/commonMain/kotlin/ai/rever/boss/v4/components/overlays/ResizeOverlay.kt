package ai.rever.boss.v4.components.overlays

import ai.rever.boss.platform.CursorUtil.cursorForHorizontalResize
import ai.rever.boss.platform.CursorUtil.cursorForVerticalResize
import ai.rever.boss.v4.components.model.ResizeBossPanelModel
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

@Composable
fun BoxScope.ResizeOverlay(isLeftPanelVisible: Boolean,
                           isRightPanelVisible: Boolean,
                           isBottomPanelVisible: Boolean,
                           resizeBossPanelModel: ResizeBossPanelModel
) {

    // Min and max constraints for panel sizes
    val minPanelWidth = 150.dp
    val maxPanelWidth = 500.dp
    val minPanelHeight = 100.dp
    val maxPanelHeight = 500.dp

    // Density for converting between dp and pixels
    val density = LocalDensity.current

    // Transparent overlays for resizing - positioned in fixed locations
    if (isLeftPanelVisible) {
        // Left panel resize overlay
        Box(
            modifier = Modifier
                .offset { IntOffset(resizeBossPanelModel.leftPanelWidth.roundToPx() - 8.dp.roundToPx(), 0) }
                .width(16.dp)
                .fillMaxHeight(if (isBottomPanelVisible) 1f - (resizeBossPanelModel.bottomPanelHeight / 1000.dp) else 1f)
                .alpha(0f)
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        with(density) {
                            val newWidth = resizeBossPanelModel.leftPanelWidth + dragAmount.x.toDp()
                            resizeBossPanelModel.leftPanelWidth = newWidth.coerceIn(minPanelWidth, maxPanelWidth)
                        }
                    }
                }
                .cursorForHorizontalResize()
        )
    }

    if (isRightPanelVisible) {
        // Right panel resize overlay
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset { IntOffset(-resizeBossPanelModel.rightPanelWidth.roundToPx() - 1.dp.roundToPx() + 8.dp.roundToPx(), 0) }
                .width(16.dp)
                .fillMaxHeight(if (isBottomPanelVisible) 1f - (resizeBossPanelModel.bottomPanelHeight / 1000.dp) else 1f)
                .alpha(0f)
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        with(density) {
                            val newWidth = resizeBossPanelModel.rightPanelWidth - dragAmount.x.toDp()
                            resizeBossPanelModel.rightPanelWidth = newWidth.coerceIn(minPanelWidth, maxPanelWidth)
                        }
                    }
                }
                .cursorForHorizontalResize()
        )
    }

    if (isBottomPanelVisible) {
        // Bottom panel resize overlay
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset { IntOffset(0, -resizeBossPanelModel.bottomPanelHeight.roundToPx() - 1.dp.roundToPx() + 8.dp.roundToPx()) }
                .fillMaxWidth()
                .height(16.dp)
                .alpha(0f)
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        with(density) {
                            val newHeight = resizeBossPanelModel.bottomPanelHeight - dragAmount.y.toDp()
                            resizeBossPanelModel.bottomPanelHeight = newHeight.coerceIn(minPanelHeight, maxPanelHeight)
                        }
                    }
                }
                .cursorForVerticalResize()
        )
    }
}