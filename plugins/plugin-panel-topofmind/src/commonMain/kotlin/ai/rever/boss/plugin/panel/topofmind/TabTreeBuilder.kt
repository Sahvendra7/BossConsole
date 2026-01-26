package ai.rever.boss.plugin.panel.topofmind

import ai.rever.boss.plugin.api.WorkspaceDataProvider
import ai.rever.boss.plugin.logging.BossLogger
import ai.rever.boss.plugin.logging.LogCategory
import ai.rever.boss.plugin.workspace.SplitConfig

private val logger = BossLogger.forComponent("TabTreeBuilder")

/**
 * Utility to build tree structure from active tabs
 */
object TabTreeBuilder {
    /**
     * Extract all panel IDs from layout in depth-first order
     */
    private fun extractPanelIds(layout: SplitConfig): List<String> {
        return when (layout) {
            is SplitConfig.SinglePanel -> listOf(layout.panel.id)
            is SplitConfig.VerticalSplit -> extractPanelIds(layout.left) + extractPanelIds(layout.right)
            is SplitConfig.HorizontalSplit -> extractPanelIds(layout.top) + extractPanelIds(layout.bottom)
        }
    }

    /**
     * Build hierarchical tab structure from workspace layout and panel assignments
     * Uses position-based mapping instead of ID-based filtering to handle randomly-generated panel IDs
     */
    private fun buildTabStructure(
        tabs: List<ActiveTab>,
        layout: SplitConfig?,
        panelIdMapping: Map<String, String>,
        level: Int = 0
    ): List<WorkspaceTabStructure> {
        if (layout == null || tabs.isEmpty()) {
            // No layout info or no tabs - return flat list
            return tabs.map { WorkspaceTabStructure.TabItem(it) }
        }

        return when (layout) {
            is SplitConfig.SinglePanel -> {
                // Map layout panel ID to runtime panel ID
                val runtimePanelId = panelIdMapping[layout.panel.id]
                val panelTabs = if (runtimePanelId != null) {
                    tabs.filter { it.panelId == runtimePanelId }
                } else {
                    // Fallback: if mapping fails, try direct ID match
                    tabs.filter { it.panelId == layout.panel.id }
                }
                panelTabs.map { WorkspaceTabStructure.TabItem(it) }
            }

            is SplitConfig.VerticalSplit -> {
                listOf(
                    WorkspaceTabStructure.SplitSection(
                        sectionName = "Left",
                        children = buildTabStructure(tabs, layout.left, panelIdMapping, level + 1),
                        level = level
                    ),
                    WorkspaceTabStructure.SplitSection(
                        sectionName = "Right",
                        children = buildTabStructure(tabs, layout.right, panelIdMapping, level + 1),
                        level = level
                    )
                )
            }

            is SplitConfig.HorizontalSplit -> {
                listOf(
                    WorkspaceTabStructure.SplitSection(
                        sectionName = "Top",
                        children = buildTabStructure(tabs, layout.top, panelIdMapping, level + 1),
                        level = level
                    ),
                    WorkspaceTabStructure.SplitSection(
                        sectionName = "Bottom",
                        children = buildTabStructure(tabs, layout.bottom, panelIdMapping, level + 1),
                        level = level
                    )
                )
            }
        }
    }

    /**
     * Build tree structure from active tabs
     *
     * @param activeTabs List of all active tabs
     * @param workspaceDataProvider Provider for workspace data
     * @param getTabUrl Function to extract URL from a tab (needed for FluckTabInfo type checking)
     */
    fun buildTree(
        activeTabs: List<ActiveTab>,
        workspaceDataProvider: WorkspaceDataProvider?,
        getTabUrl: (ActiveTab) -> String? = { null }
    ): List<TabTreeNode> {
        val workspaceGroups = activeTabs.groupBy { it.workspaceId }
        val rootNodes = mutableListOf<TabTreeNode>()

        workspaceGroups.forEach { (workspaceId, tabs) ->
            // Use the workspace name from the first tab (they should all be the same)
            val workspaceName = tabs.firstOrNull()?.workspaceName ?: "Unknown"

            // Get workspace layout from WorkspaceManager
            val workspace = workspaceDataProvider?.workspaces?.value?.find { it.id == workspaceId }
            val layout = workspace?.layout

            // Create panel ID mapping: layout panel ID -> runtime panel ID
            // Match panels by their position in depth-first traversal
            val panelIdMapping = if (layout != null) {
                val layoutPanelIds = extractPanelIds(layout)
                val runtimePanelIds = tabs.map { it.panelId }.distinct()

                logger.debug(LogCategory.UI, "Panel ID mapping for workspace", mapOf(
                    "workspace" to workspaceName,
                    "layoutPanelIds" to layoutPanelIds.toString(),
                    "runtimePanelIds" to runtimePanelIds.toString()
                ))

                // Validate panel count matches
                if (layoutPanelIds.size != runtimePanelIds.size) {
                    logger.warn(LogCategory.UI, "Panel count mismatch - falling back to flat layout", mapOf(
                        "layoutCount" to layoutPanelIds.size,
                        "runtimeCount" to runtimePanelIds.size
                    ))
                    // Fallback: return empty map to trigger flat layout rendering
                    emptyMap()
                } else {
                    // Map layout panel IDs to runtime panel IDs by position
                    val mapping = layoutPanelIds.zip(runtimePanelIds).toMap()
                    logger.debug(LogCategory.UI, "Panel mapping created", mapOf("mapping" to mapping.toString()))
                    mapping
                }
            } else {
                emptyMap()
            }

            // Build tab structure based on layout with panel ID mapping
            val tabStructure = buildTabStructure(tabs, layout, panelIdMapping)

            val workspaceNode = TabTreeNode.WorkspaceNode(
                id = "workspace-$workspaceId",
                name = workspaceName,
                workspaceId = workspaceId,
                level = 0,
                tabStructure = tabStructure
            )

            rootNodes.add(workspaceNode)
        }

        return rootNodes
    }

    // Filter tab structure based on search query
    private fun filterTabStructure(
        structure: List<WorkspaceTabStructure>,
        searchQuery: String,
        getTabUrl: (ActiveTab) -> String?
    ): List<WorkspaceTabStructure> {
        return structure.mapNotNull { item ->
            when (item) {
                is WorkspaceTabStructure.TabItem -> {
                    val url = getTabUrl(item.activeTab)
                    val tabMatches = item.activeTab.tabInfo.title.contains(searchQuery, ignoreCase = true) ||
                        (url != null && url.contains(searchQuery, ignoreCase = true))

                    if (tabMatches) item else null
                }

                is WorkspaceTabStructure.SplitSection -> {
                    val matchingChildren = filterTabStructure(item.children, searchQuery, getTabUrl)
                    if (matchingChildren.isNotEmpty()) {
                        item.copy(children = matchingChildren)
                    } else null
                }
            }
        }
    }

    /**
     * Filter tree nodes based on search query
     *
     * @param nodes List of tree nodes to filter
     * @param searchQuery Search query string
     * @param getTabUrl Function to extract URL from a tab (needed for FluckTabInfo type checking)
     */
    fun filterTreeNodes(
        nodes: List<TabTreeNode>,
        searchQuery: String,
        getTabUrl: (ActiveTab) -> String? = { null }
    ): List<TabTreeNode> {
        return nodes.mapNotNull { node ->
            when (node) {
                is TabTreeNode.WorkspaceNode -> {
                    val filteredStructure = filterTabStructure(node.tabStructure, searchQuery, getTabUrl)
                    val workspaceMatches = node.name.contains(searchQuery, ignoreCase = true)

                    if (workspaceMatches || filteredStructure.isNotEmpty()) {
                        node.copy(tabStructure = filteredStructure)
                    } else null
                }

                is TabTreeNode.TabNode -> {
                    val url = getTabUrl(node.activeTab)
                    val tabMatches = node.activeTab.tabInfo.title.contains(searchQuery, ignoreCase = true) ||
                        (url != null && url.contains(searchQuery, ignoreCase = true))

                    if (tabMatches) node else null
                }
            }
        }
    }
}
