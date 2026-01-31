package ai.rever.boss.plugin.repository.remote

/**
 * Configuration for the remote plugin store.
 *
 * This should be initialized by the main application with Supabase credentials
 * before using RemotePluginRepository.
 */
object PluginStoreConfig {
    private var _functionUrl: String? = null
    private var _anonKey: String? = null
    private var _accessToken: String? = null

    /**
     * Supabase Functions base URL (e.g., "https://api.risaboss.com/functions/v1")
     */
    val functionUrl: String
        get() = _functionUrl
            ?: throw IllegalStateException("PluginStoreConfig not initialized. Call initialize() first.")

    /**
     * Supabase anonymous key for API access
     */
    val anonKey: String
        get() = _anonKey
            ?: throw IllegalStateException("PluginStoreConfig not initialized. Call initialize() first.")

    /**
     * Optional JWT access token for authenticated requests (ratings, publishing)
     */
    var accessToken: String?
        get() = _accessToken
        set(value) { _accessToken = value }

    /**
     * Plugin store endpoint URL (functionUrl + /plugin-store)
     */
    val pluginStoreUrl: String
        get() = "$functionUrl/plugin-store"

    /**
     * Whether the configuration has been initialized
     */
    val isInitialized: Boolean
        get() = _functionUrl != null && _anonKey != null

    /**
     * Initialize the plugin store configuration.
     *
     * @param functionUrl Supabase Functions base URL
     * @param anonKey Supabase anonymous key
     * @param accessToken Optional JWT access token for authenticated requests
     */
    fun initialize(functionUrl: String, anonKey: String, accessToken: String? = null) {
        _functionUrl = functionUrl.removeSuffix("/")
        _anonKey = anonKey
        _accessToken = accessToken
    }

    /**
     * Clear the configuration (for testing or logout)
     */
    fun clear() {
        _functionUrl = null
        _anonKey = null
        _accessToken = null
    }
}
