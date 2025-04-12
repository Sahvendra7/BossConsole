package ai.rever.boss.v4.components

import BossDarkTextPrimary
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun BossActionButton(imageVector: ImageVector? = null, text: String, onClick: () -> Unit) {
    if (imageVector != null) {
        Icon(
            imageVector = imageVector,
            contentDescription = text,
            tint = BossDarkTextPrimary,
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = 8.dp, vertical = 4.dp)
        )
    } else {
        Text(
            text = text,
            color = BossDarkTextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Normal,
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}