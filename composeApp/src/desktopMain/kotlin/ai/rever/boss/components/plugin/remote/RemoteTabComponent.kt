package ai.rever.boss.components.plugin.remote

import ai.rever.boss.ipc.proto.PluginUIServiceGrpcKt
import ai.rever.boss.ipc.proto.UIEvent
import ai.rever.boss.ui.sdk.UIEventMapper
import ai.rever.boss.ui.sdk.WidgetEvent
import ai.rever.boss.ui.sdk.WidgetTree
import androidx.compose.runtime.*
import io.grpc.ManagedChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

/**
 * Host-side tab component that renders a remote plugin's tab UI.
 *
 * Same pattern as [RemotePanelComponent] but for tab-type surfaces,
 * with additional title and loading state management.
 */
class RemoteTabComponent(
    val tabId: String,
    val displayName: String,
    private val processId: String,
    private val uiAddress: String,
) {
    private val logger = LoggerFactory.getLogger(RemoteTabComponent::class.java)
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _widgetTree = mutableStateOf<WidgetTree?>(null)
    private val _title = mutableStateOf(displayName)
    private val _isLoading = mutableStateOf(false)
    private val _connected = mutableStateOf(false)

    /** Outgoing UI events from kernel to plugin process. */
    private val outgoingEvents = MutableSharedFlow<UIEvent>(extraBufferCapacity = 256)

    val title: State<String> get() = _title
    val isLoading: State<Boolean> get() = _isLoading
    val connected: State<Boolean> get() = _connected

    /**
     * Compose content for this remote tab.
     */
    @Composable
    fun Content() {
        val tree by _widgetTree
        tree?.let { widgetTree ->
            RemoteWidgetRenderer(
                tree = widgetTree,
                onEvent = { nodeId, event ->
                    // Payloads can contain what the user typed — log the shape, not the content.
                    logger.debug(
                        "Tab UI event: tab={}, node={}, type={}",
                        tabId,
                        nodeId,
                        event::class.simpleName,
                    )
                    scope.launch {
                        sendUIEvent(nodeId, event)
                    }
                },
            )
        }
    }

    /**
     * Connect to the plugin process and start streaming widget updates.
     */
    fun connect() {
        logger.info("Connecting to remote tab: tabId={}, process={}, address={}", tabId, processId, uiAddress)
        scope.launch {
            connectToPluginProcess()
        }
    }

    /**
     * Connect using a pre-existing gRPC channel.
     */
    fun connect(channel: ManagedChannel) {
        logger.info("Connecting to remote tab via channel: tabId={}, process={}", tabId, processId)
        scope.launch {
            connectWithChannel(channel)
        }
    }

    /**
     * Update the displayed widget tree (called from IPC handler).
     */
    fun updateTree(tree: WidgetTree) {
        _widgetTree.value = tree
    }

    fun updateTitle(title: String) {
        _title.value = title
    }

    fun setLoading(loading: Boolean) {
        _isLoading.value = loading
    }

    fun dispose() {
        scope.cancel()
        _connected.value = false
        logger.info("Remote tab disposed: tabId={}", tabId)
    }

    // ---- Internal ----

    private suspend fun connectToPluginProcess() {
        try {
            val client =
                ai.rever.boss.ipc
                    .BossIpcClient(uiAddress)
            connectWithChannel(client.channel)
        } catch (e: Exception) {
            logger.error("Failed to connect to plugin process: tabId={}", tabId, e)
            _connected.value = false
        }
    }

    private suspend fun connectWithChannel(channel: ManagedChannel) {
        val stub = PluginUIServiceGrpcKt.PluginUIServiceCoroutineStub(channel)

        try {
            _connected.value = true

            val widgetUpdateStream =
                channelFlow {
                    outgoingEvents.collect { event ->
                        val update =
                            ai.rever.boss.ipc.proto.WidgetUpdate
                                .newBuilder()
                                .setSurfaceId(event.surfaceId)
                                .build()
                        send(update)
                    }
                }

            stub.streamUI(widgetUpdateStream).collect { uiEvent ->
                logger.debug("Received UI event from plugin tab: surface={}", uiEvent.surfaceId)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            _connected.value = false
            logger.warn("Connection to plugin tab lost: tabId={}, error={}", tabId, e.message)
        }
    }

    /**
     * Forward one interaction to the plugin process, mapped by [UIEventMapper] — a total mapping over
     * the sealed [WidgetEvent] type, so no proto oneof case can be silently skipped.
     */
    private suspend fun sendUIEvent(
        nodeId: String,
        event: WidgetEvent,
    ) {
        outgoingEvents.emit(UIEventMapper.toProto(tabId, nodeId, event, System.currentTimeMillis()))
    }
}
