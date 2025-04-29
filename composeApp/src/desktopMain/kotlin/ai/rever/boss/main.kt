package ai.rever.boss

import BossDarkSurface
import ai.rever.boss.v4.BossApp
import ai.rever.boss.v4.components.window_panel.components.main_window_panels.createBossAppComponent
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import java.awt.Color

fun main() = application {
    val bossAppComponent = createBossAppComponent()
    val windowState = rememberWindowState(
        size = DpSize(1280.dp, 800.dp) // Set larger initial window size
    )
    
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