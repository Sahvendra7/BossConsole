package ai.rever.bosseditor.lsp.protocol

import kotlinx.serialization.Serializable

/**
 * Parameters for a textDocument/codeAction request.
 */
@Serializable
data class CodeActionParams(
    /**
     * The document in which the command was invoked.
     */
    val textDocument: TextDocumentIdentifier,

    /**
     * The range for which the command was invoked.
     */
    val range: Range,

    /**
     * Context carrying additional information.
     */
    val context: CodeActionContext
)

/**
 * Contains additional diagnostic information about the context in which
 * a code action is run.
 */
@Serializable
data class CodeActionContext(
    /**
     * An array of diagnostics known on the client side overlapping the range.
     */
    val diagnostics: List<Diagnostic> = emptyList(),

    /**
     * Requested kind of actions to return.
     * Actions not of this kind are filtered out.
     */
    val only: List<String>? = null,

    /**
     * The reason why code actions were requested.
     */
    val triggerKind: CodeActionTriggerKind? = null
)

/**
 * The reason why code actions were requested.
 */
@Serializable
enum class CodeActionTriggerKind {
    /**
     * Code actions were explicitly requested by the user or by an extension.
     */
    Invoked,

    /**
     * Code actions were requested automatically.
     */
    Automatic
}

/**
 * A code action represents a change that can be performed in code.
 */
@Serializable
data class CodeAction(
    /**
     * A short, human-readable title for this code action.
     */
    val title: String,

    /**
     * The kind of the code action.
     */
    val kind: String? = null,

    /**
     * The diagnostics that this code action resolves.
     */
    val diagnostics: List<Diagnostic>? = null,

    /**
     * Marks this as a preferred action.
     */
    val isPreferred: Boolean? = null,

    /**
     * Marks that the code action cannot currently be applied.
     */
    val disabled: CodeActionDisabled? = null,

    /**
     * The workspace edit this code action performs.
     */
    val edit: WorkspaceEdit? = null,

    /**
     * A command this code action executes.
     */
    val command: Command? = null,

    /**
     * A data entry field that is preserved on a code action between
     * a textDocument/codeAction and a codeAction/resolve request.
     */
    val data: kotlinx.serialization.json.JsonElement? = null
)

/**
 * Reason why a code action is disabled.
 */
@Serializable
data class CodeActionDisabled(
    /**
     * Human readable description of why the code action is currently disabled.
     */
    val reason: String
)

/**
 * Standard code action kinds.
 */
object CodeActionKind {
    /**
     * Empty kind.
     */
    const val EMPTY = ""

    /**
     * Base kind for quickfix actions: 'quickfix'.
     */
    const val QUICKFIX = "quickfix"

    /**
     * Base kind for refactoring actions: 'refactor'.
     */
    const val REFACTOR = "refactor"

    /**
     * Base kind for refactoring extraction actions: 'refactor.extract'.
     */
    const val REFACTOR_EXTRACT = "refactor.extract"

    /**
     * Base kind for refactoring inline actions: 'refactor.inline'.
     */
    const val REFACTOR_INLINE = "refactor.inline"

    /**
     * Base kind for refactoring rewrite actions: 'refactor.rewrite'.
     */
    const val REFACTOR_REWRITE = "refactor.rewrite"

    /**
     * Base kind for source actions: 'source'.
     */
    const val SOURCE = "source"

    /**
     * Base kind for an organize imports source action: 'source.organizeImports'.
     */
    const val SOURCE_ORGANIZE_IMPORTS = "source.organizeImports"

    /**
     * Base kind for a fix all source action: 'source.fixAll'.
     */
    const val SOURCE_FIX_ALL = "source.fixAll"

    /**
     * Checks if a kind starts with a prefix.
     */
    fun matches(kind: String?, prefix: String): Boolean {
        if (kind == null) return false
        return kind == prefix || kind.startsWith("$prefix.")
    }

    /**
     * Checks if a kind is a refactoring action.
     */
    fun isRefactoring(kind: String?): Boolean = matches(kind, REFACTOR)

    /**
     * Checks if a kind is a quickfix action.
     */
    fun isQuickFix(kind: String?): Boolean = matches(kind, QUICKFIX)

    /**
     * Checks if a kind is a source action.
     */
    fun isSource(kind: String?): Boolean = matches(kind, SOURCE)
}

// Note: Diagnostic, DiagnosticSeverity, DiagnosticTag, CodeDescription, and
// DiagnosticRelatedInformation are defined in Diagnostics.kt - do not duplicate here
