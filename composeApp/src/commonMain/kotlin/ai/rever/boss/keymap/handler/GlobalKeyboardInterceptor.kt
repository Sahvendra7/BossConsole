package ai.rever.boss.keymap.handler

import ai.rever.boss.keymap.model.KeymapSettings

/**
 * Platform-specific global keyboard interceptor.
 * Intercepts keyboard events at the window/OS level before they reach Compose.
 *
 * On desktop, this uses AWT KeyListener to intercept events at the window level.
 * This allows global shortcuts to work even when child components have focus.
 */
expect class GlobalKeyboardInterceptor(keymapSettings: KeymapSettings) {
    /**
     * Attaches the keyboard interceptor to a window.
     * Should be called when the window is created.
     *
     * @param window The platform-specific window object (ComposeWindow on desktop)
     */
    fun attach(window: Any)

    /**
     * Detaches the keyboard interceptor from a window.
     * Should be called when the window is being disposed.
     *
     * @param window The platform-specific window object (ComposeWindow on desktop)
     */
    fun detach(window: Any)

    /**
     * Updates the keymap settings used by the interceptor.
     * Called when settings change.
     *
     * @param newSettings The new keymap settings
     */
    fun updateSettings(newSettings: KeymapSettings)
}
