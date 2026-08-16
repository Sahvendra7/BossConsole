package ai.rever.boss.components.home

import ai.rever.boss.utils.logging.BossLogger
import ai.rever.boss.utils.logging.LogCategory

private val logger = BossLogger.forComponent("HomeCatalogAccess")

/**
 * The store half of the home screen's tool grid: what could be installed, and how to install it.
 *
 * Two methods rather than exposing a `PluginRepository`, because those are the only two
 * questions the grid asks, and narrowing them keeps the screen unable to do anything else with
 * the store.
 */
interface HomeCatalogProvider {
    /**
     * Store rows the grid could offer, already reduced and already compatibility-checked.
     *
     * Returns empty rather than failing when the store is unreachable: a home screen that
     * cannot reach the network should show the tools that are installed, not an error. The
     * implementation logs the failure.
     */
    suspend fun discoverable(): List<HomeStorePluginInput>

    /**
     * Install one plugin by id, resolving it against the store.
     *
     * The failure message is shown to the user, so it says what happened rather than surfacing
     * a transport error.
     */
    suspend fun install(pluginId: String): Result<Unit>
}

/**
 * Holds the desktop implementation of [HomeCatalogProvider] for the commonMain home screen.
 *
 * A holder for the same reason as
 * [ai.rever.boss.services.llm.BrokeredCredentialAccess]: the implementation speaks HTTP to the
 * plugin store and installs jars, so it lives in `desktopMain`, while `HomeScreen` is in
 * `commonMain`. Desktop startup registers it.
 *
 * Null until then, and null on any build that registers none - in which case the grid shows
 * only what is installed, which is a strictly better screen than the one this replaces rather
 * than a broken one.
 */
object HomeCatalogAccess {
    @Volatile
    private var provider: HomeCatalogProvider? = null

    /** Called once from desktop startup. */
    fun initialize(implementation: HomeCatalogProvider) {
        provider = implementation
        logger.debug(LogCategory.SYSTEM, "HomeCatalogAccess initialized")
    }

    fun current(): HomeCatalogProvider? = provider
}
