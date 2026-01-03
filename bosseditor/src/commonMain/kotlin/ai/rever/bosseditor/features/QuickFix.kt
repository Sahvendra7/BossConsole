package ai.rever.bosseditor.features

import ai.rever.bosseditor.core.EditorRange

/**
 * Represents a quick fix action that can be applied to resolve an issue.
 *
 * Quick fixes are shown in a lightbulb popup and can be triggered with Alt+Enter.
 * They can fix spelling errors, add imports, refactor code, etc.
 *
 * @property title Short title shown in the popup (e.g., "Change to 'receive'")
 * @property description Optional longer description of the fix
 * @property kind The type of quick fix (determines icon and grouping)
 * @property priority Lower values appear first in the list
 * @property range Optional range this fix applies to (for highlighting)
 */
data class QuickFix(
    val title: String,
    val description: String? = null,
    val kind: QuickFixKind,
    val priority: Int = 100,
    val range: EditorRange? = null,
    internal val actionHandler: () -> Unit
) {
    /**
     * Executes the quick fix action.
     */
    fun execute() = actionHandler()

    companion object {
        /**
         * Creates a spelling correction quick fix.
         */
        fun spellingCorrection(
            suggestion: String,
            range: EditorRange,
            replaceAction: (String) -> Unit
        ): QuickFix = QuickFix(
            title = "Change to '$suggestion'",
            kind = QuickFixKind.SPELLING,
            priority = 10,
            range = range,
            actionHandler = { replaceAction(suggestion) }
        )

        /**
         * Creates an "Add to dictionary" quick fix.
         */
        fun addToDictionary(
            word: String,
            addAction: (String) -> Unit
        ): QuickFix = QuickFix(
            title = "Add '$word' to dictionary",
            kind = QuickFixKind.SPELLING,
            priority = 20,
            actionHandler = { addAction(word) }
        )

        /**
         * Creates an import quick fix.
         */
        fun addImport(
            className: String,
            packageName: String,
            importAction: (String) -> Unit
        ): QuickFix = QuickFix(
            title = "Import '$packageName.$className'",
            description = "Add import statement",
            kind = QuickFixKind.IMPORT,
            priority = 5,
            actionHandler = { importAction("$packageName.$className") }
        )

        /**
         * Creates a refactoring quick fix.
         */
        fun refactoring(
            title: String,
            description: String? = null,
            range: EditorRange? = null,
            refactorAction: () -> Unit
        ): QuickFix = QuickFix(
            title = title,
            description = description,
            kind = QuickFixKind.REFACTOR,
            priority = 50,
            range = range,
            actionHandler = refactorAction
        )
    }
}

/**
 * Types of quick fixes, used for grouping and icon selection.
 */
enum class QuickFixKind {
    /** Spelling correction or dictionary action */
    SPELLING,

    /** Add missing import */
    IMPORT,

    /** Code refactoring (rename, extract, inline, etc.) */
    REFACTOR,

    /** Fix a diagnostic error */
    ERROR_FIX,

    /** Fix a diagnostic warning */
    WARNING_FIX,

    /** Intention action (not fixing an error) */
    INTENTION,

    /** Suppress warning/error */
    SUPPRESS,

    /** Other quick fix type */
    OTHER
}

/**
 * Represents a line that has available quick fixes.
 * Used to show the lightbulb icon in the gutter.
 *
 * @property line The line number (0-indexed)
 * @property fixes List of available quick fixes for this line
 * @property highestPriority The kind of the highest-priority fix (determines icon)
 */
data class QuickFixLine(
    val line: Int,
    val fixes: List<QuickFix>
) {
    /** The kind of the most important fix (for icon selection) */
    val primaryKind: QuickFixKind
        get() = fixes.minByOrNull { it.priority }?.kind ?: QuickFixKind.OTHER

    /** Whether this line has any error fixes */
    val hasErrorFix: Boolean
        get() = fixes.any { it.kind == QuickFixKind.ERROR_FIX }

    /** Whether this line has any warning fixes */
    val hasWarningFix: Boolean
        get() = fixes.any { it.kind == QuickFixKind.WARNING_FIX }

    /** Whether this line has spelling fixes */
    val hasSpellingFix: Boolean
        get() = fixes.any { it.kind == QuickFixKind.SPELLING }
}

/**
 * Manages quick fixes for the editor.
 * Provides efficient lookup of fixes by line and position.
 */
class QuickFixManager {
    private val fixesByLine = mutableMapOf<Int, MutableList<QuickFix>>()

    /**
     * Adds a quick fix for a specific line.
     */
    fun addFix(line: Int, fix: QuickFix) {
        fixesByLine.getOrPut(line) { mutableListOf() }.add(fix)
    }

    /**
     * Adds multiple quick fixes for a specific line.
     */
    fun addFixes(line: Int, fixes: List<QuickFix>) {
        if (fixes.isEmpty()) return
        fixesByLine.getOrPut(line) { mutableListOf() }.addAll(fixes)
    }

    /**
     * Sets all fixes for a line, replacing existing ones.
     */
    fun setFixes(line: Int, fixes: List<QuickFix>) {
        if (fixes.isEmpty()) {
            fixesByLine.remove(line)
        } else {
            fixesByLine[line] = fixes.toMutableList()
        }
    }

    /**
     * Clears all quick fixes for a specific line.
     */
    fun clearLine(line: Int) {
        fixesByLine.remove(line)
    }

    /**
     * Clears all quick fixes.
     */
    fun clear() {
        fixesByLine.clear()
    }

    /**
     * Gets quick fixes for a specific line.
     */
    fun getFixesForLine(line: Int): List<QuickFix> {
        return fixesByLine[line]?.sortedBy { it.priority } ?: emptyList()
    }

    /**
     * Checks if a line has any quick fixes.
     */
    fun hasFixesOnLine(line: Int): Boolean {
        return fixesByLine.containsKey(line) && fixesByLine[line]?.isNotEmpty() == true
    }

    /**
     * Gets all lines that have quick fixes.
     */
    fun getLinesWithFixes(): Set<Int> = fixesByLine.keys.toSet()

    /**
     * Gets quick fix lines for rendering lightbulb icons.
     */
    fun getQuickFixLines(): List<QuickFixLine> {
        return fixesByLine
            .filter { it.value.isNotEmpty() }
            .map { (line, fixes) ->
                QuickFixLine(line, fixes.sortedBy { it.priority })
            }
    }

    /**
     * Gets the total count of available quick fixes.
     */
    fun totalFixCount(): Int = fixesByLine.values.sumOf { it.size }
}
