package ai.rever.boss.plugin.browser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The part of the frame-stall repair that decides whether a user ever sees a flicker loop.
 *
 * Extracted from `BrowserHandleImpl` precisely so these cases can be asserted without a live
 * JxBrowser view, and with the clock supplied rather than read - the cooldown boundary is not
 * testable otherwise.
 */
class FrameStallPolicyTest {
    private fun policy() = FrameStallPolicy(maxIneffective = 3, cooldownMs = 10_000L)

    @Test
    fun `the first claim is granted`() {
        assertEquals(FrameStallPolicy.Decision.REATTACH, policy().claim(0L))
    }

    @Test
    fun `a second claim inside the cooldown is denied, and allowed on the boundary`() {
        val p = policy()
        assertEquals(FrameStallPolicy.Decision.REATTACH, p.claim(1_000L))
        assertEquals(FrameStallPolicy.Decision.COOLING_DOWN, p.claim(1_000L + 9_999L))
        assertEquals(FrameStallPolicy.Decision.REATTACH, p.claim(1_000L + 10_000L))
    }

    @Test
    fun `a denied claim does not restart the cooldown`() {
        // If a refusal moved the clock forward, a tab navigating faster than the cooldown would
        // never be repaired again - each attempt pushing the window out ahead of itself.
        val p = policy()
        assertEquals(FrameStallPolicy.Decision.REATTACH, p.claim(0L))
        assertEquals(FrameStallPolicy.Decision.COOLING_DOWN, p.claim(9_000L))
        assertEquals(FrameStallPolicy.Decision.REATTACH, p.claim(10_000L))
    }

    @Test
    fun `the cap trips after N ineffective repairs in a row and reports once`() {
        val p = policy()
        var now = 0L
        repeat(3) {
            assertEquals(FrameStallPolicy.Decision.REATTACH, p.claim(now), "attempt at $now")
            p.recordOutcome(recovered = false)
            now += 10_000L
        }
        assertEquals(3, p.ineffectiveInARow)
        // First refusal is the one the caller logs; every later one stays silent, so a tab that
        // keeps navigating does not repeat the give-up line forever.
        assertEquals(FrameStallPolicy.Decision.GIVE_UP_NOW, p.claim(now))
        assertEquals(FrameStallPolicy.Decision.GIVEN_UP, p.claim(now + 10_000L))
        assertEquals(FrameStallPolicy.Decision.GIVEN_UP, p.claim(now + 100_000L))
    }

    @Test
    fun `a repair that works resets the run, so a repeatedly-repairable tab is never abandoned`() {
        // The regression this exists for: an earlier lifetime cap abandoned the 4th and 5th
        // click-through on one tab while the repair was recovering the page 3 times out of 3.
        val p = policy()
        var now = 0L
        repeat(10) {
            assertEquals(FrameStallPolicy.Decision.REATTACH, p.claim(now), "attempt at $now")
            p.recordOutcome(recovered = true)
            now += 10_000L
        }
        assertEquals(10, p.attempts)
        assertEquals(0, p.ineffectiveInARow)
    }

    @Test
    fun `two failures then a success clears the run`() {
        val p = policy()
        var now = 0L
        p.claim(now)
        p.recordOutcome(recovered = false)
        now += 10_000L
        p.claim(now)
        p.recordOutcome(recovered = false)
        assertEquals(2, p.ineffectiveInARow)
        now += 10_000L
        p.claim(now)
        p.recordOutcome(recovered = true)
        assertEquals(0, p.ineffectiveInARow)
        // Still granted well past what would have been the cap.
        now += 10_000L
        assertEquals(FrameStallPolicy.Decision.REATTACH, p.claim(now))
    }

    @Test
    fun `a healthy navigation decays the run`() {
        // "Consecutive" must be counted against evidence the tab is broken, not against wall time:
        // three ineffective attempts spread over hours of healthy browsing should not retire the
        // watchdog for that tab.
        val p = policy()
        var now = 0L
        repeat(2) {
            p.claim(now)
            p.recordOutcome(recovered = false)
            now += 10_000L
        }
        assertEquals(2, p.ineffectiveInARow)
        p.recordHealthyNavigation()
        assertEquals(0, p.ineffectiveInARow)
    }

    @Test
    fun `a give-up can be reported again after the tab recovers`() {
        val p = policy()
        var now = 0L
        repeat(3) {
            p.claim(now)
            p.recordOutcome(recovered = false)
            now += 10_000L
        }
        assertEquals(FrameStallPolicy.Decision.GIVE_UP_NOW, p.claim(now))
        p.recordHealthyNavigation()
        now += 10_000L
        assertEquals(FrameStallPolicy.Decision.REATTACH, p.claim(now))
        // Fails three more times: the operator gets a fresh line rather than permanent silence.
        repeat(2) {
            p.recordOutcome(recovered = false)
            now += 10_000L
            p.claim(now)
        }
        p.recordOutcome(recovered = false)
        now += 10_000L
        assertEquals(FrameStallPolicy.Decision.GIVE_UP_NOW, p.claim(now))
    }

    @Test
    fun `attempts counts only granted claims`() {
        val p = policy()
        p.claim(0L)
        p.claim(1L)
        p.claim(2L)
        assertEquals(1, p.attempts)
    }

    @Test
    fun `the shipped defaults are sane`() {
        val shipped = FrameStallPolicy()
        assertEquals(FrameStallPolicy.Decision.REATTACH, shipped.claim(0L))
        assertEquals(FrameStallPolicy.Decision.COOLING_DOWN, shipped.claim(1L))
        assertTrue(BrowserFrameStall.REATTACH_COOLDOWN_MS >= 1_000)
        assertTrue(BrowserFrameStall.MAX_INEFFECTIVE_REATTACHES in 1..10)
    }
}
