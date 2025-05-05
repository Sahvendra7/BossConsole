package ai.rever.boss

import ai.rever.boss.v4.BossApp
import ai.rever.boss.v4.components.window_panel.components.main_window_panels.createBossAppComponent
import androidx.compose.ui.window.ComposeUIViewController

fun MainViewControllerV4() = ComposeUIViewController {
    // Create root component with iOS lifecycle
    with(createBossAppComponent()) {
        // Display the app
        BossApp()
    }

}