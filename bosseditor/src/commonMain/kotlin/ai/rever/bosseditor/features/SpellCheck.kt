package ai.rever.bosseditor.features

import ai.rever.bosseditor.core.OffsetRange

/**
 * Represents a spelling error in the editor.
 *
 * Spelling errors are displayed as blue squiggly underlines
 * and can be fixed via right-click context menu or quick fix lightbulb.
 *
 * @property range The character offset range in the document
 * @property word The misspelled word
 * @property suggestions List of suggested corrections
 * @property line The line number (0-indexed) where the error occurs
 */
data class SpellingError(
    val range: OffsetRange,
    val word: String,
    val suggestions: List<String>,
    val line: Int
) {
    /** Start offset in the document */
    val startOffset: Int get() = range.start

    /** End offset in the document */
    val endOffset: Int get() = range.end

    companion object {
        /**
         * Creates a spelling error with automatic suggestion generation.
         */
        fun create(
            startOffset: Int,
            endOffset: Int,
            word: String,
            suggestions: List<String>,
            line: Int
        ): SpellingError = SpellingError(
            range = OffsetRange(startOffset, endOffset),
            word = word,
            suggestions = suggestions,
            line = line
        )
    }
}

/**
 * Types of text that can be spell-checked.
 */
enum class SpellCheckableTokenType {
    /** Single-line comment (// ...) */
    COMMENT,

    /** Multi-line comment block */
    COMMENT_BLOCK,

    /** Documentation comment */
    DOC_COMMENT,

    /** String literal */
    STRING,

    /** Raw/multiline string */
    STRING_MULTILINE
}

/**
 * Manages spelling errors for the editor.
 * Provides efficient lookup of errors by line and position.
 */
class SpellCheckManager {
    private val errors = mutableListOf<SpellingError>()
    private var errorsByLine: Map<Int, List<SpellingError>> = emptyMap()

    /**
     * Sets the spelling errors, replacing any existing ones.
     */
    fun setErrors(newErrors: List<SpellingError>) {
        errors.clear()
        errors.addAll(newErrors)
        rebuildIndex()
    }

    /**
     * Adds a single spelling error.
     */
    fun addError(error: SpellingError) {
        errors.add(error)
        rebuildIndex()
    }

    /**
     * Removes all spelling errors.
     */
    fun clear() {
        errors.clear()
        errorsByLine = emptyMap()
    }

    /**
     * Gets all spelling errors.
     */
    fun getAllErrors(): List<SpellingError> = errors.toList()

    /**
     * Gets spelling errors for a specific line.
     */
    fun getErrorsForLine(line: Int): List<SpellingError> {
        return errorsByLine[line] ?: emptyList()
    }

    /**
     * Gets the spelling error at a specific offset, if any.
     */
    fun getErrorAtOffset(offset: Int): SpellingError? {
        return errors.find { offset in it.range }
    }

    /**
     * Checks if a line has any spelling errors.
     */
    fun hasErrorsOnLine(line: Int): Boolean {
        return errorsByLine.containsKey(line)
    }

    /**
     * Gets the total count of spelling errors.
     */
    fun errorCount(): Int = errors.size

    /**
     * Gets lines that have spelling errors.
     */
    fun getLinesWithErrors(): Set<Int> = errorsByLine.keys

    private fun rebuildIndex() {
        errorsByLine = errors.groupBy { it.line }
    }
}

/**
 * Interface for custom dictionary management.
 * Allows users to add words to their personal dictionary.
 */
interface CustomDictionary {
    /**
     * Adds a word to the custom dictionary.
     */
    fun addWord(word: String)

    /**
     * Removes a word from the custom dictionary.
     */
    fun removeWord(word: String)

    /**
     * Checks if a word is in the custom dictionary.
     */
    fun contains(word: String): Boolean

    /**
     * Gets all words in the custom dictionary.
     */
    fun getAllWords(): Set<String>

    /**
     * Saves the dictionary to persistent storage.
     */
    fun save()

    /**
     * Loads the dictionary from persistent storage.
     */
    fun load()
}
