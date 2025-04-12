package ai.rever.boss.v4.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.twotone.Description
import androidx.compose.material.icons.twotone.Folder
import androidx.compose.material.icons.twotone.ModeEditOutline
import androidx.compose.material.icons.twotone.Search
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun BossTopBar() {
    HorizontalBar(36.dp) {
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = 16.dp)
                .align(Alignment.CenterStart),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BossActionButton(imageVector = Icons.TwoTone.Description, text = "File", onClick = {})
            BossActionButton(imageVector = Icons.TwoTone.ModeEditOutline, text = "Edit", onClick = {})
            BossActionButton(imageVector = Icons.TwoTone.Search, text = "View", onClick = {})
            BossActionButton(text = "Navigate", onClick = {})
            BossActionButton(text = "Code", onClick = {})

            Spacer(modifier = Modifier.weight(1f))

            BossActionButton(text = "Code", onClick = {})
        }
    }
}