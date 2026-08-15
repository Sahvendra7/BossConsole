package ai.rever.boss.project

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * BOSS with no project selected used to work out of the user's home directory, which on macOS
 * means TCC prompts for `~/Desktop`, `~/Documents` and `~/Downloads` the first time anything
 * lists it. `~/BossProjects` - the directory [ProjectCreationService] already creates projects
 * in - holds nothing macOS guards.
 *
 * Everything here goes through [DefaultWorkingDirectory.ensureDirectory] against a temporary
 * directory. [DefaultWorkingDirectory.path] itself resolves under the real home directory, and
 * a test that called it would create `~/BossProjects` on whatever machine ran it.
 */
class DefaultWorkingDirectoryTest {
    private val tempRoot: File = Files.createTempDirectory("boss-default-cwd").toFile()

    @AfterTest
    fun cleanup() {
        tempRoot.deleteRecursively()
    }

    @Test
    fun `the directory is created when it does not exist`() {
        val target = File(tempRoot, "BossProjects")

        assertEquals(target.path, DefaultWorkingDirectory.ensureDirectory(target))
        assertTrue(target.isDirectory, "the caller is handed a path a terminal can start in")
    }

    /** Missing intermediate directories too - a home directory BOSS has never run in. */
    @Test
    fun `a missing parent is created`() {
        val target = File(tempRoot, "nested/deeper/BossProjects")

        assertEquals(target.path, DefaultWorkingDirectory.ensureDirectory(target))
        assertTrue(target.isDirectory)
    }

    @Test
    fun `an existing directory is reported as is`() {
        val target = File(tempRoot, "BossProjects").also { it.mkdirs() }
        val marker = File(target, "existing-project").also { it.mkdirs() }

        assertEquals(target.path, DefaultWorkingDirectory.ensureDirectory(target))
        assertTrue(marker.isDirectory, "nothing is cleared out from under the user's projects")
    }

    /**
     * A *file* named BossProjects. mkdirs() fails, and reporting the path usable would hand a
     * terminal a working directory it cannot start in - worse than the home directory this
     * replaces.
     */
    @Test
    fun `a file at the path is not usable`() {
        val target = File(tempRoot, "BossProjects").also { it.writeText("not a directory") }

        assertNull(DefaultWorkingDirectory.ensureDirectory(target))
    }

    @Test
    fun `a selected project always wins`() {
        assertEquals("/tmp/some-project", DefaultWorkingDirectory.selectedOrNull("/tmp/some-project"))
        assertEquals("/tmp/some-project", DefaultWorkingDirectory.resolve("/tmp/some-project"))
    }

    /** "No project" is an empty path in `WindowProjectState`, and a saved workspace can hold blanks. */
    @Test
    fun `no project falls through to the default directory`() {
        assertNull(DefaultWorkingDirectory.selectedOrNull(""))
        assertNull(DefaultWorkingDirectory.selectedOrNull("   "))
        assertNull(DefaultWorkingDirectory.selectedOrNull(null))
    }

    /**
     * The point of the change. Asserted on the string rather than by running [path], so the
     * test does not depend on `~/BossProjects` being creatable on the machine running it.
     */
    @Test
    fun `the default directory is BossProjects, not the home directory`() {
        val home = System.getProperty("user.home")
        val default = ProjectCreationService.getDefaultProjectsDirectory()

        assertNotEquals(home, default)
        assertEquals(File(home, "BossProjects").path, File(default).path)
    }
}
