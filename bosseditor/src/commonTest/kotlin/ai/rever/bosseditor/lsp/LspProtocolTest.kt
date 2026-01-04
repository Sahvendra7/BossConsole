package ai.rever.bosseditor.lsp

import ai.rever.bosseditor.lsp.protocol.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Tests for LSP protocol types serialization/deserialization.
 */
class LspProtocolTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        prettyPrint = false
    }

    // ==================== JSON-RPC Tests ====================

    @Test
    fun testRequestMessageSerialization() {
        val request = RequestMessage(
            id = 1,
            method = "textDocument/completion",
            params = JsonPrimitive("test")
        )

        val jsonString = json.encodeToString(request)
        val decoded = json.decodeFromString<RequestMessage>(jsonString)

        assertEquals(request.id, decoded.id)
        assertEquals(request.method, decoded.method)
        assertEquals(request.params, decoded.params)
        assertEquals("2.0", decoded.jsonrpc)
    }

    @Test
    fun testResponseMessageSerialization() {
        val response = ResponseMessage(
            id = 1,
            result = JsonPrimitive("success")
        )

        val jsonString = json.encodeToString(response)
        val decoded = json.decodeFromString<ResponseMessage>(jsonString)

        assertEquals(response.id, decoded.id)
        assertEquals(response.result, decoded.result)
        assertNull(decoded.error)
    }

    @Test
    fun testResponseErrorSerialization() {
        val error = ResponseError(
            code = ErrorCodes.METHOD_NOT_FOUND,
            message = "Method not found"
        )
        val response = ResponseMessage(
            id = 1,
            error = error
        )

        val jsonString = json.encodeToString(response)
        val decoded = json.decodeFromString<ResponseMessage>(jsonString)

        assertNull(decoded.result)
        assertNotNull(decoded.error)
        assertEquals(ErrorCodes.METHOD_NOT_FOUND, decoded.error!!.code)
        assertEquals("Method not found", decoded.error!!.message)
    }

    @Test
    fun testNotificationMessageSerialization() {
        val notification = NotificationMessage(
            method = "textDocument/didOpen",
            params = JsonPrimitive("test")
        )

        val jsonString = json.encodeToString(notification)
        val decoded = json.decodeFromString<NotificationMessage>(jsonString)

        assertEquals(notification.method, decoded.method)
        assertEquals(notification.params, decoded.params)
    }

    // ==================== LSP Types Tests ====================

    @Test
    fun testPositionSerialization() {
        val position = Position(line = 10, character = 5)
        val jsonString = json.encodeToString(position)
        val decoded = json.decodeFromString<Position>(jsonString)

        assertEquals(10, decoded.line)
        assertEquals(5, decoded.character)
    }

    @Test
    fun testRangeSerialization() {
        val range = Range(
            start = Position(0, 0),
            end = Position(10, 20)
        )
        val jsonString = json.encodeToString(range)
        val decoded = json.decodeFromString<Range>(jsonString)

        assertEquals(0, decoded.start.line)
        assertEquals(0, decoded.start.character)
        assertEquals(10, decoded.end.line)
        assertEquals(20, decoded.end.character)
    }

    @Test
    fun testTextDocumentIdentifierSerialization() {
        val identifier = TextDocumentIdentifier(uri = "file:///path/to/file.py")
        val jsonString = json.encodeToString(identifier)
        val decoded = json.decodeFromString<TextDocumentIdentifier>(jsonString)

        assertEquals("file:///path/to/file.py", decoded.uri)
    }

    @Test
    fun testTextDocumentItemSerialization() {
        val item = TextDocumentItem(
            uri = "file:///path/to/file.py",
            languageId = "python",
            version = 1,
            text = "print('hello')"
        )
        val jsonString = json.encodeToString(item)
        val decoded = json.decodeFromString<TextDocumentItem>(jsonString)

        assertEquals("file:///path/to/file.py", decoded.uri)
        assertEquals("python", decoded.languageId)
        assertEquals(1, decoded.version)
        assertEquals("print('hello')", decoded.text)
    }

    @Test
    fun testLocationSerialization() {
        val location = Location(
            uri = "file:///path/to/file.py",
            range = Range(Position(10, 5), Position(10, 15))
        )
        val jsonString = json.encodeToString(location)
        val decoded = json.decodeFromString<Location>(jsonString)

        assertEquals("file:///path/to/file.py", decoded.uri)
        assertEquals(10, decoded.range.start.line)
    }

    // ==================== Document Sync Tests ====================

    @Test
    fun testDidOpenTextDocumentParamsSerialization() {
        val params = DidOpenTextDocumentParams(
            textDocument = TextDocumentItem(
                uri = "file:///test.py",
                languageId = "python",
                version = 1,
                text = "# test"
            )
        )
        val jsonString = json.encodeToString(params)
        val decoded = json.decodeFromString<DidOpenTextDocumentParams>(jsonString)

        assertEquals("file:///test.py", decoded.textDocument.uri)
        assertEquals("python", decoded.textDocument.languageId)
    }

    @Test
    fun testDidChangeTextDocumentParamsSerialization() {
        val params = DidChangeTextDocumentParams(
            textDocument = VersionedTextDocumentIdentifier(
                uri = "file:///test.py",
                version = 2
            ),
            contentChanges = listOf(
                TextDocumentContentChangeEvent.fullDocument("new content")
            )
        )
        val jsonString = json.encodeToString(params)
        val decoded = json.decodeFromString<DidChangeTextDocumentParams>(jsonString)

        assertEquals("file:///test.py", decoded.textDocument.uri)
        assertEquals(2, decoded.textDocument.version)
        assertEquals(1, decoded.contentChanges.size)
        assertEquals("new content", decoded.contentChanges[0].text)
    }

    @Test
    fun testIncrementalChangeEvent() {
        val change = TextDocumentContentChangeEvent.incremental(
            range = Range(Position(5, 0), Position(5, 10)),
            text = "replaced"
        )

        assertEquals(5, change.range?.start?.line)
        assertEquals("replaced", change.text)
    }

    // ==================== Diagnostics Tests ====================

    @Test
    fun testDiagnosticSerialization() {
        val diagnostic = Diagnostic(
            range = Range(Position(10, 0), Position(10, 20)),
            severity = DiagnosticSeverity.ERROR,
            code = "E001",
            source = "pylint",
            message = "Undefined variable 'x'"
        )
        val jsonString = json.encodeToString(diagnostic)
        val decoded = json.decodeFromString<Diagnostic>(jsonString)

        assertEquals(DiagnosticSeverity.ERROR, decoded.severity)
        assertEquals("E001", decoded.code)
        assertEquals("pylint", decoded.source)
        assertEquals("Undefined variable 'x'", decoded.message)
    }

    @Test
    fun testPublishDiagnosticsParamsSerialization() {
        val params = PublishDiagnosticsParams(
            uri = "file:///test.py",
            version = 1,
            diagnostics = listOf(
                Diagnostic(
                    range = Range(Position(0, 0), Position(0, 5)),
                    severity = DiagnosticSeverity.WARNING,
                    message = "Unused import"
                )
            )
        )
        val jsonString = json.encodeToString(params)
        val decoded = json.decodeFromString<PublishDiagnosticsParams>(jsonString)

        assertEquals("file:///test.py", decoded.uri)
        assertEquals(1, decoded.version)
        assertEquals(1, decoded.diagnostics.size)
    }

    // ==================== Semantic Tokens Tests ====================

    @Test
    fun testSemanticTokensParamsSerialization() {
        val params = SemanticTokensParams(
            textDocument = TextDocumentIdentifier("file:///test.py")
        )
        val jsonString = json.encodeToString(params)
        val decoded = json.decodeFromString<SemanticTokensParams>(jsonString)

        assertEquals("file:///test.py", decoded.textDocument.uri)
    }

    @Test
    fun testSemanticTokensSerialization() {
        val tokens = SemanticTokens(
            resultId = "result-1",
            data = listOf(0, 0, 5, 0, 0, 0, 6, 3, 1, 0)
        )
        val jsonString = json.encodeToString(tokens)
        val decoded = json.decodeFromString<SemanticTokens>(jsonString)

        assertEquals("result-1", decoded.resultId)
        assertEquals(10, decoded.data.size)
    }

    @Test
    fun testSemanticTokenDecoder() {
        // Encoded tokens: [deltaLine, deltaStartChar, length, tokenType, tokenModifiers]
        // Token 1: line 0, char 0, length 5, type 0 (namespace), modifiers 0
        // Token 2: line 0, char 6 (delta 6), length 3, type 1 (type), modifiers 0
        // Token 3: line 1, char 0 (new line, delta 1), length 10, type 2 (class), modifiers 1 (declaration)
        val data = listOf(
            0, 0, 5, 0, 0,   // namespace at (0, 0)
            0, 6, 3, 1, 0,   // type at (0, 6)
            1, 0, 10, 2, 1   // class declaration at (1, 0)
        )

        val legend = SemanticTokensLegend(
            tokenTypes = SemanticTokenTypes.ALL,
            tokenModifiers = SemanticTokenModifiers.ALL
        )

        val decoded = SemanticTokenDecoder.decode(data, legend)

        assertEquals(3, decoded.size)

        // First token
        assertEquals(0, decoded[0].line)
        assertEquals(0, decoded[0].startChar)
        assertEquals(5, decoded[0].length)
        assertEquals(SemanticTokenTypes.NAMESPACE, decoded[0].tokenType)
        assertEquals(emptySet<String>(), decoded[0].modifiers)

        // Second token
        assertEquals(0, decoded[1].line)
        assertEquals(6, decoded[1].startChar)
        assertEquals(3, decoded[1].length)
        assertEquals(SemanticTokenTypes.TYPE, decoded[1].tokenType)

        // Third token (new line)
        assertEquals(1, decoded[2].line)
        assertEquals(0, decoded[2].startChar)
        assertEquals(10, decoded[2].length)
        assertEquals(SemanticTokenTypes.CLASS, decoded[2].tokenType)
        assertEquals(setOf(SemanticTokenModifiers.DECLARATION), decoded[2].modifiers)
    }

    @Test
    fun testSemanticTokenDecoderEmptyData() {
        val decoded = SemanticTokenDecoder.decode(
            emptyList(),
            SemanticTokensLegend(SemanticTokenTypes.ALL, SemanticTokenModifiers.ALL)
        )
        assertEquals(0, decoded.size)
    }

    @Test
    fun testGetTokensForLine() {
        val tokens = listOf(
            DecodedSemanticToken(0, 0, 5, "function", emptySet()),
            DecodedSemanticToken(0, 10, 3, "variable", emptySet()),
            DecodedSemanticToken(1, 0, 8, "keyword", emptySet()),
            DecodedSemanticToken(2, 5, 4, "string", emptySet())
        )

        val line0Tokens = SemanticTokenDecoder.getTokensForLine(tokens, 0)
        val line1Tokens = SemanticTokenDecoder.getTokensForLine(tokens, 1)
        val line3Tokens = SemanticTokenDecoder.getTokensForLine(tokens, 3)

        assertEquals(2, line0Tokens.size)
        assertEquals(1, line1Tokens.size)
        assertEquals(0, line3Tokens.size)
    }

    // ==================== Initialize Tests ====================

    @Test
    fun testInitializeParamsSerialization() {
        val params = InitializeParams(
            processId = 12345,
            clientInfo = ClientInfo("BossEditor", "1.0.0"),
            rootUri = "file:///workspace",
            capabilities = ClientCapabilities()
        )
        val jsonString = json.encodeToString(params)
        val decoded = json.decodeFromString<InitializeParams>(jsonString)

        assertEquals(12345, decoded.processId)
        assertEquals("BossEditor", decoded.clientInfo?.name)
        assertEquals("file:///workspace", decoded.rootUri)
    }

    @Test
    fun testInitializeResultSerialization() {
        val result = InitializeResult(
            capabilities = ServerCapabilities(
                textDocumentSync = TextDocumentSyncOptions(
                    openClose = true,
                    change = TextDocumentSyncKind.INCREMENTAL
                ),
                completionProvider = CompletionOptions(
                    triggerCharacters = listOf(".", ":")
                ),
                hoverProvider = true,
                definitionProvider = true
            ),
            serverInfo = ServerInfo("pylsp", "1.0.0")
        )
        val jsonString = json.encodeToString(result)
        val decoded = json.decodeFromString<InitializeResult>(jsonString)

        assertEquals(true, decoded.capabilities.textDocumentSync?.openClose)
        assertEquals(TextDocumentSyncKind.INCREMENTAL, decoded.capabilities.textDocumentSync?.change)
        assertEquals(listOf(".", ":"), decoded.capabilities.completionProvider?.triggerCharacters)
        assertEquals(true, decoded.capabilities.hoverProvider)
        assertEquals("pylsp", decoded.serverInfo?.name)
    }
}
