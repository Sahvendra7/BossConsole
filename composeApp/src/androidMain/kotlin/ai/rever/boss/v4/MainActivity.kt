package ai.rever.boss.v4

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.arkivanov.decompose.defaultComponentContext
import ai.rever.boss.v4.BossApp
import ai.rever.boss.v4.RootComponent

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Create root component with the activity's component context
        val rootComponent = RootComponent(defaultComponentContext())
        
        setContent {
            BossApp(rootComponent)
        }
    }
} 