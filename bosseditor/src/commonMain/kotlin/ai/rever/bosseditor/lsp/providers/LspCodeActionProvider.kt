package ai.rever.bosseditor.lsp.providers

import ai.rever.bosseditor.logging.EditorLogger
import ai.rever.bosseditor.logging.EditorLogCategory
import ai.rever.bosseditor.lsp.client.LspClient
import ai.rever.bosseditor.lsp.protocol.*
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.*

/**
 * Provider for LSP code actions (textDocument/codeAction).
 *
 * Code actions are used to:
 * - Fix diagnostics (quickfixes)
 * - Perform refactorings
 * - Execute source actions (organize imports, etc.)
 *
 * @property client The LSP client
 */
class LspCodeActionProvider(
    private val client: LspClient
) {
    private val logger = EditorLogger.forComponent("LspCodeActionProvider")
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Requests code actions for a range in a document.
     *
     * @param uri The document URI
     * @param range The range for which to get code actions
     * @param diagnostics Diagnostics overlapping the range
     * @param only Filter to only return specific kinds of code actions
     * @return List of available code actions
     */
    suspend fun getCodeActions(
        uri: String,
        range: Range,
        diagnostics: List<Diagnostic> = emptyList(),
        only: List<String>? = null
    ): List<CodeAction> {
        if (!client.isInitialized) {
            return emptyList()
        }

        val params = CodeActionParams(
            textDocument = TextDocumentIdentifier(uri),
            range = range,
            context = CodeActionContext(
                diagnostics = diagnostics,
                only = only,
                triggerKind = CodeActionTriggerKind.Invoked
            )
        )

        return try {
            val paramsJson = json.encodeToJsonElement(params)
            val response = client.request("textDocument/codeAction", paramsJson)

            if (response == null || response is JsonNull) {
                emptyList()
            } else {
                parseCodeActionResponse(response)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error(EditorLogCategory.EDITOR, "LSP codeAction error", error = e)
            emptyList()
        }
    }

    /**
     * Gets refactoring code actions for a range.
     *
     * @param uri The document URI
     * @param range The range to refactor
     * @return List of refactoring actions
     */
    suspend fun getRefactoringActions(uri: String, range: Range): List<CodeAction> {
        return getCodeActions(
            uri = uri,
            range = range,
            only = listOf(CodeActionKind.REFACTOR)
        )
    }

    /**
     * Gets quickfix code actions for diagnostics.
     *
     * @param uri The document URI
     * @param range The range with diagnostics
     * @param diagnostics The diagnostics to fix
     * @return List of quickfix actions
     */
    suspend fun getQuickFixes(
        uri: String,
        range: Range,
        diagnostics: List<Diagnostic>
    ): List<CodeAction> {
        return getCodeActions(
            uri = uri,
            range = range,
            diagnostics = diagnostics,
            only = listOf(CodeActionKind.QUICKFIX)
        )
    }

    /**
     * Gets source actions (organize imports, fix all, etc.).
     *
     * @param uri The document URI
     * @return List of source actions
     */
    suspend fun getSourceActions(uri: String): List<CodeAction> {
        return getCodeActions(
            uri = uri,
            range = Range(Position.ZERO, Position.ZERO),
            only = listOf(CodeActionKind.SOURCE)
        )
    }

    /**
     * Resolves additional information for a code action.
     *
     * @param codeAction The code action to resolve
     * @return The resolved code action with full details
     */
    suspend fun resolveCodeAction(codeAction: CodeAction): CodeAction {
        if (!client.isInitialized) {
            return codeAction
        }

        return try {
            val paramsJson = json.encodeToJsonElement(codeAction)
            val response = client.request("codeAction/resolve", paramsJson)

            if (response == null || response is JsonNull) {
                codeAction
            } else {
                json.decodeFromJsonElement(response)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error(EditorLogCategory.EDITOR, "LSP codeAction/resolve error", error = e)
            codeAction
        }
    }

    /**
     * Executes a code action's edit or command.
     *
     * @param codeAction The code action to execute
     * @return The workspace edit to apply, or null if no edit
     */
    suspend fun executeCodeAction(codeAction: CodeAction): WorkspaceEdit? {
        // If the code action has a direct edit, return it
        if (codeAction.edit != null) {
            return codeAction.edit
        }

        // If the code action has a command, execute it
        if (codeAction.command != null) {
            return executeCommand(codeAction.command)
        }

        // If neither, try to resolve the code action first
        val resolved = resolveCodeAction(codeAction)
        return resolved.edit
    }

    /**
     * Executes a command.
     *
     * @param command The command to execute
     * @return The workspace edit if the command produces one
     */
    private suspend fun executeCommand(command: Command): WorkspaceEdit? {
        if (!client.isInitialized) {
            return null
        }

        return try {
            val params = buildJsonObject {
                put("command", command.command)
                command.arguments?.let { args ->
                    put("arguments", JsonArray(args))
                }
            }

            val response = client.request("workspace/executeCommand", params)

            // Some commands return a workspace edit
            if (response != null && response !is JsonNull) {
                try {
                    json.decodeFromJsonElement<WorkspaceEdit>(response)
                } catch (e: Exception) {
                    // Command didn't return a workspace edit
                    null
                }
            } else {
                null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error(EditorLogCategory.EDITOR, "LSP workspace/executeCommand error", error = e)
            null
        }
    }

    /**
     * Parses the code action response.
     */
    private fun parseCodeActionResponse(response: JsonElement): List<CodeAction> {
        return when (response) {
            is JsonArray -> response.mapNotNull { element ->
                try {
                    when (element) {
                        is JsonObject -> {
                            // Check if it's a CodeAction or a Command
                            if (element.containsKey("title") && !element.containsKey("command")) {
                                // It's a Command, wrap it in a CodeAction
                                val command = json.decodeFromJsonElement<Command>(element)
                                CodeAction(
                                    title = command.title,
                                    command = command
                                )
                            } else {
                                // It's a CodeAction
                                json.decodeFromJsonElement<CodeAction>(element)
                            }
                        }
                        else -> null
                    }
                } catch (e: Exception) {
                    logger.warn(EditorLogCategory.EDITOR, "Failed to parse code action", error = e)
                    null
                }
            }
            else -> emptyList()
        }
    }

    companion object {
        /**
         * Filters code actions by kind.
         *
         * @param actions The list of code actions
         * @param kind The kind to filter by
         * @return Filtered list of code actions
         */
        fun filterByKind(actions: List<CodeAction>, kind: String): List<CodeAction> {
            return actions.filter { CodeActionKind.matches(it.kind, kind) }
        }

        /**
         * Groups code actions by their kind prefix.
         *
         * @param actions The list of code actions
         * @return Map of kind prefix to actions
         */
        fun groupByKind(actions: List<CodeAction>): Map<String, List<CodeAction>> {
            return actions.groupBy { action ->
                when {
                    CodeActionKind.isQuickFix(action.kind) -> CodeActionKind.QUICKFIX
                    CodeActionKind.isRefactoring(action.kind) -> CodeActionKind.REFACTOR
                    CodeActionKind.isSource(action.kind) -> CodeActionKind.SOURCE
                    else -> CodeActionKind.EMPTY
                }
            }
        }
    }
}
