package ai.rever.boss.v4.components.bars.horizontal

import BossDarkBorder
import BossDarkTextPrimary
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Divider
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun BossTitleBar(title: String = "Boss Console", height: Dp = 26.dp) {
    HorizontalBar(height = height) {
        Text(
            text = title,
            color = BossDarkTextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center)
        )
    }
    Divider(color = BossDarkBorder)
}
