package ai.rever.boss.search

import ai.rever.boss.components.settings.search.SettingsSearchIndex
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Exercises the REAL settings registrar, not a hand-rolled stand-in.
 *
 * [GlobalSearchNewSourcesTest] builds its own entry-to-record mapping so it can control what is
 * indexed, which leaves the production mapping - the thing that actually runs - untested. That is a
 * gap with teeth: transposing `group` and `breadcrumb`, or dropping `panelId`, would keep that
 * whole suite green while the shipped app silently lost signposts or highlighted the wrong row,
 * because a search index is the one place staleness and misrouting are invisible.
 *
 * Asserts against a real built-in row rather than a fixture, so a rename in
 * `SettingsSearchEntries.kt` that this file does not follow shows up here. `SettingsSearchIndexDriftTest`
 * already guarantees the label exists; this guarantees it survives the trip into the global search.
 */
class SearchSourceRegistrarTest {
    @BeforeTest
    fun setUp() {
        SearchSources.clearForTests()
        GlobalSearchService.clearResults()
        GlobalSearchService.clearIndex()
        GlobalSearchService.setActiveCategory(SearchCategory.ALL)
    }

    @AfterTest
    fun tearDown() {
        SearchSources.clearForTests()
        GlobalSearchService.clearResults()
        GlobalSearchService.setActiveCategory(SearchCategory.ALL)
    }

    @Test
    fun `the real registrar carries a built-in row through with its section and breadcrumb`() {
        SettingsSearchIndex.registerWithGlobalSearch()

        val hit =
            runBlocking { GlobalSearchService.search("show title bar", windowId = null) }
                .filterIsInstance<SearchResult.SettingResult>()
                .firstOrNull { it.label == "Show Title Bar" }

        assertNotNull(hit, "the registrar must deliver a known built-in setting")
        // The two fields the window navigates and highlights by. Swapping them is the failure this
        // test exists for: search would still find the row and then land on the wrong page.
        assertEquals("WINDOW_APPEARANCE", hit.section)
        assertEquals("Title Bar", hit.group)
        assertEquals("Appearance > Title Bar", hit.breadcrumb)
        assertEquals(null, hit.pluginPageId)
        assertEquals(null, hit.panelId)
        assertTrue(hit.highlightable, "a real host control can be pointed at")
    }

    @Test
    fun `the real registrar ranks with the settings matcher, so a multi-word query works`() {
        // The reason the registrar hands over a function instead of rows. FuzzyMatcher cannot match
        // "title bar" against "Show Title Bar" as one target in a way that beats nothing - the
        // tokenised matcher can, and this proves the shipped wiring uses it.
        SettingsSearchIndex.registerWithGlobalSearch()

        val labels =
            runBlocking { GlobalSearchService.search("title bar", windowId = null) }
                .filterIsInstance<SearchResult.SettingResult>()
                .map { it.label }

        assertTrue(labels.isNotEmpty(), "a two-word query has to reach the settings index")
        assertTrue(labels.any { it == "Show Title Bar" })
    }

    @Test
    fun `a keyword on a real entry reaches its setting`() {
        // "passkey" -> "Platform Authenticator" is the canonical case the keywords exist for, and
        // it goes through the production keyword list rather than a fixture's.
        SettingsSearchIndex.registerWithGlobalSearch()

        val labels =
            runBlocking { GlobalSearchService.search("passkey", windowId = null) }
                .filterIsInstance<SearchResult.SettingResult>()
                .map { it.label }

        assertTrue("Platform Authenticator" in labels)
    }

    @Test
    fun `an unregistered settings source contributes nothing rather than failing the search`() {
        // The startup state. Registering is a single call in main(), so the failure mode if it is
        // ever dropped is silence - worth pinning that it is silence and not a crash.
        val results = runBlocking { GlobalSearchService.search("show title bar", windowId = null) }

        assertTrue(results.filterIsInstance<SearchResult.SettingResult>().isEmpty())
    }
}
