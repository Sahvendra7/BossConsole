package ai.rever.boss.components.plugin.panels.left_top

import ai.rever.boss.components.plugin.DefaultPlugin
import ai.rever.boss.components.registery.PanelComponentWithUI
import ai.rever.boss.components.registery.PanelInfo
import com.arkivanov.decompose.ComponentContext

/**
 * Expect function to create platform-specific Downloads panel
 */
expect fun createDownloadsPanel(ctx: ComponentContext, panelInfo: PanelInfo): PanelComponentWithUI

/**
 * Registers the Downloads panel plugin
 *
 * This plugin provides:
 * - 📥 Active Downloads: Real-time progress tracking for ongoing downloads
 * - ✅ Completed Downloads: Access to finished downloads with actions
 * - 📊 Download Speed & ETA: Live statistics for each download
 * - 🗂️ Quick Actions: Open file, reveal in folder, cancel download
 *
 * Priority 2 = Second position in left.top.bottom panel (below bookmarks)
 */
fun DefaultPlugin.registerDownloads() = panelRegistry.registerPanel(DownloadInfo) {
        ctx, panelInfo -> createDownloadsPanel(ctx, panelInfo)
}
