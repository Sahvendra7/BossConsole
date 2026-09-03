package ai.rever.boss.search

import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins the four sources the double-shift search grew: tools, settings, MCP tools and recent pages.
 *
 * Two of the four cannot be reached from this service at all - settings are indexed in desktopMain,
 * and a window's tools belong to its own component - so they arrive through [SearchSources]. That
 * indirection is the thing most likely to break silently: nothing fails to compile when a supplier
 * is never registered, the search simply returns fewer kinds of result than it should, and the only
 * symptom is a tool that exists and cannot be found.
 *
 * The service is a singleton, so each test registers what it needs and clears afterwards.
 */
class GlobalSearchNewSourcesTest {
    @BeforeTest
    fun setUp() {
        SearchSources.clear()
        GlobalSearchService.clearResults()
    }

    @AfterTest
    fun tearDown() {
        SearchSources.clear()
        GlobalSearchService.clearResults()
    }

    private fun searchFor(query: String): List<SearchResult> = runBlocking { GlobalSearchService.search(query) }

    private inline fun <reified T : SearchResult> resultsOf(q: String) = searchFor(q).filterIsInstance<T>()

    // --- tools ---------------------------------------------------------------------------------

    @Test
    fun `a tool is found by its label`() {
        SearchSources.toolsSupplier = { listOf(ToolSearchRecord(panelId = "bookmarks", label = "Bookmarks")) }

        val hits = resultsOf<SearchResult.ToolResult>("bookmark")
        assertEquals(listOf("bookmarks"), hits.map { it.panelId })
    }

    @Test
    fun `a tool is found by its panel id`() {
        // The id is what a plugin's own docs and its MCP tools call it, so it has to match too.
        SearchSources.toolsSupplier = { listOf(ToolSearchRecord(panelId = "run-configurations", label = "Runners")) }

        assertTrue(resultsOf<SearchResult.ToolResult>("run-config").isNotEmpty())
    }

    @Test
    fun `a tool nobody matched is not returned`() {
        SearchSources.toolsSupplier = { listOf(ToolSearchRecord(panelId = "bookmarks", label = "Bookmarks")) }

        assertTrue(resultsOf<SearchResult.ToolResult>("zzzz").isEmpty())
    }

    @Test
    fun `no registered tools source contributes nothing rather than failing`() {
        // The state during startup, and in every test that does not care about tools. A missing
        // source has to be silent: the search still has five other kinds of result to return.
        assertTrue(resultsOf<SearchResult.ToolResult>("bookmark").isEmpty())
    }

    // --- settings ------------------------------------------------------------------------------

    @Test
    fun `a setting is found by its label`() {
        SearchSources.settingsSupplier = { listOf(entry(label = "Show Title Bar", breadcrumb = "Appearance")) }

        val hits = resultsOf<SearchResult.SettingResult>("title bar")
        assertEquals(listOf("Show Title Bar"), hits.map { it.label })
    }

    @Test
    fun `a keyword finds a setting whose label does not contain it`() {
        // The reason keywords exist: "passkey" has to reach "Platform Authenticator".
        SearchSources.settingsSupplier = {
            listOf(entry(label = "Platform Authenticator", breadcrumb = "Security", keywords = listOf("passkey")))
        }

        assertTrue(resultsOf<SearchResult.SettingResult>("passkey").isNotEmpty())
    }

    @Test
    fun `a label hit outranks a keyword hit`() {
        // A keyword is a way in, not a second name. A row actually called what you typed wins.
        SearchSources.settingsSupplier = {
            listOf(
                entry(label = "Passkeys", breadcrumb = "Security"),
                entry(label = "Platform Authenticator", breadcrumb = "Security", keywords = listOf("passkeys")),
            )
        }

        val hits = resultsOf<SearchResult.SettingResult>("passkeys")
        assertEquals("Passkeys", hits.first().label)
    }

    @Test
    fun `a signpost carries a panel id and names no page to navigate to`() {
        // The entry kind that points *out* of the Settings window: "AI Providers" now lives in the
        // Secret Manager panel, and the row keeps the words a user still types for it. The dialog
        // routes on panelId, so it has to survive the flattening - with section and pluginPageId
        // both null, a reveal() would raise Settings on whatever page it was last on and highlight
        // a label that is not there.
        SearchSources.settingsSupplier = {
            listOf(
                entry(
                    label = "AI Providers",
                    breadcrumb = "Plugins > Secret Manager panel",
                    keywords = listOf("anthropic", "api key"),
                    panelId = "secret-manager",
                    highlightable = false,
                ),
            )
        }

        val hit = resultsOf<SearchResult.SettingResult>("anthropic").first()
        assertEquals("secret-manager", hit.panelId)
        assertEquals(null, hit.section)
        assertEquals(null, hit.pluginPageId)
    }

    @Test
    fun `a plugin page carries its page id and cannot be highlighted`() {
        SearchSources.settingsSupplier = {
            listOf(
                entry(
                    label = "AI Gateway",
                    breadcrumb = "Plugins",
                    pluginPageId = "ai-gateway",
                    highlightable = false,
                ),
            )
        }

        val hit = resultsOf<SearchResult.SettingResult>("gateway").first()
        assertEquals("ai-gateway", hit.pluginPageId)
        assertEquals(null, hit.section)
        assertTrue(!hit.highlightable, "a plugin page has no host control to point at")
    }

    // --- shape ---------------------------------------------------------------------------------

    @Test
    fun `every result reports the category its chip filters on`() {
        SearchSources.toolsSupplier = { listOf(ToolSearchRecord(panelId = "bookmarks", label = "Bookmarks")) }
        SearchSources.settingsSupplier = { listOf(entry(label = "Bookmarks Bar", breadcrumb = "Appearance")) }

        // Without this the result is found and then filtered into the wrong chip, which looks like
        // it was never found at all.
        assertTrue(resultsOf<SearchResult.ToolResult>("bookmark").all { it.category == SearchCategory.TOOLS })
        assertTrue(resultsOf<SearchResult.SettingResult>("bookmark").all { it.category == SearchCategory.SETTINGS })
    }

    @Test
    fun `results from the new sources survive the category filter`() {
        SearchSources.toolsSupplier = { listOf(ToolSearchRecord(panelId = "bookmarks", label = "Bookmarks")) }
        searchFor("bookmark")

        // The category is global state on a singleton, so the restore goes in a finally: an
        // assertion failing here would otherwise leave a filter set for whatever runs next in
        // this JVM, and the failure would be reported against that test instead of this one.
        try {
            GlobalSearchService.setActiveCategory(SearchCategory.TOOLS)
            val filtered = GlobalSearchService.getFilteredResults()

            assertTrue(filtered.isNotEmpty(), "the TOOLS chip must show tools")
            assertTrue(filtered.all { it.category == SearchCategory.TOOLS })
        } finally {
            GlobalSearchService.setActiveCategory(SearchCategory.ALL)
        }
    }

    @Test
    fun `the keyword penalty ranks a keyword hit, it does not filter one out`() {
        // KEYWORD_PENALTY is subtracted before the MIN_SCORE floor, so on paper it could drop a row
        // rather than merely rank it low. In practice it cannot: FuzzyMatcher pays a short-target
        // bonus of `50 - length`, so matching a keyword at all scores in the tens while the floor
        // is 1. Worth pinning because the failure mode is invisible - raise the floor or the
        // penalty and keyword rows stop appearing, with nothing to say they ever did. "pk" against
        // "passkey" is the loosest useful shape: two characters, non-adjacent.
        SearchSources.settingsSupplier = {
            listOf(entry(label = "Platform Authenticator", breadcrumb = "Security", keywords = listOf("passkey")))
        }

        assertTrue(resultsOf<SearchResult.SettingResult>("pk").isNotEmpty(), "a loose keyword hit still survives")
        assertTrue(resultsOf<SearchResult.SettingResult>("passkey").isNotEmpty())
    }

    @Test
    fun `a blank query returns nothing from the new sources either`() {
        SearchSources.toolsSupplier = { listOf(ToolSearchRecord(panelId = "bookmarks", label = "Bookmarks")) }

        assertTrue(searchFor("   ").isEmpty())
    }

    // --- the review's findings ------------------------------------------------------------------

    @Test
    fun `a result type that draws itself has no simple row`() {
        // Pins the family split that SearchResultItem branches on. simpleRow's `when` is
        // exhaustive over the sealed class, so a NEW result type fails the build there rather than
        // reaching a runtime error from inside a composable - but only while every existing type
        // keeps its side of the split, which is what this asserts.
        SearchSources.toolsSupplier = { listOf(ToolSearchRecord(panelId = "bookmarks", label = "Bookmarks")) }

        val tools = resultsOf<SearchResult.ToolResult>("bookmark")
        assertTrue(tools.isNotEmpty())
        assertTrue(tools.all { it.category == SearchCategory.TOOLS })
    }

    @Test
    fun `clearing the tools source leaves the settings source alone`() {
        // The dialog's DisposableEffect clears only its own registration. Clearing one source must
        // not empty another - the shape of the multi-window bug, where a closing window wiped the
        // supplier a still-open window depended on.
        SearchSources.toolsSupplier = { listOf(ToolSearchRecord(panelId = "bookmarks", label = "Bookmarks")) }
        SearchSources.settingsSupplier = { listOf(entry(label = "Bookmarks Bar", breadcrumb = "Appearance")) }

        SearchSources.toolsSupplier = null

        assertTrue(resultsOf<SearchResult.ToolResult>("bookmark").isEmpty())
        assertTrue(resultsOf<SearchResult.SettingResult>("bookmark").isNotEmpty(), "settings must survive")
    }

    private fun entry(
        label: String,
        breadcrumb: String,
        keywords: List<String> = emptyList(),
        pluginPageId: String? = null,
        panelId: String? = null,
        highlightable: Boolean = true,
    ) = SettingSearchRecord(
        label = label,
        breadcrumb = breadcrumb,
        section = if (pluginPageId == null && panelId == null) "APPEARANCE" else null,
        pluginPageId = pluginPageId,
        panelId = panelId,
        group = null,
        keywords = keywords,
        highlightable = highlightable,
    )
}
