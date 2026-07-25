package ai.rever.boss.updater

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.File
import java.nio.file.Files
import kotlin.test.assertTrue

/**
 * Fail-closed tests for [UpdateInstaller.validateDownloadFile] (Issue #37).
 *
 * Previously a canonical path escaping the staging directory, or a filename
 * carrying a shell metacharacter, only logged a warning and the update installed
 * anyway — while [UpdateScriptGenerator]'s validation rejected the identical
 * input. Both entry points now share [UpdatePathValidator] and both refuse.
 */
class UpdateDownloadValidationTest {
    private val stagingDir: File
        get() = File(System.getProperty("java.io.tmpdir"), UPDATE_STAGING_DIR_NAME)

    private fun stagedFile(name: String): File {
        stagingDir.mkdirs()
        return File(stagingDir, name).also {
            it.writeText("not a real installer")
            it.deleteOnExit()
        }
    }

    @Test
    fun `a clean artifact inside the staging directory is accepted`() {
        val file = stagedFile("BOSS-9.9.9-Universal.dmg")
        try {
            UpdateInstaller.validateDownloadFile(file, ".dmg")
        } finally {
            file.delete()
        }
    }

    @Test
    fun `an artifact outside the staging directory is rejected`() {
        val escapeDir = Files.createTempDirectory("boss-updates-escape").toFile()
        val file = File(escapeDir, "BOSS-9.9.9-Universal.dmg")
        file.writeText("not a real installer")
        try {
            val exception =
                assertThrows<SecurityException> {
                    UpdateInstaller.validateDownloadFile(file, ".dmg")
                }
            assertTrue(
                exception.message?.contains("outside the staging directory") == true,
                "Escaping the staging directory must fail closed, got: ${exception.message}",
            )
        } finally {
            file.delete()
            escapeDir.delete()
        }
    }

    /**
     * A sibling directory whose name merely starts with the staging directory's
     * name must not satisfy the containment check.
     */
    @Test
    fun `a sibling directory sharing the staging prefix is rejected`() {
        val sibling = File(System.getProperty("java.io.tmpdir"), "$UPDATE_STAGING_DIR_NAME-evil")
        sibling.mkdirs()
        val file = File(sibling, "BOSS-9.9.9-Universal.dmg")
        file.writeText("not a real installer")
        try {
            assertThrows<SecurityException> {
                UpdateInstaller.validateDownloadFile(file, ".dmg")
            }
        } finally {
            file.delete()
            sibling.delete()
        }
    }

    /**
     * Shell metacharacters in the artifact name are refused, and refused *before*
     * the filesystem is touched — the name is what gets interpolated into the
     * generated install script.
     */
    @Test
    fun `filenames carrying shell metacharacters are rejected`() {
        val hostileNames =
            listOf(
                "BOSS-9.9.9\$(whoami).dmg" to "shell metacharacters",
                "BOSS-9.9.9`whoami`.dmg" to "shell metacharacters",
                "BOSS-9.9.9;rm -rf ~.dmg" to "command separator",
                "BOSS-9.9.9|sh.dmg" to "command separator",
                "BOSS-9.9.9&sh.dmg" to "command separator",
                "BOSS-9.9.9%PATH%.dmg" to "Windows batch metacharacters",
                "BOSS-9.9.9^x.dmg" to "Windows batch metacharacters",
                "BOSS-9.9.9!x!.dmg" to "Windows batch metacharacters",
                "..BOSS-9.9.9.dmg" to "path traversal",
            )

        hostileNames.forEach { (name, expectedReason) ->
            // Deliberately a path that does NOT exist: validation must reject the
            // name without needing (or trusting) the filesystem.
            val file = File(stagingDir, name)
            val exception =
                assertThrows<SecurityException> {
                    UpdateInstaller.validateDownloadFile(file, ".dmg")
                }
            assertTrue(
                exception.message?.contains(expectedReason) == true,
                "Expected '$expectedReason' rejection for '$name', got: ${exception.message}",
            )
        }
    }

    @Test
    fun `filenames with newlines are rejected`() {
        val file = File(stagingDir, "BOSS-9.9.9.dmg\nrm -rf ~")
        val exception =
            assertThrows<SecurityException> {
                UpdateInstaller.validateDownloadFile(file, ".dmg")
            }
        assertTrue(
            exception.message?.contains("newline") == true,
            "Expected newline rejection, got: ${exception.message}",
        )
    }

    @Test
    fun `the wrong extension is rejected`() {
        val file = stagedFile("BOSS-9.9.9-Universal.zip")
        try {
            val exception =
                assertThrows<SecurityException> {
                    UpdateInstaller.validateDownloadFile(file, ".dmg")
                }
            assertTrue(
                exception.message?.contains("Invalid file extension") == true,
                "Expected extension rejection, got: ${exception.message}",
            )
        } finally {
            file.delete()
        }
    }

    @Test
    fun `a missing artifact is rejected`() {
        val file = File(stagingDir, "BOSS-does-not-exist.dmg")
        file.delete()
        val exception =
            assertThrows<SecurityException> {
                UpdateInstaller.validateDownloadFile(file, ".dmg")
            }
        assertTrue(
            exception.message?.contains("does not exist") == true,
            "Expected missing-file rejection, got: ${exception.message}",
        )
    }

    /**
     * Both entry points must agree: anything [UpdateScriptGenerator] refuses is
     * also refused by the installer's own validation.
     */
    @Test
    fun `installer validation agrees with the script generator on hostile names`() {
        val hostileName = "BOSS-9.9.9\$(whoami).dmg"

        val generatorRejection =
            assertThrows<SecurityException> {
                UpdateScriptGenerator.generateMacOSUpdateScript(
                    dmgPath = File(stagingDir, hostileName).absolutePath,
                    targetAppPath = "/Applications/BOSS.app",
                    appPid = 12345,
                )
            }
        val installerRejection =
            assertThrows<SecurityException> {
                UpdateInstaller.validateDownloadFile(File(stagingDir, hostileName), ".dmg")
            }

        assertTrue(generatorRejection.message?.contains("shell metacharacters") == true)
        assertTrue(installerRejection.message?.contains("shell metacharacters") == true)
    }
}
