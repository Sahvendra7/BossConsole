package ai.rever.boss.utils

import java.awt.Window
import javax.swing.SwingUtilities

/**
 * WindowFocusManager - Handles bringing the application window to front
 *
 * Used when deep links are received to ensure the user sees the app response
 */
actual object WindowFocusManager {
    private var mainWindow: Window? = null

    /**
     * Register the main application window
     * Note: This is desktop-specific and not part of the expect/actual interface
     */
    fun registerWindow(window: Window) {
        mainWindow = window
        println("WindowFocusManager: Registered main window")
    }

    /**
     * Bring the application window to front and request focus
     */
    actual fun bringToFront() {
        mainWindow?.let { window ->
            SwingUtilities.invokeLater {
                // Make window visible if minimized
                if (!window.isVisible) {
                    window.isVisible = true
                }

                // Bring to front
                window.toFront()

                // Request focus
                window.requestFocus()

                println("WindowFocusManager: Brought window to front")
            }
        } ?: println("WindowFocusManager: No window registered")
    }
}
