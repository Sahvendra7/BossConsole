package ai.rever.boss.v4.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.twotone.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun BossTopBar() {
    HorizontalBar(50.dp) {
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = 16.dp)
                .align(Alignment.CenterStart),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BossActionButton(text = "File", onClick = {})
            BossActionButton(text = "Edit", onClick = {})
            BossActionButton(text = "View", onClick = {})
            BossActionButton(text = "Navigate", onClick = {})
            BossActionButton(text = "Code", onClick = {})

            Spacer(modifier = Modifier.weight(1f))

            BossActionButton(imageVector = Icons.AutoMirrored.TwoTone.Logout, text = "Sign Out", onClick = {})
        }
    }
}