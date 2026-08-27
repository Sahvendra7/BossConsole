package ai.rever.boss.filetypes

import ai.rever.boss.utils.DefaultHandlerState
import ai.rever.boss.utils.logging.BossLogger
import ai.rever.boss.utils.logging.LogCategory
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Windows file associations for a [FileTypeCategory].
 *
 * **What was missing.** `WindowsDefaultBrowserHandler` wrote
 * `Capabilities\FileAssociations` entries for `.htm` and `.html` pointing at the
 * ProgID `BOSS` - and nothing ever created a ProgID called `BOSS`. A
 * `FileAssociations` value naming a ProgID that does not exist cannot resolve, so
 * BOSS never appeared as a candidate for those extensions in Settings, let alone
 * became the default. This creates a real ProgID per extension, which is what
 * makes the `FileAssociations` entry mean something.
 *
 * **Setting the default is the user's, by design of the OS.** Since Windows 10,
 * writing the `UserChoice` key yourself is blocked (it carries a hash the shell
 * verifies, and a mismatch is reverted). So `register` prepares everything and
 * `openDefaultAppsSettings` takes the user to the one place that can finish it -
 * the same shape the existing browser flow already has.
 */
internal object WindowsFileTypeHandler {
    private val logger = BossLogger.forComponent("WindowsFileTypeHandler")

    private const val PROCESS_TIMEOUT_SECONDS = 15L

    private const val CLASSES_KEY = """HKEY_CURRENT_USER\SOFTWARE\Classes"""

    private const val CAPABILITIES_FILE_ASSOCIATIONS =
        """HKEY_CURRENT_USER\SOFTWARE\Clients\StartMenuInternet\BOSS\Capabilities\FileAssociations"""

    private const val FILE_EXTS_KEY =
        """HKEY_CURRENT_USER\SOFTWARE\Microsoft\Windows\CurrentVersion\Explorer\FileExts"""

    /**
     * ProgID for an extension: `BOSS.md`, `BOSS.kt`.
     *
     * One per extension rather than one per category, because `FileAssociations`
     * and `UserChoice` are both keyed by extension, and a shared ProgID would
     * make "BOSS opens markdown" and "BOSS opens Kotlin" the same switch.
     */
    internal fun progIdFor(extension: String): String = "BOSS.${extension.lowercase()}"

    /**
     * Creates the ProgIDs and capability entries for [category].
     *
     * Idempotent (`reg add /f` overwrites), so it is safe to call on every
     * attempt rather than tracking what was already written.
     */
    fun register(category: FileTypeCategory): Boolean {
        val appPath = applicationPath()
        if (appPath == null) {
            logger.warn(
                LogCategory.SYSTEM,
                "Could not determine the executable path; cannot register file associations",
            )
            return false
        }

        val table = FileTypeCategories.table
        val extensions = table.extensionsFor(category.id)
        if (extensions.isEmpty()) return true

        var allSucceeded = true
        extensions.forEach { extension ->
            if (!registerExtension(extension, category.displayName, appPath)) allSucceeded = false
        }

        logger.info(
            LogCategory.SYSTEM,
            if (allSucceeded) {
                "Registered Windows file associations"
            } else {
                "Registered Windows file associations with failures"
            },
            mapOf("category" to category.id, "extensions" to extensions.size),
        )
        return allSucceeded
    }

    /**
     * Writes the ProgID and capability entries for one extension.
     *
     * Extracted from [register] so that function stays readable: five `reg add`
     * invocations per extension, each with its own reason, is not a loop body.
     *
     * @return true when every command succeeded. A partial result still leaves
     *   the extension better off than before, so nothing is rolled back.
     */
    private fun registerExtension(
        extension: String,
        categoryDisplayName: String,
        appPath: String,
    ): Boolean {
        val progId = progIdFor(extension)
        val description = "$categoryDisplayName (BOSS)"
        val commands =
            listOf(
                listOf("reg", "add", """$CLASSES_KEY\$progId""", "/ve", "/d", description, "/f"),
                listOf("reg", "add", """$CLASSES_KEY\$progId\DefaultIcon""", "/ve", "/d", "$appPath,0", "/f"),
                listOf(
                    "reg",
                    "add",
                    """$CLASSES_KEY\$progId\shell\open\command""",
                    "/ve",
                    "/d",
                    "\"$appPath\" \"%1\"",
                    "/f",
                ),
                // An OpenWithProgids hint puts BOSS in the "Open with" list for the
                // extension even before it is the default, which is how the user
                // finds it in the Settings picker at all.
                listOf(
                    "reg",
                    "add",
                    """$CLASSES_KEY\.$extension\OpenWithProgids""",
                    "/v",
                    progId,
                    "/t",
                    "REG_NONE",
                    "/f",
                ),
                listOf("reg", "add", CAPABILITIES_FILE_ASSOCIATIONS, "/v", ".$extension", "/d", progId, "/f"),
            )

        return commands.fold(true) { acc, command ->
            val ok = runSucceeds(command)
            if (!ok) {
                logger.warn(
                    LogCategory.SYSTEM,
                    "Registry command failed while registering a file association",
                    mapOf("extension" to extension),
                )
            }
            ok && acc
        }
    }

    /**
     * Who owns [category] according to the shell's per-extension `UserChoice`.
     *
     * Reduced the same way macOS is: BOSS only when every extension points at a
     * BOSS ProgID. An extension with no `UserChoice` at all reads as
     * [DefaultHandlerState.Other] with a null id, which is honest - the shell has
     * no recorded user preference, so the association falls back to whatever the
     * machine defaults to.
     */
    fun statusOf(category: FileTypeCategory): DefaultHandlerState {
        val extensions = FileTypeCategories.table.extensionsFor(category.id)
        if (extensions.isEmpty()) return DefaultHandlerState.Other(null)

        val states =
            extensions.map { extension ->
                val progId = userChoiceProgId(extension)
                when {
                    progId == null -> DefaultHandlerState.Other(null)
                    progId.equals(progIdFor(extension), ignoreCase = true) -> DefaultHandlerState.Ours
                    else -> DefaultHandlerState.Other(progId)
                }
            }

        return if (states.all { it.isOurs }) {
            DefaultHandlerState.Ours
        } else {
            states.first { !it.isOurs }
        }
    }

    /** Opens Settings > Default apps, the only place Windows lets the default actually change. */
    fun openDefaultAppsSettings() {
        // Not through Desktop.browse: this is an ms-settings: URI, which the AWT
        // Desktop refuses as an unsupported scheme on some JDK builds.
        runSucceeds(listOf("cmd", "/c", "start", "", "ms-settings:defaultapps"))
    }

    private fun userChoiceProgId(extension: String): String? =
        try {
            val output =
                runCapturing(
                    listOf("reg", "query", """$FILE_EXTS_KEY\.$extension\UserChoice""", "/v", "ProgId"),
                ) ?: return null
            // reg query prints "    ProgId    REG_SZ    BOSS.md"
            output
                .lineSequence()
                .firstOrNull { it.contains("ProgId", ignoreCase = true) }
                ?.trim()
                ?.split(Regex("\\s{2,}"))
                ?.lastOrNull()
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            logger.debug(
                LogCategory.SYSTEM,
                "Could not read the shell's file association",
                mapOf("extension" to extension, "reason" to (e.message ?: "unknown")),
            )
            null
        }

    private fun runSucceeds(command: List<String>): Boolean =
        try {
            val process = ProcessBuilder(command).redirectErrorStream(true).start()
            if (!process.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                logger.warn(LogCategory.SYSTEM, "Registry command timed out", mapOf("command" to command.first()))
                false
            } else {
                process.exitValue() == 0
            }
        } catch (e: Exception) {
            logger.warn(LogCategory.SYSTEM, "Could not run a registry command", error = e)
            false
        }

    private fun runCapturing(command: List<String>): String? =
        try {
            val process = ProcessBuilder(command).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            if (!process.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                null
            } else if (process.exitValue() != 0) {
                // A missing key exits non-zero, which is the common case here and
                // not worth a warning.
                null
            } else {
                output
            }
        } catch (e: Exception) {
            logger.debug(
                LogCategory.SYSTEM,
                "Could not query the registry",
                mapOf("reason" to (e.message ?: "unknown")),
            )
            null
        }

    /**
     * Path to the launcher a shell should run, or null when it cannot be found.
     *
     * Same resolution the browser registration uses, kept separate rather than
     * shared because that one is `private` and lives in a file about URL schemes;
     * both would be better off in one place, which is a follow-up rather than
     * part of this change.
     */
    private fun applicationPath(): String? =
        try {
            val codeSource =
                WindowsFileTypeHandler::class.java.protectionDomain.codeSource.location
                    .toURI()
                    .path
            when {
                codeSource.endsWith(".jar") -> {
                    val launcher = File(codeSource).parentFile?.resolve("BOSS.exe")
                    launcher?.takeIf { it.exists() }?.absolutePath
                }

                else -> {
                    null
                }
            }
        } catch (e: Exception) {
            logger.warn(LogCategory.SYSTEM, "Could not resolve the executable path", error = e)
            null
        }
}
