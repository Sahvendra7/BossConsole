package ai.rever.bosseditor.spelling

import ai.rever.bosseditor.core.OffsetRange
import ai.rever.bosseditor.features.SpellCheckableTokenType
import ai.rever.bosseditor.features.SpellingError
import ai.rever.bosseditor.highlight.Token
import ai.rever.bosseditor.highlight.TokenType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Service for spell checking editor content.
 *
 * Performs spell checking on comments and strings, extracting words
 * and checking them against a dictionary. Results are returned as
 * SpellingError objects for rendering.
 *
 * JVM-only: Desktop target only (see CLAUDE.md).
 */
class SpellCheckService(
    private val spellChecker: SpellChecker
) {
    // Word pattern: letters only, at least 2 characters
    private val wordPattern = Regex("[a-zA-Z]{2,}")

    // Patterns to skip (code-like content in comments)
    private val skipPatterns = listOf(
        Regex("@\\w+"),           // Annotations (@param, @return, etc.)
        Regex("\\{@\\w+[^}]*}"),  // Javadoc links {@link Foo}
        Regex("`[^`]+`"),         // Inline code in markdown
        Regex("\\[[^\\]]+]"),     // Markdown links
        Regex("https?://\\S+"),   // URLs
        Regex("\\S+\\.\\S+"),     // Dotted paths (file.ext, package.class)
        Regex("[A-Z][a-z]+(?:[A-Z][a-z]+)+") // CamelCase identifiers
    )

    /**
     * Checks all spellable tokens and returns spelling errors.
     *
     * @param tokens List of tokens from the lexer, grouped by line
     * @param getLineText Function to get line text by line number
     * @param getLineStartOffset Function to get line start offset
     * @return List of spelling errors found
     */
    suspend fun checkDocument(
        tokens: Map<Int, List<Token>>,
        getLineText: (Int) -> String,
        getLineStartOffset: (Int) -> Int
    ): List<SpellingError> = withContext(Dispatchers.Default) {
        if (!spellChecker.isReady()) return@withContext emptyList()

        val errors = mutableListOf<SpellingError>()

        for ((lineNumber, lineTokens) in tokens) {
            val lineText = getLineText(lineNumber)
            val lineStartOffset = getLineStartOffset(lineNumber)

            for (token in lineTokens) {
                // Only check spellable token types
                val spellableType = token.type.toSpellCheckableType() ?: continue

                // Get the text for this token
                val tokenStart = token.startOffset.coerceIn(0, lineText.length)
                val tokenEnd = token.endOffset.coerceIn(tokenStart, lineText.length)
                val tokenText = lineText.substring(tokenStart, tokenEnd)

                // Find and check words in the token
                val tokenErrors = checkText(
                    text = tokenText,
                    tokenOffset = lineStartOffset + tokenStart,
                    lineNumber = lineNumber,
                    tokenType = spellableType
                )

                errors.addAll(tokenErrors)
            }
        }

        errors
    }

    /**
     * Checks a single line of text for spelling errors.
     * Useful for incremental updates.
     *
     * @param lineNumber The line number (0-indexed)
     * @param lineText The text of the line
     * @param lineStartOffset The document offset where this line starts
     * @param lineTokens Tokens for this line
     * @return List of spelling errors on this line
     */
    suspend fun checkLine(
        lineNumber: Int,
        lineText: String,
        lineStartOffset: Int,
        lineTokens: List<Token>
    ): List<SpellingError> = withContext(Dispatchers.Default) {
        if (!spellChecker.isReady()) return@withContext emptyList()

        val errors = mutableListOf<SpellingError>()

        for (token in lineTokens) {
            val spellableType = token.type.toSpellCheckableType() ?: continue

            val tokenStart = token.startOffset.coerceIn(0, lineText.length)
            val tokenEnd = token.endOffset.coerceIn(tokenStart, lineText.length)
            val tokenText = lineText.substring(tokenStart, tokenEnd)

            val tokenErrors = checkText(
                text = tokenText,
                tokenOffset = lineStartOffset + tokenStart,
                lineNumber = lineNumber,
                tokenType = spellableType
            )

            errors.addAll(tokenErrors)
        }

        errors
    }

    /**
     * Checks text for spelling errors, returning errors with document offsets.
     */
    private fun checkText(
        text: String,
        tokenOffset: Int,
        lineNumber: Int,
        tokenType: SpellCheckableTokenType
    ): List<SpellingError> {
        val errors = mutableListOf<SpellingError>()

        // Skip if the entire text matches skip patterns
        if (shouldSkipText(text)) return errors

        // Find all words in the text
        wordPattern.findAll(text).forEach { match ->
            val word = match.value

            // Skip if word is in a skip pattern region
            if (isInSkipRegion(text, match.range)) return@forEach

            // Skip short words
            if (word.length < 2) return@forEach

            // Split CamelCase and check each part
            val partsWithOffsets = splitCamelCaseWithOffsets(word)
            if (partsWithOffsets.size > 1) {
                // Check each CamelCase part separately
                for ((part, partOffset) in partsWithOffsets) {
                    if (part.length >= 2 && !spellChecker.check(part)) {
                        val startOffset = tokenOffset + match.range.first + partOffset
                        val endOffset = startOffset + part.length
                        errors.add(
                            SpellingError.create(
                                startOffset = startOffset,
                                endOffset = endOffset,
                                word = part,
                                suggestions = spellChecker.suggest(part),
                                line = lineNumber
                            )
                        )
                    }
                }
            } else {
                // Single word
                if (!spellChecker.check(word)) {
                    val startOffset = tokenOffset + match.range.first
                    val endOffset = tokenOffset + match.range.last + 1
                    errors.add(
                        SpellingError.create(
                            startOffset = startOffset,
                            endOffset = endOffset,
                            word = word,
                            suggestions = spellChecker.suggest(word),
                            line = lineNumber
                        )
                    )
                }
            }
        }

        return errors
    }

    /**
     * Checks if entire text should be skipped (e.g., URLs, code references).
     */
    private fun shouldSkipText(text: String): Boolean {
        return skipPatterns.any { it.matches(text.trim()) }
    }

    /**
     * Checks if a word position is within a skip region (URL, code block, etc.).
     */
    private fun isInSkipRegion(text: String, wordRange: IntRange): Boolean {
        for (pattern in skipPatterns) {
            pattern.findAll(text).forEach { match ->
                if (wordRange.first >= match.range.first && wordRange.last <= match.range.last) {
                    return true
                }
            }
        }
        return false
    }

    /**
     * Splits a CamelCase word into parts with their offsets in the original string.
     * Returns list of (part, startOffset) pairs for accurate error positioning.
     *
     * Examples:
     * - "camelCase" -> [("camel", 0), ("Case", 5)]
     * - "HTMLParser" -> [("HTML", 0), ("Parser", 4)]
     * - "XMLHttpRequest" -> [("XML", 0), ("Http", 3), ("Request", 7)]
     */
    private fun splitCamelCaseWithOffsets(word: String): List<Pair<String, Int>> {
        if (word.length < 2) return listOf(word to 0)

        val parts = mutableListOf<Pair<String, Int>>()
        val current = StringBuilder()
        var partStart = 0

        for (i in word.indices) {
            val char = word[i]
            val nextChar = word.getOrNull(i + 1)

            current.append(char)

            // Split before uppercase if followed by lowercase (e.g., "HTMLParser" -> "HTML", "Parser")
            // Or split after lowercase before uppercase (e.g., "camelCase" -> "camel", "Case")
            if (nextChar != null) {
                val isUpperToLower = char.isUpperCase() && nextChar.isLowerCase() && current.length > 1
                val isLowerToUpper = char.isLowerCase() && nextChar.isUpperCase()

                if (isUpperToLower || isLowerToUpper) {
                    if (isUpperToLower && current.length > 1) {
                        // Move current char to next part
                        val partContent = current.substring(0, current.length - 1)
                        parts.add(partContent to partStart)
                        partStart = i  // New part starts at current character position
                        current.clear()
                        current.append(char)
                    } else {
                        parts.add(current.toString() to partStart)
                        partStart = i + 1  // New part starts after this character
                        current.clear()
                    }
                }
            }
        }

        if (current.isNotEmpty()) {
            parts.add(current.toString() to partStart)
        }

        return parts
    }

    /**
     * Converts a TokenType to SpellCheckableTokenType, or null if not spellable.
     */
    private fun TokenType.toSpellCheckableType(): SpellCheckableTokenType? = when (this) {
        TokenType.COMMENT -> SpellCheckableTokenType.COMMENT
        TokenType.COMMENT_BLOCK -> SpellCheckableTokenType.COMMENT_BLOCK
        TokenType.COMMENT_DOC -> SpellCheckableTokenType.DOC_COMMENT
        TokenType.STRING -> SpellCheckableTokenType.STRING
        else -> null
    }
}
