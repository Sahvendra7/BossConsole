package ai.rever.bosseditor.fold

import ai.rever.bosseditor.core.DocumentChange
import ai.rever.bosseditor.core.DocumentListener
import ai.rever.bosseditor.core.EditorDocument

/**
 * Manages fold regions and their state for an editor document.
 *
 * The FoldingModel:
 * - Stores all detected fold regions
 * - Tracks collapsed/expanded state
 * - Updates regions when document changes
 * - Provides queries for fold state at positions
 *
 * ## Usage
 * ```kotlin
 * val foldingModel = FoldingModel(document)
 * foldingModel.setFoldParser(KotlinFoldParser())
 *
 * // Toggle fold at line
 * foldingModel.toggleFoldAt(lineNumber)
 *
 * // Check if line is visible
 * val isVisible = foldingModel.isLineVisible(lineNumber)
 * ```
 */
class FoldingModel(
    private val document: EditorDocument
) : DocumentListener {

    private val regions = mutableListOf<FoldRegion>()
    private var foldParser: FoldParser? = null
    private var needsReparse = true

    // Listeners for fold state changes
    private val listeners = mutableListOf<FoldingListener>()

    init {
        document.addDocumentListener(this)
    }

    /**
     * Sets the fold parser to use for detecting fold regions.
     */
    fun setFoldParser(parser: FoldParser?) {
        foldParser = parser
        needsReparse = true
        reparseIfNeeded()
    }

    /**
     * Gets all fold regions (both collapsed and expanded).
     */
    fun getAllRegions(): List<FoldRegion> {
        reparseIfNeeded()
        return regions.toList()
    }

    /**
     * Gets all collapsed fold regions.
     */
    fun getCollapsedRegions(): List<FoldRegion> {
        reparseIfNeeded()
        return regions.filter { it.isCollapsed }
    }

    /**
     * Gets the fold region at the given line, if any.
     */
    fun getFoldAt(line: Int): FoldRegion? {
        reparseIfNeeded()
        return regions.find { it.startLine == line }
    }

    /**
     * Gets all fold regions that contain the given line.
     */
    fun getFoldsContaining(line: Int): List<FoldRegion> {
        reparseIfNeeded()
        return regions.filter { it.containsLine(line) }
    }

    /**
     * Checks if a line is visible (not hidden by a collapsed fold).
     */
    fun isLineVisible(line: Int): Boolean {
        reparseIfNeeded()
        // A line is hidden if it's in the interior of any collapsed region
        return !regions.any { it.isCollapsed && it.interiorContainsLine(line) }
    }

    /**
     * Checks if a line is the start of a fold region.
     */
    fun isFoldStart(line: Int): Boolean {
        reparseIfNeeded()
        return regions.any { it.startLine == line }
    }

    /**
     * Checks if a line has a collapsed fold.
     */
    fun isCollapsedAt(line: Int): Boolean {
        reparseIfNeeded()
        return regions.any { it.startLine == line && it.isCollapsed }
    }

    /**
     * Toggles the fold state at the given line.
     * Returns true if a fold was toggled.
     */
    fun toggleFoldAt(line: Int): Boolean {
        reparseIfNeeded()
        val index = regions.indexOfFirst { it.startLine == line }
        if (index >= 0) {
            val oldRegion = regions[index]
            val newRegion = oldRegion.toggle()
            regions[index] = newRegion

            // Notify listeners
            if (newRegion.isCollapsed) {
                notifyFoldCollapsed(newRegion)
            } else {
                notifyFoldExpanded(newRegion)
            }
            return true
        }
        return false
    }

    /**
     * Collapses the fold at the given line.
     * Returns true if the fold was collapsed.
     */
    fun collapseFoldAt(line: Int): Boolean {
        reparseIfNeeded()
        val index = regions.indexOfFirst { it.startLine == line && !it.isCollapsed }
        if (index >= 0) {
            val newRegion = regions[index].collapse()
            regions[index] = newRegion
            notifyFoldCollapsed(newRegion)
            return true
        }
        return false
    }

    /**
     * Expands the fold at the given line.
     * Returns true if the fold was expanded.
     */
    fun expandFoldAt(line: Int): Boolean {
        reparseIfNeeded()
        val index = regions.indexOfFirst { it.startLine == line && it.isCollapsed }
        if (index >= 0) {
            val newRegion = regions[index].expand()
            regions[index] = newRegion
            notifyFoldExpanded(newRegion)
            return true
        }
        return false
    }

    /**
     * Collapses all folds.
     */
    fun collapseAll() {
        reparseIfNeeded()
        for (i in regions.indices) {
            if (!regions[i].isCollapsed) {
                regions[i] = regions[i].collapse()
            }
        }
        notifyFoldsChanged()
    }

    /**
     * Expands all folds.
     */
    fun expandAll() {
        reparseIfNeeded()
        for (i in regions.indices) {
            if (regions[i].isCollapsed) {
                regions[i] = regions[i].expand()
            }
        }
        notifyFoldsChanged()
    }

    /**
     * Collapses all folds of a specific type.
     */
    fun collapseAllOfType(type: FoldType) {
        reparseIfNeeded()
        var changed = false
        for (i in regions.indices) {
            if (regions[i].type == type && !regions[i].isCollapsed) {
                regions[i] = regions[i].collapse()
                changed = true
            }
        }
        if (changed) {
            notifyFoldsChanged()
        }
    }

    /**
     * Expands folds to make a line visible.
     * Useful when navigating to a line that might be hidden.
     */
    fun expandToReveal(line: Int) {
        reparseIfNeeded()
        var changed = false
        for (i in regions.indices) {
            if (regions[i].isCollapsed && regions[i].interiorContainsLine(line)) {
                regions[i] = regions[i].expand()
                changed = true
            }
        }
        if (changed) {
            notifyFoldsChanged()
        }
    }

    /**
     * Gets the placeholder text for a collapsed fold at the given line.
     */
    fun getPlaceholderAt(line: Int): String? {
        reparseIfNeeded()
        return regions.find { it.startLine == line && it.isCollapsed }?.placeholder
    }

    /**
     * Invalidates the fold regions, causing a reparse on next access.
     */
    fun invalidate() {
        needsReparse = true
    }

    /**
     * Forces an immediate reparse of fold regions.
     */
    fun reparse() {
        needsReparse = true
        reparseIfNeeded()
    }

    /**
     * Adds a listener for fold state changes.
     */
    fun addFoldingListener(listener: FoldingListener) {
        listeners.add(listener)
    }

    /**
     * Removes a fold state listener.
     */
    fun removeFoldingListener(listener: FoldingListener) {
        listeners.remove(listener)
    }

    // DocumentListener implementation
    override fun documentChanged(change: DocumentChange) {
        // Document changed, need to reparse
        // Also need to adjust line numbers for existing collapsed regions

        val startPos = document.offsetToPosition(change.offset)
        val startLine = startPos.line
        val oldLineCount = change.oldText.count { it == '\n' }
        val newLineCount = change.newText.count { it == '\n' }
        val lineDelta = newLineCount - oldLineCount

        if (lineDelta != 0) {
            // Adjust line numbers for regions after the change
            adjustRegionsAfterChange(startLine, lineDelta)
        }

        // Mark for reparse
        needsReparse = true
    }

    /**
     * Adjusts fold region line numbers after a document change.
     */
    private fun adjustRegionsAfterChange(startLine: Int, lineDelta: Int) {
        // Save collapsed states before adjustment
        val collapsedStates = regions.associate { it.startLine to it.isCollapsed }

        // We'll need to reparse anyway, but preserve collapsed states
        // For now, just mark for reparse
        needsReparse = true
    }

    /**
     * Reparses fold regions if needed.
     */
    private fun reparseIfNeeded() {
        if (!needsReparse) return
        needsReparse = false

        val parser = foldParser ?: return

        // Save current collapsed states
        val collapsedStates = regions.associate { it.startLine to it.isCollapsed }

        // Parse new regions
        val text = document.getText()
        val result = parser.parse(text)

        // Restore collapsed states where regions still exist
        regions.clear()
        for (region in result.regions) {
            val wasCollapsed = collapsedStates[region.startLine] ?: false
            regions.add(if (wasCollapsed) region.collapse() else region)
        }

        // Sort by start line
        regions.sortBy { it.startLine }
    }

    private fun notifyFoldCollapsed(region: FoldRegion) {
        listeners.forEach { it.foldCollapsed(region) }
    }

    private fun notifyFoldExpanded(region: FoldRegion) {
        listeners.forEach { it.foldExpanded(region) }
    }

    private fun notifyFoldsChanged() {
        listeners.forEach { it.foldsChanged() }
    }

    /**
     * Disposes resources.
     */
    fun dispose() {
        document.removeDocumentListener(this)
        regions.clear()
        listeners.clear()
    }
}

/**
 * Listener for fold state changes.
 */
interface FoldingListener {
    /**
     * Called when a fold region is collapsed.
     */
    fun foldCollapsed(region: FoldRegion) {}

    /**
     * Called when a fold region is expanded.
     */
    fun foldExpanded(region: FoldRegion) {}

    /**
     * Called when multiple folds changed (e.g., collapse all).
     */
    fun foldsChanged() {}
}

/**
 * Interface for fold region parsers.
 */
interface FoldParser {
    /**
     * Parses the document text and returns detected fold regions.
     */
    fun parse(text: String): FoldParseResult

    /**
     * The language ID this parser handles.
     */
    val languageId: String
}
