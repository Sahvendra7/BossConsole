package ai.rever.bosseditor.lsp.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * LSP Completion Protocol Types
 *
 * These types follow the Language Server Protocol specification for
 * textDocument/completion requests and responses.
 */

/**
 * Parameters for textDocument/completion request.
 */
@Serializable
data class CompletionParams(
    /**
     * The text document.
     */
    val textDocument: TextDocumentIdentifier,

    /**
     * The position inside the text document.
     */
    val position: Position,

    /**
     * The completion context.
     */
    val context: CompletionContext? = null
)

/**
 * Contains additional information about the context in which a completion request is triggered.
 */
@Serializable
data class CompletionContext(
    /**
     * How the completion was triggered.
     */
    val triggerKind: Int,

    /**
     * The trigger character (single character) that has trigger code complete.
     * Is undefined if `triggerKind !== CompletionTriggerKind.TriggerCharacter`
     */
    val triggerCharacter: String? = null
)

/**
 * How a completion was triggered.
 */
object CompletionTriggerKind {
    /**
     * Completion was triggered by typing an identifier (24x7 code complete),
     * manual invocation (e.g Ctrl+Space) or via API.
     */
    const val INVOKED = 1

    /**
     * Completion was triggered by a trigger character specified by the
     * `triggerCharacters` properties of the `CompletionRegistrationOptions`.
     */
    const val TRIGGER_CHARACTER = 2

    /**
     * Completion was re-triggered as the current completion list is incomplete.
     */
    const val TRIGGER_FOR_INCOMPLETE_COMPLETIONS = 3
}

/**
 * Represents a collection of completion items to be presented in the editor.
 */
@Serializable
data class CompletionList(
    /**
     * This list is not complete. Further typing should result in recomputing this list.
     */
    val isIncomplete: Boolean,

    /**
     * The completion items.
     */
    val items: List<LspCompletionItem>
)

/**
 * A completion item represents a text snippet that is proposed to complete text
 * that is being typed.
 */
@Serializable
data class LspCompletionItem(
    /**
     * The label of this completion item.
     * The label property is also by default the text that is inserted when selecting
     * this completion.
     */
    val label: String,

    /**
     * The kind of this completion item. Based on the kind an icon is chosen by the editor.
     */
    val kind: Int? = null,

    /**
     * A human-readable string with additional information about this item,
     * like type or symbol information.
     */
    val detail: String? = null,

    /**
     * A human-readable string that represents a doc-comment.
     */
    val documentation: String? = null,

    /**
     * Indicates if this item is deprecated.
     */
    val deprecated: Boolean? = null,

    /**
     * Select this item when showing.
     */
    val preselect: Boolean? = null,

    /**
     * A string that should be used when comparing this item with other items.
     * When `falsy` the label is used.
     */
    val sortText: String? = null,

    /**
     * A string that should be used when filtering a set of completion items.
     * When `falsy` the label is used.
     */
    val filterText: String? = null,

    /**
     * A string that should be inserted into a document when selecting
     * this completion. When `falsy` the label is used.
     */
    val insertText: String? = null,

    /**
     * The format of the insert text. The format applies to both the `insertText`
     * property and the `newText` property of a provided `textEdit`.
     */
    val insertTextFormat: Int? = null,

    /**
     * An edit which is applied to a document when selecting this completion.
     * When an edit is provided the value of insertText is ignored.
     */
    val textEdit: TextEdit? = null,

    /**
     * An optional array of additional text edits that are applied when selecting
     * this completion.
     */
    val additionalTextEdits: List<TextEdit>? = null,

    /**
     * An optional set of characters that when pressed while this completion is
     * active will accept it first and then type that character.
     */
    val commitCharacters: List<String>? = null,

    /**
     * An optional command that is executed *after* inserting this completion.
     */
    val command: Command? = null,

    /**
     * A data entry field that is preserved on a completion item between
     * a completion and a completion resolve request.
     */
    val data: kotlinx.serialization.json.JsonElement? = null
)

/**
 * The kind of a completion entry.
 */
object CompletionItemKind {
    const val TEXT = 1
    const val METHOD = 2
    const val FUNCTION = 3
    const val CONSTRUCTOR = 4
    const val FIELD = 5
    const val VARIABLE = 6
    const val CLASS = 7
    const val INTERFACE = 8
    const val MODULE = 9
    const val PROPERTY = 10
    const val UNIT = 11
    const val VALUE = 12
    const val ENUM = 13
    const val KEYWORD = 14
    const val SNIPPET = 15
    const val COLOR = 16
    const val FILE = 17
    const val REFERENCE = 18
    const val FOLDER = 19
    const val ENUM_MEMBER = 20
    const val CONSTANT = 21
    const val STRUCT = 22
    const val EVENT = 23
    const val OPERATOR = 24
    const val TYPE_PARAMETER = 25
}

/**
 * Defines whether the insert text in a completion item should be interpreted as
 * plain text or a snippet.
 */
object InsertTextFormat {
    /**
     * The primary text to be inserted is treated as a plain string.
     */
    const val PLAIN_TEXT = 1

    /**
     * The primary text to be inserted is treated as a snippet.
     */
    const val SNIPPET = 2
}

/**
 * Parameters for completionItem/resolve request.
 */
typealias CompletionItemResolveParams = LspCompletionItem
