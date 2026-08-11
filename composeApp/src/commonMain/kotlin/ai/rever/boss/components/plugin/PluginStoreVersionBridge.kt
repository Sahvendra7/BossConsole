package ai.rever.boss.components.plugin

/** What the store has for a plugin, as far as installing its released build goes. */
sealed class StoreVersionLookup {
    data class Available(
        val displayName: String,
        val version: String,
    ) : StoreVersionLookup()

    /** The store has no row for this plugin - a locally built plugin that was never published. */
    data object NotPublished : StoreVersionLookup()

    /** The store could not be asked (offline, not initialised), which is not the same as absent. */
    data class Unavailable(
        val message: String,
    ) : StoreVersionLookup()
}

/**
 * Bridges the commonMain host UI to the desktopMain plugin store, for going back to the released
 * build of a plugin you are running a local or hot-reloaded copy of.
 *
 * Separate from [PluginUpdateBridge] on purpose. That one is update-shaped: everything it can do is
 * gated behind `isNewerVersion`, and the store version wanted here is usually the SAME as, or older
 * than, the local build that replaced it - so an update check reports "up to date" and its install
 * path refuses to run.
 */
expect object PluginStoreVersionBridge {
    /** Ask the store - and only the store - what it publishes for [pluginId]. */
    suspend fun lookup(pluginId: String): StoreVersionLookup

    /**
     * Download the store's [version] of [pluginId] and swap it in for whatever is running,
     * reusing [manager] to unload and load. Returns the version actually installed.
     */
    suspend fun installStoreVersion(
        pluginId: String,
        version: String,
        manager: DynamicPluginManager,
    ): Result<String>
}
