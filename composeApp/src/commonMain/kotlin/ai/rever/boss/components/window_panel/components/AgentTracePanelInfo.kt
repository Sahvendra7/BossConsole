package ai.rever.boss.components.window_panel.components

import ai.rever.boss.components.plugin.PanelIds
import ai.rever.boss.plugin.api.PanelInfo
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search

val AgentTracePanelInfo = PanelInfo(
    id = PanelIds.AGENT_TRACE,
    title = "Agent Trace",
    icon = Icons.Outlined.Search,
    initialRegion = ai.rever.boss.plugin.api.PanelRegion.BOTTOM
)
