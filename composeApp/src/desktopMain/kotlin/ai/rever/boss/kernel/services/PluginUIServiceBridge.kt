package ai.rever.boss.kernel.services

import ai.rever.boss.ipc.proto.Empty
import ai.rever.boss.ipc.proto.PluginUIServiceGrpcKt
import ai.rever.boss.ipc.proto.UIEvent
import ai.rever.boss.ipc.proto.UIRegistration
import ai.rever.boss.ipc.proto.UIRegistrationResponse
import ai.rever.boss.ipc.proto.UIUnregistration
import ai.rever.boss.ipc.proto.WidgetUpdate
import ai.rever.boss.kernel.ui.RemoteUiSurface
import ai.rever.boss.kernel.ui.RemoteUiSurfaceDescriptor
import ai.rever.boss.kernel.ui.RemoteUiSurfaceRegistry
import ai.rever.boss.kernel.ui.SurfaceRegistration
import ai.rever.boss.kernel.ui.SurfaceStream
import ai.rever.boss.plugin.logging.BossLogger
import ai.rever.boss.plugin.logging.LogCategory
import ai.rever.boss.ui.sdk.WidgetProtoConverter.toKotlin
import io.grpc.Status
import io.grpc.StatusException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicReference

/**
 * Kernel-side implementation of `PluginUIService` — the transport that makes out-of-process plugin UI
 * actually work in both directions.
 *
 * **The host used to have this backwards.** `ui_protocol.proto` makes the plugin the client (it streams
 * `WidgetUpdate`s) and the kernel the server (it streams `UIEvent`s back), but `RemotePanelComponent`
 * and `RemoteTabComponent` each opened a `PluginUIServiceCoroutineStub` and dialled *out* to the plugin.
 * Because the request stream of `StreamUI` is typed `WidgetUpdate`, every outgoing `UIEvent` had to be
 * repacked as a `WidgetUpdate` — a message with no room for an event — so what crossed the wire was a
 * `surface_id` and nothing else. Inbound `UIEvent`s, arriving on the response stream where the host was
 * pretending to be a plugin, were logged at debug and discarded. No click, keystroke or selection could
 * reach a plugin, and no plugin-pushed tree could reach the host: the whole path was decorative.
 *
 * This class is the correct half of that inversion, and [RemoteUiSurfaceRegistry] is where it meets the
 * components. Nothing in the host dials a plugin's UI service any more.
 *
 * @param registry the surface directory to route through. Defaults to the host-wide one; tests pass
 *   their own so two suites cannot see each other's surfaces.
 */
class PluginUIServiceBridge(
    private val registry: RemoteUiSurfaceRegistry = RemoteUiSurfaceRegistry.shared,
) : PluginUIServiceGrpcKt.PluginUIServiceCoroutineImplBase() {
    override suspend fun registerUI(request: UIRegistration): UIRegistrationResponse =
        when (val outcome = registry.register(request.surfaceId, request.processId, request.descriptor())) {
            is SurfaceRegistration.Rejected -> {
                logger.warn(
                    LogCategory.UI,
                    "Refused a UI surface registration",
                    mapOf("surfaceId" to request.surfaceId, "reason" to outcome.reason),
                )
                registrationResponse(success = false, error = outcome.reason)
            }

            is SurfaceRegistration.Accepted -> {
                // Applied before the stream exists so a surface renders from the moment it is opened,
                // rather than staying blank until the plugin's first update.
                if (request.hasInitialTree()) {
                    outcome.surface.pushTree(request.initialTree.toKotlin())
                }
                registrationResponse(success = true, error = "")
            }
        }

    /**
     * Bidirectional stream: inbound `WidgetUpdate`s are routed to their surface, outbound `UIEvent`s are
     * drained from that surface's ordered queue.
     *
     * The two halves are deliberately independent. A plugin that has sent its tree and has nothing more
     * to say may half-close its request stream and keep listening for events forever — so the response
     * flow outlives the request flow and ends only when the surface closes or the plugin goes away.
     *
     * `StreamUI` carries no surface id of its own, so the stream is bound by the `surface_id` of its
     * first `WidgetUpdate` and pinned to it. That is a limitation of the protocol, not a choice: a
     * plugin that registers a surface and then streams nothing cannot be matched to it, and gets
     * `INVALID_ARGUMENT` when its request stream ends rather than an RPC that hangs open.
     */
    override fun streamUI(requests: Flow<WidgetUpdate>): Flow<UIEvent> =
        channelFlow {
            val bound = CompletableDeferred<RemoteUiSurface>()
            // Written by the pump the instant it claims, and read by the finally below. The claim happens
            // inside the pump, so a `finally` guarding only the collect would miss it: an RPC cancelled
            // between `openStream()` returning Bound and this coroutine resuming from `await()` would
            // leave the surface in the registry with `streaming == true` forever — the component reading
            // *connected* for a dead plugin, and every respawn's RegisterUI refused for the rest of the
            // session, because the reclaim rule only takes over claims that are not streaming. That is
            // precisely the frozen-not-disconnected state this transport exists to avoid, and it is the
            // likeliest crash: a plugin dying right after its first WidgetUpdate.
            val claimed = AtomicReference<RemoteUiSurface>(null)
            val pump = launch { pumpUpdates(requests, bound, claimed) }
            try {
                // Throws the StatusException the pump resolved this with when the id is unusable.
                val surface = bound.await()
                // One collector for one queue: this is the hop that turns interaction order into wire
                // order, so it must stay single — see RemoteUiSurface.claimStream.
                surface.events().collect { event -> send(event) }
            } finally {
                // channelFlow does not complete until its children do, and the pump is collecting a
                // request stream the plugin may keep open indefinitely. Without this cancel, closing a
                // surface completed the event queue but left the RPC hanging: the plugin's response flow
                // never ended, so `UnregisterUI` looked like it had silently frozen the stream. The
                // surface is gone by now, so there is nothing left for the pump to route.
                //
                // cancelAndJoin, not cancel: cancel() only *requests* it and returns, so the read below
                // could race the pump sitting between `openStream()` (which has already set
                // streaming = true) and its write to `claimed` — two adjacent statements with no
                // suspension point between them, executing on a different thread, since grpc-kotlin
                // builds its RPC scope from EmptyCoroutineContext and an RPC cancellation cancels both
                // coroutines at once. Losing that read would strand the claim exactly as above.
                // NonCancellable because arriving here *via* cancellation is the common case.
                //
                // Joining is also why this cannot be pump.invokeOnCompletion: the pump completes when a
                // plugin merely half-closes its request stream, which is legal and must not tear the
                // surface down.
                withContext(NonCancellable) { pump.cancelAndJoin() }
                // Safe to reach twice — closeStream removes by identity and close() is idempotent.
                claimed.get()?.let(registry::closeStream)
            }
        }

    override suspend fun unregisterUI(request: UIUnregistration): Empty {
        val removed = registry.unregister(request.surfaceId)
        if (!removed) {
            logger.debug(
                LogCategory.UI,
                "Ignoring UnregisterUI for an unknown surface",
                mapOf("surfaceId" to request.surfaceId),
            )
        }
        return Empty.getDefaultInstance()
    }

    /**
     * Drain the plugin's update stream into its surface.
     *
     * Never fails the call by throwing: a broken request stream means the transport is already gone, and
     * letting that escape would race the response flow's own teardown for which error the plugin sees.
     * Binding problems are reported through [bound] instead, so exactly one status describes them.
     */
    private suspend fun pumpUpdates(
        requests: Flow<WidgetUpdate>,
        bound: CompletableDeferred<RemoteUiSurface>,
        claimed: AtomicReference<RemoteUiSurface>,
    ) {
        var surface: RemoteUiSurface? = null
        var refused = false
        var broken = false
        requests
            .onEach { update ->
                val target = surface
                when {
                    target != null && target.surfaceId == update.surfaceId -> {
                        target.applyUpdate(update)
                    }

                    target != null -> {
                        logger.warn(
                            LogCategory.UI,
                            "Ignoring a WidgetUpdate for a surface this stream is not bound to — " +
                                "use one StreamUI call per surface",
                            mapOf("streamSurfaceId" to target.surfaceId, "updateSurfaceId" to update.surfaceId),
                        )
                    }

                    !refused -> {
                        when (val opened = registry.openStream(update.surfaceId)) {
                            is SurfaceStream.Bound -> {
                                surface = opened.surface
                                // Recorded before anything can suspend, so the claim is never held by a
                                // coroutine that no longer has a way to release it.
                                claimed.set(opened.surface)
                                bound.complete(opened.surface)
                                opened.surface.applyUpdate(update)
                            }

                            is SurfaceStream.Refused -> {
                                refused = true
                                bound.completeExceptionally(
                                    StatusException(Status.FAILED_PRECONDITION.withDescription(opened.reason)),
                                )
                            }
                        }
                    }

                    else -> {
                        Unit
                    }
                }
            }.catch { cause ->
                broken = true
                logger.warn(
                    LogCategory.UI,
                    "Plugin widget-update stream ended abnormally — closing the surface",
                    mapOf("surfaceId" to surface?.surfaceId, "error" to cause.message),
                )
                // Whatever the reason — a dead wire, or a failure applying an update — this pump has
                // stopped reading the request stream, and the call cannot usefully continue. Leaving the
                // response side running would keep the surface claimed and reading *connected* while the
                // plugin's next send blocked on flow control forever. Closing the surface completes the
                // event queue, which completes the response flow, which ends the RPC.
                surface?.let(registry::closeStream)
            }.onCompletion { cause ->
                // An unbindable stream is one that ended of its own accord without ever naming a surface.
                // A cancellation is not that, and neither is a broken wire — `catch` above swallows the
                // cause to keep one status on the call, so `broken` is what distinguishes them here.
                val endedCleanly = cause == null && !broken
                val neverBound = surface == null && !refused
                if (endedCleanly && neverBound) {
                    bound.completeExceptionally(
                        StatusException(Status.INVALID_ARGUMENT.withDescription(UNIDENTIFIED_STREAM)),
                    )
                }
            }.collect()
    }

    /**
     * Everything a registration declares beyond identity.
     *
     * Kept on the surface for the follow-up that places it in the window, which is what needs
     * `surface_type` to choose panel vs tab and `default_slot` to position a panel.
     */
    private fun UIRegistration.descriptor(): RemoteUiSurfaceDescriptor =
        RemoteUiSurfaceDescriptor(
            surfaceType = surfaceType,
            displayName = displayName,
            iconName = iconName,
            defaultSlot = defaultSlot,
        )

    private fun registrationResponse(
        success: Boolean,
        error: String,
    ): UIRegistrationResponse =
        UIRegistrationResponse
            .newBuilder()
            .setSuccess(success)
            .setErrorMessage(error)
            .build()

    private companion object {
        val logger = BossLogger.forComponent("PluginUIServiceBridge")

        const val UNIDENTIFIED_STREAM =
            "StreamUI takes its surface identity from the surface_id of its first WidgetUpdate; " +
                "the request stream ended without sending one"
    }
}
