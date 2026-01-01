package ai.rever.bosseditor.highlight.lexers

import ai.rever.bosseditor.highlight.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Base class for lexer tests with common utilities.
 */
abstract class LexerTestBase {

    /**
     * Helper to tokenize a single line and return tokens.
     */
    protected fun tokenizeLine(lexer: BaseLexer, line: String, startState: LexerState = LexerState.NORMAL): LineTokens {
        return lexer.tokenizeLine(line, 0, startState)
    }

    /**
     * Helper to tokenize multiple lines and return all tokens.
     */
    protected fun tokenizeLines(lexer: BaseLexer, lines: List<String>): List<LineTokens> {
        val results = mutableListOf<LineTokens>()
        var state = LexerState.NORMAL
        for ((index, line) in lines.withIndex()) {
            val result = lexer.tokenizeLine(line, index, state)
            results.add(result)
            state = result.endState
        }
        return results
    }

    /**
     * Helper to tokenize text (splits by newline).
     */
    protected fun tokenizeText(lexer: BaseLexer, text: String): List<LineTokens> {
        return tokenizeLines(lexer, text.lines())
    }

    /**
     * Asserts that a token of given type exists in the line tokens.
     */
    protected fun assertHasTokenType(tokens: LineTokens, expectedType: TokenType, message: String = "") {
        assertTrue(
            tokens.tokens.any { it.type == expectedType },
            "Expected token type $expectedType not found. ${if (message.isNotEmpty()) "($message)" else ""} Tokens: ${tokens.tokens}"
        )
    }

    /**
     * Asserts that no token of given type exists in the line tokens.
     */
    protected fun assertNoTokenType(tokens: LineTokens, unexpectedType: TokenType, message: String = "") {
        assertTrue(
            tokens.tokens.none { it.type == unexpectedType },
            "Unexpected token type $unexpectedType found. ${if (message.isNotEmpty()) "($message)" else ""} Tokens: ${tokens.tokens}"
        )
    }

    /**
     * Asserts that a specific substring is tokenized with the expected type.
     */
    protected fun assertTokenType(
        line: String,
        tokens: LineTokens,
        substring: String,
        expectedType: TokenType
    ) {
        val startIndex = line.indexOf(substring)
        assertTrue(startIndex >= 0, "Substring '$substring' not found in line '$line'")

        val token = tokens.tokens.find {
            it.startOffset <= startIndex && it.endOffset >= startIndex + substring.length
        }
        assertTrue(token != null, "No token covers substring '$substring' at position $startIndex")
        assertEquals(
            expectedType,
            token.type,
            "Expected '$substring' to be $expectedType but was ${token.type}"
        )
    }

    /**
     * Asserts the end state of tokenization.
     */
    protected fun assertEndState(tokens: LineTokens, expectedState: LexerState) {
        assertEquals(expectedState, tokens.endState, "Expected end state $expectedState but was ${tokens.endState}")
    }

    /**
     * Counts tokens of a specific type.
     */
    protected fun countTokenType(tokens: LineTokens, type: TokenType): Int {
        return tokens.tokens.count { it.type == type }
    }

    /**
     * Gets the text of a token from the original line.
     */
    protected fun getTokenText(line: String, token: Token): String {
        return line.substring(token.startOffset, token.endOffset)
    }

    /**
     * Gets all tokens of a specific type.
     */
    protected fun getTokensOfType(tokens: LineTokens, type: TokenType): List<Token> {
        return tokens.tokens.filter { it.type == type }
    }
}
