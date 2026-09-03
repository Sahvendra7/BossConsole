package ai.rever.boss.plugin.browser

import java.io.File
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * No blocking renderer round trip may be made from the EDT.
 *
 * `Frame.executeJavaScript` and `JsObject.putProperty` block until the *renderer* answers, and a
 * renderer has every right not to: one parked on a modal `window.prompt` cannot run script until the
 * dialog is answered, and one being swapped out mid-redirect never answers at all. Nothing can
 * interrupt the wait - `executeJavaScript` has no suspension point, so a `withTimeoutOrNull` placed
 * around it is not a bound.
 *
 * Made from `Dispatchers.Main` that is not a slow call, it is a dead application: the EDT parks
 * forever, AppKit's main thread parks behind it, and the macOS menu bar goes with the window. Force
 * quit is the only exit. It happened twice in one morning on 9.5.7 - once through the plugin-facing
 * `executeJavaScript`, once through the co-browse page-event injection's `putProperty` - and the
 * user-visible symptom is indistinguishable from a hung machine.
 *
 * The fix is the pattern the rest of [BrowserHandleImpl] already uses in three places: make the
 * blocking call on a dedicated single daemon thread and, where a result is needed, bound the *wait*
 * from a different one. A wedged renderer then costs one parked thread per tab.
 *
 * A source check rather than a behaviour one, for the reason [InjectJsCallbackOwnershipTest] gives:
 * the failure mode is a *new call site*, and observing it at runtime needs a real Chromium and a
 * page that genuinely stops answering. Reading the source catches it as it is written.
 *
 * PR #268 fixed this same hazard for JxBrowser's RPC thread and left the EDT sites in place, which
 * is precisely the kind of half-fix a guard exists to stop. If a Main-thread round trip is ever
 * genuinely wanted, argue with this test - do not route around it.
 */
class BrowserMainThreadRoundTripTest {
    /** Blocking calls into the renderer. Neither can be interrupted once entered. */
    private val rendererRoundTrips = listOf("executeJavaScript", "putProperty")

    private fun repoRoot(): File? =
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "composeApp/build.gradle.kts").isFile }

    private fun browserSources(root: File): List<File> =
        File(root, "composeApp/src/desktopMain/kotlin/ai/rever/boss/plugin/browser")
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .toList()

    /**
     * Comments and string literals removed, whitespace collapsed.
     *
     * Comments go for the reason [InjectJsCallbackOwnershipTest] gives - this KDoc names both the
     * dispatcher and the call, and matching raw text would flag the explanation as the offence.
     * String literals go because the brace scan below counts braces: an injected script, or a string
     * template's own braces, are not block structure, and one of those inside a scanned block would
     * end it early and make this guard quietly stop guarding.
     */
    private fun codeOf(file: File): String =
        stripStrings(
            file
                .readText()
                .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), " ")
                .lines()
                // Not substringBefore("//"): that cuts at the "//" inside "https://…" too, which is
                // a false negative in a guard whose whole value is catching one occurrence.
                .joinToString(" ") { line -> line.split(Regex("(?<!:)//")).first() },
        ).replace(Regex("\\s+"), " ")

    /**
     * Every string literal replaced by an empty one, scanned by hand rather than matched.
     *
     * The obvious regex - `"(?:\\.|[^"\\])*"` - is a `(?:A|B)*` loop, which java.util.regex walks
     * recursively: against the multi-kilobyte injected scripts in this package it overflows the
     * stack, and a guard that dies is worse than one that is wrong. A single pass costs nothing and
     * handles the raw `"""…"""` strings those scripts are written as, which the regex did not.
     */
    private fun stripStrings(code: String): String {
        val out = StringBuilder(code.length)
        var i = 0
        while (i < code.length) {
            i =
                when {
                    code.startsWith("\"\"\"", i) -> skipRaw(code, i, out)
                    code[i] == '"' -> skipQuoted(code, i, out)
                    else -> copyChar(code, i, out)
                }
        }
        return out.toString()
    }

    /** Past the closing `"""`, or to the end if it is unterminated. */
    private fun skipRaw(
        code: String,
        start: Int,
        out: StringBuilder,
    ): Int {
        out.append("\"\"")
        val end = code.indexOf("\"\"\"", start + 3)
        return if (end < 0) code.length else end + 3
    }

    /** Past the closing `"`, honouring backslash escapes. */
    private fun skipQuoted(
        code: String,
        start: Int,
        out: StringBuilder,
    ): Int {
        out.append("\"\"")
        var j = start + 1
        while (j < code.length && code[j] != '"') j += if (code[j] == '\\') 2 else 1
        return if (j >= code.length) code.length else j + 1
    }

    private fun copyChar(
        code: String,
        at: Int,
        out: StringBuilder,
    ): Int {
        out.append(code[at])
        return at + 1
    }

    /** The source of every `withContext(Dispatchers.Main) { … }` block in [code], braces balanced. */
    private fun mainThreadBlocks(code: String): List<String> {
        val opener = Regex("""withContext\( ?Dispatchers\.Main ?\) ?\{""")
        return opener
            .findAll(code)
            .map { match ->
                var depth = 0
                var end = match.range.last
                for (i in match.range.last until code.length) {
                    when (code[i]) {
                        '{' -> {
                            depth++
                        }

                        '}' -> {
                            depth--
                            if (depth == 0) {
                                end = i
                                break
                            }
                        }
                    }
                }
                code.substring(match.range.first, end + 1)
            }.toList()
    }

    @Test
    fun `no renderer round trip runs inside a Main-thread block`() {
        val root = assertNotNull(repoRoot(), "could not locate the repository root")
        val scanned = browserSources(root)

        // Deliberately not a skip: a guard that passes when it cannot see the tree is decoration.
        assertTrue(scanned.size > 5, "only ${scanned.size} files scanned - the walk is not seeing the source")

        val offenders =
            scanned.flatMap { file ->
                mainThreadBlocks(codeOf(file))
                    .filter { block -> rendererRoundTrips.any { block.contains(it) } }
                    .map { block -> "${file.name}: ${block.take(140)}" }
            }

        assertTrue(
            offenders.isEmpty(),
            "blocking renderer round trip inside withContext(Dispatchers.Main): $offenders. " +
                "A renderer that does not answer parks the EDT forever, and the AppKit main thread " +
                "parks behind it - the whole app and the menu bar freeze. Make the call on a " +
                "dedicated daemon thread (see handleCallDispatcher) and bound the wait from another.",
        )
    }

    @Test
    fun `no handle scope is built on the Main dispatcher`() {
        val root = assertNotNull(repoRoot(), "could not locate the repository root")
        val scanned = browserSources(root)
        assertTrue(scanned.size > 5, "only ${scanned.size} files scanned - the walk is not seeing the source")

        // Every scope declared here drives browser work, so a Main-dispatched one puts the round
        // trips of whatever launches into it on the EDT - which is how the co-browse and page-event
        // scopes came to freeze the app without any single call site looking wrong.
        val mainScopes =
            scanned
                // A bounded window, not `[^)]*`: the context is built as
                // `CoroutineScope(SupervisorJob() + Dispatchers.Main)`, and a negated-paren run
                // stops dead at SupervisorJob's own `)` - so the first version of this guard matched
                // nothing at all and passed against the very code it was written to catch.
                .filter { codeOf(it).contains(Regex("""CoroutineScope\(.{0,120}?Dispatchers\.Main""")) }
                .map { it.name }
                .sorted()

        assertTrue(
            mainScopes.isEmpty(),
            "browser scope built on Dispatchers.Main: $mainScopes. Anything launched into it makes " +
                "its blocking JxBrowser calls on the EDT. Dispatch the scope onto a dedicated " +
                "thread instead, as coBrowseScope and pageEventScope now are.",
        )
    }
}
