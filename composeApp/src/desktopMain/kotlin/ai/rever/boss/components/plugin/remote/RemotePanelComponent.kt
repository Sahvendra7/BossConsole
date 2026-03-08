package ai.rever.boss.components.plugin.remote

import ai.rever.boss.ui.sdk.WidgetTree
import androidx.compose.runtime.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.slf4j.LoggerFactory

/**
 * Host-side panel component that renders a remote plugin's UI.
 *
 * Connects to the plugin process via gRPC [PluginUIService], collects
 * widget tree updates, and re-renders using [RemoteWidgetRenderer].
 *
 * Phase 7: Used when plugins run out-of-process with isolationMode="out-of-process".
 */
class RemotePanelComponent(
    val panelId: String,
    val displayName: String,
    private val processId: String,
    private val uiAddress: String,
) {
    private val logger = LoggerFactory.getLogger(RemotePanelComponent::class.java)
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _widgetTree = mutableStateOf<WidgetTree?>(null)

    /**
     * Compose content for this remote panel.
     */
    @Composable
    fun Content() {
        val tree by _widgetTree
        tree?.let { widgetTree ->
            RemoteWidgetRenderer(
                tree = widgetTree,
                onEvent = { nodeId, eventType, eventData ->
                    logger.debug(
                        "Panel UI event: panel={}, node={}, type={}, data={}",
                        panelId, nodeId, eventType, eventData
                    )
                    // Future: forward event back to plugin process via gRPC
                }
            )
        }
    }

    /**
     * Connect to the plugin process and start streaming widget updates.
     * Call this when the panel is first displayed.
     */
    fun connect() {
        logger.info("Connecting to remote panel: panelId={}, process={}, address={}", panelId, processId, uiAddress)
        // Widget tree streaming — Phase 7 wiring (full PluginUIService gRPC connection)
        // For now, the widget tree is set externally via [updateTree]
    }

    /**
     * Update the displayed widget tree (called from IPC handler).
     */
    fun updateTree(tree: WidgetTree) {
        _widgetTree.value = tree
    }

    fun dispose() {
        scope.cancel()
        logger.info("Remote panel disposed: panelId={}", panelId)
    }
}
