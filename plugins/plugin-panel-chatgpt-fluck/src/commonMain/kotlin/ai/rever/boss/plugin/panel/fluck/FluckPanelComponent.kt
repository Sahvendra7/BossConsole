package ai.rever.boss.plugin.panel.fluck

import ai.rever.boss.plugin.api.PanelComponentWithUI
import ai.rever.boss.plugin.api.PanelInfo
import androidx.compose.runtime.Composable
import com.arkivanov.decompose.ComponentContext

/**
 * Fluck (browser) panel component.
 *
 * This component provides embedded browser functionality using a platform-specific
 * content provider for the actual browser rendering.
 */
class FluckPanelComponent(
    ctx: ComponentContext,
    override val panelInfo: PanelInfo,
    private val contentProvider: FluckPanelContentProvider
) : PanelComponentWithUI, ComponentContext by ctx {

    @Composable
    override fun Content() {
        contentProvider.FluckPanelContent()
    }
}

/**
 * Provider interface for Fluck panel content.
 * Encapsulates the browser creation, state management, and rendering.
 */
interface FluckPanelContentProvider {
    /**
     * Render the Fluck panel content (browser or error view).
     */
    @Composable
    fun FluckPanelContent()
}
