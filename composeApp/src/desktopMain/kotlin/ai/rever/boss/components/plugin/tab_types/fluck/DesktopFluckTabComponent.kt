package ai.rever.boss.components.plugin.tab_types.fluck

import ai.rever.boss.components.registery.TabInfo
import ai.rever.boss.components.registery.TabIcon
import androidx.compose.ui.graphics.vector.ImageVector
import com.arkivanov.decompose.ComponentContext
import com.teamdev.jxbrowser.browser.Browser

actual fun createFluckTabComponent(
    config: TabInfo,
    componentContext: ComponentContext,
    onTitleUpdate: (String) -> Unit,
    onIconUpdate: (ImageVector) -> Unit,
    onTabIconUpdate: (TabIcon) -> Unit,
    onOpenInNewTab: (String) -> Unit,
    onNavigationUpdate: ((String, String) -> Unit)?
): FluckTabComponent {
    return DesktopFluckTabComponent(
        config = config,
        componentContext = componentContext,
        onTitleUpdate = onTitleUpdate,
        onIconUpdate = onIconUpdate,
        onTabIconUpdate = onTabIconUpdate,
        onOpenInNewTab = onOpenInNewTab,
        onNavigationUpdate = onNavigationUpdate
    )
}

class DesktopFluckTabComponent(
    config: TabInfo,
    componentContext: ComponentContext,
    onTitleUpdate: (String) -> Unit,
    onIconUpdate: (ImageVector) -> Unit,
    onTabIconUpdate: (TabIcon) -> Unit,
    onOpenInNewTab: (String) -> Unit,
    onNavigationUpdate: ((String, String) -> Unit)? = null
) : FluckTabComponent(
    config = config,
    componentContext = componentContext,
    onTitleUpdate = onTitleUpdate,
    onIconUpdate = onIconUpdate,
    onTabIconUpdate = onTabIconUpdate,
    onOpenInNewTab = onOpenInNewTab,
    onNavigationUpdate = onNavigationUpdate
) {
    
    override fun reload() {
        // Cast browser to JxBrowser type and reload
        val jxBrowser = browser as? Browser
        if (jxBrowser != null && !jxBrowser.isClosed) {
            jxBrowser.navigation().reload()
        }
    }
}