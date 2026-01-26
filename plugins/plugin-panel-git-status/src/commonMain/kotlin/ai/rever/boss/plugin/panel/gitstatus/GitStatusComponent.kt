package ai.rever.boss.plugin.panel.gitstatus

import ai.rever.boss.plugin.api.GitDataProvider
import ai.rever.boss.plugin.api.PanelComponentWithUI
import ai.rever.boss.plugin.api.PanelInfo
import androidx.compose.runtime.Composable
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.Lifecycle

/**
 * Component for the Git Status panel.
 */
class GitStatusComponent(
    ctx: ComponentContext,
    override val panelInfo: PanelInfo,
    dataProvider: GitDataProvider,
    private val windowIdProvider: () -> String?
) : PanelComponentWithUI, ComponentContext by ctx {

    private val viewModel = GitStatusViewModel(dataProvider)

    init {
        lifecycle.subscribe(object : Lifecycle.Callbacks {
            override fun onCreate() {
                viewModel.refreshStatus()
            }
            override fun onDestroy() {
                viewModel.dispose()
            }
        })
    }

    @Composable
    override fun Content() {
        GitStatusView(
            viewModel = viewModel,
            windowId = windowIdProvider()
        )
    }
}
