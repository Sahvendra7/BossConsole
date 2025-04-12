package ai.rever.boss.v4.components

import BossDarkSurface
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

@Composable
fun HorizontalBar(height: Dp, content: @Composable BoxScope.() -> Unit) {
    // Title bar with BOSS centered
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .background(BossDarkSurface)
    ) {
        content()
    }
}
