package ai.rever.boss.window

import ai.rever.boss.plugin.api.TabIcon
import ai.rever.boss.plugin.api.TabInfo
import ai.rever.boss.plugin.api.TabTypeId
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.ui.graphics.vector.ImageVector
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for [ClosedTabHistory], the stack behind Cmd+Shift+T.
 */
class ClosedTabHistoryTest {
    private data class FakeTab(
        override val id: String,
        override val title: String = id,
    ) : TabInfo {
        override val typeId = TabTypeId("test", "test")
        override val icon: ImageVector = Icons.Default.Add
        override val tabIcon: TabIcon? = null
    }

    private val windowA = "window-a"
    private val windowB = "window-b"

    @BeforeEach
    fun reset() {
        ClosedTabHistory.clear(windowA)
        ClosedTabHistory.clear(windowB)
    }

    @Test
    fun `pops most recently closed first`() {
        ClosedTabHistory.record(windowA, FakeTab("first"))
        ClosedTabHistory.record(windowA, FakeTab("second"))

        assertEquals("second", ClosedTabHistory.pop(windowA)?.id)
        assertEquals("first", ClosedTabHistory.pop(windowA)?.id)
        assertNull(ClosedTabHistory.pop(windowA))
    }

    @Test
    fun `history is per window`() {
        ClosedTabHistory.record(windowA, FakeTab("a-tab"))

        // A different window must not be able to reopen another window's tab.
        assertNull(ClosedTabHistory.pop(windowB))
        assertEquals("a-tab", ClosedTabHistory.pop(windowA)?.id)
    }

    @Test
    fun `re-closing a reopened tab moves it to the top instead of duplicating`() {
        ClosedTabHistory.record(windowA, FakeTab("x"))
        ClosedTabHistory.record(windowA, FakeTab("y"))
        // The user reopened x and closed it again.
        ClosedTabHistory.record(windowA, FakeTab("x"))

        assertEquals("x", ClosedTabHistory.pop(windowA)?.id)
        assertEquals("y", ClosedTabHistory.pop(windowA)?.id)
        assertNull(ClosedTabHistory.pop(windowA), "x should not be on the stack twice")
    }

    @Test
    fun `history is bounded`() {
        repeat(ClosedTabHistory.MAX_ENTRIES + 10) { i ->
            ClosedTabHistory.record(windowA, FakeTab("tab-$i"))
        }

        assertEquals(ClosedTabHistory.MAX_ENTRIES, ClosedTabHistory.depths.value[windowA])

        // The oldest entries were dropped, not the newest.
        val newest = ClosedTabHistory.pop(windowA)
        assertEquals("tab-${ClosedTabHistory.MAX_ENTRIES + 9}", newest?.id)
    }

    @Test
    fun `depth drives the menu item's enabled state`() {
        assertFalse(ClosedTabHistory.hasEntries(windowA))
        assertNull(ClosedTabHistory.depths.value[windowA])

        ClosedTabHistory.record(windowA, FakeTab("only"))
        assertTrue(ClosedTabHistory.hasEntries(windowA))
        assertEquals(1, ClosedTabHistory.depths.value[windowA])

        ClosedTabHistory.pop(windowA)
        // Back to absent rather than 0, so the menu item greys out again.
        assertFalse(ClosedTabHistory.hasEntries(windowA))
        assertNull(ClosedTabHistory.depths.value[windowA])
    }

    @Test
    fun `closing a window drops its history`() {
        ClosedTabHistory.record(windowA, FakeTab("doomed"))
        ClosedTabHistory.clear(windowA)

        assertNull(ClosedTabHistory.pop(windowA))
        assertNull(ClosedTabHistory.depths.value[windowA])
    }
}
