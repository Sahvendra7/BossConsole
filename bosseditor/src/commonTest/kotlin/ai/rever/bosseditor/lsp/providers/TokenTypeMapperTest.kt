package ai.rever.bosseditor.lsp.providers

import ai.rever.bosseditor.highlight.TokenModifier
import ai.rever.bosseditor.highlight.TokenType
import ai.rever.bosseditor.lsp.protocol.SemanticTokenModifiers
import ai.rever.bosseditor.lsp.protocol.SemanticTokenTypes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for TokenTypeMapper.
 */
class TokenTypeMapperTest {

    // ==================== LSP Type Mapping Tests ====================

    @Test
    fun testMapStandardLspTypes() {
        // Test all standard LSP semantic token types
        assertEquals(TokenType.IDENTIFIER, TokenTypeMapper.mapLspType(SemanticTokenTypes.NAMESPACE))
        assertEquals(TokenType.TYPE, TokenTypeMapper.mapLspType(SemanticTokenTypes.TYPE))
        assertEquals(TokenType.TYPE, TokenTypeMapper.mapLspType(SemanticTokenTypes.CLASS))
        assertEquals(TokenType.ENUM, TokenTypeMapper.mapLspType(SemanticTokenTypes.ENUM))
        assertEquals(TokenType.INTERFACE, TokenTypeMapper.mapLspType(SemanticTokenTypes.INTERFACE))
        assertEquals(TokenType.TYPE, TokenTypeMapper.mapLspType(SemanticTokenTypes.STRUCT))
        assertEquals(TokenType.TYPE_PARAMETER, TokenTypeMapper.mapLspType(SemanticTokenTypes.TYPE_PARAMETER))
        assertEquals(TokenType.PARAMETER, TokenTypeMapper.mapLspType(SemanticTokenTypes.PARAMETER))
        assertEquals(TokenType.VARIABLE, TokenTypeMapper.mapLspType(SemanticTokenTypes.VARIABLE))
        assertEquals(TokenType.PROPERTY, TokenTypeMapper.mapLspType(SemanticTokenTypes.PROPERTY))
        assertEquals(TokenType.ENUM_MEMBER, TokenTypeMapper.mapLspType(SemanticTokenTypes.ENUM_MEMBER))
        assertEquals(TokenType.FUNCTION, TokenTypeMapper.mapLspType(SemanticTokenTypes.EVENT))
        assertEquals(TokenType.FUNCTION, TokenTypeMapper.mapLspType(SemanticTokenTypes.FUNCTION))
        assertEquals(TokenType.FUNCTION, TokenTypeMapper.mapLspType(SemanticTokenTypes.METHOD))
        assertEquals(TokenType.PREPROCESSOR, TokenTypeMapper.mapLspType(SemanticTokenTypes.MACRO))
        assertEquals(TokenType.KEYWORD, TokenTypeMapper.mapLspType(SemanticTokenTypes.KEYWORD))
        assertEquals(TokenType.KEYWORD_MODIFIER, TokenTypeMapper.mapLspType(SemanticTokenTypes.MODIFIER))
        assertEquals(TokenType.COMMENT, TokenTypeMapper.mapLspType(SemanticTokenTypes.COMMENT))
        assertEquals(TokenType.STRING, TokenTypeMapper.mapLspType(SemanticTokenTypes.STRING))
        assertEquals(TokenType.NUMBER, TokenTypeMapper.mapLspType(SemanticTokenTypes.NUMBER))
        assertEquals(TokenType.REGEX, TokenTypeMapper.mapLspType(SemanticTokenTypes.REGEXP))
        assertEquals(TokenType.OPERATOR, TokenTypeMapper.mapLspType(SemanticTokenTypes.OPERATOR))
        assertEquals(TokenType.ANNOTATION, TokenTypeMapper.mapLspType(SemanticTokenTypes.DECORATOR))
    }

    @Test
    fun testMapUnknownTypeReturnsDefault() {
        assertEquals(TokenType.DEFAULT, TokenTypeMapper.mapLspType("unknown"))
        assertEquals(TokenType.DEFAULT, TokenTypeMapper.mapLspType("nonExistentType"))
        assertEquals(TokenType.DEFAULT, TokenTypeMapper.mapLspType(""))
    }

    @Test
    fun testMapExtendedTypes() {
        // Test common extended token types from various language servers
        assertEquals(TokenType.LABEL, TokenTypeMapper.mapLspType("label"))
        assertEquals(TokenType.BOOLEAN, TokenTypeMapper.mapLspType("boolean"))
        assertEquals(TokenType.CHAR, TokenTypeMapper.mapLspType("character"))
        assertEquals(TokenType.STRING_ESCAPE, TokenTypeMapper.mapLspType("escapeSequence"))
        assertEquals(TokenType.CONSTANT, TokenTypeMapper.mapLspType("builtinConstant"))
        assertEquals(TokenType.BRACKET, TokenTypeMapper.mapLspType("brace"))
        assertEquals(TokenType.OPERATOR_COMPARISON, TokenTypeMapper.mapLspType("comparison"))
        assertEquals(TokenType.OPERATOR_LOGICAL, TokenTypeMapper.mapLspType("logical"))
    }

    // ==================== Modifier-Aware Mapping Tests ====================

    @Test
    fun testMapWithReadonlyModifierMakesConstant() {
        val modifiers = setOf(SemanticTokenModifiers.READONLY)

        // Variable with readonly becomes constant
        assertEquals(
            TokenType.CONSTANT,
            TokenTypeMapper.mapLspTypeWithModifiers(SemanticTokenTypes.VARIABLE, modifiers)
        )

        // Property with readonly becomes constant
        assertEquals(
            TokenType.CONSTANT,
            TokenTypeMapper.mapLspTypeWithModifiers(SemanticTokenTypes.PROPERTY, modifiers)
        )
    }

    @Test
    fun testMapWithoutReadonlyPreservesType() {
        val emptyModifiers = emptySet<String>()

        assertEquals(
            TokenType.VARIABLE,
            TokenTypeMapper.mapLspTypeWithModifiers(SemanticTokenTypes.VARIABLE, emptyModifiers)
        )

        assertEquals(
            TokenType.PROPERTY,
            TokenTypeMapper.mapLspTypeWithModifiers(SemanticTokenTypes.PROPERTY, emptyModifiers)
        )
    }

    @Test
    fun testMapWithDeprecatedModifier() {
        val modifiers = setOf(SemanticTokenModifiers.DEPRECATED)

        // Deprecated function still returns FUNCTION type
        // (caller should handle strikethrough via modifiers)
        assertEquals(
            TokenType.FUNCTION,
            TokenTypeMapper.mapLspTypeWithModifiers(SemanticTokenTypes.FUNCTION, modifiers)
        )
    }

    // ==================== LSP Modifier Mapping Tests ====================

    @Test
    fun testMapEmptyModifiers() {
        val result = TokenTypeMapper.mapLspModifiers(emptySet())
        assertTrue(result.isEmpty())
    }

    @Test
    fun testMapDeprecatedModifierToStrikethrough() {
        val lspModifiers = setOf(SemanticTokenModifiers.DEPRECATED)
        val result = TokenTypeMapper.mapLspModifiers(lspModifiers)

        assertTrue(TokenModifier.STRIKETHROUGH in result)
    }

    @Test
    fun testMapReadonlyModifierToItalic() {
        val lspModifiers = setOf(SemanticTokenModifiers.READONLY)
        val result = TokenTypeMapper.mapLspModifiers(lspModifiers)

        assertTrue(TokenModifier.ITALIC in result)
    }

    @Test
    fun testMapStaticModifierToItalic() {
        val lspModifiers = setOf(SemanticTokenModifiers.STATIC)
        val result = TokenTypeMapper.mapLspModifiers(lspModifiers)

        assertTrue(TokenModifier.ITALIC in result)
    }

    @Test
    fun testMapDeclarationModifierToBold() {
        val lspModifiers = setOf(SemanticTokenModifiers.DECLARATION)
        val result = TokenTypeMapper.mapLspModifiers(lspModifiers)

        assertTrue(TokenModifier.BOLD in result)
    }

    @Test
    fun testMapDefinitionModifierToBold() {
        val lspModifiers = setOf(SemanticTokenModifiers.DEFINITION)
        val result = TokenTypeMapper.mapLspModifiers(lspModifiers)

        assertTrue(TokenModifier.BOLD in result)
    }

    @Test
    fun testMapMultipleModifiers() {
        val lspModifiers = setOf(
            SemanticTokenModifiers.DEPRECATED,
            SemanticTokenModifiers.READONLY,
            SemanticTokenModifiers.DECLARATION
        )
        val result = TokenTypeMapper.mapLspModifiers(lspModifiers)

        assertTrue(TokenModifier.STRIKETHROUGH in result)
        assertTrue(TokenModifier.ITALIC in result)
        assertTrue(TokenModifier.BOLD in result)
    }

    @Test
    fun testMapUnmappedModifiersIgnored() {
        // Async and documentation don't have visual mappings
        val lspModifiers = setOf(
            SemanticTokenModifiers.ASYNC,
            SemanticTokenModifiers.DOCUMENTATION,
            SemanticTokenModifiers.DEFAULT_LIBRARY
        )
        val result = TokenTypeMapper.mapLspModifiers(lspModifiers)

        assertTrue(result.isEmpty())
    }

    // ==================== Semantic Type Check Tests ====================

    @Test
    fun testIsSemanticTypeForSemanticTypes() {
        // These should be considered semantic types
        assertTrue(TokenTypeMapper.isSemanticType(TokenType.SEMANTIC_VARIABLE))
        assertTrue(TokenTypeMapper.isSemanticType(TokenType.SEMANTIC_PARAMETER))
        assertTrue(TokenTypeMapper.isSemanticType(TokenType.SEMANTIC_PROPERTY))
        assertTrue(TokenTypeMapper.isSemanticType(TokenType.SEMANTIC_FUNCTION))
        assertTrue(TokenTypeMapper.isSemanticType(TokenType.FUNCTION))
        assertTrue(TokenTypeMapper.isSemanticType(TokenType.TYPE))
        assertTrue(TokenTypeMapper.isSemanticType(TokenType.VARIABLE))
        assertTrue(TokenTypeMapper.isSemanticType(TokenType.PARAMETER))
    }

    @Test
    fun testIsSemanticTypeForNonSemanticTypes() {
        // These should NOT be considered semantic types
        assertFalse(TokenTypeMapper.isSemanticType(TokenType.DEFAULT))
        assertFalse(TokenTypeMapper.isSemanticType(TokenType.WHITESPACE))
        assertFalse(TokenTypeMapper.isSemanticType(TokenType.KEYWORD))
        assertFalse(TokenTypeMapper.isSemanticType(TokenType.STRING))
        assertFalse(TokenTypeMapper.isSemanticType(TokenType.NUMBER))
        assertFalse(TokenTypeMapper.isSemanticType(TokenType.COMMENT))
        assertFalse(TokenTypeMapper.isSemanticType(TokenType.OPERATOR))
        assertFalse(TokenTypeMapper.isSemanticType(TokenType.PUNCTUATION))
    }

    // ==================== Reverse Mapping Tests ====================

    @Test
    fun testReverseMapping() {
        assertEquals(SemanticTokenTypes.TYPE, TokenTypeMapper.toXLspType(TokenType.TYPE))
        assertEquals(SemanticTokenTypes.ENUM, TokenTypeMapper.toXLspType(TokenType.ENUM))
        assertEquals(SemanticTokenTypes.INTERFACE, TokenTypeMapper.toXLspType(TokenType.INTERFACE))
        assertEquals(SemanticTokenTypes.FUNCTION, TokenTypeMapper.toXLspType(TokenType.FUNCTION))
        assertEquals(SemanticTokenTypes.VARIABLE, TokenTypeMapper.toXLspType(TokenType.VARIABLE))
        assertEquals(SemanticTokenTypes.PARAMETER, TokenTypeMapper.toXLspType(TokenType.PARAMETER))
        assertEquals(SemanticTokenTypes.PROPERTY, TokenTypeMapper.toXLspType(TokenType.PROPERTY))
        assertEquals(SemanticTokenTypes.KEYWORD, TokenTypeMapper.toXLspType(TokenType.KEYWORD))
        assertEquals(SemanticTokenTypes.STRING, TokenTypeMapper.toXLspType(TokenType.STRING))
        assertEquals(SemanticTokenTypes.NUMBER, TokenTypeMapper.toXLspType(TokenType.NUMBER))
        assertEquals(SemanticTokenTypes.COMMENT, TokenTypeMapper.toXLspType(TokenType.COMMENT))
        assertEquals(SemanticTokenTypes.OPERATOR, TokenTypeMapper.toXLspType(TokenType.OPERATOR))
        assertEquals(SemanticTokenTypes.DECORATOR, TokenTypeMapper.toXLspType(TokenType.ANNOTATION))
    }

    @Test
    fun testReverseMappingForUnmappedTypes() {
        // Types that don't have a direct LSP equivalent
        assertEquals(null, TokenTypeMapper.toXLspType(TokenType.DEFAULT))
        assertEquals(null, TokenTypeMapper.toXLspType(TokenType.WHITESPACE))
        assertEquals(null, TokenTypeMapper.toXLspType(TokenType.ERROR))
        assertEquals(null, TokenTypeMapper.toXLspType(TokenType.TODO))
    }
}
