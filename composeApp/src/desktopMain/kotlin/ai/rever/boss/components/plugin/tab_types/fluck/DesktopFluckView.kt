package ai.rever.boss.components.plugin.tab_types.fluck

import ai.rever.boss.components.registery.TabIcon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.teamdev.jxbrowser.browser.Browser
import com.teamdev.jxbrowser.view.compose.BrowserViewState
import java.util.concurrent.locks.ReentrantReadWriteLock

@Composable
actual fun FluckView(
    fileId: String,
    content: String,
    browser: Any?,
    browserViewState: Any?,
    browserLock: Any?,
    onContentChange: (String) -> Unit,
    onTitleChange: (String) -> Unit,
    onIconChange: (ImageVector) -> Unit,
    onTabIconUpdate: (TabIcon) -> Unit,
    onOpenInNewTab: (String) -> Unit,
    onNavigationUpdate: ((String, String) -> Unit)?,
    onNavigationStateChange: ((isBack: Boolean) -> Unit)?,
    onFaviconCached: ((String?) -> Unit)?
) {
    // Cast browser, view state, and lock to the proper types
    val jxBrowser = browser as? Browser
    val jxBrowserViewState = browserViewState as? BrowserViewState
    val jxBrowserLock = browserLock as? ReentrantReadWriteLock

    if (jxBrowser != null && jxBrowserViewState != null && !jxBrowser.isClosed) {
        // Create lock if not provided (for standalone panels like FluckPanel)
        val lock = jxBrowserLock ?: ReentrantReadWriteLock()
        
        // Create thread-safe wrapper that handles locking internally
        val lockedBrowser = LockedBrowser(jxBrowser, lock)

        JxBrowserCompose(
            modifier = Modifier,
            browser = lockedBrowser,
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
