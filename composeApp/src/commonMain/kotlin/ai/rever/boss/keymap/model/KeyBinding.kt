package ai.rever.boss.keymap.model

import androidx.compose.ui.input.key.Key
import kotlinx.serialization.Serializable

/**
 * Represents a single keyboard shortcut binding.
 *
 * @property actionId Unique identifier for the action (e.g., "window.new", "tab.close")
 * @property key The primary key name (e.g., "N", "T", "Space", "ArrowLeft")
 * @property modifiers List of modifier key names (e.g., ["Cmd", "Shift"], ["Ctrl", "Alt"])
 * @property context The context where this shortcut is active
 * @property enabled Whether this shortcut is currently enabled
 * @property category The category this shortcut belongs to (for UI grouping)
 * @property description Human-readable description of what this shortcut does
 */
@Serializable
data class KeyBinding(
    val actionId: String,
    val key: String,
    val modifiers: List<String> = emptyList(),
    val context: ShortcutContext = ShortcutContext.GLOBAL,
    val enabled: Boolean = true,
    val category: String = "Other",
    val description: String = ""
) {
    /**
     * Returns a display string for this key binding.
     * Examples: "Cmd+N", "Ctrl+Shift+T", "Alt+Left"
     */
    fun displayString(platform: String = System.getProperty("os.name")): String {
        val isMac = platform.contains("Mac", ignoreCase = true)

        val modifierStrings = modifiers.map { modifier ->
            when (modifier.lowercase()) {
                "cmd", "meta" -> if (isMac) "⌘" else "Ctrl"
                "ctrl", "control" -> if (isMac) "⌃" else "Ctrl"
                "shift" -> if (isMac) "⇧" else "Shift"
                "alt", "option" -> if (isMac) "⌥" else "Alt"
                else -> modifier
            }
        }

        val keyString = formatKeyDisplay(key)

        return (modifierStrings + keyString).joinToString(if (isMac) "" else "+")
    }

    /**
     * Formats the key name for display.
     */
    private fun formatKeyDisplay(keyName: String): String {
        return when (keyName.lowercase()) {
            "space", "spacebar" -> "Space"
            "arrowleft", "directionleft" -> "←"
            "arrowright", "directionright" -> "→"
            "arrowup", "directionup" -> "↑"
            "arrowdown", "directiondown" -> "↓"
            "enter", "return" -> "↩"
            "backspace" -> "⌫"
            "delete" -> "⌦"
            "escape", "esc" -> "Esc"
            "tab" -> "Tab"
            else -> keyName.uppercase()
        }
    }

    /**
     * Checks if this key binding matches the given key event properties.
     */
    fun matches(
        eventKey: String,
        isMetaPressed: Boolean,
        isCtrlPressed: Boolean,
        isShiftPressed: Boolean,
        isAltPressed: Boolean
    ): Boolean {
        if (!enabled) return false

        // Check if key matches
        if (!key.equals(eventKey, ignoreCase = true)) return false

        // Check modifiers
        val hasCmd = modifiers.any { it.equals("Cmd", true) || it.equals("Meta", true) }
        val hasCtrl = modifiers.any { it.equals("Ctrl", true) || it.equals("Control", true) }
        val hasShift = modifiers.any { it.equals("Shift", true) }
        val hasAlt = modifiers.any { it.equals("Alt", true) || it.equals("Option", true) }

        return (hasCmd == isMetaPressed || hasCtrl == isCtrlPressed) &&
                hasShift == isShiftPressed &&
                hasAlt == isAltPressed
    }

    /**
     * Returns a unique signature for this key binding (for conflict detection).
     * Format: "context:modifiers+key"
     * Example: "GLOBAL:Cmd+Shift+N"
     */
    fun signature(): String {
        val modifierStr = modifiers.sorted().joinToString("+")
        val keyStr = key.uppercase()
        return "${context.name}:${if (modifierStr.isNotEmpty()) "$modifierStr+" else ""}$keyStr"
    }

    companion object {
        /**
         * Creates a KeyBinding from a Compose Key object and modifiers.
         */
        fun fromComposeKey(
            actionId: String,
            key: Key,
            isMetaPressed: Boolean,
            isCtrlPressed: Boolean,
            isShiftPressed: Boolean,
            isAltPressed: Boolean,
            context: ShortcutContext = ShortcutContext.GLOBAL,
            category: String = "Other",
            description: String = ""
        ): KeyBinding {
            val modifiers = mutableListOf<String>()
            if (isMetaPressed) modifiers.add("Cmd")
            if (isCtrlPressed) modifiers.add("Ctrl")
            if (isShiftPressed) modifiers.add("Shift")
            if (isAltPressed) modifiers.add("Alt")

            return KeyBinding(
                actionId = actionId,
                key = key.keyCode.toString(),
                modifiers = modifiers,
                context = context,
                enabled = true,
                category = category,
                description = description
            )
        }
    }
}
