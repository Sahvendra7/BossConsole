package ai.rever.bosseditor.lsp.providers

import ai.rever.bosseditor.core.EditorPosition
import ai.rever.bosseditor.features.CompletionKind
import ai.rever.bosseditor.lsp.client.LspClient
import ai.rever.bosseditor.lsp.client.LspClientState
import ai.rever.bosseditor.lsp.protocol.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Mock LSP client for testing.
 */
class MockLspClient(
    override var isInitialized: Boolean = true,
    override val state: LspClientState = LspClientState.INITIALIZED,
    override val serverCapabilities: ServerCapabilities? = null,
    private val completionResponse: JsonElement? = null
) : LspClient {

    var lastMethod: String? = null
    var lastParams: JsonElement? = null

    override suspend fun request(method: String, params: JsonElement?): JsonElement? {
        lastMethod = method
        lastParams = params
        return completionResponse
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

class LspCompletionProviderTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun testGetCompletionsWhenNotInitialized() = runBlocking {
        val client = MockLspClient(isInitialized = false)
        val provider = LspCompletionProvider(client, "file:///test.kt")

        val result = provider.getCompletions(EditorPosition(0, 5), "test", '.')

        assertTrue(result.items.isEmpty())
    }

    @Test
    fun testGetCompletionsEmptyResponse() = runBlocking {
        val client = MockLspClient(completionResponse = null)
        val provider = LspCompletionProvider(client, "file:///test.kt")

        val result = provider.getCompletions(EditorPosition(0, 5), "test")

        assertTrue(result.items.isEmpty())
    }

    @Test
    fun testGetCompletionsWithArray() = runBlocking {
        val response = buildJsonArray {
            addJsonObject {
                put("label", "item1")
                put("kind", 3)
            }
            addJsonObject {
                put("label", "item2")
                put("kind", 6)
            }
        }

        val client = MockLspClient(completionResponse = response)
        val provider = LspCompletionProvider(client, "file:///test.kt")

        val result = provider.getCompletions(EditorPosition(0, 5), "")

        assertEquals(2, result.items.size)
        assertEquals("item1", result.items[0].label)
        assertEquals(CompletionKind.FUNCTION, result.items[0].kind)
        assertEquals("item2", result.items[1].label)
        assertEquals(CompletionKind.VARIABLE, result.items[1].kind)
    }

    @Test
    fun testGetCompletionsWithList() = runBlocking {
        val response = buildJsonObject {
            put("isIncomplete", true)
            putJsonArray("items") {
                addJsonObject {
                    put("label", "getValue")
                    put("kind", 2)
                    put("detail", "(): String")
                }
            }
        }

        val client = MockLspClient(completionResponse = response)
        val provider = LspCompletionProvider(client, "file:///test.kt")

        val result = provider.getCompletions(EditorPosition(5, 10), "get")

        assertTrue(result.isIncomplete)
        assertEquals(1, result.items.size)
        assertEquals("getValue", result.items[0].label)
        assertEquals(CompletionKind.METHOD, result.items[0].kind)
        assertEquals("(): String", result.items[0].detail)
    }

    @Test
    fun testGetCompletionsWithTextEdit() = runBlocking {
        val response = buildJsonArray {
            addJsonObject {
                put("label", "import")
                put("kind", 14)
                putJsonObject("textEdit") {
                    putJsonObject("range") {
                        putJsonObject("start") { put("line", 0); put("character", 0) }
                        putJsonObject("end") { put("line", 0); put("character", 6) }
                    }
                    put("newText", "import kotlin.io.*")
                }
            }
        }

        val client = MockLspClient(completionResponse = response)
        val provider = LspCompletionProvider(client, "file:///test.kt")

        val result = provider.getCompletions(EditorPosition(0, 6), "import")

        assertEquals(1, result.items.size)
        assertEquals("import kotlin.io.*", result.items[0].insertText)
    }

    @Test
    fun testGetCompletionsWithInsertText() = runBlocking {
        val response = buildJsonArray {
            addJsonObject {
                put("label", "println")
                put("kind", 3)
                put("insertText", "println(\$1)")
            }
        }

        val client = MockLspClient(completionResponse = response)
        val provider = LspCompletionProvider(client, "file:///test.kt")

        val result = provider.getCompletions(EditorPosition(0, 5), "print")

        assertEquals(1, result.items.size)
        assertEquals("println", result.items[0].label)
        assertEquals("println(\$1)", result.items[0].insertText)
    }

    @Test
    fun testGetCompletionsRequestParams() = runBlocking {
        val client = MockLspClient(completionResponse = JsonArray(emptyList()))
        val provider = LspCompletionProvider(client, "file:///test.kt")

        provider.getCompletions(EditorPosition(10, 15), "get", '.')

        assertEquals("textDocument/completion", client.lastMethod)

        val params = client.lastParams!!.jsonObject
        assertEquals("file:///test.kt", params["textDocument"]?.jsonObject?.get("uri")?.jsonPrimitive?.content)
        assertEquals(10, params["position"]?.jsonObject?.get("line")?.jsonPrimitive?.int)
        assertEquals(15, params["position"]?.jsonObject?.get("character")?.jsonPrimitive?.int)
        assertEquals(CompletionTriggerKind.TRIGGER_CHARACTER, params["context"]?.jsonObject?.get("triggerKind")?.jsonPrimitive?.int)
        assertEquals(".", params["context"]?.jsonObject?.get("triggerCharacter")?.jsonPrimitive?.content)
    }

    @Test
    fun testGetCompletionsManualInvocation() = runBlocking {
        val client = MockLspClient(completionResponse = JsonArray(emptyList()))
        val provider = LspCompletionProvider(client, "file:///test.kt")

        provider.getCompletions(EditorPosition(5, 10), "test", null)

        val params = client.lastParams!!.jsonObject
        assertEquals(CompletionTriggerKind.INVOKED, params["context"]?.jsonObject?.get("triggerKind")?.jsonPrimitive?.int)
    }

    @Test
    fun testCompletionKindMapping() = runBlocking {
        val response = buildJsonArray {
            addJsonObject { put("label", "text"); put("kind", 1) }
            addJsonObject { put("label", "method"); put("kind", 2) }
            addJsonObject { put("label", "function"); put("kind", 3) }
            addJsonObject { put("label", "constructor"); put("kind", 4) }
            addJsonObject { put("label", "field"); put("kind", 5) }
            addJsonObject { put("label", "variable"); put("kind", 6) }
            addJsonObject { put("label", "class"); put("kind", 7) }
            addJsonObject { put("label", "interface"); put("kind", 8) }
            addJsonObject { put("label", "module"); put("kind", 9) }
            addJsonObject { put("label", "property"); put("kind", 10) }
            addJsonObject { put("label", "keyword"); put("kind", 14) }
            addJsonObject { put("label", "snippet"); put("kind", 15) }
        }

        val client = MockLspClient(completionResponse = response)
        val provider = LspCompletionProvider(client, "file:///test.kt")

        val result = provider.getCompletions(EditorPosition(0, 0), "")

        assertEquals(CompletionKind.TEXT, result.items[0].kind)
        assertEquals(CompletionKind.METHOD, result.items[1].kind)
        assertEquals(CompletionKind.FUNCTION, result.items[2].kind)
        assertEquals(CompletionKind.CONSTRUCTOR, result.items[3].kind)
        assertEquals(CompletionKind.FIELD, result.items[4].kind)
        assertEquals(CompletionKind.VARIABLE, result.items[5].kind)
        assertEquals(CompletionKind.CLASS, result.items[6].kind)
        assertEquals(CompletionKind.INTERFACE, result.items[7].kind)
        assertEquals(CompletionKind.MODULE, result.items[8].kind)
        assertEquals(CompletionKind.PROPERTY, result.items[9].kind)
        assertEquals(CompletionKind.KEYWORD, result.items[10].kind)
        assertEquals(CompletionKind.SNIPPET, result.items[11].kind)
    }

    @Test
    fun testCompletionItemWithNullKind() = runBlocking {
        val response = buildJsonArray {
            addJsonObject { put("label", "unknown") }
        }

        val client = MockLspClient(completionResponse = response)
        val provider = LspCompletionProvider(client, "file:///test.kt")

        val result = provider.getCompletions(EditorPosition(0, 0), "")

        assertEquals(CompletionKind.TEXT, result.items[0].kind)
    }

    @Test
    fun testCompletionItemPreservesMetadata() = runBlocking {
        val response = buildJsonArray {
            addJsonObject {
                put("label", "testFunc")
                put("kind", 3)
                put("detail", "(x: Int, y: Int): Int")
                put("documentation", "Adds two numbers")
                put("deprecated", true)
                put("sortText", "0001")
                put("filterText", "test_func")
            }
        }

        val client = MockLspClient(completionResponse = response)
        val provider = LspCompletionProvider(client, "file:///test.kt")

        val result = provider.getCompletions(EditorPosition(0, 0), "")
        val item = result.items[0]

        assertEquals("testFunc", item.label)
        assertEquals("(x: Int, y: Int): Int", item.detail)
        assertEquals("Adds two numbers", item.documentation)
        assertTrue(item.deprecated)
        assertEquals("0001", item.sortText)
        assertEquals("test_func", item.filterText)
    }
}

class ServerCapabilitiesExtensionsTest {

    @Test
    fun testGetCompletionTriggerCharacters() {
        val capabilities = ServerCapabilities(
            completionProvider = CompletionOptions(
                triggerCharacters = listOf(".", ":", "<")
            )
        )

        val triggers = capabilities.getCompletionTriggerCharacters()
        assertEquals(3, triggers.size)
        assertTrue(triggers.contains('.'))
        assertTrue(triggers.contains(':'))
        assertTrue(triggers.contains('<'))
    }

    @Test
    fun testGetCompletionTriggerCharactersEmpty() {
        val capabilities = ServerCapabilities(completionProvider = null)
        val triggers = capabilities.getCompletionTriggerCharacters()
        assertTrue(triggers.isEmpty())
    }

    @Test
    fun testSupportsCompletionResolveTrue() {
        val capabilities = ServerCapabilities(
            completionProvider = CompletionOptions(
                resolveProvider = true
            )
        )

        assertTrue(capabilities.supportsCompletionResolve())
    }

    @Test
    fun testSupportsCompletionResolveFalse() {
        val capabilities = ServerCapabilities(
            completionProvider = CompletionOptions(
                resolveProvider = false
            )
        )

        assertFalse(capabilities.supportsCompletionResolve())
    }

    @Test
    fun testSupportsCompletionResolveNotPresent() {
        val capabilities = ServerCapabilities(
            completionProvider = CompletionOptions()
        )

        assertFalse(capabilities.supportsCompletionResolve())
    }
}
