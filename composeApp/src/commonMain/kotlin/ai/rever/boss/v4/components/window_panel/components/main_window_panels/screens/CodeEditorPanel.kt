package ai.rever.boss.v4.components.window_panel.components.main_window_panels.screens

import ai.rever.boss.v4.components.window_panel.components.main_window_panels.BossTabsComponent
import ai.rever.boss.v4.components.window_panel.components.main_window_panels.TabConfig
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.ComponentContext

/**
 * Code screen UI
 */
@Composable
fun CodeEditorPanel(component: CodeEditorComponent) {
    Scaffold(
        topBar = {
            Text("Home", modifier = Modifier.padding(16.dp))
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Code Editor")
            }
        }
    }
}

/**
 * Home screen component
 */
class CodeEditorComponent(
    componentContext: ComponentContext,
    private val navigation: BossTabsComponent,
    private val config: TabConfig.CodeEditorConfig
) : ComponentContext by componentContext