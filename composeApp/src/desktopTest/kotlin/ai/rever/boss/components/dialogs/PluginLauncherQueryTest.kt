package ai.rever.boss.components.dialogs

import ai.rever.boss.plugin.api.PanelId
import ai.rever.boss.plugin.api.SidebarItem
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Extension
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins what the plugins dialog's search matches.
 *
 * The dialog is the only way to reach a plugin when both icon strips are off, so a filter that is
 * too strict is not a cosmetic annoyance: it is a plugin the user can see listed one keystroke
 * earlier and cannot find now.
 */
class PluginLauncherQueryTest {
    private fun item(
        id: String,
        label: String,
    ) = SidebarItem(
        pluginContentId = PanelId(panelId = id, defaultOrder = 0, pluginId = "test"),
        icon = Icons.Outlined.Extension,
        label = label,
    )

    private val terminal = item(id = "terminal-tab", label = "Terminal")

    @Test
    fun `an empty query matches everything`() {
        // The dialog opens as a full list rather than an empty state waiting to be typed into.
        assertTrue(matchesPluginQuery(terminal, ""))
        assertTrue(matchesPluginQuery(terminal, "   "), "whitespace is not a query either")
    }

    @Test
    fun `it matches the label`() {
        assertTrue(matchesPluginQuery(terminal, "Term"))
        assertTrue(matchesPluginQuery(terminal, "term"), "case must not matter")
        assertTrue(matchesPluginQuery(terminal, "MINA"), "substring, not prefix")
    }

    @Test
    fun `it matches the id too`() {
        // The label is what the tooltip says; the id is what a plugin's own documentation and the
        // Toolbox call it. Someone searching either should find it.
        assertTrue(matchesPluginQuery(terminal, "terminal-tab"))
        assertTrue(matchesPluginQuery(item(id = "boss-notes", label = "Notes"), "boss-"))
    }

    @Test
    fun `it does not match something else`() {
        assertFalse(matchesPluginQuery(terminal, "editor"))
    }

    @Test
    fun `surrounding whitespace is ignored`() {
        // Typing into a search field picks up trailing spaces constantly.
        assertTrue(matchesPluginQuery(terminal, "  Terminal  "))
    }
}
