package ai.rever.boss.plugin.browser

import ai.rever.boss.components.overlays.insetBounds
import ai.rever.boss.components.overlays.resolveRegion
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import com.teamdev.jxbrowser.browser.Browser
import com.teamdev.jxbrowser.search.FindResult
import com.teamdev.jxbrowser.search.TextFinder
import org.junit.Test
import java.lang.reflect.Proxy
import java.util.function.Consumer
import javax.swing.SwingUtilities
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Find-in-page: where the bar is placed, who owns the find chord, and what the counter is allowed
 * to say.
 *
 * The three search behaviours pinned here were each a live bug in the Swing find bar this replaced,
 * and each one reported a plausible WRONG number rather than failing visibly - which is why they
 * survived. See the individual tests.
 *
 * Reflection proxies rather than mocks, matching [PopupWindowContextMenuTest]: the build has no
 * mocking library, and these interfaces are only asked for a handful of methods.
 */
class BrowserFindTest {
    // ========================================================================
    // Where the bar goes
    // ========================================================================

    @Test
    fun `pane rect converts to a dp region inset by the corner margin`() {
        // A pane at (100, 50) sized 800x600 in device pixels, at 1x.
        val region = findBarRegion(Rect(100f, 50f, 900f, 650f), density = 1f)
        assertEquals(IntRect(108, 58, 892, 642), region)
    }

    @Test
    fun `the region is in dp, so a 2x display does not double it`() {
        // The same pane on a 2x display reports twice the pixels. A heavyweight overlay is placed
        // in logical units, so failing to divide here puts the bar off screen by the scale factor.
        val region = findBarRegion(Rect(200f, 100f, 1800f, 1300f), density = 2f)
        assertEquals(IntRect(108, 58, 892, 642), region)
    }

    @Test
    fun `an unmeasured or clipped-away pane has no region`() {
        // boundsInWindow reports CLIPPED bounds, so a collapsed or scrolled-out pane measures
        // empty. Null keeps the bar off screen; the alternative is HeavyweightCorner resolving an
        // unmeasurable parent to the primary display's origin and putting an always-on-top bar
        // there.
        assertNull(findBarRegion(Rect.Zero, density = 1f))
        assertNull(findBarRegion(Rect(10f, 10f, 10f, 400f), density = 1f))
        assertNull(findBarRegion(Rect(10f, 10f, 400f, 10f), density = 1f))
    }

    @Test
    fun `a pane narrower than the margins has no region`() {
        // Deflating a 12dp-wide pane by 8 on each side inverts it. An inverted IntRect has a
        // negative width, and placing a corner inside one lands outside the pane entirely.
        assertNull(findBarRegion(Rect(0f, 0f, 12f, 12f), density = 1f))
    }

    @Test
    fun `a bad density is not trusted`() {
        assertNull(findBarRegion(Rect(0f, 0f, 800f, 600f), density = 0f))
        assertNull(findBarRegion(Rect(0f, 0f, 800f, 600f), density = -1f))
        assertNull(findBarRegion(Rect(0f, 0f, 800f, 600f), density = Float.NaN))
    }

    @Test
    fun `no region means the inset path, unchanged`() {
        val pane = intArrayOf(1000, 500, 800, 600)
        val inset = DpSize(40.dp, 20.dp)
        assertEquals(
            insetBounds(pane, inset)!!.toList(),
            resolveRegion(pane, inset, regionInWindow = null)!!.toList(),
        )
    }

    @Test
    fun `a region is offset into screen coordinates`() {
        // The caller measures against the window; HeavyweightCorner places against the screen.
        val pane = intArrayOf(1000, 500, 800, 600)
        val resolved = resolveRegion(pane, DpSize.Zero, IntRect(400, 8, 792, 592))
        assertEquals(listOf(1400, 508, 392, 584), resolved!!.toList())
    }

    @Test
    fun `a region larger than the pane is clamped, not trusted`() {
        // Compose layout and AWT measure the pane independently and can disagree for a frame during
        // a resize. An unclamped region wider than the pane would place an always-on-top overlay
        // outside the window it belongs to.
        val pane = intArrayOf(0, 0, 800, 600)
        val resolved = resolveRegion(pane, DpSize.Zero, IntRect(0, 0, 5000, 5000))
        assertEquals(listOf(0, 0, 800, 600), resolved!!.toList())
    }

    @Test
    fun `a degenerate region falls back to the inset path`() {
        val pane = intArrayOf(0, 0, 800, 600)
        val inset = DpSize(30.dp, 10.dp)
        val resolved = resolveRegion(pane, inset, IntRect(900, 900, 950, 950))
        assertEquals(insetBounds(pane, inset)!!.toList(), resolved!!.toList())
    }

    // ========================================================================
    // Who owns the find chord
    // ========================================================================

    @Test
    fun `the bridge maps only the two verdicts it defines`() {
        val seen = mutableListOf<Boolean>()
        val bridge = BrowserFindKeyProbeBridge { seen += it }

        bridge.report(BrowserFindKeyProbe.VERDICT_HANDLED)
        bridge.report(BrowserFindKeyProbe.VERDICT_FREE)
        assertEquals(listOf(true, false), seen)

        // Anything else is a page calling the bridge with its own argument. Dropped, not guessed:
        // guessing "free" lets a site open a find bar nobody asked for, and guessing "handled"
        // lets it suppress one the user did ask for.
        bridge.report("")
        bridge.report("HANDLED")
        bridge.report("{\"verdict\":\"handled\"}")
        assertEquals(listOf(true, false), seen)
    }

    @Test
    fun `the bridge never throws into the page's JS thread`() {
        // A throw here surfaces in the site's own console and can break its scripts, so the
        // boundary swallows. Errors are deliberately NOT swallowed, but nothing this test can
        // raise is one.
        val bridge = BrowserFindKeyProbeBridge { error("consumer blew up") }
        bridge.report(BrowserFindKeyProbe.VERDICT_HANDLED)
        bridge.report(BrowserFindKeyProbe.VERDICT_FREE)
    }

    // ========================================================================
    // What the counter is allowed to say
    // ========================================================================

    @Test
    fun `a search still running never commits a count`() {
        // WAS A BUG. TextFinderImpl invokes the consumer on every reply, SEARCHING ones included,
        // and during those numberOfMatches() is a running tally while selectedMatch() need not be
        // meaningful yet. The Swing bar rendered them, so every keystroke flashed a red 0/0 before
        // the real count landed.
        withStubbedBrowser { browser, finder, _ ->
            val state = BrowserFindController.stateFor(browser)
            BrowserFindController.setQuery(browser, "needle")
            BrowserFindController.next(browser)

            val consumer = finder.consumers.single()
            consumer.accept(findResult(matches = 0, selected = 0, searching = true))
            assertFalse(state.settled, "a searching reply must not settle the counter")
            consumer.accept(findResult(matches = 2, selected = 1, searching = true))
            assertFalse(state.settled)
            assertEquals(0, state.totalMatches)

            consumer.accept(findResult(matches = 3, selected = 1, searching = false))
            assertTrue(state.settled)
            assertEquals(3, state.totalMatches)
            assertEquals(1, state.currentMatch)
        }
    }

    @Test
    fun `a reply from a superseded search cannot overwrite the current one`() {
        // WAS A BUG. TextFinderImpl allocates a fresh request id per find() and keeps a live
        // consumer for each, and the debounce cannot cancel a search already in flight - so
        // several sessions report at once and the label showed whichever FINISHED last, which is
        // not the one for the latest query.
        withStubbedBrowser { browser, finder, _ ->
            val state = BrowserFindController.stateFor(browser)

            BrowserFindController.setQuery(browser, "goo")
            BrowserFindController.next(browser)
            val stale = finder.consumers.single()

            BrowserFindController.setQuery(browser, "google")
            BrowserFindController.next(browser)
            assertEquals(2, finder.consumers.size, "the second query must start its own search")
            val current = finder.consumers.last()

            current.accept(findResult(matches = 4, selected = 2, searching = false))
            assertEquals(4, state.totalMatches)

            // The first search finishes late, with the count for text that is no longer in the box.
            stale.accept(findResult(matches = 97, selected = 12, searching = false))
            assertEquals(4, state.totalMatches, "a superseded search must not overwrite the count")
            assertEquals(2, state.currentMatch)
        }
    }

    @Test
    fun `emptying the query clears the count and stops searching`() {
        withStubbedBrowser { browser, finder, _ ->
            val state = BrowserFindController.stateFor(browser)
            BrowserFindController.setQuery(browser, "needle")
            BrowserFindController.next(browser)
            finder.consumers.single().accept(findResult(matches = 3, selected = 1, searching = false))
            assertEquals(3, state.totalMatches)

            BrowserFindController.setQuery(browser, "")
            assertEquals(0, state.totalMatches)
            assertFalse(state.settled)
            assertEquals(1, finder.clearedCount, "an empty query has no match worth keeping")
            assertEquals(0, finder.keptCount)
        }
    }

    @Test
    fun `closing keeps the match rather than clearing it`() {
        // WAS A BUG. The Swing bar called stopFindingAndClearSelection on Escape, which drops the
        // selection and scrolls the reader back to where they were before searching - throwing away
        // the result they went looking for. stopFindingAndKeepSelection existed and was unused.
        withStubbedBrowser { browser, finder, _ ->
            BrowserFindController.setQuery(browser, "needle")
            BrowserFindController.next(browser)
            finder.consumers.single().accept(findResult(matches = 3, selected = 2, searching = false))

            BrowserFindController.close(browser)
            assertEquals(1, finder.keptCount, "Escape must keep the current match selected")
            assertEquals(0, finder.clearedCount)
            assertFalse(BrowserFindController.stateFor(browser).visible)
        }
    }

    @Test
    fun `a reply arriving after dispose is dropped`() {
        // The debounce and the verdict deadline are timers: either can fire after the tab they
        // belong to is gone, and so can a reply already in flight.
        withStubbedBrowser(disposeAtEnd = false) { browser, finder, _ ->
            BrowserFindController.setQuery(browser, "needle")
            BrowserFindController.next(browser)
            val consumer = finder.consumers.single()

            BrowserFindController.dispose(browser)
            consumer.accept(findResult(matches = 9, selected = 1, searching = false))

            // dispose() drops the state, so the reply lands on a fresh one and cannot resurrect a
            // count for a browser that is gone.
            assertEquals(0, BrowserFindController.stateFor(browser).totalMatches)
            BrowserFindController.dispose(browser)
        }
    }

    // ========================================================================
    // "Find again" only claims the chord when it can act on it
    // ========================================================================

    @Test
    fun `find again is not claimed unless the bar is up with a query`() {
        withStubbedBrowser { browser, _, _ ->
            assertFalse(
                BrowserFindController.onFindAgainKey(browser, backward = false),
                "with no bar up, Cmd+G belongs to the page",
            )

            BrowserFindController.open(browser)
            assertFalse(
                BrowserFindController.onFindAgainKey(browser, backward = false),
                "an empty box has nothing to advance through",
            )

            BrowserFindController.setQuery(browser, "needle")
            assertTrue(BrowserFindController.onFindAgainKey(browser, backward = false))
            assertTrue(
                BrowserFindController.onFindAgainKey(browser, backward = true),
                "repeat presses must keep advancing - find again is not single-flighted",
            )
        }
    }

    @Test
    fun `the find chord is suppressed only when our bar already owns it`() {
        withStubbedBrowser { browser, _, _ ->
            // Not up yet: proceed, so a page with its own find-in-page gets first refusal.
            assertFalse(BrowserFindController.onFindKeyFromPage(browser))

            BrowserFindController.open(browser)
            // Up: ours, so suppress and re-focus rather than handing the page a key that would
            // open a second find UI on top of the first.
            assertTrue(BrowserFindController.onFindKeyFromPage(browser))
        }
    }

    @Test
    fun `one window serves one browser per shortcut event`() {
        // browserFindEvents is a broadcast and every composed browser surface collects it.
        // LocalIsPanelActive defaults to true, so a browser in a sidebar slot reads as active
        // alongside the main panel's - without this claim, one Cmd+F opens two bars.
        val windowId = "window-under-test-${System.nanoTime()}"
        assertTrue(BrowserFindController.claimShortcut(windowId))
        assertFalse(BrowserFindController.claimShortcut(windowId), "a second collector must not also serve it")
        assertTrue(
            BrowserFindController.claimShortcut("other-$windowId"),
            "a different window's event is a different event",
        )
    }

    @Test
    fun `the keymap path hands the chord to the page instead of opening straight away`() {
        // WAS A BUG IN THIS CHANGE. The shortcut path FEEDS the page path - it re-dispatches the
        // key so the one page-ownership decision runs - so it must not consume the single-flight
        // claim that re-entry needs. It did, which left the keymap route dispatching the key and
        // then dropping the verdict: Cmd+F with AWT focus silently did nothing on any page that
        // does not own the chord, which is nearly all of them.
        withStubbedBrowser { browser, _, dispatched ->
            val state = BrowserFindController.stateFor(browser)

            BrowserFindController.onFindKeyFromShortcut(browser)
            assertEquals(
                listOf("KeyPressed", "KeyReleased"),
                dispatched,
                "the page must get the chord, so a site with its own find can claim it",
            )
            assertFalse(state.visible, "opening is the verdict's decision, not the shortcut's")

            // The re-entry JxBrowser produces from that synthetic key.
            assertFalse(BrowserFindController.onFindKeyFromPage(browser), "the key must still proceed to the page")
            BrowserFindController.onPageVerdict(browser, pageHandledKey = false)
            assertTrue(state.visible, "a page that did not claim the chord gets the BOSS find bar")
        }
    }

    @Test
    fun `a page that claims the chord gets no BOSS find bar`() {
        withStubbedBrowser { browser, _, _ ->
            val state = BrowserFindController.stateFor(browser)
            assertFalse(BrowserFindController.onFindKeyFromPage(browser))
            BrowserFindController.onPageVerdict(browser, pageHandledKey = true)
            assertFalse(state.visible, "Sheets, Docs and Notion keep their own find")
        }
    }

    @Test
    fun `a verdict with nothing pending is ignored`() {
        // The bridge is reachable from any script on the page, so a site can call it unprompted.
        // With no decision waiting there is nothing to resolve - which is also what makes a page
        // spamming the bridge cheap to serve.
        withStubbedBrowser { browser, _, _ ->
            BrowserFindController.onPageVerdict(browser, pageHandledKey = false)
            assertFalse(BrowserFindController.stateFor(browser).visible)
        }
    }

    // ========================================================================
    // helpers
    // ========================================================================

    /**
     * A [TextFinder] that hands back every consumer it was given, so a test can decide when - and
     * in which order - a search reports.
     */
    private class RecordingTextFinder {
        val consumers = mutableListOf<Consumer<FindResult>>()
        var keptCount = 0
        var clearedCount = 0

        val proxy: TextFinder =
            Proxy.newProxyInstance(
                TextFinder::class.java.classLoader,
                arrayOf(TextFinder::class.java),
            ) { proxied, method, args ->
                when (method.name) {
                    "hashCode" -> {
                        System.identityHashCode(proxied)
                    }

                    "equals" -> {
                        proxied === args?.getOrNull(0)
                    }

                    "toString" -> {
                        "recordingTextFinder"
                    }

                    "find" -> {
                        @Suppress("UNCHECKED_CAST")
                        consumers += args?.last() as Consumer<FindResult>
                        null
                    }

                    "stopFindingAndKeepSelection" -> {
                        keptCount++
                        null
                    }

                    "stopFindingAndClearSelection" -> {
                        clearedCount++
                        null
                    }

                    else -> {
                        null
                    }
                }
            } as TextFinder
    }

    private fun findResult(
        matches: Int,
        selected: Int,
        searching: Boolean,
    ): FindResult =
        Proxy.newProxyInstance(
            FindResult::class.java.classLoader,
            arrayOf(FindResult::class.java),
        ) { proxied, method, args ->
            when (method.name) {
                "hashCode" -> System.identityHashCode(proxied)
                "equals" -> proxied === args?.getOrNull(0)
                "toString" -> "findResult($selected/$matches, searching=$searching)"
                "numberOfMatches" -> matches
                "selectedMatch" -> selected
                "isSearching" -> searching
                else -> null
            }
        } as FindResult

    private fun stubBrowser(
        finder: TextFinder,
        dispatched: MutableList<String> = mutableListOf(),
    ): Browser =
        Proxy.newProxyInstance(
            Browser::class.java.classLoader,
            arrayOf(Browser::class.java),
        ) { proxied, method, args ->
            when {
                method.name == "hashCode" -> {
                    System.identityHashCode(proxied)
                }

                method.name == "equals" -> {
                    proxied === args?.getOrNull(0)
                }

                method.name == "toString" -> {
                    "stubBrowser"
                }

                method.name == "textFinder" -> {
                    finder
                }

                method.name == "dispatch" -> {
                    dispatched += args?.getOrNull(0)?.let { it::class.java.simpleName }.orEmpty()
                    null
                }

                // isClosed comes from Closeable; false is what the boolean default below gives it,
                // and it must stay false or every call under test is skipped and the assertions
                // pass against a controller that did nothing.
                method.returnType == Boolean::class.javaPrimitiveType -> {
                    false
                }

                else -> {
                    null
                }
            }
        } as Browser

    /**
     * Run [block] on the AWT event thread with a fresh stub browser registered.
     *
     * On the EDT, not off it: the controller hops every state write there, so a test driving it
     * from a worker would assert against writes that have not happened yet. Running ON the thread
     * makes `onEdt` inline and the whole sequence synchronous, with no sleeps and nothing to flake.
     */
    private fun withStubbedBrowser(
        disposeAtEnd: Boolean = true,
        block: (Browser, RecordingTextFinder, MutableList<String>) -> Unit,
    ) {
        val finder = RecordingTextFinder()
        val dispatched = mutableListOf<String>()
        val browser = stubBrowser(finder.proxy, dispatched)
        BrowserFindController.register(browser)
        var failure: Throwable? = null
        SwingUtilities.invokeAndWait {
            try {
                block(browser, finder, dispatched)
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Throwable,
            ) {
                failure = e
            } finally {
                if (disposeAtEnd) BrowserFindController.dispose(browser)
            }
        }
        failure?.let { throw it }
    }
}
