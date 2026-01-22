package ai.rever.boss.components.events

import ai.rever.boss.components.registery.PanelId
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Event emitted when a panel should be closed.
 * @property panelId The panel to close
 * @property sourceWindowId The window that initiated this event (required for multi-window support)
 */
data class PanelCloseEvent(
    val panelId: PanelId,
    val sourceWindowId: String
)

/**
 * Event emitted when a panel should be opened.
 * @property panelId The panel to open
 * @property sourceWindowId The window that initiated this event (required for multi-window support)
 */
data class PanelOpenEvent(
    val panelId: PanelId,
    val sourceWindowId: String
)

/**
 * Event emitted when a panel should be toggled.
 * @property panelId The panel to toggle
 * @property sourceWindowId The window that initiated this event (required for multi-window support)
 */
data class PanelToggleEvent(
    val panelId: PanelId,
    val sourceWindowId: String
)

object PanelEventBus {
    private val _panelCloseEvents = MutableSharedFlow<PanelCloseEvent>()
    val panelCloseEvents: SharedFlow<PanelCloseEvent> = _panelCloseEvents.asSharedFlow()

    private val _panelOpenEvents = MutableSharedFlow<PanelOpenEvent>(
        replay = 1,  // Keep last event for late collectors (fixes race with app startup)
        extraBufferCapacity = 10
    )
    val panelOpenEvents: SharedFlow<PanelOpenEvent> = _panelOpenEvents.asSharedFlow()

    private val _panelToggleEvents = MutableSharedFlow<PanelToggleEvent>(
        extraBufferCapacity = 10
    )
    val panelToggleEvents: SharedFlow<PanelToggleEvent> = _panelToggleEvents.asSharedFlow()

    suspend fun closePanel(panelId: PanelId, sourceWindowId: String) {
        _panelCloseEvents.emit(PanelCloseEvent(panelId, sourceWindowId))
    }

    suspend fun openPanel(panelId: PanelId, sourceWindowId: String) {
        _panelOpenEvents.emit(PanelOpenEvent(panelId, sourceWindowId))
    }

    suspend fun togglePanel(panelId: PanelId, sourceWindowId: String) {
        _panelToggleEvents.emit(PanelToggleEvent(panelId, sourceWindowId))
    }
}
