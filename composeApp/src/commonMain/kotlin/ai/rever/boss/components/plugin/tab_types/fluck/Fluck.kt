package ai.rever.boss.components.plugin.tab_types.fluck

import ai.rever.boss.components.plugin.DefaultPlugin
import ai.rever.boss.components.registery.TabComponentWithUI
import ai.rever.boss.components.registery.TabInfo
import ai.rever.boss.components.registery.TabTypeId
import ai.rever.boss.components.registery.TabTypeInfo
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Language
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.vector.ImageVector
import com.arkivanov.decompose.ComponentContext

object Fluck: TabTypeInfo {
    override val typeId = TabTypeId("fluck")
    override val displayName = "FLUCK"
    override val icon = Icons.Outlined.Language
}

// Mutable tab info for dynamic title updates
data class FluckTabInfo(
    override val id: String,
    override val typeId: TabTypeId,
    private var _title: String,
    override val icon: ImageVector
) : TabInfo {
    override val title: String get() = _title
    
    fun updateTitle(newTitle: String): FluckTabInfo {
        return copy(_title = newTitle)
    }
}

class FluckTabComponent(
    override val config: TabInfo,
    private val componentContext: ComponentContext,
    private val onTitleUpdate: (String) -> Unit
) : TabComponentWithUI, ComponentContext by componentContext {

    // In a real implementation, this would hold browser state
    private var browserContent = mutableStateOf("")

    override val tabTypeInfo = Fluck

    @Composable
    override fun Content() {
        FluckView(
            fileId = config.id,
            content = browserContent.value,
            onContentChange = { browserContent.value = it },
            onTitleChange = onTitleUpdate
        )
    }
}

fun DefaultPlugin.registerFluck() = tabRegistry.registerTabType(Fluck) { tabInfo, ctx ->
    // Find the tab index to update when title changes
    val parentComponent = ctx as? ai.rever.boss.components.window_panel.components.main_window_panels.BossTabsComponent
    
    FluckTabComponent(tabInfo, ctx) { newTitle ->
        // Update the tab title when the page title changes
        parentComponent?.let { parent ->
            // Find the tab by ID instead of by reference
            val tabs = parent.tabsState.value.tabs
            val tabIndex = tabs.indexOfFirst { it.id == tabInfo.id }
            
            if (tabIndex >= 0) {
                val currentTab = tabs[tabIndex]
                if (currentTab is FluckTabInfo) {
                    // Update using the current tab info, not the original one
                    parent.updateTab(tabIndex, currentTab.updateTitle(newTitle))
                }
            }
        }
    }
}