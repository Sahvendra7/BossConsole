package ai.rever.boss.components.plugin.panels.left_top

import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for directoryHasChildren / scanDirectory edge cases:
 * empty dir, filtered-only dir (e.g. only node_modules), symlink, and
 * malformed path input (InvalidPathException must not escape as uncaught).
 */
class DesktopFileScannerTest {

    private fun tempDir(): File = Files.createTempDirectory("scanner-test").toFile().apply {
        deleteOnExit()
    }

    @Test
    fun `directoryHasChildren returns false for a non-existent path`() {
        val missing = File(tempDir(), "does-not-exist").absolutePath
        assertFalse(directoryHasChildren(missing))
    }

    @Test
    fun `directoryHasChildren returns false for an empty directory`() {
        val dir = tempDir()
        assertFalse(directoryHasChildren(dir.absolutePath))
    }

    @Test
    fun `directoryHasChildren returns true when a visible file is present`() {
        val dir = tempDir()
        File(dir, "readme.txt").writeText("hi")
        assertTrue(directoryHasChildren(dir.absolutePath))
    }

    @Test
    fun `directoryHasChildren returns false when only hidden entries are present`() {
        val dir = tempDir()
        File(dir, ".hidden").writeText("secret")
        assertFalse(directoryHasChildren(dir.absolutePath))
    }

    @Test
    fun `directoryHasChildren returns false when only a filtered directory name is present`() {
        val dir = tempDir()
        File(dir, "node_modules").mkdir()
        assertFalse(directoryHasChildren(dir.absolutePath))
    }

    @Test
    fun `directoryHasChildren does not throw on a malformed path`() {
        // A NUL byte is illegal on every platform's filesystem and makes Paths.get
        // throw InvalidPathException. That must be caught, not propagated, since
        // arbitrary strings can reach this function from plugins.
        val nul = 0.toChar()
        val malformed = "/tmp/bad" + nul + "path"
        assertFalse(directoryHasChildren(malformed))
    }

    @Test
    fun `directoryHasChildren follows a symlink to a non-empty directory`() {
        val real = tempDir()
        File(real, "file.txt").writeText("hi")
        val linkDir = tempDir()
        val link = File(linkDir, "link")
        Files.createSymbolicLink(link.toPath(), real.toPath())
        assertTrue(directoryHasChildren(link.absolutePath))
    }

    @Test
    fun `scanDirectory reports hasChildren false for a directory containing only node_modules`() {
        val dir = tempDir()
        File(dir, "node_modules").mkdir()
        val node = scanDirectory(dir.absolutePath)
        assertEquals(false, node?.hasChildren)
        assertTrue(node?.children.orEmpty().isEmpty())
    }

    @Test
    fun `scanDirectory reports hasChildren true for a directory with a visible file`() {
        val dir = tempDir()
        File(dir, "readme.txt").writeText("hi")
        val node = scanDirectory(dir.absolutePath)
        assertEquals(true, node?.hasChildren)
    }
}
