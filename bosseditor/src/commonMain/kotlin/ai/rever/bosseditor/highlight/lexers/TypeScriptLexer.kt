package ai.rever.bosseditor.highlight.lexers

import ai.rever.bosseditor.highlight.*

/**
 * TypeScript syntax highlighting lexer.
 * Extends JavaScript with TypeScript-specific keywords and types.
 */
class TypeScriptLexer : JavaScriptLexer() {

    override val languageId: String = "typescript"
    override val fileExtensions: List<String> = listOf("ts", "tsx", "mts", "cts")

    companion object {
        private val TS_KEYWORDS = JavaScriptLexer.JS_KEYWORDS + setOf(
            "abstract", "as", "asserts", "declare", "enum", "implements",
            "interface", "is", "keyof", "module", "namespace", "never",
            "override", "private", "protected", "public", "readonly",
            "type", "unknown", "infer", "satisfies"
        )

        private val TS_TYPES = JavaScriptLexer.JS_TYPES + setOf(
            "any", "boolean", "number", "string", "symbol", "void", "never",
            "unknown", "undefined", "null", "object", "bigint",
            "Partial", "Required", "Readonly", "Record", "Pick", "Omit",
            "Exclude", "Extract", "NonNullable", "Parameters", "ReturnType",
            "InstanceType", "ThisType", "Awaited", "Uppercase", "Lowercase",
            "Capitalize", "Uncapitalize"
        )

        private val TS_DECORATORS = setOf(
            "Component", "Injectable", "Module", "Directive", "Pipe",
            "Input", "Output", "ViewChild", "ViewChildren", "ContentChild",
            "HostListener", "HostBinding"
        )
    }

    override fun classifyIdentifier(identifier: String): TokenType {
        return when {
            identifier in TS_KEYWORDS -> TokenType.KEYWORD
            identifier == "true" || identifier == "false" -> TokenType.BOOLEAN
            identifier == "null" || identifier == "undefined" -> TokenType.NULL
            identifier in JS_CONSTANTS -> TokenType.CONSTANT
            identifier in TS_TYPES -> TokenType.TYPE
            identifier in TS_DECORATORS -> TokenType.ANNOTATION
            identifier in JS_FUNCTIONS -> TokenType.FUNCTION_CALL
            else -> TokenType.IDENTIFIER
        }
    }
}
