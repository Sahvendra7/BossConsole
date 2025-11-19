package ai.rever.boss.components.plugin.panels.left_top

import ai.rever.boss.components.registery.PanelComponentWithUI
import ai.rever.boss.components.registery.PanelInfo
import com.arkivanov.decompose.ComponentContext

/**
 * Actual implementation for Desktop platform
 * Creates the Downloads panel component with desktop-specific dependencies
 */
actual fun createDownloadsPanel(ctx: ComponentContext, panelInfo: PanelInfo): PanelComponentWithUI {
    return DownloadsPanel(ctx, panelInfo)
}
