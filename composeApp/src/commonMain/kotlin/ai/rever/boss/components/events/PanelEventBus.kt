package ai.rever.boss.components.events

import ai.rever.boss.components.registery.PanelId
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class PanelCloseEvent(
    val panelId: PanelId
)

data class PanelOpenEvent(
    val panelId: PanelId
)

object PanelEventBus {
    private val _panelCloseEvents = MutableSharedFlow<PanelCloseEvent>()
    val panelCloseEvents: SharedFlow<PanelCloseEvent> = _panelCloseEvents.asSharedFlow()

    private val _panelOpenEvents = MutableSharedFlow<PanelOpenEvent>(
        replay = 1,  // Keep last event for late collectors (fixes race with app startup)
        extraBufferCapacity = 10
    )
    val panelOpenEvents: SharedFlow<PanelOpenEvent> = _panelOpenEvents.asSharedFlow()

    suspend fun closePanel(panelId: PanelId) {
        _panelCloseEvents.emit(PanelCloseEvent(panelId))
    }

    suspend fun openPanel(panelId: PanelId) {
        _panelOpenEvents.emit(PanelOpenEvent(panelId))
    }
}
