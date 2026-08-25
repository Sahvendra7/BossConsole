package ai.rever.boss.app

import ai.rever.boss.focusmode.FocusModeSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * Pins where the tools launcher goes.
 *
 * A plugin is reached by clicking its icon in a strip, so a strip that is switched off takes every
 * plugin in it with it. The failure this rules out is silent and total: no crash, no empty state,
 * just a tool nobody can open and no hint that there was ever a way.
 */
class ToolLauncherPlacementTest {
    @Test
    fun `both strips on means no launcher`() {
        // Every plugin is already one click away. A launcher here would be a second way to do
        // something that is not hard, taking a row of rail the icons themselves want.
        assertEquals(
            ToolLauncherPlacement.NONE,
            toolLauncherPlacement(leftStripHidden = false, rightStripHidden = false),
        )
    }

    @Test
    fun `the launcher goes in whichever strip is left`() {
        assertEquals(
            ToolLauncherPlacement.RIGHT_STRIP,
            toolLauncherPlacement(leftStripHidden = true, rightStripHidden = false),
            "left strip gone, so it goes in the right one",
        )
        assertEquals(
            ToolLauncherPlacement.LEFT_STRIP,
            toolLauncherPlacement(leftStripHidden = false, rightStripHidden = true),
            "right strip gone, so it goes in the left one",
        )
    }

    @Test
    fun `neither strip sends it to the host actions`() {
        assertEquals(
            ToolLauncherPlacement.HOST_ACTIONS,
            toolLauncherPlacement(leftStripHidden = true, rightStripHidden = true),
        )
    }

    @Test
    fun `the launcher never lands in the same group as the rail placement`() {
        // Load-bearing for FOCUS_QUICK_ACTION_COUNT, which the right rail reserves height from and
        // which stays at three. The launcher joins that group only in HOST_ACTIONS, and the rail
        // placement needs a right strip - so the two must be mutually exclusive by construction.
        // If this ever fails, the rail under-reserves and pushes an icon off the bottom.
        val settings = FocusModeSettings()
        listOf(false, true).forEach { leftHidden ->
            val launcher = toolLauncherPlacement(leftStripHidden = leftHidden, rightStripHidden = false)
            val quickActions =
                focusQuickActionsPlacement(
                    settings = settings,
                    topBarHidden = true,
                    rightStripHidden = false,
                    showTopBar = false,
                )
            if (quickActions == FocusQuickActionsPlacement.RIGHT_RAIL) {
                assertNotEquals(
                    ToolLauncherPlacement.HOST_ACTIONS,
                    launcher,
                    "the rail hosts the actions only when the right strip is up, and the launcher " +
                        "joins them only when both strips are down",
                )
            }
        }
    }

    @Test
    fun `every combination of the two flags is covered, and each is distinct`() {
        // The truth table in one place. Focus mode is deliberately absent from the signature -
        // a strip the user hover-reveals is still their strip, and moving the launcher into the
        // other rail for two seconds and back would be worse than leaving it alone - so these two
        // booleans are the whole input and this is the whole behaviour.
        val table =
            listOf(false, true).flatMap { l ->
                listOf(false, true).map { r -> Triple(l, r, toolLauncherPlacement(l, r)) }
            }

        assertEquals(4, table.size)
        assertEquals(
            4,
            table.map { it.third }.toSet().size,
            "each combination should reach a different placement, got: $table",
        )
    }
}
