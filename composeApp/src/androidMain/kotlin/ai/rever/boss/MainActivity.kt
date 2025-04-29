package ai.rever.boss

import ai.rever.boss.v3.AppV3
import ai.rever.boss.v4.BossApp
import ai.rever.boss.v4.components.window_panel.components.main_window_panels.BossConsoleComponent
import ai.rever.boss.v4.components.window_panel.components.main_window_panels.createBossAppComponent
import android.content.res.Configuration.SCREENLAYOUT_SIZE_XLARGE
import android.content.res.Configuration.UI_MODE_NIGHT_YES
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

        // Create root component with the activity's component context
        val bossConsoleComponent = BossConsoleComponent(defaultComponentContext())

        setContent {
            BossApp(bossConsoleComponent)
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
    BossApp(createBossAppComponent())
}