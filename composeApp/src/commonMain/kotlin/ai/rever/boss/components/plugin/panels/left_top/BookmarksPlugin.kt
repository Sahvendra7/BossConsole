package ai.rever.boss.components.plugin.panels.left_top

import ai.rever.boss.components.plugin.DefaultPlugin

/**
 * Registers the Bookmarks panel plugin
 *
 * This plugin provides:
 * - ⭐ Favorites: Quick access to bookmarked tabs
 * - 💼 All Workspaces: Browse and load complete workspace layouts
 * - ⭐ Favorite Workspaces: Quick access to frequently used workspaces
 *
 * Priority 1 = First position in left.top.top panel
 */
fun DefaultPlugin.registerBookmarks() = panelRegistry.registerPanel(BookmarksInfo) {
        ctx, panelInfo -> BookmarksPanel(ctx, panelInfo)
}
