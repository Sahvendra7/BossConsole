package ai.rever.boss

import BossDarkSurface
import ai.rever.boss.v4.BossApp
import ai.rever.boss.v4.createBossAppComponent
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.awt.Color

fun main() = application {
    val bossAppComponent = createBossAppComponent()
    val windowState = rememberWindowState()
    
    Window(
        onCloseRequest = ::exitApplication,
        title = "BOSS-Kotlin",
        state = windowState
    ) {
        window.background = Color(BossDarkSurface.value.toInt())
        window.rootPane.putClientProperty("apple.awt.fullWindowContent", true)
        window.rootPane.putClientProperty("apple.awt.transparentTitleBar", true)
        window.rootPane.putClientProperty("apple.awt.windowTitleVisible", false)

        BossApp(bossAppComponent)
    }
}