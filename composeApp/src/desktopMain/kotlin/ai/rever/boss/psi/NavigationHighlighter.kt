package ai.rever.boss.psi

import ai.rever.boss.utils.logging.BossLogger
import ai.rever.boss.utils.logging.LogCategory
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea
import java.awt.Color
import java.awt.Cursor
import javax.swing.text.DefaultHighlighter
import javax.swing.text.Highlighter

/**
 * Provides visual feedback for code navigation in RSyntaxTextArea.
 *
 * This highlighter:
 * - Highlights navigable symbols on Cmd+hover
 * - Changes cursor to hand when hovering over navigable symbols
 * - Clears highlighting when Cmd is released or cursor moves away
 *
 * @property textArea The RSyntaxTextArea to provide highlighting for
 */
class NavigationHighlighter(private val textArea: RSyntaxTextArea) {
    private val logger = BossLogger.forComponent("NavigationHighlighter")

    /**
     * Current highlight tag (for removal).
     */
    private var currentHighlightTag: Any? = null

    /**
     * The default cursor to restore.
     */
    private val defaultCursor: Cursor = textArea.cursor

    /**
     * Color for navigation highlight (blue with transparency).
     */
    private val highlightColor = Color(0x3d, 0x7e, 0xdb, 0x60)

    /**
     * Highlight painter.
     */
    private val highlightPainter = DefaultHighlighter.DefaultHighlightPainter(highlightColor)

    /**
     * Whether highlighting is currently active.
     */
    private var isHighlightActive = false

    /**
     * Highlight a range in the editor.
     *
     * @param startOffset Start of the range (inclusive)
     * @param endOffset End of the range (exclusive)
     */
    fun highlightRange(startOffset: Int, endOffset: Int) {
        logger.debug(LogCategory.EDITOR, "highlightRange called", mapOf(
            "startOffset" to startOffset,
            "endOffset" to endOffset,
            "docLen" to textArea.document.length
        ))

        // Validate range
        if (startOffset < 0 || endOffset > textArea.document.length || startOffset >= endOffset) {
            logger.debug(LogCategory.EDITOR, "Invalid range, skipping")
            return
        }

        // Clear any existing highlight
        clearHighlight()

        try {
            // Add new highlight
            currentHighlightTag = textArea.highlighter.addHighlight(
                startOffset,
                endOffset,
                highlightPainter
            )
            logger.debug(LogCategory.EDITOR, "Highlight added successfully", mapOf("tag" to currentHighlightTag.toString()))

            // Change cursor to hand
            textArea.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            isHighlightActive = true

        } catch (e: Exception) {
            logger.warn(LogCategory.EDITOR, "Error adding highlight", error = e)
        }
    }

    /**
     * Clear the current highlight.
     */
    fun clearHighlight() {
        currentHighlightTag?.let { tag ->
            try {
                textArea.highlighter.removeHighlight(tag)
            } catch (e: Exception) {
                // Ignore errors during removal
            }
            currentHighlightTag = null
        }

        // Restore default cursor
        if (isHighlightActive) {
            textArea.cursor = defaultCursor
            isHighlightActive = false
        }
    }

    /**
     * Check if highlighting is currently active.
     */
    fun hasHighlight(): Boolean = isHighlightActive

    /**
     * Update highlighting based on whether a position is navigable.
     *
     * @param offset Character offset in the document
     * @param isNavigable Whether the position is navigable
     * @param startOffset Start of navigable range
     * @param endOffset End of navigable range
     */
    fun updateHighlight(offset: Int, isNavigable: Boolean, startOffset: Int = -1, endOffset: Int = -1) {
        if (isNavigable && startOffset >= 0 && endOffset > startOffset) {
            highlightRange(startOffset, endOffset)
        } else {
            clearHighlight()
        }
    }

    /**
     * Dispose of resources.
     */
    fun dispose() {
        clearHighlight()
    }
}

/**
 * Highlight style for different navigation contexts.
 */
enum class NavigationHighlightStyle {
    /**
     * Default navigation highlight (blue).
     */
    DEFAULT,

    /**
     * Definition highlight (yellow).
     */
    DEFINITION,

    /**
     * Reference highlight (green).
     */
    REFERENCE,

    /**
     * Error highlight (red).
     */
    ERROR
}

/**
 * Factory for creating highlight painters with different styles.
 */
object NavigationHighlightPainterFactory {

    private val styleColors = mapOf(
        NavigationHighlightStyle.DEFAULT to Color(0x3d, 0x7e, 0xdb, 0x60),
        NavigationHighlightStyle.DEFINITION to Color(0xff, 0xd7, 0x00, 0x60),
        NavigationHighlightStyle.REFERENCE to Color(0x00, 0x80, 0x00, 0x60),
        NavigationHighlightStyle.ERROR to Color(0xff, 0x00, 0x00, 0x40)
    )

    /**
     * Create a highlight painter for the given style.
     */
    fun createPainter(style: NavigationHighlightStyle): Highlighter.HighlightPainter {
        val color = styleColors[style] ?: styleColors[NavigationHighlightStyle.DEFAULT]!!
        return DefaultHighlighter.DefaultHighlightPainter(color)
    }
}
