package ai.rever.boss.components.plugin.tab_types.fluck

actual fun createBrowser(): Any {
    // iOS doesn't use JxBrowser - return dummy object
    return Any()
}

actual fun disposeBrowser(browser: Any) {
    // No-op for iOS
}

actual fun createBrowserViewState(browser: Any): Any {
    // iOS doesn't use JxBrowser - return dummy object
    return Any()
}

actual fun disposeBrowserViewState(browserViewState: Any) {
    // No-op for iOS
}

actual fun getBrowserState(
    url: String,
    onOpenInNewTab: ((String) -> Unit)?
): Pair<Any, Any>? {
    // iOS doesn't support browser state preservation yet
    return null
}

actual fun releaseBrowserState(url: String) {
    // No-op for iOS
}

actual suspend fun resetBrowserProfile(): Boolean {
    // No-op for iOS - return true as no reset needed
    return true
}
