package ai.rever.boss

import ai.rever.boss.v3.AppV3
import ai.rever.boss.v4.BossApp
import ai.rever.boss.v4.components.window_panel.components.main_window_panels.BossConsoleComponent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
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

@Preview
@Composable
fun AppAndroidPreview() {
    AppV3()
}