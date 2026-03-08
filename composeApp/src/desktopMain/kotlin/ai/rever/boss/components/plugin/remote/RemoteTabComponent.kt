package ai.rever.boss.components.plugin.remote

import ai.rever.boss.ui.sdk.WidgetTree
import androidx.compose.runtime.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.slf4j.LoggerFactory

/**
 * Host-side tab component that renders a remote plugin's tab UI.
 *
 * Same pattern as [RemotePanelComponent] but for tab-type surfaces.
 * Phase 7: Used for out-of-process plugin tab rendering.
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

    val title: State<String> get() = _title
    val isLoading: State<Boolean> get() = _isLoading

    /**
     * Compose content for this remote tab.
     */
    @Composable
    fun Content() {
        val tree by _widgetTree
        tree?.let { widgetTree ->
            RemoteWidgetRenderer(
                tree = widgetTree,
                onEvent = { nodeId, eventType, eventData ->
                    logger.debug(
                        "Tab UI event: tab={}, node={}, type={}, data={}",
                        tabId, nodeId, eventType, eventData
                    )
                    // Future: forward event back to plugin process via gRPC
                }
            )
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
        logger.info("Remote tab disposed: tabId={}", tabId)
    }
}
