package ai.rever.boss.kernel.services

import ai.rever.boss.ipc.proto.Empty
import ai.rever.boss.ipc.proto.services.*
import org.slf4j.LoggerFactory

/**
 * Kernel-side stub for `ContextMenuService` — **intentionally passive**.
 *
 * Every RPC acknowledges and drops its request. The bridge holds no state, so
 * there is deliberately nothing to read back: an out-of-process plugin's context
 * menu items are *not* rendered by the host. The service is still registered so
 * that a plugin calling an RPC the IPC contract advertises gets `OK` rather than
 * `UNIMPLEMENTED`.
 *
 * This class used to claim it rendered plugin menus "using the host's native
 * context menu system" and forwarded action callbacks. It never did (issue #30),
 * and it cannot without a protocol change:
 *
 * - `ContextMenuProvider.applyContextMenu(Modifier, items)` — the host API this
 *   would have to call — is a `@Composable` modifier decorator. A gRPC handler has
 *   no composition and no UI node modifier to decorate, so the provider is simply
 *   not callable from here. (#23 removed the unused `provider` constructor
 *   parameter that implied otherwise.)
 * - Out-of-process plugin UI reaches the host as a serialized `WidgetTree` rendered
 *   by `RemoteWidgetRenderer`. `WidgetModifier` models only `clickable` /
 *   `clickEventId`; a widget node has no context-menu attachment point, and
 *   `ContextMenuProviderProxy` never populates the `node_id` the proto reserves for
 *   one.
 * - A chosen action has no way home. `ContextMenuItemData.onClick` is an in-process
 *   lambda; over IPC only labels and action ids survive. `OnContextMenuAction` is a
 *   *plugin → kernel* unary RPC, so despite its proto comment it cannot notify a
 *   plugin, and `UIEvent` — the real kernel → plugin channel — has no context-menu
 *   variant.
 *
 * Wiring this for real is therefore a change to the UI protocol, not to this class:
 * a context-menu descriptor on `WidgetModifier`, matching support in
 * `RemoteWidgetRenderer`, and a context-menu `UIEvent` to deliver the picked action
 * back to the plugin process.
 *
 * Nothing calls this service today either: `ContextMenuProviderProxy` is
 * constructed only by `RemotePluginContext.contextMenuProvider`, which no state
 * holder in `boss-microkernel-runtime` reads. In-process plugins are unaffected —
 * they hold the host `ContextMenuProvider` directly and never touch IPC.
 */
class ContextMenuServiceBridge : ContextMenuServiceGrpcKt.ContextMenuServiceCoroutineImplBase() {
    override suspend fun registerContextMenu(request: RegisterContextMenuRequest): Empty {
        logger.debug(
            "Dropping context menu registration (host does not render OOP context menus, issue #30): " +
                "id={}, node={}, items={}",
            request.contextMenuId,
            request.nodeId,
            request.itemsCount,
        )
        return Empty.getDefaultInstance()
    }

    override suspend fun unregisterContextMenu(request: ContextMenuIdRequest): Empty {
        logger.debug("Ignoring context menu unregistration (nothing was registered): id={}", request.contextMenuId)
        return Empty.getDefaultInstance()
    }

    override suspend fun onContextMenuAction(request: ContextMenuActionRequest): Empty {
        // The host never shows these menus, so it never triggers an action. If this
        // ever fires, a plugin is calling the RPC by hand — see the class KDoc for
        // why the kernel cannot route it anywhere.
        logger.debug(
            "Ignoring context menu action (host renders no OOP context menus): id={}, action={}",
            request.contextMenuId,
            request.actionId,
        )
        return Empty.getDefaultInstance()
    }

    private companion object {
        private val logger = LoggerFactory.getLogger(ContextMenuServiceBridge::class.java)
    }
}
