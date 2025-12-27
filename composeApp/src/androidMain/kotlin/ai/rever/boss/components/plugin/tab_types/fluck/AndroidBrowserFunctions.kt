package ai.rever.boss.components.plugin.tab_types.fluck

import androidx.compose.runtime.Composable

actual fun createBrowser(): Any {
    // Android doesn't use JxBrowser - return dummy object
    return Any()
}

actual fun disposeBrowser(browser: Any) {
    // No-op for Android
}

actual fun createBrowserViewState(browser: Any): Any {
    // Android doesn't use JxBrowser - return dummy object
    return Any()
}

actual fun disposeBrowserViewState(browserViewState: Any) {
    // No-op for Android
}

actual fun getBrowserState(
    url: String,
    onOpenInNewTab: ((String) -> Unit)?
): Pair<Any, Any>? {
    // Android doesn't support browser state preservation yet
    return null
}

actual fun releaseBrowserState(url: String) {
    // No-op for Android
}

actual suspend fun resetBrowserProfile(): Boolean {
    // No-op for Android - return true as no reset needed
    return true
}

actual fun getEngineGeneration(): Long {
    // Android doesn't use JxBrowser - always return 0
    return 0L
}

@Composable
actual fun collectEngineGeneration(): Long {
    // Android doesn't use JxBrowser - always return 0
    return 0L
}
