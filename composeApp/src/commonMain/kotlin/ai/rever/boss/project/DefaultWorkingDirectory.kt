package ai.rever.boss.project

import ai.rever.boss.utils.logging.BossLogger
import ai.rever.boss.utils.logging.LogCategory
import java.io.File
import java.io.IOException
import java.nio.file.Files

/**
 * Where BOSS works when no project is selected.
 *
 * Everything that needed a directory and had no project used to reach for
 * `System.getProperty("user.home")`: the `{projectPath}` placeholder in workspaces and split
 * templates, and every terminal opened before a project is picked (a null working directory
 * lands the shell in `~`). On macOS that is the worst available default - the first `ls`, or
 * any agent started in `~`, walks into `~/Desktop`, `~/Documents` and `~/Downloads`, and TCC
 * puts a permission prompt in front of the user for each one, for a directory they never
 * asked BOSS to open.
 *
 * `~/BossProjects` is the directory BOSS already owns: [ProjectCreationService] creates
 * projects there by default and the clone dialog clones there. Nothing macOS guards lives
 * inside it, so starting there asks for nothing.
 *
 * This does **not** select a project. "No Project" stays "No Project" - what changes is only
 * which directory the no-project case resolves to.
 */
object DefaultWorkingDirectory {
    private val logger = BossLogger.forComponent("DefaultWorkingDirectory")

    /**
     * The default working directory, created if it does not exist yet.
     *
     * Falls back to the user's home directory - the historical behaviour - when
     * `~/BossProjects` cannot be created or a file already occupies that name. Handing back a
     * path that does not exist would be worse than the problem this fixes: a terminal spawned
     * with a non-existent working directory fails to start, where the old default only started
     * somewhere noisy.
     *
     * Deliberately not cached. It is called when a tab is created, never in a loop, and a
     * cached path would go on being handed out after the user moved or deleted the directory.
     */
    fun path(): String {
        val target = File(ProjectCreationService.getDefaultProjectsDirectory())
        return ensureDirectory(target) ?: homeDirectory(target)
    }

    /** [projectPath] when a project is selected, [path] otherwise. */
    fun resolve(projectPath: String?): String = resolve(projectPath, ::path)

    /**
     * [projectPath] if it names a selected project, null if it stands for "no project".
     *
     * Blank counts as absent: `WindowProjectState` models "no project" as an empty path, and a
     * hand-edited workspace file can carry a whitespace-only one.
     */
    internal fun selectedOrNull(projectPath: String?): String? = projectPath?.takeIf { it.isNotBlank() }

    /**
     * [resolve] against a supplied default, so tests can exercise the fallback without calling
     * [path] - which creates a directory under the real home directory of whatever machine
     * runs them.
     */
    internal fun resolve(
        projectPath: String?,
        default: () -> String,
    ): String = selectedOrNull(projectPath) ?: default()

    /**
     * What to write to a saved workspace for a terminal whose working directory is
     * [workingDirectory], given [default] from [path].
     *
     * Null means "re-resolve on restore", and that is the answer for a terminal sitting in the
     * default directory. Before this class, a terminal opened with no project carried a null
     * working directory and `WorkspaceApplier` substituted whatever project was selected when
     * the workspace was applied *later*. Persisting the resolved `~/BossProjects` verbatim
     * would freeze that decision: re-applying the layout with a project selected would open
     * the terminal in the projects folder instead of the project.
     *
     * Two consequences of keying on the path rather than on "was this resolved from no
     * project". [default] must come from [path], not from
     * `ProjectCreationService.getDefaultProjectsDirectory()` by a route that skips `File`
     * normalization, or the comparison never matches on Windows. And a terminal the user
     * deliberately pointed at `~/BossProjects` is treated as a default one; restore then opens
     * the projects folder, which is where it was pointed anyway.
     */
    internal fun persisted(
        workingDirectory: String?,
        default: String,
    ): String? = workingDirectory?.takeIf { it != default }

    /**
     * [target]'s path once it is known to be a directory, or null if it cannot be made one.
     *
     * Split out so tests can exercise both outcomes against a temporary directory rather than
     * creating `~/BossProjects` on whatever machine runs them.
     */
    internal fun ensureDirectory(target: File): String? {
        // createDirectories, not mkdirs(). Three callers create this same path - the startup
        // warm-up in main(), the first window's resolve() and validateProjectLocation - and
        // mkdirs() returns false when another of them won the race, which would send this
        // caller to the home-directory fallback on exactly the cold first launch this exists
        // to fix. createDirectories is idempotent by contract, and there is no isDirectory
        // pre-check to go stale between the look and the create.
        val failure =
            try {
                Files.createDirectories(target.toPath())
                null
            } catch (e: IOException) {
                // Includes FileAlreadyExistsException, which createDirectories raises only for
                // a non-directory at the path - a *file* named BossProjects.
                e
            } catch (e: SecurityException) {
                e
            }

        if (failure == null) return target.path

        // Every failure that happens in the field - a read-only home, a quota, a file at the
        // path - lands here, and the user meets it as the home-directory prompts this exists
        // to remove. Silence would leave nothing in the log to explain them.
        logger.warn(
            LogCategory.FILE,
            "Cannot create the default projects directory, falling back to the home directory",
            mapOf("path" to target.path),
            error = failure,
        )
        return null
    }

    /** [target] itself if there is no home directory to fall back to, which no JVM reports. */
    private fun homeDirectory(target: File): String = System.getProperty("user.home") ?: target.path
}
