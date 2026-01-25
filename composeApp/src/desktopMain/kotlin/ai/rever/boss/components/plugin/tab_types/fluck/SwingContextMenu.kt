package ai.rever.boss.components.plugin.tab_types.fluck

import ai.rever.boss.components.overlays.ContextMenuItem
import java.awt.Color
import java.awt.Font
import java.awt.KeyboardFocusManager
import java.awt.Window
import javax.swing.BorderFactory
import javax.swing.JLabel
import javax.swing.JMenu
import javax.swing.JMenuItem
import javax.swing.JPopupMenu
import javax.swing.JSeparator

/**
 * Swing-based context menu for JxBrowser in HARDWARE_ACCELERATED mode.
 *
 * Uses native AWT JPopupMenu which is heavyweight and can appear above
 * the browser view, unlike Compose's lightweight Popup component.
 *
 * Based on BossTerm's ContextMenuController implementation.
 */
object SwingContextMenu {
    // Track current popup to ensure proper dismissal
    private var currentPopup: JPopupMenu? = null

    /**
     * Show context menu at screen coordinates using native AWT popup.
     *
     * @param screenX X coordinate in screen space
     * @param screenY Y coordinate in screen space
     * @param items List of ContextMenuItem to display
     * @param onDismiss Callback when menu is dismissed
     */
    fun show(
        screenX: Int,
        screenY: Int,
        items: List<ContextMenuItem>,
        onDismiss: () -> Unit = {}
    ) {
        // Dismiss any existing popup first
        currentPopup?.let {
            it.isVisible = false
        }

        val popup = JPopupMenu().apply {
            // Dark theme colors matching BOSS style
            background = Color(0x2B, 0x2B, 0x2B)
            border = BorderFactory.createLineBorder(Color(0x3C, 0x3F, 0x41), 1)
        }

        // Add items to popup
        addItemsToMenu(popup, items, onDismiss)

        // Add listener to track popup dismissal
        popup.addPopupMenuListener(object : javax.swing.event.PopupMenuListener {
            override fun popupMenuWillBecomeVisible(e: javax.swing.event.PopupMenuEvent?) {}
            override fun popupMenuWillBecomeInvisible(e: javax.swing.event.PopupMenuEvent?) {
                currentPopup = null
                onDismiss()
            }
            override fun popupMenuCanceled(e: javax.swing.event.PopupMenuEvent?) {}
        })

        currentPopup = popup

        // Find the window to use as invoker
        var targetWindow: Window? = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusedWindow

        // If no focused window, find window at mouse position
        if (targetWindow == null) {
            val mousePoint = java.awt.Point(screenX, screenY)
            targetWindow = Window.getWindows()
                .filter { it.isVisible && it.bounds.contains(mousePoint) }
                .maxByOrNull { it.bounds.width * it.bounds.height }

            targetWindow?.toFront()
            targetWindow?.requestFocus()
        }

        if (targetWindow != null) {
            // Convert screen coordinates to window-relative
            val windowLocation = targetWindow.locationOnScreen
            val relativeX = screenX - windowLocation.x
            val relativeY = screenY - windowLocation.y
            popup.show(targetWindow, relativeX, relativeY)
        } else {
            // Fallback: show at screen location
            popup.location = java.awt.Point(screenX, screenY)
            popup.isVisible = true
        }
    }

    /**
     * Hide the current context menu.
     */
    fun hide() {
        currentPopup?.let {
            it.isVisible = false
            currentPopup = null
        }
    }

    /**
     * Add context menu items to a JPopupMenu or JMenu.
     */
    private fun addItemsToMenu(
        menu: javax.swing.JComponent,
        items: List<ContextMenuItem>,
        onDismiss: () -> Unit
    ) {
        items.forEach { item ->
            if (item.isDivider) {
                val separator = JSeparator().apply {
                    background = Color(0x2B, 0x2B, 0x2B)
                    foreground = Color(0x3C, 0x3F, 0x41)
                }
                addToMenu(menu, separator)
            } else if (item.subMenu != null && item.subMenu.isNotEmpty()) {
                // Submenu
                val submenu = JMenu(item.text).apply {
                    background = Color(0x2B, 0x2B, 0x2B)
                    foreground = Color.WHITE
                    font = Font(".AppleSystemUIFont", Font.PLAIN, 13)
                    border = BorderFactory.createEmptyBorder(4, 12, 4, 12)
                    isOpaque = true
                    popupMenu.background = Color(0x2B, 0x2B, 0x2B)
                    popupMenu.border = BorderFactory.createLineBorder(Color(0x3C, 0x3F, 0x41), 1)
                }
                addItemsToMenu(submenu, item.subMenu, onDismiss)
                addToMenu(menu, submenu)
            } else {
                // Regular menu item
                val menuItem = JMenuItem(item.text).apply {
                    background = Color(0x2B, 0x2B, 0x2B)
                    foreground = Color.WHITE
                    font = Font(".AppleSystemUIFont", Font.PLAIN, 13)
                    border = BorderFactory.createEmptyBorder(4, 12, 4, 12)
                    isOpaque = true
                    addActionListener {
                        item.onClick()
                        onDismiss()
                    }
                }
                addToMenu(menu, menuItem)
            }
        }
    }

    /**
     * Helper to add component to the correct menu type.
     */
    private fun addToMenu(menu: javax.swing.JComponent, item: java.awt.Component) {
        when (menu) {
            is JMenu -> menu.add(item)
            is JPopupMenu -> menu.add(item)
            else -> menu.add(item)
        }
    }
}
