package ai.rever.boss.components.bars

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.max

@Composable
fun DraggableVerticalScrollbar(
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
    indicatorThickness: Dp = 8.dp,
    indicatorColor: Color = Color.Gray.copy(alpha = 0.7f),
    indicatorMinHeight: Dp = 20.dp,
    fadeInAnimationDuration: Int = 150,
    fadeOutAnimationDuration: Int = 500,
    fadeOutDelay: Int = 1500
) {
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    
    // Track if we're dragging
    var isDragging by remember { mutableStateOf(false) }
    
    // Alpha animation
    val targetAlpha = when {
        isDragging -> 1f
        scrollState.isScrollInProgress -> 0.8f
        else -> 0f
    }
    
    val scrollbarAlpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = tween(
            durationMillis = if (targetAlpha > 0) fadeInAnimationDuration else fadeOutAnimationDuration,
            delayMillis = if (targetAlpha == 0f && !isDragging) fadeOutDelay else 0
        )
    )
    
    if (scrollbarAlpha > 0f && scrollState.maxValue > 0) {
        BoxWithConstraints(
            modifier = modifier
                .fillMaxHeight()
                .width(indicatorThickness)
        ) {
            val viewportHeight = with(density) { maxHeight.toPx() }
            val contentHeight = viewportHeight + scrollState.maxValue
            val scrollbarHeight = max(
                (viewportHeight / contentHeight) * viewportHeight,
                with(density) { indicatorMinHeight.toPx() }
            )
            val scrollbarOffsetY = (scrollState.value.toFloat() / scrollState.maxValue) * 
                (viewportHeight - scrollbarHeight)
            
            Box(
                modifier = Modifier
                    .offset(y = with(density) { scrollbarOffsetY.toDp() })
                    .clip(RoundedCornerShape(indicatorThickness / 2))
                    .background(indicatorColor)
                    .fillMaxWidth()
                    .height(with(density) { scrollbarHeight.toDp() })
                    .alpha(scrollbarAlpha)
                    .pointerInput(scrollState) {
                        detectDragGestures(
                            onDragStart = { 
                                isDragging = true
                            },
                            onDragEnd = {
                                isDragging = false
                            },
                            onDrag = { _, dragAmount ->
                                coroutineScope.launch {
                                    val dragAmountPx = dragAmount.y
                                    val scrollAmount = (dragAmountPx / (viewportHeight - scrollbarHeight)) * 
                                        scrollState.maxValue
                                    val newValue = (scrollState.value + scrollAmount).coerceIn(
                                        0f, 
                                        scrollState.maxValue.toFloat()
                                    )
                                    scrollState.animateScrollTo(newValue.toInt())
                                }
                            }
                        )
                    }
            )
        }
    }
}

@Composable
fun TerminalWithDraggableScrollbar(
    modifier: Modifier = Modifier,
    scrollState: ScrollState,
    content: @Composable () -> Unit
) {
    Box(modifier = modifier) {
        // Terminal content
        Box(modifier = Modifier.fillMaxSize()) {
            content()
        }
        
        // Draggable scrollbar
        DraggableVerticalScrollbar(
            scrollState = scrollState,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 2.dp),
            indicatorThickness = 6.dp,
            indicatorColor = Color.Gray.copy(alpha = 0.7f)
        )
    }
} 