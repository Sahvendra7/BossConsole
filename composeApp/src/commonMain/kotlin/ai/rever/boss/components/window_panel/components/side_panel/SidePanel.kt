package ai.rever.boss.components.window_panel.components.side_panel

import BossDarkBackground
import BossDarkBorder
import ai.rever.boss.components.model.BossDraggableComponent
import ai.rever.boss.components.model.Panel
import ai.rever.boss.components.registery.PanelComponentStore
import ai.rever.boss.components.window_panel.components.BossPanelTopBar
import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Divider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

@Composable
fun BossDraggableComponent.SidePanel(
    panel: Panel,
    panelComponentStore: PanelComponentStore
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val pluginContentId = getPanelContentId(panel)
    val component = pluginContentId?.let { panelComponentStore.getOrCreateComponent(it) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BossDarkBackground)
            .hoverable(interactionSource)
    ) {
        val title = component?.panelInfo?.displayName ?: "Default title"//getPanelTitle(panel)

        BossPanelTopBar(
            title = title,
            isHovered = isHovered,
            onReset = pluginContentId?.let { panelId ->
                {
                    // Trigger component reset via PanelComponentStore
                    panelComponentStore.resetComponent(panelId)
                }
            },
            onMinimize = {
                setPanelVisible(panel, false)
            }
        )
        Divider(color = BossDarkBorder)

        Box(modifier = Modifier.fillMaxSize()) {
            // Force recomposition when component instance changes (e.g., after reset)
            // This ensures the UI fully refreshes instead of showing stale state
            key(component) {
                component?.Content()
            }
        }
    }
}
