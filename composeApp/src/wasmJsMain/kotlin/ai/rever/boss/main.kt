package ai.rever.boss

import ai.rever.boss.v4.BossApp
import ai.rever.boss.v4.components.window_panel.components.main_window_panels.BossMainPanel
import ai.rever.boss.v4.components.window_panel.components.main_window_panels.createBossAppComponent
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    document.body?.let { body ->
        ComposeViewport(body) {
            // Create root component with iOS lifecycle
            with(createBossAppComponent()) {
                // Display the app
                BossApp()
            }
        }
    }
}