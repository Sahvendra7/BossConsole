package ai.rever.bosseditor.refactoring

import ai.rever.bosseditor.core.EditorPosition
import ai.rever.bosseditor.core.EditorRange
import ai.rever.bosseditor.logging.EditorLogger
import ai.rever.bosseditor.logging.EditorLogCategory
import ai.rever.bosseditor.lsp.client.LspClient
import ai.rever.bosseditor.lsp.protocol.*
import ai.rever.bosseditor.lsp.providers.LspCodeActionProvider
import ai.rever.bosseditor.lsp.providers.LspNavigationProvider

/**
 * LSP-based refactoring provider for non-Kotlin files.
 *
 * Uses the Language Server Protocol to:
 * - Perform rename operations
 * - Execute code actions (refactorings, quickfixes)
 * - Query available refactorings
 *
 * @property client The LSP client
 * @property supportedExtensions File extensions this provider handles
 */
class LspRefactoringProvider(
    private val client: LspClient,
    override val supportedExtensions: Set<String>
) : RefactoringProvider {

    private val logger = EditorLogger.forComponent("LspRefactoringProvider")
    private val navigationProvider = LspNavigationProvider(client)
    private val codeActionProvider = LspCodeActionProvider(client)

    override suspend fun getAvailableRefactorings(context: RefactorContext): List<RefactorAvailability> {
        val results = mutableListOf<RefactorAvailability>()

        // Check if rename is supported
        val renameSupported = client.serverCapabilities?.renameProvider == true

        results.add(RefactorAvailability(
            kind = RefactorKind.RENAME,
            available = renameSupported,
            reason = if (!renameSupported) "Rename not supported by language server" else null
        ))

        // Check code action support for other refactorings
        val codeActionSupported = client.serverCapabilities?.codeActionProvider == true

        if (codeActionSupported && context.selection != null && !context.selection.isEmpty) {
            // Try to get available refactoring actions
            val range = editorRangeToLspRange(context.selection)
            val actions = codeActionProvider.getRefactoringActions(context.fileUri, range)

            // Extract method is available if there's an extract action
            val hasExtract = actions.any { CodeActionKind.matches(it.kind, CodeActionKind.REFACTOR_EXTRACT) }
            results.add(RefactorAvailability(
                kind = RefactorKind.EXTRACT_METHOD,
                available = hasExtract,
                reason = if (!hasExtract) "No extract actions available" else null
            ))

            results.add(RefactorAvailability(
                kind = RefactorKind.EXTRACT_VARIABLE,
                available = hasExtract,
                reason = if (!hasExtract) "No extract actions available" else null
            ))

            // Inline is available if there's an inline action
            val hasInline = actions.any { CodeActionKind.matches(it.kind, CodeActionKind.REFACTOR_INLINE) }
            results.add(RefactorAvailability(
                kind = RefactorKind.INLINE,
                available = hasInline,
                reason = if (!hasInline) "No inline actions available" else null
            ))
        } else {
            // No selection, these refactorings are not available
            results.add(RefactorAvailability(
                kind = RefactorKind.EXTRACT_METHOD,
                available = false,
                reason = "Select code to extract"
            ))
            results.add(RefactorAvailability(
                kind = RefactorKind.EXTRACT_VARIABLE,
                available = false,
                reason = "Select an expression to extract"
            ))
            results.add(RefactorAvailability(
                kind = RefactorKind.INLINE,
                available = false,
                reason = "Place cursor on symbol to inline"
            ))
        }

        // Other refactorings not supported via LSP yet
        results.add(RefactorAvailability(
            kind = RefactorKind.EXTRACT_CONSTANT,
            available = false,
            reason = "Not supported via LSP"
        ))
        results.add(RefactorAvailability(
            kind = RefactorKind.MOVE,
            available = false,
            reason = "Not supported via LSP"
        ))
        results.add(RefactorAvailability(
            kind = RefactorKind.CHANGE_SIGNATURE,
            available = false,
            reason = "Not supported via LSP"
        ))
        results.add(RefactorAvailability(
            kind = RefactorKind.SAFE_DELETE,
            available = false,
            reason = "Not supported via LSP"
        ))
        results.add(RefactorAvailability(
            kind = RefactorKind.INTRODUCE_PARAMETER,
            available = false,
            reason = "Not supported via LSP"
        ))

        return results
    }

    override suspend fun prepare(kind: RefactorKind, context: RefactorContext): PrepareResult {
        return when (kind) {
            RefactorKind.RENAME -> prepareRename(context)
            else -> PrepareResult.NotAvailable("Refactoring not supported: $kind")
        }
    }

    override suspend fun execute(kind: RefactorKind, context: RefactorContext, params: Any?): RefactorResult {
        return when (kind) {
            RefactorKind.RENAME -> {
                val renameParams = params as? RenameParams
                    ?: return RefactorResult.Error("Invalid rename parameters")
                executeRename(context, renameParams.newName)
            }
            RefactorKind.EXTRACT_METHOD,
            RefactorKind.EXTRACT_VARIABLE,
            RefactorKind.INLINE -> {
                executeCodeAction(kind, context)
            }
            else -> RefactorResult.Error("Refactoring not supported via LSP: $kind")
        }
    }

    override suspend fun preview(kind: RefactorKind, context: RefactorContext, params: Any?): List<FileChange> {
        // LSP doesn't have a standard preview mechanism
        // We would need to dry-run the refactoring
        return emptyList()
    }

    override suspend fun validateRename(newName: String, context: RefactorContext): String? {
        if (newName.isBlank()) {
            return "Name cannot be empty"
        }

        // Basic identifier validation (language-agnostic)
        if (!newName.first().isLetter() && newName.first() != '_') {
            return "Invalid identifier: must start with letter or underscore"
        }

        if (!newName.all { it.isLetterOrDigit() || it == '_' }) {
            return "Invalid identifier: contains invalid characters"
        }

        return null
    }

    /**
     * Prepares a rename operation using LSP prepareRename.
     */
    private suspend fun prepareRename(context: RefactorContext): PrepareResult {
        val position = editorPositionToLspPosition(context.position)

        val result = navigationProvider.prepareRename(context.fileUri, position)

        return if (result != null) {
            PrepareResult.Ready(
                currentName = result.placeholder,
                symbolKind = SymbolKind.UNKNOWN,
                symbolRange = lspRangeToEditorRange(result.range)
            )
        } else {
            // Try to get hover info to see if there's a symbol
            val hover = navigationProvider.getHover(context.fileUri, position)
            if (hover != null) {
                PrepareResult.NotAvailable("Symbol cannot be renamed")
            } else {
                PrepareResult.NotAvailable("No symbol found at cursor position")
            }
        }
    }

    /**
     * Executes a rename operation using LSP.
     */
    private suspend fun executeRename(context: RefactorContext, newName: String): RefactorResult {
        val position = editorPositionToLspPosition(context.position)

        val workspaceEdit = navigationProvider.rename(context.fileUri, position, newName)

        return if (workspaceEdit != null) {
            val affectedFiles = workspaceEdit.changes?.size ?: 0
            RefactorResult.Success(
                edit = workspaceEdit,
                affectedFiles = affectedFiles,
                description = "Renamed to '$newName' ($affectedFiles files)"
            )
        } else {
            RefactorResult.Error("Rename failed - no changes returned by language server")
        }
    }

    /**
     * Executes a code action-based refactoring.
     */
    private suspend fun executeCodeAction(kind: RefactorKind, context: RefactorContext): RefactorResult {
        val selection = context.selection
            ?: return RefactorResult.Error("No selection for refactoring")

        val range = editorRangeToLspRange(selection)
        val actions = codeActionProvider.getRefactoringActions(context.fileUri, range)

        // Find matching action
        val actionKind = when (kind) {
            RefactorKind.EXTRACT_METHOD, RefactorKind.EXTRACT_VARIABLE -> CodeActionKind.REFACTOR_EXTRACT
            RefactorKind.INLINE -> CodeActionKind.REFACTOR_INLINE
            else -> return RefactorResult.Error("Unexpected refactoring kind: $kind")
        }

        val action = actions.find { CodeActionKind.matches(it.kind, actionKind) }
            ?: return RefactorResult.Error("No matching refactoring action found")

        val workspaceEdit = codeActionProvider.executeCodeAction(action)
            ?: return RefactorResult.Error("Code action did not produce any changes")

        val affectedFiles = workspaceEdit.changes?.size ?: 0
        return RefactorResult.Success(
            edit = workspaceEdit,
            affectedFiles = affectedFiles,
            description = action.title
        )
    }

    /**
     * Converts an EditorPosition to an LSP Position.
     */
    private fun editorPositionToLspPosition(position: EditorPosition): Position {
        return Position(line = position.line, character = position.column)
    }

    /**
     * Converts an EditorRange to an LSP Range.
     */
    private fun editorRangeToLspRange(range: EditorRange): Range {
        return Range(
            start = editorPositionToLspPosition(range.start),
            end = editorPositionToLspPosition(range.end)
        )
    }

    /**
     * Converts an LSP Range to an EditorRange.
     */
    private fun lspRangeToEditorRange(range: Range): EditorRange {
        return EditorRange(
            start = EditorPosition(range.start.line, range.start.character),
            end = EditorPosition(range.end.line, range.end.character)
        )
    }
}
