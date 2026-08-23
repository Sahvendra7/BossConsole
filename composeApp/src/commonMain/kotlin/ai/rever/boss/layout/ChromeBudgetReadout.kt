package ai.rever.boss.layout

import ai.rever.boss.focusmode.FocusModeSettings
import ai.rever.boss.utils.logging.BossLogger
import ai.rever.boss.utils.logging.LogCategory
import ai.rever.boss.window.WindowAppearanceSettings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp

private val logger = BossLogger.forComponent("ChromeBudgetReadout")

/** Set `BOSS_CHROME_BUDGET=1` to have each window report its chrome budget as it changes. */
private const val ENV_FLAG = "BOSS_CHROME_BUDGET"

/**
 * Read once, not per composition: the flag is a developer's launch decision, and re-reading the
 * environment on every recomposition of every window would be pure waste.
 */
private val readoutEnabled: Boolean by lazy {
    System.getenv(ENV_FLAG)?.lowercase() in setOf("1", "true", "yes")
}

/**
 * Reports what this window's chrome costs the page, for the developer measuring it.
 *
 * Renders nothing. Off unless `BOSS_CHROME_BUDGET=1`, so it costs a disabled `LaunchedEffect` and
 * nothing else on a normal launch.
 *
 * This is a log line rather than a status-bar item on purpose: the configuration most worth
 * measuring is the one with the bottom bar switched off, and a readout that lives in the bottom bar
 * cannot report that. A visible readout belongs next to the density control in Settings, which is
 * reachable whatever the chrome is doing — that arrives with the density setting itself.
 */
@Composable
fun ChromeBudgetReadout(
    appearance: WindowAppearanceSettings,
    focusMode: FocusModeSettings,
) {
    if (!readoutEnabled) return

    val dimens = BossChrome.dimens
    val budget = ChromeMetrics.mainPanelBudget(appearance, focusMode, dimens)

    // containerSize is the window's content area in px; on macOS that includes the area the traffic
    // lights are drawn over, which is exactly the space the title row is reserving.
    val containerSize = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current
    val windowHeight = with(density) { containerSize.height.toDp() }
    val windowWidth = with(density) { containerSize.width.toDp() }

    LaunchedEffect(budget, windowHeight, windowWidth) {
        if (windowHeight <= 0.dp || windowWidth <= 0.dp) return@LaunchedEffect

        val heightPercent = budget.verticalFractionOf(windowHeight) * 100
        val widthPercent = budget.horizontalFractionOf(windowWidth) * 100
        logger.info(
            LogCategory.UI,
            "chrome budget: page gets ${heightPercent.format1()}% of height, ${widthPercent.format1()}% of width",
            mapOf(
                "windowDp" to "${windowWidth.value.toInt()}x${windowHeight.value.toInt()}",
                "chromeVerticalDp" to budget.vertical.value.toInt(),
                "chromeHorizontalDp" to budget.horizontal.value.toInt(),
            ),
        )
    }
}

/** One decimal place, without pulling in a platform formatter this module would have to expect/actual. */
private fun Float.format1(): String {
    val scaled = (this * 10).toInt()
    return "${scaled / 10}.${scaled % 10}"
}
