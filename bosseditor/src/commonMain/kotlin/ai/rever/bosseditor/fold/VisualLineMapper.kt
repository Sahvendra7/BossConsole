package ai.rever.bosseditor.fold

/**
 * Maps between visual lines (what's displayed) and document lines.
 *
 * When folds are collapsed, visual lines differ from document lines:
 * - Visual line 0 = Document line 0
 * - If lines 5-10 are folded, visual line 5 shows the fold placeholder,
 *   and visual line 6 shows document line 11.
 *
 * This class provides efficient bidirectional mapping and visibility queries.
 *
 * ## Usage
 * ```kotlin
 * val mapper = VisualLineMapper(documentLineCount, collapsedFolds)
 *
 * // Convert visual to document line
 * val docLine = mapper.visualToDocument(visualLine)
 *
 * // Convert document to visual line
 * val visLine = mapper.documentToVisual(documentLine)
 *
 * // Check visibility
 * val isVisible = mapper.isDocumentLineVisible(documentLine)
 * ```
 */
class VisualLineMapper private constructor(
    private val documentLineCount: Int,
    private val collapsedFolds: List<FoldRegion>,
    private val visualLineCount: Int,
    private val visualToDocMap: IntArray,
    private val docToVisualMap: IntArray
) {
    /**
     * Gets the total number of visual lines.
     */
    val visibleLineCount: Int
        get() = visualLineCount

    /**
     * Converts a visual line number to a document line number.
     *
     * @param visualLine The visual line number (0-indexed)
     * @return The corresponding document line number, or -1 if invalid
     */
    fun visualToDocument(visualLine: Int): Int {
        if (visualLine < 0 || visualLine >= visualLineCount) {
            return -1
        }
        return visualToDocMap[visualLine]
    }

    /**
     * Converts a document line number to a visual line number.
     *
     * @param documentLine The document line number (0-indexed)
     * @return The corresponding visual line number, or -1 if the line is hidden
     */
    fun documentToVisual(documentLine: Int): Int {
        if (documentLine < 0 || documentLine >= documentLineCount) {
            return -1
        }
        return docToVisualMap[documentLine]
    }

    /**
     * Checks if a document line is visible (not hidden by a fold).
     */
    fun isDocumentLineVisible(documentLine: Int): Boolean {
        if (documentLine < 0 || documentLine >= documentLineCount) {
            return false
        }
        return docToVisualMap[documentLine] >= 0
    }

    /**
     * Checks if a visual line is the start of a collapsed fold.
     */
    fun isVisualLineFoldStart(visualLine: Int): Boolean {
        if (visualLine < 0 || visualLine >= visualLineCount) {
            return false
        }
        val docLine = visualToDocMap[visualLine]
        return collapsedFolds.any { it.startLine == docLine }
    }

    /**
     * Gets the fold region at a visual line, if it's collapsed.
     */
    fun getCollapsedFoldAt(visualLine: Int): FoldRegion? {
        if (visualLine < 0 || visualLine >= visualLineCount) {
            return null
        }
        val docLine = visualToDocMap[visualLine]
        return collapsedFolds.find { it.startLine == docLine }
    }

    /**
     * Gets the range of visible visual lines for a viewport.
     */
    fun getVisibleVisualRange(firstVisualLine: Int, lastVisualLine: Int): IntRange {
        val first = firstVisualLine.coerceIn(0, visualLineCount - 1)
        val last = lastVisualLine.coerceIn(0, visualLineCount - 1)
        return first..last
    }

    /**
     * Iterates over visible lines in a range.
     * Callback receives (visualLine, documentLine, isCollapsedFoldStart, foldRegion?).
     */
    fun forEachVisibleLine(
        firstVisualLine: Int,
        lastVisualLine: Int,
        action: (visualLine: Int, documentLine: Int, isFoldStart: Boolean, fold: FoldRegion?) -> Unit
    ) {
        val range = getVisibleVisualRange(firstVisualLine, lastVisualLine)
        for (visualLine in range) {
            val docLine = visualToDocMap[visualLine]
            val fold = collapsedFolds.find { it.startLine == docLine }
            action(visualLine, docLine, fold != null, fold)
        }
    }

    companion object {
        /**
         * Creates a VisualLineMapper with no folds (1:1 mapping).
         */
        fun noFolds(documentLineCount: Int): VisualLineMapper {
            val map = IntArray(documentLineCount) { it }
            return VisualLineMapper(
                documentLineCount = documentLineCount,
                collapsedFolds = emptyList(),
                visualLineCount = documentLineCount,
                visualToDocMap = map,
                docToVisualMap = map.copyOf()
            )
        }

        /**
         * Creates a VisualLineMapper from collapsed fold regions.
         */
        fun create(documentLineCount: Int, collapsedFolds: List<FoldRegion>): VisualLineMapper {
            if (collapsedFolds.isEmpty()) {
                return noFolds(documentLineCount)
            }

            // Sort folds by start line
            val sortedFolds = collapsedFolds.sortedBy { it.startLine }

            // Calculate which document lines are hidden
            val hiddenLines = mutableSetOf<Int>()
            for (fold in sortedFolds) {
                // Interior lines are hidden (first line shows placeholder)
                for (line in (fold.startLine + 1)..fold.endLine) {
                    hiddenLines.add(line)
                }
            }

            // Build visual to document mapping
            val visualToDoc = mutableListOf<Int>()
            for (docLine in 0 until documentLineCount) {
                if (docLine !in hiddenLines) {
                    visualToDoc.add(docLine)
                }
            }

            // Build document to visual mapping
            val docToVisual = IntArray(documentLineCount) { -1 } // -1 means hidden
            for ((visualLine, docLine) in visualToDoc.withIndex()) {
                docToVisual[docLine] = visualLine
            }

            return VisualLineMapper(
                documentLineCount = documentLineCount,
                collapsedFolds = sortedFolds,
                visualLineCount = visualToDoc.size,
                visualToDocMap = visualToDoc.toIntArray(),
                docToVisualMap = docToVisual
            )
        }
    }
}

/**
 * Extension function to create a mapper from a FoldingModel.
 */
fun FoldingModel.createVisualLineMapper(documentLineCount: Int): VisualLineMapper {
    return VisualLineMapper.create(documentLineCount, getCollapsedRegions())
}
