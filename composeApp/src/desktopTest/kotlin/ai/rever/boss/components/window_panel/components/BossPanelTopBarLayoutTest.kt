package ai.rever.boss.components.window_panel.components

import ai.rever.boss.components.plugin.PluginBuildInfo
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs
import kotlin.test.assertTrue

/**
 * Where the header's trailing controls actually land.
 *
 * A unit test of this file's model would have passed throughout the bug this pins: every child was
 * present, in the right order, with the right callbacks. The defect was purely in measurement -
 * two weighted children sharing free space 1:1, so the half the title did not use was laid out
 * past the buttons - and only a laid-out screen can see it.
 */
class BossPanelTopBarLayoutTest {
    @get:Rule
    val compose = createComposeRule()

    private companion object {
        val BAR_WIDTH = 420.dp

        /** The bar's own trailing padding (2.dp) plus the button's content padding (2.dp). */
        val EDGE_TOLERANCE = 6.dp

        const val SHORT_TITLE = "A"
        const val LONG_TITLE = "A panel title long enough that it has to be ellipsized in here"
    }

    private var title by mutableStateOf(SHORT_TITLE)

    private fun show(buildInfo: PluginBuildInfo? = null) {
        compose.setContent {
            Box(modifier = Modifier.width(BAR_WIDTH)) {
                BossPanelTopBar(
                    title = title,
                    isHovered = true,
                    onMinimize = {},
                    buildInfo = buildInfo,
                    onBuildTagClick = {},
                )
            }
        }
        compose.waitForIdle()
    }

    /** A header control, found by the content description its icon carries. */
    private fun control(label: String) = compose.onNodeWithContentDescription(label)

    /** Laid-out bounds of a header control, in the root's coordinates. */
    private fun bounds(label: String) = control(label).getUnclippedBoundsInRoot()

    @Test
    fun `Minimize sits at the right edge of the bar`() {
        show()

        val gap = BAR_WIDTH - bounds("Minimize").right
        assertTrue(
            gap <= EDGE_TOLERANCE,
            "Minimize should be flush right; it is $gap short of the edge (bar $BAR_WIDTH)",
        )
    }

    @Test
    fun `the More kebab sits directly left of Minimize, not adrift in the middle`() {
        show()

        val more = bounds("More")
        val minimize = bounds("Minimize")

        assertTrue(
            more.right <= minimize.left && (minimize.left - more.right) <= EDGE_TOLERANCE,
            "More should abut Minimize; More ends at ${more.right}, Minimize starts at ${minimize.left}",
        )
    }

    @Test
    fun `the trailing controls do not drift with the length of the title`() {
        // The sharpest form of the bug: the leftover was half the title's *unused* width, so the
        // controls crept further from the edge the shorter the title. A single-title assertion with
        // a generous tolerance could pass while that was still true.
        show()
        val withShortTitle = bounds("Minimize").right

        title = LONG_TITLE
        compose.waitForIdle()
        val withLongTitle = bounds("Minimize").right

        assertTrue(
            abs((withShortTitle - withLongTitle).value) < 1f,
            "the right edge moved with the title: $withShortTitle vs $withLongTitle",
        )
    }

    @Test
    fun `the build tag does not push the controls off the right edge`() {
        // The tag is laid out inside the title group, so it must consume the group's space and not
        // the trailing controls'.
        show(
            buildInfo =
                PluginBuildInfo(
                    pluginId = "p",
                    displayName = "Probe",
                    version = "1.0.3",
                    signedBytes = false,
                    storeSourced = false,
                    reloadStamp = 1_754_890_231_447L,
                ),
        )

        val gap = BAR_WIDTH - bounds("Minimize").right
        assertTrue(
            gap <= EDGE_TOLERANCE,
            "Minimize should stay flush right with a tag present; it is $gap short of the edge",
        )
    }
}
