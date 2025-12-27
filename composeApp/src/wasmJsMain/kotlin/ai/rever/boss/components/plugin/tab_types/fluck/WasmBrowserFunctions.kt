package ai.rever.boss.components.plugin.tab_types.fluck

import androidx.compose.runtime.Composable

actual fun createBrowser(): Any {
    // WASM doesn't use JxBrowser - return dummy object
    return Any()
}

actual fun disposeBrowser(browser: Any) {
    // No-op for WASM
}

actual fun createBrowserViewState(browser: Any): Any {
    // WASM doesn't use JxBrowser - return dummy object
    return Any()
}

actual fun disposeBrowserViewState(browserViewState: Any) {
    // No-op for WASM
}

actual fun getBrowserState(
    url: String,
    onOpenInNewTab: ((String) -> Unit)?
): Pair<Any, Any>? {
    // WASM doesn't support browser state preservation yet
    return null
}

actual fun releaseBrowserState(url: String) {
    // No-op for WASM
}

actual suspend fun resetBrowserProfile(): Boolean {
    // No-op for WASM - return true as no reset needed
    return true
}

actual fun getEngineGeneration(): Long {
    // WASM doesn't use JxBrowser - always return 0
    return 0L
}

@Composable
actual fun collectEngineGeneration(): Long {
    // WASM doesn't use JxBrowser - always return 0
    return 0L
}
