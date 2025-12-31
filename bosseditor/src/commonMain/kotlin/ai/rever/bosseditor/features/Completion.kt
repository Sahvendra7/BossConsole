package ai.rever.bosseditor.features

import ai.rever.bosseditor.core.EditorPosition

/**
 * Kinds of completion items (matches LSP CompletionItemKind).
 */
enum class CompletionKind {
    TEXT,
    METHOD,
    FUNCTION,
    CONSTRUCTOR,
    FIELD,
    VARIABLE,
    CLASS,
    INTERFACE,
    MODULE,
    PROPERTY,
    UNIT,
    VALUE,
    ENUM,
    KEYWORD,
    SNIPPET,
    COLOR,
    FILE,
    REFERENCE,
    FOLDER,
    ENUM_MEMBER,
    CONSTANT,
    STRUCT,
    EVENT,
    OPERATOR,
    TYPE_PARAMETER
}

/**
 * Represents a single completion suggestion.
 *
 * @property label Display text shown in the completion list
 * @property insertText Text to insert when selected (may include snippets)
 * @property kind The kind of completion item (for icon display)
 * @property detail Additional detail (e.g., type signature)
 * @property documentation Extended documentation (for tooltip)
 * @property deprecated Whether this item is deprecated
 * @property sortText Text used for sorting (if different from label)
 * @property filterText Text used for filtering (if different from label)
 */
data class CompletionItem(
    val label: String,
    val insertText: String = label,
    val kind: CompletionKind = CompletionKind.TEXT,
    val detail: String? = null,
    val documentation: String? = null,
    val deprecated: Boolean = false,
    val sortText: String? = null,
    val filterText: String? = null
) {
    /** Text to use for filtering (filterText or label) */
    val effectiveFilterText: String get() = filterText ?: label

    /** Text to use for sorting (sortText or label) */
    val effectiveSortText: String get() = sortText ?: label

    companion object {
        /** Creates a keyword completion item */
        fun keyword(keyword: String): CompletionItem = CompletionItem(
            label = keyword,
            kind = CompletionKind.KEYWORD
        )

        /** Creates a function completion item */
        fun function(
            name: String,
            signature: String,
            returnType: String? = null
        ): CompletionItem = CompletionItem(
            label = name,
            insertText = "$name()",
            kind = CompletionKind.FUNCTION,
            detail = signature + (returnType?.let { ": $it" } ?: "")
        )

        /** Creates a variable completion item */
        fun variable(name: String, type: String? = null): CompletionItem = CompletionItem(
            label = name,
            kind = CompletionKind.VARIABLE,
            detail = type
        )

        /** Creates a class completion item */
        fun className(name: String): CompletionItem = CompletionItem(
            label = name,
            kind = CompletionKind.CLASS
        )

        /** Creates a property completion item */
        fun property(name: String, type: String? = null): CompletionItem = CompletionItem(
            label = name,
            kind = CompletionKind.PROPERTY,
            detail = type
        )

        /** Creates a snippet completion item */
        fun snippet(label: String, insertText: String, description: String? = null): CompletionItem = CompletionItem(
            label = label,
            insertText = insertText,
            kind = CompletionKind.SNIPPET,
            detail = description
        )
    }
}

/**
 * Result of a completion request.
 *
 * @property items The list of completion items
 * @property isIncomplete If true, more items may be available (trigger again)
 */
data class CompletionResult(
    val items: List<CompletionItem>,
    val isIncomplete: Boolean = false
)

/**
 * State for the completion popup.
 */
data class CompletionState(
    /** Position where completion was triggered */
    val triggerPosition: EditorPosition,

    /** Current prefix being typed (for filtering) */
    val prefix: String,

    /** All available completion items */
    val allItems: List<CompletionItem>,

    /** Currently selected index in filtered list */
    val selectedIndex: Int = 0
) {
    /** Filtered items based on current prefix */
    val filteredItems: List<CompletionItem> by lazy {
        if (prefix.isEmpty()) {
            allItems
        } else {
            allItems.filter { item ->
                item.effectiveFilterText.contains(prefix, ignoreCase = true)
            }.sortedWith(compareBy(
                // Exact prefix match first
                { !it.effectiveFilterText.startsWith(prefix, ignoreCase = true) },
                // Then by sort text
                { it.effectiveSortText }
            ))
        }
    }

    /** Whether there are any items to show */
    val hasItems: Boolean get() = filteredItems.isNotEmpty()

    /** The currently selected item, or null if none */
    val selectedItem: CompletionItem?
        get() = filteredItems.getOrNull(selectedIndex)

    /** Updates the selected index, clamping to valid range */
    fun withSelectedIndex(index: Int): CompletionState {
        val newIndex = index.coerceIn(0, (filteredItems.size - 1).coerceAtLeast(0))
        return copy(selectedIndex = newIndex)
    }

    /** Updates the prefix and resets selection */
    fun withPrefix(newPrefix: String): CompletionState {
        return copy(prefix = newPrefix, selectedIndex = 0)
    }

    /** Moves selection up */
    fun moveUp(): CompletionState = withSelectedIndex(selectedIndex - 1)

    /** Moves selection down */
    fun moveDown(): CompletionState = withSelectedIndex(selectedIndex + 1)

    /** Moves selection to first item */
    fun moveToFirst(): CompletionState = withSelectedIndex(0)

    /** Moves selection to last item */
    fun moveToLast(): CompletionState = withSelectedIndex(filteredItems.size - 1)

    /** Moves selection up by a page (10 items) */
    fun pageUp(): CompletionState = withSelectedIndex(selectedIndex - 10)

    /** Moves selection down by a page (10 items) */
    fun pageDown(): CompletionState = withSelectedIndex(selectedIndex + 10)
}

/**
 * Provider interface for completion items.
 * Implementations can provide completions from different sources (PSI, LSP, static).
 */
interface CompletionProvider {
    /**
     * Gets completion items at the given position.
     *
     * @param position The position in the document
     * @param prefix The current prefix being typed
     * @param triggerCharacter The character that triggered completion (if any)
     * @return Completion result with items
     */
    suspend fun getCompletions(
        position: EditorPosition,
        prefix: String,
        triggerCharacter: Char? = null
    ): CompletionResult
}

/**
 * Simple static completion provider for testing.
 */
class StaticCompletionProvider(
    private val items: List<CompletionItem>
) : CompletionProvider {
    override suspend fun getCompletions(
        position: EditorPosition,
        prefix: String,
        triggerCharacter: Char?
    ): CompletionResult {
        return CompletionResult(items)
    }
}

/**
 * Completion provider for Kotlin keywords.
 */
object KotlinKeywordCompletionProvider : CompletionProvider {
    private val keywords = listOf(
        "abstract", "annotation", "as", "break", "by", "catch", "class", "companion",
        "const", "constructor", "continue", "crossinline", "data", "do", "else", "enum",
        "expect", "external", "false", "final", "finally", "for", "fun", "get", "if",
        "import", "in", "infix", "init", "inline", "inner", "interface", "internal",
        "is", "lateinit", "noinline", "null", "object", "open", "operator", "out",
        "override", "package", "private", "protected", "public", "reified", "return",
        "sealed", "set", "super", "suspend", "tailrec", "this", "throw", "true", "try",
        "typealias", "typeof", "val", "var", "vararg", "when", "where", "while"
    ).map { CompletionItem.keyword(it) }

    override suspend fun getCompletions(
        position: EditorPosition,
        prefix: String,
        triggerCharacter: Char?
    ): CompletionResult {
        return CompletionResult(keywords)
    }
}
