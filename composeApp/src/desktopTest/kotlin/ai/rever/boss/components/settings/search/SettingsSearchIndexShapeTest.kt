package ai.rever.boss.components.settings.search

import ai.rever.boss.components.bars.ChromeBar
import ai.rever.boss.components.bars.displayName
import ai.rever.boss.components.settings.sidebar.SettingsSection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/** Properties of the index that hold regardless of what the sources currently say. */
class SettingsSearchIndexShapeTest {
    /**
     * The guard the whole (section, group, label) identity exists for.
     *
     * Performance declares "Warning Threshold" and "Critical Threshold" under both "Memory
     * Thresholds" and "CPU Thresholds", and Browser Engine declares "Rendering mode" under both
     * "Rendering" and "Effective Chromium command line". Had any of those been written without its
     * group, two entries would collapse to one key here - and in the window, one search result
     * would scroll to one row while lighting up two.
     */
    @Test
    fun `no two entries share a result key`() {
        val duplicates =
            SettingsSearchIndex.builtIn
                .groupBy { it.resultKey }
                .filterValues { it.size > 1 }

        if (duplicates.isNotEmpty()) {
            fail(
                "These search entries collide on (section, group, label), so they cannot be told " +
                    "apart when navigating or highlighting: ${duplicates.keys.sorted()}",
            )
        }
    }

    /** A new section must not be silently unsearchable. Sections with no controls still get one entry. */
    @Test
    fun `every section appears in the index`() {
        val covered = SettingsSearchIndex.builtIn.mapNotNull { it.section }.toSet()
        val missing = SettingsSection.entries.toSet() - covered

        assertTrue(
            missing.isEmpty(),
            "These settings sections cannot be found by search at all: ${missing.map { it.name }.sorted()}. " +
                "Add entries for their controls, or a `delegated(...)` line if the host does not own the page.",
        )
    }

    /**
     * Recomputes the templated family rather than restating it.
     *
     * `WindowAppearanceSettings` builds these labels with `"Show ${bar.displayName()}"` over
     * `ChromeBar.entries`, so the index holds four literals that no source file contains verbatim -
     * which is why the drift test has to exempt them. Adding a fifth bar has to fail somewhere, and
     * this is that somewhere.
     */
    @Test
    fun `the Bars group lists exactly one entry per ChromeBar`() {
        val expected = ChromeBar.entries.map { "Show ${it.displayName()}" }.sorted()
        val actual =
            SettingsSearchIndex.builtIn
                .filter { it.section == SettingsSection.WINDOW_APPEARANCE && it.group == "Bars" }
                .map { it.label }
                .filter { it.startsWith("Show ") }
                .sorted()

        assertEquals(expected, actual, "the indexed Bars toggles have drifted from ChromeBar.entries")
    }

    /**
     * The four delegated sections plus Shortcuts own no host controls, so they must stay
     * section-level. A hand-added BossTerm label here would produce a result that navigates to the
     * Terminal page and then highlights nothing, because there is no host control carrying it.
     */
    @Test
    fun `delegated sections carry a single non-highlightable entry`() {
        val delegated =
            setOf(
                SettingsSection.TERMINAL,
                SettingsSection.BOSS_EDITOR,
                SettingsSection.LANGUAGE_SERVERS,
                SettingsSection.LLM_PROVIDERS,
                SettingsSection.KEYMAP,
            )

        delegated.forEach { section ->
            val entries = SettingsSearchIndex.builtIn.filter { it.section == section }
            assertEquals(1, entries.size, "${section.name} should have exactly one section-level entry")
            assertTrue(
                entries.single().highlightable.not(),
                "${section.name} is rendered by another module, so its entry must not claim a control to highlight",
            )
        }
    }

    @Test
    fun `keywords are lowercase and never repeat the label`() {
        SettingsSearchIndex.builtIn.forEach { entry ->
            entry.keywords.forEach { keyword ->
                assertEquals(keyword.lowercase(), keyword, "keyword '$keyword' on '${entry.label}' is not lowercase")
                assertTrue(keyword.isNotBlank(), "blank keyword on '${entry.label}'")
                assertTrue(
                    !keyword.equals(entry.label, ignoreCase = true),
                    "keyword '$keyword' just repeats the label '${entry.label}'",
                )
            }
        }
    }

    /** A plugin page is navigable by id and has nothing the host could scroll to. */
    @Test
    fun `a plugin page entry targets its page and claims no control`() {
        val entry = pluginPageEntry("jupyter-notebook", "Jupyter", "Kernel and notebook settings")

        assertEquals("jupyter-notebook", entry.pluginPageId)
        assertEquals(null, entry.section)
        assertTrue(entry.highlightable.not())
        assertEquals("Plugins", entry.breadcrumb)
    }
}
