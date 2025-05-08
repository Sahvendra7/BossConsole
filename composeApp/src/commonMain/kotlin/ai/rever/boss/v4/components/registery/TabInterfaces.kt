package ai.rever.boss.v4.components.registery

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import com.arkivanov.decompose.ComponentContext

data class TabTypeId(
    val typeId: String,
    val pluginId: String = "ai.rever.boss"
)

interface TabInfo {
    val id: String
    val typeId: TabTypeId
    val title: String
    val icon: ImageVector
}

interface TabTypeInfo {
    val typeId: TabTypeId
    val displayName: String
    val icon: ImageVector
}

interface TabComponentWithUI: ComponentContext {
    val tabTypeInfo: TabTypeInfo
    val config: TabInfo

    @Composable
    fun Content()
}