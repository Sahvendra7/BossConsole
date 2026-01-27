package ai.rever.boss.components.plugin.tab_types.fluck

import ai.rever.boss.plugin.api.TabIcon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
    onFaviconCached: ((String?) -> Unit)?,
    onCloseTab: (() -> Unit)?
) {
    // Cast browser, view state, and lock to the proper types
    val jxBrowser = browser as? Browser
    val jxBrowserViewState = browserViewState as? BrowserViewState
    val jxBrowserLock = browserLock as? ReentrantReadWriteLock

    if (jxBrowser != null && jxBrowserViewState != null && !jxBrowser.isClosed) {
        // Create lock if not provided (for standalone panels like FluckPanel)
        val lock = remember(jxBrowserLock) { jxBrowserLock ?: ReentrantReadWriteLock() }

        // Create thread-safe wrapper that handles locking internally
        // Use remember to avoid creating new instance on every recomposition (fixes LaunchedEffect restarts)
        val lockedBrowser = remember(jxBrowser, lock) { LockedBrowser(jxBrowser, lock) }

        JxBrowserCompose(
            modifier = Modifier,
            tabId = fileId,
            browser = lockedBrowser,
            browserViewState = jxBrowserViewState,
            initialUrl = content.ifBlank { "https://www.risalabs.ai" },
            onTitleChange = onTitleChange,
            onIconChange = onIconChange,
            onTabIconUpdate = onTabIconUpdate,
            onOpenInNewTab = onOpenInNewTab,
            onNavigationUpdate = onNavigationUpdate,
            onNavigationStateChange = onNavigationStateChange,
            onFaviconCached = onFaviconCached,
            onCloseTab = onCloseTab
        )
    }
}
