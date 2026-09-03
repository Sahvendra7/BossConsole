package ai.rever.boss.search

/**
 * A tool the global search can offer, flattened out of a window's sidebar.
 *
 * @property panelId What activates it. NOT the plugin id - `activatePlugin` matches on this.
 * @property label The tool's own name.
 */
data class ToolSearchRecord(
    val panelId: String,
    val label: String,
)

/**
 * A settings row the global search can offer, **already ranked** against the query.
 *
 * A plain record rather than the index's own entry type, because that type lives in desktopMain
 * and this service does not.
 *
 * [score] comes from `SettingsSearchMatcher`, the same ranker the Settings window's own search box
 * uses, rather than from a second scoring pass here - see [SearchSources.settingsSearch]. Scores
 * are therefore on the matcher's scale and are NOT comparable with the [FuzzyMatcher] scores the
 * other sources produce. That costs nothing: `getFilteredResults` orders by category first, so
 * scores are only ever compared within one source.
 *
 * Exactly one of [section], [pluginPageId] and [panelId] is set - the index's own `init` requires
 * it. [panelId] is the entry that navigates *out* of the Settings window; see `panelSignpost`.
 */
data class SettingSearchRecord(
    val label: String,
    val breadcrumb: String,
    val section: String?,
    val pluginPageId: String?,
    val panelId: String?,
    val group: String?,
    val highlightable: Boolean,
    val score: Int,
)

/**
 * An MCP tool the global search can offer.
 *
 * @property enabled Whether it is exposed to clients: permitted AND not switched off. Computed
 *   where the registry is read rather than here, so the row cannot claim a tool is live when no
 *   client can see it.
 */
data class McpToolSearchRecord(
    val name: String,
    val providerId: String,
    val description: String,
    val enabled: Boolean,
)

/** A recently visited page the global search can offer. */
data class PageSearchRecord(
    val url: String,
    val title: String,
)

/**
 * The host state [GlobalSearchService] searches, handed in rather than read out of singletons.
 *
 * Four sources arrive here, for two different kinds of reason.
 *
 * **Reachability.** Settings are indexed by `SettingsSearchIndex`, which is desktopMain, and tools
 * live on `BossDraggableComponent`, which is per WINDOW while this service is one object shared by
 * all of them. Neither is reachable from the service at all.
 *
 * **Testability.** MCP tools and recent pages *are* reachable - `McpToolRegistryImpl` and
 * `RecentBrowserPagesManager` are both commonMain - and were read directly at first. They come
 * through here now because reading a singleton made two things impossible: injecting a fake, so
 * the RBAC filter on MCP tools (the one path where a regression would leak admin-only tool names
 * and descriptions to a signed-out user) had no test at all; and running a unit test without
 * touching `~/.boss`, because reaching the registry forces it to load its disabled-tools file.
 *
 * Suppliers rather than snapshots: every one of these sets changes while the app runs - a plugin
 * loads, a window takes focus, a page is visited - and a list captured at registration would go
 * stale silently, which for a search index means a thing that exists and cannot be found.
 *
 * Absent means "contributes nothing", which is the correct answer during startup and in tests. A
 * missing source returns no results rather than failing the whole search.
 */
object SearchSources {
    private val lock = Any()

    /**
     * Tools per window, keyed by window id.
     *
     * A map and not one slot, because a single slot could not survive two dialogs. The first
     * version cleared unconditionally on dispose, so closing one window's dialog left every other
     * window searching no tools for the rest of the session. Guarding the clear by identity fixed
     * only half of that: the *registration* still overwrote whoever was there, so while two
     * dialogs were open one window searched the other's tools, and closing the newer one still
     * emptied the older one's slot.
     *
     * Keyed, both go away. A window's dialog registers under its own id, searches under its own
     * id, and removes only its own entry - which is also the only arrangement where the tools
     * offered belong to the window whose `activatePlugin` will be asked to open them.
     */
    @Volatile
    private var toolsByWindow: Map<String, () -> List<ToolSearchRecord>> = emptyMap()

    /**
     * Rank the Settings index against a query, best first.
     *
     * A search function and not a list of rows, so that "what a settings match is worth" has ONE
     * definition. Registering rows meant ranking them here, against a second scorer with its own
     * keyword penalty - and the two disagreed in a way that lost results rather than merely
     * reordering them: `FuzzyMatcher` is a strict subsequence matcher over a single target, so the
     * global search could not match "user agent" to "Browser Identity" while the Settings window,
     * which tokenises the query, could. `SettingsSearchMatcher` exists for exactly that, and this
     * is how the global search gets to use it.
     */
    @Volatile
    var settingsSearch: ((String) -> List<SettingSearchRecord>)? = null

    /** Every MCP tool this user may see, disabled ones included. See [McpToolSearchRecord]. */
    @Volatile
    var mcpToolsSupplier: (() -> List<McpToolSearchRecord>)? = null

    /** The browser's recent pages. */
    @Volatile
    var recentPagesSupplier: (() -> List<PageSearchRecord>)? = null

    /** Register [windowId]'s sidebar tools. Paired with [unregisterTools] on the same id. */
    fun registerTools(
        windowId: String,
        supplier: () -> List<ToolSearchRecord>,
    ) {
        synchronized(lock) { toolsByWindow = toolsByWindow + (windowId to supplier) }
    }

    /** Forget [windowId]'s tools. Leaves every other window's registration alone. */
    fun unregisterTools(windowId: String) {
        synchronized(lock) { toolsByWindow = toolsByWindow - windowId }
    }

    /**
     * The tools [windowId] offers, or none if that window never registered any.
     *
     * Null [windowId] - a search with no window behind it, which is every test and the state
     * before any dialog has opened - contributes nothing rather than guessing at a window.
     */
    fun tools(windowId: String?): List<ToolSearchRecord> = windowId?.let { toolsByWindow[it] }?.invoke().orEmpty()

    /** The ranked settings rows, or none before the desktop side has registered its matcher. */
    fun settings(query: String): List<SettingSearchRecord> = settingsSearch?.invoke(query).orEmpty()

    /** The MCP tools on offer, or none if the host registered none. */
    fun mcpTools(): List<McpToolSearchRecord> = mcpToolsSupplier?.invoke().orEmpty()

    /** The recent pages on offer, or none if the host registered none. */
    fun recentPages(): List<PageSearchRecord> = recentPagesSupplier?.invoke().orEmpty()

    /**
     * Drop every registration.
     *
     * **Tests only**, which is why it says so in the name. An earlier version was documented as
     * being "for a window tearing down" as well, which was an invitation to break the app: the
     * host sources are registered exactly once at startup and never again, so calling this from a
     * closing window would drop settings, MCP tools and recent pages out of the search for the
     * rest of the session, with absence as the only symptom. A window tearing down wants
     * [unregisterTools] with its own id.
     */
    fun clearForTests() {
        synchronized(lock) { toolsByWindow = emptyMap() }
        settingsSearch = null
        mcpToolsSupplier = null
        recentPagesSupplier = null
    }
}
