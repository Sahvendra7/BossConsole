package ai.rever.boss.plugin.browser

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.CancellationException
import java.util.concurrent.Executors
import kotlin.coroutines.coroutineContext

/**
 * A blocking browser round trip, confined to one thread and answered within a deadline.
 *
 * `Frame.executeJavaScript` and `JsObject.putProperty` block until the *renderer* replies, and a
 * renderer has every right not to: one parked on a modal `window.prompt` cannot run script until the
 * dialog is answered, and one being swapped out mid-navigation never answers at all. Nothing can
 * interrupt the wait — the call has no suspension point, so a `withTimeoutOrNull` wrapped *around*
 * it is not a bound.
 *
 * Made from `Dispatchers.Main` that is a dead application, not a slow call: the EDT parks forever,
 * AppKit's main thread parks behind it, and the macOS menu bar goes with the window.
 *
 * Two threads are load-bearing, and they must be different ones:
 *
 *  - **[dispatcher]** — one daemon thread the blocking call is confined to. Daemon, because nothing
 *    can interrupt a call already inside JxBrowser and a wedged renderer must not hold up exit.
 *    Single, because that caps the cost of a wedge at one parked thread; later calls queue behind it
 *    and time out on schedule.
 *  - **[waitDispatcher]** — where the *wait* runs. Both on one thread and the timeout cannot fire:
 *    resuming the awaiting continuation needs a dispatch onto the very thread the blocking call is
 *    holding, so the wait would last as long as the renderer takes and the bound would be no bound.
 *
 * The hop to [waitDispatcher] is inside [call] rather than left to the caller on purpose. When the
 * bound depended on the caller's context, a caller that happened to be confined to a single thread
 * lost it silently, and nothing in the signature said so. The deadline is a property of this class
 * or it is not a property at all.
 *
 * One residual constraint cannot be fixed here, so it is stated instead: a caller running **on
 * [dispatcher] itself** can still have its *resumption* blocked, because resuming needs that one
 * thread back and the call it gave up on is holding it. The timeout fires and the value is ready;
 * delivering it is what waits. So do not `await` a [call] from a coroutine confined to [dispatcher]
 * — the scopes built on it launch fire-and-forget work, they do not await. The EDT is safe either
 * way, which is the property that matters.
 */
internal class BoundedBrowserCall(
    threadName: String,
    private val waitDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val executor =
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, threadName).apply { isDaemon = true }
        }

    /**
     * The one thread every blocking round trip runs on.
     *
     * Exposed so fire-and-forget browser work (injection, teardown) can be launched straight onto
     * it and stay ordered against the calls made through [call].
     */
    val dispatcher: ExecutorCoroutineDispatcher = executor.asCoroutineDispatcher()

    /**
     * Root of the calls, so a timed-out one is NOT a child of the coroutine that gave up on it.
     * Cancelling it could not interrupt the blocking call anyway, and as a child it would be
     * cancelled by the very timeout it is supposed to outlive.
     */
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    /**
     * Run [block] on [dispatcher], answering null if it has not returned within [timeoutMs].
     *
     * Null is also the answer when [block] throws (reported to [onError]) and when this call's own
     * scope is gone because [shutdown] has run — a disposed browser answers null like every other
     * failure here rather than throwing into a plugin's coroutine. A cancellation belonging to the
     * *caller* still propagates.
     *
     * A timed-out call is not abandoned cheaply: it keeps [dispatcher]'s thread until the renderer
     * answers, and later calls queue behind it. That is the trade — a tab that stops answering
     * degrades to null instead of freezing the application.
     */
    // The CancellationException below is swallowed on purpose and cannot carry information worth
    // keeping: it is either the caller's, in which case ensureActive rethrows it untouched, or it is
    // the executor rejecting a dispatch after shutdown, which is this class's own bookkeeping.
    @Suppress("SwallowedException")
    suspend fun <T> call(
        timeoutMs: Long,
        onError: (Throwable) -> Unit = {},
        block: () -> T?,
    ): T? {
        val job = scope.async { runCatching { block() } }
        return try {
            withContext(waitDispatcher) { withTimeoutOrNull(timeoutMs) { job.await() } }
                ?.getOrElse { error ->
                    onError(error)
                    null
                }
        } catch (e: CancellationException) {
            // Distinguishes "the caller gave up" from "this browser went away underneath us".
            // ensureActive rethrows only the former; a rejected dispatch after shutdown lands here
            // as a cancellation this call owns, and owes the plugin a null rather than a throw.
            coroutineContext.ensureActive()
            null
        } finally {
            if (!job.isCompleted) job.cancel()
        }
    }

    /**
     * Stop accepting new calls.
     *
     * `shutdown()` and not `shutdownNow()`: a call already inside JxBrowser cannot be interrupted,
     * so interrupting would buy nothing, and work already queued still deserves to run. The thread
     * is daemon, so a wedged call cannot hold up exit.
     */
    fun shutdown() {
        executor.shutdown()
    }
}
