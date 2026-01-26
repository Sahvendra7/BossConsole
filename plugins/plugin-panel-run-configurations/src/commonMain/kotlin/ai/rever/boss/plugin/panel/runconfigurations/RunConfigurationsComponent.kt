package ai.rever.boss.plugin.panel.runconfigurations

import ai.rever.boss.plugin.api.PanelComponentWithUI
import ai.rever.boss.plugin.api.PanelInfo
import ai.rever.boss.plugin.api.RunConfigurationDataProvider
import androidx.compose.runtime.Composable
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.Lifecycle

/**
 * Component for the Run Configurations panel.
 *
 * Wraps the view model and view, handling lifecycle management.
 */
class RunConfigurationsComponent(
    ctx: ComponentContext,
    override val panelInfo: PanelInfo,
    dataProvider: RunConfigurationDataProvider,
    private val windowContextProvider: WindowContextProviderForPlugin
) : PanelComponentWithUI, ComponentContext by ctx {

    private val viewModel = RunConfigurationsViewModel(dataProvider)

    init {
        lifecycle.subscribe(object : Lifecycle.Callbacks {
            override fun onDestroy() {
                viewModel.dispose()
            }
        })
    }

    @Composable
    override fun Content() {
        RunConfigurationsView(
            viewModel = viewModel,
            projectPath = windowContextProvider.getProjectPath(),
            windowId = windowContextProvider.getWindowId()
        )
    }
}
