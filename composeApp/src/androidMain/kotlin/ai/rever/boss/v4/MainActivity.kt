package ai.rever.boss.v4

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.arkivanov.decompose.defaultComponentContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Create root component with the activity's component context
        val bossAppComponent = BossAppComponent(defaultComponentContext())
        
        setContent {
            BossApp(bossAppComponent)
        }
    }
} 