package ai.rever.boss.components.plugin.tab_types.fluck

import ai.rever.boss.config.JxBrowserConfig
import com.teamdev.jxbrowser.browser.Browser
import com.teamdev.jxbrowser.engine.Engine
import com.teamdev.jxbrowser.engine.EngineOptions
import com.teamdev.jxbrowser.view.compose.BrowserViewState
import kotlinx.coroutines.MainScope
import java.awt.Frame
import java.awt.Window

// Singleton engine for all browser tabs
object FluckEngine {
    val engine: Engine by lazy {
        Engine.newInstance(
            EngineOptions.newBuilder(JxBrowserConfig.renderingMode)
                .licenseKey(JxBrowserConfig.licenseKey)
                .build()
        )
    }
}

actual fun createBrowser(): Any {
    return FluckEngine.engine.newBrowser()
}

actual fun disposeBrowser(browser: Any) {
    (browser as? Browser)?.close()
}

actual fun createBrowserViewState(browser: Any): Any {
    val jxBrowser = browser as Browser
    val window = Window.getWindows().firstOrNull() ?: Frame()
    // Use MainScope to ensure UI operations happen on the main thread
    return BrowserViewState(jxBrowser, MainScope(), window)
}

actual fun disposeBrowserViewState(viewState: Any) {
    // BrowserViewState doesn't have a dispose method, but it will release 
    // the browser reference when garbage collected
} 