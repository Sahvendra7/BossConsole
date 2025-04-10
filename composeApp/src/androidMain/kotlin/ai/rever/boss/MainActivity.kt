package ai.rever.boss

import ai.rever.boss.v3.AppV3
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AppV3()
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    AppV3()
}