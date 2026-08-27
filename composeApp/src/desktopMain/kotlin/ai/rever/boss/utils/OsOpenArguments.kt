package ai.rever.boss.utils

import java.io.File
import java.net.URI
import java.net.URISyntaxException

/**
 * Turns the `argv` the OS launched BOSS with into `boss://` deep links.
 *
 * Windows and Linux do not send an open-file event: the shell and the desktop
 * file hand the path to the process as an argument. Before this, `main.kt` looked
 * only for args starting `boss://`, `http://` or `https://`, so a file path in
 * `argv` reached one of two dead ends - a *running* instance logged "No URL to
 * send" and `exitProcess(0)`, and a cold start handed the path to Clikt, which
 * has no bare-path argument (only `boss file <path>`) and failed with a usage
 * error. Double-clicking a file therefore did nothing on either platform, even
 * with the association correctly registered.
 *
 * Pure, with the filesystem injected, so the interesting rule - telling a CLI
 * invocation apart from an OS open request - is testable without a filesystem or
 * a running app.
 */
internal object OsOpenArguments {
    /** URL schemes that are already a link and pass through untouched. */
    private val LINK_PREFIXES = listOf("boss://", "http://", "https://")

    /**
     * The subcommands `createBossCLI` registers.
     *
     * Their presence as the first non-flag argument means the operator is using
     * the CLI, and this object must return nothing so Clikt gets the args
     * intact. Without this test, `boss file /tmp/x.md` would be opened twice:
     * once as an extracted deep link here and once by `BossFileCommand`.
     *
     * Kept in sync with `createBossCLI` by `OsOpenArgumentsTest`, which fails if
     * a subcommand is added there and not here - the failure mode is a
     * double-open, which is easy to miss and hard to attribute.
     */
    internal val CLI_SUBCOMMANDS = setOf("url", "workspace", "file", "folder", "terminal")

    /**
     * Deep links for everything in [args] the OS is asking BOSS to open, or an
     * empty list when [args] is a CLI invocation, a flag-only launch, or empty.
     *
     * @param exists whether a path names a readable regular file. Injected for
     *   tests; the caller passes the real filesystem. A path that does not exist
     *   is deliberately **not** turned into a link: it is far more likely to be a
     *   mistyped flag or a CLI argument this function does not know about than a
     *   file worth opening, and `boss://file` would only log that it was missing.
     */
    fun deepLinksFrom(
        args: Array<String>,
        exists: (String) -> Boolean = { File(it).isFile },
    ): List<String> {
        if (args.isEmpty()) return emptyList()

        // A CLI invocation is claimed by Clikt, whichever argument the
        // subcommand sits at (`boss --flag file x` is still the CLI).
        if (args.any { it in CLI_SUBCOMMANDS }) return emptyList()

        return args.mapNotNull { arg ->
            when {
                LINK_PREFIXES.any { arg.startsWith(it, ignoreCase = true) } -> {
                    arg
                }

                // Flags are never paths. Checked before `exists` because a file
                // called `-n` in the working directory would otherwise turn a
                // flag into an open request.
                arg.startsWith("-") -> {
                    null
                }

                // A local file as a URL. The Linux desktop entry uses `Exec=%U`,
                // which is what lets it accept both links and files, and file
                // managers hand `%U` a `file://` URL rather than a bare path - so
                // without this the association would launch BOSS and open
                // nothing, which is the bug this whole object exists to fix, one
                // layer down.
                arg.startsWith(FILE_URL_PREFIX, ignoreCase = true) -> {
                    pathFromFileUrl(arg)
                        ?.takeIf(exists)
                        ?.let { fileDeepLinkFor(File(it).absolutePath) }
                }

                exists(arg) -> {
                    fileDeepLinkFor(File(arg).absolutePath)
                }

                else -> {
                    null
                }
            }
        }
    }

    private const val FILE_URL_PREFIX = "file://"

    /**
     * The local path inside a `file://` URL, or null when it names another host.
     *
     * `file://host/path` is a remote path this process cannot open, and treating
     * its host as the first path segment would silently open the wrong file. An
     * empty host and `localhost` are both the local machine.
     */
    private fun pathFromFileUrl(url: String): String? =
        try {
            val uri = URI(url)
            val host = uri.host
            when {
                host == null || host.isEmpty() -> {
                    File(uri).absolutePath
                }

                // `File(URI)` rejects any URI with an authority component, so a
                // `file://localhost/tmp/x` from a file manager has to have the
                // redundant host removed before it can be turned into a path.
                // Without this it landed in the IllegalArgumentException below
                // and the file silently did not open.
                host.equals("localhost", ignoreCase = true) -> {
                    File(URI("file", null, uri.path, null, null)).absolutePath
                }

                // `file://somehost/path` is a path on another machine that this
                // process cannot read. Dropping the host and opening the local
                // path of the same name would open the wrong file.
                else -> {
                    null
                }
            }
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: URISyntaxException) {
            null
        }
}
