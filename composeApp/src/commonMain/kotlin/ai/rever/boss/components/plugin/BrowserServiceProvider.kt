package ai.rever.boss.components.plugin

import ai.rever.boss.plugin.browser.BrowserService

/**
 * Platform-specific provider for BrowserService.
 *
 * On desktop platforms with JxBrowser support, this returns the actual implementation.
 * On other platforms, this returns null.
 */
expect fun getBrowserServiceInstance(): BrowserService?
