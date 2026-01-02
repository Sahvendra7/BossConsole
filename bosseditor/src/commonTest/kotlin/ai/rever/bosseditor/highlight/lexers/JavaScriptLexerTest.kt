package ai.rever.bosseditor.highlight.lexers

import ai.rever.bosseditor.highlight.*
import kotlin.test.Test
import kotlin.test.assertEquals

class JavaScriptLexerTest : LexerTestBase() {
    private val lexer = JavaScriptLexer()

    @Test
    fun testLanguageId() {
        assertEquals("javascript", lexer.languageId)
    }

    @Test
    fun testFileExtensions() {
        assertEquals(listOf("js", "mjs", "cjs", "jsx"), lexer.fileExtensions)
    }

    @Test
    fun testKeywords() {
        val line = "const x = function() { return this; }"
        val tokens = tokenizeLine(lexer, line)

        assertTokenType(line, tokens, "const", TokenType.KEYWORD)
        assertTokenType(line, tokens, "function", TokenType.KEYWORD)
        assertTokenType(line, tokens, "return", TokenType.KEYWORD)
        assertTokenType(line, tokens, "this", TokenType.KEYWORD)
    }

    @Test
    fun testVariableDeclarations() {
        val line = "let a = 1; var b = 2; const c = 3;"
        val tokens = tokenizeLine(lexer, line)

        assertTokenType(line, tokens, "let", TokenType.KEYWORD)
        assertTokenType(line, tokens, "var", TokenType.KEYWORD)
        assertTokenType(line, tokens, "const", TokenType.KEYWORD)
    }

    @Test
    fun testStringLiteral() {
        val line = """const s = "hello world";"""
        val tokens = tokenizeLine(lexer, line)

        assertHasTokenType(tokens, TokenType.STRING)
    }

    @Test
    fun testSingleQuoteString() {
        val line = "const s = 'hello world';"
        val tokens = tokenizeLine(lexer, line)

        assertHasTokenType(tokens, TokenType.STRING)
    }

    @Test
    fun testTemplateLiteral() {
        val line = "const msg = `Hello \${name}!`;"
        val tokens = tokenizeLine(lexer, line)

        assertHasTokenType(tokens, TokenType.STRING)
    }

    @Test
    fun testMultilineTemplateLiteral() {
        val lines = listOf(
            "const text = `",
            "  multi-line",
            "  template",
            "`;"
        )
        val results = tokenizeLines(lexer, lines)

        assertEquals(LexerState.IN_MULTILINE_STRING, results[0].endState)
        assertEquals(LexerState.IN_MULTILINE_STRING, results[1].endState)
        assertEquals(LexerState.IN_MULTILINE_STRING, results[2].endState)
        assertEquals(LexerState.NORMAL, results[3].endState)
    }

    @Test
    fun testLineComment() {
        val line = "const x = 5; // comment"
        val tokens = tokenizeLine(lexer, line)

        assertHasTokenType(tokens, TokenType.COMMENT)
    }

    @Test
    fun testBlockComment() {
        val line = "/* block comment */ const x = 5;"
        val tokens = tokenizeLine(lexer, line)

        assertHasTokenType(tokens, TokenType.COMMENT_BLOCK)
    }

    @Test
    fun testMultilineBlockComment() {
        val lines = listOf(
            "/* start",
            "middle",
            "end */ const x = 5;"
        )
        val results = tokenizeLines(lexer, lines)

        assertEquals(LexerState.IN_BLOCK_COMMENT, results[0].endState)
        assertEquals(LexerState.IN_BLOCK_COMMENT, results[1].endState)
        assertEquals(LexerState.NORMAL, results[2].endState)
    }

    @Test
    fun testJsDoc() {
        val line = "/** @param {string} name */"
        val tokens = tokenizeLine(lexer, line)

        // JSDoc is treated as block comment in basic lexer
        assertHasTokenType(tokens, TokenType.COMMENT_BLOCK)
    }

    @Test
    fun testRegexLiteral() {
        val line = "const regex = /[a-z]+/gi;"
        val tokens = tokenizeLine(lexer, line)

        assertHasTokenType(tokens, TokenType.REGEX)
    }

    @Test
    fun testNumbers() {
        val line = "const a = 42; const b = 3.14; const c = 1e-5;"
        val tokens = tokenizeLine(lexer, line)

        assertEquals(3, countTokenType(tokens, TokenType.NUMBER))
    }

    @Test
    fun testBigInt() {
        val line = "const big = 9007199254740991n;"
        val tokens = tokenizeLine(lexer, line)

        assertHasTokenType(tokens, TokenType.NUMBER)
    }

    @Test
    fun testHexNumber() {
        val line = "const hex = 0xFF00;"
        val tokens = tokenizeLine(lexer, line)

        assertHasTokenType(tokens, TokenType.NUMBER)
    }

    @Test
    fun testBinaryNumber() {
        val line = "const bin = 0b1010;"
        val tokens = tokenizeLine(lexer, line)

        assertHasTokenType(tokens, TokenType.NUMBER)
    }

    @Test
    fun testBooleanLiterals() {
        val line = "const a = true; const b = false;"
        val tokens = tokenizeLine(lexer, line)

        assertTokenType(line, tokens, "true", TokenType.BOOLEAN)
        assertTokenType(line, tokens, "false", TokenType.BOOLEAN)
    }

    @Test
    fun testNullAndUndefined() {
        val line = "const a = null; const b = undefined;"
        val tokens = tokenizeLine(lexer, line)

        assertTokenType(line, tokens, "null", TokenType.NULL)
        assertTokenType(line, tokens, "undefined", TokenType.CONSTANT)
    }

    @Test
    fun testArrowFunction() {
        val line = "const fn = (x) => x * 2;"
        val tokens = tokenizeLine(lexer, line)

        assertEndState(tokens, LexerState.NORMAL)
    }

    @Test
    fun testAsyncAwait() {
        val line = "async function fetch() { await response; }"
        val tokens = tokenizeLine(lexer, line)

        assertTokenType(line, tokens, "async", TokenType.KEYWORD)
        assertTokenType(line, tokens, "await", TokenType.KEYWORD)
    }

    @Test
    fun testClassSyntax() {
        val line = "class Foo extends Bar { constructor() { super(); } }"
        val tokens = tokenizeLine(lexer, line)

        assertTokenType(line, tokens, "class", TokenType.KEYWORD)
        assertTokenType(line, tokens, "extends", TokenType.KEYWORD)
        assertTokenType(line, tokens, "super", TokenType.KEYWORD)
    }

    @Test
    fun testImportExport() {
        val line = "import { foo } from 'module'; export default bar;"
        val tokens = tokenizeLine(lexer, line)

        assertTokenType(line, tokens, "import", TokenType.KEYWORD)
        assertTokenType(line, tokens, "from", TokenType.KEYWORD)
        assertTokenType(line, tokens, "export", TokenType.KEYWORD)
        assertTokenType(line, tokens, "default", TokenType.KEYWORD)
    }

    @Test
    fun testSpread() {
        val line = "const arr = [...items, ...more];"
        val tokens = tokenizeLine(lexer, line)

        assertEndState(tokens, LexerState.NORMAL)
    }

    @Test
    fun testDestructuring() {
        val line = "const { a, b } = obj; const [x, y] = arr;"
        val tokens = tokenizeLine(lexer, line)

        assertEndState(tokens, LexerState.NORMAL)
    }

    @Test
    fun testOptionalChaining() {
        val line = "const val = obj?.prop?.nested;"
        val tokens = tokenizeLine(lexer, line)

        assertEndState(tokens, LexerState.NORMAL)
    }

    @Test
    fun testNullishCoalescing() {
        val line = "const val = a ?? b;"
        val tokens = tokenizeLine(lexer, line)

        assertHasTokenType(tokens, TokenType.OPERATOR)
    }

    @Test
    fun testGlobalObjects() {
        val line = "console.log(JSON.parse(Math.PI));"
        val tokens = tokenizeLine(lexer, line)

        assertTokenType(line, tokens, "console", TokenType.TYPE)
        assertTokenType(line, tokens, "JSON", TokenType.TYPE)
        assertTokenType(line, tokens, "Math", TokenType.TYPE)
    }
}
