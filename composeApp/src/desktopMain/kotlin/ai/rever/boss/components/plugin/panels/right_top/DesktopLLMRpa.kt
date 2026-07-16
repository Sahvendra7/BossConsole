package ai.rever.boss.components.plugin.panels.right_top

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.*

/**
 * Platform-specific function to create LLM RPA executor
 */
actual fun createPlatformLLMRpaExecutor(browser: Any): RpaActionExecutor? {
    // Use JxBrowser-specific executor if we have access to the actual browser
    if (browser is BrowserIntegration && browser is DesktopBrowserIntegration) {
        return JxBrowserActionExecutor(browser.browser)
    }
    return null
}
