package ai.rever.boss.v2.ui

import ai.rever.boss.v2.ui.common.BossCard
import ai.rever.boss.v2.ui.common.BossColumn
import ai.rever.boss.v2.ui.common.BossRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Workspaces
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier


@Composable
fun BossScreen(onScreenChange: (Screen) -> Unit) {
    BossColumn  {
        BossRow {
            // Lighthouse Card
            BossCard(
                title = "Lighthouse",
                description = "Customer environment and integrations",
                imageVector = Icons.Default.CellTower,
                modifier = Modifier.weight(1f),
                onClick = { onScreenChange(Screen.Lighthouse) }
            )
            
            // Lanager Card
            BossCard(
                title = "Lanager",
                description = "Lanager environment and integrations",
                imageVector = Icons.Default.Workspaces,
                modifier = Modifier.weight(1f),
                onClick = { onScreenChange(Screen.Lanager) }
            )
        }
    }
}

