package ai.rever.boss.components.bars.horizontal

import BossDarkTextSecondary
import BossDarkBorder
import ai.rever.boss.components.buttons.BossActionButton
import ai.rever.boss.components.bars.ScrollbarConfig
import ai.rever.boss.components.bars.horizontalScrollWithScrollbar
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Info
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp


@Composable
fun BossBottomBar() {
    Divider(color = BossDarkBorder)
    HorizontalBar(height = 30.dp) {
        HorizontalBarRow {
            BossLeftBottomBar()
            Spacer(modifier = Modifier.weight(0.1f))
            BossRightBottomBar()
        }
    }
}

@Composable
fun RightArrow() {
    Icon(imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
        modifier = Modifier.size(18.dp),
        contentDescription = "Right Arrow",
        tint = BossDarkTextSecondary)
}

@Composable
fun RowScope.BossLeftBottomBar() {
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
            verticalAlignment = Alignment.CenterVertically
        ) {
            BossActionButton(text = "Lanager", color = BossDarkTextSecondary, onClick = {})
            RightArrow()
            BossActionButton(text = "Mastery", color = BossDarkTextSecondary, onClick = {})
            RightArrow()
            BossActionButton(text = "Task Resolver", color = BossDarkTextSecondary, onClick = {})
        }
    }
}

@Composable
fun BossRightBottomBar() {
    BossActionButton(text = "UTF-8", color = BossDarkTextSecondary, onClick = {})
    BossActionButton(imageVector = Icons.Outlined.Info, text = "Info", color = BossDarkTextSecondary, onClick = {})
}
