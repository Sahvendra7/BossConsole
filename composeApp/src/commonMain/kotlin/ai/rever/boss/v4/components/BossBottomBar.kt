package ai.rever.boss.v4.components

import BossDarkTextSecondary
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Info
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun BossBottomBar() {
    HorizontalBar(36.dp) {
        HorizontalBarRow {
            LeftBottomBar()
            Spacer(modifier = Modifier.weight(1f))
            RightBottomBar()
        }
    }
}

@Composable
fun RightArrow() {
    Icon(imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
        contentDescription = "Right Arrow",
        tint = BossDarkTextSecondary)
}

@Composable
fun LeftBottomBar() {
    BossActionButton(text = "Lanager", color = BossDarkTextSecondary, onClick = {})
    RightArrow()
    BossActionButton(text = "Mastery", color = BossDarkTextSecondary, onClick = {})
    RightArrow()
    BossActionButton(text = "Task Resolver", color = BossDarkTextSecondary, onClick = {})
}

@Composable
fun RightBottomBar() {
    BossActionButton(text = "UTF-8", color = BossDarkTextSecondary, onClick = {})
    BossActionButton(imageVector = Icons.Outlined.Info, text = "Info", color = BossDarkTextSecondary, onClick = {})
}
