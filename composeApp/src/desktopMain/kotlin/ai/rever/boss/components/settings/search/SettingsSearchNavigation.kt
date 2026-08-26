package ai.rever.boss.components.settings.search

import ai.rever.boss.components.events.PanelEventBus
import ai.rever.boss.plugin.api.PanelId
import ai.rever.boss.utils.WindowFocusManager
import ai.rever.boss.utils.logging.BossLogger
import ai.rever.boss.utils.logging.LogCategory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private val logger = BossLogger.forComponent("SettingsSearchNavigation")

/**
 * Open a sidebar panel in the main window, for a search hit whose target is not in this window.
 *
 * The one navigation Settings search performs that Settings cannot show the result of. It exists
 * because removing `Settings > AI Providers` left the words a user types for it - "api key",
 * "anthropic", "claude" - matching nothing at all; see `panelSignpost` for what builds the entry.
 *
 * Two steps rather than one, because opening a panel behind an always-visible Settings window
 * would look exactly like the click doing nothing: [PanelEventBus] puts the panel up, and
 * [WindowFocusManager.focusWindow] is what the user actually sees. Raising the main window rather
 * than closing Settings - the user picked one thing out of a window they may still be reading.
 *
 * [WindowFocusManager] registers main windows only (`BossWindow`), so `resolveActionableWindowId`
 * cannot hand back the Settings window itself. Null means no BOSS window is registered at all,
 * which is not reachable from a click inside one - logged rather than ignored, because if it ever
 * is, the click is a silent no-op and nothing else would say so.
 *
 * `defaultOrder` is deliberately not matched: the panel-open handler in `BossAppEventBusEffects`
 * compares `panelId` and `pluginId` only. That matters here - `PanelIds.SECRET_MANAGER` says 2 and
 * the plugin registers 24 - and is the same mismatch `PanelIdResolutionTest` exists for.
 */
internal fun revealPanel(
    panel: PanelId,
    label: String,
    scope: CoroutineScope,
) {
    val windowId = WindowFocusManager.resolveActionableWindowId()
    if (windowId == null) {
        logger.warn(
            LogCategory.UI,
            "Settings search could not reveal a panel: no window is registered",
            mapOf("panelId" to panel.panelId, "label" to label),
        )
        return
    }
    scope.launch {
        PanelEventBus.openPanel(panel, sourceWindowId = windowId)
        WindowFocusManager.focusWindow(windowId)
    }
}
