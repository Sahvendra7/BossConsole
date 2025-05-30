package ai.rever.boss.components.window_panel.components

import BossDarkBorder
import ai.rever.boss.platform.CursorUtil.cursorForHorizontalResize
import ai.rever.boss.platform.CursorUtil.cursorForVerticalResize
import ai.rever.boss.components.dividers.VDivider
import ai.rever.boss.components.model.Panel
import ai.rever.boss.components.model.Panel.Companion.bottom
import ai.rever.boss.components.model.Panel.Companion.isFirst
import ai.rever.boss.components.model.Panel.Companion.isHorizontal
import ai.rever.boss.components.model.Panel.Companion.isLast
import ai.rever.boss.components.model.Panel.Companion.isVertical
import ai.rever.boss.components.model.Panel.Companion.left
import ai.rever.boss.components.model.Panel.Companion.right
import ai.rever.boss.components.model.Panel.Companion.top
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

@Composable
fun BossResizablePanel(modifier: Modifier,
                       panel: Panel,
                       isPanelVisible: Boolean = false,
                       isMainVisible: Boolean = true,
                       isRelative: Boolean = false,
                       defaultWeight: Float = 1f,
                       panelContent: (@Composable BoxScope.() -> Unit)? = null,
                       mainContent: (@Composable BoxScope.() -> Unit)? = null) {

    val defaultPanelSize = run { if (panel.isHorizontal) 250.dp else 200.dp }
    val minPanelSize = run { if (panel.isHorizontal) 150.dp else 100.dp }
    val resizeAreaSize = 16.dp
    val dividerHeight = 1.dp

    var panelSize: Dp? = null

    BoxWithConstraints (modifier = modifier) {
        if (panelSize == null || !isRelative) {
            panelSize = if (panel.isHorizontal) {
                maxWidth
            } else {
                maxHeight
            }
        }

        val maxSize = run { if (panel.isHorizontal)  maxWidth else maxHeight }

        var size by remember {
            mutableStateOf(
                if (isRelative) {
                    ((maxSize.value * defaultWeight)/2f).dp
                } else {
                    defaultPanelSize
                }
            )
        }

        val panelWeight: Float = run { size.value / panelSize.value }

        val relativeSize: Dp = run { (panelWeight * maxSize.value).dp }

        val alignDirection = run {
            when (panel) {
                top -> Alignment.TopCenter
                left -> Alignment.TopStart
                right -> Alignment.TopEnd
                else -> Alignment.BottomCenter
            }
        }

        fun Modifier.resizeAreaOffset() = offset {
            val halfResizeAreaSize = resizeAreaSize/2
            val x = when (panel) {
                left -> relativeSize - halfResizeAreaSize
                right -> -relativeSize - dividerHeight + halfResizeAreaSize
                else -> 0.dp
            }.roundToPx()
            val y = when (panel) {
                top -> relativeSize - halfResizeAreaSize
                bottom -> -relativeSize - dividerHeight + halfResizeAreaSize
                else -> 0.dp
            }.roundToPx()

            IntOffset(x, y)
        }


        fun Modifier.fillSize() = run {
            if (!isMainVisible || mainContent == null) {
                fillMaxSize()
            } else if (panel.isHorizontal) {
                fillMaxHeight().fillMaxWidth(panelWeight)
            } else {
                fillMaxWidth().fillMaxHeight(panelWeight)
            }
        }

        fun Modifier.resizable() = run {
            if (panel.isHorizontal) {
                fillMaxHeight()
                    .width(resizeAreaSize)
                    .resizeAreaOffset()
                    .cursorForHorizontalResize()
            } else {
                fillMaxWidth()
                    .height(resizeAreaSize)
                    .resizeAreaOffset()
                    .cursorForVerticalResize()
            }
        }

        fun Offset.axis() = run { if (panel.isHorizontal) x  else y }

        fun Dp.direction() = run { this * (if (panel.isLast) - 1 else 1) }

        fun PointerInputScope.onDrag(dragAmount: Offset) {
            size = (size + (dragAmount.axis().toDp().direction()))
                .coerceIn(minPanelSize, maxSize)
        }

        @Composable
        fun PanelDivider() {
            if (isMainVisible) {
                if (panel.isHorizontal) {
                    VDivider()
                } else {
                    Divider(color = BossDarkBorder)
                }
            }
        }

        @Composable
        fun Body(modifier: Modifier) {
            panelContent?.let {
                if (panel.isFirst && isPanelVisible) {
                    Box(modifier = Modifier.fillSize()) {
                        it()
                    }
                    PanelDivider()
                }
            }
            mainContent?.let {
                if (isMainVisible) {
                    Box(modifier = modifier) {
                        it()
                    }
                }
            }
            panelContent?.let {
                if (panel.isLast && isPanelVisible) {
                    PanelDivider()
                    Box(modifier = Modifier.fillSize()) {
                        it()
                    }
                }
            }
        }

        if (panel.isVertical) {
            Column(modifier = Modifier.fillMaxSize()) {
                Body(modifier = Modifier.weight(1f))
            }
        } else {
            Row(modifier = Modifier.fillMaxSize()) {
                Body(modifier = Modifier.weight(1f))
            }
        }

        if (isPanelVisible && isMainVisible) {
            Box(
                modifier = Modifier
                    .align(alignDirection)
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
    }
}
