package ai.rever.bosseditor.lsp.server

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for LanguageServerConfig.
 */
class LanguageServerConfigTest {

    // ==================== Extension Handling Tests ====================

    @Test
    fun testHandlesExtensionExactMatch() {
        val config = createConfig(fileExtensions = listOf("py", "pyw"))

        assertTrue(config.handlesExtension("py"))
        assertTrue(config.handlesExtension("pyw"))
        assertFalse(config.handlesExtension("js"))
    }

    @Test
    fun testHandlesExtensionCaseInsensitive() {
        val config = createConfig(fileExtensions = listOf("py"))

        assertTrue(config.handlesExtension("py"))
        assertTrue(config.handlesExtension("PY"))
        assertTrue(config.handlesExtension("Py"))
    }

    @Test
    fun testHandlesExtensionEmpty() {
        val config = createConfig(fileExtensions = emptyList())

        assertFalse(config.handlesExtension("py"))
        assertFalse(config.handlesExtension(""))
    }

    // ==================== File Path Handling Tests ====================

    @Test
    fun testHandlesFileByExtension() {
        val config = createConfig(fileExtensions = listOf("py", "pyw"))

        assertTrue(config.handlesFile("/path/to/file.py"))
        assertTrue(config.handlesFile("/path/to/file.pyw"))
        assertTrue(config.handlesFile("file.py"))
        assertTrue(config.handlesFile("/path/to/FILE.PY"))
    }

    @Test
    fun testHandlesFileNoMatchingExtension() {
        val config = createConfig(fileExtensions = listOf("py"))

        assertFalse(config.handlesFile("/path/to/file.js"))
        assertFalse(config.handlesFile("/path/to/file.ts"))
        assertFalse(config.handlesFile("/path/to/file"))
    }

    @Test
    fun testHandlesFileByPattern() {
        val config = createConfig(
            fileExtensions = listOf("sh"),
            filePatterns = listOf(".bashrc", ".zshrc", "Dockerfile*")
        )

        assertTrue(config.handlesFile("/home/user/.bashrc"))
        assertTrue(config.handlesFile("/home/user/.zshrc"))
        assertTrue(config.handlesFile("/project/Dockerfile"))
        assertTrue(config.handlesFile("/project/Dockerfile.dev"))
        assertTrue(config.handlesFile("/script.sh"))
    }

    @Test
    fun testHandlesFilePatternWildcard() {
        val config = createConfig(
            fileExtensions = emptyList(),
            filePatterns = listOf("*.dockerfile", "Dockerfile.*")
        )

        assertTrue(config.handlesFile("production.dockerfile"))
        assertTrue(config.handlesFile("Dockerfile.production"))
    }

    @Test
    fun testHandlesFileNoExtensionNoPattern() {
        val config = createConfig(
            fileExtensions = emptyList(),
            filePatterns = emptyList()
        )

        assertFalse(config.handlesFile("/any/file.txt"))
        assertFalse(config.handlesFile("Makefile"))
    }

    // ==================== Config Data Tests ====================

    @Test
    fun testConfigProperties() {
        val config = LanguageServerConfig(
            id = "test-server",
            displayName = "Test Server",
            languageId = "test",
            command = listOf("test-lsp", "--stdio"),
            fileExtensions = listOf("test"),
            rootIndicators = listOf("test.config")
        )

        assertEquals("test-server", config.id)
        assertEquals("Test Server", config.displayName)
        assertEquals("test", config.languageId)
        assertEquals(listOf("test-lsp", "--stdio"), config.command)
        assertEquals(listOf("test"), config.fileExtensions)
        assertEquals(listOf("test.config"), config.rootIndicators)
        assertTrue(config.enabled)
    }

    @Test
    fun testConfigDefaults() {
        val config = LanguageServerConfig(
            id = "test",
            displayName = "Test",
            languageId = "test",
            command = listOf("test"),
            fileExtensions = listOf("t")
        )

        assertTrue(config.filePatterns.isEmpty())
        assertTrue(config.rootIndicators.isEmpty())
        assertEquals(null, config.initializationOptions)
        assertEquals(null, config.settings)
        assertTrue(config.enabled)
    }

    // ==================== Helper Functions ====================

    private fun createConfig(
        fileExtensions: List<String> = emptyList(),
        filePatterns: List<String> = emptyList()
    ): LanguageServerConfig {
        return LanguageServerConfig(
            id = "test",
            displayName = "Test",
            languageId = "test",
            command = listOf("test"),
            fileExtensions = fileExtensions,
            filePatterns = filePatterns
        )
    }
}
