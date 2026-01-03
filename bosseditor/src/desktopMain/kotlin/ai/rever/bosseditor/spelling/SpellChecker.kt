package ai.rever.bosseditor.spelling

/**
 * Interface for spell checking functionality.
 *
 * Implementations can use various spell checking backends:
 * - Hunspell (native library)
 * - LanguageTool
 * - Custom dictionary-based
 *
 * JVM-only: Desktop target only (see CLAUDE.md).
 */
interface SpellChecker {
    /**
     * Checks if a word is spelled correctly.
     *
     * @param word The word to check
     * @return true if the word is spelled correctly, false otherwise
     */
    fun check(word: String): Boolean

    /**
     * Gets spelling suggestions for a misspelled word.
     *
     * @param word The misspelled word
     * @return List of suggested corrections, ordered by likelihood
     */
    fun suggest(word: String): List<String>

    /**
     * Adds a word to the user's custom dictionary.
     *
     * @param word The word to add
     */
    fun addToDictionary(word: String)

    /**
     * Checks if a word is in the user's custom dictionary.
     *
     * @param word The word to check
     * @return true if the word is in the custom dictionary
     */
    fun isInCustomDictionary(word: String): Boolean

    /**
     * Gets all words in the custom dictionary.
     */
    fun getCustomDictionaryWords(): Set<String>

    /**
     * Removes a word from the custom dictionary.
     */
    fun removeFromDictionary(word: String)

    /**
     * Checks if the spell checker is initialized and ready.
     */
    fun isReady(): Boolean

    /**
     * Gets the language code being used (e.g., "en_US").
     */
    fun getLanguage(): String

    /**
     * Sets the language for spell checking.
     *
     * @param languageCode The language code (e.g., "en_US", "en_GB")
     * @return true if the language was set successfully
     */
    fun setLanguage(languageCode: String): Boolean

    /**
     * Gets available languages.
     */
    fun getAvailableLanguages(): List<String>
}
