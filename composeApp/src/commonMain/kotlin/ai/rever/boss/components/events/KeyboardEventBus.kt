package ai.rever.boss.components.events

import androidx.compose.ui.input.key.KeyEvent
import ai.rever.boss.keymap.model.ShortcutContext
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Represents a keyboard event with its source and context information.
 * Used for event bubbling through the component hierarchy.
 */
data class KeyboardEvent(
    val keyEvent: KeyEvent,
    val source: KeyEventSource,
    val context: ShortcutContext,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Identifies where a keyboard event originated from.
 * Used to determine event priority and handling logic.
 */
enum class KeyEventSource {
    /** Event intercepted at AWT window level before Compose */
    AWT_INTERCEPTOR,

    /** Event from terminal component */
    COMPONENT_TERMINAL,

    /** Event from browser component (Fluck) */
    COMPONENT_BROWSER,

    /** Event from code editor component */
    COMPONENT_EDITOR,

    /** Event from dialog components */
    COMPONENT_DIALOG,

    /** Event from workspace/BossApp level */
    WORKSPACE,

    /** Event from automated testing */
    TEST
}

/**
 * Priority levels for keyboard event handling.
 * Lower priority values are handled first (Component > Workspace > Global).
 */
enum class KeyboardEventPriority(val level: Int) {
    /** Highest priority - focused component handles first */
    COMPONENT(0),

    /** Medium priority - workspace/layout handles next */
    WORKSPACE(1),

    /** Lowest priority - global app-wide shortcuts handled last */
    GLOBAL(2);

    companion object {
        fun fromLevel(level: Int): KeyboardEventPriority {
            return entries.find { it.level == level } ?: GLOBAL
        }
    }
}

/**
 * Result of a keyboard event handler.
 */
data class KeyboardEventResult(
    val consumed: Boolean,
    val handlerName: String,
    val actionId: String? = null
)

/**
 * Central event bus for keyboard events.
 * Implements event bubbling with priority-based handling:
 * Component -> Workspace -> Global
 *
 * Each handler can consume the event or let it bubble to the next priority level.
 */
object KeyboardEventBus {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _events = MutableSharedFlow<KeyboardEvent>(
        replay = 0,
        extraBufferCapacity = 100,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    /**
     * Flow of all keyboard events emitted to the bus.
     */
    val events: SharedFlow<KeyboardEvent> = _events.asSharedFlow()

    /**
     * Handlers registered for each priority level.
     * Map: Priority -> List of (handlerName, handler function)
     *
     * Uses ConcurrentHashMap and CopyOnWriteArrayList for thread-safe access
     * from multiple coroutines without explicit synchronization.
     */
    private val handlers = ConcurrentHashMap<KeyboardEventPriority, CopyOnWriteArrayList<Pair<String, suspend (KeyboardEvent) -> KeyboardEventResult>>>()

    /**
     * Debug mode - logs all events and handling results.
     */
    var debugMode: Boolean = false

    /**
     * Emits a keyboard event to the bus.
     * Events are processed synchronously through the priority chain.
     *
     * @param event The keyboard event to emit
     * @return true if any handler consumed the event, false otherwise
     */
    suspend fun emit(event: KeyboardEvent): Boolean {
        // Emit to flow for observers
        _events.emit(event)

        // Process through priority chain
        val priorities = KeyboardEventPriority.entries.sortedBy { it.level }

        for (priority in priorities) {
            val priorityHandlers = handlers[priority] ?: continue

            for ((handlerName, handler) in priorityHandlers) {
                try {
                    val result = handler(event)

                    if (result.consumed) {
                        return true
                    }
                } catch (e: Exception) {
                    println("Error in keyboard handler '$handlerName': ${e.message}")
                }
            }
        }

        return false
    }

    /**
     * Subscribes to keyboard events with a specific priority level.
     * Handlers are called in priority order: COMPONENT -> WORKSPACE -> GLOBAL.
     *
     * @param priority The priority level for this handler
     * @param handlerName Name for debugging purposes
     * @param handler Function that processes the event and returns whether it was consumed
     * @return Job that can be cancelled to unsubscribe
     */
    fun subscribe(
        priority: KeyboardEventPriority,
        handlerName: String,
        handler: suspend (KeyboardEvent) -> KeyboardEventResult
    ): Job {
        // Add handler to the list for this priority (thread-safe with atomic computeIfAbsent)
        handlers.computeIfAbsent(priority) { CopyOnWriteArrayList() }.add(handlerName to handler)

        if (debugMode) {
            println("[KeyboardEventBus] Registered handler '$handlerName' with priority ${priority.name}")
        }

        // Return a job that removes the handler when cancelled
        return scope.launch {
            try {
                awaitCancellation()
            } finally {
                handlers[priority]?.removeAll { it.first == handlerName }
                if (debugMode) {
                    println("[KeyboardEventBus] Unregistered handler '$handlerName'")
                }
            }
        }
    }

    /**
     * Clears all registered handlers.
     * Useful for testing or resetting the event bus.
     */
    fun clearHandlers() {
        handlers.clear()
        if (debugMode) {
            println("[KeyboardEventBus] Cleared all handlers")
        }
    }

    /**
     * Gets the count of registered handlers for each priority level.
     * Useful for debugging.
     */
    fun getHandlerCounts(): Map<KeyboardEventPriority, Int> {
        return handlers.mapValues { it.value.size }
    }
}
