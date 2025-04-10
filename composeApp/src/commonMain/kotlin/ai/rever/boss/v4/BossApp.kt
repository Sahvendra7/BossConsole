@file:OptIn(DelicateDecomposeApi::class)

package ai.rever.boss.v4

import BossTheme
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.decompose.DelicateDecomposeApi
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.decompose.extensions.compose.stack.Children
import com.arkivanov.decompose.extensions.compose.stack.animation.fade
import com.arkivanov.decompose.extensions.compose.stack.animation.plus
import com.arkivanov.decompose.extensions.compose.stack.animation.scale
import com.arkivanov.decompose.extensions.compose.stack.animation.stackAnimation

/**
 * Main UI composable that displays the root component
 */
@Composable
fun BossApp(rootComponent: RootComponent) {
    BossTheme {
        Children(
            stack = rootComponent.childStack,
            animation = stackAnimation(fade() + scale()),
        ) { child ->
            when (val instance = child.instance) {
                is RootComponent.Child.Home -> HomeScreen(instance.component)
                is RootComponent.Child.Settings -> SettingsScreen(instance.component)
                is RootComponent.Child.Detail -> DetailScreen(instance.component)
            }
        }
    }
}

/**
 * Helper function to create root component with DefaultComponentContext
 */
fun createRootComponent(): RootComponent {
    val lifecycle = LifecycleRegistry()
    return RootComponent(DefaultComponentContext(lifecycle))
}



