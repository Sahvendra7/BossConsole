package ai.rever.boss.window

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Handler for menu actions that need to be processed by BossApp.
 *
 * This provides a communication bridge between the MenuBar (in BossWindow)
 * and the app logic (in BossApp) which are at different levels in the
 * composition tree.
 *
 * Similar to WindowOperations, this uses a flow-based event system to
 * decouple the menu UI from the business logic.
 */
object MenuActionsHandler {
    private val _newTabEvents = MutableSharedFlow<String>(extraBufferCapacity = 10)
    val newTabEvents: SharedFlow<String> = _newTabEvents.asSharedFlow()

    private val _closeTabEvents = MutableSharedFlow<String>(extraBufferCapacity = 10)
    val closeTabEvents: SharedFlow<String> = _closeTabEvents.asSharedFlow()

    private val _zoomInEvents = MutableSharedFlow<String>(extraBufferCapacity = 10)
    val zoomInEvents: SharedFlow<String> = _zoomInEvents.asSharedFlow()

    private val _zoomOutEvents = MutableSharedFlow<String>(extraBufferCapacity = 10)
    val zoomOutEvents: SharedFlow<String> = _zoomOutEvents.asSharedFlow()

    private val _actualSizeEvents = MutableSharedFlow<String>(extraBufferCapacity = 10)
    val actualSizeEvents: SharedFlow<String> = _actualSizeEvents.asSharedFlow()

    /**
     * Trigger a "New Tab" action for the specified window.
     *
     * @param windowId The ID of the window where the action was triggered
     */
    fun triggerNewTab(windowId: String) {
        _newTabEvents.tryEmit(windowId)
    }

    /**
     * Trigger a "Close Tab" action for the specified window.
     *
     * @param windowId The ID of the window where the action was triggered
     */
    fun triggerCloseTab(windowId: String) {
        _closeTabEvents.tryEmit(windowId)
    }

    /**
     * Trigger a "Zoom In" action for the specified window.
     *
     * @param windowId The ID of the window where the action was triggered
     */
    fun triggerZoomIn(windowId: String) {
        _zoomInEvents.tryEmit(windowId)
    }

    /**
     * Trigger a "Zoom Out" action for the specified window.
     *
     * @param windowId The ID of the window where the action was triggered
     */
    fun triggerZoomOut(windowId: String) {
        _zoomOutEvents.tryEmit(windowId)
    }

    /**
     * Trigger an "Actual Size" action for the specified window.
     *
     * @param windowId The ID of the window where the action was triggered
     */
    fun triggerActualSize(windowId: String) {
        _actualSizeEvents.tryEmit(windowId)
    }
}
