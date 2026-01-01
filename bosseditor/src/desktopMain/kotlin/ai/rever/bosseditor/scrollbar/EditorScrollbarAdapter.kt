package ai.rever.bosseditor.scrollbar

import androidx.compose.foundation.v2.ScrollbarAdapter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import kotlin.math.max

/**
 * Custom ScrollbarAdapter that bridges editor's scroll state with Compose's scrollbar component.
 *
 * Coordinate System:
 * - scrollOffset: 0 = at top, positive = scrolled down
 * - contentSize: total document height in pixels
 * - viewportSize: visible area height in pixels
 *
 * @param editorScrollOffset Current scroll offset in editor (in pixels)
 * @param totalLines Total number of visual lines in document
 * @param viewportHeight Height of visible viewport in pixels
 * @param lineHeight Height of a single line in pixels
 * @param onScroll Callback to update scroll offset when user drags scrollbar
 */
@Composable
fun rememberEditorScrollbarAdapter(
    editorScrollOffset: State<Int>,
    totalLines: () -> Int,
    viewportHeight: () -> Float,
    lineHeight: () -> Float,
    onScroll: (Int) -> Unit
): ScrollbarAdapter {
    return remember(editorScrollOffset, totalLines, viewportHeight, lineHeight, onScroll) {
        EditorScrollbarAdapter(
            editorScrollOffset = editorScrollOffset,
            totalLines = totalLines,
            viewportHeight = viewportHeight,
            lineHeight = lineHeight,
            onScroll = onScroll
        )
    }
}

/**
 * Internal implementation of ScrollbarAdapter (v2) for editor scrolling.
 */
private class EditorScrollbarAdapter(
    private val editorScrollOffset: State<Int>,
    private val totalLines: () -> Int,
    private val viewportHeight: () -> Float,
    private val lineHeight: () -> Float,
    private val onScroll: (Int) -> Unit
) : ScrollbarAdapter {

    /**
     * Total content size in pixels (all lines).
     */
    override val contentSize: Double
        get() {
            val lines = max(1, totalLines())
            val lh = lineHeight().toDouble()
            return lines * lh
        }

    /**
     * Viewport size in pixels (visible area).
     */
    override val viewportSize: Double
        get() = viewportHeight().toDouble().coerceAtLeast(1.0)

    /**
     * Current scroll position in pixels (0.0 = top).
     */
    override val scrollOffset: Double
        get() = editorScrollOffset.value.toDouble()

    /**
     * Update scroll offset when user drags scrollbar thumb.
     *
     * @param scrollOffset New scroll position in pixels (0.0 = top)
     */
    override suspend fun scrollTo(scrollOffset: Double) {
        val maxScroll = (contentSize - viewportSize).coerceAtLeast(0.0)
        val constrainedOffset = scrollOffset.coerceIn(0.0, maxScroll).toInt()
        onScroll(constrainedOffset)
    }
}
