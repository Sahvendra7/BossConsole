package ai.rever.bosseditor.features

/**
 * Types of icons that can appear in the editor gutter.
 */
enum class GutterIconType {
    /** Run/execute icon (green play button) */
    RUN,

    /** Debug icon */
    DEBUG,

    /** Breakpoint marker */
    BREAKPOINT,

    /** Breakpoint disabled */
    BREAKPOINT_DISABLED,

    /** Error indicator */
    ERROR,

    /** Warning indicator */
    WARNING,

    /** Info indicator */
    INFO,

    /** Hint indicator */
    HINT,

    /** Fold region start */
    FOLD_START,

    /** Fold region end */
    FOLD_END,

    /** Bookmark marker */
    BOOKMARK,

    /** Override/implement indicator */
    OVERRIDE,

    /** Recursive call indicator */
    RECURSIVE
}

/**
 * Represents an icon in the editor gutter.
 *
 * Gutter icons appear in the area between line numbers and code.
 * They can be clicked to perform actions (e.g., run, toggle breakpoint).
 *
 * @property line The line number where the icon appears (0-indexed)
 * @property type The type of icon to display
 * @property tooltip Tooltip text shown on hover
 * @property priority Priority for ordering when multiple icons on same line (lower = more important)
 * @property action Optional action identifier for click handling
 */
data class GutterIcon(
    val line: Int,
    val type: GutterIconType,
    val tooltip: String? = null,
    val priority: Int = 100,
    val action: String? = null
) {
    companion object {
        /**
         * Creates a run icon for executable code.
         */
        fun run(line: Int, tooltip: String = "Run"): GutterIcon =
            GutterIcon(line, GutterIconType.RUN, tooltip, priority = 10, action = "run")

        /**
         * Creates a debug icon.
         */
        fun debug(line: Int, tooltip: String = "Debug"): GutterIcon =
            GutterIcon(line, GutterIconType.DEBUG, tooltip, priority = 15, action = "debug")

        /**
         * Creates a breakpoint icon.
         */
        fun breakpoint(line: Int, enabled: Boolean = true): GutterIcon =
            GutterIcon(
                line,
                if (enabled) GutterIconType.BREAKPOINT else GutterIconType.BREAKPOINT_DISABLED,
                if (enabled) "Breakpoint" else "Disabled breakpoint",
                priority = 5,
                action = "toggleBreakpoint"
            )

        /**
         * Creates a diagnostic severity icon.
         */
        fun fromDiagnosticSeverity(line: Int, severity: DiagnosticSeverity, message: String): GutterIcon {
            val type = when (severity) {
                DiagnosticSeverity.ERROR -> GutterIconType.ERROR
                DiagnosticSeverity.WARNING -> GutterIconType.WARNING
                DiagnosticSeverity.INFO -> GutterIconType.INFO
                DiagnosticSeverity.HINT -> GutterIconType.HINT
            }
            val priority = when (severity) {
                DiagnosticSeverity.ERROR -> 1
                DiagnosticSeverity.WARNING -> 2
                DiagnosticSeverity.INFO -> 3
                DiagnosticSeverity.HINT -> 4
            }
            return GutterIcon(line, type, message, priority)
        }

        /**
         * Creates a bookmark icon.
         */
        fun bookmark(line: Int, label: String? = null): GutterIcon =
            GutterIcon(
                line,
                GutterIconType.BOOKMARK,
                label ?: "Bookmark",
                priority = 20,
                action = "toggleBookmark"
            )
    }
}

/**
 * Manages gutter icons for the editor.
 * Handles icon display, click events, and priority ordering.
 */
class GutterIconManager {
    private val icons = mutableListOf<GutterIcon>()
    private var iconsByLine: Map<Int, List<GutterIcon>> = emptyMap()

    /** Callback for when a gutter icon is clicked */
    var onIconClick: ((GutterIcon) -> Unit)? = null

    /**
     * Sets the gutter icons, replacing any existing ones.
     */
    fun setIcons(newIcons: List<GutterIcon>) {
        icons.clear()
        icons.addAll(newIcons)
        rebuildIndex()
    }

    /**
     * Adds a single gutter icon.
     */
    fun addIcon(icon: GutterIcon) {
        icons.add(icon)
        rebuildIndex()
    }

    /**
     * Removes all icons of a specific type.
     */
    fun removeIconsOfType(type: GutterIconType) {
        icons.removeAll { it.type == type }
        rebuildIndex()
    }

    /**
     * Removes icon at a specific line.
     */
    fun removeIconAtLine(line: Int, type: GutterIconType? = null) {
        if (type != null) {
            icons.removeAll { it.line == line && it.type == type }
        } else {
            icons.removeAll { it.line == line }
        }
        rebuildIndex()
    }

    /**
     * Removes all icons.
     */
    fun clear() {
        icons.clear()
        iconsByLine = emptyMap()
    }

    /**
     * Gets all icons.
     */
    fun getAllIcons(): List<GutterIcon> = icons.toList()

    /**
     * Gets icons for a specific line, sorted by priority.
     */
    fun getIconsForLine(line: Int): List<GutterIcon> {
        return iconsByLine[line]?.sortedBy { it.priority } ?: emptyList()
    }

    /**
     * Gets the highest priority icon for a line (the one to display).
     */
    fun getPrimaryIconForLine(line: Int): GutterIcon? {
        return getIconsForLine(line).firstOrNull()
    }

    /**
     * Handles a click on the gutter at the specified line.
     */
    fun handleClick(line: Int) {
        val icon = getPrimaryIconForLine(line)
        if (icon != null) {
            onIconClick?.invoke(icon)
        }
    }

    /**
     * Toggles a breakpoint at the specified line.
     */
    fun toggleBreakpoint(line: Int): Boolean {
        val existing = icons.find { it.line == line && it.type == GutterIconType.BREAKPOINT }
        return if (existing != null) {
            icons.remove(existing)
            rebuildIndex()
            false // Breakpoint removed
        } else {
            icons.add(GutterIcon.breakpoint(line))
            rebuildIndex()
            true // Breakpoint added
        }
    }

    /**
     * Checks if a line has a breakpoint.
     */
    fun hasBreakpoint(line: Int): Boolean {
        return icons.any { it.line == line && it.type == GutterIconType.BREAKPOINT }
    }

    /**
     * Gets all breakpoint lines.
     */
    fun getBreakpointLines(): List<Int> {
        return icons.filter { it.type == GutterIconType.BREAKPOINT }.map { it.line }
    }

    private fun rebuildIndex() {
        iconsByLine = icons.groupBy { it.line }
    }
}
