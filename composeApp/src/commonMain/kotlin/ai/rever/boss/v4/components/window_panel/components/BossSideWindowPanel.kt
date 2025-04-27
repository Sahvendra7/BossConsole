package ai.rever.boss.v4.components.window_panel.components

import ai.rever.boss.platform.CursorUtil.cursorForHorizontalResize
import ai.rever.boss.platform.CursorUtil.cursorForVerticalResize
import ai.rever.boss.v4.components.dividers.VDivider
import ai.rever.boss.v4.components.model.Panel
import ai.rever.boss.v4.components.model.Panel.Companion.bottom
import ai.rever.boss.v4.components.model.Panel.Companion.isFirst
import ai.rever.boss.v4.components.model.Panel.Companion.isHorizontal
import ai.rever.boss.v4.components.model.Panel.Companion.isLast
import ai.rever.boss.v4.components.model.Panel.Companion.isVertical
import ai.rever.boss.v4.components.model.Panel.Companion.left
import ai.rever.boss.v4.components.model.Panel.Companion.right
import ai.rever.boss.v4.components.model.Panel.Companion.top
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
fun BossPanel(modifier: Modifier,
              panel: Panel,
              isPanelVisible: Boolean = false,
              isMainVisible: Boolean = true,
              relativeResize: Boolean = true,
              panelContent: (@Composable BoxScope.() -> Unit)? = null,
              mainContent: (@Composable BoxScope.() -> Unit)? = null) {

    val defaultPanelWidth = 250.dp
    val defaultPanelHeight = 200.dp

    val minPanelWidth = 150.dp
    val maxPanelWidth = 500.dp
    val minPanelHeight = 100.dp
    val maxPanelHeight = 500.dp
    val resizeAreaSize = 16.dp
    val dividerHeight = 1.dp

    var panelWidth: Dp? = null
    var panelHeight: Dp? = null

    BoxWithConstraints (modifier = modifier) {
        if ((panelWidth == null || panelHeight == null) || !relativeResize) {
            panelWidth = maxWidth
            panelHeight = maxHeight
        }

        var size by remember {
            mutableStateOf(
                if (panel.isHorizontal) defaultPanelWidth else defaultPanelHeight
            )
        }

        val panelWeight: Float = run {
            if (panel.isHorizontal) {
                (size.value / panelWidth.value)
            } else {
                (size.value / panelHeight.value)
            }
        }

        val relativeSize: Dp = run {
            if (panel.isHorizontal) {
                (panelWeight * maxWidth.value).dp
            } else {
                (panelWeight * maxHeight.value).dp
            }
        }

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
            if (panel.isHorizontal) {
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


        fun PointerInputScope.onDrag(dragAmount: Offset) {
            if (panel.isHorizontal) {
                val newWidth = size + (dragAmount.x.toDp() * (if (panel.isLast) -1 else 1))
                size = newWidth.coerceIn(minPanelWidth, maxPanelWidth)
            } else  {
                val newWidth = size + (dragAmount.y.toDp() * (if (panel.isLast) -1 else 1))
                size = newWidth.coerceIn(minPanelHeight, maxPanelHeight)
            }
        }

        @Composable
        fun Body(modifier: Modifier) {

            panelContent?.let {
                if (panel.isFirst && isPanelVisible) {
                    Box(modifier = Modifier.fillSize()) {
                        it()
                    }
                    if (panel.isHorizontal) {
                        VDivider()
                    } else {
                        Divider()
                    }
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
                    if (panel.isHorizontal) {
                        VDivider()
                    } else {
                        Divider()
                    }
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

        if (isPanelVisible) {
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
