package ai.rever.boss.keymap.model

/**
 * Registry of all keyboard shortcut action IDs in BOSS.
 *
 * Action IDs follow the pattern: "category.action"
 * Examples: "window.new", "tab.close", "browser.reload"
 */
object KeymapActions {
    // Window Management Actions
    const val WINDOW_NEW = "window.new"
    const val WINDOW_CLOSE = "window.close"

    // Tab Management Actions
    const val TAB_NEW = "tab.new"
    const val TAB_CLOSE = "tab.close"

    // Browser Control Actions
    const val BROWSER_RELOAD = "browser.reload"
    const val BROWSER_ZOOM_RESET = "browser.zoom_reset"
    const val BROWSER_ZOOM_IN = "browser.zoom_in"
    const val BROWSER_ZOOM_OUT = "browser.zoom_out"

    // Navigation Actions
    const val PANEL_NAVIGATE_LEFT = "panel.navigate_left"
    const val PANEL_NAVIGATE_RIGHT = "panel.navigate_right"
    const val PANEL_NAVIGATE_UP = "panel.navigate_up"
    const val PANEL_NAVIGATE_DOWN = "panel.navigate_down"
    const val QUICK_SWITCHER_OPEN = "quick_switcher.open"

    // Workspace Actions
    const val WORKSPACE_SAVE = "workspace.save"

    // Panel/Tool Actions
    const val CODEBASE_OPEN = "codebase.open"

    // Test/Debug Actions
    const val TEST_EXTERNAL_LINK = "test.external_link"

    /**
     * Category definitions for organizing shortcuts in the UI.
     */
    object Categories {
        const val WINDOW_MANAGEMENT = "Window Management"
        const val TAB_MANAGEMENT = "Tab Management"
        const val BROWSER_CONTROLS = "Browser Controls"
        const val NAVIGATION = "Navigation"
        const val WORKSPACE = "Workspace"
        const val TOOLS = "Tools"
        const val DEBUG = "Debug"
    }

    /**
     * Human-readable descriptions for each action.
     */
    val descriptions = mapOf(
        WINDOW_NEW to "Create a new application window",
        WINDOW_CLOSE to "Close the current window",
        TAB_NEW to "Open new tab dialog",
        TAB_CLOSE to "Close the current tab (or window if last tab)",
        BROWSER_RELOAD to "Reload the current browser tab",
        BROWSER_ZOOM_RESET to "Reset browser zoom to 100%",
        BROWSER_ZOOM_IN to "Increase browser zoom level",
        BROWSER_ZOOM_OUT to "Decrease browser zoom level",
        PANEL_NAVIGATE_LEFT to "Switch to the left/previous panel",
        PANEL_NAVIGATE_RIGHT to "Switch to the right/next panel",
        PANEL_NAVIGATE_UP to "Switch to the previous panel (upward)",
        PANEL_NAVIGATE_DOWN to "Switch to the next panel (downward)",
        QUICK_SWITCHER_OPEN to "Open quick switcher (Top of Mind)",
        WORKSPACE_SAVE to "Save the current workspace layout",
        CODEBASE_OPEN to "Open CodeBase panel",
        TEST_EXTERNAL_LINK to "Test external link handling (debug)"
    )

    /**
     * Category mapping for each action.
     */
    val categories = mapOf(
        WINDOW_NEW to Categories.WINDOW_MANAGEMENT,
        WINDOW_CLOSE to Categories.WINDOW_MANAGEMENT,
        TAB_NEW to Categories.TAB_MANAGEMENT,
        TAB_CLOSE to Categories.TAB_MANAGEMENT,
        BROWSER_RELOAD to Categories.BROWSER_CONTROLS,
        BROWSER_ZOOM_RESET to Categories.BROWSER_CONTROLS,
        BROWSER_ZOOM_IN to Categories.BROWSER_CONTROLS,
        BROWSER_ZOOM_OUT to Categories.BROWSER_CONTROLS,
        PANEL_NAVIGATE_LEFT to Categories.NAVIGATION,
        PANEL_NAVIGATE_RIGHT to Categories.NAVIGATION,
        PANEL_NAVIGATE_UP to Categories.NAVIGATION,
        PANEL_NAVIGATE_DOWN to Categories.NAVIGATION,
        QUICK_SWITCHER_OPEN to Categories.NAVIGATION,
        WORKSPACE_SAVE to Categories.WORKSPACE,
        CODEBASE_OPEN to Categories.TOOLS,
        TEST_EXTERNAL_LINK to Categories.DEBUG
    )

    /**
     * Context mapping for each action.
     */
    val contexts = mapOf(
        WINDOW_NEW to ShortcutContext.GLOBAL,
        WINDOW_CLOSE to ShortcutContext.GLOBAL,
        TAB_NEW to ShortcutContext.GLOBAL,
        TAB_CLOSE to ShortcutContext.GLOBAL,
        BROWSER_RELOAD to ShortcutContext.BROWSER,
        BROWSER_ZOOM_RESET to ShortcutContext.BROWSER,
        BROWSER_ZOOM_IN to ShortcutContext.BROWSER,
        BROWSER_ZOOM_OUT to ShortcutContext.BROWSER,
        PANEL_NAVIGATE_LEFT to ShortcutContext.GLOBAL,
        PANEL_NAVIGATE_RIGHT to ShortcutContext.GLOBAL,
        PANEL_NAVIGATE_UP to ShortcutContext.GLOBAL,
        PANEL_NAVIGATE_DOWN to ShortcutContext.GLOBAL,
        QUICK_SWITCHER_OPEN to ShortcutContext.GLOBAL,
        WORKSPACE_SAVE to ShortcutContext.WORKSPACE,
        CODEBASE_OPEN to ShortcutContext.GLOBAL,
        TEST_EXTERNAL_LINK to ShortcutContext.GLOBAL
    )

    /**
     * Get all registered action IDs.
     */
    fun getAllActionIds(): List<String> = listOf(
        WINDOW_NEW, WINDOW_CLOSE,
        TAB_NEW, TAB_CLOSE,
        BROWSER_RELOAD, BROWSER_ZOOM_RESET, BROWSER_ZOOM_IN, BROWSER_ZOOM_OUT,
        PANEL_NAVIGATE_LEFT, PANEL_NAVIGATE_RIGHT, PANEL_NAVIGATE_UP, PANEL_NAVIGATE_DOWN, QUICK_SWITCHER_OPEN,
        WORKSPACE_SAVE,
        CODEBASE_OPEN,
        TEST_EXTERNAL_LINK
    )

    /**
     * Get description for an action ID.
     */
    fun getDescription(actionId: String): String = descriptions[actionId] ?: "Unknown action"

    /**
     * Get category for an action ID.
     */
    fun getCategory(actionId: String): String = categories[actionId] ?: Categories.TOOLS

    /**
     * Get context for an action ID.
     */
    fun getContext(actionId: String): ShortcutContext = contexts[actionId] ?: ShortcutContext.GLOBAL
}
