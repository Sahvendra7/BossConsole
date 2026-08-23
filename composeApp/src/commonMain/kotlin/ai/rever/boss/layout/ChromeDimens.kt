package ai.rever.boss.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.serialization.Serializable

/**
 * How tightly the window's chrome bars are drawn.
 *
 * Separate from *which* bars are on screen, which is `WindowAppearanceSettings.showTopBar` and its
 * neighbours. That answers "do I want this bar at all"; this answers "how much room may the bars I
 * do want take". The two are independent on purpose: on a 13" laptop the useful answer is usually
 * "all of them, but tighter", and per-bar switches alone cannot express it.
 */
@Serializable
enum class ChromeDensity {
    COMPACT,
    COMFORTABLE,
    SPACIOUS,
}

/**
 * The heights and widths of every host chrome bar, resolved from a [ChromeDensity].
 *
 * These were literals in the seven files that draw the bars, which meant there was no single knob
 * to turn and no way to state what the chrome costs. Read them through [BossChrome].dimens.
 *
 * Host chrome only. This deliberately does not live in `plugin-ui-core` alongside
 * `BossTheme.colors` / `BossTheme.space`: plugins never draw the title bar, the tab bar or the icon
 * strips, and that module carries binary-compatibility constraints for dynamically loaded plugins
 * that host layout tokens have no business touching.
 *
 * @property titleBarHeight The "Boss Console" row. On macOS this is also what keeps window content
 *   clear of the traffic lights, since the window sets `fullWindowContent` — see [MIN_TITLE_BAR].
 * @property topBarHeight The action bar (workspace controls, run bar, search, settings).
 * @property tabBarHeight The main panel's tab row. Floored by the new-tab button — see [MIN_TAB_BAR].
 * @property bottomBarHeight The status bar.
 * @property stripWidth One icon rail's width. Floored by the icon buttons — see [MIN_STRIP_WIDTH].
 * @property panelTopBarHeight A `SidePanel` header. Note this is *not* part of the main panel's
 *   budget — a browser tab in the main panel never pays it (see `ChromeMetrics`).
 * @property dividerThickness The hairline between stacked bars. `Divider`'s own default, named here
 *   so the budget can account for it.
 */
data class ChromeDimens(
    val titleBarHeight: Dp,
    val topBarHeight: Dp,
    val tabBarHeight: Dp,
    val bottomBarHeight: Dp,
    val stripWidth: Dp,
    val panelTopBarHeight: Dp,
    val dividerThickness: Dp = 1.dp,
) {
    companion object {
        /**
         * The shipped title bar height, and the floor for it.
         *
         * macOS draws the traffic lights over the top-left of the content (the window sets
         * `apple.awt.fullWindowContent`), so this row is what they sit in. 26.dp is what has
         * shipped; going below it risks the buttons overlapping whatever is drawn beneath.
         */
        val MIN_TITLE_BAR = 26.dp

        /**
         * The floor for [tabBarHeight]: `NEW_TAB_BUTTON_SIZE` is 32.dp, so anything under 36 leaves
         * the "+" button less than 2.dp of breathing room on each side.
         */
        val MIN_TAB_BAR = 36.dp

        /**
         * The floor for [stripWidth]: `SidebarIconRail.RowPitch` is a 32.dp button plus padding, so
         * a rail narrower than 36 starts squeezing the buttons against its edges.
         */
        val MIN_STRIP_WIDTH = 36.dp

        /** Today's shipped metrics. Changing these changes the app's default look. */
        val Comfortable =
            ChromeDimens(
                titleBarHeight = 26.dp,
                topBarHeight = 40.dp,
                tabBarHeight = 42.dp,
                bottomBarHeight = 30.dp,
                stripWidth = 40.dp,
                panelTopBarHeight = 28.dp,
            )

        /**
         * As tight as the existing content allows, for short screens.
         *
         * Every value here is at or above the floor its content imposes ([MIN_TITLE_BAR],
         * [MIN_TAB_BAR], [MIN_STRIP_WIDTH]); the title bar cannot shrink at all, since the shipped
         * height is already its floor. Worth ~20.dp of window height over [Comfortable].
         */
        val Compact =
            ChromeDimens(
                titleBarHeight = MIN_TITLE_BAR,
                // The tallest thing in the top bar is the 22.dp logo plus 2.dp of padding.
                topBarHeight = 32.dp,
                tabBarHeight = MIN_TAB_BAR,
                bottomBarHeight = 24.dp,
                stripWidth = MIN_STRIP_WIDTH,
                panelTopBarHeight = 24.dp,
            )

        /** Roomier, for a large display where the height is not the scarce resource. */
        val Spacious =
            ChromeDimens(
                titleBarHeight = 28.dp,
                topBarHeight = 44.dp,
                tabBarHeight = 46.dp,
                bottomBarHeight = 34.dp,
                stripWidth = 44.dp,
                panelTopBarHeight = 30.dp,
            )

        fun of(density: ChromeDensity): ChromeDimens =
            when (density) {
                ChromeDensity.COMPACT -> Compact
                ChromeDensity.COMFORTABLE -> Comfortable
                ChromeDensity.SPACIOUS -> Spacious
            }
    }
}

/**
 * The chrome metrics in force for this window.
 *
 * Defaults to [ChromeDimens.Comfortable] — the shipped values — so every bar renders exactly as it
 * did before this existed even where nothing provides it.
 */
val LocalChromeDimens = staticCompositionLocalOf { ChromeDimens.Comfortable }

/**
 * Accessor for host chrome metrics, mirroring the `BossTheme.colors` / `BossTheme.space` pattern
 * the design system already uses: `BossChrome.dimens.tabBarHeight`.
 */
object BossChrome {
    val dimens: ChromeDimens
        @Composable @ReadOnlyComposable
        get() = LocalChromeDimens.current
}
