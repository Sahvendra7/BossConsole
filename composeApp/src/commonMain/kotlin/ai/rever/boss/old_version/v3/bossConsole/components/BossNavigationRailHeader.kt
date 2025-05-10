package ai.rever.boss.old_version.v3.bossConsole.components

import BossDarkBorder
import androidx.compose.foundation.layout.*
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun BossNavigationRailHeader() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Terminal,
                contentDescription = "Boss Logo",
            )

            Spacer(modifier = Modifier.width(8.dp))
            // App name
            Text(
                "BOSS console",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
    Divider(color = BossDarkBorder)
}

