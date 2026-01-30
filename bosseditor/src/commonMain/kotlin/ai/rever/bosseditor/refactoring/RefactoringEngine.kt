package ai.rever.bosseditor.refactoring

import ai.rever.bosseditor.logging.EditorLogger
import ai.rever.bosseditor.logging.EditorLogCategory
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Main orchestrator for refactoring operations.
 *
 * The RefactoringEngine coordinates between different refactoring providers
 * (PSI-based for Kotlin, LSP-based for other languages) and handles:
 * - Selecting the appropriate provider based on file type
 * - Coordinating preview, execution, and undo grouping
 * - Emitting events for UI updates
 *
 * @property editApplier The applier for workspace edits
 */
class RefactoringEngine(
    private val editApplier: WorkspaceEditApplier
) {
    private val logger = EditorLogger.forComponent("RefactoringEngine")

    private val providers = mutableListOf<RefactoringProvider>()

    private val _events = MutableSharedFlow<RefactoringEvent>(extraBufferCapacity = 16)
    /** Flow of refactoring events for UI updates */
    val events: SharedFlow<RefactoringEvent> = _events.asSharedFlow()

    /**
     * Registers a refactoring provider.
     *
     * @param provider The provider to register
     */
    fun registerProvider(provider: RefactoringProvider) {
        providers.add(provider)
        logger.debug(EditorLogCategory.EDITOR, "Registered refactoring provider", mapOf(
            "extensions" to provider.supportedExtensions.joinToString()
        ))
    }

    /**
     * Unregisters a refactoring provider.
     *
     * @param provider The provider to unregister
     */
    fun unregisterProvider(provider: RefactoringProvider) {
        providers.remove(provider)
    }

    /**
     * Gets the appropriate provider for a file.
     *
     * @param filePath The path of the file
     * @return The matching provider, or null if none found
     */
    fun getProvider(filePath: String): RefactoringProvider? {
        val extension = filePath.substringAfterLast('.', "").lowercase()
        return providers.find { extension in it.supportedExtensions }
    }

    /**
     * Gets available refactorings for the given context.
     *
     * @param context The refactoring context
     * @return List of available refactorings
     */
    suspend fun getAvailableRefactorings(context: RefactorContext): List<RefactorAvailability> {
        val provider = getProvider(context.filePath)
        if (provider == null) {
            logger.debug(EditorLogCategory.EDITOR, "No refactoring provider for file", mapOf(
                "filePath" to context.filePath
            ))
            return RefactorKind.entries.map { kind ->
                RefactorAvailability(kind, false, "No refactoring support for this file type")
            }
        }

        return try {
            provider.getAvailableRefactorings(context)
        } catch (e: Exception) {
            logger.error(EditorLogCategory.EDITOR, "Error getting available refactorings", error = e)
            emptyList()
        }
    }

    /**
     * Prepares a refactoring operation.
     *
     * @param kind The type of refactoring
     * @param context The refactoring context
     * @return The preparation result
     */
    suspend fun prepare(kind: RefactorKind, context: RefactorContext): PrepareResult {
        val provider = getProvider(context.filePath)
            ?: return PrepareResult.NotAvailable("No refactoring support for this file type")

        return try {
            _events.emit(RefactoringEvent.Started(kind, context))
            provider.prepare(kind, context)
        } catch (e: Exception) {
            logger.error(EditorLogCategory.EDITOR, "Error preparing refactoring", mapOf(
                "kind" to kind.name
            ), e)
            PrepareResult.Error(e.message ?: "Unknown error during preparation")
        }
    }

    /**
     * Executes a refactoring operation.
     *
     * @param kind The type of refactoring
     * @param context The refactoring context
     * @param params Parameters for the refactoring
     * @return The result of the refactoring
     */
    suspend fun execute(kind: RefactorKind, context: RefactorContext, params: Any?): RefactorResult {
        val provider = getProvider(context.filePath)
            ?: return RefactorResult.Error("No refactoring support for this file type")

        return try {
            _events.emit(RefactoringEvent.Started(kind, context))
            _events.emit(RefactoringEvent.Progress("Analyzing code...", 0.1f))

            val result = provider.execute(kind, context, params)

            when (result) {
                is RefactorResult.Success -> {
                    _events.emit(RefactoringEvent.Progress("Applying changes...", 0.8f))
                    editApplier.apply(result.edit)
                    _events.emit(RefactoringEvent.Progress("Complete", 1.0f))
                    logger.info(EditorLogCategory.EDITOR, "Refactoring completed", mapOf(
                        "kind" to kind.name,
                        "affectedFiles" to result.affectedFiles.toString()
                    ))
                }
                is RefactorResult.Error -> {
                    logger.warn(EditorLogCategory.EDITOR, "Refactoring failed", mapOf(
                        "kind" to kind.name,
                        "message" to result.message
                    ))
                }
                is RefactorResult.Cancelled -> {
                    logger.debug(EditorLogCategory.EDITOR, "Refactoring cancelled", mapOf(
                        "kind" to kind.name
                    ))
                }
                is RefactorResult.ConfirmationRequired -> {
                    logger.debug(EditorLogCategory.EDITOR, "Refactoring requires confirmation", mapOf(
                        "kind" to kind.name
                    ))
                }
            }

            _events.emit(RefactoringEvent.Completed(result))
            result
        } catch (e: Exception) {
            logger.error(EditorLogCategory.EDITOR, "Error executing refactoring", mapOf(
                "kind" to kind.name
            ), e)
            val error = RefactorResult.Error(e.message ?: "Unknown error during refactoring")
            _events.emit(RefactoringEvent.Completed(error))
            error
        }
    }

    /**
     * Generates a preview of the refactoring changes.
     *
     * @param kind The type of refactoring
     * @param context The refactoring context
     * @param params Parameters for the refactoring
     * @return List of file changes
     */
    suspend fun preview(kind: RefactorKind, context: RefactorContext, params: Any?): List<FileChange> {
        val provider = getProvider(context.filePath)
            ?: return emptyList()

        return try {
            val changes = provider.preview(kind, context, params)
            _events.emit(RefactoringEvent.PreviewReady(changes))
            changes
        } catch (e: Exception) {
            logger.error(EditorLogCategory.EDITOR, "Error generating refactoring preview", mapOf(
                "kind" to kind.name
            ), e)
            emptyList()
        }
    }

    /**
     * Validates a new name for rename refactoring.
     *
     * @param newName The proposed new name
     * @param context The refactoring context
     * @return null if valid, or error message if invalid
     */
    suspend fun validateRename(newName: String, context: RefactorContext): String? {
        val provider = getProvider(context.filePath)
            ?: return "No refactoring support for this file type"

        return try {
            provider.validateRename(newName, context)
        } catch (e: Exception) {
            logger.error(EditorLogCategory.EDITOR, "Error validating rename", error = e)
            e.message ?: "Validation failed"
        }
    }

    /**
     * Performs a rename refactoring.
     *
     * This is a convenience method that combines prepare, execute, and apply.
     *
     * @param context The refactoring context
     * @param newName The new name for the symbol
     * @return The result of the rename operation
     */
    suspend fun rename(context: RefactorContext, newName: String): RefactorResult {
        // Validate the new name first
        val validationError = validateRename(newName, context)
        if (validationError != null) {
            return RefactorResult.Error(validationError, recoverable = true)
        }

        // Execute the rename
        return execute(RefactorKind.RENAME, context, RenameParams(newName))
    }

    /**
     * Checks if any refactoring is available at the given context.
     *
     * @param context The refactoring context
     * @return true if at least one refactoring is available
     */
    suspend fun hasAvailableRefactorings(context: RefactorContext): Boolean {
        return getAvailableRefactorings(context).any { it.available }
    }

    /**
     * Clears all registered providers.
     */
    fun clearProviders() {
        providers.clear()
    }
}
