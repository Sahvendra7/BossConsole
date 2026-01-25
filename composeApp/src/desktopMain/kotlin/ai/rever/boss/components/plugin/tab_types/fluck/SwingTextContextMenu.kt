package ai.rever.boss.components.plugin.tab_types.fluck

import androidx.compose.foundation.ContextMenuRepresentation
import androidx.compose.foundation.ContextMenuState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.round
import java.awt.Color
import java.awt.Font
import java.awt.KeyboardFocusManager
import java.awt.MouseInfo
import java.awt.Window
import javax.swing.BorderFactory
import javax.swing.JMenuItem
import javax.swing.JPopupMenu
import javax.swing.JSeparator
import javax.swing.SwingUtilities

/**
 * Swing-based ContextMenuRepresentation for text fields in HARDWARE_ACCELERATED mode.
 *
 * The default Compose context menu uses lightweight Popup which gets hidden behind
 * the JxBrowser view in HARDWARE_ACCELERATED mode. This implementation uses
 * heavyweight Swing JPopupMenu that renders correctly above all components.
 */
object SwingTextContextMenuRepresentation : ContextMenuRepresentation {

    @Composable
    override fun Representation(state: ContextMenuState, items: () -> List<androidx.compose.foundation.ContextMenuItem>) {
        val contextMenuItems = items()
        val status = state.status

        if (status is ContextMenuState.Status.Open) {
            // Get position from state
            val position = status.rect.center

            // Show Swing context menu on EDT
            LaunchedEffect(status) {
                SwingUtilities.invokeLater {
                    showSwingContextMenu(
                        position = position,
                        items = contextMenuItems,
                        onDismiss = { state.status = ContextMenuState.Status.Closed }
                    )
                }
            }
        }
    }

    private fun showSwingContextMenu(
        position: Offset,
        items: List<androidx.compose.foundation.ContextMenuItem>,
        onDismiss: () -> Unit
    ) {
        val popup = JPopupMenu().apply {
            background = Color(0x2B, 0x2B, 0x2B)
            border = BorderFactory.createLineBorder(Color(0x3C, 0x3F, 0x41), 1)
        }

        // Add items
        items.forEach { item ->
            val menuItem = JMenuItem(item.label).apply {
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
            popup.add(menuItem)
        }

        // Add dismiss listener
        popup.addPopupMenuListener(object : javax.swing.event.PopupMenuListener {
            override fun popupMenuWillBecomeVisible(e: javax.swing.event.PopupMenuEvent?) {}
            override fun popupMenuWillBecomeInvisible(e: javax.swing.event.PopupMenuEvent?) {
                onDismiss()
            }
            override fun popupMenuCanceled(e: javax.swing.event.PopupMenuEvent?) {}
        })

        // Use mouse position for accurate placement
        val mouseLocation = MouseInfo.getPointerInfo()?.location
        val screenX = mouseLocation?.x ?: position.x.toInt()
        val screenY = mouseLocation?.y ?: position.y.toInt()

        // Find window to show popup
        var targetWindow: Window? = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusedWindow
        if (targetWindow == null) {
            val mousePoint = java.awt.Point(screenX, screenY)
            targetWindow = Window.getWindows()
                .filter { it.isVisible && it.bounds.contains(mousePoint) }
                .maxByOrNull { it.bounds.width * it.bounds.height }
        }

        if (targetWindow != null) {
            val windowLocation = targetWindow.locationOnScreen
            val relativeX = screenX - windowLocation.x
            val relativeY = screenY - windowLocation.y
            popup.show(targetWindow, relativeX, relativeY)
        } else {
            popup.location = java.awt.Point(screenX, screenY)
            popup.isVisible = true
        }
    }
}
