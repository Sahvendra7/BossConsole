package ai.rever.boss.components.workspaces

import ai.rever.boss.components.window_panel.SplitViewState
import ai.rever.boss.components.window_panel.SplitNode
import ai.rever.boss.components.plugin.tab_types.fluck.FluckTabInfo
import ai.rever.boss.components.plugin.tab_types.EditorTabInfo
import ai.rever.boss.components.plugin.tab_types.TerminalTabInfo
import kotlin.time.Clock

/**
 * Extracts the current layout workspace from the split view state
 */
fun extractCurrentWorkspace(
    splitViewState: SplitViewState,
    name: String = "Current",
    description: String = "Current layout workspace"
): LayoutWorkspace {
    val layout = extractSplitConfig(splitViewState.rootNode)
    return LayoutWorkspace(
        id = LayoutWorkspace.generateId(),
        name = name,
        description = description,
        layout = layout,
        timestamp = Clock.System.now().toEpochMilliseconds()
    )
}

private fun extractSplitConfig(node: SplitNode): SplitConfig {
    return when (node) {
        is SplitNode.Panel -> {
            val tabs = node.tabsComponent.tabsState.value.tabs.map { tab ->
                when (tab) {
                    is FluckTabInfo -> TabConfig(
                        type = "browser",
                        title = tab.title,
                        url = tab.currentUrl,
                        faviconCacheKey = tab.faviconCacheKey
                    )
                    is TerminalTabInfo -> TabConfig(
                        type = "terminal",
                        title = tab.title
                    )
                    is EditorTabInfo -> TabConfig(
                        type = "editor",
                        title = tab.title,
                        filePath = tab.filePath
                    )
                    else -> TabConfig(
                        type = "unknown",
                        title = tab.title
                    )
                }
            }
            SplitConfig.SinglePanel(
                PanelConfig(
                    id = node.id,
                    tabs = tabs
                )
            )
        }
        is SplitNode.VerticalSplit -> {
            SplitConfig.VerticalSplit(
                left = extractSplitConfig(node.left),
                right = extractSplitConfig(node.right)
            )
        }
        is SplitNode.HorizontalSplit -> {
            SplitConfig.HorizontalSplit(
                top = extractSplitConfig(node.top),
                bottom = extractSplitConfig(node.bottom)
            )
        }
    }
}
