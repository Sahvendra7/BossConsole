package ai.rever.bosseditor.spelling

import java.io.File
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Simple dictionary-based spell checker implementation.
 *
 * This is a basic implementation for development/testing.
 * For production, consider using Hunspell or LanguageTool.
 *
 * Features:
 * - Loads word lists from resources or files
 * - Supports custom user dictionary
 * - Provides basic suggestions using edit distance
 * - Thread-safe lazy initialization
 *
 * JVM-only: Desktop target only (see CLAUDE.md).
 */
class SimpleSpellChecker(
    private val dictionaryPath: String? = null,
    private val customDictionaryPath: String = System.getProperty("user.home") + "/.boss/spelling/custom.txt"
) : SpellChecker {

    // Thread-safe initialization state using CountDownLatch for proper synchronization
    private val initLatch = CountDownLatch(1)
    @Volatile
    private var dictionary: Set<String> = emptySet()
    @Volatile
    private var prefixMap: Map<String, Set<String>> = emptyMap()
    private val customDictionary: MutableSet<String> = java.util.Collections.synchronizedSet(mutableSetOf())
    @Volatile
    private var currentLanguage: String = "en_US"
    @Volatile
    private var isInitialized: Boolean = false

    // Common programming terms to always accept
    private val programmingTerms = setOf(
        // Common keywords
        "kotlin", "java", "python", "javascript", "typescript",
        "func", "impl", "struct", "enum", "async", "await",
        "nullable", "nonnull", "lateinit", "inline", "reified",
        // Common abbreviations
        "args", "params", "config", "impl", "init", "ctx",
        "msg", "btn", "img", "src", "dst", "idx", "len",
        // Camel case parts
        "todo", "fixme", "xxx", "hack", "note", "bug",
        // API terms
        "api", "url", "uri", "http", "https", "json", "xml",
        "jwt", "oauth", "cors", "csrf", "xss", "sql",
        // Common tech terms
        "localhost", "webhook", "callback", "frontend", "backend"
    )

    /**
     * Ensures the spell checker is initialized.
     * Uses CountDownLatch for proper thread synchronization without busy-wait.
     * Safe to call from any thread.
     */
    private fun ensureInitialized() {
        if (isInitialized) return

        // Fast path: check if already initialized
        if (initLatch.count == 0L) return

        // Try to be the initializer thread
        synchronized(this) {
            if (isInitialized) return
            if (initLatch.count == 0L) return

            try {
                // Load main dictionary
                val words = loadMainDictionary()
                dictionary = words

                // Build prefix map for efficient suggestions (O(1) lookup by prefix)
                prefixMap = buildPrefixMap(words)

                // Load custom dictionary
                loadCustomDictionary()

                isInitialized = true
            } catch (e: Exception) {
                println("[SimpleSpellChecker] Failed to initialize: ${e.message}")
                // Fall back to empty dictionary
                dictionary = emptySet()
                prefixMap = emptyMap()
                isInitialized = true
            } finally {
                // Signal all waiting threads that initialization is complete
                initLatch.countDown()
            }
        }

        // Wait for initialization to complete (with timeout to prevent deadlock)
        if (!isInitialized) {
            try {
                initLatch.await(5, TimeUnit.SECONDS)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                println("[SimpleSpellChecker] Initialization interrupted")
            }
        }
    }

    /**
     * Builds a prefix map for efficient suggestion lookups.
     * Groups words by their first 2 characters.
     */
    private fun buildPrefixMap(words: Set<String>): Map<String, Set<String>> {
        return words.groupBy { word ->
            if (word.length >= 2) word.substring(0, 2) else word
        }.mapValues { it.value.toSet() }
    }

    private fun loadMainDictionary(): Set<String> {
        val words = mutableSetOf<String>()

        // Add programming terms
        words.addAll(programmingTerms)

        // Try to load from file if provided
        if (dictionaryPath != null) {
            try {
                val file = File(dictionaryPath)
                if (file.exists()) {
                    file.useLines { lines ->
                        lines.forEach { line ->
                            val word = line.trim().lowercase(Locale.US)
                            if (word.isNotEmpty() && !word.startsWith("#")) {
                                words.add(word)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                println("[SimpleSpellChecker] Failed to load dictionary: ${e.message}")
            }
        }

        // Load built-in common English words (basic set for demo)
        words.addAll(loadBuiltInWords())

        return words
    }

    private fun loadBuiltInWords(): Set<String> {
        // Basic set of common English words for development
        // In production, this would load from a comprehensive dictionary file
        return setOf(
            // Articles, prepositions, conjunctions
            "a", "an", "the", "and", "or", "but", "if", "then", "else",
            "in", "on", "at", "to", "for", "of", "with", "by", "from",
            "is", "are", "was", "were", "be", "been", "being",
            "have", "has", "had", "do", "does", "did", "will", "would",
            "could", "should", "may", "might", "must", "shall", "can",

            // Common verbs
            "get", "set", "add", "remove", "delete", "create", "update",
            "read", "write", "open", "close", "start", "stop", "run",
            "call", "return", "check", "validate", "process", "handle",
            "load", "save", "send", "receive", "connect", "disconnect",

            // Common nouns
            "file", "line", "code", "text", "string", "number", "value",
            "name", "type", "class", "function", "method", "variable",
            "list", "array", "map", "set", "object", "item", "element",
            "error", "warning", "message", "result", "response", "request",
            "user", "data", "state", "event", "action", "handler",
            "path", "directory", "folder", "document", "content",

            // Common adjectives
            "new", "old", "first", "last", "next", "previous",
            "valid", "invalid", "empty", "full", "null", "default",
            "public", "private", "static", "final", "abstract",
            "true", "false", "yes", "no", "ok", "cancel",

            // Numbers as words
            "zero", "one", "two", "three", "four", "five",
            "six", "seven", "eight", "nine", "ten",

            // Common programming comment words
            "this", "that", "which", "when", "where", "what", "why", "how",
            "note", "see", "also", "example", "usage", "parameter",
            "returns", "throws", "deprecated", "since", "version",
            "author", "copyright", "license"
        )
    }

    private fun loadCustomDictionary() {
        try {
            val file = File(customDictionaryPath)
            if (file.exists()) {
                file.useLines { lines ->
                    lines.forEach { line ->
                        val word = line.trim().lowercase(Locale.US)
                        if (word.isNotEmpty()) {
                            customDictionary.add(word)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            println("[SimpleSpellChecker] Failed to load custom dictionary: ${e.message}")
        }
    }

    private fun saveCustomDictionary() {
        try {
            val file = File(customDictionaryPath)
            file.parentFile?.mkdirs()
            file.writeText(customDictionary.sorted().joinToString("\n"))
        } catch (e: Exception) {
            println("[SimpleSpellChecker] Failed to save custom dictionary: ${e.message}")
        }
    }

    override fun check(word: String): Boolean {
        if (word.isBlank()) return true

        // Ensure dictionary is loaded (lazy initialization)
        ensureInitialized()

        val normalized = word.lowercase(Locale.US)

        // Skip checking for:
        // - Single characters
        // - Numbers
        // - Words with digits
        // - CamelCase parts (will be split by caller)
        if (normalized.length <= 1) return true
        if (normalized.all { it.isDigit() }) return true
        if (normalized.any { it.isDigit() }) return true

        // Check custom dictionary first (user added words)
        if (customDictionary.contains(normalized)) return true

        // Check main dictionary
        if (dictionary.contains(normalized)) return true

        // Check with common suffixes removed
        for (suffix in listOf("s", "es", "ed", "ing", "er", "est", "ly", "ness", "ment", "tion", "able", "ible")) {
            if (normalized.endsWith(suffix)) {
                val base = normalized.dropLast(suffix.length)
                if (base.length >= 2 && dictionary.contains(base)) return true
            }
        }

        return false
    }

    override fun suggest(word: String): List<String> {
        if (word.isBlank()) return emptyList()

        // Ensure dictionary is loaded (lazy initialization)
        ensureInitialized()

        val normalized = word.lowercase(Locale.US)
        val suggestions = mutableListOf<Pair<String, Int>>()

        // Use prefix map for efficient candidate lookup instead of O(n) scan
        // Check words with same prefix and similar prefixes (off-by-one first char)
        val candidatePrefixes = mutableSetOf<String>()
        if (normalized.length >= 2) {
            candidatePrefixes.add(normalized.substring(0, 2))
            // Add adjacent prefixes for typos in first two characters
            val firstChar = normalized[0]
            val secondChar = normalized[1]
            for (c in listOf(firstChar - 1, firstChar + 1)) {
                if (c.isLetter()) {
                    candidatePrefixes.add("$c$secondChar")
                }
            }
            for (c in listOf(secondChar - 1, secondChar + 1)) {
                if (c.isLetter()) {
                    candidatePrefixes.add("$firstChar$c")
                }
            }
        }

        // Get candidate words from prefix map (much smaller set than full dictionary)
        val candidates = candidatePrefixes.flatMap { prefix ->
            prefixMap[prefix] ?: emptySet()
        }.toSet() + customDictionary

        // Find words with small edit distance from candidates only
        for (dictWord in candidates) {
            if (dictWord.length in (normalized.length - 2)..(normalized.length + 2)) {
                val distance = levenshteinDistance(normalized, dictWord)
                if (distance <= 2) {
                    suggestions.add(dictWord to distance)
                }
            }
        }

        // Sort by edit distance and return top suggestions
        return suggestions
            .sortedBy { it.second }
            .take(5)
            .map { it.first }
    }

    private fun levenshteinDistance(s1: String, s2: String): Int {
        val m = s1.length
        val n = s2.length

        if (m == 0) return n
        if (n == 0) return m

        val dp = Array(m + 1) { IntArray(n + 1) }

        for (i in 0..m) dp[i][0] = i
        for (j in 0..n) dp[0][j] = j

        for (i in 1..m) {
            for (j in 1..n) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,      // deletion
                    dp[i][j - 1] + 1,      // insertion
                    dp[i - 1][j - 1] + cost // substitution
                )
            }
        }

        return dp[m][n]
    }

    override fun addToDictionary(word: String) {
        val normalized = word.lowercase(Locale.US)
        if (normalized.isNotBlank()) {
            customDictionary.add(normalized)
            saveCustomDictionary()
        }
    }

    override fun isInCustomDictionary(word: String): Boolean {
        return customDictionary.contains(word.lowercase(Locale.US))
    }

    override fun getCustomDictionaryWords(): Set<String> = customDictionary.toSet()

    override fun removeFromDictionary(word: String) {
        val normalized = word.lowercase(Locale.US)
        if (customDictionary.remove(normalized)) {
            saveCustomDictionary()
        }
    }

    override fun isReady(): Boolean = isInitialized

    override fun getLanguage(): String = currentLanguage

    override fun setLanguage(languageCode: String): Boolean {
        // Simple implementation only supports English
        return if (languageCode.startsWith("en")) {
            currentLanguage = languageCode
            true
        } else {
            false
        }
    }

    override fun getAvailableLanguages(): List<String> = listOf("en_US", "en_GB")
}
