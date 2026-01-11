package ai.rever.boss.components.plugin.panels.bottom.git

import ai.rever.boss.components.plugin.DefaultPlugin

/**
 * Register Git Status panel (desktop-only)
 * Shows changed, staged, and untracked files with staging controls.
 * Actual implementation is in desktopMain
 */
expect fun DefaultPlugin.registerGitStatus()

/**
 * Register Git Log panel (desktop-only)
 * Shows commit history with graph visualization.
 * Actual implementation is in desktopMain
 */
expect fun DefaultPlugin.registerGitLog()
