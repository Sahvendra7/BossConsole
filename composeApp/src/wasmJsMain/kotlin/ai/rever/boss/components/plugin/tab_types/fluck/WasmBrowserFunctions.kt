package ai.rever.boss.components.plugin.tab_types.fluck

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

actual fun getBrowserState(url: String): Pair<Any, Any>? {
    // WASM doesn't support browser state preservation yet
    return null
}

actual fun releaseBrowserState(url: String) {
    // No-op for WASM
}