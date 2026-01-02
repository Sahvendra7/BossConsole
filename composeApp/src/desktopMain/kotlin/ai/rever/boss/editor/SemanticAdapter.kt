package ai.rever.boss.editor

import ai.rever.boss.psi.SemanticCache
import ai.rever.boss.psi.SemanticType
import ai.rever.bosseditor.core.EditorDocument
import ai.rever.bosseditor.highlight.SemanticTokenProvider
import ai.rever.bosseditor.highlight.Token
import ai.rever.bosseditor.highlight.TokenType

/**
 * Adapter that bridges the BossConsole PSI-based semantic highlighting
 * to the BossEditor's SemanticTokenProvider interface.
 *
 * NOTE: This is a simplified adapter. Full PSI integration will be
 * implemented in Phase 10 when the editor is fully integrated.
 *
 * The adapter reads from SemanticCache, which is populated by the
 * existing SemanticHighlighter when it analyzes Kotlin files.
 */
class SemanticAdapter(
    private val document: EditorDocument,
    private val filePath: String
) : SemanticTokenProvider {

    /**
     * Gets semantic tokens for a line by querying the SemanticCache.
     *
     * Returns null if no semantic analysis is available, allowing
     * the lexer-based highlighting to be used instead.
     */
    override fun getLineTokens(lineNumber: Int): List<Token>? {
        if (lineNumber < 0 || lineNumber >= document.lineCount) {
            return null
        }

        // Get all semantic elements for this file
        val allElements = SemanticCache.get(filePath) ?: return null
        if (allElements.isEmpty()) return null

        // Get the line range in the document
        val lineStart = document.getLineStartOffset(lineNumber)
        val lineEnd = document.getLineEndOffset(lineNumber)

        // Filter elements that fall within this line
        val lineElements = allElements.filter { element ->
            element.startOffset >= lineStart && element.endOffset <= lineEnd
        }

        if (lineElements.isEmpty()) return null

        // Convert SemanticElements to BossEditor Tokens
        return lineElements.map { element ->
            Token(
                startOffset = element.startOffset - lineStart,
                endOffset = element.endOffset - lineStart,
                type = mapSemanticType(element.type)
            )
        }.sortedBy { it.startOffset }
    }

    override fun isAvailable(): Boolean {
        // Check if there's any cached data for this file
        return SemanticCache.get(filePath)?.isNotEmpty() == true
    }

    /**
     * Maps PSI SemanticType to BossEditor TokenType.
     */
    private fun mapSemanticType(type: SemanticType): TokenType = when (type) {
        SemanticType.FUNCTION_CALL -> TokenType.FUNCTION_CALL
        SemanticType.PROPERTY_ACCESS -> TokenType.PROPERTY
        SemanticType.CLASS_REFERENCE -> TokenType.TYPE
        SemanticType.OBJECT_REFERENCE -> TokenType.VARIABLE
        SemanticType.PARAMETER -> TokenType.PARAMETER
        SemanticType.LOCAL_VARIABLE -> TokenType.LOCAL_VARIABLE
        SemanticType.ANNOTATION -> TokenType.ANNOTATION
        SemanticType.LABEL -> TokenType.LABEL
        SemanticType.TYPE_PARAMETER -> TokenType.TYPE_PARAMETER
    }
}

/**
 * Factory for creating SemanticAdapters for different file types.
 */
object SemanticAdapterFactory {
    /**
     * Creates a SemanticAdapter if the file type supports semantic analysis.
     */
    fun create(document: EditorDocument, filePath: String): SemanticAdapter? {
        // Only Kotlin files currently support semantic analysis
        return if (filePath.endsWith(".kt") || filePath.endsWith(".kts")) {
            SemanticAdapter(document, filePath)
        } else {
            null
        }
    }
}
