package ai.rever.boss.updater

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Hardening tests for the update helper scripts (Issue #37):
 * - installer logs and scripts live in the **platform** temp directory, not `/tmp`
 * - helper scripts are 0700, not 0755
 */
class UpdateScriptGeneratorHardeningTest {
    private val isWindows = System.getProperty("os.name").lowercase().contains("win")

    @Test
    fun `updater temp dir resolves inside the platform temp directory`() {
        val resolved = resolveUpdaterTempDir("/custom/tmp")

        assertEquals(UPDATER_TEMP_DIR_NAME, resolved.name)
        assertEquals(File("/custom/tmp").path, resolved.parentFile.path)
    }

    /**
     * The regression itself: `/tmp/boss-updater` was hardcoded, so on Windows the
     * log directory resolved to a `C:\tmp\boss-updater` that does not exist and
     * installer logging went nowhere.
     */
    @Test
    fun `updater temp dir is not hardcoded to slash tmp for a Windows temp directory`() {
        val windowsTemp = "C:\\Users\\example\\AppData\\Local\\Temp"

        val resolved = resolveUpdaterTempDir(windowsTemp)

        assertTrue(
            resolved.path.startsWith(windowsTemp),
            "Resolved path should sit under the supplied temp directory, was: ${resolved.path}",
        )
        assertFalse(
            resolved.path.startsWith("/tmp"),
            "The updater directory must not fall back to a hardcoded /tmp",
        )
    }

    @Test
    fun `updater temp dir defaults to the java io tmpdir property`() {
        val expectedParent = File(System.getProperty("java.io.tmpdir")).path

        assertEquals(expectedParent, resolveUpdaterTempDir().parentFile.path)
    }

    @Test
    fun `installer log file lives in the platform temp directory`() {
        val logFile = resolveUpdaterLogFile(timestamp = 1234567890, tempDirPath = "/custom/tmp")

        assertEquals("update-1234567890.log", logFile.name)
        assertEquals(resolveUpdaterTempDir("/custom/tmp").path, logFile.parentFile.path)
    }

    @Test
    fun `installer log file defaults to the java io tmpdir property`() {
        val logFile = resolveUpdaterLogFile(timestamp = 42)

        assertEquals(
            resolveUpdaterTempDir(System.getProperty("java.io.tmpdir")).path,
            logFile.parentFile.path,
            "Log file should follow java.io.tmpdir, not a hardcoded /tmp",
        )
    }

    @Test
    fun `generated helper script is owner only`() {
        assumeTrue(!isWindows, "POSIX permissions are not applicable on Windows")

        val scriptFile =
            UpdateScriptGenerator.generateMacOSUpdateScript(
                dmgPath = "/tmp/update.dmg",
                targetAppPath = "/Applications/BOSS.app",
                appPid = 12345,
            )

        try {
            val permissions = Files.getPosixFilePermissions(scriptFile.toPath())
            assertEquals(
                setOf(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE,
                ),
                permissions,
                "Helper scripts are executed with elevated privileges from a shared temp directory: 0700 only",
            )
            assertTrue(scriptFile.canExecute(), "The owner must still be able to run the script")
        } finally {
            scriptFile.delete()
        }
    }

    @Test
    fun `generated script is written under the platform temp directory`() {
        val scriptFile =
            UpdateScriptGenerator.generateMacOSUpdateScript(
                dmgPath = "/tmp/update.dmg",
                targetAppPath = "/Applications/BOSS.app",
                appPid = 12345,
            )

        try {
            assertEquals(
                resolveUpdaterTempDir().canonicalPath,
                scriptFile.parentFile.canonicalPath,
            )
        } finally {
            scriptFile.delete()
        }
    }

    @Test
    fun `generated Linux script restricts the askpass helper to the owner`() {
        val scriptFile =
            UpdateScriptGenerator.generateLinuxDebUpdateScript(
                debPath = "/tmp/BOSS-9.9.9-amd64.deb",
                appPid = 12345,
            )

        try {
            val script = scriptFile.readText()
            assertTrue(
                script.contains("chmod 700 \"\$ASKPASS_SCRIPT\""),
                "The askpass helper runs under sudo from a shared temp dir; it must be 0700",
            )
            assertFalse(
                script.contains("chmod +x \"\$ASKPASS_SCRIPT\""),
                "chmod +x leaves the askpass helper group/other-executable",
            )
        } finally {
            scriptFile.delete()
        }
    }
}
