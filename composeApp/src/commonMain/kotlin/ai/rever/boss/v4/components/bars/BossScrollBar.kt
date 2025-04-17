package ai.rever.boss.v4.components.bars

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.max

fun Modifier.scrollbar(
    scrollState: ScrollState,
    direction: Orientation,
    config: ScrollbarConfig = ScrollbarConfig(),
): Modifier = composed {
    var (
        indicatorThickness, indicatorColor, indicatorCornerRadius,
        alpha, alphaAnimationSpec, padding
    ) = config

    val isScrollingOrPanning = scrollState.isScrollInProgress
    val isVertical = direction == Orientation.Vertical

    alpha = alpha ?: if (isScrollingOrPanning) 0.8f else 0f
    alphaAnimationSpec = alphaAnimationSpec ?: tween(
        delayMillis = if (isScrollingOrPanning) 0 else 1500,
        durationMillis = if (isScrollingOrPanning) 150 else 500
    )

    val scrollbarAlpha by animateFloatAsState(alpha, alphaAnimationSpec)

    drawWithContent {
        drawContent()

        val showScrollbar = isScrollingOrPanning || scrollbarAlpha > 0.0f

        // Draw scrollbar only if currently scrolling or if scroll animation is ongoing.
        if (showScrollbar) {
            val (topPadding, bottomPadding, startPadding, endPadding) = arrayOf(
                padding.calculateTopPadding().toPx(), padding.calculateBottomPadding().toPx(),
                padding.calculateStartPadding(layoutDirection).toPx(),
                padding.calculateEndPadding(layoutDirection).toPx()
            )

            val isLtr = layoutDirection == LayoutDirection.Ltr
            val contentOffset = scrollState.value
            val viewPortLength = if (isVertical) size.height else size.width
            val viewPortCrossAxisLength = if (isVertical) size.width else size.height
            val contentLength = max(viewPortLength + scrollState.maxValue, 0.001f /* To prevent divide by zero error */)
            val scrollbarLength = viewPortLength -
                    (if (isVertical) topPadding + bottomPadding else startPadding + endPadding)
            val indicatorThicknessPx = indicatorThickness.toPx()
            val indicatorLength = max((scrollbarLength / contentLength) * viewPortLength, 20f.dp.toPx())
            val indicatorOffset = (scrollbarLength / contentLength) * contentOffset
            val scrollIndicatorSize = if (isVertical) Size(indicatorThicknessPx, indicatorLength)
            else Size(indicatorLength, indicatorThicknessPx)

            val scrollIndicatorPosition = if (isVertical)
                Offset(
                    x = if (isLtr) viewPortCrossAxisLength - indicatorThicknessPx - endPadding
                    else startPadding,
                    y = indicatorOffset + topPadding
                )
            else
                Offset(
                    x = if (isLtr) indicatorOffset + startPadding
                    else viewPortLength - indicatorOffset - indicatorLength - endPadding,
                    y = if (config.horizontalScrollbarAtTop) topPadding
                    else viewPortCrossAxisLength - indicatorThicknessPx - bottomPadding
                )

            drawRoundRect(
                color = indicatorColor,
                cornerRadius = indicatorCornerRadius.let { CornerRadius(it.toPx(), it.toPx()) },
                topLeft = scrollIndicatorPosition,
                size = scrollIndicatorSize,
                alpha = scrollbarAlpha
            )
        }
    }
}

data class ScrollbarConfig(
    val indicatorThickness: Dp = 8.dp,
    val indicatorColor: Color = Color.Gray.copy(alpha = 0.7f),
    val indicatorCornerRadius: Dp = indicatorThickness / 2,
    val alpha: Float? = null,
    val alphaAnimationSpec: AnimationSpec<Float>? = null,
    val padding: PaddingValues = PaddingValues(all = 0.dp),
    val horizontalScrollbarAtTop: Boolean = false
)

fun Modifier.verticalScrollWithScrollbar(
    scrollState: ScrollState,
    enabled: Boolean = true,
    flingBehavior: FlingBehavior? = null,
    reverseScrolling: Boolean = false,
    scrollbarConfig: ScrollbarConfig = ScrollbarConfig()
): Modifier = this
    .scrollbar(scrollState, direction = Orientation.Vertical, config = scrollbarConfig)
    .verticalScroll(scrollState, enabled, flingBehavior, reverseScrolling)


fun Modifier.horizontalScrollWithScrollbar(
    scrollState: ScrollState,
    enabled: Boolean = true,
    flingBehavior: FlingBehavior? = null,
    reverseScrolling: Boolean = false,
    scrollbarConfig: ScrollbarConfig = ScrollbarConfig()
): Modifier = this
    .scrollbar(scrollState, direction = Orientation.Horizontal, config = scrollbarConfig)
    .horizontalScroll(scrollState, enabled, flingBehavior, reverseScrolling)