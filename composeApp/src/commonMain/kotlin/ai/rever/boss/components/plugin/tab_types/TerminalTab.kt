package ai.rever.boss.components.plugin.tab_types

import ai.rever.boss.components.plugin.DefaultPlugin
import ai.rever.boss.components.plugin.panels.bottom.terminal.TerminalView
import ai.rever.boss.components.plugin.panels.bottom.terminal.TerminalViewModel
import ai.rever.boss.components.registery.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.Lifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

object TerminalTab : TabTypeInfo {
    override val typeId = TabTypeId("terminal")
    override val icon = Icons.Outlined.Terminal
    override val displayName = "Terminal"
}

data class TerminalTabInfo(
    override val id: String,
    override val typeId: TabTypeId,
    override val title: String = "Terminal",
    override val icon: androidx.compose.ui.graphics.vector.ImageVector = TerminalTab.icon,
    override val tabIcon: TabIcon = TabIcon.Vector(icon)
) : TabInfo

class TerminalTabComponent(
    override val config: TabInfo,
    private val componentContext: ComponentContext,
    private val onClose: () -> Unit
) : TabComponentWithUI, ComponentContext by componentContext {
    
    override val tabTypeInfo = TerminalTab
    private val terminalViewModel = TerminalViewModel()
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    init {
        // Dispose terminal when component is destroyed
        lifecycle.subscribe(
            callbacks = object : Lifecycle.Callbacks {
                override fun onDestroy() {
                    terminalViewModel.dispose()
                    coroutineScope.cancel()
                }
            }
        )
        
        // Monitor terminal running state
        coroutineScope.launch {
            // Wait for terminal to fully initialize
            
            // First wait for terminal to start
            var hasStarted = false
            while (!hasStarted && isActive) {
                if (terminalViewModel.wasStarted) {
                    hasStarted = true
                } else {
                    delay(100)
                }
            }
            
            // Now monitor for when it stops
            terminalViewModel.isRunning.collect { isRunning ->
                if (hasStarted && !isRunning) {
                    // Terminal was running but now stopped
                    // Give a small delay to ensure clean shutdown
                    delay(500)
                    onClose()
                }
            }
        }
    }
    
    @Composable
    override fun Content() {
        TerminalView(terminalViewModel)
        
        // Ensure terminal is started
        LaunchedEffect(Unit) {
            terminalViewModel.ensureStarted()
        }
    }
}

fun DefaultPlugin.registerTerminalTab() = tabRegistry.registerTabType(TerminalTab) { config, context ->
    // Get the parent tab component through the component tree
    var parentTabsComponent: ai.rever.boss.components.window_panel.components.main_window_panels.BossTabsComponent? = null
    
    // The context passed here is the BossTabsComponent itself
    if (context is ai.rever.boss.components.window_panel.components.main_window_panels.BossTabsComponent) {
        parentTabsComponent = context
    }
    
    TerminalTabComponent(
        config = config,
        componentContext = context,
        onClose = {
            // Find and remove this tab
            parentTabsComponent?.let { tabs ->
                val index = tabs.tabsState.value.tabs.indexOfFirst { it.id == config.id }
                if (index >= 0) {
                    tabs.removeTab(index)
                }
            }
        }
    )
} 