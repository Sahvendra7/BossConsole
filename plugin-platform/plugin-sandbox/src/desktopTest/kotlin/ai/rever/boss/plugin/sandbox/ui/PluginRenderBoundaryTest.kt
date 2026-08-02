package ai.rever.boss.plugin.sandbox.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Covers containment of plugin crashes in the render phases.
 *
 * The failure being guarded against: a plugin's composables have already
 * returned by the time Compose measures, places or draws their nodes, so an
 * exception there carries no plugin frame and
 * [PluginCrashInterceptor.attributeToPlugin] cannot identify an owner. The
 * window handler then treats it as a host fault and disposes the window, ending
 * the app — which is how a duplicate LazyColumn key in the bookmarks plugin took
 * BossConsole down (BossConsole-Releases#16).
 *
 * These tests assert the boundary catches each phase, hands the throwable to the
 * owner, and leaves the host composition standing.
 */
// The boundary under test exists to catch anything, so the tests must be able to
// throw and observe anything — including Errors.
@Suppress("TooGenericExceptionCaught")
@OptIn(ExperimentalTestApi::class)
class PluginRenderBoundaryTest {
    /** Throws while being measured — the phase from the real crash report. */
    @Composable
    private fun ThrowsDuringMeasure(error: Throwable) {
        Layout(content = {}) { _, _ -> throw error }
    }

    /** Throws while placing its children. */
    @Composable
    private fun ThrowsDuringPlacement(error: Throwable) {
        Layout(content = {}) { _, _ ->
            layout(10, 10) { throw error }
        }
    }

    /** Throws while drawing. */
    @Composable
    private fun ThrowsDuringDraw(error: Throwable) {
        Box(
            Modifier
                .size(10.dp)
                .drawWithContent { throw error },
        )
    }

    private fun captureCrashFrom(content: @Composable () -> Unit): Throwable? {
        val captured = AtomicReference<Throwable?>(null)
        runComposeUiTest {
            setContent {
                Box(Modifier.fillMaxSize()) {
                    PluginRenderBoundary(
                        pluginId = "ai.rever.boss.plugin.dynamic.test",
                        onRenderCrash = { captured.set(it) },
                        content = content,
                    )
                }
            }
        }
        return captured.get()
    }

    @Test
    fun `a crash during measure is contained and handed to the owner`() {
        val boom = IllegalStateException("layout state is not idle before measure starts")

        val captured = captureCrashFrom { ThrowsDuringMeasure(boom) }

        assertNotNull(captured, "the measure crash escaped the boundary")
        assertEquals(boom, captured, "the owner was handed the wrong throwable")
    }

    @Test
    fun `a crash during placement is contained`() {
        val boom = IllegalStateException("placement blew up")

        val captured = captureCrashFrom { ThrowsDuringPlacement(boom) }

        assertNotNull(captured, "the placement crash escaped the boundary")
        assertEquals(boom, captured)
    }

    @Test
    fun `a crash during draw is contained`() {
        val boom = IllegalStateException("draw blew up")

        val captured = captureCrashFrom { ThrowsDuringDraw(boom) }

        assertNotNull(captured, "the draw crash escaped the boundary")
        assertEquals(boom, captured)
    }

    @Test
    fun `the reported duplicate-LazyColumn-key crash is contained`() {
        // The actual shape from BossConsole-Releases#16: two items under one key
        // share a subcomposition slot and therefore one LayoutNode, so the list
        // measures that node twice in a pass. Reproduced here rather than
        // described, so the boundary is proven against the real failure and not
        // just a synthetic throw.
        val captured = AtomicReference<Throwable?>(null)
        var hostStillComposed = false

        runComposeUiTest {
            setContent {
                Box(Modifier.fillMaxSize()) {
                    PluginRenderBoundary(
                        pluginId = "ai.rever.boss.plugin.dynamic.bookmarks",
                        onRenderCrash = { captured.set(it) },
                    ) {
                        LazyColumn(Modifier.fillMaxSize()) {
                            items(listOf("dup", "dup"), key = { it }) {
                                Box(Modifier.size(20.dp))
                            }
                        }
                    }
                }
                // Composed as a sibling of the boundary: if the crash had escaped,
                // the whole composition would have gone down with it.
                hostStillComposed = true
            }
        }

        assertNotNull(
            captured.get(),
            "duplicate keys did not throw here — if Compose stopped treating this as an error, " +
                "this test needs rewriting, but the boundary is still what contains it",
        )
        assertTrue(hostStillComposed, "the host composition did not survive the plugin's crash")
    }

    @Test
    fun `content that renders cleanly is untouched`() {
        val captured = AtomicReference<Throwable?>(null)
        var childComposed = false

        runComposeUiTest {
            setContent {
                Box(Modifier.fillMaxSize()) {
                    PluginRenderBoundary(
                        pluginId = "ai.rever.boss.plugin.dynamic.test",
                        onRenderCrash = { captured.set(it) },
                    ) {
                        Box(Modifier.size(24.dp))
                        childComposed = true
                    }
                }
            }
        }

        assertNull(captured.get(), "a healthy subtree must not report a crash")
        assertTrue(childComposed, "the boundary must not stop its content composing")
    }

    @Test
    fun `an OutOfMemoryError is not swallowed`() {
        // A dead JVM is not the plugin's to own, and pretending otherwise would
        // turn an unrecoverable condition into a silent one.
        val fatal = OutOfMemoryError("Java heap space")
        val captured = AtomicReference<Throwable?>(null)
        var escaped: Throwable? = null

        try {
            runComposeUiTest {
                setContent {
                    PluginRenderBoundary(
                        pluginId = "ai.rever.boss.plugin.dynamic.test",
                        onRenderCrash = { captured.set(it) },
                    ) {
                        ThrowsDuringMeasure(fatal)
                    }
                }
            }
        } catch (t: Throwable) {
            escaped = t
        }

        assertNull(captured.get(), "a fatal JVM error must not be reported as a plugin crash")
        assertNotNull(escaped, "an OutOfMemoryError must propagate, not be contained")
    }
}
