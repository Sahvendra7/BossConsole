package ai.rever.bosseditor.highlight.lexers

import ai.rever.bosseditor.highlight.*
import kotlin.test.Test
import kotlin.test.assertEquals

class JavaLexerTest : LexerTestBase() {
    private val lexer = JavaLexer()

    @Test
    fun testLanguageId() {
        assertEquals("java", lexer.languageId)
    }

    @Test
    fun testFileExtensions() {
        assertEquals(listOf("java"), lexer.fileExtensions)
    }

    @Test
    fun testKeywords() {
        val line = "public class Foo extends Bar implements Baz"
        val tokens = tokenizeLine(lexer, line)

        assertTokenType(line, tokens, "public", TokenType.KEYWORD)
        assertTokenType(line, tokens, "class", TokenType.KEYWORD)
        assertTokenType(line, tokens, "extends", TokenType.KEYWORD)
        assertTokenType(line, tokens, "implements", TokenType.KEYWORD)
    }

    @Test
    fun testPrimitiveTypes() {
        val line = "int x; boolean flag; double value;"
        val tokens = tokenizeLine(lexer, line)

        assertTokenType(line, tokens, "int", TokenType.KEYWORD)
        assertTokenType(line, tokens, "boolean", TokenType.KEYWORD)
        assertTokenType(line, tokens, "double", TokenType.KEYWORD)
    }

    @Test
    fun testStringLiteral() {
        val line = """String s = "hello world";"""
        val tokens = tokenizeLine(lexer, line)

        assertHasTokenType(tokens, TokenType.STRING)
        assertTokenType(line, tokens, """"hello world"""", TokenType.STRING)
    }

    @Test
    fun testCharLiteral() {
        val line = "char c = 'a';"
        val tokens = tokenizeLine(lexer, line)

        assertHasTokenType(tokens, TokenType.CHAR)
    }

    @Test
    fun testLineComment() {
        val line = "int x = 5; // this is a comment"
        val tokens = tokenizeLine(lexer, line)

        assertHasTokenType(tokens, TokenType.COMMENT)
        assertTokenType(line, tokens, "// this is a comment", TokenType.COMMENT)
    }

    @Test
    fun testBlockComment() {
        val line = "/* block comment */ int x;"
        val tokens = tokenizeLine(lexer, line)

        assertHasTokenType(tokens, TokenType.COMMENT_BLOCK)
    }

    @Test
    fun testMultilineBlockComment() {
        val lines = listOf(
            "/* start of comment",
            "middle of comment",
            "end of comment */ int x;"
        )
        val results = tokenizeLines(lexer, lines)

        assertEquals(LexerState.IN_BLOCK_COMMENT, results[0].endState)
        assertEquals(LexerState.IN_BLOCK_COMMENT, results[1].endState)
        assertEquals(LexerState.NORMAL, results[2].endState)
    }

    @Test
    fun testDocComment() {
        val line = "/** JavaDoc comment */"
        val tokens = tokenizeLine(lexer, line)

        assertHasTokenType(tokens, TokenType.COMMENT_DOC)
    }

    @Test
    fun testAnnotation() {
        val line = "@Override public void method() {}"
        val tokens = tokenizeLine(lexer, line)

        assertHasTokenType(tokens, TokenType.ANNOTATION)
        assertTokenType(line, tokens, "@Override", TokenType.ANNOTATION)
    }

    @Test
    fun testNumbers() {
        val line = "int a = 42; double b = 3.14; long c = 100L;"
        val tokens = tokenizeLine(lexer, line)

        assertEquals(3, countTokenType(tokens, TokenType.NUMBER))
    }

    @Test
    fun testHexNumber() {
        val line = "int hex = 0xFF00;"
        val tokens = tokenizeLine(lexer, line)

        assertHasTokenType(tokens, TokenType.NUMBER)
    }

    @Test
    fun testBooleanLiterals() {
        val line = "boolean a = true; boolean b = false;"
        val tokens = tokenizeLine(lexer, line)

        assertTokenType(line, tokens, "true", TokenType.BOOLEAN)
        assertTokenType(line, tokens, "false", TokenType.BOOLEAN)
    }

    @Test
    fun testNullLiteral() {
        val line = "Object obj = null;"
        val tokens = tokenizeLine(lexer, line)

        assertTokenType(line, tokens, "null", TokenType.NULL)
    }

    @Test
    fun testTypeIdentifier() {
        val line = "String name; Integer count; MyClass obj;"
        val tokens = tokenizeLine(lexer, line)

        // Types starting with uppercase should be TYPE
        assertTokenType(line, tokens, "String", TokenType.TYPE)
        assertTokenType(line, tokens, "Integer", TokenType.TYPE)
        assertTokenType(line, tokens, "MyClass", TokenType.TYPE)
    }

    @Test
    fun testOperators() {
        val line = "x = a + b - c * d / e % f;"
        val tokens = tokenizeLine(lexer, line)

        assertHasTokenType(tokens, TokenType.OPERATOR)
    }

    @Test
    fun testBrackets() {
        val line = "array[0] = map.get(key);"
        val tokens = tokenizeLine(lexer, line)

        assertHasTokenType(tokens, TokenType.BRACKET)
        assertHasTokenType(tokens, TokenType.PARENTHESIS)
    }

    @Test
    fun testTextBlock() {
        val lines = listOf(
            "String text = \"\"\"",
            "    Hello",
            "    World",
            "    \"\"\";"
        )
        val results = tokenizeLines(lexer, lines)

        // Text blocks span multiple lines
        assertEquals(LexerState.IN_MULTILINE_STRING, results[0].endState)
        assertEquals(LexerState.IN_MULTILINE_STRING, results[1].endState)
        assertEquals(LexerState.IN_MULTILINE_STRING, results[2].endState)
        assertEquals(LexerState.NORMAL, results[3].endState)
    }

    @Test
    fun testEscapeSequences() {
        val line = """String s = "hello\nworld\t";"""
        val tokens = tokenizeLine(lexer, line)

        assertHasTokenType(tokens, TokenType.STRING)
    }

    @Test
    fun testLambda() {
        val line = "list.forEach(x -> System.out.println(x));"
        val tokens = tokenizeLine(lexer, line)

        // Should tokenize without errors
        assertEndState(tokens, LexerState.NORMAL)
    }

    @Test
    fun testGenerics() {
        val line = "List<String> list = new ArrayList<>();"
        val tokens = tokenizeLine(lexer, line)

        assertTokenType(line, tokens, "List", TokenType.TYPE)
        assertTokenType(line, tokens, "String", TokenType.TYPE)
        assertTokenType(line, tokens, "ArrayList", TokenType.TYPE)
    }
}
