@file:OptIn(DelicateDecomposeApi::class)

package ai.rever.boss.v4

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.decompose.DelicateDecomposeApi
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.router.stack.pop
import com.arkivanov.decompose.router.stack.push
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.decompose.extensions.compose.stack.Children
import com.arkivanov.decompose.extensions.compose.stack.animation.fade
import com.arkivanov.decompose.extensions.compose.stack.animation.plus
import com.arkivanov.decompose.extensions.compose.stack.animation.scale
import com.arkivanov.decompose.extensions.compose.stack.animation.stackAnimation
import kotlinx.serialization.Serializable

/**
 * Root component for the BOSS app using Decompose for navigation
 */
class RootComponent(
    componentContext: ComponentContext
) : ComponentContext by componentContext {
    
    // Navigation controller
    private val navigation = StackNavigation<Config>()
    
    // Stack state for child components
    val childStack: Value<ChildStack<Config, Child>> = childStack(
        source = navigation,
        serializer = Config.serializer(),
        initialConfiguration = Config.Home,
        handleBackButton = true,
        childFactory = ::createChild
    )
    
    // Configuration classes for different screens
    @Serializable
    sealed class Config {
        @Serializable
        data object Home : Config()
        
        @Serializable
        data object Settings : Config()
        
        @Serializable
        data class Detail(val id: String) : Config()
    }
    
    // Child classes for different screens
    sealed class Child {
        data class Home(val component: HomeComponent) : Child()
        data class Settings(val component: SettingsComponent) : Child()
        data class Detail(val component: DetailComponent) : Child()
    }
    
    // Factory method to create children based on configuration
    private fun createChild(config: Config, componentContext: ComponentContext): Child =
        when (config) {
            is Config.Home -> Child.Home(HomeComponent(componentContext, navigation))
            is Config.Settings -> Child.Settings(SettingsComponent(componentContext, navigation))
            is Config.Detail -> Child.Detail(DetailComponent(componentContext, navigation, config.id))
        }
}

/**
 * Home screen component
 */
class HomeComponent(
    componentContext: ComponentContext,
    private val navigation: StackNavigation<RootComponent.Config>
) : ComponentContext by componentContext {
    
    fun onSettingsClicked() {
        navigation.push(RootComponent.Config.Settings)
    }
    
    fun onDetailClicked(id: String) {
        navigation.push(RootComponent.Config.Detail(id))
    }
}

/**
 * Settings screen component
 */
class SettingsComponent(
    componentContext: ComponentContext,
    private val navigation: StackNavigation<RootComponent.Config>
) : ComponentContext by componentContext {
    
    fun onBackClicked() {
        navigation.pop()
    }
}

/**
 * Detail screen component
 */
class DetailComponent(
    componentContext: ComponentContext,
    private val navigation: StackNavigation<RootComponent.Config>,
    val id: String
) : ComponentContext by componentContext {
    
    fun onBackClicked() {
        navigation.pop()
    }
}

/**
 * Main UI composable that displays the root component
 */
@Composable
fun BossApp(rootComponent: RootComponent) {
    MaterialTheme {
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

/**
 * Home screen UI
 */
@Composable
private fun HomeScreen(component: HomeComponent) {
    Scaffold(
        topBar = { 
            Text("Home", modifier = Modifier.padding(16.dp)) 
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Welcome to BOSS v4")
            
            Button(
                onClick = { component.onSettingsClicked() },
                modifier = Modifier.padding(8.dp)
            ) {
                Text("Go to Settings")
            }
            
            Button(
                onClick = { component.onDetailClicked("sample-id") },
                modifier = Modifier.padding(8.dp)
            ) {
                Text("Go to Details")
            }
        }
    }
}

/**
 * Settings screen UI
 */
@Composable
private fun SettingsScreen(component: SettingsComponent) {
    Scaffold(
        topBar = { 
            Text("Settings", modifier = Modifier.padding(16.dp)) 
        }
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Settings Screen")
                
                Button(
                    onClick = { component.onBackClicked() },
                    modifier = Modifier.padding(8.dp)
                ) {
                    Text("Go Back")
                }
            }
        }
    }
}

/**
 * Detail screen UI
 */
@Composable
private fun DetailScreen(component: DetailComponent) {
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
                Text("Detail Screen for ID: ${component.id}")
                
                Button(
                    onClick = { component.onBackClicked() },
                    modifier = Modifier.padding(8.dp)
                ) {
                    Text("Go Back")
                }
            }
        }
    }
}

