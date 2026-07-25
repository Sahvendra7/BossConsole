package ai.rever.boss.updater

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Ownership tests for the updater (Issues #19, #37).
 *
 * The bug these lock down: the updater is process-wide, but every window's
 * dispose called `UpdateManager.cleanup()`, so the *first* window to close
 * cancelled the shared scope — silently killing periodic checks and any in-flight
 * download for every window still open, with nothing to re-create the scope.
 *
 * Ownership is now explicit: [UpdateCoordinator] can shut down, [UpdateHandle]
 * cannot.
 */
class UpdateOwnershipTest {
    private fun newManager(): UpdateManager = UpdateManager()

    /** Whether work submitted to the manager's long-lived scope still runs. */
    private fun runsBackgroundWork(
        manager: UpdateManager,
        timeoutMs: Long = 5_000,
    ): Boolean {
        val ran = CountDownLatch(1)
        manager.launchInBackground { ran.countDown() }
        return ran.await(timeoutMs, TimeUnit.MILLISECONDS)
    }

    /**
     * The Rust port's test, in Kotlin: three window handles, drop two, the updater
     * still works for the third.
     */
    @Test
    fun `dropping two of three window handles leaves the updater running for the third`() {
        val manager = newManager()
        val coordinator = UpdateCoordinator(manager)
        try {
            val first = coordinator.handleFor("window-1")
            val second = coordinator.handleFor("window-2")
            val third = coordinator.handleFor("window-3")
            assertEquals(3, coordinator.activeWindowCount, "Three windows should hold handles")

            first.release()
            second.release()

            assertTrue(first.isReleased, "First handle should be released")
            assertTrue(second.isReleased, "Second handle should be released")
            assertFalse(third.isReleased, "Surviving window's handle must stay live")
            assertEquals(1, coordinator.activeWindowCount)

            assertFalse(coordinator.isShutDown, "Closing windows must not shut the updater down")
            assertTrue(manager.isActive, "Updater scope must survive other windows closing")
            assertTrue(
                runsBackgroundWork(manager),
                "Background update work (e.g. an in-flight download) must still run for the remaining window",
            )

            // The surviving handle can still act on shared update state.
            third.resetState()
            assertEquals(UpdateState.Idle, manager.updateState.value)
        } finally {
            coordinator.shutdown()
        }
    }

    @Test
    fun `releasing the last handle still does not shut the updater down`() {
        val manager = newManager()
        val coordinator = UpdateCoordinator(manager)
        try {
            val only = coordinator.handleFor("window-1")
            only.release()

            assertEquals(0, coordinator.activeWindowCount)
            assertFalse(coordinator.isShutDown)
            assertTrue(
                manager.isActive,
                "The app outlives its windows (macOS keeps running with none open) and a queued download must finish",
            )
            assertTrue(runsBackgroundWork(manager))
        } finally {
            coordinator.shutdown()
        }
    }

    @Test
    fun `releasing a handle twice is a no-op`() {
        val manager = newManager()
        val coordinator = UpdateCoordinator(manager)
        try {
            val first = coordinator.handleFor("window-1")
            val second = coordinator.handleFor("window-2")

            first.release()
            first.release()

            assertEquals(1, coordinator.activeWindowCount, "Double release must not drop another window's handle")
            assertFalse(second.isReleased)
        } finally {
            coordinator.shutdown()
        }
    }

    @Test
    fun `re-acquiring a handle for the same window releases the previous one`() {
        val manager = newManager()
        val coordinator = UpdateCoordinator(manager)
        try {
            val stale = coordinator.handleFor("window-1")
            val fresh = coordinator.handleFor("window-1")

            assertTrue(stale.isReleased, "The superseded handle should be released")
            assertFalse(fresh.isReleased)
            assertEquals(1, coordinator.activeWindowCount, "One window means one live handle")

            // Releasing the stale handle must not evict the fresh one.
            stale.release()
            assertEquals(1, coordinator.activeWindowCount)
            assertFalse(fresh.isReleased)
        } finally {
            coordinator.shutdown()
        }
    }

    @Test
    fun `released handle does not touch shared update state`() =
        runBlocking {
            val manager = newManager()
            val coordinator = UpdateCoordinator(manager)
            try {
                val handle = coordinator.handleFor("window-1")
                handle.release()

                val result = handle.checkForUpdates(force = true)

                assertEquals(UpdateResult.NoUpdateAvailable, result, "A released handle should go inert")
                assertEquals(UpdateState.Idle, manager.updateState.value, "No state transition should have happened")
                assertNull(manager.lastCheckTime.value, "A real check would have stamped the last check time")
            } finally {
                coordinator.shutdown()
            }
        }

    @Test
    fun `coordinator shutdown stops the updater and is idempotent`() {
        val manager = newManager()
        val coordinator = UpdateCoordinator(manager)
        val handle = coordinator.handleFor("window-1")

        coordinator.shutdown()

        assertTrue(coordinator.isShutDown)
        assertFalse(manager.isActive, "App-level shutdown should cancel the updater scope")
        assertFalse(manager.isPeriodicCheckActive)
        assertTrue(handle.isReleased, "Shutdown should release outstanding handles")
        assertEquals(0, coordinator.activeWindowCount)
        assertFalse(runsBackgroundWork(manager, timeoutMs = 500), "No background work should run after shutdown")

        // Second call must not throw or double-cancel.
        coordinator.shutdown()
        assertTrue(coordinator.isShutDown)
    }

    @Test
    fun `handles acquired after shutdown are inert`() {
        val manager = newManager()
        val coordinator = UpdateCoordinator(manager)
        coordinator.shutdown()

        val handle = coordinator.handleFor("window-1")

        assertTrue(handle.isReleased, "A handle handed out after shutdown must not pretend to be live")
        assertEquals(0, coordinator.activeWindowCount)
    }

    @Test
    fun `ensureStarted is ignored after shutdown`() =
        runBlocking {
            val manager = newManager()
            val coordinator = UpdateCoordinator(manager)
            coordinator.shutdown()

            coordinator.ensureStarted()

            assertFalse(manager.isPeriodicCheckActive, "A shut-down updater must not be restarted")
        }

    /**
     * The ownership split has to be visible in the API, not just in a comment:
     * a window holding an [UpdateHandle] must have no way to reach teardown.
     */
    @Test
    fun `window handles expose no teardown entry point`() {
        val teardownNames =
            setOf(
                "shutdown",
                "cleanup",
                "dispose",
                "close",
                "stopPeriodicChecks",
                "startPeriodicChecks",
            )
        val handleMethods =
            UpdateHandle::class.java.methods
                .map { it.name }
                .toSet()

        teardownNames.forEach { name ->
            assertFalse(
                name in handleMethods,
                "UpdateHandle must not expose '$name' - per-window teardown of the shared updater is the bug",
            )
        }

        assertFalse(
            UpdateManager::class.java.methods.any { it.name == "cleanup" },
            "UpdateManager.cleanup() was the per-window teardown call; it must not come back as public API",
        )
    }
}
