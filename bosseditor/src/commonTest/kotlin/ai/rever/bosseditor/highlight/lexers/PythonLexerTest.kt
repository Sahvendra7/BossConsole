package ai.rever.bosseditor.highlight.lexers

import ai.rever.bosseditor.highlight.*
import kotlin.test.Test
import kotlin.test.assertEquals

class PythonLexerTest : LexerTestBase() {
    private val lexer = PythonLexer()

    @Test
    fun testLanguageId() {
        assertEquals("python", lexer.languageId)
    }

    @Test
    fun testFileExtensions() {
        assertEquals(listOf("py", "pyw", "pyi"), lexer.fileExtensions)
    }

    @Test
    fun testKeywords() {
        val line = "def foo(x): return x if x else None"
        val tokens = tokenizeLine(lexer, line)

        assertTokenType(line, tokens, "def", TokenType.KEYWORD)
        assertTokenType(line, tokens, "return", TokenType.KEYWORD)
        assertTokenType(line, tokens, "if", TokenType.KEYWORD)
        assertTokenType(line, tokens, "else", TokenType.KEYWORD)
    }

    @Test
    fun testClassDefinition() {
        val line = "class MyClass(BaseClass):"
        val tokens = tokenizeLine(lexer, line)

        assertTokenType(line, tokens, "class", TokenType.KEYWORD)
    }

    @Test
    fun testStringLiteral() {
        val line = """name = "hello world" """
        val tokens = tokenizeLine(lexer, line)

        assertHasTokenType(tokens, TokenType.STRING)
    }

    @Test
    fun testSingleQuoteString() {
        val line = "name = 'hello world'"
        val tokens = tokenizeLine(lexer, line)

        assertHasTokenType(tokens, TokenType.STRING)
    }

    @Test
    fun testFString() {
        val line = """msg = f"Hello {name}!" """
        val tokens = tokenizeLine(lexer, line)

        assertHasTokenType(tokens, TokenType.STRING)
    }

    @Test
    fun testRawString() {
        val line = """path = r"C:\Users\name" """
        val tokens = tokenizeLine(lexer, line)

        assertHasTokenType(tokens, TokenType.STRING)
    }

    @Test
    fun testTripleQuoteString() {
        val lines = listOf(
            "text = \"\"\"",
            "multi-line",
            "string\"\"\""
        )
        val results = tokenizeLines(lexer, lines)

        assertEquals(LexerState.IN_MULTILINE_STRING, results[0].endState)
        assertEquals(LexerState.IN_MULTILINE_STRING, results[1].endState)
        assertEquals(LexerState.NORMAL, results[2].endState)
    }

    @Test
    fun testDocstring() {
        val line = "'''This is a docstring'''"
        val tokens = tokenizeLine(lexer, line)

        assertHasTokenType(tokens, TokenType.STRING)
    }

    @Test
    fun testHashComment() {
        val line = "x = 5  # this is a comment"
        val tokens = tokenizeLine(lexer, line)

        assertHasTokenType(tokens, TokenType.COMMENT)
    }

    @Test
    fun testDecorator() {
        val line = "@staticmethod"
        val tokens = tokenizeLine(lexer, line)

        assertHasTokenType(tokens, TokenType.ANNOTATION)
    }

    @Test
    fun testDecoratorWithArgs() {
        val line = "@decorator(arg1, arg2)"
        val tokens = tokenizeLine(lexer, line)

        assertHasTokenType(tokens, TokenType.ANNOTATION)
    }

    @Test
    fun testNumbers() {
        val line = "a = 42; b = 3.14; c = 1e-5"
        val tokens = tokenizeLine(lexer, line)

        assertEquals(3, countTokenType(tokens, TokenType.NUMBER))
    }

    @Test
    fun testHexNumber() {
        val line = "hex_val = 0xFF00"
        val tokens = tokenizeLine(lexer, line)

        assertHasTokenType(tokens, TokenType.NUMBER)
    }

    @Test
    fun testOctalNumber() {
        val line = "oct_val = 0o755"
        val tokens = tokenizeLine(lexer, line)

        assertHasTokenType(tokens, TokenType.NUMBER)
    }

    @Test
    fun testBinaryNumber() {
        val line = "bin_val = 0b1010"
        val tokens = tokenizeLine(lexer, line)

        assertHasTokenType(tokens, TokenType.NUMBER)
    }

    @Test
    fun testBooleanLiterals() {
        val line = "a = True"
        val tokens = tokenizeLine(lexer, line)

        // True/False are in KEYWORDS set, so classified as KEYWORD
        // (KEYWORDS check comes first in classifyIdentifier)
        assertHasTokenType(tokens, TokenType.KEYWORD)
    }

    @Test
    fun testNoneLiteral() {
        val line = "x = None"
        val tokens = tokenizeLine(lexer, line)

        // None is in KEYWORDS set, so classified as KEYWORD
        assertHasTokenType(tokens, TokenType.KEYWORD)
    }

    @Test
    fun testBuiltinFunctions() {
        val line = "print(len(list))"
        val tokens = tokenizeLine(lexer, line)

        assertTokenType(line, tokens, "print", TokenType.FUNCTION_CALL)
        assertTokenType(line, tokens, "len", TokenType.FUNCTION_CALL)
    }

    @Test
    fun testSelfParameter() {
        val line = "def method(self, x):"
        val tokens = tokenizeLine(lexer, line)

        // self is just an identifier in basic lexer
        assertTokenType(line, tokens, "self", TokenType.IDENTIFIER)
    }

    @Test
    fun testDunderMethod() {
        val line = "def __init__(self):"
        val tokens = tokenizeLine(lexer, line)

        // __dunder__ methods are classified as CONSTANT
        assertTokenType(line, tokens, "__init__", TokenType.CONSTANT)
    }

    @Test
    fun testComprehension() {
        val line = "[x for x in range(10) if x % 2 == 0]"
        val tokens = tokenizeLine(lexer, line)

        assertTokenType(line, tokens, "for", TokenType.KEYWORD)
        assertTokenType(line, tokens, "in", TokenType.KEYWORD)
        assertTokenType(line, tokens, "if", TokenType.KEYWORD)
    }

    @Test
    fun testLambda() {
        val line = "fn = lambda x: x * 2"
        val tokens = tokenizeLine(lexer, line)

        assertTokenType(line, tokens, "lambda", TokenType.KEYWORD)
    }

    @Test
    fun testAsyncAwait() {
        val line = "async def fetch(): await response"
        val tokens = tokenizeLine(lexer, line)

        assertTokenType(line, tokens, "async", TokenType.KEYWORD)
        assertTokenType(line, tokens, "await", TokenType.KEYWORD)
    }

    @Test
    fun testOperators() {
        val line = "x = a + b - c * d / e // f % g ** h"
        val tokens = tokenizeLine(lexer, line)

        assertHasTokenType(tokens, TokenType.OPERATOR)
    }

    @Test
    fun testWalrus() {
        val line = "if (n := len(data)) > 10:"
        val tokens = tokenizeLine(lexer, line)

        // Should handle := operator
        assertEndState(tokens, LexerState.NORMAL)
    }

    @Test
    fun testTypeHint() {
        val line = "def greet(name: str) -> str:"
        val tokens = tokenizeLine(lexer, line)

        // str is in BUILTINS (checked before TYPES), so classified as FUNCTION_CALL
        assertHasTokenType(tokens, TokenType.FUNCTION_CALL)
    }
}
