package ai.rever.bosseditor.features

import ai.rever.bosseditor.core.EditorDocument

/**
 * Provides rainbow bracket colorization for the editor.
 *
 * Assigns colors to matching bracket pairs based on their nesting depth.
 * The color cycles through 4 colors (0-3) as nesting increases:
 * - Depth 0: Color 1 (outermost)
 * - Depth 1: Color 2
 * - Depth 2: Color 3
 * - Depth 3: Color 4
 * - Depth 4: Color 1 (cycles back)
 * - ...
 *
 * ## Usage
 * ```kotlin
 * val rainbow = RainbowBrackets(document)
 * val brackets = rainbow.getRainbowBrackets()
 * for (bracket in brackets) {
 *     highlight(bracket.offset, colors.getRainbowBracketColor(bracket.depth))
 * }
 * ```
 */
class RainbowBrackets(
    private val document: EditorDocument
) {
    private val bracketMatcher = BracketMatcher(document)

    /**
     * Gets all brackets with their rainbow color depth.
     * The depth determines which color to use (cycles every 4 levels).
     *
     * @return List of rainbow brackets sorted by offset
     */
    fun getRainbowBrackets(): List<RainbowBracket> {
        val pairs = bracketMatcher.findAllBracketPairs()
        if (pairs.isEmpty()) return emptyList()

        val result = mutableListOf<RainbowBracket>()

        // Sort pairs by open offset to process in order
        val sortedPairs = pairs.sortedBy { it.openOffset }

        // Track nesting depth using a stack-like approach
        // For each position, track how many brackets are "active" (opened but not closed)
        val brackets = mutableListOf<BracketWithDepth>()

        // First, collect all bracket positions
        for (pair in sortedPairs) {
            brackets.add(BracketWithDepth(pair.openOffset, pair.openChar, isOpening = true, pairId = pair.hashCode()))
            brackets.add(BracketWithDepth(pair.closeOffset, pair.closeChar, isOpening = false, pairId = pair.hashCode()))
        }

        // Sort by offset
        brackets.sortBy { it.offset }

        // Assign depths using a stack
        var currentDepth = 0
        val depthByPairId = mutableMapOf<Int, Int>()

        for (bracket in brackets) {
            if (bracket.isOpening) {
                // Opening bracket gets current depth, then depth increases
                depthByPairId[bracket.pairId] = currentDepth
                result.add(RainbowBracket(bracket.offset, bracket.char, currentDepth))
                currentDepth++
            } else {
                // Closing bracket gets same depth as its opening bracket
                val depth = depthByPairId[bracket.pairId] ?: 0
                result.add(RainbowBracket(bracket.offset, bracket.char, depth))
                currentDepth = (currentDepth - 1).coerceAtLeast(0)
            }
        }

        return result.sortedBy { it.offset }
    }

    /**
     * Gets rainbow brackets for a specific line.
     * More efficient for per-line rendering.
     *
     * @param line The line number (0-indexed)
     * @return List of rainbow brackets on this line
     */
    fun getRainbowBracketsForLine(line: Int): List<RainbowBracket> {
        val lineStartOffset = document.getLineStartOffset(line)
        val lineEndOffset = if (line < document.lineCount - 1) {
            document.getLineStartOffset(line + 1)
        } else {
            document.length
        }

        return getRainbowBrackets().filter { bracket ->
            bracket.offset in lineStartOffset until lineEndOffset
        }
    }

    /**
     * Gets the color depth for a specific offset if it's a bracket.
     *
     * @param offset The document offset
     * @return The depth for coloring, or null if not a bracket
     */
    fun getDepthAtOffset(offset: Int): Int? {
        return getRainbowBrackets().find { it.offset == offset }?.depth
    }

    private data class BracketWithDepth(
        val offset: Int,
        val char: Char,
        val isOpening: Boolean,
        val pairId: Int
    )
}

/**
 * A bracket with its rainbow color depth.
 *
 * @property offset The offset in the document
 * @property char The bracket character
 * @property depth The nesting depth (0-based), use with getRainbowBracketColor(depth)
 */
data class RainbowBracket(
    val offset: Int,
    val char: Char,
    val depth: Int
)
