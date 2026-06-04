package ai.rever.boss.components.workspaces

import ai.rever.boss.plugin.workspace.SplitConfig.SinglePanel
import ai.rever.boss.plugin.workspace.SplitConfig.VerticalSplit
import ai.rever.boss.plugin.workspace.SplitConfig.HorizontalSplit
import ai.rever.boss.cache.loadFaviconFromCache
import ai.rever.boss.components.plugin.tab_types.fluck.FluckTabInfo
import ai.rever.boss.plugin.tab.fluck.FluckTabType
import ai.rever.boss.plugin.tab.codeeditor.EditorTabInfo
import ai.rever.boss.plugin.tab.codeeditor.CodeEditorTabType
import ai.rever.boss.plugin.tab.terminal.TerminalTabInfo
import ai.rever.boss.plugin.tab.terminal.TerminalTabType
import ai.rever.boss.window.Project
import ai.rever.boss.window.WindowProjectState
import ai.rever.boss.plugin.api.TabIcon
import ai.rever.boss.plugin.api.TabInfo
import ai.rever.boss.icons.FileIcons
import ai.rever.boss.utils.extractFileName
import ai.rever.boss.components.window_panel.SplitViewState
import ai.rever.boss.components.window_panel.SplitOrientation
import ai.rever.boss.dashboard.SplitTemplatesManager
import kotlin.random.Random
import kotlin.time.Clock

/**
 * Applies a layout workspace to the split view
 * @param workspace The workspace to apply
 * @param splitViewState The split view state to apply the workspace to
 * @param windowProjectState The window project state for multi-window support (optional)
 * @param restoreProject Whether to restore the project from the workspace. Set to false when
 *                       applying workspace due to project selection change (to avoid overwriting
 *                       the user's project selection).
 */
suspend fun applyWorkspace(
    workspace: LayoutWorkspace,
    splitViewState: SplitViewState,
    windowProjectState: WindowProjectState? = null,
    restoreProject: Boolean = true
) {
    // Generate ID if missing
    val workspaceId = workspace.id.ifEmpty { LayoutWorkspace.generateId() }

    // Restore project if workspace has one and restoreProject is true
    if (restoreProject && windowProjectState != null) {
        workspace.projectPath?.let { path ->
            if (path.isNotEmpty()) {
                val projectName = path.trimEnd('/').trimEnd('\\').extractFileName().ifEmpty { "Project" }
                windowProjectState.selectProject(Project(
                    name = projectName,
                    path = path,
                    lastOpened = Clock.System.now().toEpochMilliseconds()
                ))
            }
        }
    }

    // Get current project path for tab creation
    val currentProjectPath = windowProjectState?.selectedProject?.value?.path ?: workspace.projectPath ?: ""

    // Try to restore preserved state first
    if (splitViewState.restorePreservedState(workspaceId)) {
        // State restored successfully
        return
    }

    // No preserved state, apply workspace from scratch
    splitViewState.clearAllPanels()

    // Apply the workspace recursively
    applyWorkspaceNode(workspace.layout, splitViewState, "main", currentProjectPath)
}

private suspend fun applyWorkspaceNode(
    node: SplitConfig,
    splitViewState: SplitViewState,
    currentPanelId: String,
    projectPath: String
) {
    when (node) {
        is SinglePanel -> {
            // Add tabs to current panel
            val tabsComponent = splitViewState.getPanelTabsComponent(currentPanelId)
            node.panel.tabs.forEach { tabConfig ->
                createTabFromWorkspaceConfig(tabConfig, projectPath)?.let { tabsComponent?.addTab(it) }
            }
        }

        is VerticalSplit -> {
            // First process left side in current panel
            when (val leftNode = node.left) {
                is SinglePanel -> {
                    // Add tabs to current panel
                    val tabsComponent = splitViewState.getPanelTabsComponent(currentPanelId)
                    leftNode.panel.tabs.forEach { tabConfig ->
                        createTabFromWorkspaceConfig(tabConfig, projectPath)?.let { tabsComponent?.addTab(it) }
                    }
                }
                else -> {
                    // Recursively apply left workspace config
                    applyWorkspaceNode(leftNode, splitViewState, currentPanelId, projectPath)
                }
            }

            // Then create vertical split for right side
            // Resolve the first tab up front; if it doesn't map to a supported tab type
            // (e.g. a legacy panel-host entry in a recovered workspace), skip the split
            // instead of creating an empty "ghost" panel via splitPanel(tabToMove = null).
            val firstRightTabInfo = getFirstTab(node.right)?.let { createTabFromWorkspaceConfig(it, projectPath) }
            if (firstRightTabInfo != null) {
                val rightPanelId = splitViewState.splitPanel(
                    panelId = currentPanelId,
                    orientation = SplitOrientation.VERTICAL,
                    tabToMove = firstRightTabInfo
                )

                // Add remaining tabs or process splits for right side
                when (val rightNode = node.right) {
                    is SinglePanel -> {
                        // Add remaining tabs
                        val tabsComponent = splitViewState.getPanelTabsComponent(rightPanelId)
                        rightNode.panel.tabs.drop(1).forEach { tabConfig ->
                            createTabFromWorkspaceConfig(tabConfig, projectPath)?.let { tabsComponent?.addTab(it) }
                        }
                    }
                    else -> {
                        // Recursively apply right workspace config
                        applyWorkspaceNode(rightNode, splitViewState, rightPanelId, projectPath)
                    }
                }
            }
        }

        is HorizontalSplit -> {
            // First process top side in current panel
            when (val topNode = node.top) {
                is SinglePanel -> {
                    // Add tabs to current panel
                    val tabsComponent = splitViewState.getPanelTabsComponent(currentPanelId)
                    topNode.panel.tabs.forEach { tabConfig ->
                        createTabFromWorkspaceConfig(tabConfig, projectPath)?.let { tabsComponent?.addTab(it) }
                    }
                }
                else -> {
                    // Recursively apply top workspace config
                    applyWorkspaceNode(topNode, splitViewState, currentPanelId, projectPath)
                }
            }

            // Then create horizontal split for bottom side
            // Resolve the first tab up front (see the VerticalSplit note) — never create an
            // empty split panel for an unsupported first tab.
            val firstBottomTabInfo = getFirstTab(node.bottom)?.let { createTabFromWorkspaceConfig(it, projectPath) }
            if (firstBottomTabInfo != null) {
                val bottomPanelId = splitViewState.splitPanel(
                    panelId = currentPanelId,
                    orientation = SplitOrientation.HORIZONTAL,
                    tabToMove = firstBottomTabInfo
                )

                // Add remaining tabs or process splits for bottom side
                when (val bottomNode = node.bottom) {
                    is SinglePanel -> {
                        // Add remaining tabs
                        val tabsComponent = splitViewState.getPanelTabsComponent(bottomPanelId)
                        bottomNode.panel.tabs.drop(1).forEach { tabConfig ->
                            createTabFromWorkspaceConfig(tabConfig, projectPath)?.let { tabsComponent?.addTab(it) }
                        }
                    }
                    else -> {
                        // Recursively apply bottom workspace config
                        applyWorkspaceNode(bottomNode, splitViewState, bottomPanelId, projectPath)
                    }
                }
            }
        }
    }
}

private fun getFirstTab(workspaceConfig: SplitConfig): TabConfig? {
    return when (workspaceConfig) {
        is SinglePanel -> workspaceConfig.panel.tabs.firstOrNull()
        is VerticalSplit -> getFirstTab(workspaceConfig.left)
        is HorizontalSplit -> getFirstTab(workspaceConfig.top)
    }
}

private fun createTabFromWorkspaceConfig(tabConfig: TabConfig, projectPath: String): TabInfo? {
    // Resolve project path for placeholder resolution
    val resolvedProjectPath = projectPath.ifEmpty {
        System.getProperty("user.home") ?: ""
    }

    return when (tabConfig.type) {
        "browser" -> {
            // Load favicon from cache if available (Issue #160)
            val cachedFavicon = loadFaviconFromCache(tabConfig.faviconCacheKey)

            // Process URL placeholders
            val processedUrl = tabConfig.url?.let {
                SplitTemplatesManager.processPlaceholders(it, resolvedProjectPath, null)
            } ?: "about:blank"

            FluckTabInfo(
                id = "browser-${Random.nextLong()}",
                typeId = FluckTabType.typeId,
                _title = tabConfig.title,
                _tabIcon = cachedFavicon,
                url = processedUrl,
                faviconCacheKey = tabConfig.faviconCacheKey
            )
        }
        "terminal" -> {
            // Process working directory placeholder
            val workingDir = tabConfig.workingDirectory?.let {
                SplitTemplatesManager.processPlaceholders(it, resolvedProjectPath, null)
            } ?: resolvedProjectPath.ifEmpty { null }

            // Process initial command placeholder
            val initialCmd = tabConfig.initialCommand?.let {
                SplitTemplatesManager.processPlaceholders(it, resolvedProjectPath, null)
            }

            TerminalTabInfo(
                id = "terminal-${Random.nextLong()}",
                typeId = TerminalTabType.typeId,
                title = tabConfig.title,
                workingDirectory = workingDir,
                initialCommand = initialCmd
            )
        }
        "editor" -> {
            // Process file path placeholder
            val filePath = tabConfig.filePath?.let {
                SplitTemplatesManager.processPlaceholders(it, resolvedProjectPath, null)
            } ?: ""
            val fileIconInfo = FileIcons.forFile(tabConfig.title)

            EditorTabInfo(
                id = "editor-${Random.nextLong()}",
                typeId = CodeEditorTabType.typeId,
                title = tabConfig.title,
                icon = fileIconInfo.icon,
                tabIcon = ai.rever.boss.plugin.api.TabIcon.Vector(fileIconInfo.icon, fileIconInfo.color),
                filePath = filePath
            )
        }
        // Unsupported/legacy/transient tab type (e.g. a sidebar-promoted "panel-host"
        // tab that should never have been persisted) — skip it instead of crashing
        // the whole workspace restore.
        else -> null
    }
}
