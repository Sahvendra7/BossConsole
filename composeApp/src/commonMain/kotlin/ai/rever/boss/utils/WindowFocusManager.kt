package ai.rever.boss.utils

/**
 * WindowFocusManager - Platform-specific window focus management
 */
expect object WindowFocusManager {
    /**
     * Bring the application window to front and request focus
     */
    fun bringToFront()
}
