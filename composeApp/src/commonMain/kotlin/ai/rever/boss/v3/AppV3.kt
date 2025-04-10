package ai.rever.boss.v3

import BossTheme
import ai.rever.boss.v3.bossConsole.BossConsole
import androidx.compose.runtime.Composable
import moe.tlaster.precompose.PreComposeApp

@Composable
fun AppV3() {
    PreComposeApp {
        BossTheme {
            BossConsole()
        }
    }
}