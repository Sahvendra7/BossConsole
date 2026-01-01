package ai.rever.boss.components.plugin.tab_types

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea
import java.awt.event.InputEvent
import java.awt.event.KeyEvent as AwtKeyEvent
import javax.swing.InputMap
import javax.swing.JComponent
import javax.swing.KeyStroke

/**
 * Keyboard bridge for RSyntaxTextArea integration with BOSS keyboard system.
 *
 * This class handles the keyboard shortcut conflicts between RSyntaxTextArea's
 * default bindings and BOSS application-level shortcuts.
 *
 * Strategy:
 * 1. Remove conflicting RSTA shortcuts that BOSS handles globally (Cmd+N, Cmd+W, Cmd+T, Cmd+,)
 * 2. Let RSTA handle editor-specific shortcuts (Cmd+Z, Cmd+F, Cmd+H, Cmd+G, etc.)
 * 3. Preserve all text editing functionality
 *
 * BOSS Global shortcuts that must NOT be handled by RSTA:
 * - Cmd+N: New Window
 * - Cmd+W: Close Tab
 * - Cmd+T: New Tab
 * - Cmd+,: Settings
 * - Cmd+Shift+N: New Private Window
 * - Cmd+Q: Quit Application
 * - Cmd+Shift+W: Close All Tabs
 *
 * RSTA shortcuts to preserve:
 * - Cmd+Z: Undo
 * - Cmd+Shift+Z: Redo
 * - Cmd+Y: Redo (alternative)
 * - Cmd+F: Find
 * - Cmd+H: Replace
 * - Cmd+G: Find Next / Go to Line
 * - Cmd+Shift+G: Find Previous
 * - Cmd+A: Select All
 * - Cmd+C: Copy
 * - Cmd+V: Paste
 * - Cmd+X: Cut
 * - Cmd+D: Duplicate Line (if available)
 * - Cmd+/: Toggle Comment
 * - Tab/Shift+Tab: Indent/Unindent
 * - F3/Shift+F3: Find Next/Previous
 */
object RSyntaxKeyboardBridge {

    private val isMacOS = System.getProperty("os.name").lowercase().contains("mac")

    /**
     * Modifier mask for primary key (Cmd on macOS, Ctrl on Windows/Linux)
     */
    private val primaryModifier = if (isMacOS) {
        InputEvent.META_DOWN_MASK
    } else {
        InputEvent.CTRL_DOWN_MASK
    }

    /**
     * List of KeyStrokes that BOSS handles globally and should be removed from RSTA.
     * These are shortcuts that conflict with BOSS application-level functionality.
     */
    private val conflictingShortcuts: List<KeyStroke> by lazy {
        listOf(
            // Window management
            KeyStroke.getKeyStroke(AwtKeyEvent.VK_N, primaryModifier),           // Cmd+N: New Window
            KeyStroke.getKeyStroke(AwtKeyEvent.VK_W, primaryModifier),           // Cmd+W: Close Tab
            KeyStroke.getKeyStroke(AwtKeyEvent.VK_T, primaryModifier),           // Cmd+T: New Tab
            KeyStroke.getKeyStroke(AwtKeyEvent.VK_COMMA, primaryModifier),       // Cmd+,: Settings
            KeyStroke.getKeyStroke(AwtKeyEvent.VK_Q, primaryModifier),           // Cmd+Q: Quit

            // Shift combinations
            KeyStroke.getKeyStroke(AwtKeyEvent.VK_N, primaryModifier or InputEvent.SHIFT_DOWN_MASK),  // Cmd+Shift+N
            KeyStroke.getKeyStroke(AwtKeyEvent.VK_W, primaryModifier or InputEvent.SHIFT_DOWN_MASK),  // Cmd+Shift+W
            KeyStroke.getKeyStroke(AwtKeyEvent.VK_T, primaryModifier or InputEvent.SHIFT_DOWN_MASK),  // Cmd+Shift+T

            // Navigation that BOSS handles
            KeyStroke.getKeyStroke(AwtKeyEvent.VK_LEFT, primaryModifier or InputEvent.ALT_DOWN_MASK),   // Cmd+Alt+Left
            KeyStroke.getKeyStroke(AwtKeyEvent.VK_RIGHT, primaryModifier or InputEvent.ALT_DOWN_MASK),  // Cmd+Alt+Right
            KeyStroke.getKeyStroke(AwtKeyEvent.VK_P, primaryModifier or InputEvent.SHIFT_DOWN_MASK),    // Cmd+Shift+P: Command Palette
            KeyStroke.getKeyStroke(AwtKeyEvent.VK_K, primaryModifier),           // Cmd+K: Quick Switcher (if used)
        )
    }

    /**
     * Configures the RSyntaxTextArea's key bindings for BOSS integration.
     * Removes conflicting shortcuts while preserving editor functionality.
     *
     * @param textArea The RSyntaxTextArea to configure
     */
    fun configureKeyBindings(textArea: RSyntaxTextArea) {
        // Get the input maps at different focus levels
        val focusedMap = textArea.getInputMap(JComponent.WHEN_FOCUSED)
        val ancestorMap = textArea.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
        val windowMap = textArea.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)

        // Remove conflicting shortcuts from all input maps
        removeConflictingBindings(focusedMap)
        removeConflictingBindings(ancestorMap)
        removeConflictingBindings(windowMap)

        // Ensure essential editor shortcuts are preserved
        ensureEditorShortcuts(textArea)
    }

    /**
     * Removes conflicting key bindings from an InputMap.
     */
    private fun removeConflictingBindings(inputMap: InputMap?) {
        if (inputMap == null) return

        for (keyStroke in conflictingShortcuts) {
            // Remove the binding by setting it to "none"
            inputMap.put(keyStroke, "none")
        }

        // Also check parent InputMap recursively
        removeConflictingBindings(inputMap.parent)
    }

    /**
     * Ensures essential editor shortcuts are properly bound.
     * This re-adds any editor shortcuts that might have been accidentally removed.
     */
    private fun ensureEditorShortcuts(textArea: RSyntaxTextArea) {
        // RSyntaxTextArea already has good defaults for:
        // - Cmd+Z: Undo (RTextArea.rtaUndoAction)
        // - Cmd+Y / Cmd+Shift+Z: Redo (RTextArea.rtaRedoAction)
        // - Cmd+A: Select All (RTextArea.rtaSelectAllAction)
        // - Cmd+C/V/X: Copy/Paste/Cut

        // The default bindings should be preserved by not removing them.
        // If specific bindings need to be added/restored, do it here.

        // RSyntaxTextArea doesn't have built-in Find/Replace dialogs - RSTAUI provides those.
        // We bind the search shortcuts to trigger EditorSearchEventBus actions.
        bindSearchShortcuts(textArea)
    }

    /**
     * Binds search and save shortcuts (Cmd+F, Cmd+H, Cmd+G, Cmd+S, etc.) to event bus actions.
     * This allows the search dialogs provided by RSyntaxSearchManager to be triggered,
     * and file save to be triggered via FileSaveEventBus.
     */
    private fun bindSearchShortcuts(textArea: RSyntaxTextArea) {
        val focusedMap = textArea.getInputMap(JComponent.WHEN_FOCUSED)
        val actionMap = textArea.actionMap

        // Cmd+S: Save
        val saveStroke = KeyStroke.getKeyStroke(AwtKeyEvent.VK_S, primaryModifier)
        focusedMap.put(saveStroke, "boss-save")
        actionMap.put("boss-save", object : javax.swing.AbstractAction() {
            override fun actionPerformed(e: java.awt.event.ActionEvent?) {
                FileSaveEventBus.requestSave()
            }
        })

        // Cmd+F: Find
        val findStroke = KeyStroke.getKeyStroke(AwtKeyEvent.VK_F, primaryModifier)
        focusedMap.put(findStroke, "boss-find")
        actionMap.put("boss-find", object : javax.swing.AbstractAction() {
            override fun actionPerformed(e: java.awt.event.ActionEvent?) {
                EditorSearchEventBus.triggerFind()
            }
        })

        // Cmd+H: Replace
        val replaceStroke = KeyStroke.getKeyStroke(AwtKeyEvent.VK_H, primaryModifier)
        focusedMap.put(replaceStroke, "boss-replace")
        actionMap.put("boss-replace", object : javax.swing.AbstractAction() {
            override fun actionPerformed(e: java.awt.event.ActionEvent?) {
                EditorSearchEventBus.triggerReplace()
            }
        })

        // Cmd+G: Find Next
        val findNextStroke = KeyStroke.getKeyStroke(AwtKeyEvent.VK_G, primaryModifier)
        focusedMap.put(findNextStroke, "boss-find-next")
        actionMap.put("boss-find-next", object : javax.swing.AbstractAction() {
            override fun actionPerformed(e: java.awt.event.ActionEvent?) {
                EditorSearchEventBus.triggerFindNext()
            }
        })

        // Cmd+Shift+G: Find Previous
        val findPrevStroke = KeyStroke.getKeyStroke(AwtKeyEvent.VK_G, primaryModifier or InputEvent.SHIFT_DOWN_MASK)
        focusedMap.put(findPrevStroke, "boss-find-previous")
        actionMap.put("boss-find-previous", object : javax.swing.AbstractAction() {
            override fun actionPerformed(e: java.awt.event.ActionEvent?) {
                EditorSearchEventBus.triggerFindPrevious()
            }
        })

        // Cmd+L: Go to Line (BOSS default)
        val goToLineStroke = KeyStroke.getKeyStroke(AwtKeyEvent.VK_L, primaryModifier)
        focusedMap.put(goToLineStroke, "boss-goto-line")
        actionMap.put("boss-goto-line", object : javax.swing.AbstractAction() {
            override fun actionPerformed(e: java.awt.event.ActionEvent?) {
                EditorSearchEventBus.triggerGoToLine()
            }
        })

        // F3: Find Next (alternative)
        val f3Stroke = KeyStroke.getKeyStroke(AwtKeyEvent.VK_F3, 0)
        focusedMap.put(f3Stroke, "boss-find-next-f3")
        actionMap.put("boss-find-next-f3", object : javax.swing.AbstractAction() {
            override fun actionPerformed(e: java.awt.event.ActionEvent?) {
                EditorSearchEventBus.triggerFindNext()
            }
        })

        // Shift+F3: Find Previous (alternative)
        val shiftF3Stroke = KeyStroke.getKeyStroke(AwtKeyEvent.VK_F3, InputEvent.SHIFT_DOWN_MASK)
        focusedMap.put(shiftF3Stroke, "boss-find-previous-f3")
        actionMap.put("boss-find-previous-f3", object : javax.swing.AbstractAction() {
            override fun actionPerformed(e: java.awt.event.ActionEvent?) {
                EditorSearchEventBus.triggerFindPrevious()
            }
        })
    }

    /**
     * Creates a KeyStroke with the primary modifier (Cmd on macOS, Ctrl elsewhere).
     */
    fun createPrimaryModifierStroke(keyCode: Int): KeyStroke {
        return KeyStroke.getKeyStroke(keyCode, primaryModifier)
    }

    /**
     * Creates a KeyStroke with primary modifier + Shift.
     */
    fun createPrimaryShiftModifierStroke(keyCode: Int): KeyStroke {
        return KeyStroke.getKeyStroke(keyCode, primaryModifier or InputEvent.SHIFT_DOWN_MASK)
    }

    /**
     * Creates a KeyStroke with primary modifier + Alt.
     */
    fun createPrimaryAltModifierStroke(keyCode: Int): KeyStroke {
        return KeyStroke.getKeyStroke(keyCode, primaryModifier or InputEvent.ALT_DOWN_MASK)
    }

    /**
     * Checks if a KeyStroke is a BOSS global shortcut that should not be handled by the editor.
     */
    fun isGlobalBossShortcut(keyStroke: KeyStroke): Boolean {
        return conflictingShortcuts.any { it == keyStroke }
    }

    /**
     * Checks if a KeyStroke is an editor-specific shortcut that RSTA should handle.
     */
    fun isEditorShortcut(keyStroke: KeyStroke): Boolean {
        val editorShortcuts = listOf(
            // Undo/Redo
            KeyStroke.getKeyStroke(AwtKeyEvent.VK_Z, primaryModifier),
            KeyStroke.getKeyStroke(AwtKeyEvent.VK_Z, primaryModifier or InputEvent.SHIFT_DOWN_MASK),
            KeyStroke.getKeyStroke(AwtKeyEvent.VK_Y, primaryModifier),

            // Find/Replace
            KeyStroke.getKeyStroke(AwtKeyEvent.VK_F, primaryModifier),
            KeyStroke.getKeyStroke(AwtKeyEvent.VK_H, primaryModifier),
            KeyStroke.getKeyStroke(AwtKeyEvent.VK_G, primaryModifier),
            KeyStroke.getKeyStroke(AwtKeyEvent.VK_G, primaryModifier or InputEvent.SHIFT_DOWN_MASK),
            KeyStroke.getKeyStroke(AwtKeyEvent.VK_F3, 0),
            KeyStroke.getKeyStroke(AwtKeyEvent.VK_F3, InputEvent.SHIFT_DOWN_MASK),

            // Selection/Editing
            KeyStroke.getKeyStroke(AwtKeyEvent.VK_A, primaryModifier),
            KeyStroke.getKeyStroke(AwtKeyEvent.VK_C, primaryModifier),
            KeyStroke.getKeyStroke(AwtKeyEvent.VK_V, primaryModifier),
            KeyStroke.getKeyStroke(AwtKeyEvent.VK_X, primaryModifier),
            KeyStroke.getKeyStroke(AwtKeyEvent.VK_D, primaryModifier),
            KeyStroke.getKeyStroke(AwtKeyEvent.VK_SLASH, primaryModifier),

            // Save (BOSS may also handle this)
            KeyStroke.getKeyStroke(AwtKeyEvent.VK_S, primaryModifier),
        )

        return editorShortcuts.any { it == keyStroke }
    }

    /**
     * Debug utility: Lists all current key bindings in the text area.
     */
    fun debugPrintKeyBindings(textArea: RSyntaxTextArea) {
        println("=== RSyntaxTextArea Key Bindings ===")

        val inputMap = textArea.getInputMap(JComponent.WHEN_FOCUSED)
        val actionMap = textArea.actionMap

        inputMap.allKeys()?.forEach { keyStroke ->
            val actionKey = inputMap.get(keyStroke)
            val action = actionMap.get(actionKey)
            println("  $keyStroke -> $actionKey (${action?.javaClass?.simpleName ?: "null"})")
        }

        println("====================================")
    }
}

/**
 * Extension function to easily configure an RSyntaxTextArea for BOSS integration.
 */
fun RSyntaxTextArea.configureBossKeyBindings() {
    RSyntaxKeyboardBridge.configureKeyBindings(this)
}
