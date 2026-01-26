package ai.rever.boss.plugin.panel.secretmanager

import ai.rever.boss.plugin.api.PanelComponentWithUI
import ai.rever.boss.plugin.api.PanelInfo
import ai.rever.boss.plugin.api.SecretDataProvider
import ai.rever.boss.plugin.api.UserManagementProvider
import androidx.compose.runtime.Composable
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.doOnDestroy

/**
 * Component for Secret Manager panel
 *
 * This component provides the UI for managing encrypted website credentials.
 * It uses provider interfaces for data operations, allowing it to be in a
 * separate plugin module.
 *
 * Lifecycle management:
 * - ViewModel is disposed when component is destroyed to prevent memory leaks
 */
class SecretManagerComponent(
    ctx: ComponentContext,
    override val panelInfo: PanelInfo,
    secretDataProvider: SecretDataProvider,
    userManagementProvider: UserManagementProvider,
    onSecretChanged: (() -> Unit)? = null
) : PanelComponentWithUI, ComponentContext by ctx {

    private val viewModel = SecretManagerViewModel(
        secretDataProvider = secretDataProvider,
        userManagementProvider = userManagementProvider,
        onSecretChanged = onSecretChanged
    )

    init {
        lifecycle.doOnDestroy {
            viewModel.dispose()
        }
    }

    override fun onInitialized() {
        // Panel initialized
    }

    override fun onBeforeReset() {
        // Preparing to reset panel
    }

    @Composable
    override fun Content() {
        SecretManagerView(viewModel)
    }
}
