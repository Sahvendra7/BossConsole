package ai.rever.boss.components.plugin.panels.right_top

import androidx.compose.runtime.Composable

/**
 * iOS implementation of browser accessor
 */
actual class BrowserAccessor {
    actual fun getActiveBrowserIntegration(): BrowserIntegration? {
        // iOS implementation not yet available
        return null
    }
    
    actual companion object {
        actual var selectedTabId: String? = null
    }
}

/**
 * iOS implementation of browser connection setup
 */
@Composable
actual fun SetupBrowserConnection() {
    // iOS implementation not yet available
}

/**
 * iOS implementation to store split view state
 */
actual fun storeSplitViewState(splitViewState: Any) {
    // iOS implementation not yet available
}

/**
 * iOS implementation to create FluckTabInfo from ActiveTab
 */
actual fun createFluckTabInfo(activeTab: Any): FluckTabInfo? {
    // iOS implementation not yet available
    return null
}

/**
 * iOS implementation of RPA Recorder Factory
 */
actual class RpaRecorderFactory {
    actual fun createComponent(ctx: com.arkivanov.decompose.ComponentContext, panelInfo: ai.rever.boss.components.registery.PanelInfo): RpaRecorderComponent {
        // Use base implementation for iOS
        return RpaRecorderComponent(ctx, panelInfo)
    }
}
