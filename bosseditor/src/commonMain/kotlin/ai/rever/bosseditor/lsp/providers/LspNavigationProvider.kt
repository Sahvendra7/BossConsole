package ai.rever.bosseditor.lsp.providers

import ai.rever.bosseditor.logging.EditorLogCategory
import ai.rever.bosseditor.logging.EditorLogger
import ai.rever.bosseditor.lsp.client.LspClient
import ai.rever.bosseditor.lsp.protocol.*
import kotlinx.serialization.json.*
import kotlin.coroutines.cancellation.CancellationException

private val logger = EditorLogger.forComponent("LspNavigationProvider")

/**
 * LSP-based navigation provider.
 *
 * Provides code navigation features using the Language Server Protocol:
 * - Go to Definition
 * - Go to Type Definition
 * - Go to Implementation
 * - Go to Declaration
 * - Find References
 * - Hover documentation
 * - Document symbols
 * - Workspace symbols
 *
 * ## Usage
 * ```kotlin
 * val provider = LspNavigationProvider(lspClient)
 *
 * // Go to definition
 * val definitions = provider.goToDefinition(uri, Position(10, 5))
 * definitions.forEach { location ->
 *     openFile(location.uri, location.range)
 * }
 *
 * // Get hover info
 * val hover = provider.getHover(uri, Position(10, 5))
 * if (hover != null) {
 *     showHoverPopup(hover)
 * }
 * ```
 */
class LspNavigationProvider(
    private val client: LspClient
) {
    private val json = Json { ignoreUnknownKeys = true }

    // ========================================================================
    // Go to Definition
    // ========================================================================

    /**
     * Go to the definition of the symbol at the given position.
     *
     * @param uri The document URI
     * @param position The position in the document
     * @return List of definition locations (may be empty)
     */
    suspend fun goToDefinition(uri: String, position: Position): List<Location> {
        return goToLocation("textDocument/definition", uri, position)
    }

    /**
     * Go to the type definition of the symbol at the given position.
     *
     * @param uri The document URI
     * @param position The position in the document
     * @return List of type definition locations (may be empty)
     */
    suspend fun goToTypeDefinition(uri: String, position: Position): List<Location> {
        return goToLocation("textDocument/typeDefinition", uri, position)
    }

    /**
     * Go to the implementation of the symbol at the given position.
     *
     * @param uri The document URI
     * @param position The position in the document
     * @return List of implementation locations (may be empty)
     */
    suspend fun goToImplementation(uri: String, position: Position): List<Location> {
        return goToLocation("textDocument/implementation", uri, position)
    }

    /**
     * Go to the declaration of the symbol at the given position.
     *
     * @param uri The document URI
     * @param position The position in the document
     * @return List of declaration locations (may be empty)
     */
    suspend fun goToDeclaration(uri: String, position: Position): List<Location> {
        return goToLocation("textDocument/declaration", uri, position)
    }

    /**
     * Generic helper for go to location requests.
     */
    private suspend fun goToLocation(method: String, uri: String, position: Position): List<Location> {
        if (!client.isInitialized) {
            return emptyList()
        }

        val params = DefinitionParams(
            textDocument = TextDocumentIdentifier(uri),
            position = position
        )

        return try {
            val paramsJson = json.encodeToJsonElement(params)
            val response = client.request(method, paramsJson)

            if (response == null) {
                emptyList()
            } else {
                parseLocationResponse(response)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error(EditorLogCategory.NAVIGATION, "LSP $method error", error = e)
            emptyList()
        }
    }

    /**
     * Parse a location response which can be:
     * - null (no result)
     * - Location (single location)
     * - List<Location> (multiple locations)
     * - List<LocationLink> (location links)
     */
    private fun parseLocationResponse(response: JsonElement): List<Location> {
        return when (response) {
            is JsonNull -> emptyList()
            is JsonArray -> {
                if (response.isEmpty()) {
                    emptyList()
                } else {
                    // Check if it's LocationLink or Location
                    val first = response[0].jsonObject
                    if (first.containsKey("targetUri")) {
                        // LocationLink array
                        response.mapNotNull { parseLocationLink(it) }
                            .map { Location(it.targetUri, it.targetSelectionRange) }
                    } else {
                        // Location array
                        response.mapNotNull { parseLocation(it) }
                    }
                }
            }
            is JsonObject -> {
                if (response.containsKey("targetUri")) {
                    // Single LocationLink
                    parseLocationLink(response)?.let {
                        listOf(Location(it.targetUri, it.targetSelectionRange))
                    } ?: emptyList()
                } else {
                    // Single Location
                    parseLocation(response)?.let { listOf(it) } ?: emptyList()
                }
            }
            else -> emptyList()
        }
    }

    private fun parseLocation(element: JsonElement): Location? {
        return try {
            json.decodeFromJsonElement(element)
        } catch (e: Exception) {
            null
        }
    }

    private fun parseLocationLink(element: JsonElement): LocationLink? {
        return try {
            json.decodeFromJsonElement(element)
        } catch (e: Exception) {
            null
        }
    }

    // ========================================================================
    // Find References
    // ========================================================================

    /**
     * Find all references to the symbol at the given position.
     *
     * @param uri The document URI
     * @param position The position in the document
     * @param includeDeclaration Whether to include the declaration itself
     * @return List of reference locations
     */
    suspend fun findReferences(
        uri: String,
        position: Position,
        includeDeclaration: Boolean = true
    ): List<Location> {
        if (!client.isInitialized) {
            return emptyList()
        }

        val params = ReferenceParams(
            textDocument = TextDocumentIdentifier(uri),
            position = position,
            context = ReferenceContext(includeDeclaration = includeDeclaration)
        )

        return try {
            val paramsJson = json.encodeToJsonElement(params)
            val response = client.request("textDocument/references", paramsJson)

            if (response == null || response is JsonNull) {
                emptyList()
            } else if (response is JsonArray) {
                response.mapNotNull { parseLocation(it) }
            } else {
                emptyList()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error(EditorLogCategory.NAVIGATION, "LSP references error", error = e)
            emptyList()
        }
    }

    // ========================================================================
    // Hover
    // ========================================================================

    /**
     * Get hover information at the given position.
     *
     * @param uri The document URI
     * @param position The position in the document
     * @return Hover information, or null if not available
     */
    suspend fun getHover(uri: String, position: Position): SimpleHoverContent? {
        if (!client.isInitialized) {
            return null
        }

        val params = HoverParams(
            textDocument = TextDocumentIdentifier(uri),
            position = position
        )

        return try {
            val paramsJson = json.encodeToJsonElement(params)
            val response = client.request("textDocument/hover", paramsJson)

            if (response == null || response is JsonNull) {
                null
            } else {
                parseHoverResponse(response)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error(EditorLogCategory.NAVIGATION, "LSP hover error", error = e)
            null
        }
    }

    /**
     * Parse hover response which can have various content formats.
     */
    private fun parseHoverResponse(response: JsonElement): SimpleHoverContent? {
        if (response !is JsonObject) return null

        val contents = response["contents"] ?: return null
        val range = response["range"]?.let {
            try {
                json.decodeFromJsonElement<Range>(it)
            } catch (e: Exception) {
                null
            }
        }

        val (text, isMarkdown) = parseHoverContents(contents)
        if (text.isEmpty()) return null

        return SimpleHoverContent(
            text = text,
            isMarkdown = isMarkdown,
            range = range
        )
    }

    /**
     * Parse hover contents which can be:
     * - string
     * - MarkupContent { kind, value }
     * - MarkedString { language, value }
     * - Array of the above
     */
    private fun parseHoverContents(contents: JsonElement): Pair<String, Boolean> {
        return when (contents) {
            is JsonPrimitive -> {
                contents.contentOrNull?.let { Pair(it, false) } ?: Pair("", false)
            }
            is JsonObject -> {
                if (contents.containsKey("kind")) {
                    // MarkupContent
                    val kind = contents["kind"]?.jsonPrimitive?.contentOrNull
                    val value = contents["value"]?.jsonPrimitive?.contentOrNull ?: ""
                    Pair(value, kind == "markdown")
                } else if (contents.containsKey("language")) {
                    // MarkedString { language, value }
                    val language = contents["language"]?.jsonPrimitive?.contentOrNull ?: ""
                    val value = contents["value"]?.jsonPrimitive?.contentOrNull ?: ""
                    Pair("```$language\n$value\n```", true)
                } else if (contents.containsKey("value")) {
                    // Plain { value }
                    Pair(contents["value"]?.jsonPrimitive?.contentOrNull ?: "", false)
                } else {
                    Pair("", false)
                }
            }
            is JsonArray -> {
                // Array of contents
                val parts = contents.mapNotNull { element ->
                    val (text, _) = parseHoverContents(element)
                    text.takeIf { it.isNotEmpty() }
                }
                Pair(parts.joinToString("\n\n---\n\n"), true)
            }
        }
    }

    // ========================================================================
    // Document Symbols
    // ========================================================================

    /**
     * Get all symbols in a document.
     *
     * @param uri The document URI
     * @return List of document symbols (hierarchical) or symbol information (flat)
     */
    suspend fun getDocumentSymbols(uri: String): List<DocumentSymbol> {
        if (!client.isInitialized) {
            return emptyList()
        }

        val params = DocumentSymbolParams(
            textDocument = TextDocumentIdentifier(uri)
        )

        return try {
            val paramsJson = json.encodeToJsonElement(params)
            val response = client.request("textDocument/documentSymbol", paramsJson)

            if (response == null || response is JsonNull) {
                emptyList()
            } else if (response is JsonArray) {
                if (response.isEmpty()) {
                    emptyList()
                } else {
                    val first = response[0].jsonObject
                    if (first.containsKey("location")) {
                        // SymbolInformation array - convert to DocumentSymbol
                        response.mapNotNull { parseSymbolInformation(it) }
                            .map { it.toDocumentSymbol() }
                    } else {
                        // DocumentSymbol array
                        response.mapNotNull { parseDocumentSymbol(it) }
                    }
                }
            } else {
                emptyList()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error(EditorLogCategory.NAVIGATION, "LSP documentSymbol error", error = e)
            emptyList()
        }
    }

    private fun parseDocumentSymbol(element: JsonElement): DocumentSymbol? {
        return try {
            json.decodeFromJsonElement(element)
        } catch (e: Exception) {
            null
        }
    }

    private fun parseSymbolInformation(element: JsonElement): SymbolInformation? {
        return try {
            json.decodeFromJsonElement(element)
        } catch (e: Exception) {
            null
        }
    }

    // ========================================================================
    // Workspace Symbols
    // ========================================================================

    /**
     * Search for symbols across the workspace.
     *
     * @param query The search query
     * @return List of matching symbols
     */
    suspend fun searchWorkspaceSymbols(query: String): List<SymbolInformation> {
        if (!client.isInitialized) {
            return emptyList()
        }

        val params = WorkspaceSymbolParams(query = query)

        return try {
            val paramsJson = json.encodeToJsonElement(params)
            val response = client.request("workspace/symbol", paramsJson)

            if (response == null || response is JsonNull) {
                emptyList()
            } else if (response is JsonArray) {
                response.mapNotNull { parseSymbolInformation(it) }
            } else {
                emptyList()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error(EditorLogCategory.NAVIGATION, "LSP workspace/symbol error", error = e)
            emptyList()
        }
    }

    // ========================================================================
    // Rename
    // ========================================================================

    /**
     * Prepare a rename operation.
     *
     * @param uri The document URI
     * @param position The position of the symbol to rename
     * @return The range and placeholder text, or null if rename not allowed
     */
    suspend fun prepareRename(uri: String, position: Position): PrepareRenameResult? {
        if (!client.isInitialized) {
            return null
        }

        val params = PrepareRenameParams(
            textDocument = TextDocumentIdentifier(uri),
            position = position
        )

        return try {
            val paramsJson = json.encodeToJsonElement(params)
            val response = client.request("textDocument/prepareRename", paramsJson)

            if (response == null || response is JsonNull) {
                null
            } else {
                json.decodeFromJsonElement(response)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error(EditorLogCategory.NAVIGATION, "LSP prepareRename error", error = e)
            null
        }
    }

    /**
     * Perform a rename operation.
     *
     * @param uri The document URI
     * @param position The position of the symbol to rename
     * @param newName The new name
     * @return Workspace edit with all changes, or null on failure
     */
    suspend fun rename(uri: String, position: Position, newName: String): WorkspaceEdit? {
        if (!client.isInitialized) {
            return null
        }

        val params = RenameParams(
            textDocument = TextDocumentIdentifier(uri),
            position = position,
            newName = newName
        )

        return try {
            val paramsJson = json.encodeToJsonElement(params)
            val response = client.request("textDocument/rename", paramsJson)

            if (response == null || response is JsonNull) {
                null
            } else {
                json.decodeFromJsonElement(response)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error(EditorLogCategory.NAVIGATION, "LSP rename error", error = e)
            null
        }
    }
}

/**
 * Convert SymbolInformation to DocumentSymbol.
 */
private fun SymbolInformation.toDocumentSymbol(): DocumentSymbol {
    return DocumentSymbol(
        name = name,
        detail = containerName,
        kind = kind,
        tags = tags,
        deprecated = deprecated,
        range = location.range,
        selectionRange = location.range,
        children = null
    )
}

/**
 * Extension to check if server supports definition.
 */
fun ServerCapabilities.supportsDefinition(): Boolean {
    return definitionProvider != null
}

/**
 * Extension to check if server supports type definition.
 */
fun ServerCapabilities.supportsTypeDefinition(): Boolean {
    return typeDefinitionProvider != null
}

/**
 * Extension to check if server supports implementation.
 */
fun ServerCapabilities.supportsImplementation(): Boolean {
    return implementationProvider != null
}

/**
 * Extension to check if server supports references.
 */
fun ServerCapabilities.supportsReferences(): Boolean {
    return referencesProvider != null
}

/**
 * Extension to check if server supports hover.
 */
fun ServerCapabilities.supportsHover(): Boolean {
    return hoverProvider != null
}

/**
 * Extension to check if server supports document symbols.
 */
fun ServerCapabilities.supportsDocumentSymbol(): Boolean {
    return documentSymbolProvider != null
}

/**
 * Extension to check if server supports workspace symbols.
 */
fun ServerCapabilities.supportsWorkspaceSymbol(): Boolean {
    return workspaceSymbolProvider != null
}

/**
 * Extension to check if server supports rename.
 */
fun ServerCapabilities.supportsRename(): Boolean {
    return renameProvider != null
}
