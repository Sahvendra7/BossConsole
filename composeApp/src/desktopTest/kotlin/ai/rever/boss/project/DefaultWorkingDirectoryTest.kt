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
     * The reason this is `Files.createDirectories` and not `mkdirs()`, which returns false when
     * someone else created the directory first. Three callers race on a cold first launch - the
     * startup warm-up, the first window resolving a terminal's working directory, and
     * `validateProjectLocation` - and a loser falling back to the home directory would put the
     * TCC prompts back on exactly the launch this exists to fix.
     */
    @Test
    fun `losing the creation race still yields the directory`() {
        val target = File(tempRoot, "BossProjects")

        val first = DefaultWorkingDirectory.ensureDirectory(target)
        val second = DefaultWorkingDirectory.ensureDirectory(target)

        assertEquals(target.path, first)
        assertEquals(target.path, second, "the second caller gets the path, not the fallback")
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

        val default = { "/tmp/default" }
        assertEquals("/tmp/default", DefaultWorkingDirectory.resolve("", default))
        assertEquals("/tmp/default", DefaultWorkingDirectory.resolve("   ", default))
        assertEquals("/tmp/default", DefaultWorkingDirectory.resolve(null, default))
        assertEquals("/tmp/project", DefaultWorkingDirectory.resolve("/tmp/project", default))
    }

    /**
     * A terminal sitting in the default directory is saved as null so restore re-resolves it.
     * Persisting the resolved path would freeze the no-project answer into the workspace, and
     * re-applying that layout *with* a project selected would open the projects folder instead
     * of the project - the behaviour null carried before anything resolved eagerly.
     */
    @Test
    fun `the default working directory is not persisted into a workspace`() {
        val default = "/Users/someone/BossProjects"

        assertNull(DefaultWorkingDirectory.persisted(default, default))
        assertNull(DefaultWorkingDirectory.persisted(null, default))
        assertEquals("/work/repo", DefaultWorkingDirectory.persisted("/work/repo", default))
    }

    /**
     * The point of the change. Through [DefaultWorkingDirectory.nominalPath], which is the
     * whole reason that function exists: it answers without creating anything, so this does
     * not depend on - or leave behind - `~/BossProjects` on the machine running it.
     */
    @Test
    fun `the default directory is BossProjects, not the home directory`() {
        val home = System.getProperty("user.home")

        assertNotEquals(home, DefaultWorkingDirectory.nominalPath())
        assertEquals(File(home, "BossProjects").path, DefaultWorkingDirectory.nominalPath())
    }

    /**
     * `persisted()` compares a tab's working directory against this, and `resolve()` is what
     * put that string there. A separator that disagreed - `getDefaultProjectsDirectory()` used
     * to build its own with a literal `/` - would make the comparison silently never match on
     * Windows, so every no-project terminal would freeze the resolved path into saved layouts.
     */
    @Test
    fun `the nominal path is separator-normalised, like every path compared against it`() {
        assertEquals(
            File(ProjectCreationService.getDefaultProjectsDirectory()).path,
            DefaultWorkingDirectory.nominalPath(),
        )
        assertEquals(DefaultWorkingDirectory.nominalPath(), ProjectCreationService.getDefaultProjectsDirectory())
    }
}
