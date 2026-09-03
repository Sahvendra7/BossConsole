package ai.rever.boss.search

import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins the order the "All" view draws, arrows through and activates.
 *
 * `SearchResultsList` numbers rows by walking [SearchCategory] in declaration order, while the
 * keyboard indexes [GlobalSearchService.getFilteredResults]. If those two orders ever disagree
 * again, the highlighted row and the row Enter activates are different rows - a bug that looks
 * like the search opening the wrong thing, with nothing in a stack trace to say why.
 */
class GlobalSearchOrderingTest {
    @BeforeTest
    fun setUp() {
        SearchSources.clear()
        GlobalSearchService.clearResults()
        GlobalSearchService.setActiveCategory(SearchCategory.ALL)
    }

    @AfterTest
    fun tearDown() {
        SearchSources.clear()
        GlobalSearchService.clearResults()
        GlobalSearchService.setActiveCategory(SearchCategory.ALL)
    }

    private fun settingRecord(label: String) =
        SettingSearchRecord(
            label = label,
            breadcrumb = "Appearance",
            section = "APPEARANCE",
            pluginPageId = null,
            panelId = null,
            group = null,
            keywords = emptyList(),
            highlightable = true,
        )

    @Test
    fun `a tool leads the All view even when another category scores higher`() {
        // The "atlas" case: one tool against a pile of same-word matches from other sources. A
        // tool is the most actionable row the search has, so it does not get buried by score.
        SearchSources.toolsSupplier = { listOf(ToolSearchRecord(panelId = "atlas", label = "Atlas")) }
        SearchSources.settingsSupplier = { listOf(settingRecord("Atlas")) }

        runBlocking { GlobalSearchService.search("atlas") }
        val ordered = GlobalSearchService.getFilteredResults()

        assertTrue(ordered.isNotEmpty())
        assertEquals(SearchCategory.TOOLS, ordered.first().category, "Tools must lead the All view")
    }

    @Test
    fun `the filtered order is grouped by category, never interleaved`() {
        // What makes the drawn order and the keyboard order the same list: every row of a category
        // is contiguous, in declaration order. Interleaving is what broke row numbering.
        SearchSources.toolsSupplier = {
            listOf(
                ToolSearchRecord(panelId = "atlas", label = "Atlas"),
                ToolSearchRecord(panelId = "atlas-two", label = "Atlas Two"),
            )
        }
        SearchSources.settingsSupplier = { listOf(settingRecord("Atlas"), settingRecord("Atlas Bar")) }

        runBlocking { GlobalSearchService.search("atlas") }
        val categories = GlobalSearchService.getFilteredResults().map { it.category }

        assertEquals(categories.distinct(), categories.distinct().sortedBy { it.ordinal }, "declaration order")
        assertEquals(categories.distinct().size, categories.zipWithNext().count { it.first != it.second } + 1)
    }

    @Test
    fun `within one category the order is still by score`() {
        // Category order replaces score only BETWEEN categories. Inside one, the better match wins,
        // which is what the exact-label test in GlobalSearchNewSourcesTest depends on.
        SearchSources.settingsSupplier = { listOf(settingRecord("Passkeys"), settingRecord("Passkeys Extra Long")) }

        runBlocking { GlobalSearchService.search("passkeys") }
        val scores = GlobalSearchService.getFilteredResults().map { it.score }

        assertEquals(scores.sortedDescending(), scores)
    }

    @Test
    fun `filtering to one category keeps pure score order`() {
        SearchSources.settingsSupplier = { listOf(settingRecord("Passkeys"), settingRecord("Passkeys Extra Long")) }
        runBlocking { GlobalSearchService.search("passkeys") }

        try {
            GlobalSearchService.setActiveCategory(SearchCategory.SETTINGS)
            val scores = GlobalSearchService.getFilteredResults().map { it.score }

            assertEquals(scores.sortedDescending(), scores)
            assertTrue(scores.isNotEmpty())
        } finally {
            GlobalSearchService.setActiveCategory(SearchCategory.ALL)
        }
    }

    @Test
    fun `Tools is the first category after All`() {
        // The chip row, the section order and the row the keyboard starts on all read this order,
        // so it is worth asserting rather than trusting a diff not to reshuffle the enum.
        assertEquals(SearchCategory.ALL, SearchCategory.entries.first())
        assertEquals(SearchCategory.TOOLS, SearchCategory.entries[1])
    }
}
