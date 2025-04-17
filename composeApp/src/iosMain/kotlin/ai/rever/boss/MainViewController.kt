package ai.rever.boss

import ai.rever.boss.v4.components.window_panel.components.main_window_panel.BossMainWindowPanel
import ai.rever.boss.v4.components.window_panel.components.main_window_panel.createBossAppComponent
import androidx.compose.ui.window.ComposeUIViewController

fun MainViewControllerV4() = ComposeUIViewController {
    // Create root component with iOS lifecycle
    val rootComponent = createBossAppComponent()

    // Display the app
    BossMainWindowPanel(rootComponent)
}