package ai.rever.boss.window

import ai.rever.boss.keymap.KeymapSettingsManager
import ai.rever.boss.keymap.handler.KeymapMatcher
import ai.rever.boss.keymap.model.KeyBinding
import ai.rever.boss.keymap.model.KeymapActions
import ai.rever.boss.keymap.model.ShortcutContext
import ai.rever.boss.utils.SystemUtils
import java.awt.KeyEventDispatcher
import java.awt.KeyboardFocusManager
import java.awt.Window
import java.awt.event.KeyEvent
import java.util.concurrent.ConcurrentHashMap

/**
 * AWT-level keyboard interceptor that captures keyboard shortcuts before they reach
 * Swing/AWT components like BossTerm terminals.
 *
 * This solves the issue where BossTerm consumes all keyboard events for terminal emulation,
 * preventing global shortcuts (Cmd+N, Cmd+W, etc.) from working.
 *
 * The interceptor uses KeyboardFocusManager to intercept events at the AWT level,
 * checks if they match registered shortcuts, and dispatches actions through
 * MenuActionsHandler if matched.
 */
object AWTKeyboardInterceptor {

    private var isInstalled = false
    private var dispatcher: KeyEventDispatcher? = null

    /**
     * Map of AWT window to BOSS window ID for routing events to correct window.
     * Uses ConcurrentHashMap for thread-safety (AWT events come from EDT).
     */
    private val windowIdMap = ConcurrentHashMap<Window, String>()

    // Double-shift detection for global search (like IntelliJ's Search Everywhere)
    private var lastShiftPressTime: Long = 0
    private var lastShiftReleaseTime: Long = 0
    private var shiftPressCount: Int = 0
    // 500ms threshold follows accessibility guidelines for double-tap gestures (typically 500-800ms)
    private const val DOUBLE_SHIFT_THRESHOLD_MS = 500
    // Minimum time shift must be released to count as a clean release (prevents false positives from held shift)
    private const val MIN_SHIFT_RELEASE_MS = 50

    /**
     * Register an AWT window with its BOSS window ID.
     * Call this from BossWindow's DisposableEffect when window is created.
     */
    fun registerWindow(awtWindow: Window, windowId: String) {
        windowIdMap[awtWindow] = windowId
    }

    /**
     * Unregister an AWT window when it's closed.
     * Call this from BossWindow's DisposableEffect onDispose.
     */
    fun unregisterWindow(awtWindow: Window) {
        windowIdMap.remove(awtWindow)
    }

    /**
     * Install the global keyboard interceptor.
     * Should be called once at application startup.
     */
    fun install() {
        if (isInstalled) return

        dispatcher = KeyEventDispatcher { event ->
            // Handle double-shift detection for global search
            if (event.keyCode == KeyEvent.VK_SHIFT) {
                val currentTime = System.currentTimeMillis()

                when (event.id) {
                    KeyEvent.KEY_PRESSED -> {
                        // Check if this is a quick second press after a clean release
                        val timeSinceRelease = currentTime - lastShiftReleaseTime
                        if (timeSinceRelease < DOUBLE_SHIFT_THRESHOLD_MS &&
                            timeSinceRelease >= MIN_SHIFT_RELEASE_MS && // Ensure clean release (not held)
                            shiftPressCount == 1) {
                            // Double-shift detected!
                            shiftPressCount = 0
                            lastShiftPressTime = 0
                            lastShiftReleaseTime = 0

                            // Get the focused window's BOSS window ID
                            val focusedWindow = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusedWindow
                            val windowId = findWindowId(focusedWindow)
                            if (windowId != null) {
                                try {
                                    MenuActionsHandler.triggerOpenGlobalSearch(windowId)
                                    event.consume()
                                    return@KeyEventDispatcher true
                                } catch (e: Exception) {
                                    // Log but don't crash the event dispatcher
                                    System.err.println("Error triggering global search: ${e.message}")
                                }
                            }
                        } else {
                            // First shift press or timeout - start counting
                            shiftPressCount = 1
                            lastShiftPressTime = currentTime
                        }
                    }
                    KeyEvent.KEY_RELEASED -> {
                        // Record release time for detecting second press
                        if (shiftPressCount == 1 && currentTime - lastShiftPressTime < DOUBLE_SHIFT_THRESHOLD_MS) {
                            lastShiftReleaseTime = currentTime
                        } else {
                            // Too slow or wrong sequence - reset
                            shiftPressCount = 0
                        }
                    }
                }
                return@KeyEventDispatcher false // Let shift events propagate
            }

            // Reset double-shift state if any other key is pressed
            if (event.id == KeyEvent.KEY_PRESSED && !isModifierOnlyKey(event.keyCode)) {
                shiftPressCount = 0
                lastShiftPressTime = 0
                lastShiftReleaseTime = 0
            }

            // Only intercept KEY_PRESSED events for other shortcuts
            if (event.id != KeyEvent.KEY_PRESSED) {
                return@KeyEventDispatcher false
            }

            // Skip if no modifier keys are pressed (most shortcuts require modifiers)
            if (!event.isMetaDown && !event.isControlDown && !event.isAltDown) {
                return@KeyEventDispatcher false
            }

            // Skip modifier-only key presses
            if (isModifierOnlyKey(event.keyCode)) {
                return@KeyEventDispatcher false
            }

            // Get the focused window's BOSS window ID
            val focusedWindow = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusedWindow
            val windowId = findWindowId(focusedWindow) ?: return@KeyEventDispatcher false

            // Get current keymap settings and create matcher
            val settings = KeymapSettingsManager.currentSettings.value
            val matcher = KeymapMatcher(settings)

            // Try to match the key event against shortcuts
            // Check TERMINAL context first (since terminal is likely focused), then WORKSPACE, then GLOBAL
            val binding = findMatchingBinding(event, matcher)

            if (binding != null) {
                // Dispatch the action through MenuActionsHandler
                val handled = dispatchAction(binding.actionId, windowId)
                if (handled) {
                    // Consume the event to prevent it from reaching BossTerm
                    event.consume()
                    return@KeyEventDispatcher true
                }
            }

            false // Let event propagate normally
        }

        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(dispatcher)
        isInstalled = true
    }

    /**
     * Uninstall the keyboard interceptor.
     * Should be called when the application exits.
     */
    fun uninstall() {
        dispatcher?.let { d ->
            KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(d)
        }
        dispatcher = null
        isInstalled = false
        windowIdMap.clear()
    }

    /**
     * Find the BOSS window ID for an AWT window, checking parent windows.
     */
    private fun findWindowId(window: Window?): String? {
        var current: Window? = window
        while (current != null) {
            val id = windowIdMap[current]
            if (id != null) return id
            current = current.owner
        }
        return null
    }

    /**
     * Check if a key code represents a modifier-only key.
     */
    private fun isModifierOnlyKey(keyCode: Int): Boolean {
        return keyCode in setOf(
            KeyEvent.VK_SHIFT,
            KeyEvent.VK_CONTROL,
            KeyEvent.VK_ALT,
            KeyEvent.VK_META,
            KeyEvent.VK_CAPS_LOCK,
            KeyEvent.VK_NUM_LOCK,
            KeyEvent.VK_SCROLL_LOCK
        )
    }

    /**
     * Find a matching binding for the AWT KeyEvent.
     * Checks TERMINAL, WORKSPACE, and GLOBAL contexts.
     */
    private fun findMatchingBinding(event: KeyEvent, matcher: KeymapMatcher): KeyBinding? {
        // Convert AWT KeyEvent to the format expected by KeymapMatcher
        val keyName = getKeyName(event.keyCode)
        val settings = KeymapSettingsManager.currentSettings.value

        // Check all enabled bindings
        for (binding in settings.shortcuts.values) {
            if (!binding.enabled) continue

            // Check if key matches
            if (!binding.key.equals(keyName, ignoreCase = true)) continue

            // Check modifiers
            val hasCmd = binding.modifiers.any { it.equals("Cmd", true) || it.equals("Meta", true) }
            val hasCtrl = binding.modifiers.any { it.equals("Ctrl", true) || it.equals("Control", true) }
            val hasShift = binding.modifiers.any { it.equals("Shift", true) }
            val hasAlt = binding.modifiers.any { it.equals("Alt", true) || it.equals("Option", true) }

            // Platform-aware modifier matching
            val isMacOS = SystemUtils.isMacOS
            val primaryMatch = if (hasCmd || hasCtrl) {
                if (isMacOS) {
                    (hasCmd && event.isMetaDown) || (hasCtrl && event.isControlDown)
                } else {
                    (hasCmd && event.isControlDown) || (hasCtrl && event.isMetaDown)
                }
            } else {
                !event.isMetaDown && !event.isControlDown
            }

            if (primaryMatch && hasShift == event.isShiftDown && hasAlt == event.isAltDown) {
                return binding
            }
        }

        return null
    }

    /**
     * Convert AWT key code to key name string.
     */
    private fun getKeyName(keyCode: Int): String {
        return when (keyCode) {
            KeyEvent.VK_A -> "A"
            KeyEvent.VK_B -> "B"
            KeyEvent.VK_C -> "C"
            KeyEvent.VK_D -> "D"
            KeyEvent.VK_E -> "E"
            KeyEvent.VK_F -> "F"
            KeyEvent.VK_G -> "G"
            KeyEvent.VK_H -> "H"
            KeyEvent.VK_I -> "I"
            KeyEvent.VK_J -> "J"
            KeyEvent.VK_K -> "K"
            KeyEvent.VK_L -> "L"
            KeyEvent.VK_M -> "M"
            KeyEvent.VK_N -> "N"
            KeyEvent.VK_O -> "O"
            KeyEvent.VK_P -> "P"
            KeyEvent.VK_Q -> "Q"
            KeyEvent.VK_R -> "R"
            KeyEvent.VK_S -> "S"
            KeyEvent.VK_T -> "T"
            KeyEvent.VK_U -> "U"
            KeyEvent.VK_V -> "V"
            KeyEvent.VK_W -> "W"
            KeyEvent.VK_X -> "X"
            KeyEvent.VK_Y -> "Y"
            KeyEvent.VK_Z -> "Z"
            KeyEvent.VK_0 -> "Zero"
            KeyEvent.VK_1 -> "One"
            KeyEvent.VK_2 -> "Two"
            KeyEvent.VK_3 -> "Three"
            KeyEvent.VK_4 -> "Four"
            KeyEvent.VK_5 -> "Five"
            KeyEvent.VK_6 -> "Six"
            KeyEvent.VK_7 -> "Seven"
            KeyEvent.VK_8 -> "Eight"
            KeyEvent.VK_9 -> "Nine"
            KeyEvent.VK_ENTER -> "Enter"
            KeyEvent.VK_ESCAPE -> "Esc"
            KeyEvent.VK_SPACE -> "Space"
            KeyEvent.VK_TAB -> "Tab"
            KeyEvent.VK_BACK_SPACE -> "Backspace"
            KeyEvent.VK_DELETE -> "Delete"
            KeyEvent.VK_LEFT -> "Left"
            KeyEvent.VK_RIGHT -> "Right"
            KeyEvent.VK_UP -> "Up"
            KeyEvent.VK_DOWN -> "Down"
            KeyEvent.VK_HOME -> "Home"
            KeyEvent.VK_END -> "End"
            KeyEvent.VK_PAGE_UP -> "PageUp"
            KeyEvent.VK_PAGE_DOWN -> "PageDown"
            KeyEvent.VK_F1 -> "F1"
            KeyEvent.VK_F2 -> "F2"
            KeyEvent.VK_F3 -> "F3"
            KeyEvent.VK_F4 -> "F4"
            KeyEvent.VK_F5 -> "F5"
            KeyEvent.VK_F6 -> "F6"
            KeyEvent.VK_F7 -> "F7"
            KeyEvent.VK_F8 -> "F8"
            KeyEvent.VK_F9 -> "F9"
            KeyEvent.VK_F10 -> "F10"
            KeyEvent.VK_F11 -> "F11"
            KeyEvent.VK_F12 -> "F12"
            KeyEvent.VK_MINUS -> "Minus"
            KeyEvent.VK_EQUALS -> "Equals"
            KeyEvent.VK_PLUS -> "Plus"
            KeyEvent.VK_OPEN_BRACKET -> "OpenBracket"
            KeyEvent.VK_CLOSE_BRACKET -> "CloseBracket"
            KeyEvent.VK_SLASH -> "Slash"
            KeyEvent.VK_BACK_SLASH -> "Backslash"
            KeyEvent.VK_SEMICOLON -> "Semicolon"
            KeyEvent.VK_QUOTE -> "Apostrophe"
            KeyEvent.VK_COMMA -> "Comma"
            KeyEvent.VK_PERIOD -> "Period"
            KeyEvent.VK_BACK_QUOTE -> "Grave"
            else -> KeyEvent.getKeyText(keyCode)
        }
    }

    /**
     * Dispatch an action through MenuActionsHandler.
     * Returns true if the action was handled, false otherwise.
     */
    private fun dispatchAction(actionId: String, windowId: String): Boolean {
        return when (actionId) {
            // Tab Management
            KeymapActions.TAB_NEW -> {
                MenuActionsHandler.triggerNewTab(windowId)
                true
            }
            KeymapActions.TAB_CLOSE -> {
                MenuActionsHandler.triggerCloseTab(windowId)
                true
            }

            // Window Management
            KeymapActions.WINDOW_NEW -> {
                WindowOperations.createNewWindow()
                true
            }
            KeymapActions.WINDOW_CLOSE -> {
                WindowOperations.closeWindow(windowId)
                true
            }

            // Browser Controls (Zoom)
            KeymapActions.BROWSER_ZOOM_IN -> {
                MenuActionsHandler.triggerZoomIn(windowId)
                true
            }
            KeymapActions.BROWSER_ZOOM_OUT -> {
                MenuActionsHandler.triggerZoomOut(windowId)
                true
            }
            KeymapActions.BROWSER_ZOOM_RESET -> {
                MenuActionsHandler.triggerActualSize(windowId)
                true
            }

            // View Controls
            KeymapActions.FOCUS_MODE_TOGGLE -> {
                MenuActionsHandler.triggerToggleFocusMode(windowId)
                true
            }

            // Panel Navigation
            KeymapActions.PANEL_NAVIGATE_LEFT -> {
                MenuActionsHandler.triggerNavigatePanelLeft(windowId)
                true
            }
            KeymapActions.PANEL_NAVIGATE_RIGHT -> {
                MenuActionsHandler.triggerNavigatePanelRight(windowId)
                true
            }
            KeymapActions.PANEL_NAVIGATE_UP -> {
                MenuActionsHandler.triggerNavigatePanelUp(windowId)
                true
            }
            KeymapActions.PANEL_NAVIGATE_DOWN -> {
                MenuActionsHandler.triggerNavigatePanelDown(windowId)
                true
            }

            // Split Panel
            KeymapActions.PANEL_SPLIT_VERTICAL -> {
                MenuActionsHandler.triggerSplitVertically(windowId)
                true
            }
            KeymapActions.PANEL_SPLIT_HORIZONTAL -> {
                MenuActionsHandler.triggerSplitHorizontally(windowId)
                true
            }

            // Browser Controls
            KeymapActions.BROWSER_RELOAD -> {
                MenuActionsHandler.triggerReloadBrowser(windowId)
                true
            }

            // Codebase
            KeymapActions.CODEBASE_OPEN -> {
                MenuActionsHandler.triggerOpenCodebase(windowId)
                true
            }

            // Global Search
            KeymapActions.GLOBAL_SEARCH_OPEN -> {
                MenuActionsHandler.triggerOpenGlobalSearch(windowId)
                true
            }

            // Settings
            KeymapActions.SETTINGS_OPEN -> {
                MenuActionsHandler.triggerOpenSettings(windowId)
                true
            }

            // Workspace
            KeymapActions.WORKSPACE_SAVE -> {
                MenuActionsHandler.triggerSaveWorkspace(windowId)
                true
            }

            // Help
            KeymapActions.HELP_SHORTCUTS -> {
                MenuActionsHandler.triggerShowShortcutHelp(windowId)
                true
            }

            else -> false
        }
    }
}
