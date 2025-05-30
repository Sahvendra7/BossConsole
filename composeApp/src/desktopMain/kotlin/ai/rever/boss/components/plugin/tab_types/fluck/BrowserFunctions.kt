package ai.rever.boss.components.plugin.tab_types.fluck

import com.teamdev.jxbrowser.browser.Browser
import com.teamdev.jxbrowser.view.compose.BrowserViewState
import kotlinx.coroutines.MainScope
import java.awt.Frame
import java.awt.Window

actual fun createBrowser(): Any {
    return FluckEngine.engine.newBrowser()
}

actual fun disposeBrowser(browser: Any) {
    val jxBrowser = browser as? Browser
    if (jxBrowser != null && !jxBrowser.isClosed) {
        jxBrowser.close()
    }
}

actual fun createBrowserViewState(browser: Any): Any {
    val jxBrowser = browser as Browser
    val window = Window.getWindows().firstOrNull() ?: Frame()
    // Use MainScope to ensure UI operations happen on the main thread
    return BrowserViewState(jxBrowser, MainScope(), window)
}

actual fun disposeBrowserViewState(browserViewState: Any) {
    // BrowserViewState doesn't have explicit disposal on JVM
} 