package ai.rever.bosseditor.lsp.providers

import ai.rever.bosseditor.core.EditorPosition
import ai.rever.bosseditor.features.CompletionItem
import ai.rever.bosseditor.features.CompletionKind
import ai.rever.bosseditor.features.CompletionProvider
import ai.rever.bosseditor.features.CompletionResult
import ai.rever.bosseditor.lsp.client.LspClient
import ai.rever.bosseditor.lsp.protocol.*
import kotlinx.serialization.json.*
import kotlin.coroutines.cancellation.CancellationException

/**
 * LSP-based completion provider.
 *
 * This provider fetches completion suggestions from a Language Server Protocol (LSP)
 * server, providing intelligent completions based on the language's semantic understanding.
 *
 * ## Usage
 * ```kotlin
 * val provider = LspCompletionProvider(lspClient, documentUri)
 *
 * // Get completions at a position
 * val result = provider.getCompletions(
 *     position = EditorPosition(5, 10),
 *     prefix = "get",
 *     triggerCharacter = '.'
 * )
 *
 * // Use completions
 * result.items.forEach { item ->
 *     println("${item.label} - ${item.detail}")
 * }
 * ```
 */
class LspCompletionProvider(
    private val client: LspClient,
    private val documentUri: String
) : CompletionProvider {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Gets completion items from the LSP server.
     *
     * @param position The position in the document
     * @param prefix The current prefix being typed (used for filtering)
     * @param triggerCharacter The character that triggered completion (if any)
     * @return Completion result with items from the server
     */
    override suspend fun getCompletions(
        position: EditorPosition,
        prefix: String,
        triggerCharacter: Char?
    ): CompletionResult {
        if (!client.isInitialized) {
            return CompletionResult(emptyList())
        }

        val params = CompletionParams(
            textDocument = TextDocumentIdentifier(documentUri),
            position = Position(position.line, position.column),
            context = CompletionContext(
                triggerKind = if (triggerCharacter != null)
                    CompletionTriggerKind.TRIGGER_CHARACTER
                else
                    CompletionTriggerKind.INVOKED,
                triggerCharacter = triggerCharacter?.toString()
            )
        )

        return try {
            val paramsJson = json.encodeToJsonElement(params)
            val response = client.request("textDocument/completion", paramsJson)

            if (response == null) {
                return CompletionResult(emptyList())
            }

            parseCompletionResponse(response)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            println("LSP completion error: ${e.message}")
            CompletionResult(emptyList())
        }
    }

    /**
     * Resolve additional details for a completion item.
     *
     * Some servers return minimal items initially and require a
     * completionItem/resolve request for full details.
     *
     * @param item The item to resolve
     * @return The resolved item with additional details
     */
    suspend fun resolveCompletionItem(item: LspCompletionItem): LspCompletionItem {
        if (!client.isInitialized) {
            return item
        }

        return try {
            val itemJson = json.encodeToJsonElement(item)
            val response = client.request("completionItem/resolve", itemJson)

            if (response != null) {
                json.decodeFromJsonElement(response)
            } else {
                item
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            println("LSP completion resolve error: ${e.message}")
            item
        }
    }

    /**
     * Parse the completion response from the server.
     *
     * The response can be either a CompletionList or an array of CompletionItems.
     */
    private fun parseCompletionResponse(response: JsonElement): CompletionResult {
        return when (response) {
            is JsonArray -> {
                // Direct array of completion items
                val items = response.mapNotNull { parseCompletionItem(it) }
                CompletionResult(items.map { it.toEditorItem() })
            }
            is JsonObject -> {
                if (response.containsKey("items")) {
                    // CompletionList object
                    val list = json.decodeFromJsonElement<CompletionList>(response)
                    CompletionResult(
                        items = list.items.map { it.toEditorItem() },
                        isIncomplete = list.isIncomplete
                    )
                } else if (response.containsKey("label")) {
                    // Single completion item (unlikely but handle it)
                    val item = json.decodeFromJsonElement<LspCompletionItem>(response)
                    CompletionResult(listOf(item.toEditorItem()))
                } else {
                    CompletionResult(emptyList())
                }
            }
            else -> CompletionResult(emptyList())
        }
    }

    /**
     * Parse a single completion item from JSON.
     */
    private fun parseCompletionItem(element: JsonElement): LspCompletionItem? {
        return try {
            json.decodeFromJsonElement(element)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Convert an LSP completion item to an editor completion item.
     */
    private fun LspCompletionItem.toEditorItem(): CompletionItem {
        return CompletionItem(
            label = label,
            insertText = textEdit?.newText ?: insertText ?: label,
            kind = mapCompletionKind(kind),
            detail = detail,
            documentation = documentation,
            deprecated = deprecated ?: false,
            sortText = sortText,
            filterText = filterText
        )
    }

    /**
     * Map LSP completion kind to editor completion kind.
     */
    private fun mapCompletionKind(kind: Int?): CompletionKind {
        return when (kind) {
            CompletionItemKind.TEXT -> CompletionKind.TEXT
            CompletionItemKind.METHOD -> CompletionKind.METHOD
            CompletionItemKind.FUNCTION -> CompletionKind.FUNCTION
            CompletionItemKind.CONSTRUCTOR -> CompletionKind.CONSTRUCTOR
            CompletionItemKind.FIELD -> CompletionKind.FIELD
            CompletionItemKind.VARIABLE -> CompletionKind.VARIABLE
            CompletionItemKind.CLASS -> CompletionKind.CLASS
            CompletionItemKind.INTERFACE -> CompletionKind.INTERFACE
            CompletionItemKind.MODULE -> CompletionKind.MODULE
            CompletionItemKind.PROPERTY -> CompletionKind.PROPERTY
            CompletionItemKind.UNIT -> CompletionKind.UNIT
            CompletionItemKind.VALUE -> CompletionKind.VALUE
            CompletionItemKind.ENUM -> CompletionKind.ENUM
            CompletionItemKind.KEYWORD -> CompletionKind.KEYWORD
            CompletionItemKind.SNIPPET -> CompletionKind.SNIPPET
            CompletionItemKind.COLOR -> CompletionKind.COLOR
            CompletionItemKind.FILE -> CompletionKind.FILE
            CompletionItemKind.REFERENCE -> CompletionKind.REFERENCE
            CompletionItemKind.FOLDER -> CompletionKind.FOLDER
            CompletionItemKind.ENUM_MEMBER -> CompletionKind.ENUM_MEMBER
            CompletionItemKind.CONSTANT -> CompletionKind.CONSTANT
            CompletionItemKind.STRUCT -> CompletionKind.STRUCT
            CompletionItemKind.EVENT -> CompletionKind.EVENT
            CompletionItemKind.OPERATOR -> CompletionKind.OPERATOR
            CompletionItemKind.TYPE_PARAMETER -> CompletionKind.TYPE_PARAMETER
            else -> CompletionKind.TEXT
        }
    }
}

/**
 * Extension function to get trigger characters from server capabilities.
 */
fun ServerCapabilities.getCompletionTriggerCharacters(): List<Char> {
    val options = completionProvider ?: return emptyList()
    return options.triggerCharacters?.mapNotNull { it.firstOrNull() } ?: emptyList()
}

/**
 * Check if the server supports completion resolve.
 */
fun ServerCapabilities.supportsCompletionResolve(): Boolean {
    return completionProvider?.resolveProvider ?: false
}
