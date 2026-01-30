package ai.rever.bosseditor.refactoring

import ai.rever.bosseditor.core.EditorPosition
import ai.rever.bosseditor.core.EditorRange
import ai.rever.bosseditor.lsp.protocol.TextEdit
import ai.rever.bosseditor.lsp.protocol.WorkspaceEdit

/**
 * Types of refactoring operations supported by the editor.
 */
enum class RefactorKind {
    /** Rename a symbol across the codebase */
    RENAME,

    /** Extract selected expression into a variable */
    EXTRACT_VARIABLE,

    /** Extract selected code block into a method */
    EXTRACT_METHOD,

    /** Extract selected expression into a constant */
    EXTRACT_CONSTANT,

    /** Inline a variable or method */
    INLINE,

    /** Move a symbol to another location */
    MOVE,

    /** Change a function's signature (parameters, return type) */
    CHANGE_SIGNATURE,

    /** Safely delete a symbol (with usage check) */
    SAFE_DELETE,

    /** Introduce a parameter from expression */
    INTRODUCE_PARAMETER
}

/**
 * Context information for a refactoring operation.
 *
 * @property fileUri The URI of the file being refactored
 * @property filePath The file system path of the file
 * @property position The cursor position in the editor
 * @property selection The current selection range (if any)
 * @property symbolName The name of the symbol at the cursor (if any)
 * @property symbolKind The kind of symbol at the cursor (if any)
 */
data class RefactorContext(
    val fileUri: String,
    val filePath: String,
    val position: EditorPosition,
    val selection: EditorRange?,
    val symbolName: String? = null,
    val symbolKind: SymbolKind? = null
)

/**
 * Kind of symbol being refactored.
 */
enum class SymbolKind {
    CLASS,
    INTERFACE,
    OBJECT,
    FUNCTION,
    METHOD,
    PROPERTY,
    VARIABLE,
    PARAMETER,
    CONSTANT,
    ENUM,
    ENUM_MEMBER,
    TYPE_ALIAS,
    FILE,
    UNKNOWN
}

/**
 * Result of a refactoring operation.
 */
sealed class RefactorResult {
    /**
     * Refactoring completed successfully.
     *
     * @property edit The workspace edit containing all changes
     * @property affectedFiles Number of files affected
     * @property description Human-readable description of what was done
     */
    data class Success(
        val edit: WorkspaceEdit,
        val affectedFiles: Int,
        val description: String
    ) : RefactorResult()

    /**
     * Refactoring failed with an error.
     *
     * @property message Error message describing what went wrong
     * @property recoverable Whether the user can retry with different parameters
     */
    data class Error(
        val message: String,
        val recoverable: Boolean = true
    ) : RefactorResult()

    /**
     * Refactoring was cancelled by the user.
     */
    data object Cancelled : RefactorResult()

    /**
     * Refactoring requires confirmation before proceeding.
     *
     * @property message Message to show to the user
     * @property warnings List of warnings about potential issues
     * @property affectedFiles Files that will be modified
     */
    data class ConfirmationRequired(
        val message: String,
        val warnings: List<String>,
        val affectedFiles: List<String>
    ) : RefactorResult()
}

/**
 * Represents a change to a single file.
 *
 * @property uri The file URI
 * @property filePath The file system path
 * @property edits List of text edits to apply
 * @property previewBefore Preview of the content before changes (optional)
 * @property previewAfter Preview of the content after changes (optional)
 */
data class FileChange(
    val uri: String,
    val filePath: String,
    val edits: List<TextEdit>,
    val previewBefore: String? = null,
    val previewAfter: String? = null
)

/**
 * Parameters for a rename refactoring.
 *
 * @property newName The new name for the symbol
 */
data class RenameParams(
    val newName: String
)

/**
 * Parameters for extracting a variable.
 *
 * @property variableName The name for the new variable
 * @property replaceAll Whether to replace all occurrences of the expression
 * @property isVal Whether to use val (true) or var (false)
 */
data class ExtractVariableParams(
    val variableName: String,
    val replaceAll: Boolean = false,
    val isVal: Boolean = true
)

/**
 * Parameters for extracting a method.
 *
 * @property methodName The name for the new method
 * @property visibility The visibility modifier (public, private, etc.)
 * @property makeStatic Whether to make the method static/companion
 */
data class ExtractMethodParams(
    val methodName: String,
    val visibility: String = "private",
    val makeStatic: Boolean = false
)

/**
 * Parameters for changing a function signature.
 *
 * @property newName New function name (or null to keep current)
 * @property newParameters List of parameter info for the new signature
 * @property newReturnType New return type (or null to keep current)
 * @property newVisibility New visibility modifier (or null to keep current)
 */
data class ChangeSignatureParams(
    val newName: String? = null,
    val newParameters: List<ParameterInfo>,
    val newReturnType: String? = null,
    val newVisibility: String? = null
)

/**
 * Information about a function parameter.
 *
 * @property name Parameter name
 * @property type Parameter type
 * @property defaultValue Default value (if any)
 * @property isVararg Whether this is a vararg parameter
 */
data class ParameterInfo(
    val name: String,
    val type: String,
    val defaultValue: String? = null,
    val isVararg: Boolean = false
)

/**
 * Parameters for safe delete refactoring.
 *
 * @property forceDelete If true, delete even if usages exist
 */
data class SafeDeleteParams(
    val forceDelete: Boolean = false
)

/**
 * Represents a parameter change in signature refactoring.
 *
 * @property name Parameter name
 * @property type Parameter type
 * @property defaultValue Default value (if any)
 * @property action What to do with this parameter
 */
data class ParameterChange(
    val name: String,
    val type: String,
    val defaultValue: String? = null,
    val action: ParameterAction = ParameterAction.KEEP
)

/**
 * Action to perform on a parameter during signature change.
 */
enum class ParameterAction {
    /** Keep the parameter as is */
    KEEP,

    /** Add a new parameter */
    ADD,

    /** Remove the parameter */
    REMOVE,

    /** Modify the parameter */
    MODIFY,

    /** Reorder the parameter */
    REORDER
}

/**
 * Event emitted during refactoring operations.
 */
sealed class RefactoringEvent {
    /**
     * Refactoring operation started.
     */
    data class Started(val kind: RefactorKind, val context: RefactorContext) : RefactoringEvent()

    /**
     * Progress update during refactoring.
     */
    data class Progress(val message: String, val progress: Float) : RefactoringEvent()

    /**
     * Preview is available for the refactoring.
     */
    data class PreviewReady(val changes: List<FileChange>) : RefactoringEvent()

    /**
     * Refactoring completed.
     */
    data class Completed(val result: RefactorResult) : RefactoringEvent()
}

/**
 * Availability information for a refactoring.
 *
 * @property kind The refactoring kind
 * @property available Whether the refactoring is available at the current context
 * @property reason Reason why it's not available (if applicable)
 */
data class RefactorAvailability(
    val kind: RefactorKind,
    val available: Boolean,
    val reason: String? = null
)
