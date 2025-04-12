package ai.rever.boss

import ai.rever.boss.v4.BossConsoleApp
import ai.rever.boss.v4.createBossAppComponent
import androidx.compose.ui.window.ComposeUIViewController

fun MainViewControllerV4() = ComposeUIViewController {
    // Create root component with iOS lifecycle
    val rootComponent = createBossAppComponent()

    // Display the app
    BossConsoleApp(rootComponent)
}