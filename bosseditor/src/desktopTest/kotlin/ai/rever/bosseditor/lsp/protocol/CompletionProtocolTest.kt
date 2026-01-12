package ai.rever.bosseditor.lsp.protocol

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CompletionProtocolTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun testCompletionParamsSerialization() {
        val params = CompletionParams(
            textDocument = TextDocumentIdentifier("file:///test.kt"),
            position = Position(10, 5),
            context = CompletionContext(
                triggerKind = CompletionTriggerKind.TRIGGER_CHARACTER,
                triggerCharacter = "."
            )
        )

        val jsonString = json.encodeToString(CompletionParams.serializer(), params)
        assertTrue(jsonString.contains("\"uri\":\"file:///test.kt\""))
        assertTrue(jsonString.contains("\"line\":10"))
        assertTrue(jsonString.contains("\"character\":5"))
        assertTrue(jsonString.contains("\"triggerKind\":2"))
        assertTrue(jsonString.contains("\"triggerCharacter\":\".\""))
    }

    @Test
    fun testCompletionParamsDeserialization() {
        val jsonString = """
            {
                "textDocument": {"uri": "file:///test.kt"},
                "position": {"line": 5, "character": 10},
                "context": {"triggerKind": 1}
            }
        """.trimIndent()

        val params = json.decodeFromString(CompletionParams.serializer(), jsonString)
        assertEquals("file:///test.kt", params.textDocument.uri)
        assertEquals(5, params.position.line)
        assertEquals(10, params.position.character)
        assertEquals(CompletionTriggerKind.INVOKED, params.context?.triggerKind)
        assertNull(params.context?.triggerCharacter)
    }

    @Test
    fun testCompletionContextWithTrigger() {
        val context = CompletionContext(
            triggerKind = CompletionTriggerKind.TRIGGER_CHARACTER,
            triggerCharacter = ":"
        )

        val jsonString = json.encodeToString(CompletionContext.serializer(), context)
        val decoded = json.decodeFromString(CompletionContext.serializer(), jsonString)

        assertEquals(CompletionTriggerKind.TRIGGER_CHARACTER, decoded.triggerKind)
        assertEquals(":", decoded.triggerCharacter)
    }

    @Test
    fun testCompletionListDeserialization() {
        val jsonString = """
            {
                "isIncomplete": true,
                "items": [
                    {"label": "item1", "kind": 3},
                    {"label": "item2", "kind": 6, "detail": "String"}
                ]
            }
        """.trimIndent()

        val list = json.decodeFromString(CompletionList.serializer(), jsonString)
        assertTrue(list.isIncomplete)
        assertEquals(2, list.items.size)
        assertEquals("item1", list.items[0].label)
        assertEquals(CompletionItemKind.FUNCTION, list.items[0].kind)
        assertEquals("item2", list.items[1].label)
        assertEquals("String", list.items[1].detail)
    }

    @Test
    fun testLspCompletionItemFullDeserialization() {
        val jsonString = """
            {
                "label": "println",
                "kind": 3,
                "detail": "(message: Any?) -> Unit",
                "documentation": "Prints a message to stdout",
                "deprecated": false,
                "preselect": true,
                "sortText": "0001",
                "filterText": "println",
                "insertText": "println($1)",
                "insertTextFormat": 2
            }
        """.trimIndent()

        val item = json.decodeFromString(LspCompletionItem.serializer(), jsonString)
        assertEquals("println", item.label)
        assertEquals(CompletionItemKind.FUNCTION, item.kind)
        assertEquals("(message: Any?) -> Unit", item.detail)
        assertEquals("Prints a message to stdout", item.documentation)
        assertEquals(false, item.deprecated)
        assertEquals(true, item.preselect)
        assertEquals("0001", item.sortText)
        assertEquals("println", item.filterText)
        assertEquals("println(\$1)", item.insertText)
        assertEquals(InsertTextFormat.SNIPPET, item.insertTextFormat)
    }

    @Test
    fun testLspCompletionItemMinimal() {
        val jsonString = """{"label": "test"}"""

        val item = json.decodeFromString(LspCompletionItem.serializer(), jsonString)
        assertEquals("test", item.label)
        assertNull(item.kind)
        assertNull(item.detail)
        assertNull(item.documentation)
        assertNull(item.insertText)
    }

    @Test
    fun testCompletionItemWithTextEdit() {
        val jsonString = """
            {
                "label": "import",
                "kind": 14,
                "textEdit": {
                    "range": {
                        "start": {"line": 0, "character": 0},
                        "end": {"line": 0, "character": 6}
                    },
                    "newText": "import kotlin.io.*"
                }
            }
        """.trimIndent()

        val item = json.decodeFromString(LspCompletionItem.serializer(), jsonString)
        assertEquals("import", item.label)
        assertEquals(CompletionItemKind.KEYWORD, item.kind)
        assertNotNull(item.textEdit)
        val textEdit = item.textEdit
        assertEquals(0, textEdit.range.start.line)
        assertEquals("import kotlin.io.*", textEdit.newText)
    }

    @Test
    fun testCompletionItemKindValues() {
        assertEquals(1, CompletionItemKind.TEXT)
        assertEquals(2, CompletionItemKind.METHOD)
        assertEquals(3, CompletionItemKind.FUNCTION)
        assertEquals(4, CompletionItemKind.CONSTRUCTOR)
        assertEquals(5, CompletionItemKind.FIELD)
        assertEquals(6, CompletionItemKind.VARIABLE)
        assertEquals(7, CompletionItemKind.CLASS)
        assertEquals(8, CompletionItemKind.INTERFACE)
        assertEquals(9, CompletionItemKind.MODULE)
        assertEquals(10, CompletionItemKind.PROPERTY)
        assertEquals(11, CompletionItemKind.UNIT)
        assertEquals(12, CompletionItemKind.VALUE)
        assertEquals(13, CompletionItemKind.ENUM)
        assertEquals(14, CompletionItemKind.KEYWORD)
        assertEquals(15, CompletionItemKind.SNIPPET)
    }

    @Test
    fun testCompletionTriggerKindValues() {
        assertEquals(1, CompletionTriggerKind.INVOKED)
        assertEquals(2, CompletionTriggerKind.TRIGGER_CHARACTER)
        assertEquals(3, CompletionTriggerKind.TRIGGER_FOR_INCOMPLETE_COMPLETIONS)
    }

    @Test
    fun testInsertTextFormatValues() {
        assertEquals(1, InsertTextFormat.PLAIN_TEXT)
        assertEquals(2, InsertTextFormat.SNIPPET)
    }

    @Test
    fun testCompletionItemWithAdditionalTextEdits() {
        val jsonString = """
            {
                "label": "useEffect",
                "kind": 3,
                "additionalTextEdits": [
                    {
                        "range": {
                            "start": {"line": 0, "character": 0},
                            "end": {"line": 0, "character": 0}
                        },
                        "newText": "import { useEffect } from 'react';\n"
                    }
                ]
            }
        """.trimIndent()

        val item = json.decodeFromString(LspCompletionItem.serializer(), jsonString)
        assertNotNull(item.additionalTextEdits)
        val additionalTextEdits = item.additionalTextEdits
        assertEquals(1, additionalTextEdits.size)
        assertTrue(additionalTextEdits[0].newText.contains("useEffect"))
    }

    @Test
    fun testCompletionItemWithCommitCharacters() {
        val jsonString = """
            {
                "label": "toString",
                "kind": 2,
                "commitCharacters": [".", "("]
            }
        """.trimIndent()

        val item = json.decodeFromString(LspCompletionItem.serializer(), jsonString)
        assertNotNull(item.commitCharacters)
        val commitCharacters = item.commitCharacters
        assertEquals(2, commitCharacters.size)
        assertTrue(commitCharacters.contains("."))
        assertTrue(commitCharacters.contains("("))
    }
}
