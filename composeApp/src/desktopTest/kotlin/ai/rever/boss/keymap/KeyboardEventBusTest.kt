package ai.rever.boss.keymap

import ai.rever.boss.components.events.KeyEventSource
import ai.rever.boss.components.events.KeyboardEvent
import ai.rever.boss.components.events.KeyboardEventBus
import ai.rever.boss.components.events.KeyboardEventPriority
import ai.rever.boss.components.events.KeyboardEventResult
import ai.rever.boss.keymap.model.ShortcutContext
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent as ComposeKeyEvent
import androidx.compose.ui.input.key.KeyEventType
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for KeyboardEventBus component.
 *
 * Tests cover:
 * - Priority-based handler ordering
 * - Event consumption stopping propagation
 * - Handler registration and unregistration
 * - Multiple handlers at same priority
 */
class KeyboardEventBusTest {

    @BeforeEach
    fun setUp() {
        KeyboardEventBus.clearHandlers()
        KeyboardEventBus.debugMode = false
    }

    @AfterEach
    fun tearDown() {
        KeyboardEventBus.clearHandlers()
    }

    // ==================== PRIORITY ORDERING TESTS ====================

    @Test
    fun `handlers are called in priority order`() = runBlocking {
        val callOrder = mutableListOf<String>()

        // Register in reverse order to test sorting
        KeyboardEventBus.subscribe(KeyboardEventPriority.GLOBAL, "global") {
            callOrder.add("global")
            KeyboardEventResult(consumed = false, handlerName = "global")
        }

        KeyboardEventBus.subscribe(KeyboardEventPriority.COMPONENT, "component") {
            callOrder.add("component")
            KeyboardEventResult(consumed = false, handlerName = "component")
        }

        KeyboardEventBus.subscribe(KeyboardEventPriority.WORKSPACE, "workspace") {
            callOrder.add("workspace")
            KeyboardEventResult(consumed = false, handlerName = "workspace")
        }

        val event = createTestEvent()
        KeyboardEventBus.emit(event)

        assertEquals(listOf("component", "workspace", "global"), callOrder)
    }

    @Test
    fun `COMPONENT priority is highest`() {
        assertEquals(0, KeyboardEventPriority.COMPONENT.level)
    }

    @Test
    fun `WORKSPACE priority is medium`() {
        assertEquals(1, KeyboardEventPriority.WORKSPACE.level)
    }

    @Test
    fun `GLOBAL priority is lowest`() {
        assertEquals(2, KeyboardEventPriority.GLOBAL.level)
    }

    @Test
    fun `fromLevel returns correct priority`() {
        assertEquals(KeyboardEventPriority.COMPONENT, KeyboardEventPriority.fromLevel(0))
        assertEquals(KeyboardEventPriority.WORKSPACE, KeyboardEventPriority.fromLevel(1))
        assertEquals(KeyboardEventPriority.GLOBAL, KeyboardEventPriority.fromLevel(2))
    }

    @Test
    fun `fromLevel returns GLOBAL for unknown level`() {
        assertEquals(KeyboardEventPriority.GLOBAL, KeyboardEventPriority.fromLevel(99))
    }

    // ==================== EVENT CONSUMPTION TESTS ====================

    @Test
    fun `consumed event stops propagation`() = runBlocking {
        val callOrder = mutableListOf<String>()

        KeyboardEventBus.subscribe(KeyboardEventPriority.COMPONENT, "component") {
            callOrder.add("component")
            KeyboardEventResult(consumed = true, handlerName = "component")
        }

        KeyboardEventBus.subscribe(KeyboardEventPriority.WORKSPACE, "workspace") {
            callOrder.add("workspace")
            KeyboardEventResult(consumed = false, handlerName = "workspace")
        }

        val event = createTestEvent()
        val consumed = KeyboardEventBus.emit(event)

        assertTrue(consumed, "emit should return true when event is consumed")
        assertEquals(listOf("component"), callOrder, "Only component should be called")
    }

    @Test
    fun `unconsumed event propagates to all handlers`() = runBlocking {
        val callOrder = mutableListOf<String>()

        KeyboardEventBus.subscribe(KeyboardEventPriority.COMPONENT, "component") {
            callOrder.add("component")
            KeyboardEventResult(consumed = false, handlerName = "component")
        }

        KeyboardEventBus.subscribe(KeyboardEventPriority.WORKSPACE, "workspace") {
            callOrder.add("workspace")
            KeyboardEventResult(consumed = false, handlerName = "workspace")
        }

        KeyboardEventBus.subscribe(KeyboardEventPriority.GLOBAL, "global") {
            callOrder.add("global")
            KeyboardEventResult(consumed = false, handlerName = "global")
        }

        val event = createTestEvent()
        val consumed = KeyboardEventBus.emit(event)

        assertFalse(consumed, "emit should return false when no handler consumes event")
        assertEquals(3, callOrder.size, "All three handlers should be called")
    }

    @Test
    fun `emit returns false when no handlers registered`() = runBlocking {
        val event = createTestEvent()
        val consumed = KeyboardEventBus.emit(event)

        assertFalse(consumed)
    }

    // ==================== REGISTRATION TESTS ====================

    @Test
    fun `subscribe adds handler`() {
        KeyboardEventBus.subscribe(KeyboardEventPriority.GLOBAL, "test") {
            KeyboardEventResult(consumed = false, handlerName = "test")
        }

        val counts = KeyboardEventBus.getHandlerCounts()
        assertEquals(1, counts[KeyboardEventPriority.GLOBAL])
    }

    @Test
    fun `multiple handlers at same priority all called`() = runBlocking {
        val callCount = mutableListOf<Int>()

        KeyboardEventBus.subscribe(KeyboardEventPriority.GLOBAL, "handler1") {
            callCount.add(1)
            KeyboardEventResult(consumed = false, handlerName = "handler1")
        }

        KeyboardEventBus.subscribe(KeyboardEventPriority.GLOBAL, "handler2") {
            callCount.add(2)
            KeyboardEventResult(consumed = false, handlerName = "handler2")
        }

        val event = createTestEvent()
        KeyboardEventBus.emit(event)

        assertEquals(2, callCount.size, "Both handlers should be called")
        assertTrue(callCount.contains(1) && callCount.contains(2))
    }

    @Test
    fun `cancelled job removes handler`() = runBlocking {
        val job = KeyboardEventBus.subscribe(KeyboardEventPriority.GLOBAL, "test") {
            KeyboardEventResult(consumed = false, handlerName = "test")
        }

        assertEquals(1, KeyboardEventBus.getHandlerCounts()[KeyboardEventPriority.GLOBAL])

        job.cancel()
        delay(100) // Give time for cleanup

        assertEquals(0, KeyboardEventBus.getHandlerCounts()[KeyboardEventPriority.GLOBAL] ?: 0)
    }

    // ==================== CLEAR HANDLERS TESTS ====================

    @Test
    fun `clearHandlers removes all handlers`() {
        KeyboardEventBus.subscribe(KeyboardEventPriority.COMPONENT, "comp") {
            KeyboardEventResult(consumed = false, handlerName = "comp")
        }
        KeyboardEventBus.subscribe(KeyboardEventPriority.WORKSPACE, "work") {
            KeyboardEventResult(consumed = false, handlerName = "work")
        }
        KeyboardEventBus.subscribe(KeyboardEventPriority.GLOBAL, "global") {
            KeyboardEventResult(consumed = false, handlerName = "global")
        }

        KeyboardEventBus.clearHandlers()

        val counts = KeyboardEventBus.getHandlerCounts()
        assertTrue(counts.values.all { it == 0 } || counts.isEmpty())
    }

    // ==================== ERROR HANDLING TESTS ====================

    @Test
    fun `handler exception does not stop other handlers`() = runBlocking {
        val callOrder = mutableListOf<String>()

        KeyboardEventBus.subscribe(KeyboardEventPriority.COMPONENT, "failing") {
            callOrder.add("failing")
            throw RuntimeException("Test error")
        }

        KeyboardEventBus.subscribe(KeyboardEventPriority.WORKSPACE, "succeeding") {
            callOrder.add("succeeding")
            KeyboardEventResult(consumed = false, handlerName = "succeeding")
        }

        val event = createTestEvent()
        KeyboardEventBus.emit(event)

        assertEquals(listOf("failing", "succeeding"), callOrder, "Both handlers should be attempted")
    }

    // ==================== HANDLER COUNTS TESTS ====================

    @Test
    fun `getHandlerCounts returns empty map initially`() {
        KeyboardEventBus.clearHandlers()
        val counts = KeyboardEventBus.getHandlerCounts()

        // Should be empty or have zero counts
        assertTrue(counts.values.all { it == 0 } || counts.isEmpty())
    }

    @Test
    fun `getHandlerCounts returns correct counts per priority`() {
        KeyboardEventBus.subscribe(KeyboardEventPriority.COMPONENT, "comp1") {
            KeyboardEventResult(consumed = false, handlerName = "comp1")
        }
        KeyboardEventBus.subscribe(KeyboardEventPriority.COMPONENT, "comp2") {
            KeyboardEventResult(consumed = false, handlerName = "comp2")
        }
        KeyboardEventBus.subscribe(KeyboardEventPriority.GLOBAL, "global1") {
            KeyboardEventResult(consumed = false, handlerName = "global1")
        }

        val counts = KeyboardEventBus.getHandlerCounts()

        assertEquals(2, counts[KeyboardEventPriority.COMPONENT])
        assertEquals(1, counts[KeyboardEventPriority.GLOBAL])
    }

    // ==================== DEBUG MODE TESTS ====================

    @Test
    fun `debug mode can be enabled`() {
        KeyboardEventBus.debugMode = true
        assertTrue(KeyboardEventBus.debugMode)

        KeyboardEventBus.debugMode = false
        assertFalse(KeyboardEventBus.debugMode)
    }

    // ==================== KEY EVENT SOURCE TESTS ====================

    @Test
    fun `KeyEventSource has expected values`() {
        assertEquals(7, KeyEventSource.entries.size)
        assertTrue(KeyEventSource.entries.contains(KeyEventSource.AWT_INTERCEPTOR))
        assertTrue(KeyEventSource.entries.contains(KeyEventSource.COMPONENT_TERMINAL))
        assertTrue(KeyEventSource.entries.contains(KeyEventSource.COMPONENT_BROWSER))
        assertTrue(KeyEventSource.entries.contains(KeyEventSource.COMPONENT_EDITOR))
        assertTrue(KeyEventSource.entries.contains(KeyEventSource.COMPONENT_DIALOG))
        assertTrue(KeyEventSource.entries.contains(KeyEventSource.WORKSPACE))
        assertTrue(KeyEventSource.entries.contains(KeyEventSource.TEST))
    }

    // ==================== KEYBOARD EVENT RESULT TESTS ====================

    @Test
    fun `KeyboardEventResult consumed property works`() {
        val consumed = KeyboardEventResult(consumed = true, handlerName = "test")
        val notConsumed = KeyboardEventResult(consumed = false, handlerName = "test")

        assertTrue(consumed.consumed)
        assertFalse(notConsumed.consumed)
    }

    @Test
    fun `KeyboardEventResult includes actionId when provided`() {
        val result = KeyboardEventResult(
            consumed = true,
            handlerName = "test",
            actionId = "test.action"
        )

        assertEquals("test.action", result.actionId)
    }

    // ==================== HELPER FUNCTIONS ====================

    /**
     * Creates a test KeyboardEvent using mocked components.
     * Note: This creates a minimal event for testing event bus behavior.
     */
    private fun createTestEvent(): KeyboardEvent {
        // We need to create a mock KeyEvent for testing
        // Using a workaround since Compose KeyEvent requires native event
        return KeyboardEvent(
            keyEvent = createMockComposeKeyEvent(),
            source = KeyEventSource.TEST,
            context = ShortcutContext.GLOBAL,
            timestamp = System.currentTimeMillis()
        )
    }

    /**
     * Creates a mock Compose KeyEvent for testing.
     * This is a workaround since KeyEvent constructor is internal.
     */
    private fun createMockComposeKeyEvent(): ComposeKeyEvent {
        // Create a synthetic AWT event for testing using a Canvas as the dummy component
        val dummyComponent = java.awt.Canvas()
        val awtEvent = java.awt.event.KeyEvent(
            dummyComponent,
            java.awt.event.KeyEvent.KEY_PRESSED,
            System.currentTimeMillis(),
            0,
            java.awt.event.KeyEvent.VK_N,
            'N'
        )
        return ComposeKeyEvent(awtEvent)
    }
}
