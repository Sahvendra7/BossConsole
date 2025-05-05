package ai.rever.boss.v4.components.window_panel.components.main_window_panels.screens

import ai.rever.boss.v4.components.window_panel.components.main_window_panels.BossTabsComponent
import ai.rever.boss.v4.components.window_panel.components.main_window_panels.TabConfig.WebBrowserConfig
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.ComponentContext

/**
 * Detail screen UI
 */
@Composable
fun WebBrowserComponent.WebBrowserPanel() {
    Scaffold(
        topBar = {
            Text("Details", modifier = Modifier.padding(16.dp))
        }
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Web Browser: $url")

                Button(
                    onClick = { onBackClicked() },
                    modifier = Modifier.padding(8.dp)
                ) {
                    Text("Go Back")
                }
            }
        }
    }
}

/**
 * Detail screen component
 */
class WebBrowserComponent(
    componentContext: ComponentContext,
    private val navigation: BossTabsComponent,
    private val config: WebBrowserConfig,
) : ComponentContext by componentContext {

    val url get() = config.url

    fun onBackClicked() {
        navigation.closeTab(navigation.tabsState.value.activeIndex)
    }
}