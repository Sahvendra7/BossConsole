package ai.rever.bosseditor.fold

/**
 * Represents a foldable region in the editor.
 *
 * A fold region spans multiple lines and can be collapsed to show
 * a placeholder text instead of the full content.
 *
 * ## Placeholder Examples (IntelliJ-style)
 * - Imports: `import ...`
 * - Functions: `fun name(...) { ... }`
 * - Classes: `class Name { ... }`
 * - Comments: `/* ... */` or `/** ... */`
 * - Lambda: `{ ... }`
 *
 * @property startLine The first line of the fold region (0-indexed)
 * @property endLine The last line of the fold region (0-indexed, inclusive)
 * @property type The type of fold (for styling and behavior)
 * @property placeholder The text to show when collapsed
 * @property isCollapsed Whether the region is currently collapsed
 */
data class FoldRegion(
    val startLine: Int,
    val endLine: Int,
    val type: FoldType,
    val placeholder: String,
    val isCollapsed: Boolean = false
) {
    /**
     * The number of lines in this region.
     */
    val lineCount: Int
        get() = endLine - startLine + 1

    /**
     * The number of lines hidden when collapsed (all but the first).
     */
    val hiddenLineCount: Int
        get() = if (isCollapsed) lineCount - 1 else 0

    /**
     * Whether this region contains the given line.
     */
    fun containsLine(line: Int): Boolean = line in startLine..endLine

    /**
     * Whether this region's interior (excluding first line) contains the given line.
     */
    fun interiorContainsLine(line: Int): Boolean = line in (startLine + 1)..endLine

    /**
     * Whether this region overlaps with another region.
     */
    fun overlaps(other: FoldRegion): Boolean {
        return startLine <= other.endLine && endLine >= other.startLine
    }

    /**
     * Whether this region is nested inside another region.
     */
    fun isNestedIn(other: FoldRegion): Boolean {
        return startLine >= other.startLine && endLine <= other.endLine && this != other
    }

    /**
     * Creates a copy with the collapsed state toggled.
     */
    fun toggle(): FoldRegion = copy(isCollapsed = !isCollapsed)

    /**
     * Creates a collapsed copy.
     */
    fun collapse(): FoldRegion = copy(isCollapsed = true)

    /**
     * Creates an expanded copy.
     */
    fun expand(): FoldRegion = copy(isCollapsed = false)

    companion object {
        /**
         * Creates a fold region for imports.
         * Imports are collapsed by default.
         */
        fun forImports(startLine: Int, endLine: Int, collapsed: Boolean = true): FoldRegion {
            return FoldRegion(
                startLine = startLine,
                endLine = endLine,
                type = FoldType.IMPORTS,
                placeholder = "import ...",
                isCollapsed = collapsed
            )
        }

        /**
         * Creates a fold region for a code block (class, function, etc.).
         */
        fun forCodeBlock(
            startLine: Int,
            endLine: Int,
            firstLineText: String,
            collapsed: Boolean = false
        ): FoldRegion {
            val placeholder = generateCodeBlockPlaceholder(firstLineText)
            return FoldRegion(
                startLine = startLine,
                endLine = endLine,
                type = FoldType.CODE,
                placeholder = placeholder,
                isCollapsed = collapsed
            )
        }

        /**
         * Creates a fold region for a comment.
         */
        fun forComment(
            startLine: Int,
            endLine: Int,
            isDocComment: Boolean = false,
            collapsed: Boolean = false
        ): FoldRegion {
            val placeholder = if (isDocComment) "/** ... */" else "/* ... */"
            return FoldRegion(
                startLine = startLine,
                endLine = endLine,
                type = if (isDocComment) FoldType.DOC_COMMENT else FoldType.COMMENT,
                placeholder = placeholder,
                isCollapsed = collapsed
            )
        }

        /**
         * Generates a simple placeholder for a code block.
         */
        @Suppress("UNUSED_PARAMETER")
        private fun generateCodeBlockPlaceholder(firstLineText: String): String {
            // Simple placeholder for all code blocks
            return "{ ... }"
        }
    }
}

/**
 * Types of foldable regions.
 */
enum class FoldType {
    /**
     * Import statements block.
     */
    IMPORTS,

    /**
     * Code block (class, function, if, when, etc.).
     */
    CODE,

    /**
     * Regular block comment.
     */
    COMMENT,

    /**
     * Documentation comment.
     */
    DOC_COMMENT,

    /**
     * String literal (multi-line).
     */
    STRING,

    /**
     * Custom/user-defined region.
     */
    CUSTOM
}

/**
 * Result of fold detection for a document.
 */
data class FoldParseResult(
    val regions: List<FoldRegion>,
    val parseTimeMs: Long = 0
)
