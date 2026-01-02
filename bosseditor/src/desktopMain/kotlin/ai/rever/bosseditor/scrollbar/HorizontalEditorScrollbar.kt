package ai.rever.bosseditor.scrollbar

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.v2.ScrollbarAdapter
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max

private const val AUTO_HIDE_DELAY_MS = 1500L

/**
 * Horizontal scrollbar for BossEditor with auto-hide behavior.
 * Shows when scrolling, hovered, or dragging; hides smoothly after inactivity.
 *
 * @param adapter ScrollbarAdapter that provides scroll position information
 * @param modifier Modifier to be applied to the scrollbar container
 * @param thickness Height of the scrollbar in Dp
 * @param thumbColor Color of the scrollbar thumb
 * @param trackColor Color of the scrollbar track background
 * @param minThumbWidth Minimum width of the thumb in Dp
 * @param alwaysVisible If true, scrollbar is always visible (no auto-hide)
 * @param userScrollTrigger State that triggers showing scrollbar when value changes
 */
@Composable
fun HorizontalEditorScrollbar(
    adapter: ScrollbarAdapter,
    modifier: Modifier = Modifier,
    thickness: Dp = 12.dp,
    thumbColor: Color = Color.White.copy(alpha = 0.5f),
    trackColor: Color = Color.Transparent,
    minThumbWidth: Dp = 32.dp,
    alwaysVisible: Boolean = false,
    userScrollTrigger: State<Int> = mutableStateOf(0)
) {
    var containerWidth by remember { mutableStateOf(0f) }
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val scope = rememberCoroutineScope()

    // Auto-hide state tracking
    var isVisible by remember { mutableStateOf(alwaysVisible) }
    var isDragging by remember { mutableStateOf(false) }
    var dragStartScrollOffset by remember { mutableStateOf(0.0) }
    var accumulatedDrag by remember { mutableStateOf(0.0) }

    // Read scroll state
    val scrollOffset = adapter.scrollOffset
    val maxScroll = if (containerWidth > 0f) {
        (adapter.contentSize - adapter.viewportSize).coerceAtLeast(0.0)
    } else {
        0.0
    }

    // Detect user-initiated scroll activity and show scrollbar
    LaunchedEffect(userScrollTrigger.value) {
        if (userScrollTrigger.value > 0) {
            isVisible = true
        }
    }

    // Auto-hide timer: hide after inactivity threshold
    LaunchedEffect(isVisible, isHovered, isDragging, alwaysVisible) {
        if (!alwaysVisible && isVisible && !isHovered && !isDragging) {
            delay(AUTO_HIDE_DELAY_MS)
            if (!isHovered && !isDragging) {
                isVisible = false
            }
        }
    }

    // Calculate target alpha based on visibility state
    val shouldShow = alwaysVisible || isVisible || isHovered || isDragging
    val targetAlpha = if (shouldShow) 1f else 0f
    val scrollbarAlpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = tween(durationMillis = 300)
    )

    // Calculate thumb opacity based on hover state
    val thumbAlpha = if (isHovered || isDragging) 0.8f else 0.5f

    Box(
        modifier = modifier
            .height(thickness)
            .fillMaxWidth()
            .onSizeChanged { containerWidth = it.width.toFloat() }
            .alpha(scrollbarAlpha)
    ) {
        // Only render visible scrollbar when there's content to scroll
        if (containerWidth > 0f && maxScroll > 0.0) {
            // Calculate thumb dimensions
            val thumbWidthPx = run {
                val visibleRatio = containerWidth / (containerWidth + maxScroll)
                max(
                    with(LocalDensity.current) { minThumbWidth.toPx() }.toDouble(),
                    containerWidth * visibleRatio
                )
            }
            val thumbOffsetPx = run {
                val scrollableWidth = containerWidth - thumbWidthPx
                if (maxScroll > 0) (scrollOffset / maxScroll) * scrollableWidth else 0.0
            }

            // Track - handles all pointer events
            Box(
                modifier = Modifier
                    .height(thickness)
                    .fillMaxWidth()
                    .background(trackColor, shape = RoundedCornerShape(4.dp))
                    .hoverable(interactionSource)
                    // Drag gesture for scrolling
                    .pointerInput(maxScroll, containerWidth, thumbWidthPx) {
                        detectDragGestures(
                            onDragStart = { _ ->
                                isDragging = true
                                isVisible = true
                                dragStartScrollOffset = adapter.scrollOffset
                                accumulatedDrag = 0.0
                            },
                            onDragEnd = {
                                isDragging = false
                                accumulatedDrag = 0.0
                            },
                            onDragCancel = {
                                isDragging = false
                                accumulatedDrag = 0.0
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()

                                if (maxScroll > 0.0 && containerWidth > 0f) {
                                    accumulatedDrag += dragAmount.x
                                    val scrollableWidth = containerWidth - thumbWidthPx
                                    if (scrollableWidth > 0.0) {
                                        val dragRatio = accumulatedDrag / scrollableWidth
                                        val newScrollOffset = (dragStartScrollOffset + dragRatio * maxScroll)
                                            .coerceIn(0.0, maxScroll)

                                        scope.launch {
                                            adapter.scrollTo(newScrollOffset)
                                        }
                                    }
                                }
                            }
                        )
                    }
                    // Tap gesture for click-to-position
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            if (containerWidth > 0f && maxScroll > 0.0) {
                                val clickedPosition = offset.x / containerWidth
                                val targetScroll = clickedPosition * maxScroll
                                scope.launch {
                                    adapter.scrollTo(targetScroll)
                                }
                            }
                        }
                    }
            ) {
                // Thumb
                Box(
                    modifier = Modifier
                        .offset { IntOffset(thumbOffsetPx.toInt(), 0) }
                        .width(with(LocalDensity.current) { thumbWidthPx.toFloat().toDp() })
                        .height(thickness)
                        .padding(2.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(thumbColor.copy(alpha = thumbAlpha))
                )
            }
        }
    }
}
