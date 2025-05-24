package ai.rever.boss.components.plugin.panels.bottom.terminal

import ai.rever.boss.components.model.Panel.Companion.bottom
import ai.rever.boss.components.model.Panel.Companion.left
import ai.rever.boss.components.plugin.DefaultPlugin
import ai.rever.boss.components.registery.PanelComponentWithUI
import ai.rever.boss.components.registery.PanelId
import ai.rever.boss.components.registery.PanelInfo
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.runtime.Composable
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.Lifecycle.Callbacks
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

object TerminalInfo : PanelInfo {
    override val id = PanelId("terminal", 11)
    override val displayName = "Terminal"
    override val icon = Icons.Outlined.Terminal
    override val defaultSlotPosition = left.bottom
}

class TerminalComponent(
    ctx: ComponentContext,
    override val panelInfo: PanelInfo
) : PanelComponentWithUI, ComponentContext by ctx {
    
    private val terminalViewModel = TerminalViewModel()
    
    init {
        // Dispose the terminal when the component is destroyed
        lifecycle.subscribe(
            callbacks = object : Callbacks {
                override fun onDestroy() {
                    terminalViewModel.dispose()
                }
            }
        )
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