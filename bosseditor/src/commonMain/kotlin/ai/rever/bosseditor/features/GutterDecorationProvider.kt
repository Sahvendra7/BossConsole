package ai.rever.bosseditor.features

/**
 * Provider interface for custom gutter decorations.
 *
 * Plugins can implement this interface to provide dynamic gutter icons
 * based on the visible line range. The editor will query providers
 * whenever the visible range changes.
 *
 * ## Usage
 * ```kotlin
 * class CoverageGutterProvider(private val coverage: Map<Int, Boolean>) : GutterDecorationProvider {
 *     override suspend fun getDecorations(startLine: Int, endLine: Int): List<GutterIcon> {
 *         return coverage.entries
 *             .filter { it.key in startLine..endLine }
 *             .map { (line, covered) ->
 *                 GutterIcon.custom(
 *                     line = line,
 *                     color = if (covered) Color.Green else Color.Red,
 *                     shape = GutterIconShape.FILLED_CIRCLE,
 *                     tooltip = if (covered) "Covered" else "Not covered"
 *                 )
 *             }
 *     }
 * }
 * ```
 */
interface GutterDecorationProvider {
    /**
     * Gets gutter decorations for the given line range.
     *
     * Called by the editor when the visible line range changes.
     * Implementations should return decorations only for lines within the range.
     *
     * **Note:** Exceptions thrown by this method are silently caught by the editor
     * to prevent a failing provider from breaking the UI. Implement your own error
     * handling/logging if you need to track failures.
     *
     * @param startLine The first visible line (0-indexed, inclusive)
     * @param endLine The last visible line (0-indexed, inclusive)
     * @return List of gutter icons to display
     */
    suspend fun getDecorations(startLine: Int, endLine: Int): List<GutterIcon>
}
