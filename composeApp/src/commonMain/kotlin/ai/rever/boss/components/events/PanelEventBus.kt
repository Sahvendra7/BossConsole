package ai.rever.boss.components.events

import ai.rever.boss.components.registery.PanelId
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class PanelCloseEvent(
    val panelId: PanelId
)

object PanelEventBus {
    private val _panelCloseEvents = MutableSharedFlow<PanelCloseEvent>()
    val panelCloseEvents: SharedFlow<PanelCloseEvent> = _panelCloseEvents.asSharedFlow()
    
    suspend fun closePanel(panelId: PanelId) {
        _panelCloseEvents.emit(PanelCloseEvent(panelId))
    }
}
