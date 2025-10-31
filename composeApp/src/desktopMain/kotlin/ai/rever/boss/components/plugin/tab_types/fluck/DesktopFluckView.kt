package ai.rever.boss.components.plugin.tab_types.fluck

import ai.rever.boss.components.registery.TabIcon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.teamdev.jxbrowser.browser.Browser
import com.teamdev.jxbrowser.view.compose.BrowserViewState

@Composable
actual fun FluckView(
    fileId: String,
    content: String,
    browser: Any?,
    browserViewState: Any?,
    onContentChange: (String) -> Unit,
    onTitleChange: (String) -> Unit,
    onIconChange: (ImageVector) -> Unit,
    onTabIconUpdate: (TabIcon) -> Unit,
    onOpenInNewTab: (String) -> Unit,
    onNavigationUpdate: ((String, String) -> Unit)?,
    onNavigationStateChange: ((isBack: Boolean) -> Unit)?,
    onFaviconCached: ((String?) -> Unit)?
) {
    // Cast browser and view state to the proper types
    val jxBrowser = browser as? Browser
    val jxBrowserViewState = browserViewState as? BrowserViewState

    if (jxBrowser != null && jxBrowserViewState != null && !jxBrowser.isClosed) {
        JxBrowserCompose(
            modifier = Modifier,
            browser = jxBrowser,
            browserViewState = jxBrowserViewState,
            initialUrl = content.ifBlank { "https://www.risalabs.ai" },
            onTitleChange = onTitleChange,
            onIconChange = onIconChange,
            onTabIconUpdate = onTabIconUpdate,
            onOpenInNewTab = onOpenInNewTab,
            onNavigationUpdate = onNavigationUpdate,
            onNavigationStateChange = onNavigationStateChange,
            onFaviconCached = onFaviconCached
        )
    }
}
