package ai.rever.boss

import ai.rever.boss.v1.AppV1
import ai.rever.boss.v2.ui.AppV2
import ai.rever.boss.v3.BossApp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import moe.tlaster.precompose.ProvidePreComposeLocals

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "BOSS-Kotlin",
    ) {
        ProvidePreComposeLocals {
            BossApp()
        }
    }
}