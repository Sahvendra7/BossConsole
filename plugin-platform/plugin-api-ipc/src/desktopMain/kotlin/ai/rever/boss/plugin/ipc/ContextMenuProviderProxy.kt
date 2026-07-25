package ai.rever.boss.plugin.ipc

import ai.rever.boss.ipc.proto.services.*
import ai.rever.boss.plugin.api.ContextMenuProvider
import ai.rever.boss.plugin.ui.ContextMenuItemData
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.grpc.ManagedChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * IPC proxy implementation of ContextMenuProvider — **context menus do not work
 * out-of-process** (BossConsole issue #30).
 *
 * Context menus require Compose modifier access, which cannot be serialized over gRPC.
 * The intent was for this proxy to register menu descriptors and for the kernel to attach
 * them to the matching UI node while rendering. The kernel never did: its
 * `ContextMenuServiceBridge` acknowledges and discards every registration, a rendered
 * widget node has no context-menu attachment point, and there is no kernel -> plugin
 * event that could deliver a picked action back to [ContextMenuItemData.onClick].
 *
 * So [applyContextMenu] returns the modifier untouched and the right-click does nothing.
 * The registration RPC is still sent — harmlessly — so that a real consumer shows up in
 * the kernel's debug log; the KDoc, not the wire, was the bug.
 *
 * Plugins that need a context menu must run in-process, where the host
 * `ContextMenuProvider` decorates a real modifier.
 */
class ContextMenuProviderProxy(
    channel: ManagedChannel,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob()),
) : ContextMenuProvider {
    private val stub = ContextMenuServiceGrpcKt.ContextMenuServiceCoroutineStub(channel)

    @Composable
    override fun applyContextMenu(
        modifier: Modifier,
        items: List<ContextMenuItemData>,
    ): Modifier {
        // Announce the items to the kernel, which currently discards them (see KDoc).
        // Nothing is applied to the returned modifier — there is no menu.
        val contextMenuId = "ctx_${items.hashCode()}"
        scope.launch {
            try {
                val protoItems =
                    items.map { item ->
                        ContextMenuItemProto
                            .newBuilder()
                            .setLabel(item.label)
                            .setActionId(item.label) // Use label as action ID
                            .build()
                    }
                stub.registerContextMenu(
                    RegisterContextMenuRequest
                        .newBuilder()
                        .setContextMenuId(contextMenuId)
                        .addAllItems(protoItems)
                        .build(),
                )
            } catch (_: Exception) {
            }
        }
        return modifier
    }
}
