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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

@Composable
fun BoxScope.ResizeOverlay(resizeBossPanelModel: BossWindowPanelModel) {

    // Transparent overlays for resizing - positioned in fixed locations
    ResizeHandle(resizeBossPanelModel, Panel.LEFT)
    ResizeHandle(resizeBossPanelModel, Panel.RIGHT)
    ResizeHandle(resizeBossPanelModel, Panel.BOTTOM)

}



@Composable
private fun BoxScope.ResizeHandle(resizeBossPanelModel: BossWindowPanelModel, panel: Panel) {

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

    if (panel == Panel.LEFT && !isLeftPanelVisible
        || panel == Panel.RIGHT && !isRightPanelVisible
        || panel == Panel.BOTTOM && !isBottomPanelVisible) {
        return
    }

    Box(
        modifier = Modifier
            .run {
                when (panel) {
                    Panel.LEFT -> Modifier.align(Alignment.TopStart)
                    Panel.RIGHT -> Modifier.align(Alignment.TopEnd)
                    Panel.BOTTOM -> Modifier.align(Alignment.BottomCenter)
                }
            }
            .offset {
                when (panel) {
                    Panel.LEFT -> IntOffset(leftPanelWidth.roundToPx() - 8.dp.roundToPx(), 0)
                    Panel.RIGHT -> IntOffset(
                        -rightPanelWidth.roundToPx() - 1.dp.roundToPx() + 8.dp.roundToPx(),
                        0
                    )
                    Panel.BOTTOM -> IntOffset(
                        0,
                        -bottomPanelHeight.roundToPx() - 1.dp.roundToPx() + 8.dp.roundToPx()
                    )
                }
            }
            .run {
                when (panel) {
                    Panel.LEFT, Panel.RIGHT -> {
                        width(16.dp)
                            .fillMaxHeight(if (isBottomPanelVisible) 1f - (bottomPanelHeight / 1000.dp) else 1f)
                            .cursorForHorizontalResize()
                    }
                    Panel.BOTTOM -> {
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
                        when (panel) {
                            Panel.LEFT -> {
                                val newWidth = leftPanelWidth + dragAmount.x.toDp()
                                resizeBossPanelModel.leftPanelWidth = newWidth.coerceIn(minPanelWidth, maxPanelWidth)
                            }
                            Panel.RIGHT -> {
                                val newWidth = rightPanelWidth - dragAmount.x.toDp()
                                resizeBossPanelModel.rightPanelWidth = newWidth.coerceIn(minPanelWidth, maxPanelWidth)
                            }
                            Panel.BOTTOM -> {
                                val newHeight = bottomPanelHeight - dragAmount.y.toDp()
                                resizeBossPanelModel.bottomPanelHeight = newHeight.coerceIn(minPanelHeight, maxPanelHeight)
                            }
                        }
                    }
                }
            }
    )
}

