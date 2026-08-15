package ai.rever.boss.project

import ai.rever.boss.utils.logging.BossLogger
import ai.rever.boss.utils.logging.LogCategory
import java.io.File

/**
 * Where BOSS works when no project is selected.
 *
 * Everything that needs a directory and has no project used to reach for
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
    fun resolve(projectPath: String?): String = selectedOrNull(projectPath) ?: path()

    /**
     * [projectPath] if it names a selected project, null if it stands for "no project".
     *
     * Blank counts as absent: `WindowProjectState` models "no project" as an empty path, and a
     * hand-edited workspace file can carry a whitespace-only one. Separate from [resolve] so
     * that rule is testable without calling [path], which creates a directory under the real
     * home directory of whatever machine runs the tests.
     */
    internal fun selectedOrNull(projectPath: String?): String? = projectPath?.takeIf { it.isNotBlank() }

    /**
     * [target]'s path once it is known to be a directory, or null if it cannot be made one.
     *
     * Split out so tests can exercise both outcomes against a temporary directory rather than
     * creating `~/BossProjects` on whatever machine runs them.
     */
    internal fun ensureDirectory(target: File): String? =
        try {
            // isDirectory is false for a *file* at that path, and mkdirs() then fails too, so
            // the file case falls through to the fallback rather than being reported usable.
            if (target.isDirectory || target.mkdirs()) target.path else null
        } catch (e: SecurityException) {
            logger.warn(
                LogCategory.FILE,
                "Cannot create the default projects directory, falling back to the home directory",
                mapOf("path" to target.path),
                error = e,
            )
            null
        }

    /** [target] itself if there is no home directory to fall back to, which no JVM reports. */
    private fun homeDirectory(target: File): String = System.getProperty("user.home") ?: target.path
}
