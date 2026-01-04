package ai.rever.bosseditor.lsp.providers

import ai.rever.bosseditor.highlight.TokenModifier
import ai.rever.bosseditor.highlight.TokenType
import ai.rever.bosseditor.lsp.protocol.SemanticTokenModifiers
import ai.rever.bosseditor.lsp.protocol.SemanticTokenTypes
import ai.rever.bosseditor.lsp.protocol.SemanticTokensLegend
import kotlin.test.*

/**
 * Tests for LspSemanticTokenProvider.
 */
class LspSemanticTokenProviderTest {

    private lateinit var provider: LspSemanticTokenProvider
    private lateinit var legend: SemanticTokensLegend

    @BeforeTest
    fun setup() {
        provider = LspSemanticTokenProvider()
        legend = SemanticTokensLegend(
            tokenTypes = SemanticTokenTypes.ALL,
            tokenModifiers = SemanticTokenModifiers.ALL
        )
        provider.setLegend(legend)
        provider.setLineCount(100)
    }

    // ==================== Basic Availability Tests ====================

    @Test
    fun testInitiallyNotAvailable() {
        val freshProvider = LspSemanticTokenProvider()
        assertFalse(freshProvider.isAvailable())
    }

    @Test
    fun testBecomesAvailableAfterUpdate() {
        provider.updateTokens(listOf(0, 0, 5, 0, 0), "result-1")
        assertTrue(provider.isAvailable())
    }

    @Test
    fun testClearMakesUnavailable() {
        provider.updateTokens(listOf(0, 0, 5, 0, 0), "result-1")
        provider.clear()
        assertFalse(provider.isAvailable())
    }

    // ==================== Token Update Tests ====================

    @Test
    fun testUpdateTokensWithSingleToken() {
        // Token: line 0, char 0, length 5, type 0 (namespace), no modifiers
        val data = listOf(0, 0, 5, 0, 0)
        provider.updateTokens(data)

        val tokens = provider.getLineTokens(0)
        assertNotNull(tokens)
        assertEquals(1, tokens.size)

        val token = tokens[0]
        assertEquals(0, token.startOffset)
        assertEquals(5, token.endOffset)
        assertEquals(TokenType.IDENTIFIER, token.type) // namespace -> IDENTIFIER
    }

    @Test
    fun testUpdateTokensWithMultipleTokensSameLine() {
        // Token 1: line 0, char 0, length 5, type 0 (namespace)
        // Token 2: line 0, char 6, length 3, type 12 (function)
        val data = listOf(
            0, 0, 5, 0, 0,  // first token
            0, 6, 3, 12, 0  // second token (delta from first)
        )
        provider.updateTokens(data)

        val tokens = provider.getLineTokens(0)
        assertNotNull(tokens)
        assertEquals(2, tokens.size)

        // First token
        assertEquals(0, tokens[0].startOffset)
        assertEquals(5, tokens[0].endOffset)

        // Second token
        assertEquals(6, tokens[1].startOffset)
        assertEquals(9, tokens[1].endOffset)
        assertEquals(TokenType.FUNCTION, tokens[1].type)
    }

    @Test
    fun testUpdateTokensWithMultipleLines() {
        // Token 1: line 0, char 0, length 5
        // Token 2: line 1, char 0, length 10
        // Token 3: line 3, char 5, length 3
        val data = listOf(
            0, 0, 5, 0, 0,   // line 0
            1, 0, 10, 1, 0,  // line 1 (delta 1)
            2, 5, 3, 2, 0    // line 3 (delta 2)
        )
        provider.updateTokens(data)

        // Line 0
        val line0 = provider.getLineTokens(0)
        assertNotNull(line0)
        assertEquals(1, line0.size)

        // Line 1
        val line1 = provider.getLineTokens(1)
        assertNotNull(line1)
        assertEquals(1, line1.size)
        assertEquals(0, line1[0].startOffset)
        assertEquals(10, line1[0].endOffset)

        // Line 2 - no tokens
        val line2 = provider.getLineTokens(2)
        assertTrue(line2.isNullOrEmpty())

        // Line 3
        val line3 = provider.getLineTokens(3)
        assertNotNull(line3)
        assertEquals(1, line3.size)
        assertEquals(5, line3[0].startOffset)
        assertEquals(8, line3[0].endOffset)
    }

    @Test
    fun testUpdateTokensWithModifiers() {
        // Token with deprecated modifier (bit 4 = 16)
        // Deprecated is at index 4 in SemanticTokenModifiers.ALL
        val data = listOf(0, 0, 5, 0, 16)
        provider.updateTokens(data)

        val tokens = provider.getLineTokens(0)
        assertNotNull(tokens)
        assertEquals(1, tokens.size)

        // Should have STRIKETHROUGH modifier
        assertTrue(TokenModifier.STRIKETHROUGH in tokens[0].modifiers)
    }

    @Test
    fun testUpdateTokensWithDeclarationModifier() {
        // Declaration modifier (bit 0 = 1)
        val data = listOf(0, 0, 5, 12, 1) // function with declaration
        provider.updateTokens(data)

        val tokens = provider.getLineTokens(0)
        assertNotNull(tokens)
        assertEquals(1, tokens.size)

        // Should have BOLD modifier
        assertTrue(TokenModifier.BOLD in tokens[0].modifiers)
    }

    // ==================== Result ID Tests ====================

    @Test
    fun testResultIdTracking() {
        assertNull(provider.getLastResultId())
        assertFalse(provider.supportsDelta())

        provider.updateTokens(listOf(0, 0, 5, 0, 0), "result-1")

        assertEquals("result-1", provider.getLastResultId())
        assertTrue(provider.supportsDelta())
    }

    @Test
    fun testResultIdUpdatesOnNewTokens() {
        provider.updateTokens(listOf(0, 0, 5, 0, 0), "result-1")
        provider.updateTokens(listOf(0, 0, 5, 0, 0), "result-2")

        assertEquals("result-2", provider.getLastResultId())
    }

    // ==================== Line Tokens Access Tests ====================

    @Test
    fun testGetLineTokensReturnsNullWhenNotAvailable() {
        val freshProvider = LspSemanticTokenProvider()
        assertNull(freshProvider.getLineTokens(0))
    }

    @Test
    fun testGetLineTokensReturnsNullForLineWithNoTokens() {
        provider.updateTokens(listOf(0, 0, 5, 0, 0)) // Only line 0 has tokens
        assertNull(provider.getLineTokens(5))
    }

    @Test
    fun testGetTokensInRange() {
        // Tokens on lines 0, 2, 4
        val data = listOf(
            0, 0, 5, 0, 0,
            2, 0, 5, 1, 0,
            2, 0, 5, 2, 0
        )
        provider.updateTokens(data)

        val rangeTokens = provider.getTokensInRange(0, 3)

        assertTrue(0 in rangeTokens)
        assertTrue(2 in rangeTokens)
        assertFalse(1 in rangeTokens)
        assertFalse(3 in rangeTokens)
    }

    @Test
    fun testGetTokensInRangeReturnsEmptyWhenNotAvailable() {
        val freshProvider = LspSemanticTokenProvider()
        val result = freshProvider.getTokensInRange(0, 10)
        assertTrue(result.isEmpty())
    }

    // ==================== Line Invalidation Tests ====================

    @Test
    fun testInvalidateLinesRemovesTokens() {
        // Tokens on lines 0, 1, 2
        val data = listOf(
            0, 0, 5, 0, 0,
            1, 0, 5, 1, 0,
            1, 0, 5, 2, 0
        )
        provider.updateTokens(data)

        // Invalidate line 1
        provider.invalidateLines(1, 1)

        assertNotNull(provider.getLineTokens(0))
        assertNull(provider.getLineTokens(1))
        assertNotNull(provider.getLineTokens(2))
    }

    @Test
    fun testInvalidateLinesWithDeltaShiftsTokens() {
        // Tokens on lines 0, 5, 10
        val data = listOf(
            0, 0, 5, 0, 0,
            5, 0, 5, 1, 0,
            5, 0, 5, 2, 0
        )
        provider.updateTokens(data)

        // Insert 2 lines at position 3 (affects tokens at 5 and 10)
        provider.invalidateLines(3, 3, lineDelta = 2)

        // Line 0 unchanged
        assertNotNull(provider.getLineTokens(0))

        // Line 5 moved to 7
        assertNull(provider.getLineTokens(5))
        assertNotNull(provider.getLineTokens(7))

        // Line 10 moved to 12
        assertNull(provider.getLineTokens(10))
        assertNotNull(provider.getLineTokens(12))
    }

    @Test
    fun testInvalidateLinesWithNegativeDeltaShiftsUp() {
        // Tokens on lines 0, 5, 10
        val data = listOf(
            0, 0, 5, 0, 0,
            5, 0, 5, 1, 0,
            5, 0, 5, 2, 0
        )
        provider.updateTokens(data)

        // Delete 2 lines at position 3 (affects tokens at 5 and 10)
        provider.invalidateLines(3, 4, lineDelta = -2)

        // Line 0 unchanged
        assertNotNull(provider.getLineTokens(0))

        // Line 5 moved to 3
        assertNull(provider.getLineTokens(5))
        assertNotNull(provider.getLineTokens(3))

        // Line 10 moved to 8
        assertNull(provider.getLineTokens(10))
        assertNotNull(provider.getLineTokens(8))
    }

    // ==================== Clear Tests ====================

    @Test
    fun testClearRemovesAllTokens() {
        val data = listOf(
            0, 0, 5, 0, 0,
            1, 0, 5, 1, 0
        )
        provider.updateTokens(data, "result-1")

        provider.clear()

        assertFalse(provider.isAvailable())
        assertNull(provider.getLineTokens(0))
        assertNull(provider.getLineTokens(1))
        assertNull(provider.getLastResultId())
    }

    // ==================== Token Type Mapping Tests ====================

    @Test
    fun testAllStandardTokenTypesMap() {
        // Test that all standard LSP token types can be processed
        // Create one token for each type (0-22)
        val data = mutableListOf<Int>()
        for (typeIndex in 0 until SemanticTokenTypes.ALL.size) {
            // Each token on a new line
            val lineDelta = if (typeIndex == 0) 0 else 1
            data.addAll(listOf(lineDelta, 0, 5, typeIndex, 0))
        }

        provider.updateTokens(data)

        // Verify each line has a token
        for (line in 0 until SemanticTokenTypes.ALL.size) {
            val tokens = provider.getLineTokens(line)
            assertNotNull(tokens, "Line $line should have tokens")
            assertEquals(1, tokens.size, "Line $line should have exactly one token")
        }
    }

    // ==================== Empty Data Tests ====================

    @Test
    fun testUpdateWithEmptyData() {
        provider.updateTokens(emptyList())

        // Should still become available (valid empty response)
        assertTrue(provider.isAvailable())

        // No tokens on any line
        assertNull(provider.getLineTokens(0))
    }

    @Test
    fun testUpdateWithoutLegendIsIgnored() {
        val providerWithoutLegend = LspSemanticTokenProvider()
        // Don't set legend
        providerWithoutLegend.updateTokens(listOf(0, 0, 5, 0, 0))

        // Should not become available
        assertFalse(providerWithoutLegend.isAvailable())
    }
}
