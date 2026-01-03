package ai.rever.bosseditor.spelling

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.lang.reflect.Method

class SpellCheckServiceTest {

    @Test
    fun testSplitCamelCaseSimple() {
        val result = splitCamelCaseWithOffsets("camelCase")

        assertEquals(2, result.size)
        assertEquals("camel" to 0, result[0])
        assertEquals("Case" to 5, result[1])
    }

    @Test
    fun testSplitCamelCaseAcronym() {
        val result = splitCamelCaseWithOffsets("HTMLParser")

        assertEquals(2, result.size)
        assertEquals("HTML" to 0, result[0])
        assertEquals("Parser" to 4, result[1])
    }

    @Test
    fun testSplitCamelCaseMultipleAcronyms() {
        val result = splitCamelCaseWithOffsets("XMLHttpRequest")

        assertEquals(3, result.size)
        assertEquals("XML" to 0, result[0])
        assertEquals("Http" to 3, result[1])
        assertEquals("Request" to 7, result[2])
    }

    @Test
    fun testSplitCamelCaseSingleWord() {
        val result = splitCamelCaseWithOffsets("hello")

        assertEquals(1, result.size)
        assertEquals("hello" to 0, result[0])
    }

    @Test
    fun testSplitCamelCaseAllUppercase() {
        val result = splitCamelCaseWithOffsets("HTML")

        assertEquals(1, result.size)
        assertEquals("HTML" to 0, result[0])
    }

    @Test
    fun testSplitCamelCaseAllLowercase() {
        val result = splitCamelCaseWithOffsets("lowercase")

        assertEquals(1, result.size)
        assertEquals("lowercase" to 0, result[0])
    }

    @Test
    fun testSplitCamelCaseShortWord() {
        val result = splitCamelCaseWithOffsets("a")

        assertEquals(1, result.size)
        assertEquals("a" to 0, result[0])
    }

    @Test
    fun testSplitCamelCaseEmpty() {
        val result = splitCamelCaseWithOffsets("")

        assertEquals(1, result.size)
        assertEquals("" to 0, result[0])
    }

    @Test
    fun testSplitCamelCaseMultipleParts() {
        val result = splitCamelCaseWithOffsets("getValueFromDatabase")

        assertEquals(4, result.size)
        assertEquals("get" to 0, result[0])
        assertEquals("Value" to 3, result[1])
        assertEquals("From" to 8, result[2])
        assertEquals("Database" to 12, result[3])
    }

    @Test
    fun testSplitCamelCaseOffsetsAreCorrect() {
        val word = "XMLHttpRequest"
        val result = splitCamelCaseWithOffsets(word)

        // Verify each part can be extracted from original string at the given offset
        for ((part, offset) in result) {
            val extracted = word.substring(offset, offset + part.length)
            assertEquals(part, extracted, "Part '$part' at offset $offset should match substring")
        }
    }

    @Test
    fun testSplitCamelCasePartsAreContinuous() {
        val word = "camelCaseWord"
        val result = splitCamelCaseWithOffsets(word)

        // Sum of all part lengths should equal original word length
        val totalLength = result.sumOf { it.first.length }
        assertEquals(word.length, totalLength, "Sum of part lengths should equal word length")

        // Concatenating parts should reconstruct the word
        val reconstructed = result.joinToString("") { it.first }
        assertEquals(word, reconstructed, "Parts should reconstruct original word")
    }

    // Helper to access private method via reflection
    private fun splitCamelCaseWithOffsets(word: String): List<Pair<String, Int>> {
        // Create a minimal SpellCheckService for testing
        val spellChecker = object : SpellChecker {
            override fun check(word: String) = true
            override fun suggest(word: String) = emptyList<String>()
            override fun addToDictionary(word: String) {}
            override fun isInCustomDictionary(word: String) = false
            override fun getCustomDictionaryWords() = emptySet<String>()
            override fun removeFromDictionary(word: String) {}
            override fun isReady() = true
            override fun getLanguage() = "en_US"
            override fun setLanguage(languageCode: String) = true
            override fun getAvailableLanguages() = listOf("en_US")
        }

        val service = SpellCheckService(spellChecker)
        val method: Method = SpellCheckService::class.java.getDeclaredMethod(
            "splitCamelCaseWithOffsets",
            String::class.java
        )
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return method.invoke(service, word) as List<Pair<String, Int>>
    }
}
