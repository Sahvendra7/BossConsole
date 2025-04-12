package ai.rever.boss.v4.components

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material.Divider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun VDivider() {
    Divider(
        modifier = Modifier
            .fillMaxHeight()
            .width(1.dp),
    )
}
