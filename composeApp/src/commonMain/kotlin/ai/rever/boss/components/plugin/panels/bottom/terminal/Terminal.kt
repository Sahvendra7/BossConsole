package ai.rever.boss.components.plugin.panels.bottom.terminal

import ai.rever.boss.components.model.Panel.Companion.bottom
import ai.rever.boss.components.model.Panel.Companion.left
import ai.rever.boss.components.plugin.DefaultPlugin
import ai.rever.boss.components.registery.PanelComponentWithUI
import ai.rever.boss.components.registery.PanelId
import ai.rever.boss.components.registery.PanelInfo
import ai.rever.boss.components.events.PanelEventBus
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.runtime.Composable
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.Lifecycle.Callbacks
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

object TerminalInfo : PanelInfo {
    override val id = PanelId("terminal", 13)
    override val displayName = "Terminal"
    override val icon = Icons.Outlined.Terminal
    override val defaultSlotPosition = left.bottom
}

class TerminalComponent(
    ctx: ComponentContext,
    override val panelInfo: PanelInfo
) : PanelComponentWithUI, ComponentContext by ctx {
    
    private val terminalViewModel = TerminalViewModel()
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    init {
        // Dispose the terminal when the component is destroyed
        lifecycle.subscribe(
            callbacks = object : Callbacks {
                override fun onDestroy() {
                    terminalViewModel.dispose()
                    coroutineScope.cancel()
                }
            }
        )
        
        // Monitor terminal running state using Flow - more robust approach
        coroutineScope.launch {
            var hasEverStarted = false
            
            // Monitor terminal state changes
            terminalViewModel.isRunning.collect { isRunning ->
                if (isRunning && !hasEverStarted) {
                    // Terminal started for the first time
                    hasEverStarted = true
                } else if (!isRunning && hasEverStarted) {
                    // Terminal was running but now stopped - close panel
                    PanelEventBus.closePanel(panelInfo.id)
                    return@collect // Exit the collection
                }
            }
        }
    }

    @Composable
    override fun Content() {
        TerminalView(terminalViewModel)
    }
}

fun DefaultPlugin.registerTerminal() = panelRegistry.registerPanel(TerminalInfo) {
    ctx, panelInfo -> TerminalComponent(ctx, panelInfo)
}

interface Terminal {
    val output: Flow<String>
    val isRunning: StateFlow<Boolean>
    
    suspend fun start()
    suspend fun write(input: String)
    suspend fun resize(columns: Int, rows: Int)
    fun stop()
}

expect class TerminalFactory() {
    fun createTerminal(): Terminal
}
