package ai.rever.boss.ui.lighthouse

import ai.rever.boss.ui.Screen
import ai.rever.boss.ui.common.BossCard
import ai.rever.boss.ui.common.BossColumn
import ai.rever.boss.ui.common.BossRow
import ai.rever.boss.ui.common.BossHeader
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun LighthouseScreen(onScreenChange: (Screen) -> Unit) {
    BossColumn {
        BossRow {
            BossCard(
                title = "Worklist",
                description = "Worklist environment and integrations",
                imageVector = Icons.Default.Work,
                modifier = Modifier.weight(1f),
                onClick = { onScreenChange(Screen.Worklist) }
            )
            BossCard(
                title = "System of Records",
                description = "System of Records environment and integrations",
                imageVector = Icons.Default.DatasetLinked,
                modifier = Modifier.weight(1f),
                onClick = { onScreenChange(Screen.SystemOfRecords) }
            )
        }
    }
}