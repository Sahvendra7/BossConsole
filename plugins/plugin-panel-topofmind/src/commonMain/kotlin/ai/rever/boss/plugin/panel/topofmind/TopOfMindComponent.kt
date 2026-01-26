package ai.rever.boss.plugin.panel.topofmind

import ai.rever.boss.plugin.api.LocalSplitViewOperations
import ai.rever.boss.plugin.api.LocalWorkspaceDataProvider
import ai.rever.boss.plugin.api.PanelComponentWithUI
import ai.rever.boss.plugin.api.PanelInfo
import ai.rever.boss.plugin.api.TabIcon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Tab
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import com.arkivanov.decompose.ComponentContext

/**
 * Top of Mind panel component.
 *
 * This component displays all active tabs organized by workspace with search functionality.
 * It uses CompositionLocals for accessing workspace and split view providers.
 *
 * @param ctx Component context from Decompose
 * @param panelInfo Panel metadata
 * @param collectAllActiveTabs Function to collect all active tabs from the split view state
 * @param getAllPanelStates Function to get current panel states for reactivity
 * @param faviconLoader Function to load favicon for a tab
 * @param getTabUrl Function to get URL from a tab (for FluckTabInfo)
 * @param getFaviconCacheKey Function to get favicon cache key from a tab
 * @param getFallbackIcon Function to get fallback icon for a tab
 */
class TopOfMindComponent(
    ctx: ComponentContext,
    override val panelInfo: PanelInfo,
    private val collectAllActiveTabs: () -> List<ActiveTab>,
    private val getAllPanelStates: @Composable () -> List<Triple<String, Int, List<String>>>,
    private val faviconLoader: @Composable (String?) -> TabIcon.Image?,
    private val getTabUrl: (ActiveTab) -> String? = { null },
    private val getFaviconCacheKey: (ActiveTab) -> String? = { null },
    private val getFallbackIcon: (ActiveTab) -> ImageVector = { Icons.Outlined.Tab }
) : PanelComponentWithUI, ComponentContext by ctx {

    @Composable
    override fun Content() {
        val splitViewOperations = LocalSplitViewOperations.current
        val workspaceDataProvider = LocalWorkspaceDataProvider.current

        TopOfMindContent(
            splitViewOperations = splitViewOperations,
            workspaceDataProvider = workspaceDataProvider,
            collectAllActiveTabs = collectAllActiveTabs,
            getAllPanelStates = getAllPanelStates,
            faviconLoader = faviconLoader,
            getTabUrl = getTabUrl,
            getFaviconCacheKey = getFaviconCacheKey,
            getFallbackIcon = getFallbackIcon
        )
    }
}
