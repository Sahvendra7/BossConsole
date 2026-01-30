package ai.rever.bosseditor.ui

import ai.rever.bosseditor.refactoring.RefactorAvailability
import ai.rever.bosseditor.refactoring.RefactorKind
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import java.awt.Color
import java.awt.Font
import java.awt.KeyboardFocusManager
import java.awt.MouseInfo
import java.awt.Window
import javax.swing.BorderFactory
import javax.swing.JMenu
import javax.swing.JMenuItem
import javax.swing.JPopupMenu
import javax.swing.JSeparator
import javax.swing.KeyStroke

/**
 * Context menu item data.
 */
data class ContextMenuItem(
    val id: String,
    val label: String,
    val shortcut: String? = null,
    val enabled: Boolean = true,
    val onClick: () -> Unit
)

/**
 * Context menu section data.
 */
data class ContextMenuSection(
    val items: List<ContextMenuItem>
)

/**
 * Controller for managing editor context menu using native AWT JPopupMenu.
 * This provides proper submenu handling that works correctly when hovering.
 */
class EditorContextMenuController {
    private var currentPopup: JPopupMenu? = null

    /**
     * Show the editor context menu at the current mouse position.
     */
    fun showMenu(
        hasSelection: Boolean,
        canPaste: Boolean,
        isOnDefinition: Boolean,
        refactorings: List<RefactorAvailability>,
        onCut: (() -> Unit)?,
        onCopy: (() -> Unit)?,
        onPaste: (() -> Unit)?,
        onSelectAll: (() -> Unit)?,
        onGoToDefinition: (() -> Unit)?,
        onFindUsages: (() -> Unit)?,
        onRefactoring: ((RefactorKind) -> Unit)?,
        onDismiss: () -> Unit
    ) {
        // Dismiss any existing popup first
        currentPopup?.let {
            it.isVisible = false
        }

        val popup = JPopupMenu().apply {
            background = Color(0x2B, 0x2B, 0x2B)
            border = BorderFactory.createLineBorder(Color(0x3C, 0x3F, 0x41), 1)
        }

        // Edit section
        if (onCut != null || onCopy != null || onPaste != null || onSelectAll != null) {
            onCut?.let {
                popup.add(createMenuItem("Cut", "meta X", hasSelection) {
                    it()
                    onDismiss()
                })
            }
            onCopy?.let {
                popup.add(createMenuItem("Copy", "meta C", hasSelection) {
                    it()
                    onDismiss()
                })
            }
            onPaste?.let {
                popup.add(createMenuItem("Paste", "meta V", canPaste) {
                    it()
                    onDismiss()
                })
            }
            onSelectAll?.let {
                popup.add(createMenuItem("Select All", "meta A", true) {
                    it()
                    onDismiss()
                })
            }
            popup.add(createSeparator())
        }

        // Navigation section
        if (onGoToDefinition != null || onFindUsages != null) {
            if (isOnDefinition) {
                onFindUsages?.let {
                    popup.add(createMenuItem("Find Usages", "alt F7", true) {
                        it()
                        onDismiss()
                    })
                }
            } else {
                onGoToDefinition?.let {
                    popup.add(createMenuItem("Go to Definition", "meta CLICK", true) {
                        it()
                        onDismiss()
                    })
                }
            }

            // Always show the secondary option if available
            if (isOnDefinition && onGoToDefinition != null) {
                popup.add(createMenuItem("Go to Definition", "meta CLICK", true) {
                    onGoToDefinition()
                    onDismiss()
                })
            } else if (!isOnDefinition && onFindUsages != null) {
                popup.add(createMenuItem("Find Usages", "alt F7", true) {
                    onFindUsages()
                    onDismiss()
                })
            }

            popup.add(createSeparator())
        }

        // Refactor section
        if (onRefactoring != null && refactorings.isNotEmpty()) {
            val availableRefactorings = refactorings.filter { it.available }

            // Create Refactor submenu
            val refactorMenu = JMenu("Refactor").apply {
                background = Color(0x2B, 0x2B, 0x2B)
                foreground = if (availableRefactorings.isNotEmpty()) Color.WHITE else Color.GRAY
                font = Font(".AppleSystemUIFont", Font.PLAIN, 13)
                border = BorderFactory.createEmptyBorder(4, 12, 4, 12)
                isOpaque = true
                isEnabled = availableRefactorings.isNotEmpty()
                popupMenu.background = Color(0x2B, 0x2B, 0x2B)
                popupMenu.border = BorderFactory.createLineBorder(Color(0x3C, 0x3F, 0x41), 1)
            }

            // Add refactoring items to submenu
            refactorings.forEach { refactoring ->
                val (label, shortcut) = getRefactoringLabelAndShortcut(refactoring.kind)
                refactorMenu.add(createMenuItem(label, shortcut, refactoring.available) {
                    onRefactoring(refactoring.kind)
                    onDismiss()
                })
            }

            popup.add(refactorMenu)

            // Also show Rename as a top-level item since it's common
            val renameAvailable = refactorings.find { it.kind == RefactorKind.RENAME }?.available == true
            popup.add(createMenuItem("Rename Symbol", "shift F6", renameAvailable) {
                onRefactoring(RefactorKind.RENAME)
                onDismiss()
            })
        }

        // Add popup listener for dismiss callback
        popup.addPopupMenuListener(object : javax.swing.event.PopupMenuListener {
            override fun popupMenuWillBecomeVisible(e: javax.swing.event.PopupMenuEvent?) {}
            override fun popupMenuWillBecomeInvisible(e: javax.swing.event.PopupMenuEvent?) {
                currentPopup = null
            }
            override fun popupMenuCanceled(e: javax.swing.event.PopupMenuEvent?) {
                onDismiss()
            }
        })

        currentPopup = popup

        // Get mouse position and show popup
        val mouseLocation = MouseInfo.getPointerInfo()?.location
        if (mouseLocation != null) {
            showPopupAtScreen(popup, mouseLocation.x, mouseLocation.y)
        }
    }

    /**
     * Hide the context menu if visible.
     */
    fun hideMenu() {
        currentPopup?.let {
            it.isVisible = false
            currentPopup = null
        }
    }

    private fun showPopupAtScreen(popup: JPopupMenu, screenX: Int, screenY: Int) {
        var targetWindow: Window? = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusedWindow

        if (targetWindow == null) {
            val mousePoint = java.awt.Point(screenX, screenY)
            targetWindow = Window.getWindows()
                .filter { it.isVisible && it.bounds.contains(mousePoint) }
                .maxByOrNull { it.bounds.width * it.bounds.height }

            targetWindow?.toFront()
            targetWindow?.requestFocus()
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

    private fun createMenuItem(
        label: String,
        shortcut: String?,
        enabled: Boolean,
        action: () -> Unit
    ): JMenuItem {
        return JMenuItem(label).apply {
            isEnabled = enabled
            background = Color(0x2B, 0x2B, 0x2B)
            foreground = if (enabled) Color.WHITE else Color.GRAY
            font = Font(".AppleSystemUIFont", Font.PLAIN, 13)
            border = BorderFactory.createEmptyBorder(4, 12, 4, 12)
            isOpaque = true
            if (shortcut != null) {
                // Parse shortcut and set accelerator for display
                try {
                    accelerator = KeyStroke.getKeyStroke(shortcut)
                } catch (e: Exception) {
                    // Ignore invalid shortcuts
                }
            }
            addActionListener { action() }
        }
    }

    private fun createSeparator(): JSeparator {
        return JSeparator().apply {
            background = Color(0x2B, 0x2B, 0x2B)
            foreground = Color(0x3C, 0x3F, 0x41)
        }
    }
}

/**
 * Editor context menu shown on right-click.
 * Uses native AWT JPopupMenu for proper submenu handling.
 *
 * @param position The position to show the menu at (used as fallback)
 * @param onDismiss Called when the menu should be dismissed
 * @param hasSelection Whether there is selected text
 * @param canPaste Whether paste is available
 * @param isOnDefinition Whether the cursor is on a definition
 * @param refactorings Available refactoring operations
 * @param onCut Called when Cut is selected
 * @param onCopy Called when Copy is selected
 * @param onPaste Called when Paste is selected
 * @param onSelectAll Called when Select All is selected
 * @param onGoToDefinition Called when Go to Definition is selected
 * @param onFindUsages Called when Find Usages is selected
 * @param onRefactoring Called when a refactoring is selected
 */
@Composable
fun EditorContextMenu(
    position: Offset,
    onDismiss: () -> Unit,
    hasSelection: Boolean = false,
    canPaste: Boolean = true,
    isOnDefinition: Boolean = false,
    refactorings: List<RefactorAvailability> = emptyList(),
    onCut: (() -> Unit)? = null,
    onCopy: (() -> Unit)? = null,
    onPaste: (() -> Unit)? = null,
    onSelectAll: (() -> Unit)? = null,
    onGoToDefinition: (() -> Unit)? = null,
    onFindUsages: (() -> Unit)? = null,
    onRefactoring: ((RefactorKind) -> Unit)? = null
) {
    val controller = remember { EditorContextMenuController() }

    // Show the native menu when this composable enters composition
    LaunchedEffect(Unit) {
        controller.showMenu(
            hasSelection = hasSelection,
            canPaste = canPaste,
            isOnDefinition = isOnDefinition,
            refactorings = refactorings,
            onCut = onCut,
            onCopy = onCopy,
            onPaste = onPaste,
            onSelectAll = onSelectAll,
            onGoToDefinition = onGoToDefinition,
            onFindUsages = onFindUsages,
            onRefactoring = onRefactoring,
            onDismiss = onDismiss
        )
    }

    // Clean up when dismissed
    DisposableEffect(Unit) {
        onDispose {
            controller.hideMenu()
        }
    }

    // No composable UI - native menu is shown directly
}

/**
 * Gets the display label and keyboard shortcut for a refactoring kind.
 */
private fun getRefactoringLabelAndShortcut(kind: RefactorKind): Pair<String, String?> {
    return when (kind) {
        RefactorKind.RENAME -> "Rename" to "shift F6"
        RefactorKind.EXTRACT_VARIABLE -> "Extract Variable" to "meta alt V"
        RefactorKind.EXTRACT_METHOD -> "Extract Method" to "meta alt M"
        RefactorKind.EXTRACT_CONSTANT -> "Extract Constant" to null
        RefactorKind.INLINE -> "Inline" to "meta alt N"
        RefactorKind.MOVE -> "Move" to null
        RefactorKind.CHANGE_SIGNATURE -> "Change Signature" to null
        RefactorKind.SAFE_DELETE -> "Safe Delete" to "meta DELETE"
        RefactorKind.INTRODUCE_PARAMETER -> "Introduce Parameter" to null
    }
}

/**
 * Gets the refactoring kind from a menu item ID.
 */
fun getRefactorKindFromId(id: String): RefactorKind? {
    return when (id) {
        "rename" -> RefactorKind.RENAME
        "extractVariable" -> RefactorKind.EXTRACT_VARIABLE
        "extractMethod" -> RefactorKind.EXTRACT_METHOD
        "extractConstant" -> RefactorKind.EXTRACT_CONSTANT
        "inline" -> RefactorKind.INLINE
        "move" -> RefactorKind.MOVE
        "changeSignature" -> RefactorKind.CHANGE_SIGNATURE
        "safeDelete" -> RefactorKind.SAFE_DELETE
        "introduceParameter" -> RefactorKind.INTRODUCE_PARAMETER
        else -> null
    }
}
