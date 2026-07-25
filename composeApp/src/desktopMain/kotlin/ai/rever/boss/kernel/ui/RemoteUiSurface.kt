package ai.rever.boss.kernel.ui

import ai.rever.boss.ipc.proto.UIEvent
import ai.rever.boss.ipc.proto.WidgetUpdate
import ai.rever.boss.plugin.logging.BossLogger
import ai.rever.boss.plugin.logging.LogCategory
import ai.rever.boss.ui.sdk.WidgetDiffEngine
import ai.rever.boss.ui.sdk.WidgetProtoConverter.toDiffOperations
import ai.rever.boss.ui.sdk.WidgetProtoConverter.toKotlin
import ai.rever.boss.ui.sdk.WidgetTree
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.onEach
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import ai.rever.boss.ipc.proto.WidgetDiff as ProtoWidgetDiff

/**
 * One remote UI surface, from the host's side of the IPC boundary.
 *
 * A surface is the meeting point of two independent lifetimes: an out-of-process plugin that streams
 * widget trees in, and a host component ([ai.rever.boss.components.plugin.remote.RemotePanelComponent]
 * or `RemoteTabComponent`) that renders them and hands back user events. Neither one holds the other
 * — both address the surface by `surfaceId` through [RemoteUiSurfaceRegistry], so they can appear in
 * either order and either can go away without stranding the other.
 *
 * This object owns the two pieces of per-surface transport state:
 * - the last widget tree the plugin published (retained, so a component attaching later renders
 *   immediately instead of waiting for the next update), and
 * - the queue of user events waiting to go out to the plugin.
 *
 * Instances come from [RemoteUiSurfaceRegistry.register]; the constructor is internal because a
 * surface that is not in the registry is unreachable by definition.
 */
class RemoteUiSurface internal constructor(
    val surfaceId: String,
    val processId: String,
    private val publishTree: (WidgetTree) -> Unit,
    private val publishConnected: (Boolean) -> Unit,
) {
    /**
     * User events queued for the plugin process.
     *
     * **Ordered, and bounded.** Ordering is the load-bearing property: `TextChange` carries the whole
     * field value with last-write-wins semantics, so two keystrokes that arrive out of order silently
     * revert a character. A single channel written straight from the Compose callback makes emission
     * order equal interaction order, and one collector drains it into one gRPC stream, which preserves
     * that order on the wire.
     *
     * The bound is the part that differs from a purely in-process queue. Across a process boundary the
     * reader can stop reading — a wedged or paused plugin leaves gRPC flow control blocking our
     * collector, and an unlimited queue would then grow without limit inside the *host* for a fault
     * that is entirely the plugin's. [OUTGOING_BUFFER] events is over ten seconds of continuous human
     * interaction; a surface that falls that far behind is not "busy", it is gone.
     *
     * Overflow drops the **oldest** event, not the newest. Both lose information, but dropping the
     * newest inverts last-write-wins: the plugin would end up holding a stale text value with no later
     * event to correct it, and host and plugin would disagree about the field forever. Dropping from
     * the head keeps the queue converging on the current truth, and preserves the order of what
     * survives. Sheds are counted in [shedEventCount] rather than being silent.
     */
    private val outgoing = Channel<UIEvent>(OUTGOING_BUFFER, BufferOverflow.DROP_OLDEST)

    private val streamClaimed = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)
    private val buffered = AtomicInteger(0)
    private val shed = AtomicLong(0)

    /** The last tree the plugin published, or `null` before its first update. */
    @Volatile
    var tree: WidgetTree? = null
        private set

    /** Whether a plugin process currently holds this surface's `StreamUI` call. */
    val streaming: Boolean get() = streamClaimed.get()

    /** How many outgoing events have been shed because the plugin stopped reading. */
    val shedEventCount: Long get() = shed.get()

    /**
     * Take ownership of this surface's outgoing stream, or fail if someone already has it.
     *
     * One stream per surface is not a detail: two concurrent `StreamUI` calls would each drain part of
     * [outgoing], splitting one ordered event sequence across two readers and losing the ordering
     * guarantee the queue exists to provide.
     */
    internal fun claimStream(): Boolean {
        val claimed = streamClaimed.compareAndSet(false, true)
        if (claimed) publishConnected(true)
        return claimed
    }

    /** The outgoing event stream. Consumable exactly once — see [claimStream]. */
    internal fun events(): Flow<UIEvent> = outgoing.consumeAsFlow().onEach { buffered.decrementAndGet() }

    /**
     * Queue one user event for the plugin.
     *
     * Non-suspending on purpose: it is called straight from a Compose callback, where suspending would
     * mean either blocking the frame or handing the event to a coroutine that can be reordered against
     * its neighbours.
     *
     * @return `false` when the surface is closed — a late event is dropped, never thrown at the caller.
     */
    internal fun emit(event: UIEvent): Boolean {
        if (outgoing.trySend(event).isFailure) return false
        // DROP_OLDEST makes trySend always succeed, so overflow has to be inferred: the buffer cannot
        // hold more than its capacity, so a count above it means an eviction happened on this send.
        if (buffered.incrementAndGet() > OUTGOING_BUFFER) {
            buffered.decrementAndGet()
            val total = shed.incrementAndGet()
            if (total == 1L || total % SHED_LOG_INTERVAL == 0L) {
                logger.warn(
                    LogCategory.UI,
                    "Plugin is not reading its UI events — shedding the oldest",
                    mapOf("surfaceId" to surfaceId, "processId" to processId, "shed" to total),
                )
            }
        }
        return true
    }

    /** Publish a tree the host already holds in SDK form (a registration's `initial_tree`, or a test). */
    internal fun pushTree(next: WidgetTree) {
        tree = next
        publishTree(next)
    }

    /** Route one inbound `WidgetUpdate` into this surface's tree. */
    internal fun applyUpdate(update: WidgetUpdate) {
        when (update.updateCase) {
            WidgetUpdate.UpdateCase.FULL_TREE -> {
                pushTree(update.fullTree.toKotlin())
            }

            WidgetUpdate.UpdateCase.DIFF -> {
                applyDiff(update.diff)
            }

            WidgetUpdate.UpdateCase.UPDATE_NOT_SET, null -> {
                // Neither oneof arm set: a sender bug, or a proto case newer than this build. Either
                // way there is nothing to render, and guessing would corrupt the tree.
                logger.warn(
                    LogCategory.UI,
                    "Ignoring a WidgetUpdate that carries neither a full tree nor a diff",
                    mapOf("surfaceId" to surfaceId, "processId" to processId),
                )
            }
        }
    }

    /**
     * Close the surface: complete the outgoing stream and report the surface disconnected.
     *
     * Completing the channel is what unblocks the `StreamUI` collector and lets gRPC finish the call
     * cleanly, so this is also the anti-leak path. The last tree is deliberately **kept**: a surface
     * whose plugin died shows its final state with `connected == false` rather than going blank, which
     * is the difference between "disconnected" and "frozen" from the user's side.
     *
     * Idempotent, because it is legitimately reached twice on a graceful shutdown: `UnregisterUI` closes
     * the surface, which ends the event queue, which ends the `StreamUI` call, whose teardown closes the
     * surface again. Without the guard the host component would be told it disconnected twice.
     */
    internal fun close() {
        if (!closed.compareAndSet(false, true)) return
        outgoing.close()
        streamClaimed.set(false)
        publishConnected(false)
    }

    private fun applyDiff(diff: ProtoWidgetDiff) {
        val base = tree
        if (base == null) {
            // A diff is meaningless without the tree it was computed against, and the protocol has no
            // way to ask for a resync — so say so loudly instead of inventing an empty base.
            logger.warn(
                LogCategory.UI,
                "Dropping a widget diff for a surface with no tree yet — the plugin must send a full tree first",
                mapOf("surfaceId" to surfaceId, "processId" to processId),
            )
            return
        }
        if (diff.baseVersion != 0L && diff.baseVersion != base.version) {
            // Best-effort rather than fatal: refusing would freeze the surface permanently, whereas the
            // next full tree from the plugin repairs whatever this misapplies.
            logger.warn(
                LogCategory.UI,
                "Widget diff base version does not match the surface's tree — applying anyway",
                mapOf("surfaceId" to surfaceId, "expected" to base.version, "actual" to diff.baseVersion),
            )
        }
        val applied = WidgetDiffEngine.apply(base, diff.toDiffOperations())
        // WidgetDiffEngine bumps the version by one; honour the sender's numbering when it supplied
        // some, so the next diff's base_version check compares against what the plugin believes.
        pushTree(if (diff.newVersion != 0L) applied.copy(version = diff.newVersion) else applied)
    }

    companion object {
        /**
         * How many outgoing events a surface holds for a plugin that has stopped reading.
         *
         * Public because it is a documented property of the transport, not a tuning detail: it is the
         * point past which the host starts shedding a plugin's events. See [outgoing].
         */
        const val OUTGOING_BUFFER = 256

        /** Log the first shed event, then every Nth, so a wedged plugin cannot flood the log. */
        private const val SHED_LOG_INTERVAL = 256L

        private val logger = BossLogger.forComponent("RemoteUiSurface")
    }
}
