package ai.rever.bosseditor.features

import ai.rever.bosseditor.core.EditorPosition

/**
 * Represents a single breadcrumb item in the navigation bar.
 *
 * Breadcrumbs show the current scope hierarchy (File > Class > Method).
 * Clicking a breadcrumb navigates to that scope.
 *
 * @property name The display name (e.g., "MyClass", "myMethod")
 * @property kind The type of scope element
 * @property position The position in the document to navigate to
 * @property icon Optional icon identifier for this kind
 */
data class BreadcrumbItem(
    val name: String,
    val kind: BreadcrumbKind,
    val position: EditorPosition,
    val icon: String? = null
) {
    /** Line number (0-indexed) */
    val line: Int get() = position.line

    /** Column number (0-indexed) */
    val column: Int get() = position.column

    companion object {
        /**
         * Creates a file breadcrumb.
         */
        fun file(filename: String): BreadcrumbItem = BreadcrumbItem(
            name = filename,
            kind = BreadcrumbKind.FILE,
            position = EditorPosition(0, 0)
        )

        /**
         * Creates a class breadcrumb.
         */
        fun classItem(name: String, line: Int, column: Int = 0): BreadcrumbItem = BreadcrumbItem(
            name = name,
            kind = BreadcrumbKind.CLASS,
            position = EditorPosition(line, column)
        )

        /**
         * Creates a function/method breadcrumb.
         */
        fun function(name: String, line: Int, column: Int = 0): BreadcrumbItem = BreadcrumbItem(
            name = name,
            kind = BreadcrumbKind.FUNCTION,
            position = EditorPosition(line, column)
        )

        /**
         * Creates a property breadcrumb.
         */
        fun property(name: String, line: Int, column: Int = 0): BreadcrumbItem = BreadcrumbItem(
            name = name,
            kind = BreadcrumbKind.PROPERTY,
            position = EditorPosition(line, column)
        )
    }
}

/**
 * Types of breadcrumb elements.
 */
enum class BreadcrumbKind {
    /** File root */
    FILE,

    /** Package or namespace */
    PACKAGE,

    /** Module */
    MODULE,

    /** Class or struct */
    CLASS,

    /** Interface */
    INTERFACE,

    /** Enum */
    ENUM,

    /** Function or method */
    FUNCTION,

    /** Property or field */
    PROPERTY,

    /** Variable */
    VARIABLE,

    /** Constructor */
    CONSTRUCTOR,

    /** Object (Kotlin object declaration) */
    OBJECT,

    /** Companion object */
    COMPANION,

    /** Lambda or anonymous function */
    LAMBDA,

    /** Block scope (if, for, when, etc.) */
    BLOCK,

    /** Other/unknown scope */
    OTHER
}

/**
 * Manages breadcrumb navigation state.
 *
 * Tracks the current scope hierarchy and provides methods
 * to update breadcrumbs based on cursor position.
 */
class BreadcrumbManager {
    private var items: List<BreadcrumbItem> = emptyList()
    private var filename: String = ""

    /**
     * Gets the current breadcrumb items.
     */
    fun getItems(): List<BreadcrumbItem> = items

    /**
     * Sets the filename for the root breadcrumb.
     */
    fun setFilename(name: String) {
        filename = name
        if (items.isEmpty() || items.firstOrNull()?.kind != BreadcrumbKind.FILE) {
            items = listOf(BreadcrumbItem.file(name))
        } else {
            items = listOf(BreadcrumbItem.file(name)) + items.drop(1)
        }
    }

    /**
     * Updates the breadcrumb path.
     */
    fun setPath(path: List<BreadcrumbItem>) {
        items = if (filename.isNotEmpty()) {
            listOf(BreadcrumbItem.file(filename)) + path.filter { it.kind != BreadcrumbKind.FILE }
        } else {
            path
        }
    }

    /**
     * Clears all breadcrumbs except the file.
     */
    fun clearPath() {
        items = if (filename.isNotEmpty()) {
            listOf(BreadcrumbItem.file(filename))
        } else {
            emptyList()
        }
    }

    /**
     * Checks if there are any breadcrumbs.
     */
    fun hasBreadcrumbs(): Boolean = items.isNotEmpty()

    /**
     * Gets the current scope (last breadcrumb).
     */
    fun getCurrentScope(): BreadcrumbItem? = items.lastOrNull()

    /**
     * Gets the parent scope of the current scope.
     */
    fun getParentScope(): BreadcrumbItem? = items.dropLast(1).lastOrNull()
}

/**
 * Service interface for computing breadcrumbs from PSI.
 * Implemented in desktop target with PSI access.
 */
interface BreadcrumbProvider {
    /**
     * Computes breadcrumbs for the given cursor position.
     *
     * @param line The cursor line (0-indexed)
     * @param column The cursor column (0-indexed)
     * @return List of breadcrumb items from outermost to innermost scope
     */
    fun computeBreadcrumbs(line: Int, column: Int): List<BreadcrumbItem>

    /**
     * Gets the filename for the breadcrumb root.
     */
    fun getFilename(): String
}
