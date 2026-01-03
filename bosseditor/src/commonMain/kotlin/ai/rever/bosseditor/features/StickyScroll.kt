package ai.rever.bosseditor.features

/**
 * Represents a sticky header line that pins to the top of the editor.
 *
 * Sticky scroll shows context headers (class/method definitions) when
 * scrolling through code, like VS Code's sticky scroll feature.
 *
 * @property line The document line number (0-indexed)
 * @property text The text content to display
 * @property depth The nesting depth (0 = outermost, 1 = nested, etc.)
 * @property kind The type of scope (class, method, etc.)
 */
data class StickyHeader(
    val line: Int,
    val text: String,
    val depth: Int,
    val kind: StickyHeaderKind = StickyHeaderKind.OTHER
) {
    companion object {
        /**
         * Creates a class header.
         */
        fun classHeader(line: Int, text: String, depth: Int): StickyHeader = StickyHeader(
            line = line,
            text = text,
            depth = depth,
            kind = StickyHeaderKind.CLASS
        )

        /**
         * Creates a function/method header.
         */
        fun functionHeader(line: Int, text: String, depth: Int): StickyHeader = StickyHeader(
            line = line,
            text = text,
            depth = depth,
            kind = StickyHeaderKind.FUNCTION
        )
    }
}

/**
 * Types of sticky headers for styling.
 */
enum class StickyHeaderKind {
    /** Class, struct, or interface definition */
    CLASS,

    /** Function or method definition */
    FUNCTION,

    /** Control structure (if, for, when, etc.) */
    CONTROL,

    /** Lambda or closure */
    LAMBDA,

    /** Other block type */
    OTHER
}

/**
 * Configuration for sticky scroll behavior.
 *
 * @property maxHeaders Maximum number of sticky headers to show (default 3)
 * @property minLinesVisible Minimum lines of content visible before showing header
 * @property showLineNumbers Whether to show line numbers in sticky headers
 * @property enabled Whether sticky scroll is enabled
 */
data class StickyScrollConfig(
    val maxHeaders: Int = 3,
    val minLinesVisible: Int = 2,
    val showLineNumbers: Boolean = true,
    val enabled: Boolean = true
)

/**
 * Manages sticky scroll state for the editor.
 *
 * Computes which headers should be pinned based on the current scroll
 * position and code structure (fold regions).
 */
class StickyScrollState(
    private val maxHeaders: Int = 3
) {
    private var headers: List<StickyHeader> = emptyList()
    private var lastFirstVisibleLine: Int = -1

    /**
     * Gets the current sticky headers.
     */
    fun getHeaders(): List<StickyHeader> = headers

    /**
     * Returns true if there are sticky headers to show.
     */
    fun hasHeaders(): Boolean = headers.isNotEmpty()

    /**
     * Gets the total height of sticky headers in lines.
     */
    fun headerCount(): Int = headers.size

    /**
     * Updates sticky headers based on scroll position and scope regions.
     *
     * @param firstVisibleLine The first visible document line (0-indexed)
     * @param scopes List of scope regions (class, method definitions)
     * @param getLineText Function to get line text
     */
    fun updateHeaders(
        firstVisibleLine: Int,
        scopes: List<ScopeRegion>,
        getLineText: (Int) -> String
    ) {
        // Skip if scroll position hasn't changed
        if (firstVisibleLine == lastFirstVisibleLine) return
        lastFirstVisibleLine = firstVisibleLine

        // Find all scopes that contain the first visible line
        val containingScopes = scopes.filter { scope ->
            scope.startLine < firstVisibleLine && scope.endLine > firstVisibleLine
        }.sortedBy { it.startLine }

        // Take the innermost scopes up to maxHeaders
        val relevantScopes = containingScopes.takeLast(maxHeaders)

        // Create headers from scopes
        headers = relevantScopes.mapIndexed { index, scope ->
            StickyHeader(
                line = scope.startLine,
                text = getLineText(scope.startLine).trimEnd(),
                depth = index,
                kind = scope.kind.toStickyHeaderKind()
            )
        }
    }

    /**
     * Clears all sticky headers.
     */
    fun clear() {
        headers = emptyList()
        lastFirstVisibleLine = -1
    }
}

/**
 * Represents a scope region for sticky scroll computation.
 * Can be derived from FoldRegions or PSI.
 *
 * @property startLine First line of the scope (the header line)
 * @property endLine Last line of the scope (closing brace)
 * @property kind Type of scope
 */
data class ScopeRegion(
    val startLine: Int,
    val endLine: Int,
    val kind: ScopeKind
) {
    /** Whether a line is inside this scope (excluding the header line itself) */
    fun containsLine(line: Int): Boolean = line > startLine && line < endLine

    /** Whether this scope is still visible at the given scroll position */
    fun isRelevantAt(firstVisibleLine: Int): Boolean =
        startLine < firstVisibleLine && endLine > firstVisibleLine
}

/**
 * Types of code scopes.
 */
enum class ScopeKind {
    /** Class, struct, object */
    CLASS,

    /** Interface */
    INTERFACE,

    /** Function, method */
    FUNCTION,

    /** Property getter/setter */
    PROPERTY,

    /** Lambda expression */
    LAMBDA,

    /** If/else statement */
    IF,

    /** For/while loop */
    LOOP,

    /** When expression */
    WHEN,

    /** Try/catch block */
    TRY,

    /** General block */
    BLOCK,

    /** Other scope type */
    OTHER;

    /**
     * Converts to StickyHeaderKind for styling.
     */
    fun toStickyHeaderKind(): StickyHeaderKind = when (this) {
        CLASS, INTERFACE -> StickyHeaderKind.CLASS
        FUNCTION, PROPERTY -> StickyHeaderKind.FUNCTION
        LAMBDA -> StickyHeaderKind.LAMBDA
        IF, LOOP, WHEN, TRY -> StickyHeaderKind.CONTROL
        BLOCK, OTHER -> StickyHeaderKind.OTHER
    }
}

/**
 * Provider interface for extracting scope regions from code.
 * Implemented in desktop target with PSI/fold region access.
 */
interface ScopeRegionProvider {
    /**
     * Gets all scope regions for the current document.
     */
    fun getScopeRegions(): List<ScopeRegion>

    /**
     * Gets scope regions that contain the given line.
     */
    fun getScopesContaining(line: Int): List<ScopeRegion>
}
