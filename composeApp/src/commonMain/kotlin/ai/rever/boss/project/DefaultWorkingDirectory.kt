package ai.rever.boss.project

import ai.rever.boss.utils.logging.BossLogger
import ai.rever.boss.utils.logging.LogCategory
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.InvalidPathException

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
    fun path(): String = path(::ensureDirectory)

    /**
     * [path] against a supplied creator, so the fallback composition is testable without a
     * machine on which creating `~/BossProjects` actually fails.
     */
    internal fun path(ensure: (File) -> String?): String {
        val target = File(nominalPath())
        return ensure(target) ?: homeDirectory(target)
    }

    /**
     * Where the default working directory *would* be, without creating it or touching the
     * filesystem at all.
     *
     * For callers that only need the string to compare against - [persisted], through
     * `WorkspaceExtractor`. Two reasons they must not use [path] instead:
     *
     * - **Cost and position.** Extraction runs from the auto-save `snapshotFlow`, whose
     *   producer re-runs on the composition thread whenever any tab's title, url or working
     *   directory changes, and from the Last Session teardown, which can be the shutdown-hook
     *   thread. A `createDirectories` syscall per browser-title update is the kind of blocking
     *   I/O `docs/THREADING.md` rules out, and its warn-on-failure would repeat just as often.
     * - **Stability.** [path] answers with the *home* directory when creation fails. A
     *   transient failure at extract time would make [persisted] compare a terminal's real
     *   `~/BossProjects` against `~`, find them different, and freeze the resolved default
     *   into the saved layout - precisely what [persisted] exists to prevent. This cannot
     *   fail, so *that* direction is closed: which string the comparison uses no longer
     *   depends on filesystem state at extract time.
     *
     * The mirror case is not closed, and is not worth the machinery: if creation failed when
     * the *terminal* was created, its working directory is the home directory, which differs
     * from this and so is persisted verbatim. Treating `~` as a default too would silence a
     * user who deliberately opened a terminal there, to fix a case that only arises once the
     * fallback has already fired.
     *
     * Through `File` on both sides, so the separator normalization matches what [resolve]
     * handed the tab.
     */
    fun nominalPath(): String = File(ProjectCreationService.getDefaultProjectsDirectory()).path

    /**
     * [projectPath] when a project is selected, [path] otherwise.
     *
     * **Touches the filesystem on the no-project branch** - a `stat`, and a create the first
     * time. Free when a project is selected, since [path] is never reached. Call it off the
     * main thread where that is possible: `applyWorkspace` does. The tab-creation handlers do
     * not, because they are not suspending and the cost is one `stat` in the steady state
     * (the startup warm-up in `main` has normally done the create already) - but on a network
     * home directory, a Windows roaming profile or a macOS network account, an unresponsive
     * volume stalls the frame. That is the known cost of this being uncached; see [path].
     */
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
     * deliberately pointed at `~/BossProjects` is indistinguishable from a default one: with
     * no project selected restore lands back in the projects folder, which is where it was
     * pointed, but *with* a project selected the null re-resolves to the project instead. That
     * second case loses the user's choice. Accepted rather than fixed - telling the two apart
     * needs a flag on `TabConfig`, i.e. a workspace format change, to serve a terminal
     * deliberately opened in the projects folder itself.
     */
    internal fun persisted(
        workingDirectory: String?,
        default: String,
    ): String? = workingDirectory?.takeIf { it != default }

    /**
     * A saved workspace's terminal working directory, read back with the home directory
     * treated as "not set".
     *
     * Without this the fix does not reach anyone who already uses BOSS. Saved layouts on disk
     * were written by the code this replaces: a no-project terminal resolved to `~`, carried a
     * *non-null* `~`, and `WorkspaceExtractor` persisted it verbatim. So Last Session would go
     * on restoring terminals into the home directory after the upgrade - and [persisted] would
     * go on writing `~` back out, because `~` is not the default it compares against. The TCC
     * prompts would return on the first restore and never stop, for exactly the people this
     * change is for.
     *
     * Reading `~` as absent lets [WorkspaceApplier] re-resolve it to `~/BossProjects`; the next
     * auto-save then extracts that, [persisted] nulls it, and the layout is repaired. It is a
     * migration by value rather than a versioned one, because `TabConfig` carries no version
     * and adding one to fix this would be a workspace format change.
     *
     * The cost is a terminal someone deliberately parked in their home directory, which
     * re-resolves to the projects folder once. That is the trade this whole change is: the
     * home directory is the place BOSS should not be working in.
     */
    internal fun restored(
        workingDirectory: String?,
        home: String? = System.getProperty("user.home"),
    ): String? = workingDirectory?.takeIf { it != home }

    /**
     * [target]'s path once it is known to be a directory, or null if it cannot be made one.
     *
     * Split out so tests can exercise both outcomes against a temporary directory rather than
     * creating `~/BossProjects` on whatever machine runs them.
     */
    internal fun ensureDirectory(target: File): String? {
        val failure =
            try {
                // isDirectory first: that is the steady state and the only branch the UI
                // thread normally takes, and it is one stat with no create and no exception.
                // Short-circuiting the *success* case is race-free - a true answer means the
                // directory is there.
                //
                // Otherwise createDirectories, not mkdirs(). Three callers create this same
                // path - the startup warm-up in main(), the first window's resolve() and
                // validateProjectLocation - and mkdirs() returns false when another of them
                // won the race, which would send this caller to the home-directory fallback
                // on exactly the cold first launch this exists to fix. createDirectories
                // succeeds when the directory is already there, so losing the race between
                // the check above and this line is not a failure either.
                if (!target.isDirectory) Files.createDirectories(target.toPath())
                null
            } catch (e: IOException) {
                // Includes FileAlreadyExistsException, which createDirectories raises only for
                // a non-directory at the path - a *file* named BossProjects.
                e
            } catch (e: SecurityException) {
                e
            } catch (e: InvalidPathException) {
                // Unchecked, from toPath(). Vanishingly unlikely for a home-relative path, but
                // it would otherwise escape past the fallback to the caller.
                e
            }

        // Every failure that happens in the field - a read-only home, a quota, a file at the
        // path - lands here, and the user meets it as either the home-directory prompts this
        // exists to remove or a refusal in the New Project wizard. Silence would leave nothing
        // in the log to explain either. The message names only the failure, not what the
        // caller does about it: validateProjectLocation does not fall back to anything.
        failure?.let {
            logger.warn(
                LogCategory.FILE,
                "Cannot create the projects directory",
                mapOf("path" to target.path),
                error = it,
            )
        }

        return if (failure == null) target.path else null
    }

    /**
     * [target] itself if there is no home directory to fall back to, which no JVM reports.
     *
     * The one branch where this class breaks its own contract of "a path a terminal can start
     * in": with no `user.home`, `getDefaultProjectsDirectory()` yields the *relative*
     * `BossProjects`, which resolves against the process working directory - `/` for a
     * packaged `.app`. Nothing is gained by inventing a better guess here; the JVM defining no
     * home directory is a broken environment, and the previous code produced a literal
     * `null/BossProjects` in the same situation.
     */
    private fun homeDirectory(target: File): String = System.getProperty("user.home") ?: target.path
}
