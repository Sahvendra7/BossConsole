package ai.rever.boss.components.configuration

import kotlinx.browser.window

/**
 * WebAssembly implementation to open configuration directory
 */
actual fun openConfigurationDirectory(path: String) {
    // In the browser, we can't open a local directory
    // Instead, show an alert with the information
    try {
        window.alert("Configurations are stored in browser localStorage.\nPrefix: $path")
    } catch (e: Exception) {
    }
}
