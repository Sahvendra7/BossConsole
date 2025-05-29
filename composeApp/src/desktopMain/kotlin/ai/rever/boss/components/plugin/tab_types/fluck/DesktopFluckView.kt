package ai.rever.boss.components.plugin.tab_types.fluck

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
    onOpenInNewTab: (String) -> Unit
) {
    // Cast browser and view state to the proper types
    val jxBrowser = browser as? Browser
    val jxBrowserViewState = browserViewState as? BrowserViewState
    
    if (jxBrowser != null && jxBrowserViewState != null) {
        JxBrowserCompose(
            modifier = Modifier,
            browser = jxBrowser,
            browserViewState = jxBrowserViewState,
            initialUrl = content.ifBlank { "https://www.google.com" },
            onTitleChange = onTitleChange,
            onOpenInNewTab = onOpenInNewTab
        )
    }
}