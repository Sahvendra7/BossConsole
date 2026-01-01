package ai.rever.bosseditor.highlight.lexers

import ai.rever.bosseditor.highlight.*
import kotlin.test.Test
import kotlin.test.assertEquals

class RustLexerTest : LexerTestBase() {
    private val lexer = RustLexer()

    @Test
    fun testLanguageId() {
        assertEquals("rust", lexer.languageId)
    }

    @Test
    fun testFileExtensions() {
        assertEquals(listOf("rs"), lexer.fileExtensions)
    }

    @Test
    fun testKeywords() {
        val line = "fn main() { let mut x = 5; }"
        val tokens = tokenizeLine(lexer, line)

        assertTokenType(line, tokens, "fn", TokenType.KEYWORD)
        assertTokenType(line, tokens, "let", TokenType.KEYWORD)
        assertTokenType(line, tokens, "mut", TokenType.KEYWORD)
    }

    @Test
    fun testStructEnum() {
        val line = "struct Point { x: i32, y: i32 }"
        val tokens = tokenizeLine(lexer, line)

        assertTokenType(line, tokens, "struct", TokenType.KEYWORD)
    }

    @Test
    fun testTraitImpl() {
        val line = "impl Display for MyType { }"
        val tokens = tokenizeLine(lexer, line)

        assertTokenType(line, tokens, "impl", TokenType.KEYWORD)
        assertTokenType(line, tokens, "for", TokenType.KEYWORD)
    }

    @Test
    fun testStringLiteral() {
        val line = """let s = "hello world";"""
        val tokens = tokenizeLine(lexer, line)

        assertHasTokenType(tokens, TokenType.STRING)
    }

    @Test
    fun testRawString() {
        val line = """let s = r#"raw string"#;"""
        val tokens = tokenizeLine(lexer, line)

        assertHasTokenType(tokens, TokenType.STRING)
    }

    @Test
    fun testByteString() {
        val line = """let b = b"bytes";"""
        val tokens = tokenizeLine(lexer, line)

        assertHasTokenType(tokens, TokenType.STRING)
    }

    @Test
    fun testCharLiteral() {
        val line = "let c = 'a';"
        val tokens = tokenizeLine(lexer, line)

        assertHasTokenType(tokens, TokenType.CHAR)
    }

    @Test
    fun testLineComment() {
        val line = "let x = 5; // comment"
        val tokens = tokenizeLine(lexer, line)

        assertHasTokenType(tokens, TokenType.COMMENT)
    }

    @Test
    fun testDocComment() {
        val line = "/// Documentation comment"
        val tokens = tokenizeLine(lexer, line)

        assertHasTokenType(tokens, TokenType.COMMENT_DOC)
    }

    @Test
    fun testBlockComment() {
        val line = "/* block comment */ let x = 5;"
        val tokens = tokenizeLine(lexer, line)

        assertHasTokenType(tokens, TokenType.COMMENT_BLOCK)
    }

    @Test
    fun testAttribute() {
        val line = "#[derive(Debug, Clone)]"
        val tokens = tokenizeLine(lexer, line)

        assertHasTokenType(tokens, TokenType.ANNOTATION)
    }

    @Test
    fun testInnerAttribute() {
        val line = "#![allow(unused)]"
        val tokens = tokenizeLine(lexer, line)

        assertHasTokenType(tokens, TokenType.ANNOTATION)
    }

    @Test
    fun testNumbers() {
        val line = "let a = 42; let b = 3.14; let c = 1e-5;"
        val tokens = tokenizeLine(lexer, line)

        assertEquals(3, countTokenType(tokens, TokenType.NUMBER))
    }

    @Test
    fun testTypedNumbers() {
        val line = "let a = 42u32; let b = 3.14f64; let c = 100i64;"
        val tokens = tokenizeLine(lexer, line)

        assertEquals(3, countTokenType(tokens, TokenType.NUMBER))
    }

    @Test
    fun testHexBinaryOctal() {
        val line = "let h = 0xFF; let b = 0b1010; let o = 0o755;"
        val tokens = tokenizeLine(lexer, line)

        assertEquals(3, countTokenType(tokens, TokenType.NUMBER))
    }

    @Test
    fun testBooleans() {
        val line = "let a = true; let b = false;"
        val tokens = tokenizeLine(lexer, line)

        // In Rust, true/false are keywords (classifyIdentifier checks KEYWORDS first)
        assertTokenType(line, tokens, "true", TokenType.KEYWORD)
        assertTokenType(line, tokens, "false", TokenType.KEYWORD)
    }

    @Test
    fun testPrimitiveTypes() {
        val line = "let a: i32; let b: u64; let c: f32; let d: bool; let e: str;"
        val tokens = tokenizeLine(lexer, line)

        assertTokenType(line, tokens, "i32", TokenType.TYPE)
        assertTokenType(line, tokens, "u64", TokenType.TYPE)
        assertTokenType(line, tokens, "f32", TokenType.TYPE)
        assertTokenType(line, tokens, "bool", TokenType.TYPE)
    }

    @Test
    fun testLifetimes() {
        val line = "fn foo<'a>(x: &'a str) -> &'a str"
        val tokens = tokenizeLine(lexer, line)

        // Lifetimes are classified as ANNOTATION
        assertHasTokenType(tokens, TokenType.ANNOTATION)
    }

    @Test
    fun testMacroInvocation() {
        val line = "println!(\"Hello\"); vec![1, 2, 3];"
        val tokens = tokenizeLine(lexer, line)

        assertHasTokenType(tokens, TokenType.FUNCTION_CALL)
    }

    @Test
    fun testMatchExpression() {
        val line = "match x { Some(v) => v, None => 0 }"
        val tokens = tokenizeLine(lexer, line)

        assertTokenType(line, tokens, "match", TokenType.KEYWORD)
    }

    @Test
    fun testOptionResult() {
        val line = "let opt: Option<i32> = Some(5); let res: Result<i32, Error> = Ok(5);"
        val tokens = tokenizeLine(lexer, line)

        assertTokenType(line, tokens, "Option", TokenType.TYPE)
        assertTokenType(line, tokens, "Result", TokenType.TYPE)
    }

    @Test
    fun testAsyncAwait() {
        val line = "async fn fetch() { let result = await future; }"
        val tokens = tokenizeLine(lexer, line)

        assertTokenType(line, tokens, "async", TokenType.KEYWORD)
    }

    @Test
    fun testUnsafe() {
        val line = "unsafe { ptr::read(addr) }"
        val tokens = tokenizeLine(lexer, line)

        assertTokenType(line, tokens, "unsafe", TokenType.KEYWORD)
    }

    @Test
    fun testClosure() {
        val line = "let f = |x| x * 2;"
        val tokens = tokenizeLine(lexer, line)

        assertEndState(tokens, LexerState.NORMAL)
    }

    @Test
    fun testModuleUse() {
        val line = "use std::collections::HashMap; mod my_module;"
        val tokens = tokenizeLine(lexer, line)

        assertTokenType(line, tokens, "use", TokenType.KEYWORD)
        assertTokenType(line, tokens, "mod", TokenType.KEYWORD)
    }
}
