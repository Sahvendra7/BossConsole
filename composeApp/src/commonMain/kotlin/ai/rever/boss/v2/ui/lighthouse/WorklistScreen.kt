package ai.rever.boss.v2.ui.lighthouse

import ai.rever.boss.v2.ui.Screen
import ai.rever.boss.v2.ui.common.BossColumn
import ai.rever.boss.v2.ui.common.BossItem
import ai.rever.boss.works
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun WorklistScreen(onScreenChange: (Screen) -> Unit) {
    BossColumn {
        LazyColumn {
            items(works) { work ->
                BossItem(
                    mainText = work.longDescription,
                    secondaryText = "Status: ${work.status}",
                    tertiaryText = "Created: ${work.createdAt}",
                    onClick = {  }
                )
            }
        }
    }
}

@Composable
fun WorklistActions(onScreenChange: (Screen) -> Unit) {
    Text(
        text = "Add worklist source",
        modifier = Modifier.clickable {
            onScreenChange(Screen.Worklist)
        }
    )
}