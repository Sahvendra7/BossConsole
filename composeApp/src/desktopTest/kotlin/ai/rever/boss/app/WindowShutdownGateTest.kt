package ai.rever.boss.app

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for the last-window gate behind the "Last Session" save (Issue #19).
 *
 * The bug: every window's dispose wrote its own layout into the single
 * "Last Session" workspace, so closing a secondary window overwrote the primary
 * window's session. Exactly one dispose may now perform that write.
 */
class WindowShutdownGateTest {
    @BeforeEach
    fun reset() {
        WindowShutdownGate.resetForTesting()
    }

    @Test
    fun `only the last window closing performs app-level teardown`() {
        WindowShutdownGate.register("window-1")
        WindowShutdownGate.register("window-2")
        WindowShutdownGate.register("window-3")
        assertEquals(3, WindowShutdownGate.liveWindowCount)

        assertFalse(
            WindowShutdownGate.releaseAndWasLast("window-2"),
            "A secondary window closing must not write the app-level session",
        )
        assertFalse(
            WindowShutdownGate.releaseAndWasLast("window-1"),
            "Still another window open - not the last",
        )
        assertTrue(
            WindowShutdownGate.releaseAndWasLast("window-3"),
            "The last window closing owns the save",
        )
        assertEquals(0, WindowShutdownGate.liveWindowCount)
    }

    @Test
    fun `a single window closing is the last window`() {
        WindowShutdownGate.register("window-1")

        assertTrue(WindowShutdownGate.releaseAndWasLast("window-1"))
    }

    @Test
    fun `releasing twice does not claim teardown twice`() {
        WindowShutdownGate.register("window-1")

        assertTrue(WindowShutdownGate.releaseAndWasLast("window-1"))
        assertFalse(
            WindowShutdownGate.releaseAndWasLast("window-1"),
            "A double dispose must not write the session a second time",
        )
    }

    @Test
    fun `an unregistered window never claims teardown`() {
        WindowShutdownGate.register("window-1")

        assertFalse(WindowShutdownGate.releaseAndWasLast("window-unknown"))
        assertEquals(1, WindowShutdownGate.liveWindowCount, "Unknown ids must not disturb tracking")
    }

    @Test
    fun `registering the same window twice is idempotent`() {
        WindowShutdownGate.register("window-1")
        WindowShutdownGate.register("window-1")

        assertEquals(1, WindowShutdownGate.liveWindowCount)
        assertTrue(WindowShutdownGate.releaseAndWasLast("window-1"))
    }

    @Test
    fun `isOnlyLiveWindow reflects the remaining window`() {
        WindowShutdownGate.register("window-1")
        WindowShutdownGate.register("window-2")

        assertFalse(WindowShutdownGate.isOnlyLiveWindow("window-1"))

        WindowShutdownGate.releaseAndWasLast("window-2")

        assertTrue(WindowShutdownGate.isOnlyLiveWindow("window-1"))
        assertFalse(WindowShutdownGate.isOnlyLiveWindow("window-2"))
    }
}
