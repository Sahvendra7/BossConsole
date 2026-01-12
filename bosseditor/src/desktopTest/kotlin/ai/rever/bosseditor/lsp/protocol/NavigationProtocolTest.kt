package ai.rever.bosseditor.lsp.protocol

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NavigationProtocolTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun testDefinitionParamsSerialization() {
        val params = DefinitionParams(
            textDocument = TextDocumentIdentifier("file:///test.kt"),
            position = Position(10, 5)
        )

        val jsonString = json.encodeToString(DefinitionParams.serializer(), params)
        assertTrue(jsonString.contains("\"uri\":\"file:///test.kt\""))
        assertTrue(jsonString.contains("\"line\":10"))
        assertTrue(jsonString.contains("\"character\":5"))
    }

    @Test
    fun testDefinitionParamsDeserialization() {
        val jsonString = """
            {
                "textDocument": {"uri": "file:///test.kt"},
                "position": {"line": 5, "character": 10}
            }
        """.trimIndent()

        val params = json.decodeFromString(DefinitionParams.serializer(), jsonString)
        assertEquals("file:///test.kt", params.textDocument.uri)
        assertEquals(5, params.position.line)
        assertEquals(10, params.position.character)
    }

    @Test
    fun testTypeDefinitionParams() {
        val params = TypeDefinitionParams(
            textDocument = TextDocumentIdentifier("file:///test.kt"),
            position = Position(1, 2)
        )

        val jsonString = json.encodeToString(TypeDefinitionParams.serializer(), params)
        val decoded = json.decodeFromString(TypeDefinitionParams.serializer(), jsonString)

        assertEquals(params.textDocument.uri, decoded.textDocument.uri)
        assertEquals(params.position.line, decoded.position.line)
    }

    @Test
    fun testImplementationParams() {
        val params = ImplementationParams(
            textDocument = TextDocumentIdentifier("file:///test.kt"),
            position = Position(3, 4)
        )

        val jsonString = json.encodeToString(ImplementationParams.serializer(), params)
        val decoded = json.decodeFromString(ImplementationParams.serializer(), jsonString)

        assertEquals(params.textDocument.uri, decoded.textDocument.uri)
        assertEquals(params.position.line, decoded.position.line)
    }

    @Test
    fun testDeclarationParams() {
        val params = DeclarationParams(
            textDocument = TextDocumentIdentifier("file:///test.kt"),
            position = Position(5, 6)
        )

        val jsonString = json.encodeToString(DeclarationParams.serializer(), params)
        val decoded = json.decodeFromString(DeclarationParams.serializer(), jsonString)

        assertEquals(params.textDocument.uri, decoded.textDocument.uri)
        assertEquals(params.position.line, decoded.position.line)
    }

    @Test
    fun testReferenceParamsSerialization() {
        val params = ReferenceParams(
            textDocument = TextDocumentIdentifier("file:///test.kt"),
            position = Position(10, 5),
            context = ReferenceContext(includeDeclaration = true)
        )

        val jsonString = json.encodeToString(ReferenceParams.serializer(), params)
        assertTrue(jsonString.contains("\"includeDeclaration\":true"))
    }

    @Test
    fun testReferenceParamsDeserialization() {
        val jsonString = """
            {
                "textDocument": {"uri": "file:///test.kt"},
                "position": {"line": 5, "character": 10},
                "context": {"includeDeclaration": false}
            }
        """.trimIndent()

        val params = json.decodeFromString(ReferenceParams.serializer(), jsonString)
        assertEquals(false, params.context.includeDeclaration)
    }

    @Test
    fun testHoverParamsSerialization() {
        val params = HoverParams(
            textDocument = TextDocumentIdentifier("file:///test.kt"),
            position = Position(10, 5)
        )

        val jsonString = json.encodeToString(HoverParams.serializer(), params)
        assertTrue(jsonString.contains("\"uri\":\"file:///test.kt\""))
    }

    @Test
    fun testDocumentSymbolParams() {
        val params = DocumentSymbolParams(
            textDocument = TextDocumentIdentifier("file:///test.kt")
        )

        val jsonString = json.encodeToString(DocumentSymbolParams.serializer(), params)
        val decoded = json.decodeFromString(DocumentSymbolParams.serializer(), jsonString)

        assertEquals("file:///test.kt", decoded.textDocument.uri)
    }

    @Test
    fun testSymbolInformationDeserialization() {
        val jsonString = """
            {
                "name": "MyClass",
                "kind": 5,
                "location": {
                    "uri": "file:///test.kt",
                    "range": {
                        "start": {"line": 10, "character": 0},
                        "end": {"line": 50, "character": 1}
                    }
                },
                "containerName": "com.example"
            }
        """.trimIndent()

        val symbol = json.decodeFromString(SymbolInformation.serializer(), jsonString)
        assertEquals("MyClass", symbol.name)
        assertEquals(SymbolKind.CLASS, symbol.kind)
        assertEquals("file:///test.kt", symbol.location.uri)
        assertEquals("com.example", symbol.containerName)
    }

    @Test
    fun testDocumentSymbolDeserialization() {
        val jsonString = """
            {
                "name": "myFunction",
                "detail": "(x: Int, y: Int): Int",
                "kind": 12,
                "range": {
                    "start": {"line": 5, "character": 0},
                    "end": {"line": 10, "character": 1}
                },
                "selectionRange": {
                    "start": {"line": 5, "character": 4},
                    "end": {"line": 5, "character": 14}
                }
            }
        """.trimIndent()

        val symbol = json.decodeFromString(DocumentSymbol.serializer(), jsonString)
        assertEquals("myFunction", symbol.name)
        assertEquals("(x: Int, y: Int): Int", symbol.detail)
        assertEquals(SymbolKind.FUNCTION, symbol.kind)
        assertEquals(5, symbol.range.start.line)
        assertNull(symbol.children)
    }

    @Test
    fun testDocumentSymbolWithChildren() {
        val jsonString = """
            {
                "name": "MyClass",
                "kind": 5,
                "range": {
                    "start": {"line": 0, "character": 0},
                    "end": {"line": 20, "character": 1}
                },
                "selectionRange": {
                    "start": {"line": 0, "character": 6},
                    "end": {"line": 0, "character": 13}
                },
                "children": [
                    {
                        "name": "method1",
                        "kind": 6,
                        "range": {
                            "start": {"line": 2, "character": 4},
                            "end": {"line": 5, "character": 5}
                        },
                        "selectionRange": {
                            "start": {"line": 2, "character": 8},
                            "end": {"line": 2, "character": 15}
                        }
                    }
                ]
            }
        """.trimIndent()

        val symbol = json.decodeFromString(DocumentSymbol.serializer(), jsonString)
        assertEquals("MyClass", symbol.name)
        assertNotNull(symbol.children)
        val children = symbol.children
        assertEquals(1, children.size)
        assertEquals("method1", children[0].name)
        assertEquals(SymbolKind.METHOD, children[0].kind)
    }

    @Test
    fun testWorkspaceSymbolParams() {
        val params = WorkspaceSymbolParams(query = "MyClass")

        val jsonString = json.encodeToString(WorkspaceSymbolParams.serializer(), params)
        val decoded = json.decodeFromString(WorkspaceSymbolParams.serializer(), jsonString)

        assertEquals("MyClass", decoded.query)
    }

    @Test
    fun testRenameParams() {
        val params = RenameParams(
            textDocument = TextDocumentIdentifier("file:///test.kt"),
            position = Position(10, 5),
            newName = "newFunctionName"
        )

        val jsonString = json.encodeToString(RenameParams.serializer(), params)
        assertTrue(jsonString.contains("\"newName\":\"newFunctionName\""))
    }

    @Test
    fun testPrepareRenameParams() {
        val params = PrepareRenameParams(
            textDocument = TextDocumentIdentifier("file:///test.kt"),
            position = Position(10, 5)
        )

        val jsonString = json.encodeToString(PrepareRenameParams.serializer(), params)
        val decoded = json.decodeFromString(PrepareRenameParams.serializer(), jsonString)

        assertEquals("file:///test.kt", decoded.textDocument.uri)
    }

    @Test
    fun testPrepareRenameResult() {
        val jsonString = """
            {
                "range": {
                    "start": {"line": 10, "character": 5},
                    "end": {"line": 10, "character": 15}
                },
                "placeholder": "oldName"
            }
        """.trimIndent()

        val result = json.decodeFromString(PrepareRenameResult.serializer(), jsonString)
        assertEquals(10, result.range.start.line)
        assertEquals(5, result.range.start.character)
        assertEquals("oldName", result.placeholder)
    }

    @Test
    fun testSymbolKindValues() {
        assertEquals(1, SymbolKind.FILE)
        assertEquals(2, SymbolKind.MODULE)
        assertEquals(3, SymbolKind.NAMESPACE)
        assertEquals(4, SymbolKind.PACKAGE)
        assertEquals(5, SymbolKind.CLASS)
        assertEquals(6, SymbolKind.METHOD)
        assertEquals(7, SymbolKind.PROPERTY)
        assertEquals(8, SymbolKind.FIELD)
        assertEquals(9, SymbolKind.CONSTRUCTOR)
        assertEquals(10, SymbolKind.ENUM)
        assertEquals(11, SymbolKind.INTERFACE)
        assertEquals(12, SymbolKind.FUNCTION)
        assertEquals(13, SymbolKind.VARIABLE)
        assertEquals(14, SymbolKind.CONSTANT)
    }

    @Test
    fun testSymbolTagValues() {
        assertEquals(1, SymbolTag.DEPRECATED)
    }
}
