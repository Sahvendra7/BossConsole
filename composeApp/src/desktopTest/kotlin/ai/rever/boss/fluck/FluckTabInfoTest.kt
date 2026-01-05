package ai.rever.boss.fluck

import ai.rever.boss.components.plugin.tab_types.fluck.FluckTabInfo
import ai.rever.boss.components.registery.TabTypeId
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for FluckTabInfo navigation state management.
 *
 * Tests cover:
 * - URL navigation tracking (Issue #379)
 * - Navigation history (back/forward)
 * - Thread safety of URL state
 * - Empty URL handling
 */
class FluckTabInfoTest {

    private fun createTabInfo(
        url: String = "https://example.com",
        currentUrl: String = url
    ): FluckTabInfo {
        return FluckTabInfo(
            id = "test-tab",
            typeId = TabTypeId("fluck"),
            _title = "Test Tab",
            url = url,
            _currentUrl = currentUrl
        )
    }

    // ==================== URL NAVIGATION TESTS (Issue #379) ====================

    @Test
    fun `currentUrl returns initial URL when no navigation has occurred`() {
        val tabInfo = createTabInfo(url = "https://initial.com")
        assertEquals("https://initial.com", tabInfo.currentUrl)
    }

    @Test
    fun `currentUrl returns navigated URL after navigation`() {
        val tabInfo = createTabInfo(url = "https://initial.com")
        tabInfo.navigateToPage("Page B", "https://navigated.com")

        assertEquals("https://navigated.com", tabInfo.currentUrl)
    }

    @Test
    fun `currentUrl tracks multiple navigations correctly`() {
        val tabInfo = createTabInfo(url = "https://a.com")

        tabInfo.navigateToPage("Page B", "https://b.com")
        assertEquals("https://b.com", tabInfo.currentUrl)

        tabInfo.navigateToPage("Page C", "https://c.com")
        assertEquals("https://c.com", tabInfo.currentUrl)

        tabInfo.navigateToPage("Page D", "https://d.com")
        assertEquals("https://d.com", tabInfo.currentUrl)
    }

    // ==================== NAVIGATION HISTORY TESTS ====================

    @Test
    fun `navigateBack returns to previous URL`() {
        val tabInfo = createTabInfo(url = "https://a.com")
        tabInfo.navigateToPage("Page A", "https://a.com") // Add to history
        tabInfo.navigateToPage("Page B", "https://b.com")

        tabInfo.navigateBack()

        assertEquals("https://a.com", tabInfo.currentUrl)
    }

    @Test
    fun `navigateForward returns to next URL after navigateBack`() {
        val tabInfo = createTabInfo(url = "https://a.com")
        tabInfo.navigateToPage("Page A", "https://a.com")
        tabInfo.navigateToPage("Page B", "https://b.com")
        tabInfo.navigateBack()

        tabInfo.navigateForward()

        assertEquals("https://b.com", tabInfo.currentUrl)
    }

    @Test
    fun `navigateBack at start of history does nothing`() {
        val tabInfo = createTabInfo(url = "https://only.com")
        tabInfo.navigateToPage("Only Page", "https://only.com")

        tabInfo.navigateBack() // Should not throw or change URL

        assertEquals("https://only.com", tabInfo.currentUrl)
    }

    @Test
    fun `navigateForward at end of history does nothing`() {
        val tabInfo = createTabInfo(url = "https://last.com")
        tabInfo.navigateToPage("Last Page", "https://last.com")

        tabInfo.navigateForward() // Should not throw or change URL

        assertEquals("https://last.com", tabInfo.currentUrl)
    }

    @Test
    fun `navigation after navigateBack truncates forward history`() {
        val tabInfo = createTabInfo(url = "https://a.com")
        tabInfo.navigateToPage("Page A", "https://a.com")
        tabInfo.navigateToPage("Page B", "https://b.com")
        tabInfo.navigateToPage("Page C", "https://c.com")

        tabInfo.navigateBack() // Now at B
        tabInfo.navigateToPage("Page D", "https://d.com") // Should truncate C

        assertEquals("https://d.com", tabInfo.currentUrl)

        // Forward should not go to C (it was truncated)
        tabInfo.navigateForward()
        assertEquals("https://d.com", tabInfo.currentUrl) // Still at D
    }

    // ==================== EMPTY URL HANDLING TESTS ====================

    @Test
    fun `empty URL is handled gracefully`() {
        val tabInfo = createTabInfo(url = "")
        assertEquals("", tabInfo.currentUrl)
    }

    @Test
    fun `navigation to empty URL updates currentUrl`() {
        val tabInfo = createTabInfo(url = "https://initial.com")
        tabInfo.navigateToPage("Empty", "")

        assertEquals("", tabInfo.currentUrl)
    }

    // ==================== THREAD SAFETY TESTS ====================

    @Test
    fun `concurrent navigation updates are thread-safe`() {
        val tabInfo = createTabInfo(url = "https://initial.com")
        val executor = Executors.newFixedThreadPool(10)
        val latch = CountDownLatch(100)
        val errors = mutableListOf<Throwable>()

        repeat(100) { i ->
            executor.submit {
                try {
                    tabInfo.navigateToPage("Page $i", "https://page$i.com")
                } catch (e: Throwable) {
                    synchronized(errors) { errors.add(e) }
                } finally {
                    latch.countDown()
                }
            }
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS), "Timed out waiting for threads")
        executor.shutdown()

        assertTrue(errors.isEmpty(), "Concurrent navigation caused errors: $errors")
        // URL should be one of the navigated URLs (last writer wins)
        assertTrue(tabInfo.currentUrl.startsWith("https://page"))
    }

    @Test
    fun `concurrent reads and writes are thread-safe`() {
        val tabInfo = createTabInfo(url = "https://initial.com")
        val executor = Executors.newFixedThreadPool(10)
        val latch = CountDownLatch(200)
        val errors = mutableListOf<Throwable>()
        val readResults = mutableListOf<String>()

        // 100 writers
        repeat(100) { i ->
            executor.submit {
                try {
                    tabInfo.navigateToPage("Page $i", "https://page$i.com")
                } catch (e: Throwable) {
                    synchronized(errors) { errors.add(e) }
                } finally {
                    latch.countDown()
                }
            }
        }

        // 100 readers
        repeat(100) {
            executor.submit {
                try {
                    val url = tabInfo.currentUrl
                    synchronized(readResults) { readResults.add(url) }
                } catch (e: Throwable) {
                    synchronized(errors) { errors.add(e) }
                } finally {
                    latch.countDown()
                }
            }
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS), "Timed out waiting for threads")
        executor.shutdown()

        assertTrue(errors.isEmpty(), "Concurrent read/write caused errors: $errors")
        // All reads should be valid URLs (not corrupted)
        readResults.forEach { url ->
            assertTrue(
                url == "https://initial.com" || url.startsWith("https://page"),
                "Invalid URL read: $url"
            )
        }
    }

    // ==================== DUPLICATE NAVIGATION TESTS ====================

    @Test
    fun `duplicate consecutive navigation does not add to history`() {
        val tabInfo = createTabInfo(url = "https://a.com")
        tabInfo.navigateToPage("Page A", "https://a.com")
        val initialHistorySize = tabInfo.navigationHistory.size

        tabInfo.navigateToPage("Page A", "https://a.com") // Same URL

        assertEquals(initialHistorySize, tabInfo.navigationHistory.size)
    }

    // ==================== OBJECT IDENTITY TESTS (Stale Reference Bug) ====================

    @Test
    fun `navigation updates are visible on original object after copy operations`() {
        // This test verifies the fix for the stale object reference bug.
        // When FluckTabComponent caches tabInfo and title/icon updates create copies,
        // navigation must update the ORIGINAL object (not the copy) for recovery to work.

        val original = createTabInfo(url = "https://initial.com")

        // Simulate title update creating a copy (what updateTitle() does)
        val copyAfterTitleUpdate = original.copy(_title = "New Title")

        // Navigate on the ORIGINAL object (what the fixed onNavigationUpdate does)
        original.navigateToPage("Page B", "https://navigated.com")

        // The original should have the updated URL
        assertEquals("https://navigated.com", original.currentUrl)

        // The copy should still have the old URL (it's a different object)
        // This demonstrates why we must update the original, not look up from tabsState
        assertEquals("https://initial.com", copyAfterTitleUpdate.currentUrl)
    }

    @Test
    fun `multiple copy operations do not affect navigation on original`() {
        val original = createTabInfo(url = "https://a.com")

        // Simulate multiple UI updates creating copies
        val copy1 = original.copy(_title = "Title 1")
        val copy2 = copy1.copy(_title = "Title 2")
        val copy3 = copy2.copy(_title = "Title 3")

        // Navigate on original multiple times
        original.navigateToPage("Page B", "https://b.com")
        original.navigateToPage("Page C", "https://c.com")
        original.navigateToPage("Page D", "https://d.com")

        // Original has latest URL (for recovery)
        assertEquals("https://d.com", original.currentUrl)

        // All copies have stale URLs (demonstrating the bug this fix addresses)
        assertEquals("https://a.com", copy1.currentUrl)
        assertEquals("https://a.com", copy2.currentUrl)
        assertEquals("https://a.com", copy3.currentUrl)
    }

    @Test
    fun `object identity is preserved after navigation`() {
        val original = createTabInfo(url = "https://initial.com")
        val reference = original // Same object reference (simulates FluckTabComponent.fluckTabInfo)

        original.navigateToPage("Page B", "https://navigated.com")

        // Both references point to the same object
        assertTrue(original === reference, "Object identity should be preserved")
        assertEquals(original.currentUrl, reference.currentUrl)
    }
}
