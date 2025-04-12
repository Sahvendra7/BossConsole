package ai.rever.boss.v4.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun BossTopBar() {
    HorizontalBar(40.dp) {
        HorizontalBarRow {
            BossTopLeftBar()
            Spacer(modifier = Modifier.weight(1f))
            BossTopRightBar()
        }
    }
}

@Composable
fun BossTopLeftBar() {
    BossActionButton(text = "File", onClick = {})
    BossActionButton(text = "Edit", onClick = {})
    BossActionButton(text = "View", onClick = {})
    BossActionButton(text = "Navigate", onClick = {})
    BossActionButton(text = "Code", onClick = {})
}

@Composable
fun BossTopRightBar() {
    BossActionButton(imageVector = Icons.Outlined.PersonAdd, text = "Sign Out", onClick = {})
    BossActionButton(imageVector = Icons.Outlined.Search, text = "Search", onClick = {})
    BossActionButton(imageVector = Icons.Outlined.Settings, text = "Settings", onClick = {})
}