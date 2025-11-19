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
    onNavigationUpdate: ((String, String) -> Unit)?,
    onFaviconCacheKeyUpdate: ((String?) -> Unit)?
): FluckTabComponent {
    return DesktopFluckTabComponent(
        config = config,
        componentContext = componentContext,
        onTitleUpdate = onTitleUpdate,
        onIconUpdate = onIconUpdate,
        onTabIconUpdate = onTabIconUpdate,
        onOpenInNewTab = onOpenInNewTab,
        onNavigationUpdate = onNavigationUpdate,
        onFaviconCacheKeyUpdate = onFaviconCacheKeyUpdate
    )
}

class DesktopFluckTabComponent(
    config: TabInfo,
    componentContext: ComponentContext,
    onTitleUpdate: (String) -> Unit,
    onIconUpdate: (ImageVector) -> Unit,
    onTabIconUpdate: (TabIcon) -> Unit,
    onOpenInNewTab: (String) -> Unit,
    onNavigationUpdate: ((String, String) -> Unit)? = null,
    onFaviconCacheKeyUpdate: ((String?) -> Unit)? = null
) : FluckTabComponent(
    config = config,
    componentContext = componentContext,
    onTitleUpdate = onTitleUpdate,
    onIconUpdate = onIconUpdate,
    onTabIconUpdate = onTabIconUpdate,
    onOpenInNewTab = onOpenInNewTab,
    onNavigationUpdate = onNavigationUpdate,
    onFaviconCacheKeyUpdate = onFaviconCacheKeyUpdate
) {
    
    override fun reload() {
        // Cast browser to JxBrowser type and wrap with LockedBrowser for thread-safe access
        val jxBrowser = browser as? Browser
        if (jxBrowser != null && !jxBrowser.isClosed) {
            val lockedBrowser = LockedBrowser(jxBrowser, browserLock)
            lockedBrowser.navigation().reload()
        }
    }

    override fun zoomIn() {
        val jxBrowser = browser as? Browser
        if (jxBrowser != null && !jxBrowser.isClosed) {
            try {
                val lockedBrowser = LockedBrowser(jxBrowser, browserLock)
                val currentLevel = lockedBrowser.zoom().level().value()
                val newLevel = getNextZoomLevel(currentLevel, isZoomIn = true)
                lockedBrowser.zoom().level(com.teamdev.jxbrowser.zoom.ZoomLevel.of(newLevel))
                println("🔍 Zoom In: ${(currentLevel * 100).toInt()}% → ${(newLevel * 100).toInt()}%")
            } catch (e: Exception) {
                println("Error zooming in: ${e.message}")
            }
        }
    }

    override fun zoomOut() {
        val jxBrowser = browser as? Browser
        if (jxBrowser != null && !jxBrowser.isClosed) {
            try {
                val lockedBrowser = LockedBrowser(jxBrowser, browserLock)
                val currentLevel = lockedBrowser.zoom().level().value()
                val newLevel = getNextZoomLevel(currentLevel, isZoomIn = false)
                lockedBrowser.zoom().level(com.teamdev.jxbrowser.zoom.ZoomLevel.of(newLevel))
                println("🔍 Zoom Out: ${(currentLevel * 100).toInt()}% → ${(newLevel * 100).toInt()}%")
            } catch (e: Exception) {
                println("Error zooming out: ${e.message}")
            }
        }
    }

    override fun actualSize() {
        val jxBrowser = browser as? Browser
        if (jxBrowser != null && !jxBrowser.isClosed) {
            try {
                val lockedBrowser = LockedBrowser(jxBrowser, browserLock)
                lockedBrowser.zoom().level(com.teamdev.jxbrowser.zoom.ZoomLevel.P_100)
                println("🔍 Actual Size: Reset to 100%")
            } catch (e: Exception) {
                println("Error resetting zoom: ${e.message}")
            }
        }
    }

    private fun getNextZoomLevel(current: Double, isZoomIn: Boolean): Double {
        val levels = listOf(0.25, 0.50, 0.75, 0.90, 1.0, 1.10, 1.25, 1.50, 1.75, 2.0, 2.50, 3.0)
        return if (isZoomIn) {
            levels.firstOrNull { it > current } ?: 3.0
        } else {
            levels.lastOrNull { it < current } ?: 0.25
        }
    }
}
