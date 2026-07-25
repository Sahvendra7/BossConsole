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
     * A duplicate id is refused rather than silently taken over: two plugins rendering into one surface
     * would interleave trees, and the second one's events would be delivered to the first's stream.
     */
    fun register(
        surfaceId: String,
        processId: String,
    ): SurfaceRegistration {
        if (surfaceId.isBlank()) {
            return SurfaceRegistration.Rejected("surface_id is required")
        }
        val created =
            RemoteUiSurface(
                surfaceId = surfaceId,
                processId = processId,
                publishTree = { tree -> hosts[surfaceId]?.onTreeUpdated(tree) },
                publishConnected = { connected -> hosts[surfaceId]?.onConnectionChanged(connected) },
            )
        val existing = surfaces.putIfAbsent(surfaceId, created)
        return if (existing != null) {
            SurfaceRegistration.Rejected(
                "surface_id '$surfaceId' is already registered by process '${existing.processId}'",
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

    /** Tear a surface down at the plugin's request. @return `false` if it was not registered. */
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

    /** Bind a host component to [surfaceId], replaying whatever the surface already holds. */
    fun attach(
        surfaceId: String,
        host: RemoteUiSurfaceHost,
    ) {
        hosts[surfaceId] = host
        val surface = surfaces[surfaceId]
        host.onConnectionChanged(surface?.streaming == true)
        surface?.tree?.let(host::onTreeUpdated)
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
