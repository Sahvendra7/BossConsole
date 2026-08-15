package ai.rever.boss.updater

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The Windows update helper installed the MSI and then simply ended, so "Install
 * update" quit BOSS and nothing came back - while the macOS script always finished
 * with `open` and the Linux one with `nohup .../BOSS`. These pin the relaunch, and
 * the resolution of the launcher path it needs.
 */
class WindowsUpdateRelaunchTest {
    private val msiPath = """C:\Users\Bob\AppData\Local\Temp\boss-updates\BOSS-9.9.9.msi"""
    private val exePath = """C:\Users\Bob\AppData\Local\BOSS\BOSS.exe"""

    private fun script(targetExePath: String?): String {
        val scriptFile =
            UpdateScriptGenerator.generateWindowsUpdateScript(
                msiPath = msiPath,
                appPid = 12345,
                targetExePath = targetExePath,
            )
        try {
            return scriptFile.readText()
        } finally {
            scriptFile.delete()
        }
    }

    // ==================== The script ====================

    @Test
    fun `the Windows script relaunches the installed launcher after a successful install`() {
        val script = script(exePath)

        assertTrue(
            script.contains("""start "" "$exePath""""),
            "The script must launch the installed exe:\n$script",
        )
        assertTrue(
            script.indexOf("msiexec") < script.indexOf("""start "" "$exePath""""),
            "The relaunch must come after the install, not before it",
        )
    }

    /**
     * A major upgrade is only *usually* into the same INSTALLDIR - a per-user install
     * replaced by a per-machine one lands elsewhere - and `start` on a missing path
     * raises a Windows error dialog rather than failing quietly.
     */
    @Test
    fun `the relaunch is guarded by an existence check`() {
        val script = script(exePath)

        assertTrue(
            script.contains("""if exist "$exePath""""),
            "The relaunch should be guarded by if exist:\n$script",
        )
    }

    /**
     * The failure branch hands the user the interactive installer. Relaunching into
     * that would put a BOSS holding its own files open in front of an installer that
     * needs them free.
     */
    @Test
    fun `a failed install does not relaunch`() {
        val script = script(exePath)

        val failureBranch =
            script
                .substringAfter("if not %MSI_RESULT% EQU 0 (")
                .substringBefore(")")

        assertFalse(
            failureBranch.contains(exePath),
            "The failure branch must not relaunch BOSS:\n$failureBranch",
        )
        assertTrue(
            failureBranch.contains("goto cleanup"),
            "The failure branch should skip to cleanup:\n$failureBranch",
        )
    }

    /**
     * `/norestart` makes msiexec report 3010 ("installed, wants a reboot") instead of
     * 0 whenever a file was in use, which is routine when replacing a running app's
     * directory. Branching on `NEQ 0` would call that a failure and open an installer
     * the user does not need.
     */
    @Test
    fun `the reboot-pending exit codes count as success`() {
        val script = script(exePath)

        assertTrue(script.contains("if %MSI_RESULT% EQU 3010 set MSI_RESULT=0"), script)
        assertTrue(script.contains("if %MSI_RESULT% EQU 1641 set MSI_RESULT=0"), script)
    }

    /** An unresolvable launcher must not stop the update - it only loses the relaunch. */
    @Test
    fun `an unknown launcher path still produces an installing script`() {
        val script = script(null)

        assertTrue(script.contains("msiexec /i \"$msiPath\""), "The install must still happen:\n$script")
        assertFalse(script.contains("if exist"), "There is nothing to relaunch:\n$script")
        assertTrue(script.contains("please start BOSS manually"), "The log should say why:\n$script")
    }

    /**
     * `trimIndent` runs *after* interpolation, so splicing a multi-line block into an
     * indented template leaves the block's own lines at column 0, makes the common
     * indent 0, and emits every other line still carrying the Kotlin template's
     * indent - including the `:labels` `goto` depends on.
     */
    @Test
    fun `directives and labels sit at column 0`() {
        listOf(script(exePath), script(null)).forEach { script ->
            assertTrue(script.startsWith("@echo off"), "Script should open with an unindented @echo off:\n$script")
            assertTrue(script.lines().contains(":waitloop"), "The waitloop label must not be indented:\n$script")
            assertTrue(script.lines().contains(":cleanup"), "The cleanup label must not be indented:\n$script")
        }
    }

    /**
     * cmd.exe parses a batch file by seeking byte offsets that assume CRLF, so an
     * LF-only file breaks `goto` into a label - and breaks it by jumping somewhere
     * wrong rather than by failing. The failure branch depends on that `goto`.
     */
    @Test
    fun `the batch file is written with CRLF line endings`() {
        val script = script(exePath)

        assertFalse(
            script.contains(Regex("(?<!\r)\n")),
            "Every newline in a .bat must be a CRLF",
        )
    }

    /**
     * `launchScript` starts the helper with ProcessBuilder, so its stdin is a pipe, and
     * `timeout` refuses to run at all under redirected input - it exited immediately
     * with "ERROR: Input redirection is not supported", which turned the quit poll into
     * a hot loop and wrote three errors into the one log anybody diagnosing a failed
     * update would read.
     */
    @Test
    fun `the waits survive the redirected stdin the script is launched with`() {
        val script = script(exePath)

        assertFalse(script.contains("timeout /t"), "timeout cannot run under redirected stdin:\n$script")
        assertTrue(script.contains("ping -n 2 127.0.0.1 >NUL"), "The quit poll needs a working sleep:\n$script")
    }

    // ==================== Resolving the launcher ====================
    //
    // Paths are built with File so the parent walk exercises the *host* separator:
    // a literal `C:\...\app\composeApp.jar` has no parent at all on a POSIX CI leg,
    // which would make these pass for the wrong reason there.

    private val installDir = File(File(System.getProperty("java.io.tmpdir")), "BOSS")
    private val installedLauncher = File(installDir, "BOSS.exe").path
    private val runningJar = File(File(installDir, "app"), "composeApp.jar").path

    @Test
    fun `jpackage app-path wins when it points at something real`() {
        val resolved =
            windowsLauncherPathFor(
                jpackageAppPath = exePath,
                codeSourcePath = runningJar,
                exists = { it == exePath || it == installedLauncher },
            )

        assertEquals(exePath, resolved, "The property jpackage set should be preferred over any inference")
    }

    /**
     * jpackage sets the property on every packaged launch, but an installation whose
     * directory moved leaves it naming a path that is gone. Falling through to the
     * layout walk is what keeps the relaunch working there.
     */
    @Test
    fun `a stale jpackage app-path falls through to the install layout`() {
        val resolved =
            windowsLauncherPathFor(
                jpackageAppPath = File(File(File(System.getProperty("java.io.tmpdir")), "Old BOSS"), "BOSS.exe").path,
                codeSourcePath = runningJar,
                exists = { it == installedLauncher },
            )

        assertEquals(installedLauncher, resolved)
    }

    @Test
    fun `a blank jpackage app-path is ignored`() {
        val resolved =
            windowsLauncherPathFor(
                jpackageAppPath = "",
                codeSourcePath = runningJar,
                exists = { it == installedLauncher },
            )

        assertEquals(installedLauncher, resolved)
    }

    /** A development run has no launcher anywhere above it, and must resolve to null. */
    @Test
    fun `a dev run resolves to null rather than guessing`() {
        assertNull(
            windowsLauncherPathFor(
                jpackageAppPath = null,
                codeSourcePath = runningJar,
                exists = { false },
            ),
        )
    }

    @Test
    fun `an absent code source resolves to null`() {
        assertNull(
            windowsLauncherPathFor(
                jpackageAppPath = null,
                codeSourcePath = null,
                exists = { true },
            ),
        )
    }
}
