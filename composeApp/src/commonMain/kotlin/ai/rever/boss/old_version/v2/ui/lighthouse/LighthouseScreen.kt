package ai.rever.boss.old_version.v2.ui.lighthouse

import ai.rever.boss.old_version.v2.ui.Screen
import ai.rever.boss.old_version.v2.ui.common.BossCard
import ai.rever.boss.old_version.v2.ui.common.BossColumn
import ai.rever.boss.old_version.v2.ui.common.BossRow
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