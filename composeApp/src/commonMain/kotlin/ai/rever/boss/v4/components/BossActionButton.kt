package ai.rever.boss.v4.components

import BossDarkTextPrimary
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun BossActionButton(imageVector: ImageVector? = null,
                     text: String,
                     color: Color = BossDarkTextPrimary,
                     modifier: Modifier = Modifier,
                     onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = 16.dp, minWidth = 16.dp),
    ) {
        if (imageVector != null) {
            Icon(
                imageVector = imageVector,
                contentDescription = text,
                tint = color
            )
        } else {
            Text(
                text = text,
                color = color,
                fontSize = 13.sp,
                fontWeight = FontWeight.Normal,
                textAlign = TextAlign.Center,
            )
        }
    }

}