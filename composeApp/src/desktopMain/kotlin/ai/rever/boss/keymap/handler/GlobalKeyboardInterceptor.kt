package ai.rever.boss.keymap.handler

import ai.rever.boss.components.events.KeyEventSource
import ai.rever.boss.components.events.KeyboardEvent
import ai.rever.boss.components.events.KeyboardEventBus
import ai.rever.boss.keymap.lifecycle.ShortcutLifecycleManager
import ai.rever.boss.keymap.model.KeymapSettings
import ai.rever.boss.keymap.model.KeyBinding
import ai.rever.boss.keymap.model.ShortcutContext
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent as ComposeKeyEvent
import androidx.compose.ui.input.key.KeyEventType
import kotlinx.coroutines.*
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent as AwtKeyEvent

/**
 * Desktop implementation of global keyboard interceptor using AWT KeyListener.
 * Intercepts keyboard events at the AWT window level before Compose focus system.
 */
actual class GlobalKeyboardInterceptor actual constructor(
    keymapSettings: KeymapSettings
) {
    private var settings: KeymapSettings = keymapSettings
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val attachedWindows = mutableSetOf<ComposeWindow>()

    /**
     * AWT KeyListener that intercepts events at the window level.
     */
    private val keyListener = object : KeyAdapter() {
        override fun keyPressed(e: AwtKeyEvent) {
            // 🔍 DIAGNOSTIC: Log EVERY key press that reaches AWT interceptor
            println("⌨️  [GlobalKeyboardInterceptor.keyPressed] AWT Key detected: keyCode=${e.keyCode}, keyChar=${e.keyChar}, modifiers: Cmd=${e.isMetaDown}, Ctrl=${e.isControlDown}, Shift=${e.isShiftDown}, Alt=${e.isAltDown}")

            // Convert AWT KeyEvent to Compose KeyEvent
            val composeEvent = convertAwtToCompose(e) ?: return

            // Check if this is a global-priority shortcut
            val binding = findGlobalPriorityBinding(composeEvent, e)
            if (binding != null) {
                println("🌐 [GlobalKeyboardInterceptor] Found GLOBAL priority shortcut: ${binding.actionId}")

                // Check lifecycle condition asynchronously
                scope.launch {
                    val enabled = ShortcutLifecycleManager.isEnabled(binding.actionId)
                    if (enabled) {
                        println("   ✓ [GlobalKeyboardInterceptor] Lifecycle enabled, emitting to bus")

                        // Emit to KeyboardEventBus
                        KeyboardEventBus.emit(
                            KeyboardEvent(
                                keyEvent = composeEvent,
                                source = KeyEventSource.AWT_INTERCEPTOR,
                                context = binding.context
                            )
                        )

                        // Consume the AWT event to prevent further processing
                        e.consume()
                    } else {
                        println("   🚫 [GlobalKeyboardInterceptor] Lifecycle disabled, skipping")
                    }
                }
            }
            // If not a global priority shortcut, let it flow to Compose
        }

        override fun keyReleased(e: AwtKeyEvent) {
            // We only handle keyPressed for shortcuts
            // keyReleased is ignored to avoid duplicate processing
        }
    }

    /**
     * Attaches the keyboard interceptor to a ComposeWindow.
     */
    actual fun attach(window: Any) {
        if (window !is ComposeWindow) {
            println("[GlobalKeyboardInterceptor] Warning: Expected ComposeWindow but got ${window::class.simpleName}")
            return
        }

        if (attachedWindows.contains(window)) {
            println("[GlobalKeyboardInterceptor] Warning: Already attached to window")
            return
        }

        window.addKeyListener(keyListener)
        attachedWindows.add(window)

        println("[GlobalKeyboardInterceptor] Attached to window")
    }

    /**
     * Detaches the keyboard interceptor from a ComposeWindow.
     */
    actual fun detach(window: Any) {
        if (window !is ComposeWindow) {
            println("[GlobalKeyboardInterceptor] Warning: Expected ComposeWindow but got ${window::class.simpleName}")
            return
        }

        window.removeKeyListener(keyListener)
        attachedWindows.remove(window)

        println("[GlobalKeyboardInterceptor] Detached from window")
    }

    /**
     * Updates the keymap settings.
     */
    actual fun updateSettings(newSettings: KeymapSettings) {
        settings = newSettings
        println("[GlobalKeyboardInterceptor] Updated settings")
    }

    /**
     * Converts AWT KeyEvent to Compose KeyEvent.
     * Returns null if the conversion fails.
     */
    private fun convertAwtToCompose(awtEvent: AwtKeyEvent): ComposeKeyEvent? {
        try {
            // Map AWT keyCode to Compose Key
            val key = mapAwtKeyToCompose(awtEvent.keyCode)

            // Create a synthetic Compose KeyEvent
            // Note: This is a simplified conversion. The actual KeyEvent class
            // is internal to Compose, so we use the public API indirectly.
            // In practice, this will be used for matching only, not for full event recreation.

            return null // TODO: Proper Compose KeyEvent creation
            // For now, we'll rely on matching against the raw AWT event data
        } catch (e: Exception) {
            println("[GlobalKeyboardInterceptor] Error converting AWT event: ${e.message}")
            return null
        }
    }

    /**
     * Finds a global-priority binding for the given event.
     * Returns null if no global binding matches.
     */
    private fun findGlobalPriorityBinding(composeEvent: ComposeKeyEvent?, awtEvent: AwtKeyEvent): KeyBinding? {
        // Extract modifiers from AWT event
        val modifiers = mutableListOf<String>()
        if (awtEvent.isMetaDown) modifiers.add("Cmd")
        if (awtEvent.isControlDown) modifiers.add("Ctrl")
        if (awtEvent.isShiftDown) modifiers.add("Shift")
        if (awtEvent.isAltDown) modifiers.add("Alt")

        // Get the key name
        val keyName = getKeyName(awtEvent)

        // Search for matching global binding
        for (binding in settings.shortcuts.values) {
            // Only check GLOBAL context shortcuts for AWT interception
            if (binding.context != ShortcutContext.GLOBAL) continue
            if (!binding.enabled) continue

            // Match key and modifiers
            if (matchesBinding(binding, keyName, modifiers)) {
                return binding
            }
        }

        return null
    }

    /**
     * Checks if the event matches a binding.
     */
    private fun matchesBinding(binding: KeyBinding, keyName: String, modifiers: List<String>): Boolean {
        // Normalize key names for comparison
        val bindingKey = binding.key.uppercase()
        val eventKey = keyName.uppercase()

        // Key must match
        if (bindingKey != eventKey) return false

        // Modifiers must match (order-independent)
        val bindingMods = binding.modifiers.map { it.uppercase() }.sorted()
        val eventMods = modifiers.map { it.uppercase() }.sorted()

        return bindingMods == eventMods
    }

    /**
     * Gets a normalized key name from AWT KeyEvent.
     */
    private fun getKeyName(e: AwtKeyEvent): String {
        return when (e.keyCode) {
            AwtKeyEvent.VK_ENTER -> "Enter"
            AwtKeyEvent.VK_BACK_SPACE -> "Backspace"
            AwtKeyEvent.VK_TAB -> "Tab"
            AwtKeyEvent.VK_ESCAPE -> "Escape"
            AwtKeyEvent.VK_SPACE -> "Spacebar"
            AwtKeyEvent.VK_LEFT -> "DirectionLeft"
            AwtKeyEvent.VK_UP -> "DirectionUp"
            AwtKeyEvent.VK_RIGHT -> "DirectionRight"
            AwtKeyEvent.VK_DOWN -> "DirectionDown"
            AwtKeyEvent.VK_DELETE -> "Delete"
            else -> {
                // For letters and numbers, use the key char
                val char = e.keyChar
                if (char.isLetterOrDigit()) {
                    char.uppercase()
                } else {
                    // Use key code name
                    AwtKeyEvent.getKeyText(e.keyCode)
                }
            }
        }
    }

    /**
     * Maps AWT keyCode to Compose Key.
     * This is a partial mapping for common keys.
     */
    private fun mapAwtKeyToCompose(keyCode: Int): Key {
        return when (keyCode) {
            AwtKeyEvent.VK_A -> Key.A
            AwtKeyEvent.VK_B -> Key.B
            AwtKeyEvent.VK_C -> Key.C
            AwtKeyEvent.VK_D -> Key.D
            AwtKeyEvent.VK_E -> Key.E
            AwtKeyEvent.VK_F -> Key.F
            AwtKeyEvent.VK_G -> Key.G
            AwtKeyEvent.VK_H -> Key.H
            AwtKeyEvent.VK_I -> Key.I
            AwtKeyEvent.VK_J -> Key.J
            AwtKeyEvent.VK_K -> Key.K
            AwtKeyEvent.VK_L -> Key.L
            AwtKeyEvent.VK_M -> Key.M
            AwtKeyEvent.VK_N -> Key.N
            AwtKeyEvent.VK_O -> Key.O
            AwtKeyEvent.VK_P -> Key.P
            AwtKeyEvent.VK_Q -> Key.Q
            AwtKeyEvent.VK_R -> Key.R
            AwtKeyEvent.VK_S -> Key.S
            AwtKeyEvent.VK_T -> Key.T
            AwtKeyEvent.VK_U -> Key.U
            AwtKeyEvent.VK_V -> Key.V
            AwtKeyEvent.VK_W -> Key.W
            AwtKeyEvent.VK_X -> Key.X
            AwtKeyEvent.VK_Y -> Key.Y
            AwtKeyEvent.VK_Z -> Key.Z
            AwtKeyEvent.VK_0 -> Key.Zero
            AwtKeyEvent.VK_1 -> Key.One
            AwtKeyEvent.VK_2 -> Key.Two
            AwtKeyEvent.VK_3 -> Key.Three
            AwtKeyEvent.VK_4 -> Key.Four
            AwtKeyEvent.VK_5 -> Key.Five
            AwtKeyEvent.VK_6 -> Key.Six
            AwtKeyEvent.VK_7 -> Key.Seven
            AwtKeyEvent.VK_8 -> Key.Eight
            AwtKeyEvent.VK_9 -> Key.Nine
            AwtKeyEvent.VK_ENTER -> Key.Enter
            AwtKeyEvent.VK_BACK_SPACE -> Key.Backspace
            AwtKeyEvent.VK_TAB -> Key.Tab
            AwtKeyEvent.VK_ESCAPE -> Key.Escape
            AwtKeyEvent.VK_SPACE -> Key.Spacebar
            AwtKeyEvent.VK_LEFT -> Key.DirectionLeft
            AwtKeyEvent.VK_UP -> Key.DirectionUp
            AwtKeyEvent.VK_RIGHT -> Key.DirectionRight
            AwtKeyEvent.VK_DOWN -> Key.DirectionDown
            AwtKeyEvent.VK_DELETE -> Key.Delete
            AwtKeyEvent.VK_F1 -> Key.F1
            AwtKeyEvent.VK_F2 -> Key.F2
            AwtKeyEvent.VK_F3 -> Key.F3
            AwtKeyEvent.VK_F4 -> Key.F4
            AwtKeyEvent.VK_F5 -> Key.F5
            AwtKeyEvent.VK_F6 -> Key.F6
            AwtKeyEvent.VK_F7 -> Key.F7
            AwtKeyEvent.VK_F8 -> Key.F8
            AwtKeyEvent.VK_F9 -> Key.F9
            AwtKeyEvent.VK_F10 -> Key.F10
            AwtKeyEvent.VK_F11 -> Key.F11
            AwtKeyEvent.VK_F12 -> Key.F12
            else -> Key.Unknown
        }
    }

    /**
     * Cleanup when the interceptor is no longer needed.
     */
    fun dispose() {
        // Detach from all windows
        attachedWindows.toList().forEach { detach(it) }
        // Cancel all coroutines
        scope.cancel()
    }
}
