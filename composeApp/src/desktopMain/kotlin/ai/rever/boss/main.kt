package ai.rever.boss

import ai.rever.boss.v4.BossApp
import ai.rever.boss.v4.createRootComponent
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    // Create root component
    val rootComponent = createRootComponent()

    Window(
        onCloseRequest = ::exitApplication,
        title = "BOSS-Kotlin",
    ) {
        BossApp(rootComponent)
    }
}