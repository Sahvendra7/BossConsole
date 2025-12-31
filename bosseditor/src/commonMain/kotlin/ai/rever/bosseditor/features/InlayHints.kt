package ai.rever.bosseditor.features

import ai.rever.bosseditor.core.EditorPosition

/**
 * Kinds of inlay hints.
 */
enum class InlayHintKind {
    /** Parameter name hint (e.g., `foo(/*name=*/ value)`) */
    PARAMETER,

    /** Type hint (e.g., `val x/*: Int*/ = 42`) */
    TYPE,

    /** Chained method hint */
    CHAIN,

    /** Other/custom hint */
    OTHER
}

/**
 * Position of the inlay hint relative to the reference position.
 */
enum class InlayHintPosition {
    /** Hint appears before the position */
    BEFORE,

    /** Hint appears after the position */
    AFTER
}

/**
 * Represents an inline hint displayed in the editor.
 *
 * Inlay hints are small, semi-transparent labels that appear inline with code
 * to provide additional information without modifying the actual text.
 *
 * @property position The position in the document where the hint appears
 * @property text The hint text to display
 * @property kind The kind of hint (for styling)
 * @property tooltip Optional tooltip shown on hover
 * @property paddingLeft Whether to add padding before the hint
 * @property paddingRight Whether to add padding after the hint
 * @property hintPosition Whether hint appears before or after the position
 */
data class InlayHint(
    val position: EditorPosition,
    val text: String,
    val kind: InlayHintKind = InlayHintKind.OTHER,
    val tooltip: String? = null,
    val paddingLeft: Boolean = false,
    val paddingRight: Boolean = true,
    val hintPosition: InlayHintPosition = InlayHintPosition.BEFORE
) {
    /** The line this hint appears on */
    val line: Int get() = position.line

    /** The column this hint appears at */
    val column: Int get() = position.column

    companion object {
        /**
         * Creates a parameter name hint.
         * Displayed as `/*name:*/ ` before the argument.
         */
        fun parameter(
            position: EditorPosition,
            parameterName: String
        ): InlayHint = InlayHint(
            position = position,
            text = "$parameterName:",
            kind = InlayHintKind.PARAMETER,
            paddingLeft = false,
            paddingRight = true,
            hintPosition = InlayHintPosition.BEFORE
        )

        /**
         * Creates a type hint.
         * Displayed as `: Type` after the variable/expression.
         */
        fun type(
            position: EditorPosition,
            typeName: String
        ): InlayHint = InlayHint(
            position = position,
            text = ": $typeName",
            kind = InlayHintKind.TYPE,
            paddingLeft = false,
            paddingRight = false,
            hintPosition = InlayHintPosition.AFTER
        )

        /**
         * Creates a chained method result type hint.
         */
        fun chain(
            position: EditorPosition,
            resultType: String
        ): InlayHint = InlayHint(
            position = position,
            text = resultType,
            kind = InlayHintKind.CHAIN,
            paddingLeft = true,
            paddingRight = false,
            hintPosition = InlayHintPosition.AFTER
        )
    }
}

/**
 * Manages inlay hints for the editor.
 */
class InlayHintManager {
    private val hints = mutableListOf<InlayHint>()
    private var hintsByLine: Map<Int, List<InlayHint>> = emptyMap()

    /**
     * Sets the inlay hints, replacing any existing ones.
     */
    fun setHints(newHints: List<InlayHint>) {
        hints.clear()
        hints.addAll(newHints)
        rebuildIndex()
    }

    /**
     * Adds a single hint.
     */
    fun addHint(hint: InlayHint) {
        hints.add(hint)
        rebuildIndex()
    }

    /**
     * Removes all hints.
     */
    fun clear() {
        hints.clear()
        hintsByLine = emptyMap()
    }

    /**
     * Gets all hints.
     */
    fun getAllHints(): List<InlayHint> = hints.toList()

    /**
     * Gets hints for a specific line, sorted by column.
     */
    fun getHintsForLine(line: Int): List<InlayHint> {
        return hintsByLine[line]?.sortedBy { it.column } ?: emptyList()
    }

    /**
     * Gets hints at a specific position.
     */
    fun getHintsAtPosition(position: EditorPosition): List<InlayHint> {
        return getHintsForLine(position.line).filter { it.column == position.column }
    }

    /**
     * Calculates the total width offset for hints before a given column on a line.
     * Used to adjust text positioning when rendering.
     *
     * @param line The line number
     * @param column The column to calculate offset for
     * @param charWidth The width of a character
     * @return The total width of hints before this column
     */
    fun calculateHintOffset(line: Int, column: Int, charWidth: Float): Float {
        return getHintsForLine(line)
            .filter { it.column < column && it.hintPosition == InlayHintPosition.BEFORE }
            .sumOf { hint ->
                val textWidth = hint.text.length * charWidth
                val padding = (if (hint.paddingLeft) charWidth else 0f) +
                              (if (hint.paddingRight) charWidth else 0f)
                (textWidth + padding).toDouble()
            }.toFloat()
    }

    private fun rebuildIndex() {
        hintsByLine = hints.groupBy { it.line }
    }
}

/**
 * Provider interface for inlay hints.
 * Implementations can provide hints from different sources (PSI, LSP).
 */
interface InlayHintProvider {
    /**
     * Gets inlay hints for a range of lines.
     *
     * @param startLine First line (inclusive)
     * @param endLine Last line (inclusive)
     * @return List of hints for the range
     */
    suspend fun getHints(startLine: Int, endLine: Int): List<InlayHint>
}
