package ai.rever.boss.components.overlays

import ai.rever.boss.plugin.ui.menu.NativeMenuNode
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The host-side half of the native menu decision: which menus are eligible, and how the app's
 * richer [ContextMenuItem] collapses into the native model.
 *
 * Rendering itself is not reachable here - `java.awt.MenuComponent` throws `HeadlessException` -
 * so the engine's own behaviour is covered in plugin-ui-core's `NativeContextMenusTest`.
 */
class NativeContextMenuRoutingTest {
    // ----- eligibility -----

    @Test
    fun `a plain menu is representable`() {
        val items =
            listOf(
                ContextMenuItem(text = "Split Right"),
                ContextMenuItem(isDivider = true),
                ContextMenuItem(text = "Close Tab"),
            )
        assertTrue(items.isNativeRepresentable())
    }

    @Test
    fun `an inline trailing button makes a menu non-representable`() {
        // The run-history and sidebar menus use these for inline delete and edit. Dropping them
        // silently would remove an affordance rather than merely restyle it.
        val items =
            listOf(
                ContextMenuItem(
                    text = "npm run dev",
                    trailingIcon = Icons.Filled.Delete,
                    onTrailingClick = {},
                ),
            )
        assertFalse(items.isNativeRepresentable())
    }

    @Test
    fun `a second trailing button also disqualifies`() {
        val items =
            listOf(
                ContextMenuItem(text = "entry", secondaryTrailingIcon = Icons.Filled.Delete),
            )
        assertFalse(items.isNativeRepresentable())
    }

    @Test
    fun `a leading icon disqualifies`() {
        assertFalse(listOf(ContextMenuItem(text = "Open", icon = Icons.Filled.Delete)).isNativeRepresentable())
    }

    @Test
    fun `a submenu is inspected too, at any depth`() {
        val nested =
            listOf(
                ContextMenuItem(
                    text = "Options",
                    subMenu =
                        listOf(
                            ContextMenuItem(
                                text = "More",
                                subMenu = listOf(ContextMenuItem(text = "Deep", icon = Icons.Filled.Delete)),
                            ),
                        ),
                ),
            )
        assertFalse(nested.isNativeRepresentable(), "a rich item nested two levels down still counts")

        val clean =
            listOf(
                ContextMenuItem(text = "Options", subMenu = listOf(ContextMenuItem(text = "Save"))),
            )
        assertTrue(clean.isNativeRepresentable())
    }

    @Test
    fun `an empty menu is trivially representable`() {
        assertTrue(emptyList<ContextMenuItem>().isNativeRepresentable())
    }

    // ----- conversion -----

    @Test
    fun `dividers become separators and items keep their label`() {
        val nodes =
            listOf(
                ContextMenuItem(text = "New Tab"),
                ContextMenuItem(isDivider = true),
                ContextMenuItem(text = "Close"),
            ).toNativeMenuNodes()

        assertEquals(3, nodes.size)
        assertEquals("New Tab", (nodes[0] as NativeMenuNode.Item).label)
        assertEquals(NativeMenuNode.Separator, nodes[1])
        assertEquals("Close", (nodes[2] as NativeMenuNode.Item).label)
    }

    @Test
    fun `submenus convert recursively`() {
        val nodes =
            listOf(
                ContextMenuItem(
                    text = "Share",
                    subMenu = listOf(ContextMenuItem(text = "Tab"), ContextMenuItem(text = "Window")),
                ),
            ).toNativeMenuNodes()

        val submenu = nodes.single() as NativeMenuNode.Submenu
        assertEquals("Share", submenu.label)
        assertEquals(listOf("Tab", "Window"), submenu.children.map { (it as NativeMenuNode.Item).label })
    }

    @Test
    fun `the click handler is carried across, not dropped`() {
        var fired = 0
        val nodes = listOf(ContextMenuItem(text = "Go", onClick = { fired += 1 })).toNativeMenuNodes()
        (nodes.single() as NativeMenuNode.Item).action()
        assertEquals(1, fired)
    }

    @Test
    fun `a divider that also carries text is still a separator`() {
        // isDivider wins: the drawn renderer ignores text on a divider row too.
        val nodes = listOf(ContextMenuItem(text = "ignored", isDivider = true)).toNativeMenuNodes()
        assertEquals(NativeMenuNode.Separator, nodes.single())
    }
}
