package ai.rever.boss.crash

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Covers the containment decision for exceptions escaping the Compose render
 * loop.
 *
 * Both directions matter and they pull against each other. Containing too
 * eagerly leaves the user an app that repaints forever without working;
 * escalating too eagerly restores the behaviour this exists to fix, where one
 * bad frame disposes the window and ends the session
 * (BossConsole-Releases#16).
 */
class RenderCrashPolicyTest {
    /** Controllable clock — a real one would make the window assertions timing-dependent. */
    private class FakeClock(
        var now: Long = 0L,
    ) {
        fun advance(millis: Long) {
            now += millis
        }
    }

    private fun policy(
        clock: FakeClock,
        maxFailures: Int = 3,
        windowMillis: Long = 10_000,
    ) = RenderCrashPolicy(maxFailures = maxFailures, windowMillis = windowMillis, now = { clock.now })

    @Test
    fun `a burst up to the limit is contained`() {
        val clock = FakeClock()
        val policy = policy(clock)

        repeat(3) { attempt ->
            assertTrue(policy.shouldContain(), "failure ${attempt + 1} should have been contained")
        }
    }

    @Test
    fun `the failure past the limit escalates`() {
        val clock = FakeClock()
        val policy = policy(clock)

        repeat(3) { policy.shouldContain() }

        assertFalse(policy.shouldContain(), "a scene that keeps throwing must not be contained forever")
    }

    @Test
    fun `failures older than the window do not count`() {
        val clock = FakeClock()
        val policy = policy(clock)

        repeat(3) { policy.shouldContain() }
        clock.advance(10_001)

        assertTrue(
            policy.shouldContain(),
            "an app that hits one bad frame long after the last one is healthy, not looping",
        )
        assertTrue(policy.recentFailureCount() == 1, "stale failures should have been discarded")
    }

    @Test
    fun `failures just inside the window still count`() {
        val clock = FakeClock()
        val policy = policy(clock)

        repeat(3) { policy.shouldContain() }
        // Inside the window by a millisecond: this is still the same burst.
        clock.advance(9_999)

        assertFalse(policy.shouldContain(), "a failure inside the window must not reset the count")
    }

    @Test
    fun `a slow trickle never escalates`() {
        val clock = FakeClock()
        val policy = policy(clock)

        // One failure per minute, far apart: annoying, but the app is rendering.
        repeat(20) {
            assertTrue(policy.shouldContain(), "a widely spaced failure should always be contained")
            clock.advance(60_000)
        }
    }

    @Test
    fun `reset forgets the burst`() {
        val clock = FakeClock()
        val policy = policy(clock)

        repeat(3) { policy.shouldContain() }
        policy.reset()

        assertTrue(policy.shouldContain(), "reset should return the policy to a clean state")
    }
}
