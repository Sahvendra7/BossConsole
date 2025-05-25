package ai.rever.boss.components.plugin.tab_types.fluck

import ai.rever.boss.components.plugin.DefaultPlugin
import ai.rever.boss.components.registery.TabComponentWithUI
import ai.rever.boss.components.registery.TabInfo
import ai.rever.boss.components.registery.TabTypeId
import ai.rever.boss.components.registery.TabTypeInfo
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import com.arkivanov.decompose.ComponentContext

object Fluck: TabTypeInfo {
    override val typeId = TabTypeId("fluck")
    override val displayName = "FLUCK"
    override val icon = Icons.Outlined.Code
}

class FluckTabComponent(
    override val config: TabInfo,
    componentContext: ComponentContext
) : TabComponentWithUI, ComponentContext by componentContext {

    // In a real implementation, this would hold browser state
    private var browserContent = mutableStateOf("")

    override val tabTypeInfo = Fluck

    @Composable
    override fun Content() {
        FluckView(
            fileId = config.id,
            content = browserContent.value,
            onContentChange = { browserContent.value = it }
        )
    }
}

fun DefaultPlugin.registerFluck() = tabRegistry.registerTabType(Fluck) {
    tabInfo, ctx -> FluckTabComponent(tabInfo, ctx)
}