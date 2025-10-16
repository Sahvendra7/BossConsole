package ai.rever.boss.components.registery

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import com.arkivanov.decompose.ComponentContext

data class TabTypeId(
    val typeId: String,
    val pluginId: String = "ai.rever.boss"
)

// Wrapper for tab icons that can be either vector or bitmap
sealed class TabIcon {
    data class Vector(val imageVector: ImageVector) : TabIcon()
    data class Image(val painter: Painter) : TabIcon()
    
    @Composable
    fun asPainter(): Painter = when (this) {
        is Vector -> rememberVectorPainter(imageVector)
        is Image -> painter
    }
}

interface TabInfo {
    val id: String
    val typeId: TabTypeId
    val title: String
    val icon: ImageVector // Keep for backward compatibility
    val tabIcon: TabIcon? // New flexible icon
        get() = null // Default implementation
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
