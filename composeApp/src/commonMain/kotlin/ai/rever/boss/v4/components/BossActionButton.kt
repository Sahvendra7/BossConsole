package ai.rever.boss.v4.components

import BossDarkTextPrimary
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp

@Composable
fun BossActionButton(imageVector: ImageVector? = null, text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        if (imageVector != null) {
            Icon(
                imageVector = imageVector,
                contentDescription = text,
                tint = BossDarkTextPrimary
            )
        } else {
            Text(
                text = text,
                color = BossDarkTextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Normal,
                textAlign = TextAlign.Center,
            )
        }
    }

}