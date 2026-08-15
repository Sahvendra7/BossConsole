package ai.rever.boss.window

import java.io.File
import kotlin.test.Test
import kotlin.test.fail

/**
 * Asserts every file that opens a top-level window also brands it.
 *
 * A Compose `Window` given no `icon`, or a bare `JFrame`, leaves `Frame.iconImages` empty, and
 * Windows then draws the JDK's default Java icon in the title bar, the taskbar button and the
 * Alt-Tab card. macOS reads the `.app` bundle and Linux the `.desktop` file, so neither shows it -
 * which is exactly why this needs a test rather than review vigilance. The omission is invisible in
 * a diff (an absent argument), invisible on the two platforms most of this is developed on, and the
 * window works perfectly in every other respect. Nine windows shipped that way, for years, before
 * anyone noticed the Settings window was wearing a coffee cup.
 *
 * Deliberately a text check, like [ai.rever.boss.components.overlays.NoRawDialogConventionTest]
 * next door. It cannot see a fully-qualified call or a window opened through a helper, but it
 * catches the shape every real call site in this repo takes and it costs nothing.
 */
class WindowIconConventionTest {
    /**
     * The call shapes that open a window with an icon surface.
     *
     * `\b` before each name matters: without it `Window\(` also matches `positionInWindow()` and
     * `boundsInWindow()`, which are on half the layout code in this repo. It is also why
     * `DialogWindow` needs its own entry - the `g` before its `W` is a word character, so the
     * `Window` pattern never sees it.
     */
    private val windowOpeners =
        listOf(
            Regex("""\bWindow\("""),
            Regex("""\bDialogWindow\("""),
            Regex("""\bsingleWindowApplication\("""),
            Regex("""\bJFrame\("""),
        )

    /** Either shape of the shared icon: the Compose painter, or the AWT image list. */
    private val brandingReference = Regex("""\bbossWindowIcon\b|\bBossWindowIcon\b""")

    @Test
    fun `every file that opens a window references the shared BOSS icon`() {
        val root = repoRoot()
        val roots =
            listOf(File(root, "composeApp/src"), File(root, "plugin-platform"))
                .filter { it.isDirectory }
        check(roots.isNotEmpty()) { "no source roots found under $root" }

        val offenders =
            roots
                .flatMap { it.walkTopDown().filter { f -> f.isFile && f.extension == "kt" } }
                .filter { file ->
                    val text = file.readText()
                    windowOpeners.any { it.containsMatchIn(text) } && !brandingReference.containsMatchIn(text)
                }.map { it.relativeTo(root).path }
                .sorted()

        if (offenders.isNotEmpty()) {
            fail(
                "These files open a top-level window without referencing the shared BOSS icon, so on " +
                    "Windows it will show the default Java icon in the title bar, taskbar and Alt-Tab. " +
                    "Pass `icon = bossWindowIcon()` to a Compose Window/DialogWindow, or assign " +
                    "`frame.iconImages = BossWindowIcon.images` to a raw JFrame " +
                    "(ai.rever.boss.window.WindowIcon):\n  " + offenders.joinToString("\n  "),
            )
        }
    }

    /**
     * Guards the guard: if the patterns above ever stop matching anything, the test above passes
     * vacuously and would keep passing while every window in the app went unbranded.
     */
    @Test
    fun `the window-opening patterns still match the known call sites`() {
        val root = repoRoot()
        val matched =
            File(root, "composeApp/src")
                .walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .count { file -> windowOpeners.any { it.containsMatchIn(file.readText()) } }

        // Fifteen at the time of writing (ten Compose windows, five raw frames, spread over
        // fourteen files, plus WindowIcon.kt's own KDoc). A floor, not an equality: adding a window
        // must not fail this test, only the one above.
        if (matched < 10) {
            fail("expected the window-opening patterns to match many files, matched only $matched")
        }
    }

    /** Walks up from the test's working directory to the checkout root. */
    private fun repoRoot(): File {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            if (File(dir, "composeApp").isDirectory && File(dir, "version.properties").isFile) return dir
            dir = dir.parentFile
        }
        fail("could not locate the repository root from ${File(".").absolutePath}")
    }
}
