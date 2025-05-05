package ai.rever.boss

import ai.rever.boss.v4.BossApp
import ai.rever.boss.v4.components.window_panel.components.main_window_panels.BossTabsComponent
import ai.rever.boss.v4.components.window_panel.components.main_window_panels.createBossAppComponent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.Devices
import com.arkivanov.decompose.defaultComponentContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        with(BossTabsComponent(defaultComponentContext())) {
            setContent {
                BossApp()
            }
        }

    }
}

@Preview(
//    showSystemUi = true,
    device = Devices.AUTOMOTIVE_1024p,
    widthDp = 1280,
    heightDp = 640
)
@Composable
fun AppAndroidPreview() {
    with(createBossAppComponent()) {
        BossApp()
    }
}