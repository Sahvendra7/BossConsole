package ai.rever.boss.keymap.handler

import ai.rever.boss.keymap.model.KeyBinding
import ai.rever.boss.keymap.model.KeymapSettings
import ai.rever.boss.keymap.model.ShortcutContext
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.key

/**
 * Matches keyboard events to key bindings.
 * Handles context-aware matching and modifier key resolution.
 */
class KeymapMatcher(
    private val settings: KeymapSettings
) {
    /**
     * Find the first matching key binding for a keyboard event in a specific context.
     * Returns null if no match is found.
     *
     * Priority order:
     * 1. Context-specific shortcuts (e.g., BROWSER)
     * 2. WORKSPACE shortcuts (work everywhere)
     * 3. GLOBAL shortcuts (fallback)
     */
    fun match(event: KeyEvent, context: ShortcutContext): KeyBinding? {
        // First check context-specific bindings
        val contextCandidates = getEnabledBindingsForContext(context)

        val contextMatch = contextCandidates.firstOrNull { binding ->
            matchesBinding(event, binding)
        }

        if (contextMatch != null) return contextMatch

        // Check WORKSPACE shortcuts (work in all contexts)
        if (context != ShortcutContext.WORKSPACE) {
            val workspaceCandidates = getEnabledBindingsForContext(ShortcutContext.WORKSPACE)

            val workspaceMatch = workspaceCandidates.firstOrNull { binding ->
                matchesBinding(event, binding)
            }

            if (workspaceMatch != null) return workspaceMatch
        }

        // If no context-specific match, check GLOBAL shortcuts as fallback
        // (but only if we're not already in GLOBAL context)
        if (context != ShortcutContext.GLOBAL) {
            val globalCandidates = getEnabledBindingsForContext(ShortcutContext.GLOBAL)

            val globalMatch = globalCandidates.firstOrNull { binding ->
                matchesBinding(event, binding)
            }

            if (globalMatch != null) return globalMatch
        }

        return null
    }

    /**
     * Find all matching key bindings for a keyboard event (including global context).
     * Returns list ordered by specificity (context-specific first, global last).
     */
    fun matchAll(event: KeyEvent, context: ShortcutContext): List<KeyBinding> {
        val contextBindings = getEnabledBindingsForContext(context)
        val globalBindings = if (context != ShortcutContext.GLOBAL) {
            getEnabledBindingsForContext(ShortcutContext.GLOBAL)
        } else {
            emptyList()
        }

        val matches = mutableListOf<KeyBinding>()

        // Check context-specific bindings first
        contextBindings.forEach { binding ->
            if (matchesBinding(event, binding)) {
                matches.add(binding)
            }
        }

        // Then check global bindings
        globalBindings.forEach { binding ->
            if (matchesBinding(event, binding)) {
                matches.add(binding)
            }
        }

        return matches
    }

    /**
     * Check if a keyboard event matches a specific key binding.
     */
    private fun matchesBinding(event: KeyEvent, binding: KeyBinding): Boolean {
        if (!binding.enabled) return false

        // Check if key matches
        if (!keyMatches(event.key, binding.key)) return false

        // Check modifiers
        val hasCmd = binding.modifiers.any { it.equals("Cmd", true) || it.equals("Meta", true) }
        val hasCtrl = binding.modifiers.any { it.equals("Ctrl", true) || it.equals("Control", true) }
        val hasShift = binding.modifiers.any { it.equals("Shift", true) }
        val hasAlt = binding.modifiers.any { it.equals("Alt", true) || it.equals("Option", true) }

        // On macOS, Cmd key sets isMetaPressed
        // On Windows/Linux, Ctrl key sets isMetaPressed
        // So "Cmd" in binding should match isMetaPressed
        val eventMeta = event.isMetaPressed
        val eventCtrl = event.isCtrlPressed
        val eventShift = event.isShiftPressed
        val eventAlt = event.isAltPressed

        // Match logic: binding requires Cmd OR Ctrl → event has Meta OR Ctrl
        val modifierMatch = (hasCmd == eventMeta || hasCtrl == eventCtrl) &&
                hasShift == eventShift &&
                hasAlt == eventAlt

        return modifierMatch
    }

    /**
     * Check if event key matches binding key.
     * Handles key name normalization and aliases.
     */
    private fun keyMatches(eventKey: Key, bindingKeyName: String): Boolean {
        // Extract key name from the Key object
        // Key.toString() format is "Key: X" where X is the key name
        val eventKeyString = eventKey.toString()
        val eventKeyName = if (eventKeyString.startsWith("Key: ")) {
            eventKeyString.substring(5).trim()
        } else {
            eventKey.keyCode.toString()
        }

        val eventKeyNormalized = normalizeKeyName(eventKeyName)
        val bindingKeyNormalized = normalizeKeyName(bindingKeyName)

        return eventKeyNormalized.equals(bindingKeyNormalized, ignoreCase = true)
    }

    /**
     * Normalize key names to handle variations.
     * Examples: "Space"/"Spacebar", "Left"/"DirectionLeft", "Enter"/"Return"
     * Also handles Compose's symbol representations (e.g., ␣ for space)
     * Handles character-to-word mappings for numbers and symbols (e.g., "-" -> "Minus", "0" -> "Zero")
     */
    private fun normalizeKeyName(keyName: String): String {
        // Handle special symbol characters that Compose uses
        return when (keyName) {
            "␣" -> "Space"  // Compose renders space as ␣ (U+2423 OPEN BOX)
            // Number character to word mappings
            "0" -> "Zero"
            "1" -> "One"
            "2" -> "Two"
            "3" -> "Three"
            "4" -> "Four"
            "5" -> "Five"
            "6" -> "Six"
            "7" -> "Seven"
            "8" -> "Eight"
            "9" -> "Nine"
            // Symbol character to word mappings
            "-" -> "Minus"
            "=" -> "Equals"
            "+" -> "Plus"
            "[" -> "OpenBracket"
            "]" -> "CloseBracket"
            "/" -> "Slash"
            "\\" -> "Backslash"
            ";" -> "Semicolon"
            "'" -> "Apostrophe"
            "," -> "Comma"
            "." -> "Period"
            "`" -> "Grave"
            // Arrow character to word mappings
            "←" -> "Left"
            "→" -> "Right"
            "↑" -> "Up"
            "↓" -> "Down"
            else -> when (keyName.lowercase()) {
                "spacebar", "space" -> "Space"
                "directionleft", "left" -> "Left"
                "directionright", "right" -> "Right"
                "directionup", "up" -> "Up"
                "directiondown", "down" -> "Down"
                "return", "enter" -> "Enter"
                "escape", "esc" -> "Esc"
                // Word names normalize to themselves (case-insensitive)
                "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine" -> keyName.replaceFirstChar { it.uppercase() }
                "minus", "equals", "plus" -> keyName.replaceFirstChar { it.uppercase() }
                "openbracket", "closebracket", "slash", "backslash" -> keyName.replaceFirstChar { it.uppercase() }
                "semicolon", "apostrophe", "comma", "period", "grave" -> keyName.replaceFirstChar { it.uppercase() }
                else -> keyName
            }
        }
    }

    /**
     * Get all enabled bindings for a specific context.
     */
    private fun getEnabledBindingsForContext(context: ShortcutContext): List<KeyBinding> {
        return settings.shortcuts.values
            .filter { it.enabled && it.context == context }
    }

    /**
     * Get the display string for a matched binding.
     */
    fun getDisplayString(binding: KeyBinding): String {
        return binding.displayString()
    }

    companion object {
        /**
         * Create a KeymapMatcher from settings.
         */
        fun from(settings: KeymapSettings): KeymapMatcher {
            return KeymapMatcher(settings)
        }
    }
}
