package ai.rever.boss.plugin.panel.rparecorder

import ai.rever.boss.plugin.api.Panel.Companion.right
import ai.rever.boss.plugin.api.Panel.Companion.top
import ai.rever.boss.plugin.api.PanelId
import ai.rever.boss.plugin.api.PanelInfo
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayCircle

/**
 * RPA Recorder panel info
 *
 * Priority 19 = Position in right.top.top panel
 */
object RpaRecorderInfo : PanelInfo {
    override val id = PanelId("rpa_recorder", 19)
    override val displayName = "RPA Recorder"
    override val icon = Icons.Default.PlayCircle
    override val defaultSlotPosition = right.top.top
}
