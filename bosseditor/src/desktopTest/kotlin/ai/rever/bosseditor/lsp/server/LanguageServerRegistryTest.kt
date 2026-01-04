package ai.rever.bosseditor.lsp.server

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for LanguageServerRegistry.
 */
class LanguageServerRegistryTest {

    // ==================== Built-in Config Tests ====================

    @Test
    fun testPythonConfigExists() {
        val config = LanguageServerRegistry.getConfigForLanguage("python")
        assertNotNull(config)
        assertEquals("pylsp", config.id)
        assertEquals("python", config.languageId)
        assertTrue(config.fileExtensions.contains("py"))
    }

    @Test
    fun testTypeScriptConfigExists() {
        val config = LanguageServerRegistry.getConfigForLanguage("typescript")
        assertNotNull(config)
        assertTrue(config.command.contains("typescript-language-server"))
        assertTrue(config.fileExtensions.contains("ts"))
        assertTrue(config.fileExtensions.contains("tsx"))
    }

    @Test
    fun testJavaScriptConfigExists() {
        val config = LanguageServerRegistry.getConfigForLanguage("javascript")
        assertNotNull(config)
        assertTrue(config.fileExtensions.contains("js"))
        assertTrue(config.fileExtensions.contains("jsx"))
    }

    @Test
    fun testRustConfigExists() {
        val config = LanguageServerRegistry.getConfigForLanguage("rust")
        assertNotNull(config)
        assertEquals("rust-analyzer", config.id)
        assertTrue(config.fileExtensions.contains("rs"))
    }

    @Test
    fun testGoConfigExists() {
        val config = LanguageServerRegistry.getConfigForLanguage("go")
        assertNotNull(config)
        assertEquals("gopls", config.id)
        assertTrue(config.fileExtensions.contains("go"))
    }

    @Test
    fun testKotlinConfigExists() {
        val config = LanguageServerRegistry.getConfigForLanguage("kotlin")
        assertNotNull(config)
        assertTrue(config.fileExtensions.contains("kt"))
        assertTrue(config.fileExtensions.contains("kts"))
    }

    @Test
    fun testJavaConfigExists() {
        val config = LanguageServerRegistry.getConfigForLanguage("java")
        assertNotNull(config)
        assertEquals("jdtls", config.id)
        assertTrue(config.fileExtensions.contains("java"))
    }

    @Test
    fun testCppConfigExists() {
        val config = LanguageServerRegistry.getConfigForLanguage("cpp")
        assertNotNull(config)
        assertEquals("clangd", config.id)
        assertTrue(config.fileExtensions.contains("cpp"))
        assertTrue(config.fileExtensions.contains("c"))
        assertTrue(config.fileExtensions.contains("h"))
    }

    // ==================== Get Config For File Tests ====================

    @Test
    fun testGetConfigForPythonFile() {
        val config = LanguageServerRegistry.getConfigForFile("/path/to/script.py")
        assertNotNull(config)
        assertEquals("python", config.languageId)
    }

    @Test
    fun testGetConfigForTypeScriptFile() {
        val config = LanguageServerRegistry.getConfigForFile("/project/src/app.ts")
        assertNotNull(config)
        assertEquals("typescript", config.languageId)
    }

    @Test
    fun testGetConfigForTypeScriptReactFile() {
        val config = LanguageServerRegistry.getConfigForFile("/project/src/Component.tsx")
        assertNotNull(config)
        assertEquals("typescript", config.languageId)
    }

    @Test
    fun testGetConfigForJavaScriptFile() {
        val config = LanguageServerRegistry.getConfigForFile("/project/index.js")
        assertNotNull(config)
        assertEquals("javascript", config.languageId)
    }

    @Test
    fun testGetConfigForRustFile() {
        val config = LanguageServerRegistry.getConfigForFile("/project/src/main.rs")
        assertNotNull(config)
        assertEquals("rust", config.languageId)
    }

    @Test
    fun testGetConfigForGoFile() {
        val config = LanguageServerRegistry.getConfigForFile("/project/main.go")
        assertNotNull(config)
        assertEquals("go", config.languageId)
    }

    @Test
    fun testGetConfigForKotlinFile() {
        val config = LanguageServerRegistry.getConfigForFile("/project/src/Main.kt")
        assertNotNull(config)
        assertEquals("kotlin", config.languageId)
    }

    @Test
    fun testGetConfigForGradleKtsFile() {
        val config = LanguageServerRegistry.getConfigForFile("/project/build.gradle.kts")
        assertNotNull(config)
        assertEquals("kotlin", config.languageId)
    }

    @Test
    fun testGetConfigForHtmlFile() {
        val config = LanguageServerRegistry.getConfigForFile("/project/index.html")
        assertNotNull(config)
        assertEquals("html", config.languageId)
    }

    @Test
    fun testGetConfigForCssFile() {
        val config = LanguageServerRegistry.getConfigForFile("/project/styles.css")
        assertNotNull(config)
        assertEquals("css", config.languageId)
    }

    @Test
    fun testGetConfigForJsonFile() {
        val config = LanguageServerRegistry.getConfigForFile("/project/package.json")
        assertNotNull(config)
        assertEquals("json", config.languageId)
    }

    @Test
    fun testGetConfigForYamlFile() {
        val config = LanguageServerRegistry.getConfigForFile("/project/.github/workflows/ci.yml")
        assertNotNull(config)
        assertEquals("yaml", config.languageId)
    }

    @Test
    fun testGetConfigForMarkdownFile() {
        val config = LanguageServerRegistry.getConfigForFile("/project/README.md")
        assertNotNull(config)
        assertEquals("markdown", config.languageId)
    }

    @Test
    fun testGetConfigForUnknownFile() {
        val config = LanguageServerRegistry.getConfigForFile("/project/file.unknown")
        assertNull(config)
    }

    @Test
    fun testGetConfigForFileWithoutExtension() {
        // Note: Dockerfile uses file patterns, not extensions
        val config = LanguageServerRegistry.getConfigForFile("/project/Dockerfile")
        assertNotNull(config)
        assertEquals("dockerfile", config.languageId)
    }

    @Test
    fun testGetConfigCaseInsensitive() {
        val config1 = LanguageServerRegistry.getConfigForFile("/path/to/FILE.PY")
        val config2 = LanguageServerRegistry.getConfigForFile("/path/to/file.py")

        assertNotNull(config1)
        assertNotNull(config2)
        assertEquals(config1.languageId, config2.languageId)
    }

    // ==================== Get Config By ID Tests ====================

    @Test
    fun testGetConfigById() {
        val config = LanguageServerRegistry.getConfigById("pylsp")
        assertNotNull(config)
        assertEquals("python", config.languageId)
    }

    @Test
    fun testGetConfigByIdNotFound() {
        val config = LanguageServerRegistry.getConfigById("non-existent-server")
        assertNull(config)
    }

    // ==================== Registry Modification Tests ====================

    @Test
    fun testRegisterCustomConfig() {
        val customConfig = LanguageServerConfig(
            id = "custom-test-server",
            displayName = "Custom Test Server",
            languageId = "customtest",
            command = listOf("custom-test-lsp"),
            fileExtensions = listOf("ctest")
        )

        LanguageServerRegistry.register(customConfig)

        val retrieved = LanguageServerRegistry.getConfigForLanguage("customtest")
        assertNotNull(retrieved)
        assertEquals("custom-test-server", retrieved.id)

        // Also should be findable by file
        val byFile = LanguageServerRegistry.getConfigForFile("test.ctest")
        assertNotNull(byFile)
        assertEquals("customtest", byFile.languageId)

        // Cleanup
        LanguageServerRegistry.unregister("customtest")
    }

    @Test
    fun testRegisterDoesNotOverrideByDefault() {
        val originalConfig = LanguageServerRegistry.getConfigForLanguage("python")
        assertNotNull(originalConfig)
        val originalCommand = originalConfig.command

        val customConfig = LanguageServerConfig(
            id = "custom-python",
            displayName = "Custom Python",
            languageId = "python",
            command = listOf("custom-pylsp"),
            fileExtensions = listOf("py")
        )

        LanguageServerRegistry.register(customConfig, override = false)

        val afterRegister = LanguageServerRegistry.getConfigForLanguage("python")
        assertNotNull(afterRegister)
        assertEquals(originalCommand, afterRegister.command) // Should be unchanged
    }

    @Test
    fun testRegisterWithOverride() {
        // First register a custom test language
        val original = LanguageServerConfig(
            id = "override-test",
            displayName = "Original",
            languageId = "overridetest",
            command = listOf("original-cmd"),
            fileExtensions = listOf("ot")
        )
        LanguageServerRegistry.register(original)

        // Now override
        val override = LanguageServerConfig(
            id = "override-test-v2",
            displayName = "Override",
            languageId = "overridetest",
            command = listOf("override-cmd"),
            fileExtensions = listOf("ot")
        )
        LanguageServerRegistry.register(override, override = true)

        val retrieved = LanguageServerRegistry.getConfigForLanguage("overridetest")
        assertNotNull(retrieved)
        assertEquals("override-cmd", retrieved.command.first())

        // Cleanup
        LanguageServerRegistry.unregister("overridetest")
    }

    @Test
    fun testUnregister() {
        val config = LanguageServerConfig(
            id = "unregister-test",
            displayName = "Unregister Test",
            languageId = "unregtest",
            command = listOf("test"),
            fileExtensions = listOf("unreg")
        )

        LanguageServerRegistry.register(config)
        assertNotNull(LanguageServerRegistry.getConfigForLanguage("unregtest"))

        LanguageServerRegistry.unregister("unregtest")
        assertNull(LanguageServerRegistry.getConfigForLanguage("unregtest"))
    }

    // ==================== Query Tests ====================

    @Test
    fun testGetAllConfigs() {
        val configs = LanguageServerRegistry.getAllConfigs()
        assertTrue(configs.isNotEmpty())
        assertTrue(configs.any { it.languageId == "python" })
        assertTrue(configs.any { it.languageId == "typescript" })
        assertTrue(configs.any { it.languageId == "rust" })
    }

    @Test
    fun testGetEnabledConfigs() {
        val configs = LanguageServerRegistry.getEnabledConfigs()
        assertTrue(configs.isNotEmpty())
        assertTrue(configs.all { it.enabled })
    }

    @Test
    fun testGetSupportedExtensions() {
        val extensions = LanguageServerRegistry.getSupportedExtensions()
        assertTrue(extensions.contains("py"))
        assertTrue(extensions.contains("ts"))
        assertTrue(extensions.contains("js"))
        assertTrue(extensions.contains("rs"))
        assertTrue(extensions.contains("go"))
        assertTrue(extensions.contains("kt"))
    }

    @Test
    fun testHasServerForExtension() {
        assertTrue(LanguageServerRegistry.hasServerForExtension("py"))
        assertTrue(LanguageServerRegistry.hasServerForExtension("ts"))
        assertTrue(LanguageServerRegistry.hasServerForExtension("PY")) // Case insensitive
        assertFalse(LanguageServerRegistry.hasServerForExtension("unknownext"))
    }
}
