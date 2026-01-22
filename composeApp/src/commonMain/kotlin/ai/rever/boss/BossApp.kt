package ai.rever.boss

import BossTheme
import ai.rever.boss.components.bars.horizontal.BossBottomBar
import ai.rever.boss.components.bars.horizontal.BossTitleBar
import ai.rever.boss.components.bars.horizontal.BossTopBar
import ai.rever.boss.components.bars.vertical.BossLeftSideBar
import ai.rever.boss.components.bars.vertical.BossRightSideBar
import ai.rever.boss.components.model.BossDraggableComponent
import ai.rever.boss.components.model.Panel.Companion.bottom
import ai.rever.boss.components.model.Panel.Companion.left
import ai.rever.boss.components.model.Panel.Companion.right
import ai.rever.boss.components.model.Panel.Companion.top
import ai.rever.boss.components.model.TabDraggableComponent
import ai.rever.boss.components.model.TabDropResult
import ai.rever.boss.components.overlays.DraggingItemOverlay
import ai.rever.boss.components.overlays.TabDraggingOverlay
import ai.rever.boss.components.plugin.DefaultPlugin
import ai.rever.boss.components.plugin.tab_types.fluck.FluckTabInfo
import ai.rever.boss.components.plugin.tab_types.EditorTabInfo
import ai.rever.boss.components.registery.*
import ai.rever.boss.components.dialogs.NewTabDialog
import ai.rever.boss.components.dialogs.TabType
import ai.rever.boss.components.dialogs.TerminalLinkOpenDialog
import ai.rever.boss.components.dialogs.NewProjectWizardDialog
import ai.rever.boss.icons.FileIcons
import ai.rever.boss.utils.extractFileName
import ai.rever.boss.terminal.ExistingSplitTargetMode
import ai.rever.boss.terminal.TerminalLinkOpenMode
import ai.rever.boss.terminal.TerminalLinkSettingsManager
import ai.rever.boss.components.window_panel.BossWindow
import ai.rever.boss.components.window_panel.components.main_window_panels.BossTabsComponent
import ai.rever.boss.components.window_panel.rememberSplitViewState
import ai.rever.boss.components.window_panel.SplitNode
import ai.rever.boss.components.window_panel.SplitViewStateRegistry
import ai.rever.boss.window.WindowProjectStateRegistry
import ai.rever.boss.window.LocalWindowProjectState
import ai.rever.boss.window.LocalWindowId
import ai.rever.boss.window.selectProjectInWindow
import ai.rever.boss.components.plugin.panels.left_top.WindowProjectState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.take
import ai.rever.boss.components.events.FileEventBus
import ai.rever.boss.components.events.FileValidationResult
import ai.rever.boss.components.events.ParsedFileReference
import ai.rever.boss.components.events.parseFileReference
import ai.rever.boss.components.events.stripFilePrefix
import ai.rever.boss.components.events.validateFilePath
import ai.rever.boss.components.events.TerminalEventBus
import ai.rever.boss.components.events.TerminalLinkEventBus
import ai.rever.boss.components.events.PanelEventBus
import ai.rever.boss.components.events.RunEventBus
import ai.rever.boss.components.events.GitTerminalEventBus
import ai.rever.boss.components.events.RunnerTerminalEventBus
import ai.rever.boss.git.GitTerminalService
import ai.rever.boss.components.events.NavigationTargetBus
import ai.rever.boss.components.events.DashboardEventBus
import ai.rever.boss.run.RunConfigurationManager
import ai.rever.boss.run.RunExecutionService
import ai.rever.boss.run.RunnerSettingsManager
import ai.rever.boss.run.RunnerTerminalService
import ai.rever.boss.run.RunnerTerminalTarget
import ai.rever.boss.startup.StartupSettingsManager
import ai.rever.boss.components.plugin.tab_types.TerminalTab
import ai.rever.boss.components.plugin.tab_types.TerminalTabInfo
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import com.arkivanov.decompose.ComponentContext
import kotlin.random.Random
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import ai.rever.boss.components.workspaces.workspaceManager
import ai.rever.boss.components.workspaces.LayoutWorkspace
import ai.rever.boss.components.workspaces.applyWorkspace
import ai.rever.boss.components.workspaces.extractCurrentWorkspace
import ai.rever.boss.components.workspaces.WorkspaceSettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import ai.rever.boss.components.plugin.panels.left_bottom.TopOfMind.LocalSplitViewState
import ai.rever.boss.components.plugin.panels.left_bottom.TopOfMind.LocalWorkspaceManager
import ai.rever.boss.components.plugin.panels.left_bottom.TopOfMind.TabTreeState
import ai.rever.boss.components.dialogs.TopOfMindDialog
import ai.rever.boss.components.windows.SettingsWindow
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import ai.rever.boss.components.plugin.panels.right_top.LLMSettingsManager
import ai.rever.boss.updater.UpdateManager
import ai.rever.boss.updater.UpdateBanner
import ai.rever.boss.updater.UpdateSettings
import androidx.compose.runtime.collectAsState
import kotlin.time.Clock
import ai.rever.boss.services.auth.CoreAuthService
import ai.rever.boss.services.URLHandlerService
import ai.rever.boss.services.TerminalHandlerService
import ai.rever.boss.services.FileHandlerService
import ai.rever.boss.services.WorkspaceHandlerService
import ai.rever.boss.utils.WindowFocusManager
import ai.rever.boss.utils.CLIVersionManager
import ai.rever.boss.utils.CLIInstaller
import ai.rever.boss.utils.Version
import ai.rever.boss.keymap.KeymapSettingsManager
import ai.rever.boss.keymap.handler.KeymapHandler
import ai.rever.boss.keymap.model.KeymapActions
import ai.rever.boss.keymap.model.ShortcutContext
import ai.rever.boss.keymap.lifecycle.ShortcutLifecycleManager
import ai.rever.boss.keymap.lifecycle.conditions.*
import ai.rever.boss.components.events.KeyboardEventBus
import ai.rever.boss.components.events.KeyboardEventPriority
import ai.rever.boss.components.events.KeyEventSource
import ai.rever.boss.components.events.KeyboardEvent as BossKeyboardEvent
import ai.rever.boss.components.events.KeyboardEventResult
import ai.rever.boss.actions.BossActionHandler
import ai.rever.boss.focusmode.FocusModeSettingsManager
import ai.rever.boss.components.window_panel.SplitOrientation
import ai.rever.boss.components.window_panel.SplitViewState
import ai.rever.boss.window.WindowAppearanceSettingsManager
import ai.rever.boss.performance.PerformanceState
import ai.rever.boss.performance.BrowserTabInfo
import ai.rever.boss.performance.TerminalInfo
import ai.rever.boss.performance.EditorTabResourceInfo
import ai.rever.boss.components.plugin.panels.left_top.ProjectState
import ai.rever.boss.components.plugin.panels.left_top.Project
import ai.rever.boss.components.plugin.panels.left_top.CodeBaseInfo
import ai.rever.boss.components.plugin.panels.left_bottom.RunConfigurationsInfo

// Platform-specific download tab close callback setup
expect fun setupDownloadTabCloseCallback(splitViewState: SplitViewState)

// Platform-specific function to consume pending initial tab for a window
// Returns the TabInfo if there's a pending tab for this window, null otherwise
expect fun consumePendingInitialTab(windowId: String): TabInfo?

/**
 * Platform-specific function to consume pending initial project for a window.
 * When a window is created with a project via "Open in New Window", the project
 * is stored as pending and consumed here when the window initializes.
 *
 * @param windowId The window ID to get the pending project for
 * @return The pending Project if one exists, null otherwise
 */
expect fun consumePendingInitialProject(windowId: String): Project?

/**
 * Handle the result of a tab drop operation.
 * Includes bounds checking to handle cases where tab list may have changed during drag.
 */
private fun handleTabDropResult(result: TabDropResult, splitViewState: SplitViewState) {
    when (result) {
        is TabDropResult.Reorder -> {
            // Reorder within the same panel
            val panel = splitViewState.getPanel(result.panelId)
            val tabCount = panel?.tabsComponent?.tabsState?.value?.tabs?.size ?: 0
            // Validate indices are within bounds before reordering
            if (result.fromIndex in 0 until tabCount && result.toIndex in 0..tabCount) {
                panel?.tabsComponent?.moveTab(result.fromIndex, result.toIndex)
            }
        }
        is TabDropResult.MoveToPanel -> {
            // Move tab from source panel to target panel
            val sourcePanel = splitViewState.getPanel(result.sourcePanelId)
            val targetPanel = splitViewState.getPanel(result.targetPanelId)
            val sourceTabCount = sourcePanel?.tabsComponent?.tabsState?.value?.tabs?.size ?: 0

            // Validate source index is within bounds
            if (sourcePanel != null && targetPanel != null && result.sourceIndex in 0 until sourceTabCount) {
                // Remove from source panel FIRST to prevent duplicate entries
                // and ensure sourceIndex remains valid during removal
                sourcePanel.tabsComponent.removeTab(result.sourceIndex)

                // Then add to target panel
                val newIndex = targetPanel.tabsComponent.addTab(result.tabInfo)
                if (newIndex >= 0) {
                    targetPanel.tabsComponent.selectTab(newIndex)
                }

                // Set target panel as active
                splitViewState.setActivePanel(result.targetPanelId)
            }
        }
        is TabDropResult.CreateSplit -> {
            // Remove from source panel FIRST if it's a different panel
            // This ensures the tab is removed before splitPanel potentially modifies state
            if (result.sourcePanelId != result.targetPanelId) {
                val sourcePanel = splitViewState.getPanel(result.sourcePanelId)
                // Use tab ID for removal instead of index - more reliable after state changes
                sourcePanel?.tabsComponent?.removeTabById(result.tabInfo.id)
            }

            // Create a new split with the tab
            splitViewState.splitPanel(
                panelId = result.targetPanelId,
                orientation = result.orientation,
                tabToMove = result.tabInfo
            )
        }
    }
}

/**
 * Helper function to open a runner terminal in the main panel.
 * Creates a terminal tab with the run command and adds it to the active panel.
 */
private fun openRunnerInMainPanel(
    event: ai.rever.boss.components.events.RunnerTerminalOpenEvent,
    splitViewState: ai.rever.boss.components.window_panel.SplitViewState
) {
    // Create terminal tab in active panel
    val terminalTab = TerminalTabInfo(
        id = event.terminalId,
        typeId = ai.rever.boss.components.registery.TabTypeId("terminal"),
        title = "Run: ${event.configName}",
        initialCommand = event.command,
        workingDirectory = event.workingDirectory
    )

    // Find existing tab or create new one
    val existingPanel = splitViewState.findPanelWithTab(event.terminalId)
    if (existingPanel != null && event.isRerun) {
        // Re-run: Update existing tab with new command
        existingPanel.tabsComponent.removeTabById(event.terminalId)
    }

    // Add to active panel (or first available)
    val activeComponent = splitViewState.getActiveTabsComponent()
        ?: splitViewState.getAllPanels().firstOrNull()?.tabsComponent

    if (activeComponent != null) {
        val tabIndex = activeComponent.addTab(terminalTab)
        if (tabIndex >= 0) {
            activeComponent.selectTab(tabIndex)
            println("[BossApp] Runner terminal tab created in main panel: ${event.terminalId}")
        }
    } else {
        println("[BossApp] ERROR - No panel available for runner terminal")
    }
}

/**
 * Creates a browser tab for the given URL.
 * Extracted to reduce duplication in openTerminalLink.
 */
private fun createBrowserTab(url: String): FluckTabInfo {
    return FluckTabInfo(
        id = "browser-${kotlin.random.Random.nextLong()}",
        typeId = TabTypeId("fluck"),
        _title = "Loading...",
        url = url
    )
}

/**
 * Creates an editor tab for the given file path.
 * Used in openTerminalLink when handling file: URLs.
 *
 * Note: This function assumes the path has already been validated by the caller.
 * Use [validateFilePath] before calling this function.
 *
 * @param filePath The validated file path (may include "file:" prefix, which will be stripped)
 */
private fun createEditorTab(filePath: String): EditorTabInfo {
    val cleanPath = stripFilePrefix(filePath)
    val fileName = cleanPath.extractFileName().ifEmpty { "untitled" }
    val fileIconInfo = FileIcons.forFile(fileName)
    return EditorTabInfo(
        id = "editor-${Random.nextLong()}",
        typeId = TabTypeId("editor"),
        title = fileName,
        icon = fileIconInfo.icon,
        tabIcon = TabIcon.Vector(fileIconInfo.icon, fileIconInfo.color),
        filePath = cleanPath
    )
}

/**
 * Checks if a URL is a file URL (starts with "file:").
 */
private fun isFileUrl(url: String): Boolean = url.startsWith("file:")

/**
 * Helper function to open a terminal link based on user's selected mode.
 * Handles creating browser tabs (for HTTP) or editor tabs (for file:) and splitting panels.
 *
 * Issue #346: Terminal link click prompt with remember preference
 * Issue #506: Added windowId for multi-window navigation filtering
 *
 * @param url The URL to open (HTTP or file: URL)
 * @param mode How to open the link (split or new tab)
 * @param splitViewState The split view state for panel operations
 * @param sourceTerminalId Optional terminal tab ID where the link was clicked (for finding source panel)
 * @param scope CoroutineScope for launching navigation events (structured concurrency)
 * @param windowId The window ID for multi-window filtering (Issue #506)
 */
private fun openTerminalLink(
    url: String,
    mode: TerminalLinkOpenMode,
    splitViewState: ai.rever.boss.components.window_panel.SplitViewState,
    sourceTerminalId: String? = null,
    scope: CoroutineScope,
    windowId: String? = null
) {
    // Find the source panel (where the terminal is) to correctly identify "the other" panel
    // This is important because cmd+click doesn't change focus, so activePanelId may not be the terminal panel
    val sourcePanelId = sourceTerminalId?.let { terminalId ->
        splitViewState.findPanelWithTab(terminalId)?.id
    } ?: splitViewState.activePanelId

    // Defensive check: verify source panel still exists (could be closed between link click and handling)
    // Fall back to active panel if source panel no longer exists
    val validSourcePanelId = if (splitViewState.findPanel(sourcePanelId) != null) {
        sourcePanelId
    } else {
        splitViewState.activePanelId
    }

    // Determine if this is a file URL - file links open in editor, HTTP links open in browser
    val isFile = isFileUrl(url)

    // For file URLs, perform defensive validation (primary validation happens in DesktopTerminalContent)
    // This protects against race conditions or direct calls to this function
    if (isFile) {
        // Parse file reference to extract line:column (e.g., file:/path/file.kt:123:45)
        val rawPath = stripFilePrefix(url)
        val parsed = parseFileReference(rawPath)

        when (val result = validateFilePath(parsed.path)) {
            is FileValidationResult.Invalid -> {
                println("[BossApp] Cannot open file: ${result.reason}")
                return
            }
            is FileValidationResult.Valid -> {
                // Continue with validated path - use canonical path for consistency
                // TOCTOU note: There's a small window between validation and opening where
                // the file could be deleted. This is acceptable as the editor handles missing
                // files gracefully, and fully preventing this race is impractical.
                openTerminalLinkInternal(
                    url = "file:${result.canonicalPath}",
                    mode = mode,
                    splitViewState = splitViewState,
                    validSourcePanelId = validSourcePanelId,
                    isFile = true,
                    fileLine = parsed.line,
                    fileColumn = parsed.column,
                    scope = scope,
                    windowId = windowId
                )
            }
        }
    } else {
        // HTTP URLs don't need validation
        openTerminalLinkInternal(
            url = url,
            mode = mode,
            splitViewState = splitViewState,
            validSourcePanelId = validSourcePanelId,
            isFile = false,
            scope = scope,
            windowId = windowId
        )
    }
}

/**
 * Internal implementation of openTerminalLink after validation.
 * This is separated to avoid code duplication after the file validation branch.
 *
 * @param url The URL to open (HTTP or file: URL with canonical path)
 * @param mode How to open the link (split or new tab)
 * @param splitViewState The split view state for panel operations
 * @param validSourcePanelId The validated source panel ID
 * @param isFile Whether this is a file URL (vs HTTP)
 * @param fileLine 1-based line number for file navigation (0 = no navigation)
 * @param fileColumn 1-based column number for file navigation (0 = no navigation)
 * @param scope CoroutineScope for launching navigation events (structured concurrency)
 * @param windowId The window ID for multi-window filtering (Issue #506)
 */
private fun openTerminalLinkInternal(
    url: String,
    mode: TerminalLinkOpenMode,
    splitViewState: ai.rever.boss.components.window_panel.SplitViewState,
    validSourcePanelId: String,
    isFile: Boolean,
    fileLine: Int = 0,
    fileColumn: Int = 0,
    scope: CoroutineScope,
    windowId: String? = null
) {
    // Helper to create the appropriate tab type
    fun createTab() = if (isFile) createEditorTab(url) else createBrowserTab(url)

    // Helper to trigger navigation after opening a file with line:column
    // Uses structured concurrency - coroutine is cancelled if the composable is disposed
    // Issue #506: Pass windowId for multi-window filtering
    fun navigateToLineIfNeeded() {
        if (isFile && fileLine > 0 && windowId != null) {
            val cleanPath = stripFilePrefix(url)
            scope.launch(Dispatchers.Main) {
                NavigationTargetBus.navigateTo(cleanPath, fileLine, fileColumn, sourceWindowId = windowId)
            }
        }
    }

    when (mode) {
        TerminalLinkOpenMode.EXISTING_SPLIT -> {
            // Open in existing split panel (not the source panel where terminal is)
            // Use the target mode setting to determine which panel to use
            val targetMode = TerminalLinkSettingsManager.currentSettings.value.existingSplitTarget
            val targetPanel = when (targetMode) {
                ExistingSplitTargetMode.MOST_RECENT_ACTIVE ->
                    splitViewState.getOtherPanelExcluding(validSourcePanelId)
                ExistingSplitTargetMode.FIRST_AVAILABLE ->
                    splitViewState.getFirstOtherPanelExcluding(validSourcePanelId)
            }
            if (targetPanel != null) {
                val tab = createTab()
                val tabIndex = targetPanel.tabsComponent.addTab(tab)
                if (tabIndex >= 0) {
                    targetPanel.tabsComponent.selectTab(tabIndex)
                    splitViewState.setActivePanel(targetPanel.id)
                    navigateToLineIfNeeded()
                }
            } else {
                // IMPORTANT: Fallback when user saved EXISTING_SPLIT preference but later closed all splits.
                // Creates a new vertical split instead of failing silently.
                splitViewState.splitPanel(
                    panelId = validSourcePanelId,
                    orientation = SplitOrientation.VERTICAL,
                    tabToMove = createTab()
                )
                navigateToLineIfNeeded()
            }
        }
        TerminalLinkOpenMode.VERTICAL_SPLIT, TerminalLinkOpenMode.HORIZONTAL_SPLIT -> {
            val orientation = if (mode == TerminalLinkOpenMode.VERTICAL_SPLIT) {
                SplitOrientation.VERTICAL
            } else {
                SplitOrientation.HORIZONTAL
            }
            // Create split from the source panel (where terminal is), not from active panel
            splitViewState.splitPanel(
                panelId = validSourcePanelId,
                orientation = orientation,
                tabToMove = createTab()
            )
            navigateToLineIfNeeded()
        }
        TerminalLinkOpenMode.NEW_TAB, TerminalLinkOpenMode.ALWAYS_ASK -> {
            // NEW_TAB opens in current panel; ALWAYS_ASK shouldn't reach here but handle gracefully
            if (isFile) {
                // For file URLs, use openFileInActivePanel for consistent behavior
                val cleanPath = stripFilePrefix(url)
                val fileName = cleanPath.extractFileName().ifEmpty { "untitled" }
                splitViewState.openFileInActivePanel(cleanPath, fileName)
                navigateToLineIfNeeded()
            } else {
                splitViewState.openUrlInActivePanel(url, "Loading...")
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ComponentContext.BossApp(
    windowId: String,
    isFirstWindow: Boolean = false,
    panelRegistry: PanelRegistry,
    onToggleMaximize: (() -> Unit)? = null
) {

    // Use the passed panelRegistry instance (created in BossWindow for menu access)
    val tabRegistry = remember { TabRegistry() }

    val panelComponentStore = remember { PanelComponentStore(this, panelRegistry) }

    val draggablePanelComponent = remember { BossDraggableComponent(panelRegistry) }
    val tabDragComponent = remember { TabDraggableComponent() }
    val tabsComponent = remember { BossTabsComponent(this, tabRegistry, windowId) }
    
    // Create split view state that manages all tab panels
    val splitViewState = rememberSplitViewState(
        tabRegistry = tabRegistry,
        windowId = windowId,
        initialTabsComponent = tabsComponent
    )

    // Register this window's state in the global registry for multi-window features
    LaunchedEffect(splitViewState, windowId) {
        SplitViewStateRegistry.register(windowId, splitViewState)
    }

    // Register callback for FluckEngine to auto-close download redirect tabs (desktop only)
    LaunchedEffect(splitViewState) {
        setupDownloadTabCloseCallback(splitViewState)
    }

    // Cancel any active drag when window loses focus (prevents stuck ghost)
    LaunchedEffect(tabDragComponent, windowId) {
        WindowFocusManager.focusedWindowFlow.collect { focusedWindowId ->
            // If this window lost focus and there's an active drag, cancel it
            if (focusedWindowId != windowId && tabDragComponent.isDragging) {
                tabDragComponent.cancelDrag()
            }
        }
    }

    // Consume any pending initial tab for this window (from "Open in New Window" context menu)
    LaunchedEffect(windowId, splitViewState) {
        val pendingTab = consumePendingInitialTab(windowId)
        if (pendingTab != null) {
            println("BossApp: Consuming pending tab '${pendingTab.title}' for window: $windowId")
            // Add the tab to the active panel (first panel by default)
            val activePanel = splitViewState.getAllPanels().firstOrNull()
            if (activePanel != null) {
                val index = activePanel.tabsComponent.addTab(pendingTab)
                if (index >= 0) {
                    activePanel.tabsComponent.selectTab(index)
                }
            }
        }
    }

    // Create per-window project state (each window has independent project)
    val windowProjectState = remember(windowId) {
        WindowProjectStateRegistry.getOrCreate(windowId)
    }

    // Consume any pending initial project for this window (from "Open in New Window" context menu)
    LaunchedEffect(windowId, windowProjectState) {
        val pendingProject = consumePendingInitialProject(windowId)
        if (pendingProject != null) {
            println("BossApp: Consuming pending project '${pendingProject.name}' for window: $windowId")
            windowProjectState.selectProject(pendingProject)
            PanelEventBus.openPanel(CodeBaseInfo.id, sourceWindowId = windowId)
            PanelEventBus.openPanel(RunConfigurationsInfo.id, sourceWindowId = windowId)
        }
    }

    // Collect window-specific project state reactively (used by multiple effects below)
    val selectedProject by windowProjectState.selectedProject.collectAsState()

    // Open CodeBase and RunConfigurations panels if a project is selected at startup
    // Note: Pending project is handled in the LaunchedEffect above, this handles
    // existing window project state (e.g., when restored from workspace)
    LaunchedEffect(windowProjectState) {
        val initialProject = windowProjectState.selectedProject.value
        if (initialProject.path.isNotEmpty()) {
            println("BossApp: Project '${initialProject.name}' already selected at startup, opening panels")
            PanelEventBus.openPanel(CodeBaseInfo.id, sourceWindowId = windowId)
            PanelEventBus.openPanel(RunConfigurationsInfo.id, sourceWindowId = windowId)
        }
    }

    // NOTE: Default workspace application is handled in a LaunchedEffect below
    // (see "Apply default workspace when project is selected")

    // Register resource count providers for performance monitoring
    // Use DisposableEffect to clean up on disposal and prevent memory leaks
    DisposableEffect(splitViewState, draggablePanelComponent) {
        // Cache for getAllPanels() to avoid repeated tree traversals
        // All 6 providers are called within milliseconds of each other every 5 seconds
        // Using synchronized block for thread-safe access from provider lambdas
        val cacheLock = Any()
        var cachedPanels: List<SplitNode.Panel>? = null
        var cacheTimestamp = 0L
        val cacheTtlMs = 500L // Cache valid for 500ms (well within 5s collection interval)

        fun getCachedPanels(): List<SplitNode.Panel> {
            synchronized(cacheLock) {
                val now = System.currentTimeMillis()
                val cached = cachedPanels
                if (cached == null || now - cacheTimestamp > cacheTtlMs) {
                    val newPanels = splitViewState.getAllPanels()
                    cachedPanels = newPanels
                    cacheTimestamp = now
                    return newPanels
                }
                return cached
            }
        }

        PerformanceState.registerResourceProviders(
            browserTabs = {
                getCachedPanels().sumOf { panel ->
                    panel.tabsComponent.tabsState.value.tabs.count { it is FluckTabInfo }
                }
            },
            terminals = {
                getCachedPanels().sumOf { panel ->
                    panel.tabsComponent.tabsState.value.tabs.count { it is TerminalTabInfo }
                }
            },
            editorTabs = {
                getCachedPanels().sumOf { panel ->
                    panel.tabsComponent.tabsState.value.tabs.count { it is EditorTabInfo }
                }
            },
            panels = {
                // Count visible panels from the draggable panel component
                listOf(
                    bottom,
                    left.top,
                    left.bottom,
                    right.top,
                    right.bottom
                ).count { panel -> draggablePanelComponent.isVisible(panel) }
            },
            windows = {
                SplitViewStateRegistry.states.value.size
            }
        )

        // Register detailed resource providers for the Resources tab
        PerformanceState.registerDetailedResourceProviders(
            browserTabs = {
                getCachedPanels().flatMap { panel ->
                    val tabsState = panel.tabsComponent.tabsState.value
                    val activeTabId = tabsState.activeTab?.id
                    tabsState.tabs.filterIsInstance<FluckTabInfo>().map { tab ->
                        BrowserTabInfo(
                            id = tab.id,
                            title = tab.title,
                            url = tab.currentUrl,
                            isActive = tab.id == activeTabId
                        )
                    }
                }
            },
            terminals = {
                getCachedPanels().flatMap { panel ->
                    val tabsState = panel.tabsComponent.tabsState.value
                    val activeTabId = tabsState.activeTab?.id
                    tabsState.tabs.filterIsInstance<TerminalTabInfo>().map { tab ->
                        TerminalInfo(
                            id = tab.id,
                            title = tab.title,
                            isActive = tab.id == activeTabId
                        )
                    }
                }
            },
            editorTabs = {
                getCachedPanels().flatMap { panel ->
                    val tabsState = panel.tabsComponent.tabsState.value
                    val activeTabId = tabsState.activeTab?.id
                    tabsState.tabs.filterIsInstance<EditorTabInfo>().map { tab ->
                        EditorTabResourceInfo(
                            id = tab.id,
                            fileName = tab.title,
                            filePath = tab.filePath,
                            isActive = tab.id == activeTabId
                        )
                    }
                }
            }
        )

        onDispose {
            PerformanceState.clearResourceProviders()
        }
    }

    // Workspace manager - use global singleton to ensure Bookmarks panel sees updates
    val workspaceManager = remember { workspaceManager }
    val coroutineScope = rememberCoroutineScope()

    // Focus requester for keyboard shortcuts
    val focusRequester = remember { FocusRequester() }

    // Keyboard shortcut handler with customizable keymaps
    val keymapSettings by KeymapSettingsManager.currentSettings.collectAsState()
    val keymapHandler = remember(keymapSettings) {
        KeymapHandler.from(keymapSettings)
    }

    // Focus mode settings
    val focusModeSettings by FocusModeSettingsManager.currentSettings.collectAsState()
    val isFocusModeEnabled = focusModeSettings.enabled

    // Window appearance settings
    val windowAppearanceSettings by WindowAppearanceSettingsManager.currentSettings.collectAsState()
    val showTitleBarSetting = windowAppearanceSettings.showTitleBar
    val isAutoRevealEnabled = focusModeSettings.autoRevealEnabled
    val revealOffsetDp = with(LocalDensity.current) { focusModeSettings.revealOffsetPx.toDp() }
    val revealDelayMs = focusModeSettings.revealDelayMs

    // Focus mode hover reveal state - edge strip hover detection
    // Hovering states track raw cursor position in hover strips
    var hoveringTopStrip by remember { mutableStateOf(false) }
    var hoveringLeftStrip by remember { mutableStateOf(false) }
    var hoveringRightStrip by remember { mutableStateOf(false) }
    var hoveringBottomStrip by remember { mutableStateOf(false) }

    // Reveal states are set after delay threshold is met
    var hoverRevealTop by remember { mutableStateOf(false) }
    var hoverRevealLeft by remember { mutableStateOf(false) }
    var hoverRevealRight by remember { mutableStateOf(false) }
    var hoverRevealBottom by remember { mutableStateOf(false) }

    // Apply reveal delay before triggering reveal
    LaunchedEffect(hoveringTopStrip, revealDelayMs) {
        if (hoveringTopStrip) {
            delay(revealDelayMs)
            hoverRevealTop = true
        } else {
            hoverRevealTop = false
        }
    }

    LaunchedEffect(hoveringLeftStrip, revealDelayMs) {
        if (hoveringLeftStrip) {
            delay(revealDelayMs)
            hoverRevealLeft = true
        } else {
            hoverRevealLeft = false
        }
    }

    LaunchedEffect(hoveringRightStrip, revealDelayMs) {
        if (hoveringRightStrip) {
            delay(revealDelayMs)
            hoverRevealRight = true
        } else {
            hoverRevealRight = false
        }
    }

    LaunchedEffect(hoveringBottomStrip, revealDelayMs) {
        if (hoveringBottomStrip) {
            delay(revealDelayMs)
            hoverRevealBottom = true
        } else {
            hoverRevealBottom = false
        }
    }

    // Interaction sources for sidebar hover tracking
    val topBarInteractionSource = remember { MutableInteractionSource() }
    val leftSidebarInteractionSource = remember { MutableInteractionSource() }
    val rightSidebarInteractionSource = remember { MutableInteractionSource() }
    val bottomBarInteractionSource = remember { MutableInteractionSource() }

    // Track hover state on revealed content itself
    val topBarHovered by topBarInteractionSource.collectIsHoveredAsState()
    val leftSidebarHovered by leftSidebarInteractionSource.collectIsHoveredAsState()
    val rightSidebarHovered by rightSidebarInteractionSource.collectIsHoveredAsState()
    val bottomBarHovered by bottomBarInteractionSource.collectIsHoveredAsState()

    // Debounced visibility states with grace period for smoother transitions
    var showTopBar by remember { mutableStateOf(false) }
    var showLeftSidebar by remember { mutableStateOf(false) }
    var showRightSidebar by remember { mutableStateOf(false) }
    var showBottomBar by remember { mutableStateOf(false) }

    // Add grace period before hiding to prevent flicker when moving mouse from strip to sidebar
    LaunchedEffect(hoverRevealTop, topBarHovered, isFocusModeEnabled) {
        if (!isFocusModeEnabled) {
            showTopBar = true
        } else if (hoverRevealTop || topBarHovered) {
            showTopBar = true
        } else {
            // Add 2000ms delay before hiding for menu interactions
            delay(2000)
            if (!hoverRevealTop && !topBarHovered) {
                showTopBar = false
            }
        }
    }

    LaunchedEffect(hoverRevealLeft, leftSidebarHovered, isFocusModeEnabled) {
        if (!isFocusModeEnabled) {
            showLeftSidebar = true
        } else if (hoverRevealLeft || leftSidebarHovered) {
            showLeftSidebar = true
        } else {
            // Add 2000ms delay before hiding for menu interactions
            delay(2000)
            if (!hoverRevealLeft && !leftSidebarHovered) {
                showLeftSidebar = false
            }
        }
    }

    LaunchedEffect(hoverRevealRight, rightSidebarHovered, isFocusModeEnabled) {
        if (!isFocusModeEnabled) {
            showRightSidebar = true
        } else if (hoverRevealRight || rightSidebarHovered) {
            showRightSidebar = true
        } else {
            // Add 2000ms delay before hiding for menu interactions
            delay(2000)
            if (!hoverRevealRight && !rightSidebarHovered) {
                showRightSidebar = false
            }
        }
    }

    LaunchedEffect(hoverRevealBottom, bottomBarHovered, isFocusModeEnabled) {
        if (!isFocusModeEnabled) {
            showBottomBar = true
        } else if (hoverRevealBottom || bottomBarHovered) {
            showBottomBar = true
        } else {
            // Add 2000ms delay before hiding for menu interactions
            delay(2000)
            if (!hoverRevealBottom && !bottomBarHovered) {
                showBottomBar = false
            }
        }
    }


    // Request focus when auth session resolves (event-driven, no delays)
    val isSessionResolved by CoreAuthService.isSessionResolved.collectAsState()

    LaunchedEffect(isSessionResolved) {
        if (isSessionResolved) {
            focusRequester.requestFocus()
        }
    }

    // Set up workspace deletion callback to cleanup tabs
    LaunchedEffect(workspaceManager, splitViewState) {
        workspaceManager.setOnWorkspaceDeleted { deletedWorkspaceId ->
            // Clean up preserved states for the deleted workspace
            splitViewState.cleanupDeletedWorkspace(deletedWorkspaceId)
        }
    }
    
    // State for showing new tab dialog
    var showNewTabDialog by remember { mutableStateOf(false) }
    var newTabDialogInitialType by remember { mutableStateOf<ai.rever.boss.components.dialogs.TabType?>(null) }
    // Track if workspace restoration has completed (for first window only)
    // New windows don't restore Last Session, so start as complete
    var workspaceRestorationComplete by remember { mutableStateOf(!isFirstWindow) }
    // Track if handlers have been marked ready (prevents race condition between workspace load and timeout)
    // Uses atomic flag to ensure handler marking happens exactly once
    val handlersMarked = remember { java.util.concurrent.atomic.AtomicBoolean(false) }
    var showTopOfMindDialog by remember { mutableStateOf(false) }
    var showProjectDialog by remember { mutableStateOf(false) }
    var showNewProjectDialog by remember { mutableStateOf(false) }

    // State for save feedback
    var saveMessage by remember { mutableStateOf<String?>(null) }

    // State for showing settings dialog
    var showSettingsDialog by remember { mutableStateOf(false) }
    var settingsInitialSection by remember { mutableStateOf<String?>(null) }

    // State for terminal link open dialog (Issue #346)
    var showTerminalLinkDialog by remember { mutableStateOf(false) }
    var pendingTerminalLinkUrl by remember { mutableStateOf("") }
    var pendingTerminalSourceId by remember { mutableStateOf<String?>(null) }

    // Apply default workspace when project is selected
    LaunchedEffect(selectedProject.path) {
        if (selectedProject.path.isNotEmpty()) {
            val defaultWorkspace = WorkspaceSettingsManager.getDefaultWorkspace()
            if (defaultWorkspace != null) {
                // Apply the workspace
                println("BossApp: Applying default workspace '${defaultWorkspace.name}' for project '${selectedProject.name}'")
                applyWorkspace(defaultWorkspace, splitViewState)
                workspaceManager.loadWorkspace(defaultWorkspace)
            }
        }
    }

    // Open CodeBase and RunConfigurations panels when project is selected (reactive architecture)
    LaunchedEffect(selectedProject.path, windowId) {
        if (selectedProject.path.isNotEmpty()) {
            PanelEventBus.openPanel(CodeBaseInfo.id, sourceWindowId = windowId)
            PanelEventBus.openPanel(RunConfigurationsInfo.id, sourceWindowId = windowId)
        }
    }

    // Action handler for keyboard shortcuts
    val actionHandler = remember(
        splitViewState,
        windowId,
        workspaceManager,
        draggablePanelComponent,
        coroutineScope
    ) {
        BossActionHandler(
            splitViewState = splitViewState,
            windowId = windowId,
            workspaceManager = workspaceManager,
            draggablePanelComponent = draggablePanelComponent,
            onShowNewTabDialog = { showNewTabDialog = true },
            onShowTopOfMindDialog = { showTopOfMindDialog = true },
            onShowSaveMessage = { saveMessage = it },
            onShowSettings = { showSettingsDialog = true },
            coroutineScope = coroutineScope
        )
    }

    // Register lifecycle conditions for shortcuts
    LaunchedEffect(Unit) {
        // Note: TAB_CLOSE does not use lifecycle conditions because it has inline
        // tab count checking in its handler. Using lifecycle conditions would break
        // multi-window support since ShortcutLifecycleManager is a singleton and
        // each window would overwrite the previous window's condition.

        // Panel-dependent shortcuts
        ShortcutLifecycleManager.registerCondition(
            KeymapActions.PANEL_NAVIGATE_LEFT,
            SplitNavigationCondition(
                getSplitCount = { splitViewState.getAllPanels().size }
            )
        )

        ShortcutLifecycleManager.registerCondition(
            KeymapActions.PANEL_NAVIGATE_RIGHT,
            SplitNavigationCondition(
                getSplitCount = { splitViewState.getAllPanels().size }
            )
        )

        ShortcutLifecycleManager.registerCondition(
            KeymapActions.PANEL_NAVIGATE_UP,
            SplitNavigationCondition(
                getSplitCount = { splitViewState.getAllPanels().size }
            )
        )

        ShortcutLifecycleManager.registerCondition(
            KeymapActions.PANEL_NAVIGATE_DOWN,
            SplitNavigationCondition(
                getSplitCount = { splitViewState.getAllPanels().size }
            )
        )
    }

    // Reevaluate lifecycle conditions when panels change
    LaunchedEffect(splitViewState.getAllPanels().size) {
        ShortcutLifecycleManager.reevaluate()
    }

    // Subscribe to keyboard events with WORKSPACE priority
    LaunchedEffect(keymapHandler, splitViewState, windowId) {
        val handlerName = "BossApp-Workspace-$windowId"
        KeyboardEventBus.subscribe(
            priority = KeyboardEventPriority.WORKSPACE,
            handlerName = handlerName
        ) { event ->
            // IMPORTANT: Only handle keyboard events if this window is focused
            // This prevents multiple windows from processing the same shortcut
            if (!WindowFocusManager.isWindowFocused(windowId)) {
                return@subscribe KeyboardEventResult(
                    consumed = false,
                    handlerName = handlerName
                )
            }

            // Determine current context based on active tab type
            val activeTabsComponent = splitViewState.getPanelTabsComponent(splitViewState.activePanelId)
            val activeTab = activeTabsComponent?.tabsState?.value?.activeTab
            val currentContext = when {
                activeTab is FluckTabInfo -> ShortcutContext.BROWSER
                activeTab is TerminalTabInfo -> ShortcutContext.TERMINAL
                activeTab is EditorTabInfo -> ShortcutContext.EDITOR
                else -> ShortcutContext.WORKSPACE
            }

            // Try to handle as workspace shortcut using the KeymapHandler
            val handled = keymapHandler.handleKeyEvent(event.keyEvent, currentContext) { actionId ->
                // Check lifecycle condition before executing (synchronous check)
                // Skip lifecycle check for TAB_CLOSE - it has inline tab count checking
                if (actionId != KeymapActions.TAB_CLOSE) {
                    val state = ShortcutLifecycleManager.getState(actionId)
                    if (state != null && !state.enabled) {
                        return@handleKeyEvent false
                    }
                }

                // Execute the shortcut action via the action handler
                actionHandler.handleAction(actionId)
            }

            KeyboardEventResult(consumed = handled, handlerName = handlerName)
        }
    }

    DisposableEffect(panelRegistry, tabRegistry) {
        val plugin = DefaultPlugin(panelRegistry, tabRegistry)
        draggablePanelComponent.update()

        onDispose {
            // NOTE: Browser disposal moved to main.kt onCloseRequest handler
            // Browsers must be disposed BEFORE Compose disposal begins, not during it
            // See main.kt onCloseRequest for the disposeAllBrowsersBlocking() call

            // Save current workspace as "Last Session" when app closes
            try {
                // Use runBlocking to ensure save completes before app closes
                kotlinx.coroutines.runBlocking {
                    val currentLayout = extractCurrentWorkspace(splitViewState)
                    val lastSessionConfig = currentLayout.copy(
                        id = "last-session",
                        name = "Last Session",
                        description = "Automatically saved session"
                    )
                    workspaceManager.updateCurrentWorkspace(lastSessionConfig)
                    workspaceManager.saveCurrentWorkspace("Last Session")
                }
            } catch (e: Exception) {
                println("❌ [BossApp] Failed to save Last Session workspace: ${e.message}")
            }

            // Cleanup plugin coroutines
            plugin.dispose()

            // Cleanup update manager
            UpdateManager.instance.cleanup()

            // Unregister this window's state from the global registry
            SplitViewStateRegistry.unregister(windowId)

            // Unregister this window's project state from the registry
            WindowProjectStateRegistry.unregister(windowId)
        }
    }
    
    // Load LLM settings on startup
    LaunchedEffect(Unit) {
        try {
            LLMSettingsManager.loadSettings()
        } catch (e: Exception) {
            // Ignore errors during settings load to prevent app crash
            println("Warning: Failed to load LLM settings: ${e.message}")
        }
    }
    
    // Initialize update manager and conditionally start periodic checks
    LaunchedEffect(Unit) {
        try {
            // Only start periodic checks if enabled in settings
            if (UpdateSettings.autoCheckEnabled) {
                UpdateManager.instance.startPeriodicChecks()

                // Check for updates on startup if enough time has passed
                if (UpdateManager.instance.shouldCheckForUpdates()) {
                    UpdateManager.instance.checkForUpdates()
                }
            }
        } catch (e: Exception) {
            println("Warning: Failed to initialize update manager: ${e.message}")
        }
    }

    // Check and auto-update CLI version on startup
    LaunchedEffect(Unit) {
        launch {
            try {
                if (CLIVersionManager.needsCLIUpdate()) {
                    println("CLI auto-update: Updating CLI scripts to version ${Version.CURRENT}")
                    val result = CLIInstaller.installCLI()
                    if (result.success) {
                        println("✅ CLI auto-update successful: ${result.message}")
                    } else {
                        println("⚠️ CLI auto-update failed: ${result.message}")
                    }
                }
            } catch (e: Exception) {
                println("⚠️ CLI auto-update error: ${e.message}")
            }
        }
    }

    // Load last used workspace on startup
    LaunchedEffect(workspaceManager, splitViewState) {
        // Wait for workspaces to be loaded
        workspaceManager.workspaces
            .onEach { configs ->
                // Clean up orphaned workspace states
                val existingWorkspaceIds = configs.map { it.id }.toSet()
                splitViewState.cleanupDeletedWorkspaces(existingWorkspaceIds)
                
                // Handle workspace restoration - only process when configs are loaded (non-empty)
                // Empty configs might mean either "loading" or "fresh install" - we use timeout for fresh install
                if (configs.isNotEmpty() && workspaceManager.currentWorkspace.value == null) {
                    // Only load "Last Session" for the first window (app startup)
                    // New windows should start fresh (Issue #129)
                    if (isFirstWindow) {
                        // Check if there's a saved "last-session" workspace
                        val lastSessionConfig = configs.find { it.name == "Last Session" }

                        if (lastSessionConfig != null) {
                            println("BossApp: Loading Last Session workspace BEFORE processing URLs/terminals")

                            // Ensure it has the correct ID
                            val configWithId = if (lastSessionConfig.id != "last-session") {
                                lastSessionConfig.copy(id = "last-session")
                            } else {
                                lastSessionConfig
                            }
                            // Apply the last session workspace FIRST
                            workspaceManager.loadWorkspace(configWithId)
                            applyWorkspace(configWithId, splitViewState)

                            println("BossApp: Last Session loaded, now processing queued URLs/terminals")
                        } else {
                            println("BossApp: No Last Session found, starting with empty workspace")
                        }

                        // Mark workspace restoration as complete (for auto-show dialog logic)
                        workspaceRestorationComplete = true

                        // CRITICAL: Mark handlers as ready AFTER Last Session loads (or after determining no session exists)
                        // This ensures URLs/terminals/files/workspaces create tabs AFTER workspace is loaded,
                        // not before (which would cause tabs to be destroyed by clearAllPanels)
                        // Uses atomic compareAndSet to prevent race with timeout fallback
                        if (handlersMarked.compareAndSet(false, true)) {
                            URLHandlerService.markAppReady()
                            println("BossApp: Marked URL handler as ready (window: $windowId)")

                            FileHandlerService.markReady()
                            println("BossApp: Marked file handler as ready (window: $windowId)")

                            WorkspaceHandlerService.markReady()
                            println("BossApp: Marked workspace handler as ready (window: $windowId)")

                            // Wait for session to resolve before marking terminal handler ready
                            // This ensures terminal tabs only appear after authentication is fully initialized
                            if (isSessionResolved) {
                                TerminalHandlerService.markReady()
                                println("BossApp: Marked terminal handler as ready (window: $windowId)")
                            } else {
                                println("BossApp: Session not resolved yet, will mark terminal handler ready later")
                            }
                        }
                    }
                    // Else: New window - don't load Last Session, start with empty workspace, but still mark ready
                    else {
                        // Uses atomic compareAndSet to prevent race with timeout fallback
                        if (handlersMarked.compareAndSet(false, true)) {
                            URLHandlerService.markAppReady()
                            println("BossApp: Marked URL handler as ready (new window: $windowId)")

                            FileHandlerService.markReady()
                            println("BossApp: Marked file handler as ready (new window: $windowId)")

                            WorkspaceHandlerService.markReady()
                            println("BossApp: Marked workspace handler as ready (new window: $windowId)")

                            if (isSessionResolved) {
                                TerminalHandlerService.markReady()
                                println("BossApp: Marked terminal handler as ready (new window: $windowId)")
                            }
                        }
                    }
                }
            }
            .launchIn(this)
    }

    // Fallback timeout for fresh install (no workspaces on disk at all)
    // This handles the case where workspace manager never emits non-empty configs
    LaunchedEffect(isFirstWindow, isSessionResolved) {
        if (isFirstWindow && !workspaceRestorationComplete) {
            // Read timeout from settings (use current value, don't make it a key to avoid restart)
            val timeoutMs = StartupSettingsManager.currentSettings.value.workspaceLoadTimeoutMs
            delay(timeoutMs) // Wait for workspace manager to load from disk
            if (!workspaceRestorationComplete) {
                // Still not complete after timeout - assume fresh install
                println("BossApp: Workspace loading timeout (${timeoutMs}ms), assuming fresh install")
                workspaceRestorationComplete = true

                // Uses atomic compareAndSet to prevent race with workspace loading flow
                if (handlersMarked.compareAndSet(false, true)) {
                    URLHandlerService.markAppReady()
                    FileHandlerService.markReady()
                    WorkspaceHandlerService.markReady()
                    if (isSessionResolved) {
                        TerminalHandlerService.markReady()
                    }
                    println("BossApp: Marked all handlers ready (fresh install fallback)")
                }
            }
        }
    }

    // Listen for file open events - now handled by split state
    // Issue #506: Filter by window to prevent file opening in all windows
    LaunchedEffect(splitViewState, windowId) {
        FileEventBus.fileOpenEvents
            .filter { event -> event.sourceWindowId == windowId }
            .onEach { event ->
                println("[BossApp] FileEventBus received: ${event.filePath}:${event.line} (window: $windowId)")
                splitViewState.openFileInActivePanel(event.filePath, event.fileName)
                // Emit navigation target for cursor positioning (PSI navigation)
                // Issue #506: Pass windowId for multi-window filtering
                if (event.line > 0) {
                    NavigationTargetBus.navigateTo(event.filePath, event.line, event.column, sourceWindowId = windowId)
                }
            }
            .launchIn(this)
    }

    // Listen for terminal open events - now handled by split state
    // Issue #506: Filter by window to prevent terminal opening in all windows
    LaunchedEffect(splitViewState, windowId) {
        TerminalEventBus.terminalOpenEvents
            .filter { event -> event.sourceWindowId == windowId }
            .onEach { event ->
                println("[BossApp] TerminalEventBus received (window: $windowId)")
                splitViewState.openTerminalInActivePanel(event.command)
            }
            .launchIn(this)

        // Note: We DON'T call markReady() here - that happens AFTER Last Session loads
        // just like URL handler, to prevent terminals from being destroyed by clearAllPanels()
    }

    // Listen for runner terminal events (Issue #347 - Runner in terminal sidebar)
    // Issue #498: Filter events by window to prevent duplicate tabs in all windows
    LaunchedEffect(splitViewState, windowId) {
        // Open runner terminal events
        RunnerTerminalEventBus.openEvents
            .filter { event -> event.sourceWindowId == windowId }
            .onEach { event ->
                println("[BossApp] Runner terminal open event: ${event.configName}")

                // Check settings for terminal target
                val settings = RunnerSettingsManager.currentSettings.value
                val usesSidebar = settings.terminalTarget == RunnerTerminalTarget.SIDEBAR_PANEL

                if (usesSidebar) {
                    // Open in sidebar terminal panel
                    // First, ensure the sidebar terminal panel is open
                    PanelEventBus.openPanel(ai.rever.boss.components.plugin.panels.bottom.terminal.TerminalInfo.id, sourceWindowId = windowId)

                    // Create a new tab in the sidebar terminal with the command (window-scoped)
                    val success = RunnerTerminalService.openInSidebarTerminal(
                        windowId = windowId,
                        configId = event.configId,
                        command = event.command,
                        workingDirectory = event.workingDirectory,
                        tabTitle = "Run: ${event.configName}",
                        isRerun = event.isRerun
                    )

                    if (success) {
                        println("[BossApp] Runner opened in sidebar terminal: ${event.configName}")
                    } else {
                        // Fallback to main panel if sidebar terminal not available
                        println("[BossApp] Sidebar terminal not available, falling back to main panel")
                        openRunnerInMainPanel(event, splitViewState)
                    }
                } else {
                    // Open in main panel (original behavior)
                    openRunnerInMainPanel(event, splitViewState)
                }
            }
            .launchIn(this)

        // Close runner terminal events
        // Issue #506: Filter by window to prevent closing in all windows
        RunnerTerminalEventBus.closeEvents
            .filter { event -> event.sourceWindowId == windowId }
            .onEach { event ->
                println("[BossApp] Runner terminal close event: ${event.terminalId} (window: $windowId)")

                // Find and close the terminal tab
                val panel = splitViewState.findPanelWithTab(event.terminalId)
                panel?.tabsComponent?.removeTabById(event.terminalId)

                // Notify service that terminal was removed (window-scoped)
                RunnerTerminalService.removeTerminal(windowId, event.terminalId)
            }
            .launchIn(this)

        // Stop runner terminal events
        // Note: Ctrl+C is sent by RunnerTerminalService.stopRunner() via TabbedTerminalStateRegistry
        // Issue #506: Filter by window to prevent stopping in all windows
        RunnerTerminalEventBus.stopEvents
            .filter { event -> event.sourceWindowId == windowId }
            .onEach { event ->
                println("[BossApp] Runner terminal stop event: ${event.terminalId} (window: $windowId)")
                // Ctrl+C is already sent by the service - this event is for any additional UI handling
            }
            .launchIn(this)
    }

    // Listen for Git terminal events (opens git commands in sidebar terminal)
    // Issue #498: Filter events by window to prevent duplicate tabs in all windows
    LaunchedEffect(splitViewState, windowId) {
        GitTerminalEventBus.openEvents
            .filter { event -> event.sourceWindowId == windowId }
            .onEach { event ->
                println("[BossApp] Git terminal event: ${event.operationName} - ${event.command}")

                // Open the terminal panel if not already open
                PanelEventBus.openPanel(ai.rever.boss.components.plugin.panels.bottom.terminal.TerminalInfo.id, sourceWindowId = windowId)

                // Create a new tab in the sidebar terminal with the git command (window-scoped)
                val success = GitTerminalService.openInSidebarTerminal(
                    windowId = windowId,
                    command = event.command,
                    workingDirectory = event.workingDirectory,
                    operationName = event.operationName
                )

                if (success) {
                    println("[BossApp] Git command opened in sidebar terminal: ${event.operationName}")
                } else {
                    println("[BossApp] Failed to open git command in sidebar terminal")
                }
            }
            .launchIn(this)
    }

    // Listen for terminal link click events (Issue #346)
    // Shows dialog or auto-opens based on user preference
    // Note: We collect linkClickEvents directly (not with combine()) to avoid
    // re-processing the same event when settings change (e.g., when user clicks "Remember")
    // Issue #498: Filter events by window to prevent dialog appearing in all windows
    LaunchedEffect(splitViewState, windowId) {
        TerminalLinkEventBus.linkClickEvents
            .filter { event -> event.sourceWindowId == windowId }
            .onEach { event ->
                val settings = TerminalLinkSettingsManager.currentSettings.value
                println("[BossApp] Terminal link click: ${event.url}, mode: ${settings.openMode}, window: $windowId")

                when (settings.openMode) {
                    TerminalLinkOpenMode.ALWAYS_ASK -> {
                        pendingTerminalLinkUrl = event.url
                        pendingTerminalSourceId = event.sourceTerminalId
                        showTerminalLinkDialog = true
                    }
                    else -> {
                        openTerminalLink(event.url, settings.openMode, splitViewState, event.sourceTerminalId, this, windowId = windowId)
                    }
                }
            }
            .launchIn(this)
    }

    // Listen for run execute events (Issue #321 - Run functionality)
    // IntelliJ-style: Adds config to run history when executed
    // Issue #506: Filter by sourceWindowId for multi-window support
    LaunchedEffect(splitViewState, windowId) {
        RunEventBus.executeEvents
            .filter { event -> event.sourceWindowId == windowId }
            .onEach { event ->
                println("[BossApp] Run event received: ${event.configuration.name} (window: $windowId)")

                // Add to run history (IntelliJ-style)
                // Note: addConfiguration() already handles deduplication by filePath,
                // so we don't need an external check (avoids TOCTOU race condition)
                val historyConfig = event.configuration.copy(isAutoDetected = false)
                RunConfigurationManager.addConfiguration(historyConfig)

                // Select the config in top bar dropdown
                // Use filePath lookup since addConfiguration may deduplicate (existing config has different ID)
                val savedConfigs = RunConfigurationManager.currentSettings.value.configurations
                val configToSelect = savedConfigs.find { it.filePath == historyConfig.filePath }
                if (configToSelect != null) {
                    RunConfigurationManager.selectConfiguration(configToSelect.id)
                }

                RunExecutionService.execute(event.configuration, event.debug, windowId)
            }
            .launchIn(this)

        RunEventBus.stopEvents
            .filter { event -> event.sourceWindowId == windowId }
            .onEach { event ->
                println("[BossApp] Stop event received: configId=${event.configId} (window: $windowId)")
                if (event.configId != null) {
                    RunExecutionService.stop(event.configId)
                } else {
                    RunExecutionService.stopAll()
                }
            }
            .launchIn(this)

        // Scan events are still handled for explicit scan requests (e.g., from Run Configurations plugin)
        // Issue #506: Filter by sourceWindowId for multi-window support
        RunEventBus.scanEvents
            .filter { event -> event.sourceWindowId == windowId }
            .onEach { event ->
                println("[BossApp] Scan event received: ${event.projectPath} (window: $windowId)")
                RunConfigurationManager.scanProject(event.projectPath)
            }
            .launchIn(this)
    }

    // NOTE: Removed auto-scan on project change (IntelliJ-style behavior)
    // Run configuration detection should be done via a dedicated plugin,
    // not automatically when project changes.

    // Listen for workspace load events from CLI
    // Issue #506: Filter by sourceWindowId for multi-window support
    LaunchedEffect(splitViewState, workspaceManager, windowId) {
        ai.rever.boss.components.events.WorkspaceEventBus.workspaceLoadEvents
            .filter { event -> event.sourceWindowId == windowId }
            .onEach { event ->
                try {
                    val file = java.io.File(event.workspacePath)
                    if (file.exists() && file.canRead()) {
                        val json = file.readText()
                        val workspace = ai.rever.boss.components.workspaces.WorkspaceSerializer.deserialize(json)

                        // Use the same loading pattern as the UI
                        workspaceManager.loadWorkspace(workspace)
                        applyWorkspace(workspace, splitViewState)

                        println("BossApp: Workspace loaded from CLI: ${file.absolutePath} (window: $windowId)")
                    } else {
                        println("BossApp: Cannot load workspace: ${file.absolutePath}")
                    }
                } catch (e: Exception) {
                    println("BossApp: Error loading workspace: ${e.message}")
                }
            }
            .launchIn(this)
    }

    // Listen for panel open events (e.g., from CLI folder command)
    // Issue #506: Filter by window to prevent panel opening in all windows
    LaunchedEffect(draggablePanelComponent, panelRegistry, windowId) {
        PanelEventBus.panelOpenEvents
            .filter { event -> event.sourceWindowId == windowId }
            .onEach { event ->
                try {
                    // Find the panel info from registry
                    // Compare only panelId and pluginId, ignore defaultOrder (UI metadata)
                    val panelInfo = panelRegistry.getAllPanels().find {
                        it.id.panelId == event.panelId.panelId &&
                        it.id.pluginId == event.panelId.pluginId
                    }

                    if (panelInfo != null) {
                        val panelSlot = panelInfo.defaultSlotPosition
                        val panelItems = draggablePanelComponent.getItemsForSlot(panelSlot)
                        val targetItem = panelItems.find { it.pluginContentId.panelId == event.panelId.panelId }

                        if (targetItem != null) {
                            // Check if panel is already open before toggling
                            // If already visible and showing this panel, don't toggle (keep it open)
                            val targetPanel = when (panelSlot) {
                                left.bottom -> bottom
                                left.top.top -> left.top
                                right.top.top -> right.top
                                left.top.bottom -> left.top
                                right.top.bottom -> right.top
                                else -> null
                            }

                            if (targetPanel != null) {
                                val isAlreadyVisible = draggablePanelComponent.isVisible(targetPanel)
                                val currentPanelId = draggablePanelComponent.getPanelContentId(targetPanel)
                                val isSamePanel = currentPanelId?.panelId == event.panelId.panelId

                                // Only invoke onClick if panel is not already visible showing this content
                                if (!isAlreadyVisible || !isSamePanel) {
                                    draggablePanelComponent.onClick.invoke(targetItem)
                                }
                                println("BossApp: Opened panel: ${event.panelId.panelId}")
                            } else {
                                println("BossApp: Could not determine target panel for slot: $panelSlot")
                            }
                        } else {
                            println("BossApp: Panel item not found: ${event.panelId.panelId}")
                        }
                    } else {
                        println("BossApp: Panel info not found: ${event.panelId}")
                    }
                } catch (e: Exception) {
                    println("BossApp: Error opening panel: ${e.message}")
                }
            }
            .launchIn(this)
    }

    // Listen for Dashboard events from Fluck tabs (when Dashboard is shown in empty browser tabs)
    // Issue #506: Filter by window to prevent events affecting all windows
    LaunchedEffect(splitViewState, windowId) {
        // Handle file open events
        DashboardEventBus.openFileEvents
            .filter { event -> event.sourceWindowId == windowId }
            .onEach { event ->
                splitViewState.openFileInActivePanel(
                    event.path,
                    event.path.extractFileName().ifEmpty { "untitled" }
                )
            }
            .launchIn(this)

        // Handle URL open in new tab events
        DashboardEventBus.openUrlInNewTabEvents
            .filter { event -> event.sourceWindowId == windowId }
            .onEach { event ->
                splitViewState.openUrlInActivePanel(event.url, "Loading...")
            }
            .launchIn(this)

        // Handle new tab events
        DashboardEventBus.newTabEvents
            .filter { event -> event.sourceWindowId == windowId }
            .onEach {
                showNewTabDialog = true
            }
            .launchIn(this)

        // Handle new terminal events
        DashboardEventBus.newTerminalEvents
            .filter { event -> event.sourceWindowId == windowId }
            .onEach {
                val timestamp = System.currentTimeMillis()
                val projectPath = windowProjectState.selectedProject.value.path
                val terminalTab = ai.rever.boss.components.plugin.tab_types.TerminalTabInfo(
                    id = "terminal-$timestamp",
                    typeId = ai.rever.boss.components.plugin.tab_types.TerminalTab.typeId,
                    title = "Terminal",
                    icon = ai.rever.boss.components.plugin.tab_types.TerminalTab.icon,
                    workingDirectory = projectPath.ifEmpty { null }
                )
                splitViewState.getActiveTabsComponent()?.addTab(terminalTab)
            }
            .launchIn(this)

        // Handle project dialog events
        DashboardEventBus.showProjectDialogEvents
            .filter { event -> event.sourceWindowId == windowId }
            .onEach {
                showProjectDialog = true
            }
            .launchIn(this)

        // Handle file dialog events
        DashboardEventBus.showFileDialogEvents
            .filter { event -> event.sourceWindowId == windowId }
            .onEach {
                // File dialog is typically handled by a system file chooser
                // For now, show new tab dialog with file option
                showNewTabDialog = true
            }
            .launchIn(this)

        // Handle new project events
        DashboardEventBus.showNewProjectEvents
            .filter { event -> event.sourceWindowId == windowId }
            .onEach {
                showNewProjectDialog = true
            }
            .launchIn(this)

        // Handle split template events
        DashboardEventBus.applySplitTemplateEvents
            .filter { event -> event.sourceWindowId == windowId }
            .onEach { event ->
                // Split templates from Fluck Dashboard - apply using active panel
                val activeComponent = splitViewState.getActiveTabsComponent()
                if (activeComponent != null) {
                    val activePanelId = splitViewState.activePanelId
                    val projectPath = windowProjectState.selectedProject.value.path.ifEmpty {
                        System.getProperty("user.home")
                    }
                    // Create tabs from template panels
                    val leftPanelConfig = event.template.panels.find { it.position == "left" }
                    val rightPanelConfig = event.template.panels.find { it.position == "right" }

                    leftPanelConfig?.let { config ->
                        createTabFromTemplateConfig(config, projectPath)?.let { tab ->
                            activeComponent.addTab(tab)
                            if (rightPanelConfig != null) {
                                createTabFromTemplateConfig(rightPanelConfig, projectPath)?.let { rightTab ->
                                    splitViewState.splitPanel(
                                        panelId = activePanelId,
                                        orientation = ai.rever.boss.components.window_panel.SplitOrientation.VERTICAL,
                                        tabToMove = rightTab
                                    )
                                }
                            }
                        }
                    } ?: rightPanelConfig?.let { config ->
                        createTabFromTemplateConfig(config, projectPath)?.let { tab ->
                            activeComponent.addTab(tab)
                        }
                    }
                }
            }
            .launchIn(this)

        // Handle plugin activation events
        DashboardEventBus.activatePluginEvents
            .filter { event -> event.sourceWindowId == windowId }
            .onEach { event ->
                draggablePanelComponent.activatePlugin(event.pluginId)
            }
            .launchIn(this)
    }

    // Separate effect to handle session resolution AFTER Last Session may have loaded
    // This ensures terminal handler is marked ready even if session resolves late
    LaunchedEffect(isSessionResolved, workspaceManager.currentWorkspace.value) {
        if (isSessionResolved && workspaceManager.currentWorkspace.value != null) {
            // Session is now resolved and workspace has been loaded
            // Mark terminal handler ready if it hasn't been already
            TerminalHandlerService.markReady()
            println("BossApp: Marked terminal handler as ready (session resolved after workspace load)")
        }
    }

    // Combined LaunchedEffect for URL handling and auto-show dialog (Issue #168)
    // Uses reactive state observation with processing state tracking to eliminate race conditions
    @OptIn(kotlinx.coroutines.FlowPreview::class)
    LaunchedEffect(splitViewState, windowId) {
        // Set up URL listener for incoming URLs
        // Note: We DON'T call markAppReady() here - that happens AFTER Last Session loads
        // Issue #506: Filter by window to prevent URL opening in all windows
        ai.rever.boss.components.events.URLEventBus.urlOpenEvents
            .filter { event -> event.sourceWindowId == windowId }
            .onEach { event ->
                // sourceWindowId is required, so we already filtered to the correct window
                println("BossApp: Opening URL in window $windowId: ${event.url}")
                splitViewState.openUrlInActivePanel(event.url, event.title)
            }
            .launchIn(this)

        // Step 3: Observe tab count AND processing state (URLs + Terminals + Files + Workspace Restoration) reactively
        // This eliminates all timing assumptions by waiting for actual completion
        snapshotFlow {
            val allPanels = splitViewState.getAllPanels()
            val totalTabs = allPanels.sumOf { panel ->
                panel.tabsComponent.tabsState.value.tabs.size
            }
            val isProcessingURLs = URLHandlerService.isProcessingURLs()
            val isProcessingTerminals = TerminalHandlerService.isProcessingTerminals()
            val isProcessingFiles = FileHandlerService.isProcessingFiles()

            data class ProcessingState(
                val totalTabs: Int,
                val isProcessingURLs: Boolean,
                val isProcessingTerminals: Boolean,
                val isProcessingFiles: Boolean,
                val isRestorationComplete: Boolean
            )
            ProcessingState(totalTabs, isProcessingURLs, isProcessingTerminals, isProcessingFiles, workspaceRestorationComplete)
        }
            .debounce(200) // Wait for 200ms of stability
            .take(1)       // Only take first stabilized value
            .collect { state ->
                println("BossApp: State stabilized - tabs: ${state.totalTabs}, processing URLs: ${state.isProcessingURLs}, processing terminals: ${state.isProcessingTerminals}, processing files: ${state.isProcessingFiles}, restoration complete: ${state.isRestorationComplete} (window: $windowId)")

                // Only show dialog if no tabs AND nothing being processed AND workspace restoration is complete
                if (state.totalTabs == 0 && !state.isProcessingURLs && !state.isProcessingTerminals && !state.isProcessingFiles && state.isRestorationComplete) {
                    showNewTabDialog = true
                    println("BossApp: Auto-showing New Tab Dialog (window: $windowId, no tabs, no processing, restoration complete)")
                } else {
                    println("BossApp: Skipping auto-show (window: $windowId, tabs: ${state.totalTabs}, processing URLs: ${state.isProcessingURLs}, processing terminals: ${state.isProcessingTerminals}, processing files: ${state.isProcessingFiles}, restoration complete: ${state.isRestorationComplete})")
                }
            }
    }

    // Monitor for layout changes to mark workspace as dirty and auto-save
    LaunchedEffect(splitViewState, workspaceManager) {
        var lastWorkspaceSnapshot: LayoutWorkspace? = null
        var saveJob: Job? = null
        
        // Monitor the entire layout structure for changes
        snapshotFlow { 
            // Extract current layout workspace
            extractCurrentWorkspace(splitViewState)
        }
        .onEach { currentLayout ->
            // Check if we have a loaded workspace
            val loadedConfig = workspaceManager.currentWorkspace.value
            
            if (loadedConfig != null) {
                // Compare with the last known workspace state
                if (lastWorkspaceSnapshot == null) {
                    // First snapshot after loading
                    lastWorkspaceSnapshot = currentLayout
                } else if (currentLayout != lastWorkspaceSnapshot) {
                    // Layout has changed (splits, tabs added/removed, etc.)
                    lastWorkspaceSnapshot = currentLayout
                    
                    // Mark the current workspace as modified (if it's not "Last Session")
                    if (loadedConfig.name != "Last Session") {
                        TabTreeState.markWorkspaceAsModified(loadedConfig.id)
                    }
                    
                    // Cancel previous save job if any
                    saveJob?.cancel()
                    
                    // Auto-save to current workspace or "Last Session" after a short delay
                    saveJob = launch {
                        delay(2000) // Wait 2 seconds before saving
                        
                        if (loadedConfig.name == "Last Session") {
                            // If we're already in "Last Session", update it
                            val lastSessionConfig = currentLayout.copy(
                                id = "last-session",
                                name = "Last Session",
                                description = "Automatically saved session"
                            )
                            workspaceManager.updateCurrentWorkspace(lastSessionConfig)
                            workspaceManager.saveCurrentWorkspace("Last Session")
                        } else {
                            // Update the current loaded workspace with changes
                            val updatedConfig = loadedConfig.copy(
                                layout = currentLayout.layout,
                                timestamp = Clock.System.now().toEpochMilliseconds()
                            )
                            workspaceManager.updateCurrentWorkspace(updatedConfig)
                            workspaceManager.saveCurrentWorkspace()
                            
                            // Clear the modified state since we just auto-saved
                            TabTreeState.markWorkspaceAsSaved(loadedConfig.id)
                        }
                    }
                }
            } else {
                // No workspace loaded, but still save as "Last Session"
                if (currentLayout != lastWorkspaceSnapshot) {
                    lastWorkspaceSnapshot = currentLayout
                    
                    // Cancel previous save job if any
                    saveJob?.cancel()
                    
                    // Auto-save as "Last Session" after a short delay
                    saveJob = launch {
                        delay(2000) // Wait 2 seconds before saving
                        val lastSessionConfig = currentLayout.copy(
                            id = "last-session",
                            name = "Last Session",
                            description = "Automatically saved session"
                        )
                        workspaceManager.updateCurrentWorkspace(lastSessionConfig)
                        workspaceManager.saveCurrentWorkspace("Last Session")
                    }
                }
            }
        }
        .launchIn(this)
        
        // Reset snapshot when workspace changes
        workspaceManager.currentWorkspace
            .onEach { config ->
                if (config != null && config.name != "Last Session") {
                    // Workspace loaded (but not Last Session), reset tracking
                    lastWorkspaceSnapshot = null
                    // Clear modified status when loading a workspace
                    TabTreeState.markWorkspaceAsSaved(config.id)
                }
            }
            .launchIn(this)
    }
    
    // Listen for panel close events
    // Issue #506: Filter by window to prevent panel closing in all windows
    LaunchedEffect(draggablePanelComponent, windowId) {
        PanelEventBus.panelCloseEvents
            .filter { event -> event.sourceWindowId == windowId }
            .onEach { event ->
                // Find which panel contains this component
                val panels = listOf(
                    bottom,
                    left.top,
                    left.bottom,
                    right.top,
                    right.bottom
                )

                for (panel in panels) {
                    val panelContentId = draggablePanelComponent.getPanelContentId(panel)
                    if (panelContentId == event.panelId) {
                        draggablePanelComponent.setPanelVisible(panel, false)
                        // Remove the component from store to ensure fresh instance next time
                        panelComponentStore.removeComponent(event.panelId)
                        break
                    }
                }
            }
            .launchIn(this)
    }

    // Listen for panel toggle events (open if closed, close if open)
    // Issue #506: Filter by window to prevent panel toggling in all windows
    LaunchedEffect(draggablePanelComponent, panelRegistry, windowId) {
        PanelEventBus.panelToggleEvents
            .filter { event -> event.sourceWindowId == windowId }
            .onEach { event ->
                try {
                    val panels = listOf(
                        bottom,
                        left.top,
                        left.bottom,
                        right.top,
                        right.bottom
                    )

                    // Check if the panel is currently visible with this content
                    var foundVisible = false
                    for (panel in panels) {
                        val panelContentId = draggablePanelComponent.getPanelContentId(panel)
                        if (panelContentId?.panelId == event.panelId.panelId &&
                            draggablePanelComponent.isVisible(panel)) {
                            // Panel is visible - close it
                            draggablePanelComponent.setPanelVisible(panel, false)
                            panelComponentStore.removeComponent(event.panelId)
                            println("BossApp: Toggled panel closed: ${event.panelId.panelId}")
                            foundVisible = true
                            break
                        }
                    }

                    if (!foundVisible) {
                        // Panel is not visible - open it using the same logic as panelOpenEvents
                        val panelInfo = panelRegistry.getAllPanels().find {
                            it.id.panelId == event.panelId.panelId &&
                            it.id.pluginId == event.panelId.pluginId
                        }

                        if (panelInfo != null) {
                            val panelSlot = panelInfo.defaultSlotPosition
                            val panelItems = draggablePanelComponent.getItemsForSlot(panelSlot)
                            val targetItem = panelItems.find { it.pluginContentId.panelId == event.panelId.panelId }

                            if (targetItem != null) {
                                draggablePanelComponent.onClick.invoke(targetItem)
                                println("BossApp: Toggled panel open: ${event.panelId.panelId}")
                            }
                        }
                    }
                } catch (e: Exception) {
                    println("BossApp: Error toggling panel: ${e.message}")
                }
            }
            .launchIn(this)
    }

    // Listen for menu actions from MenuBar (File > New Tab, etc.)
    LaunchedEffect(windowId) {
        ai.rever.boss.window.MenuActionsHandler.newTabEvents
            .onEach { eventWindowId ->
                if (eventWindowId == windowId) {
                    // Show new tab dialog when menu item is clicked
                    showNewTabDialog = true
                }
            }
            .launchIn(this)
    }

    LaunchedEffect(windowId) {
        ai.rever.boss.window.MenuActionsHandler.closeTabEvents
            .onEach { eventWindowId ->
                if (eventWindowId == windowId) {
                    // Close current tab when menu item is clicked
                    val activeTabsComponent = splitViewState.getPanelTabsComponent(splitViewState.activePanelId)
                    if (activeTabsComponent != null) {
                        val tabs = activeTabsComponent.tabsState.value.tabs
                        val activeIndex = activeTabsComponent.tabsState.value.activeIndex
                        if (activeIndex >= 0 && activeIndex < tabs.size) {
                            activeTabsComponent.removeTab(activeIndex)

                            // Check if all panels in window are now empty
                            val allPanels = splitViewState.getAllPanels()
                            val totalTabs = allPanels.sumOf { panel ->
                                panel.tabsComponent.tabsState.value.tabs.size
                            }

                            // If no tabs remaining in any panel, close the window
                            if (totalTabs == 0) {
                                ai.rever.boss.window.WindowOperations.closeWindow(windowId)
                            }
                        }
                    }
                }
            }
            .launchIn(this)
    }

    // Listen for zoom menu actions
    LaunchedEffect(windowId) {
        ai.rever.boss.window.MenuActionsHandler.zoomInEvents
            .onEach { eventWindowId ->
                if (eventWindowId == windowId) {
                    val activeTabsComponent = splitViewState.getPanelTabsComponent(splitViewState.activePanelId)
                    val activeTab = activeTabsComponent?.getActiveComponent()
                    if (activeTab is ai.rever.boss.components.plugin.tab_types.fluck.FluckTabComponent) {
                        activeTab.zoomIn()
                    }
                }
            }
            .launchIn(this)
    }

    LaunchedEffect(windowId) {
        ai.rever.boss.window.MenuActionsHandler.zoomOutEvents
            .onEach { eventWindowId ->
                if (eventWindowId == windowId) {
                    val activeTabsComponent = splitViewState.getPanelTabsComponent(splitViewState.activePanelId)
                    val activeTab = activeTabsComponent?.getActiveComponent()
                    if (activeTab is ai.rever.boss.components.plugin.tab_types.fluck.FluckTabComponent) {
                        activeTab.zoomOut()
                    }
                }
            }
            .launchIn(this)
    }

    LaunchedEffect(windowId) {
        ai.rever.boss.window.MenuActionsHandler.actualSizeEvents
            .onEach { eventWindowId ->
                if (eventWindowId == windowId) {
                    val activeTabsComponent = splitViewState.getPanelTabsComponent(splitViewState.activePanelId)
                    val activeTab = activeTabsComponent?.getActiveComponent()
                    if (activeTab is ai.rever.boss.components.plugin.tab_types.fluck.FluckTabComponent) {
                        activeTab.actualSize()
                    }
                }
            }
            .launchIn(this)
    }

    // Handle new File menu events
    LaunchedEffect(windowId) {
        ai.rever.boss.window.MenuActionsHandler.openProjectEvents
            .onEach { eventWindowId ->
                if (eventWindowId == windowId) {
                    showProjectDialog = true
                }
            }
            .launchIn(this)
    }

    LaunchedEffect(windowId) {
        ai.rever.boss.window.MenuActionsHandler.openFileEvents
            .onEach { eventWindowId ->
                if (eventWindowId == windowId) {
                    // Open file tab selection - show new tab dialog with File tab pre-selected
                    newTabDialogInitialType = ai.rever.boss.components.dialogs.TabType.FILE
                    showNewTabDialog = true
                }
            }
            .launchIn(this)
    }

    LaunchedEffect(windowId) {
        ai.rever.boss.window.MenuActionsHandler.newTerminalEvents
            .onEach { eventWindowId ->
                if (eventWindowId == windowId) {
                    // Directly create and open terminal tab
                    val activeTabsComponent = splitViewState.getPanelTabsComponent(splitViewState.activePanelId)
                    activeTabsComponent?.let { component ->
                        // Get current project path for terminal working directory
                        val projectPath = ai.rever.boss.components.plugin.panels.left_top.ProjectState.selectedProject.value.path
                        val terminalTab = TerminalTabInfo(
                            id = "terminal-${Random.nextLong()}",
                            typeId = TerminalTab.typeId,
                            title = "Terminal",
                            workingDirectory = projectPath.ifEmpty { null }
                        )
                        component.addTab(terminalTab)
                    }
                }
            }
            .launchIn(this)
    }

    LaunchedEffect(windowId) {
        ai.rever.boss.window.MenuActionsHandler.selectWorkspaceEvents
            .onEach { eventWindowId ->
                if (eventWindowId == windowId) {
                    showTopOfMindDialog = true
                }
            }
            .launchIn(this)
    }

    LaunchedEffect(windowId, workspaceManager, splitViewState) {
        ai.rever.boss.window.MenuActionsHandler.applyWorkspaceEvents
            .onEach { (eventWindowId, workspace) ->
                if (eventWindowId == windowId) {
                    // Load workspace into manager
                    workspaceManager.loadWorkspace(workspace)

                    // Apply workspace to UI
                    applyWorkspace(workspace, splitViewState)
                }
            }
            .launchIn(this)
    }

    LaunchedEffect(windowId) {
        ai.rever.boss.window.MenuActionsHandler.openSettingsEvents
            .onEach { eventWindowId ->
                if (eventWindowId == windowId) {
                    showSettingsDialog = true
                }
            }
            .launchIn(this)
    }

    // Handle global settings events (from terminal panels, etc.)
    LaunchedEffect(Unit) {
        ai.rever.boss.window.MenuActionsHandler.globalOpenSettingsEvents
            .onEach { section ->
                settingsInitialSection = section
                showSettingsDialog = true
            }
            .launchIn(this)
    }

    // Handle View menu events
    LaunchedEffect(windowId) {
        ai.rever.boss.window.MenuActionsHandler.toggleFocusModeEvents
            .onEach { eventWindowId ->
                if (eventWindowId == windowId) {
                    coroutineScope.launch {
                        FocusModeSettingsManager.toggleFocusMode()
                    }
                }
            }
            .launchIn(this)
    }

    LaunchedEffect(windowId) {
        ai.rever.boss.window.MenuActionsHandler.splitVerticallyEvents
            .onEach { eventWindowId ->
                if (eventWindowId == windowId) {
                    // Copy the active tab to the new panel to prevent empty panel auto-close
                    val currentTab = splitViewState.getActiveTabsComponent()?.getCurrentTab()
                    splitViewState.splitPanel(
                        panelId = splitViewState.activePanelId,
                        orientation = ai.rever.boss.components.window_panel.SplitOrientation.VERTICAL,
                        tabToMove = currentTab
                    )
                }
            }
            .launchIn(this)
    }

    LaunchedEffect(windowId) {
        ai.rever.boss.window.MenuActionsHandler.splitHorizontallyEvents
            .onEach { eventWindowId ->
                if (eventWindowId == windowId) {
                    // Copy the active tab to the new panel to prevent empty panel auto-close
                    val currentTab = splitViewState.getActiveTabsComponent()?.getCurrentTab()
                    splitViewState.splitPanel(
                        panelId = splitViewState.activePanelId,
                        orientation = ai.rever.boss.components.window_panel.SplitOrientation.HORIZONTAL,
                        tabToMove = currentTab
                    )
                }
            }
            .launchIn(this)
    }

    // Track whether split is enabled (has tabs in active panel)
    val activePanelId by splitViewState.activePanelIdState
    val activeTabsComponent = splitViewState.getActiveTabsComponent()
    val hasActiveTabs = activeTabsComponent?.tabsState?.value?.tabs?.isNotEmpty() == true
    LaunchedEffect(windowId, activePanelId, hasActiveTabs) {
        ai.rever.boss.window.MenuActionsHandler.updateSplitEnabled(windowId, hasActiveTabs)
    }

    // Handle Plugin menu events
    LaunchedEffect(windowId) {
        ai.rever.boss.window.MenuActionsHandler.revealPluginEvents
            .onEach { (eventWindowId, pluginId) ->
                if (eventWindowId == windowId) {
                    // Activate the plugin (same as clicking its sidebar icon)
                    draggablePanelComponent.activatePlugin(pluginId)
                }
            }
            .launchIn(this)
    }

    with(draggablePanelComponent) {
        BossTheme {
            CompositionLocalProvider(
                LocalWindowId provides windowId,
                LocalSplitViewState provides splitViewState,
                LocalWorkspaceManager provides workspaceManager,
                LocalWindowProjectState provides windowProjectState
            ) {
                Box(modifier = Modifier
                .fillMaxSize()
                .focusRequester(focusRequester)
                .focusable()
                .onPreviewKeyEvent { event ->
                    // Emit keyboard events to the event bus for priority-based handling
                    // Child components (terminal, browser) consume their own events first
                    // Events that reach here are passed to the KeyboardEventBus subscribers
                    if (event.type == KeyEventType.KeyDown) {
                        // Determine current context based on active tab type
                        val activeTabsComponent = splitViewState.getPanelTabsComponent(splitViewState.activePanelId)
                        val activeTab = activeTabsComponent?.tabsState?.value?.activeTab
                        val currentContext = when {
                            activeTab is FluckTabInfo -> ShortcutContext.BROWSER
                            activeTab is TerminalTabInfo -> ShortcutContext.TERMINAL
                            activeTab is EditorTabInfo -> ShortcutContext.EDITOR
                            else -> ShortcutContext.WORKSPACE
                        }

                        // Emit to KeyboardEventBus for priority-based handling
                        coroutineScope.launch {
                            KeyboardEventBus.emit(
                                BossKeyboardEvent(
                                    keyEvent = event,
                                    source = KeyEventSource.WORKSPACE,
                                    context = currentContext
                                )
                            )
                        }

                        // Don't consume the event - let KeyboardEventBus handlers decide
                        false
                    } else {
                        false
                    }
                }
            ) { // Use Box to allow overlaying the drag ghost
                Column(modifier = Modifier.fillMaxSize()) {
                    // Title bar - conditionally shown based on settings
                    // Default: hidden on Linux/Windows, shown on macOS
                    if (showTitleBarSetting) {
                        BossTitleBar(
                            onToggleMaximize = onToggleMaximize
                        )
                    }

                    // Update banner - always visible (even in focus mode)
                    val updateState by UpdateManager.instance.updateState.collectAsState()
                    UpdateBanner(
                        updateState = updateState,
                        onCheckForUpdates = {
                            coroutineScope.launch {
                                UpdateManager.instance.checkForUpdates()
                            }
                        },
                        onDownloadUpdate = { updateInfo ->
                            coroutineScope.launch {
                                UpdateManager.instance.downloadUpdate(updateInfo)
                            }
                        },
                        onInstallUpdate = { downloadPath ->
                            coroutineScope.launch {
                                val success = UpdateManager.instance.installUpdate(downloadPath)
                                if (success) {
                                    // Optionally restart the application here
                                    // ApplicationRestarter.restart()
                                }
                            }
                        },
                        onDismiss = {
                            UpdateManager.instance.resetState()
                        }
                    )

                    // Top bar - hidden in focus mode with smooth expand/shrink animation
                    AnimatedVisibility(
                        visible = showTopBar,
                        enter = expandVertically(
                            expandFrom = Alignment.Top,
                            animationSpec = tween(durationMillis = 250)
                        ),
                        exit = shrinkVertically(
                            shrinkTowards = Alignment.Top,
                            animationSpec = tween(durationMillis = 250)
                        )
                    ) {
                        Box(
                            modifier = Modifier.hoverable(interactionSource = topBarInteractionSource)
                        ) {
                            BossTopBar(
                                workspaceManager = workspaceManager,
                                onApplyWorkspace = { workspace ->
                                    coroutineScope.launch {
                                        // Preserve current state before switching
                                        val currentWorkspace = workspaceManager.currentWorkspace.value
                                        if (currentWorkspace != null && currentWorkspace.id.isNotEmpty()) {
                                            splitViewState.preserveCurrentState(currentWorkspace.id, currentWorkspace.name)
                                        }

                                        // First load the workspace to reset dirty state
                                        workspaceManager.loadWorkspace(workspace)
                                        // Then apply it to the UI (which will try to restore preserved state)
                                        applyWorkspace(workspace, splitViewState)
                                    }
                                },
                                getCurrentWorkspace = {
                                    extractCurrentWorkspace(splitViewState)
                                },
                                onShowTopOfMind = {
                                    showTopOfMindDialog = true
                                },
                                onShowSettings = {
                                    showSettingsDialog = true
                                },
                                onNewProject = {
                                    showNewProjectDialog = true
                                }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.weight(1f)
                    ) {
                        // Left sidebar - hidden in focus mode with smooth expand/shrink animation
                        AnimatedVisibility(
                            visible = showLeftSidebar,
                            enter = expandHorizontally(
                                expandFrom = Alignment.Start,
                                animationSpec = tween(durationMillis = 250)
                            ),
                            exit = shrinkHorizontally(
                                shrinkTowards = Alignment.Start,
                                animationSpec = tween(durationMillis = 250)
                            )
                        ) {
                            Box(
                                modifier = Modifier.hoverable(interactionSource = leftSidebarInteractionSource)
                            ) {
                                BossLeftSideBar()
                            }
                        }

                        // Main content area - always visible (contains tabs)
                        BossWindow(
                            modifier = Modifier.weight(1f),
                            tabsComponent = tabsComponent,
                            panelComponentStore = panelComponentStore,
                            splitViewState = splitViewState,
                            tabDragComponent = tabDragComponent,
                            onTabDropResult = { result ->
                                handleTabDropResult(result, splitViewState)
                            },
                            onShowSettings = { showSettingsDialog = true },
                            onOpenProjectDialog = { showProjectDialog = true },
                            onNewProject = { showNewProjectDialog = true }
                        )

                        // Right sidebar - hidden in focus mode with smooth expand/shrink animation
                        AnimatedVisibility(
                            visible = showRightSidebar,
                            enter = expandHorizontally(
                                expandFrom = Alignment.End,
                                animationSpec = tween(durationMillis = 250)
                            ),
                            exit = shrinkHorizontally(
                                shrinkTowards = Alignment.End,
                                animationSpec = tween(durationMillis = 250)
                            )
                        ) {
                            Box(
                                modifier = Modifier.hoverable(interactionSource = rightSidebarInteractionSource)
                            ) {
                                BossRightSideBar()
                            }
                        }
                    }

                    // Bottom bar - hidden in focus mode with smooth expand/shrink animation
                    AnimatedVisibility(
                        visible = showBottomBar,
                        enter = expandVertically(
                            expandFrom = Alignment.Bottom,
                            animationSpec = tween(durationMillis = 250)
                        ),
                        exit = shrinkVertically(
                            shrinkTowards = Alignment.Bottom,
                            animationSpec = tween(durationMillis = 250)
                        )
                    ) {
                        Box(
                            modifier = Modifier.hoverable(interactionSource = bottomBarInteractionSource)
                        ) {
                            BossBottomBar(splitViewState.getActiveTabsComponent())
                        }
                    }
                }

                // Hover reveal strips for focus mode - dynamic sizing to avoid blocking clicks
                // Top hover strip - uses revealOffsetPx when hidden, 1dp when visible (doesn't block clicks)
                if (isFocusModeEnabled && isAutoRevealEnabled) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(if (showTopBar) 1.dp else revealOffsetDp)
                            .align(Alignment.TopStart)
                            .zIndex(10f)
                            .background(Color.Transparent)
                            .onPointerEvent(androidx.compose.ui.input.pointer.PointerEventType.Enter) {
                                hoveringTopStrip = true
                            }
                            .onPointerEvent(androidx.compose.ui.input.pointer.PointerEventType.Exit) {
                                hoveringTopStrip = false
                            }
                    )
                }

                // Left hover strip - uses revealOffsetPx when hidden, 1dp when visible (doesn't block clicks)
                if (isFocusModeEnabled && isAutoRevealEnabled) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(if (showLeftSidebar) 1.dp else revealOffsetDp)
                            .align(Alignment.CenterStart)
                            .zIndex(10f)
                            .background(Color.Transparent)
                            .onPointerEvent(androidx.compose.ui.input.pointer.PointerEventType.Enter) {
                                hoveringLeftStrip = true
                            }
                            .onPointerEvent(androidx.compose.ui.input.pointer.PointerEventType.Exit) {
                                hoveringLeftStrip = false
                            }
                    )
                }

                // Right hover strip - uses revealOffsetPx when hidden, 1dp when visible (doesn't block clicks)
                if (isFocusModeEnabled && isAutoRevealEnabled) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(if (showRightSidebar) 1.dp else revealOffsetDp)
                            .align(Alignment.CenterEnd)
                            .zIndex(10f)
                            .background(Color.Transparent)
                            .onPointerEvent(androidx.compose.ui.input.pointer.PointerEventType.Enter) {
                                hoveringRightStrip = true
                            }
                            .onPointerEvent(androidx.compose.ui.input.pointer.PointerEventType.Exit) {
                                hoveringRightStrip = false
                            }
                    )
                }

                // Bottom hover strip - uses revealOffsetPx when hidden, 1dp when visible (doesn't block clicks)
                if (isFocusModeEnabled && isAutoRevealEnabled) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(if (showBottomBar) 1.dp else revealOffsetDp)
                            .align(Alignment.BottomStart)
                            .zIndex(10f)
                            .background(Color.Transparent)
                            .onPointerEvent(androidx.compose.ui.input.pointer.PointerEventType.Enter) {
                                hoveringBottomStrip = true
                            }
                            .onPointerEvent(androidx.compose.ui.input.pointer.PointerEventType.Exit) {
                                hoveringBottomStrip = false
                            }
                    )
                }

                // Draw the dragging item overlay (ghost) if an item is being dragged
                DraggingItemOverlay()

                // Draw the tab dragging overlay (ghost tab) if a tab is being dragged
                tabDragComponent.TabDraggingOverlay()
            }
            
            // Show new tab dialog
            if (showNewTabDialog) {
                NewTabDialog(
                    onDismiss = {
                        showNewTabDialog = false
                        newTabDialogInitialType = null
                        focusRequester.requestFocus()
                    },
                    onCreateTab = { type, path ->
                        // Get the active panel component first, fallback to last interacted, then original
                        val targetComponent = splitViewState.getActiveTabsComponent() 
                            ?: splitViewState.getLastInteractedTabComponent() 
                            ?: tabsComponent
                        
                        when (type) {
                            TabType.URL -> {
                                val tab = FluckTabInfo(
                                    id = "browser-${Random.nextLong()}",
                                    typeId = TabTypeId("fluck"),
                                    _title = "Loading...",
                                    url = path
                                )
                                targetComponent.addTab(tab)
                            }
                            TabType.FILE -> {
                                val fileName = path.extractFileName()
                                val fileIconInfo = FileIcons.forFile(fileName)
                                val tab = EditorTabInfo(
                                    id = "editor-${Random.nextLong()}",
                                    typeId = TabTypeId("editor"),
                                    title = fileName,
                                    icon = fileIconInfo.icon,
                                    tabIcon = TabIcon.Vector(fileIconInfo.icon, fileIconInfo.color),
                                    filePath = path
                                )
                                targetComponent.addTab(tab)
                            }
                            TabType.TERMINAL -> {
                                // Get current project path for terminal working directory
                                val projectPath = ai.rever.boss.components.plugin.panels.left_top.ProjectState.selectedProject.value.path
                                val tab = TerminalTabInfo(
                                    id = "terminal-${Random.nextLong()}",
                                    typeId = TerminalTab.typeId,
                                    title = "Terminal",
                                    workingDirectory = projectPath.ifEmpty { null }
                                )
                                targetComponent.addTab(tab)
                            }
                        }
                        // Reset the initial type after tab creation
                        newTabDialogInitialType = null
                    },
                    initialTabType = newTabDialogInitialType
                )
            }

            // Top of mind quick switcher dialog
            if (showTopOfMindDialog) {
                TopOfMindDialog(
                    splitViewState = splitViewState,
                    workspaceManager = workspaceManager,
                    onDismiss = {
                        showTopOfMindDialog = false
                        focusRequester.requestFocus()
                    },
                    onTabSelect = { activeTab ->
                        showTopOfMindDialog = false
                        coroutineScope.launch {
                            // Preserve current state before switching
                            val currentWorkspace = workspaceManager.currentWorkspace.value
                            if (currentWorkspace != null && currentWorkspace.id.isNotEmpty()) {
                                splitViewState.preserveCurrentState(currentWorkspace.id, currentWorkspace.name)
                            }

                            // Find the workspace containing this tab
                            val targetWorkspace = workspaceManager.workspaces.value.find {
                                it.id == activeTab.workspaceId
                            }

                            if (targetWorkspace != null) {
                                // Load and apply the target workspace
                                workspaceManager.loadWorkspace(targetWorkspace)
                                applyWorkspace(targetWorkspace, splitViewState)

                                // Focus the specific tab after a short delay to ensure workspace is applied
                                delay(100)
                                splitViewState.selectTabInPanel(activeTab.tabInfo.id, activeTab.panelId)
                            }
                        }

                        focusRequester.requestFocus()
                    }
                )
            }

            // Settings Window - always available, even in focus mode
            if (showSettingsDialog) {
                SettingsWindow(
                    onClose = {
                        showSettingsDialog = false
                        settingsInitialSection = null
                    },
                    initialSection = settingsInitialSection
                )
            }

            // Terminal link open dialog (Issue #346)
            if (showTerminalLinkDialog) {
                TerminalLinkOpenDialog(
                    url = pendingTerminalLinkUrl,
                    hasTabs = splitViewState.hasTabs(),
                    hasSplits = splitViewState.hasSplits(),
                    onDismiss = {
                        showTerminalLinkDialog = false
                        pendingTerminalLinkUrl = ""
                        pendingTerminalSourceId = null
                    },
                    onOpenLink = { mode, rememberChoice ->
                        showTerminalLinkDialog = false

                        // Save preference if user wants to remember
                        if (rememberChoice) {
                            coroutineScope.launch {
                                TerminalLinkSettingsManager.setOpenMode(mode)
                            }
                        }

                        // Open the link using helper function
                        // Issue #506: Pass windowId for multi-window navigation filtering
                        openTerminalLink(pendingTerminalLinkUrl, mode, splitViewState, pendingTerminalSourceId, coroutineScope, windowId = windowId)
                        pendingTerminalLinkUrl = ""
                        pendingTerminalSourceId = null
                    }
                )
            }

            // Directory picker for project selection (must be outside conditional for Compose)
            val directoryPicker = ai.rever.boss.platform.rememberDirectoryPicker { path ->
                path?.let {
                    val projectName = it.extractFileName().ifEmpty { "Unknown" }
                    ai.rever.boss.components.plugin.panels.left_top.ProjectState.selectProject(
                        ai.rever.boss.components.plugin.panels.left_top.Project(
                            name = projectName,
                            path = it
                        )
                    )
                    // Show CodeBase panel when project is selected
                    draggablePanelComponent.setPanelVisible(
                        ai.rever.boss.components.model.Panel.Companion.left.top,
                        true
                    )
                    // Close the dialog after selection
                    showProjectDialog = false
                }
            }

            // Project selection dialog (triggered from File > Open Project menu)
            // Note: Dialog handles empty recentProjects case internally by opening directory picker directly
            if (showProjectDialog) {
                ai.rever.boss.components.dialogs.ProjectSelectionDialog(
                    onDismiss = { showProjectDialog = false },
                    onOpenDirectoryPicker = {
                        showProjectDialog = false
                        directoryPicker.pickDirectory()
                    }
                )
            }

            // New project wizard dialog (Issue #436)
            if (showNewProjectDialog) {
                NewProjectWizardDialog(
                    onDismiss = {
                        showNewProjectDialog = false
                        focusRequester.requestFocus()
                    },
                    onProjectCreated = { project ->
                        selectProjectInWindow(windowProjectState, project)
                        showNewProjectDialog = false
                        focusRequester.requestFocus()
                    }
                )
            }

            // Save feedback snackbar
            saveMessage?.let { message ->
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Surface(
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colors.primary,
                        shape = RoundedCornerShape(8.dp),
                        elevation = 8.dp
                    ) {
                        Text(
                            text = message,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            color = MaterialTheme.colors.onPrimary,
                            style = MaterialTheme.typography.body2
                        )
                    }
                }
            }
            }
        }
    }
}

/**
 * Create a tab from template panel configuration.
 * Used by DashboardEventBus handlers for split template events from Fluck Dashboard.
 */
private fun createTabFromTemplateConfig(
    panelConfig: ai.rever.boss.dashboard.TemplatePanelConfig,
    projectPath: String
): ai.rever.boss.components.registery.TabInfo? {
    val timestamp = System.currentTimeMillis()

    return when (panelConfig.type) {
        "terminal" -> {
            val command = panelConfig.content.command?.let {
                ai.rever.boss.dashboard.SplitTemplatesManager.processPlaceholders(it, projectPath, null)
            }
            ai.rever.boss.components.plugin.tab_types.TerminalTabInfo(
                id = "terminal-$timestamp",
                typeId = ai.rever.boss.components.plugin.tab_types.TerminalTab.typeId,
                title = command?.substringBefore(" ")?.extractFileName() ?: "Terminal",
                icon = ai.rever.boss.components.plugin.tab_types.TerminalTab.icon,
                workingDirectory = projectPath,
                initialCommand = command
            )
        }
        "browser" -> {
            val url = panelConfig.content.url ?: ""
            ai.rever.boss.components.plugin.tab_types.fluck.FluckTabInfo(
                id = "fluck-$timestamp",
                typeId = ai.rever.boss.components.plugin.tab_types.fluck.Fluck.typeId,
                _title = "Loading...",
                url = url
            )
        }
        "editor" -> {
            val filePath = panelConfig.content.filePath?.let {
                ai.rever.boss.dashboard.SplitTemplatesManager.processPlaceholders(it, projectPath, null)
            } ?: return null
            ai.rever.boss.components.plugin.tab_types.EditorTabInfo(
                id = "editor-$timestamp",
                typeId = ai.rever.boss.components.plugin.tab_types.CodeEditor.typeId,
                title = filePath.extractFileName(),
                icon = ai.rever.boss.components.plugin.tab_types.CodeEditor.icon,
                filePath = filePath
            )
        }
        else -> null
    }
}



