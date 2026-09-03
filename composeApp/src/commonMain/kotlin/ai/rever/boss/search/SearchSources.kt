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
 * A settings row the global search can offer, flattened out of the Settings index.
 *
 * A plain record rather than the index's own entry type, because that type lives in desktopMain
 * and this service does not.
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
    val keywords: List<String>,
    val highlightable: Boolean,
)

/**
 * The two search sources [GlobalSearchService] cannot reach on its own.
 *
 * Files, tabs, MCP tools and recent pages are all reachable from commonMain, so they are read
 * directly. These two are not, for different reasons:
 *
 * - **Settings** are indexed by `SettingsSearchIndex`, which is desktopMain. Rather than move a
 *   113-entry curated index across the module boundary, the desktop side registers a supplier of
 *   plain records at startup.
 * - **Tools** live on `BossDraggableComponent`, which is per WINDOW, while this service is a
 *   single object shared by all of them. The window that owns the search dialog registers its own.
 *
 * Suppliers rather than snapshots: both sets change while the app runs - a plugin loads, a window
 * takes focus - and a list captured at registration would go stale silently, which for a search
 * index means a tool that exists and cannot be found.
 *
 * Null means "nothing registered", which is the correct answer during startup and in tests. A
 * source that is absent contributes no results rather than failing the whole search.
 */
object SearchSources {
    /** Every tool in the active window's sidebar, hidden ones included. See [toolsSupplier]. */
    @Volatile
    var toolsSupplier: (() -> List<ToolSearchRecord>)? = null

    /** Every row in the Settings index, plugin pages included. */
    @Volatile
    var settingsSupplier: (() -> List<SettingSearchRecord>)? = null

    /** The tools on offer, or none while no window has registered any. */
    fun tools(): List<ToolSearchRecord> = toolsSupplier?.invoke().orEmpty()

    /** The settings rows on offer, or none before the desktop side has registered them. */
    fun settings(): List<SettingSearchRecord> = settingsSupplier?.invoke().orEmpty()

    /** For tests, and for a window tearing down: leaves the service with neither source. */
    fun clear() {
        toolsSupplier = null
        settingsSupplier = null
    }
}
