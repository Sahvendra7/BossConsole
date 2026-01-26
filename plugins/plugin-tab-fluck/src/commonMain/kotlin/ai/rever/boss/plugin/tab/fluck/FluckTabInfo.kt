package ai.rever.boss.plugin.tab.fluck

import ai.rever.boss.plugin.api.TabTypeId
import ai.rever.boss.plugin.api.TabTypeInfo
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Language

/**
 * Fluck (browser) tab type info
 */
object FluckTabType : TabTypeInfo {
    override val typeId = TabTypeId("fluck")
    override val displayName = "FLUCK"
    override val icon = Icons.Outlined.Language
}
