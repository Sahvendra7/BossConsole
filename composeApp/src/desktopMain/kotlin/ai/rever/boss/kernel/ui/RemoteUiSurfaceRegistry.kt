package ai.rever.boss.kernel.ui

import ai.rever.boss.ipc.proto.UIEvent
import ai.rever.boss.plugin.logging.BossLogger
import ai.rever.boss.plugin.logging.LogCategory
import ai.rever.boss.ui.sdk.WidgetTree
import java.util.concurrent.ConcurrentHashMap

/**
 * The host-side renderer of one remote surface, as the transport sees it.
 *
 * Implemented by `RemotePanelComponent` / `RemoteTabComponent`. Both callbacks arrive on whichever
 * thread gRPC delivered the message on, never the UI thread — implementations must only touch
 * thread-safe state (Compose snapshot state is).
 */
interface RemoteUiSurfaceHost {
    /** A new widget tree to render. */
    fun onTreeUpdated(tree: WidgetTree)

    /** Whether a plugin process is currently streaming this surface. */
    fun onConnectionChanged(connected: Boolean)
}

/**
 * What a plugin declared about a surface when it registered it.
 *
 * Carried, not acted on: placing a remote surface in the window is the follow-up this transport unblocks,
 * and it is what will read these. Mirrors the corresponding `UIRegistration` fields.
 */
data class RemoteUiSurfaceDescriptor(
    val surfaceType: String = "",
    val displayName: String = "",
    val iconName: String = "",
    val defaultSlot: String = "",
)

/** Outcome of a plugin's `RegisterUI`. */
sealed interface SurfaceRegistration {
    data class Accepted(
        val surface: RemoteUiSurface,
    ) : SurfaceRegistration

    /** [reason] is written to be readable by a plugin author — it goes out as `error_message`. */
    data class Rejected(
        val reason: String,
    ) : SurfaceRegistration
}

/** Outcome of a plugin's `StreamUI` binding to a surface. */
sealed interface SurfaceStream {
    data class Bound(
        val surface: RemoteUiSurface,
    ) : SurfaceStream

    data class Refused(
        val reason: String,
    ) : SurfaceStream
}

/**
 * Directory of live remote UI surfaces, and the seam the two sides of a surface meet at.
 *
 * The problem this solves is that a surface's two halves start independently: the plugin process
 * registers and streams whenever it happens to come up, while the host component is constructed when
 * the user opens the panel or tab. Either order is normal, and a plugin can crash and respawn under a
 * component that never went away.
 *
 * So neither half holds a reference to the other. Both are indexed by `surfaceId` in separate maps —
 * plugin-side surfaces here, host-side renderers in [hosts] — and every delivery is a lookup at the
 * moment it happens. A tree that arrives with nobody attached is retained on the surface for whoever
 * attaches next; an event emitted with no plugin registered is reported undeliverable to its caller
 * rather than queued into a void or thrown; and a component that attaches to an already-streaming
 * surface is immediately given the current tree and connection state, so it does not render blank
 * until the plugin's next update.
 */
class RemoteUiSurfaceRegistry {
    private val surfaces = ConcurrentHashMap<String, RemoteUiSurface>()
    private val hosts = ConcurrentHashMap<String, RemoteUiSurfaceHost>()

    /**
     * Claim [surfaceId] for a plugin process.
     *
     * A claim held by a *different* process is refused rather than taken over: two plugins rendering into
     * one surface would interleave trees, and the second one's events would be delivered to the first's
     * stream.
     *
     * A claim held by the **same** process with no stream open is taken over, because that is what a
     * respawn looks like. [closeStream] releases the id when a stream dies, but a plugin can also die in
     * the window between `RegisterUI` returning and `StreamUI` binding, or hold claims on more surfaces
     * than it streams — and there is no notification for either. Refusing those would lock a plugin out of
     * its own `surface_id` forever, leaving the attached component permanently disconnected: exactly the
     * lockout `closeStream` exists to prevent, reached by a path it cannot see.
     */
    fun register(
        surfaceId: String,
        processId: String,
        descriptor: RemoteUiSurfaceDescriptor = RemoteUiSurfaceDescriptor(),
    ): SurfaceRegistration {
        if (surfaceId.isBlank()) {
            return SurfaceRegistration.Rejected("surface_id is required")
        }
        val created =
            RemoteUiSurface(
                surfaceId = surfaceId,
                processId = processId,
                descriptor = descriptor,
                publishTree = { tree -> hosts[surfaceId]?.onTreeUpdated(tree) },
                publishConnected = { connected -> hosts[surfaceId]?.onConnectionChanged(connected) },
            )
        val stale = claim(surfaceId, created)
        return if (stale != null) {
            SurfaceRegistration.Rejected(
                "surface_id '$surfaceId' is already registered by process '${stale.processId}'",
            )
        } else {
            logger.info(
                LogCategory.UI,
                "Remote UI surface registered",
                mapOf("surfaceId" to surfaceId, "processId" to processId, "attached" to hosts.containsKey(surfaceId)),
            )
            SurfaceRegistration.Accepted(created)
        }
    }

    /**
     * Install [created], returning the surface that blocked it, or `null` on success.
     *
     * Loops because the abandoned-claim replacement is a compare-and-set: another `RegisterUI` for the
     * same id can win the race, and the loser has to re-read rather than assume its own view.
     */
    private fun claim(
        surfaceId: String,
        created: RemoteUiSurface,
    ): RemoteUiSurface? {
        var blocker = surfaces.putIfAbsent(surfaceId, created)
        while (blocker != null) {
            val abandoned = blocker.processId == created.processId && !blocker.streaming
            if (!abandoned) break
            if (surfaces.replace(surfaceId, blocker, created)) {
                logger.info(
                    LogCategory.UI,
                    "Reclaimed an abandoned UI surface for its own process — treating it as a respawn",
                    mapOf("surfaceId" to surfaceId, "processId" to created.processId),
                )
                blocker.close()
                blocker = null
            } else {
                // Another RegisterUI for this id won the swap; re-read rather than trust our own view.
                blocker = surfaces.putIfAbsent(surfaceId, created)
            }
        }
        return blocker
    }

    /**
     * Tear a surface down at the plugin's request. @return `false` if it was not registered.
     *
     * Unattributed, unlike [closeStream]'s two-argument removal: `UIUnregistration` carries only a
     * `surface_id`, so a late call from a dying incarnation can evict a respawn's fresh surface. That
     * plugin's next `RegisterUI` recovers, and the window needs an `UnregisterUI` to arrive after a
     * respawn has already registered — narrow enough to accept rather than to widen the proto for.
     */
    fun unregister(surfaceId: String): Boolean {
        val surface = surfaces.remove(surfaceId) ?: return false
        surface.close()
        logger.info(LogCategory.UI, "Remote UI surface unregistered", mapOf("surfaceId" to surfaceId))
        return true
    }

    /**
     * Bind a plugin's `StreamUI` call to its surface.
     *
     * Registration first, deliberately: a stream for an unknown id is a protocol error worth reporting,
     * and accepting it would mean inventing a surface with no `surface_type`, `display_name` or slot —
     * i.e. one the host could never place.
     */
    fun openStream(surfaceId: String): SurfaceStream {
        val surface = surfaces[surfaceId]
        return when {
            surface == null -> {
                SurfaceStream.Refused("surface_id '$surfaceId' is not registered — call RegisterUI first")
            }

            !surface.claimStream() -> {
                SurfaceStream.Refused("surface_id '$surfaceId' already has an open StreamUI call")
            }

            else -> {
                SurfaceStream.Bound(surface)
            }
        }
    }

    /**
     * Release a stream that has ended, for any reason.
     *
     * This also drops the registration. A dying stream is the *only* signal the host gets that a plugin
     * process is gone — there is no `UnregisterUI` from a process that crashed — so holding the claim
     * would lock the id out and a respawned plugin could never re-register it. Any attached component
     * stays attached and simply sees `connected == false`, ready for the replacement process.
     */
    fun closeStream(surface: RemoteUiSurface) {
        surfaces.remove(surface.surfaceId, surface)
        surface.close()
        logger.info(
            LogCategory.UI,
            "Remote UI stream closed",
            mapOf("surfaceId" to surface.surfaceId, "processId" to surface.processId, "shed" to surface.shedEventCount),
        )
    }

    /**
     * Bind a host component to [surfaceId], replaying whatever the surface already holds.
     *
     * Ordered so no update can fall between the two steps: the host goes into [hosts] *first*, so a
     * surface that registers a microsecond later delivers straight to it, and the replay then happens
     * under that surface's publish lock, so it cannot hand back a tree older than one already delivered.
     */
    fun attach(
        surfaceId: String,
        host: RemoteUiSurfaceHost,
    ) {
        hosts[surfaceId] = host
        val surface = surfaces[surfaceId]
        if (surface == null) {
            host.onConnectionChanged(false)
        } else {
            surface.replayTo(host)
        }
    }

    /** Unbind a host component. Scoped to [host] so a component disposed late cannot evict its successor. */
    fun detach(
        surfaceId: String,
        host: RemoteUiSurfaceHost,
    ) {
        hosts.remove(surfaceId, host)
    }

    /**
     * Queue a user event for the plugin behind [surfaceId].
     *
     * @return `false` when there is nothing to deliver to — no registered surface, or one already closed.
     *   Callers log and move on; a click that lands during teardown is not an error condition.
     */
    fun emit(
        surfaceId: String,
        event: UIEvent,
    ): Boolean = surfaces[surfaceId]?.emit(event) == true

    /** The live surface for [surfaceId], if a plugin currently holds it. */
    fun surfaceOf(surfaceId: String): RemoteUiSurface? = surfaces[surfaceId]

    /**
     * Close and forget every surface.
     *
     * For kernel shutdown. [shared] outlives a single `KernelBootstrap`, so without this a restart would
     * come up holding claims from processes that no longer exist. Attached components are left attached
     * and simply see `connected == false` — they belong to the window, not to the kernel.
     */
    fun clear() {
        val closing = surfaces.keys.toList()
        closing.forEach { surfaceId -> surfaces.remove(surfaceId)?.close() }
        if (closing.isNotEmpty()) {
            logger.info(LogCategory.UI, "Closed all remote UI surfaces", mapOf("count" to closing.size))
        }
    }

    companion object {
        /**
         * The host-wide registry.
         *
         * One per process, because the surfaces it indexes are process-wide: the single IPC server every
         * plugin connects to is on one side and the single window's components on the other. Tests build
         * their own instances instead, which is why every collaborator takes one as a parameter rather
         * than reaching for this.
         */
        val shared = RemoteUiSurfaceRegistry()

        private val logger = BossLogger.forComponent("RemoteUiSurfaceRegistry")
    }
}
