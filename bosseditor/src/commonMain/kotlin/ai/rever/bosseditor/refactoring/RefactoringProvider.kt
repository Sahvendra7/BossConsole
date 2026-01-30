package ai.rever.bosseditor.refactoring

/**
 * Interface for language-specific refactoring implementations.
 *
 * Each language can provide its own implementation of this interface
 * to support refactoring operations. The PSI-based implementation handles
 * Kotlin files, while the LSP-based implementation delegates to language servers.
 */
interface RefactoringProvider {

    /**
     * Returns the list of file extensions this provider handles.
     * E.g., ["kt", "kts"] for Kotlin.
     */
    val supportedExtensions: Set<String>

    /**
     * Checks which refactorings are available at the given context.
     *
     * @param context The refactoring context (file, position, selection)
     * @return List of available refactorings with availability status
     */
    suspend fun getAvailableRefactorings(context: RefactorContext): List<RefactorAvailability>

    /**
     * Prepares a refactoring operation without executing it.
     *
     * This is used to validate the operation and gather information
     * for the user interface (e.g., current symbol name for rename).
     *
     * @param kind The type of refactoring to prepare
     * @param context The refactoring context
     * @return Preparation result with relevant information, or error if not possible
     */
    suspend fun prepare(kind: RefactorKind, context: RefactorContext): PrepareResult

    /**
     * Executes a refactoring operation.
     *
     * @param kind The type of refactoring to execute
     * @param context The refactoring context
     * @param params Language-specific parameters for the refactoring
     * @return The result of the refactoring operation
     */
    suspend fun execute(kind: RefactorKind, context: RefactorContext, params: Any?): RefactorResult

    /**
     * Generates a preview of the refactoring changes without applying them.
     *
     * @param kind The type of refactoring
     * @param context The refactoring context
     * @param params Language-specific parameters
     * @return List of file changes that would be made
     */
    suspend fun preview(kind: RefactorKind, context: RefactorContext, params: Any?): List<FileChange>

    /**
     * Validates a new name for rename refactoring.
     *
     * @param newName The proposed new name
     * @param context The refactoring context
     * @return null if valid, or error message if invalid
     */
    suspend fun validateRename(newName: String, context: RefactorContext): String?
}

/**
 * Result of preparing a refactoring operation.
 */
sealed class PrepareResult {
    /**
     * Preparation succeeded with the required information.
     */
    data class Ready(
        /** The current name of the symbol (for rename) */
        val currentName: String? = null,
        /** The kind of symbol being refactored */
        val symbolKind: SymbolKind? = null,
        /** Range of the symbol in the current file */
        val symbolRange: ai.rever.bosseditor.core.EditorRange? = null,
        /** Additional information for the UI */
        val info: Map<String, String> = emptyMap()
    ) : PrepareResult()

    /**
     * Preparation failed - refactoring not available.
     */
    data class NotAvailable(
        /** Reason why the refactoring is not available */
        val reason: String
    ) : PrepareResult()

    /**
     * Preparation failed with an error.
     */
    data class Error(
        /** Error message */
        val message: String
    ) : PrepareResult()
}

/**
 * Base class for refactoring parameters that supports type-safe casting.
 */
interface RefactorParams

/**
 * Extension to check if a provider supports a specific file.
 */
fun RefactoringProvider.supportsFile(filePath: String): Boolean {
    val extension = filePath.substringAfterLast('.', "").lowercase()
    return extension in supportedExtensions
}

/**
 * Extension to check if a provider supports a specific refactoring kind
 * at the given context.
 */
suspend fun RefactoringProvider.supports(kind: RefactorKind, context: RefactorContext): Boolean {
    return getAvailableRefactorings(context).any { it.kind == kind && it.available }
}
