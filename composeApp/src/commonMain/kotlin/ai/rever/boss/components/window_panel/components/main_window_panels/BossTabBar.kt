package ai.rever.boss.components.window_panel.components.main_window_panels

import BossDarkTextSecondary
import ai.rever.boss.components.bars.ScrollbarConfig
import ai.rever.boss.components.bars.horizontalScrollWithScrollbar
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Horizontal scrollable tab bar for the left section of the main tab bar.
 *
 * Displays tabs in a horizontally scrollable row with a custom scrollbar indicator.
 * The scrollbar appears at the top of the content when scrolling is available.
 *
 * @param content Composable content to display in the tab bar (typically tab buttons)
 */
@Composable
fun RowScope.BossLeftTabBar(content: @Composable RowScope.() -> Unit) {
    Column(modifier = Modifier.weight(2f).padding(horizontal = 8.dp)) {
        Row(
            modifier = Modifier
                .horizontalScrollWithScrollbar(
                    rememberScrollState(),
                    scrollbarConfig = ScrollbarConfig(
                        indicatorThickness = 2.dp,
                        indicatorColor = BossDarkTextSecondary,
                        indicatorCornerRadius = 4.dp,
                        horizontalScrollbarAtTop = true
                    )
                )
            ,
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}
