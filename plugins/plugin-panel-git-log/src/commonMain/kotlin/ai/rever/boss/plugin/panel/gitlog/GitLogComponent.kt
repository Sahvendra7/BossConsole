package ai.rever.boss.plugin.panel.gitlog

import ai.rever.boss.plugin.api.GitDataProvider
import ai.rever.boss.plugin.api.PanelComponentWithUI
import ai.rever.boss.plugin.api.PanelInfo
import androidx.compose.runtime.Composable
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.Lifecycle

/**
 * Component for the Git Log panel.
 */
class GitLogComponent(
    ctx: ComponentContext,
    override val panelInfo: PanelInfo,
    dataProvider: GitDataProvider
) : PanelComponentWithUI, ComponentContext by ctx {

    private val viewModel = GitLogViewModel(dataProvider)

    init {
        lifecycle.subscribe(object : Lifecycle.Callbacks {
            override fun onCreate() {
                viewModel.refreshLog()
            }
            override fun onDestroy() {
                viewModel.dispose()
            }
        })
    }

    @Composable
    override fun Content() {
        GitLogView(viewModel = viewModel)
    }
}
