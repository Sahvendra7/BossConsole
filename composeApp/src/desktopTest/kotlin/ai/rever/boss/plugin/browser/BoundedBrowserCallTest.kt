package ai.rever.boss.plugin.browser

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import kotlin.system.measureTimeMillis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What the deadline actually buys, pinned without a Chromium.
 *
 * The freeze this class exists to prevent needs a real renderer that stops answering, which no unit
 * test can arrange. What it *can* arrange is the shape: a call that never returns, on the one thread
 * it is confined to. Everything the KDoc promises about that is checked here, because the promise
 * was wrong once already - the wait used to run in the caller's context, so a caller that happened
 * to be single-threaded lost the bound with nothing in the signature to say so.
 */
class BoundedBrowserCallTest {
    private val timeout = 300L

    /** Long enough that a *bounded* wait cannot reach it, short enough to fail a test run fast. */
    private val generous = 10_000L

    @Test
    fun `a call that returns is answered with its value`() {
        val call = BoundedBrowserCall("test-bounded-ok")
        try {
            runBlocking { assertEquals("answered", call.call(generous) { "answered" }) }
        } finally {
            call.shutdown()
        }
    }

    /**
     * The property this class exists for, and the only one every other test here takes for granted.
     *
     * Everything else is about the *wait*. If a refactor ran `block()` inline in the caller's context
     * the deadline tests would all still pass and the EDT would be back in the blocking call - which
     * is the entire bug.
     */
    @Test
    fun `the block runs on the dedicated thread, not the caller's`() {
        val call = BoundedBrowserCall("test-bounded-thread")
        try {
            runBlocking {
                // startsWith, not equals: with coroutine debug on, kotlinx appends "@coroutine#N" to
                // the thread name. The prefix is the assertion - it says which thread ran the block.
                val ranOn = call.call(generous) { Thread.currentThread().name }.orEmpty()
                assertTrue(
                    ranOn.startsWith("test-bounded-thread"),
                    "block ran on \"$ranOn\" - it must run on the dedicated thread, not the caller's",
                )
            }
        } finally {
            call.shutdown()
        }
    }

    @Test
    fun `a call that never answers gives up on schedule`() {
        val call = BoundedBrowserCall("test-bounded-wedge")
        val release = CountDownLatch(1)
        try {
            val elapsed =
                measureTimeMillis {
                    runBlocking {
                        assertNull(
                            call.call(timeout) {
                                release.await()
                                "never"
                            },
                        )
                    }
                }
            // Both sides: `< generous` proves the wait ended, `>= timeout` proves the DEADLINE is what
            // ended it rather than the call quietly answering null for some unrelated reason.
            assertTrue(elapsed < generous, "waited ${elapsed}ms - the deadline did not bound the call")
            assertTrue(elapsed >= timeout, "returned after ${elapsed}ms, before its own ${timeout}ms deadline")
        } finally {
            release.countDown()
            call.shutdown()
        }
    }

    /**
     * A wedge has to be *reportable*, not just survivable.
     *
     * The first version answered null on timeout and told nobody: `onError` was reachable only
     * through the success path's `getOrElse`, and `withTimeoutOrNull` returning null short-circuited
     * straight past it. That turns "the app froze" into "plugin JS silently returns null forever",
     * which is better to live with and much worse to diagnose.
     */
    @Test
    fun `a timeout is reported, and is not reported as an error`() {
        val call = BoundedBrowserCall("test-bounded-report")
        val release = CountDownLatch(1)
        var timedOut = 0
        val errors = mutableListOf<Throwable>()
        try {
            runBlocking {
                assertNull(
                    call.call(timeout, onError = { errors += it }, onTimeout = { timedOut++ }) {
                        release.await()
                        "never"
                    },
                )
            }
            assertEquals(1, timedOut, "the timeout produced no report at all")
            assertTrue(errors.isEmpty(), "a timeout was reported as a thrown error: $errors")
        } finally {
            release.countDown()
            call.shutdown()
        }
    }

    /**
     * The trade the KDoc describes: the wedged call keeps the thread, so later calls do NOT get
     * through - they queue behind it and answer null on time. Degradation, not a freeze.
     */
    @Test
    fun `later calls still answer on schedule while one is wedged`() {
        val call = BoundedBrowserCall("test-bounded-queue")
        val release = CountDownLatch(1)
        try {
            runBlocking {
                assertNull(
                    call.call(timeout) {
                        release.await()
                        "never"
                    },
                )
                val elapsed = measureTimeMillis { assertNull(call.call(timeout) { "queued behind it" }) }
                assertTrue(elapsed < generous, "the second call waited ${elapsed}ms rather than its own deadline")
                assertTrue(elapsed >= timeout, "the second call returned after ${elapsed}ms, before its deadline")
            }
        } finally {
            release.countDown()
            call.shutdown()
        }
    }

    /**
     * The regression that matters: the bound must not depend on where the caller runs.
     *
     * A caller confined to its own single thread - which is what `coBrowseScope` and `pageEventScope`
     * are - used to lose the deadline entirely, because `withTimeoutOrNull { await() }` ran in the
     * caller's context and resuming it needed a dispatch this class had no say over.
     */
    @Test
    fun `the bound holds for a caller confined to its own single thread`() {
        val call = BoundedBrowserCall("test-bounded-confined")
        val callerThread = Executors.newSingleThreadExecutor { r -> Thread(r, "test-confined-caller") }
        val release = CountDownLatch(1)
        try {
            val elapsed =
                measureTimeMillis {
                    runBlocking {
                        withContext(callerThread.asCoroutineDispatcher()) {
                            assertNull(
                                call.call(timeout) {
                                    release.await()
                                    "never"
                                },
                            )
                        }
                    }
                }
            assertTrue(elapsed < generous, "waited ${elapsed}ms - the caller's dispatcher still decides the bound")
            assertTrue(elapsed >= timeout, "returned after ${elapsed}ms, before its own deadline")
        } finally {
            release.countDown()
            call.shutdown()
            callerThread.shutdownNow()
        }
    }

    /**
     * After [BoundedBrowserCall.shutdown] the executor rejects the dispatch and kotlinx cancels the
     * job. That cancellation belongs to this class, not to the caller, so it owes a null rather than
     * throwing into a plugin's coroutine - which is what every other failure path here answers.
     */
    @Test
    fun `a call after shutdown answers null rather than throwing`() {
        val call = BoundedBrowserCall("test-bounded-shutdown")
        call.shutdown()
        runBlocking { assertNull(call.call(generous) { "unreachable" }) }
    }

    /** A throwing call is a null answer plus one report, not a propagated exception. */
    @Test
    fun `a throwing call is reported and answered with null`() {
        val call = BoundedBrowserCall("test-bounded-throw")
        val seen = mutableListOf<Throwable>()
        try {
            runBlocking {
                assertNull(call.call(generous, onError = { seen += it }) { error("renderer said no") })
            }
            assertEquals(1, seen.size, "expected exactly one reported failure, got $seen")
        } finally {
            call.shutdown()
        }
    }

    /** The caller's own cancellation is not swallowed by the shutdown handling above. */
    @Test
    fun `a caller's cancellation still propagates`() {
        val call = BoundedBrowserCall("test-bounded-cancel")
        val release = CountDownLatch(1)
        val entered = CountDownLatch(1)
        try {
            var propagated = false
            runBlocking {
                val job =
                    launch {
                        try {
                            call.call(generous) {
                                entered.countDown()
                                release.await()
                                "never"
                            }
                        } catch (_: CancellationException) {
                            propagated = true
                        }
                    }
                // Cancel only once the blocking body is genuinely in flight, so this tests the
                // await being cancelled rather than the job never having started.
                withContext(Dispatchers.IO) { entered.await() }
                job.cancelAndJoin()
            }
            assertTrue(propagated, "the caller's cancellation was swallowed and answered as null")
        } finally {
            release.countDown()
            call.shutdown()
        }
    }
}
