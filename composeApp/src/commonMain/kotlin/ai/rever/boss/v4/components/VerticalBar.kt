package ai.rever.boss.v4.components

import BossDarkSurface
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun VerticalBar(width: Dp, content: @Composable BoxScope.() -> Unit) {
    // Title bar with BOSS centered
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(width)
            .background(BossDarkSurface)
    ) {
        content()
    }
}