package ai.rever.boss.plugin.panel.downloads

import ai.rever.boss.plugin.api.DownloadDataProvider
import ai.rever.boss.plugin.api.PanelComponentWithUI
import ai.rever.boss.plugin.api.PanelInfo
import androidx.compose.runtime.Composable
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.Lifecycle

/**
 * Component for the Downloads panel.
 */
class DownloadsComponent(
    ctx: ComponentContext,
    override val panelInfo: PanelInfo,
    dataProvider: DownloadDataProvider
) : PanelComponentWithUI, ComponentContext by ctx {

    private val viewModel = DownloadsViewModel(dataProvider)

    init {
        lifecycle.subscribe(object : Lifecycle.Callbacks {
            override fun onDestroy() {
                viewModel.dispose()
            }
        })
    }

    @Composable
    override fun Content() {
        DownloadsView(viewModel = viewModel)
    }
}
