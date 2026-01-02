package ai.rever.bosseditor.scrollbar

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max

private const val AUTO_HIDE_DELAY_MS = 1500L  // Hide after 1.5 seconds of inactivity

/**
 * Custom scrollbar for BossEditor with auto-hide behavior.
 * Shows when scrolling, hovered, or dragging; hides smoothly after inactivity.
 *
 * @param adapter ScrollbarAdapter that provides scroll position information
 * @param modifier Modifier to be applied to the scrollbar container
 * @param thickness Width of the scrollbar in Dp
 * @param thumbColor Color of the scrollbar thumb
 * @param trackColor Color of the scrollbar track background
 * @param minThumbHeight Minimum height of the thumb in Dp
 * @param searchMatchPositions Normalized [0, 1] positions of search matches for scrollbar markers
 * @param currentSearchMatchIndex Index of the current search match (-1 if none)
 * @param searchMarkerColor Color for regular search match markers
 * @param currentSearchMarkerColor Color for current search match marker
 * @param errorPositions Normalized [0, 1] positions of errors for scrollbar markers
 * @param warningPositions Normalized [0, 1] positions of warnings for scrollbar markers
 * @param errorMarkerColor Color for error markers
 * @param warningMarkerColor Color for warning markers
 * @param alwaysVisible If true, scrollbar is always visible (no auto-hide)
 */
@Composable
fun EditorScrollbar(
    adapter: ScrollbarAdapter,
    modifier: Modifier = Modifier,
    thickness: Dp = 12.dp,
    thumbColor: Color = Color.White.copy(alpha = 0.5f),
    trackColor: Color = Color.Transparent,
    minThumbHeight: Dp = 32.dp,
    searchMatchPositions: List<Float> = emptyList(),
    currentSearchMatchIndex: Int = -1,
    searchMarkerColor: Color = Color(0xFFFFAA00),
    currentSearchMarkerColor: Color = Color(0xFFFF6600),
    errorPositions: List<Float> = emptyList(),
    warningPositions: List<Float> = emptyList(),
    errorMarkerColor: Color = Color(0xFFFF5555),
    warningMarkerColor: Color = Color(0xFFFFAA00),
    alwaysVisible: Boolean = false,
    userScrollTrigger: State<Int> = mutableStateOf(0)
) {
    var containerHeight by remember { mutableStateOf(0f) }
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
    val maxScroll = if (containerHeight > 0f) {
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
            .width(thickness)
            .fillMaxHeight()
            .onSizeChanged { containerHeight = it.height.toFloat() }
            .alpha(scrollbarAlpha)
    ) {
        // Only render visible scrollbar when there's content to scroll
        if (containerHeight > 0f && maxScroll > 0.0) {
            // Calculate thumb dimensions
            val thumbHeightPx = run {
                val visibleRatio = containerHeight / (containerHeight + maxScroll)
                max(
                    with(LocalDensity.current) { minThumbHeight.toPx() }.toDouble(),
                    containerHeight * visibleRatio
                )
            }
            val thumbOffsetPx = run {
                val scrollableHeight = containerHeight - thumbHeightPx
                if (maxScroll > 0) (scrollOffset / maxScroll) * scrollableHeight else 0.0
            }

            // Track - handles all pointer events
            Box(
                modifier = Modifier
                    .width(thickness)
                    .fillMaxHeight()
                    .background(trackColor, shape = RoundedCornerShape(4.dp))
                    .hoverable(interactionSource)
                    // Drag gesture for scrolling
                    .pointerInput(maxScroll, containerHeight, thumbHeightPx) {
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

                                if (maxScroll > 0.0 && containerHeight > 0f) {
                                    accumulatedDrag += dragAmount.y
                                    val scrollableHeight = containerHeight - thumbHeightPx
                                    if (scrollableHeight > 0.0) {
                                        val dragRatio = accumulatedDrag / scrollableHeight
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
                            if (containerHeight > 0f && maxScroll > 0.0) {
                                val clickedPosition = offset.y / containerHeight
                                val targetScroll = clickedPosition * maxScroll
                                scope.launch {
                                    adapter.scrollTo(targetScroll)
                                }
                            }
                        }
                    }
            ) {
                // Draw markers on the track
                val hasMarkers = searchMatchPositions.isNotEmpty() ||
                                 errorPositions.isNotEmpty() ||
                                 warningPositions.isNotEmpty()

                if (hasMarkers) {
                    val density = LocalDensity.current
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val markerWidth = size.width * 0.6f
                        val markerX = (size.width - markerWidth) / 2

                        // Draw error markers (highest priority - draw on top)
                        errorPositions.forEach { position ->
                            val y = position * size.height
                            drawRect(
                                color = errorMarkerColor,
                                topLeft = Offset(markerX, y - 1f),
                                size = Size(markerWidth, 3f)
                            )
                        }

                        // Draw warning markers
                        warningPositions.forEach { position ->
                            val y = position * size.height
                            drawRect(
                                color = warningMarkerColor,
                                topLeft = Offset(markerX, y - 1f),
                                size = Size(markerWidth, 3f)
                            )
                        }

                        // Draw search match markers
                        searchMatchPositions.forEachIndexed { index, position ->
                            val y = position * size.height
                            val color = if (index == currentSearchMatchIndex) {
                                currentSearchMarkerColor
                            } else {
                                searchMarkerColor
                            }
                            drawRect(
                                color = color,
                                topLeft = Offset(markerX, y - 1f),
                                size = Size(markerWidth, 3f)
                            )
                        }
                    }
                }

                // Thumb
                Box(
                    modifier = Modifier
                        .offset { IntOffset(0, thumbOffsetPx.toInt()) }
                        .width(thickness)
                        .height(with(LocalDensity.current) { thumbHeightPx.toFloat().toDp() })
                        .padding(2.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(thumbColor.copy(alpha = thumbAlpha))
                )
            }
        }
    }
}
