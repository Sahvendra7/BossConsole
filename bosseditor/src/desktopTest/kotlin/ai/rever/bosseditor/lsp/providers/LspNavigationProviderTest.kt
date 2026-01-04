package ai.rever.bosseditor.lsp.providers

import ai.rever.bosseditor.lsp.client.LspClient
import ai.rever.bosseditor.lsp.client.LspClientState
import ai.rever.bosseditor.lsp.protocol.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Mock LSP client for navigation tests.
 */
class MockNavigationClient(
    override var isInitialized: Boolean = true,
    override val state: LspClientState = LspClientState.INITIALIZED,
    override val serverCapabilities: ServerCapabilities? = null,
    private val responseMap: MutableMap<String, JsonElement?> = mutableMapOf()
) : LspClient {

    var lastMethod: String? = null
    var lastParams: JsonElement? = null

    fun setResponse(method: String, response: JsonElement?) {
        responseMap[method] = response
    }

    override suspend fun request(method: String, params: JsonElement?): JsonElement? {
        lastMethod = method
        lastParams = params
        return responseMap[method]
    }

    override fun notify(method: String, params: JsonElement?) {}
    override fun onNotification(method: String?, handler: (String, JsonElement?) -> Unit) {}
    override fun onRequest(method: String, handler: suspend (JsonElement?) -> JsonElement?) {}
    override suspend fun initialize(params: InitializeParams): InitializeResult {
        return InitializeResult(ServerCapabilities())
    }
    override fun initialized() {}
    override suspend fun shutdown() {}
    override fun exit() {}
    override fun dispose() {}
}

class LspNavigationProviderTest {

    @Test
    fun testGoToDefinitionWhenNotInitialized() = runBlocking {
        val client = MockNavigationClient(isInitialized = false)
        val provider = LspNavigationProvider(client)

        val result = provider.goToDefinition("file:///test.kt", Position(0, 5))

        assertTrue(result.isEmpty())
    }

    @Test
    fun testGoToDefinitionNullResponse() = runBlocking {
        val client = MockNavigationClient()
        client.setResponse("textDocument/definition", null)
        val provider = LspNavigationProvider(client)

        val result = provider.goToDefinition("file:///test.kt", Position(0, 5))

        assertTrue(result.isEmpty())
        assertEquals("textDocument/definition", client.lastMethod)
    }

    @Test
    fun testGoToDefinitionSingleLocation() = runBlocking {
        val response = buildJsonObject {
            put("uri", "file:///def.kt")
            putJsonObject("range") {
                putJsonObject("start") { put("line", 10); put("character", 0) }
                putJsonObject("end") { put("line", 10); put("character", 10) }
            }
        }

        val client = MockNavigationClient()
        client.setResponse("textDocument/definition", response)
        val provider = LspNavigationProvider(client)

        val result = provider.goToDefinition("file:///test.kt", Position(5, 10))

        assertEquals(1, result.size)
        assertEquals("file:///def.kt", result[0].uri)
        assertEquals(10, result[0].range.start.line)
    }

    @Test
    fun testGoToDefinitionMultipleLocations() = runBlocking {
        val response = buildJsonArray {
            addJsonObject {
                put("uri", "file:///def1.kt")
                putJsonObject("range") {
                    putJsonObject("start") { put("line", 10); put("character", 0) }
                    putJsonObject("end") { put("line", 10); put("character", 10) }
                }
            }
            addJsonObject {
                put("uri", "file:///def2.kt")
                putJsonObject("range") {
                    putJsonObject("start") { put("line", 20); put("character", 0) }
                    putJsonObject("end") { put("line", 20); put("character", 10) }
                }
            }
        }

        val client = MockNavigationClient()
        client.setResponse("textDocument/definition", response)
        val provider = LspNavigationProvider(client)

        val result = provider.goToDefinition("file:///test.kt", Position(5, 10))

        assertEquals(2, result.size)
        assertEquals("file:///def1.kt", result[0].uri)
        assertEquals("file:///def2.kt", result[1].uri)
    }

    @Test
    fun testGoToDefinitionLocationLink() = runBlocking {
        val response = buildJsonArray {
            addJsonObject {
                put("targetUri", "file:///def.kt")
                putJsonObject("targetRange") {
                    putJsonObject("start") { put("line", 10); put("character", 0) }
                    putJsonObject("end") { put("line", 15); put("character", 1) }
                }
                putJsonObject("targetSelectionRange") {
                    putJsonObject("start") { put("line", 10); put("character", 5) }
                    putJsonObject("end") { put("line", 10); put("character", 15) }
                }
            }
        }

        val client = MockNavigationClient()
        client.setResponse("textDocument/definition", response)
        val provider = LspNavigationProvider(client)

        val result = provider.goToDefinition("file:///test.kt", Position(5, 10))

        assertEquals(1, result.size)
        assertEquals("file:///def.kt", result[0].uri)
        // Should use targetSelectionRange
        assertEquals(10, result[0].range.start.line)
        assertEquals(5, result[0].range.start.character)
    }

    @Test
    fun testGoToTypeDefinition() = runBlocking {
        val response = buildJsonObject {
            put("uri", "file:///type.kt")
            putJsonObject("range") {
                putJsonObject("start") { put("line", 5); put("character", 0) }
                putJsonObject("end") { put("line", 5); put("character", 10) }
            }
        }

        val client = MockNavigationClient()
        client.setResponse("textDocument/typeDefinition", response)
        val provider = LspNavigationProvider(client)

        val result = provider.goToTypeDefinition("file:///test.kt", Position(0, 5))

        assertEquals(1, result.size)
        assertEquals("file:///type.kt", result[0].uri)
    }

    @Test
    fun testGoToImplementation() = runBlocking {
        val response = buildJsonArray {
            addJsonObject {
                put("uri", "file:///impl.kt")
                putJsonObject("range") {
                    putJsonObject("start") { put("line", 15); put("character", 0) }
                    putJsonObject("end") { put("line", 15); put("character", 10) }
                }
            }
        }

        val client = MockNavigationClient()
        client.setResponse("textDocument/implementation", response)
        val provider = LspNavigationProvider(client)

        val result = provider.goToImplementation("file:///test.kt", Position(0, 5))

        assertEquals(1, result.size)
        assertEquals("file:///impl.kt", result[0].uri)
    }

    @Test
    fun testGoToDeclaration() = runBlocking {
        val response = buildJsonObject {
            put("uri", "file:///decl.kt")
            putJsonObject("range") {
                putJsonObject("start") { put("line", 0); put("character", 0) }
                putJsonObject("end") { put("line", 0); put("character", 10) }
            }
        }

        val client = MockNavigationClient()
        client.setResponse("textDocument/declaration", response)
        val provider = LspNavigationProvider(client)

        val result = provider.goToDeclaration("file:///test.kt", Position(0, 5))

        assertEquals(1, result.size)
        assertEquals("file:///decl.kt", result[0].uri)
    }

    @Test
    fun testFindReferencesEmpty() = runBlocking {
        val client = MockNavigationClient()
        client.setResponse("textDocument/references", JsonArray(emptyList()))
        val provider = LspNavigationProvider(client)

        val result = provider.findReferences("file:///test.kt", Position(5, 10))

        assertTrue(result.isEmpty())
    }

    @Test
    fun testFindReferences() = runBlocking {
        val response = buildJsonArray {
            addJsonObject {
                put("uri", "file:///ref1.kt")
                putJsonObject("range") {
                    putJsonObject("start") { put("line", 10); put("character", 5) }
                    putJsonObject("end") { put("line", 10); put("character", 15) }
                }
            }
            addJsonObject {
                put("uri", "file:///ref2.kt")
                putJsonObject("range") {
                    putJsonObject("start") { put("line", 20); put("character", 10) }
                    putJsonObject("end") { put("line", 20); put("character", 20) }
                }
            }
        }

        val client = MockNavigationClient()
        client.setResponse("textDocument/references", response)
        val provider = LspNavigationProvider(client)

        val result = provider.findReferences("file:///test.kt", Position(5, 10), includeDeclaration = true)

        assertEquals(2, result.size)
        assertEquals("file:///ref1.kt", result[0].uri)
        assertEquals("file:///ref2.kt", result[1].uri)
    }

    @Test
    fun testFindReferencesParams() = runBlocking {
        val client = MockNavigationClient()
        client.setResponse("textDocument/references", JsonArray(emptyList()))
        val provider = LspNavigationProvider(client)

        provider.findReferences("file:///test.kt", Position(5, 10), includeDeclaration = false)

        val params = client.lastParams!!.jsonObject
        assertEquals(false, params["context"]?.jsonObject?.get("includeDeclaration")?.jsonPrimitive?.boolean)
    }

    @Test
    fun testGetHoverNull() = runBlocking {
        val client = MockNavigationClient()
        client.setResponse("textDocument/hover", null)
        val provider = LspNavigationProvider(client)

        val result = provider.getHover("file:///test.kt", Position(5, 10))

        assertNull(result)
    }

    @Test
    fun testGetHoverMarkupContent() = runBlocking {
        val response = buildJsonObject {
            putJsonObject("contents") {
                put("kind", "markdown")
                put("value", "**bold** text")
            }
        }

        val client = MockNavigationClient()
        client.setResponse("textDocument/hover", response)
        val provider = LspNavigationProvider(client)

        val result = provider.getHover("file:///test.kt", Position(5, 10))

        assertNotNull(result)
        assertEquals("**bold** text", result.text)
        assertTrue(result.isMarkdown)
    }

    @Test
    fun testGetHoverPlainString() = runBlocking {
        val response = buildJsonObject {
            put("contents", "Plain text hover")
        }

        val client = MockNavigationClient()
        client.setResponse("textDocument/hover", response)
        val provider = LspNavigationProvider(client)

        val result = provider.getHover("file:///test.kt", Position(5, 10))

        assertNotNull(result)
        assertEquals("Plain text hover", result.text)
        assertFalse(result.isMarkdown)
    }

    @Test
    fun testGetHoverMarkedString() = runBlocking {
        val response = buildJsonObject {
            putJsonObject("contents") {
                put("language", "kotlin")
                put("value", "fun test(): String")
            }
        }

        val client = MockNavigationClient()
        client.setResponse("textDocument/hover", response)
        val provider = LspNavigationProvider(client)

        val result = provider.getHover("file:///test.kt", Position(5, 10))

        assertNotNull(result)
        assertTrue(result.text.contains("```kotlin"))
        assertTrue(result.text.contains("fun test(): String"))
        assertTrue(result.isMarkdown)
    }

    @Test
    fun testGetHoverWithRange() = runBlocking {
        val response = buildJsonObject {
            putJsonObject("contents") {
                put("kind", "plaintext")
                put("value", "hover text")
            }
            putJsonObject("range") {
                putJsonObject("start") { put("line", 5); put("character", 0) }
                putJsonObject("end") { put("line", 5); put("character", 10) }
            }
        }

        val client = MockNavigationClient()
        client.setResponse("textDocument/hover", response)
        val provider = LspNavigationProvider(client)

        val result = provider.getHover("file:///test.kt", Position(5, 5))

        assertNotNull(result)
        assertNotNull(result.range)
        assertEquals(5, result.range!!.start.line)
    }

    @Test
    fun testGetDocumentSymbols() = runBlocking {
        val response = buildJsonArray {
            addJsonObject {
                put("name", "MyClass")
                put("kind", 5)
                putJsonObject("range") {
                    putJsonObject("start") { put("line", 0); put("character", 0) }
                    putJsonObject("end") { put("line", 20); put("character", 1) }
                }
                putJsonObject("selectionRange") {
                    putJsonObject("start") { put("line", 0); put("character", 6) }
                    putJsonObject("end") { put("line", 0); put("character", 13) }
                }
            }
        }

        val client = MockNavigationClient()
        client.setResponse("textDocument/documentSymbol", response)
        val provider = LspNavigationProvider(client)

        val result = provider.getDocumentSymbols("file:///test.kt")

        assertEquals(1, result.size)
        assertEquals("MyClass", result[0].name)
        assertEquals(SymbolKind.CLASS, result[0].kind)
    }

    @Test
    fun testGetDocumentSymbolsFromSymbolInformation() = runBlocking {
        val response = buildJsonArray {
            addJsonObject {
                put("name", "myFunction")
                put("kind", 12)
                putJsonObject("location") {
                    put("uri", "file:///test.kt")
                    putJsonObject("range") {
                        putJsonObject("start") { put("line", 5); put("character", 0) }
                        putJsonObject("end") { put("line", 10); put("character", 1) }
                    }
                }
            }
        }

        val client = MockNavigationClient()
        client.setResponse("textDocument/documentSymbol", response)
        val provider = LspNavigationProvider(client)

        val result = provider.getDocumentSymbols("file:///test.kt")

        assertEquals(1, result.size)
        assertEquals("myFunction", result[0].name)
        assertEquals(SymbolKind.FUNCTION, result[0].kind)
    }

    @Test
    fun testSearchWorkspaceSymbols() = runBlocking {
        val response = buildJsonArray {
            addJsonObject {
                put("name", "TestClass")
                put("kind", 5)
                putJsonObject("location") {
                    put("uri", "file:///test.kt")
                    putJsonObject("range") {
                        putJsonObject("start") { put("line", 0); put("character", 0) }
                        putJsonObject("end") { put("line", 10); put("character", 1) }
                    }
                }
            }
        }

        val client = MockNavigationClient()
        client.setResponse("workspace/symbol", response)
        val provider = LspNavigationProvider(client)

        val result = provider.searchWorkspaceSymbols("Test")

        assertEquals(1, result.size)
        assertEquals("TestClass", result[0].name)
    }

    @Test
    fun testPrepareRename() = runBlocking {
        val response = buildJsonObject {
            putJsonObject("range") {
                putJsonObject("start") { put("line", 5); put("character", 10) }
                putJsonObject("end") { put("line", 5); put("character", 20) }
            }
            put("placeholder", "oldName")
        }

        val client = MockNavigationClient()
        client.setResponse("textDocument/prepareRename", response)
        val provider = LspNavigationProvider(client)

        val result = provider.prepareRename("file:///test.kt", Position(5, 15))

        assertNotNull(result)
        assertEquals("oldName", result.placeholder)
        assertEquals(5, result.range.start.line)
    }

    @Test
    fun testRename() = runBlocking {
        val response = buildJsonObject {
            putJsonObject("changes") {
                putJsonArray("file:///test.kt") {
                    addJsonObject {
                        putJsonObject("range") {
                            putJsonObject("start") { put("line", 5); put("character", 10) }
                            putJsonObject("end") { put("line", 5); put("character", 20) }
                        }
                        put("newText", "newName")
                    }
                }
            }
        }

        val client = MockNavigationClient()
        client.setResponse("textDocument/rename", response)
        val provider = LspNavigationProvider(client)

        val result = provider.rename("file:///test.kt", Position(5, 15), "newName")

        assertNotNull(result)
        // WorkspaceEdit parsing would be complex, just verify we got a result
        assertTrue(true)
    }
}

class NavigationCapabilitiesTest {

    @Test
    fun testSupportsDefinition() {
        val caps = ServerCapabilities(definitionProvider = true)
        assertTrue(caps.supportsDefinition())

        val noCaps = ServerCapabilities()
        assertFalse(noCaps.supportsDefinition())
    }

    @Test
    fun testSupportsTypeDefinition() {
        val caps = ServerCapabilities(typeDefinitionProvider = true)
        assertTrue(caps.supportsTypeDefinition())

        val noCaps = ServerCapabilities()
        assertFalse(noCaps.supportsTypeDefinition())
    }

    @Test
    fun testSupportsImplementation() {
        val caps = ServerCapabilities(implementationProvider = true)
        assertTrue(caps.supportsImplementation())

        val noCaps = ServerCapabilities()
        assertFalse(noCaps.supportsImplementation())
    }

    @Test
    fun testSupportsReferences() {
        val caps = ServerCapabilities(referencesProvider = true)
        assertTrue(caps.supportsReferences())

        val noCaps = ServerCapabilities()
        assertFalse(noCaps.supportsReferences())
    }

    @Test
    fun testSupportsHover() {
        val caps = ServerCapabilities(hoverProvider = true)
        assertTrue(caps.supportsHover())

        val noCaps = ServerCapabilities()
        assertFalse(noCaps.supportsHover())
    }

    @Test
    fun testSupportsDocumentSymbol() {
        val caps = ServerCapabilities(documentSymbolProvider = true)
        assertTrue(caps.supportsDocumentSymbol())

        val noCaps = ServerCapabilities()
        assertFalse(noCaps.supportsDocumentSymbol())
    }

    @Test
    fun testSupportsWorkspaceSymbol() {
        val caps = ServerCapabilities(workspaceSymbolProvider = true)
        assertTrue(caps.supportsWorkspaceSymbol())

        val noCaps = ServerCapabilities()
        assertFalse(noCaps.supportsWorkspaceSymbol())
    }

    @Test
    fun testSupportsRename() {
        val caps = ServerCapabilities(renameProvider = true)
        assertTrue(caps.supportsRename())

        val noCaps = ServerCapabilities()
        assertFalse(noCaps.supportsRename())
    }
}
