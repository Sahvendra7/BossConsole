package ai.rever.boss.utils

import ai.rever.boss.utils.logging.BossLogger
import ai.rever.boss.utils.logging.LogCategory
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

private val logger = BossLogger.forComponent("WindowsProtocolHandler")
private val isWindows = System.getProperty("os.name").lowercase().contains("windows")

/** Root of the per-user protocol registration this file owns. */
private const val PROTOCOL_KEY = """HKEY_CURRENT_USER\Software\Classes\boss"""

/** The `shell\open\command` value under it — where the launch command lives. */
private const val PROTOCOL_COMMAND_KEY = PROTOCOL_KEY + """\shell\open\command"""

/** A wedged `reg.exe` must not hang the uninstall hook. */
private const val REG_TIMEOUT_SECONDS = 5L

/** `%VARIABLE%` reference left unexpanded in a REG_EXPAND_SZ value. */
private val UNEXPANDED_VARIABLE = Regex("""%[A-Za-z_][A-Za-z0-9_()]*%""")

/**
 * Windows-specific protocol handler for registering URL schemes.
 *
 * Registration happens at runtime rather than in the MSI (see
 * [docs/WINDOWS_DEEP_LINK_SETUP.md]), which means an uninstall leaves the `boss:`
 * handler in the registry pointing at a deleted executable. [unregisterProtocol] is the
 * cleanup hook for that — reachable as `BOSS.exe --unregister-protocol` (see
 * [unregisterProtocolExitCode]) so an uninstall action, or support instructions, can
 * call it.
 */
object WindowsProtocolHandler {
    /**
     * Outcome of [unregisterProtocol]. Distinguishes "there is nothing left to clean"
     * from "I deliberately did not touch it" — deleting a registration we merely could
     * not parse would break a working install.
     */
    enum class UnregisterOutcome {
        /** The registration was ours, pointed at a deleted exe, or had no command value — and was removed. */
        REMOVED,

        /** Nothing was registered. */
        ABSENT,

        /** Not a Windows host — there is no registry registration to remove. */
        NOT_APPLICABLE,

        /** Registered to a different, still present BOSS installation. Left alone. */
        OTHER_INSTALL,

        /** Registered to a command this code cannot parse (e.g. installer-authored, unquoted). Left alone. */
        UNREADABLE,

        /** `reg delete` itself failed. */
        FAILED,
    }

    /**
     * Register the boss:// protocol in Windows Registry
     * This should be called on first launch or during installation
     *
     * Production-safe: Only registers if needed, validates existing registrations,
     * and prevents conflicts with other BOSS installations
     */
    fun registerProtocol() {
        if (!isWindows) return

        try {
            // 1. Get application path
            val appPath = getApplicationPath()
            if (appPath.isNullOrEmpty()) {
                // Development mode or unable to determine path
                return
            }

            // 2. Check current registry state
            val currentCommand = getCurrentRegistryCommand()

            // 3. Determine if registration is needed
            val needsRegistration =
                when {
                    currentCommand == null -> {
                        logger.info(LogCategory.SYSTEM, "Protocol not registered. Registering...")
                        true
                    }

                    !commandPointsToValidExecutable(currentCommand) -> {
                        logger.info(
                            LogCategory.SYSTEM,
                            "Protocol points to invalid path, re-registering",
                            mapOf("command" to maskUserPath(currentCommand)),
                        )
                        true
                    }

                    !currentCommand.contains(appPath, ignoreCase = true) -> {
                        // SAFETY CHECK: Only re-register if current path doesn't exist
                        val currentExePath = extractExecutablePath(currentCommand)
                        if (currentExePath != null && File(currentExePath).exists()) {
                            logger.info(
                                LogCategory.SYSTEM,
                                "Protocol already registered to different valid BOSS installation, skipping",
                                mapOf("path" to maskUserPath(currentExePath)),
                            )
                            false
                        } else {
                            logger.info(
                                LogCategory.SYSTEM,
                                "Protocol points to non-existent path, re-registering",
                                mapOf("command" to maskUserPath(currentCommand)),
                            )
                            true
                        }
                    }

                    else -> {
                        logger.debug(LogCategory.SYSTEM, "Protocol already correctly registered")
                        false
                    }
                }

            // 4. Perform registration if needed
            if (needsRegistration) {
                performRegistration(appPath)
            }
        } catch (e: Exception) {
            logger.error(LogCategory.SYSTEM, "Failed to register Windows protocol", error = e)
        }
    }

    /**
     * Perform the actual registry writes
     */
    private fun performRegistration(appPath: String) {
        logger.info(
            LogCategory.SYSTEM,
            "Starting BOSS protocol registration",
            mapOf("appPath" to maskUserPath(appPath)),
        )

        val commands =
            listOf(
                // Create protocol key
                """reg add "$PROTOCOL_KEY" /ve /d "URL:BOSS Protocol" /f""",
                """reg add "$PROTOCOL_KEY" /v "URL Protocol" /d "" /f""",
                // Set icon
                """reg add "$PROTOCOL_KEY\DefaultIcon" /ve /d "$appPath,0" /f""",
                // Set command to open the app with URL
                """reg add "$PROTOCOL_COMMAND_KEY" /ve /d "\"$appPath\" \"%1\"" /f""",
            )

        var successCount = 0
        commands.forEach { command ->
            try {
                val process = Runtime.getRuntime().exec(command)
                val exitCode = process.waitFor()
                if (exitCode == 0) {
                    successCount++
                } else {
                    logger.warn(LogCategory.SYSTEM, "Registry command failed", mapOf("exitCode" to exitCode))
                }
            } catch (e: Exception) {
                logger.error(LogCategory.SYSTEM, "Failed to execute registry command", error = e)
            }
        }

        if (successCount == commands.size) {
            logger.info(LogCategory.SYSTEM, "Protocol registration successful")
        } else {
            logger.warn(
                LogCategory.SYSTEM,
                "Protocol registration partial",
                mapOf(
                    "successCount" to successCount,
                    "totalCommands" to commands.size,
                ),
            )
        }
    }

    /**
     * Remove the `boss:` protocol registration.
     *
     * Because registration happens at runtime, an uninstall would otherwise leave a
     * handler pointing at a deleted `BOSS.exe`, and later `boss://` links fail with
     * Windows' generic "no app associated" error. Invoke via
     * `BOSS.exe --unregister-protocol` from an uninstall action or by hand.
     *
     * Safety policy lives in [classifyProtocolCleanup] and [parseCommandState] (both pure
     * and unit-tested): the key is deleted only when it points at *this* installation, at an
     * executable that no longer exists, or carries no command value at all — a partial
     * registration this code produced, identified by reg's own "unable to find" output
     * rather than by an exit code (`reg.exe` exits 1 for access-denied too) or by "the value
     * did not parse". Anything else is left alone: a registration owned by a different live
     * install, a command in a form this code does not read (`REG_EXPAND_SZ`, unquoted —
     * what a WiX/MSI-authored registration looks like), and any registry read that failed
     * for any reason. Failing to *read* must never escalate to *deleting*.
     *
     * Note this protection is delete-only. [registerProtocol] still overwrites an
     * unparseable command (`commandPointsToValidExecutable` fails closed), so an
     * installer-authored registration survives cleanup but not a re-registration.
     */
    fun unregisterProtocol(): UnregisterOutcome {
        if (!isWindows) return UnregisterOutcome.NOT_APPLICABLE

        val command = queryProtocolCommand()
        val decision =
            classifyProtocolCleanup(
                rootPresent = queryRootKeyPresent(),
                command = command,
                appPath = getApplicationPath(),
                exeExists = { File(it).exists() },
            )
        logCleanupDecision(decision, command)
        return when (decision) {
            is CleanupDecision.Report -> decision.outcome
            CleanupDecision.Delete -> deleteProtocolKey()
        }
    }

    /**
     * CLI mapping for `--unregister-protocol`: 0 when nothing is left to clean up
     * (removed / nothing registered / not a Windows host), 1 when the registration was
     * deliberately left in place, 2 when the delete itself failed.
     */
    fun unregisterProtocolExitCode(): Int {
        val outcome = unregisterProtocol()
        logger.info(LogCategory.SYSTEM, "boss:// protocol cleanup finished", mapOf("outcome" to outcome.name))
        return exitCodeFor(outcome)
    }

    /**
     * Check if the protocol is already registered
     */
    fun isProtocolRegistered(): Boolean {
        if (!isWindows) return false

        return try {
            val process = Runtime.getRuntime().exec("""reg query "$PROTOCOL_KEY" """)
            process.waitFor()
            process.exitValue() == 0
        } catch (e: Exception) {
            logger.debug(
                LogCategory.SYSTEM,
                "reg query failed - treating boss:// protocol as unregistered",
                mapOf("error" to e.toString()),
            )
            false
        }
    }

    /**
     * Get the path to the running application
     */
    private fun getApplicationPath(): String? {
        return try {
            // Priority 1: Check for jpackage installation (MSI/EXE)
            // This is the most reliable method for production deployments
            val jpackagePath = System.getProperty("jpackage.app-path")
            if (!jpackagePath.isNullOrEmpty()) {
                val file = File(jpackagePath)
                if (file.exists()) {
                    logger.debug(LogCategory.SYSTEM, "Detected jpackage installation", mapOf("path" to jpackagePath))
                    return jpackagePath
                } else {
                    logger.warn(LogCategory.SYSTEM, "jpackage.app-path set but file doesn't exist", mapOf("path" to jpackagePath))
                }
            }

            // Priority 2: Try to get the path from the running JAR/EXE
            val jarPath =
                WindowsProtocolHandler::class.java.protectionDomain.codeSource.location
                    .toURI()
                    .path

            // Convert to Windows path format and handle different packaging scenarios
            when {
                jarPath.endsWith(".jar") -> {
                    // Running from JAR - look for launcher executable
                    val jarFile = File(jarPath)
                    val launcherPath = jarFile.parentFile.resolve("BOSS.exe")
                    if (launcherPath.exists()) {
                        launcherPath.absolutePath
                    } else {
                        // Cannot use "javaw.exe -jar" as registry needs executable path
                        logger.warn(LogCategory.SYSTEM, "Running from JAR without launcher executable")
                        null
                    }
                }

                jarPath.contains("BOSS.exe") -> {
                    // Already an executable
                    File(jarPath).absolutePath
                }

                else -> {
                    // Development environment - return null to skip registration
                    logger.debug(LogCategory.SYSTEM, "Running in development mode - deep links require MSI installation")
                    null
                }
            }
        } catch (e: Exception) {
            logger.error(LogCategory.SYSTEM, "Error determining application path", error = e)
            null
        }
    }

    /**
     * Parse command line arguments to extract deep link URL
     */
    fun extractDeepLinkFromArgs(args: Array<String>): String? {
        // Windows passes the URL as the first argument when launched via protocol
        return args.firstOrNull { it.startsWith("boss://") }
    }

    /**
     * Get the current command registered in the Windows registry for boss:// protocol,
     * or null when it is absent or unreadable. Callers that must tell those two apart
     * (cleanup does — see [classifyProtocolCleanup]) use [queryProtocolCommand] instead.
     */
    private fun getCurrentRegistryCommand(): String? = (queryProtocolCommand() as? CommandState.Present)?.command

    /**
     * Check if the command points to a valid executable file
     */
    private fun commandPointsToValidExecutable(command: String): Boolean {
        val exePath = extractExecutablePath(command) ?: return false
        return File(exePath).exists()
    }
}

/** Result of a `reg.exe` invocation. */
private class RegResult(
    val exitCode: Int,
    val output: String,
)

/**
 * Run `reg.exe` with a timeout and narrow error handling, returning null when the command
 * could not be run to completion.
 *
 * Centralized because both cleanup paths need identical guarantees: a wedged `reg.exe` must
 * not hang the `--unregister-protocol` hook an uninstaller is waiting on, and a failure to
 * *run* a query must stay distinguishable from a definitive answer.
 */
private fun runReg(vararg args: String): RegResult? =
    try {
        val process =
            ProcessBuilder(listOf("reg", *args))
                .redirectErrorStream(true)
                .start()
        val output = process.inputStream.bufferedReader().readText()
        if (process.waitFor(REG_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            RegResult(process.exitValue(), output)
        } else {
            process.destroyForcibly()
            logger.warn(
                LogCategory.SYSTEM,
                "reg.exe timed out",
                mapOf("args" to args.joinToString(" "), "timeoutSeconds" to REG_TIMEOUT_SECONDS),
            )
            null
        }
    } catch (e: IOException) {
        logger.debug(LogCategory.SYSTEM, "reg.exe could not be run", mapOf("error" to e.toString()))
        null
    } catch (e: SecurityException) {
        logger.debug(LogCategory.SYSTEM, "reg.exe was not permitted to run", mapOf("error" to e.toString()))
        null
    } catch (e: UnsupportedOperationException) {
        logger.debug(LogCategory.SYSTEM, "reg.exe could not be spawned", mapOf("error" to e.toString()))
        null
    } catch (e: InterruptedException) {
        Thread.currentThread().interrupt()
        logger.debug(LogCategory.SYSTEM, "Interrupted running reg.exe", mapOf("error" to e.toString()))
        null
    }

/**
 * `reg delete` the whole `boss` key tree. File-private rather than an object member so
 * [WindowsProtocolHandler] stays within its function budget; it is only ever reached from
 * [WindowsProtocolHandler.unregisterProtocol], which decides whether deleting is safe.
 */
private fun deleteProtocolKey(): WindowsProtocolHandler.UnregisterOutcome {
    val result = runReg("delete", PROTOCOL_KEY, "/f")
    return when {
        result == null -> {
            logger.warn(
                LogCategory.SYSTEM,
                "Failed to remove boss:// protocol registration",
                mapOf("key" to PROTOCOL_KEY),
            )
            WindowsProtocolHandler.UnregisterOutcome.FAILED
        }

        result.exitCode == 0 -> {
            logger.info(LogCategory.SYSTEM, "Removed boss:// protocol registration", mapOf("key" to PROTOCOL_KEY))
            WindowsProtocolHandler.UnregisterOutcome.REMOVED
        }

        else -> {
            logger.warn(
                LogCategory.SYSTEM,
                "Failed to remove boss:// protocol registration",
                mapOf("key" to PROTOCOL_KEY, "output" to result.output.trim()),
            )
            WindowsProtocolHandler.UnregisterOutcome.FAILED
        }
    }
}

/**
 * What the registered `shell\open\command` currently holds.
 *
 * The distinction matters for cleanup: "the value is not there" is a partial registration
 * this code produced and may delete, while "the value is there but I cannot read it" must be
 * left alone — it can be a perfectly working registration written by someone else (an
 * installer authoring a `REG_EXPAND_SZ` or unquoted command), and a failure to *read* the
 * registry must never escalate to *deleting* it.
 */
internal sealed interface CommandState {
    /** `reg query` positively reports the key/value as absent (not merely a failed query). */
    object Missing : CommandState

    /** Present, but not readable here: unexpected value type, no value text, or the query failed. */
    object Unreadable : CommandState

    /** Present and read. */
    data class Present(
        val command: String,
    ) : CommandState
}

/**
 * Read the registered `shell\open\command`, keeping the absent/unreadable distinction.
 * Process I/O only — the decision lives in [parseCommandState] so it can be tested.
 */
private fun queryProtocolCommand(): CommandState {
    val result = runReg("query", PROTOCOL_COMMAND_KEY, "/ve") ?: return CommandState.Unreadable
    return parseCommandState(result.exitCode, result.output)
}

/**
 * Whether the protocol root key exists, or null when the query could not be run.
 *
 * Cleanup needs that third state: a read that failed must not be reported as "definitely
 * absent", which would tell an uninstaller cleanup had succeeded when nothing was checked.
 */
private fun queryRootKeyPresent(): Boolean? = runReg("query", PROTOCOL_KEY)?.let { it.exitCode == 0 }

/**
 * Classify the result of `reg query <key> /ve`.
 *
 * Ordered so that failing closed is the default, because the stakes are asymmetric:
 * leaving an orphan key behind is cosmetic, deleting a live third-party registration is
 * not.
 *  * A value that parses is [CommandState.Present], whatever the exit code says.
 *  * [CommandState.Missing] — the only state that licenses a delete — additionally
 *    requires reg's own "unable to find" text. `reg.exe` exits 1 for absence *and* for
 *    access-denied, policy/EDR blocks and truncated output, so the exit code alone cannot
 *    distinguish "not there" from "could not read".
 *  * Everything else, including a localized not-found message on a non-English Windows, is
 *    [CommandState.Unreadable]: cleanup then reports rather than deletes.
 *
 * `REG_EXPAND_SZ` is matched as well as `REG_SZ` — an installer-authored command such as
 * `"%LOCALAPPDATA%\BOSS\BOSS.exe" "%1"` is ordinary and must not read as missing.
 */
internal fun parseCommandState(
    exitCode: Int,
    output: String,
): CommandState {
    // Parse: "    (Default)    REG_SZ    C:\Path\To\BOSS.exe "%1""
    val command =
        // The default-value name is localized ("(Default)", "(Standard)", "(Par défaut)"),
        // so match any parenthesized name; the value type is not localized. `find` returns
        // the first match and the type always precedes the value, so a command whose own
        // text contains "REG_SZ" cannot be picked up instead.
        Regex("""^\s+\(.+?\)\s+REG_(?:EXPAND_)?SZ\s+(.+)$""", RegexOption.MULTILINE)
            .find(output)
            ?.groupValues
            ?.get(1)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    return when {
        command != null -> CommandState.Present(command)
        exitCode != 0 && output.contains("unable to find", ignoreCase = true) -> CommandState.Missing
        else -> CommandState.Unreadable
    }
}

/**
 * Extract executable path from registry command string.
 * Example: `"C:\Path\To\BOSS.exe" "%1"` -> `C:\Path\To\BOSS.exe`
 *
 * Only the quoted form is recognized; an unquoted command yields null, which callers must
 * treat as "unreadable", never as "stale".
 */
internal fun extractExecutablePath(command: String): String? {
    val match = Regex("""^"([^"]+)"""").find(command)
    return match?.groupValues?.get(1)
}

/**
 * What to do about the current `boss:` registration.
 *
 * A dedicated type rather than a nullable outcome: [Delete] destroys a registry key tree,
 * and that branch should name itself at every call site and in every test.
 */
internal sealed interface CleanupDecision {
    /** The registration is ours, dead, or an orphan this code produced: remove it. */
    object Delete : CleanupDecision

    /** Leave the registry alone and report this outcome. */
    data class Report(
        val outcome: WindowsProtocolHandler.UnregisterOutcome,
    ) : CleanupDecision
}

/**
 * Decide whether the `boss:` registration may be deleted — the whole safety policy of
 * [WindowsProtocolHandler.unregisterProtocol], kept pure so every branch is testable off
 * Windows (see `WindowsProtocolCleanupTest`). Only the `reg delete` itself needs a real
 * registry.
 *
 * @param rootPresent whether the protocol root key exists, or null when that could not be
 *   determined (a failed read is not evidence of absence)
 * @param command state of its `shell\open\command` value
 * @param appPath this installation's executable, or null when it cannot be determined
 *   (development runs)
 * @param exeExists existence check for the registered executable
 */
internal fun classifyProtocolCleanup(
    rootPresent: Boolean?,
    command: CommandState,
    appPath: String?,
    exeExists: (String) -> Boolean,
): CleanupDecision {
    val registeredExe = (command as? CommandState.Present)?.command?.let { extractExecutablePath(it) }
    return when {
        // A failed read of the root key is not evidence of absence.
        rootPresent == null -> {
            CleanupDecision.Report(WindowsProtocolHandler.UnregisterOutcome.UNREADABLE)
        }

        !rootPresent -> {
            CleanupDecision.Report(WindowsProtocolHandler.UnregisterOutcome.ABSENT)
        }

        // Root key exists and reg positively reports no command value: a partial
        // registration this code produced (performRegistration issues four independent
        // `reg add`s and only logs when some fail). registerProtocol already self-heals it
        // by re-registering, so cleanup owns it too rather than orphaning keys forever.
        command is CommandState.Missing -> {
            CleanupDecision.Delete
        }

        // Present but not readable here. Never delete what we cannot parse.
        registeredExe == null -> {
            CleanupDecision.Report(WindowsProtocolHandler.UnregisterOutcome.UNREADABLE)
        }

        // A REG_EXPAND_SZ path still contains %VARIABLE% references and nothing here expands
        // them, so exeExists() is always false for one — which would classify a perfectly
        // live installer-authored registration as dead and delete it. Not evaluatable means
        // unreadable.
        UNEXPANDED_VARIABLE.containsMatchIn(registeredExe) -> {
            CleanupDecision.Report(WindowsProtocolHandler.UnregisterOutcome.UNREADABLE)
        }

        // Ours, or pointing at an executable that is gone: safe to remove.
        registeredExe.equals(appPath, ignoreCase = true) || !exeExists(registeredExe) -> {
            CleanupDecision.Delete
        }

        else -> {
            CleanupDecision.Report(WindowsProtocolHandler.UnregisterOutcome.OTHER_INSTALL)
        }
    }
}

/**
 * Log the cleanup decision with the context a support case needs — "why did
 * `--unregister-protocol` refuse?" has to be answerable from the log, since the CLI hook
 * has no console output (BOSS.exe is GUI-subsystem).
 *
 * Registered paths embed a Windows username, so they are masked (AGENTS.md).
 */
private fun logCleanupDecision(
    decision: CleanupDecision,
    command: CommandState,
) {
    val registered = (command as? CommandState.Present)?.command
    when {
        decision is CleanupDecision.Report && decision.outcome == WindowsProtocolHandler.UnregisterOutcome.ABSENT -> {
            logger.debug(LogCategory.SYSTEM, "boss:// protocol is not registered - nothing to clean up")
        }

        decision is CleanupDecision.Report -> {
            logger.info(
                LogCategory.SYSTEM,
                "boss:// protocol left registered",
                mapOf(
                    "key" to PROTOCOL_KEY,
                    "outcome" to decision.outcome.name,
                    "command" to maskUserPath(registered),
                ),
            )
        }

        else -> {
            logger.info(
                LogCategory.SYSTEM,
                "boss:// protocol will be removed",
                mapOf("key" to PROTOCOL_KEY, "command" to maskUserPath(registered)),
            )
        }
    }
}

/**
 * Strip the Windows account name out of a path before logging it.
 *
 * Covers `C:\Users\<name>\…`, the legacy `C:\Documents and Settings\<name>\…`, and an
 * unexpanded `%USERPROFILE%`-rooted path. AGENTS.md requires sanitizing user data in logs and
 * `LogSanitizer` has no path masker yet — a shared one belongs there (it lives in the
 * plugin-facing plugin-logging module), which is why this is local for now.
 */
private fun maskUserPath(path: String?): String {
    if (path == null) return "(none)"
    return path
        .replace(Regex("""(?i)(\\Users\\)([^\\"]+)"""), "$1***")
        .replace(Regex("""(?i)(\\Documents and Settings\\)([^\\"]+)"""), "$1***")
        .replace(Regex("""(?i)%USERPROFILE%"""), "%USERPROFILE%")
}

/**
 * Exit-code contract for `BOSS.exe --unregister-protocol`, which an uninstall action reads:
 * 0 nothing left to clean, 1 the registration was deliberately left in place, 2 the delete
 * itself failed. Extracted so the contract is unit-tested rather than inspected.
 */
internal fun exitCodeFor(outcome: WindowsProtocolHandler.UnregisterOutcome): Int =
    when (outcome) {
        WindowsProtocolHandler.UnregisterOutcome.REMOVED,
        WindowsProtocolHandler.UnregisterOutcome.ABSENT,
        WindowsProtocolHandler.UnregisterOutcome.NOT_APPLICABLE,
        -> 0

        WindowsProtocolHandler.UnregisterOutcome.OTHER_INSTALL,
        WindowsProtocolHandler.UnregisterOutcome.UNREADABLE,
        -> 1

        WindowsProtocolHandler.UnregisterOutcome.FAILED -> 2
    }
