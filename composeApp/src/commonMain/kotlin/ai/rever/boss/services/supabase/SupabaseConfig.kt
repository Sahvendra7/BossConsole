package ai.rever.boss.services.supabase

import ai.rever.boss.components.plugin.panels.right_top.getEnvironmentVariable
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.storage.storage
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.functions.functions
import io.ktor.client.engine.cio.CIO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Supabase configuration and client management
 */
object SupabaseConfig {
    // Configuration keys - these should be stored securely
    private const val SUPABASE_URL_KEY = "SUPABASE_URL"
    private const val SUPABASE_ANON_KEY = "SUPABASE_ANON_KEY"
    
    private var _client: SupabaseClient? = null
    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()
    
    /**
     * Initialize the Supabase client with the provided credentials
     * @param url The Supabase project URL
     * @param anonKey The Supabase anonymous key
     */
    fun initialize(url: String, anonKey: String) {
        if (_client != null) {
            println("Supabase client already initialized")
            return
        }
        
        try {
            // Ensure URL has https:// prefix
            val fullUrl = if (url.startsWith("http://") || url.startsWith("https://")) {
                url
            } else {
                "https://$url"
            }
            
            _client = createSupabaseClient(
                supabaseUrl = fullUrl,
                supabaseKey = anonKey
            ) {
                // Install modules
                install(Auth) {
                    // Configure redirect URL for email verification
                    scheme = "boss"
                    host = "auth"
                    // Enable persistent session management and auto-refresh for proper session persistence
                    alwaysAutoRefresh = true
                    autoLoadFromStorage = true
                }
                install(Postgrest)
                install(Realtime)
                install(Storage)
                install(Functions)
                
                // Configure HTTP client
                httpEngine = CIO.create()
            }
            
            _isInitialized.value = true
            println("Supabase client initialized successfully")
        } catch (e: Exception) {
            println("Failed to initialize Supabase client: ${e.message}")
            throw e
        }
    }
    
    /**
     * Initialize from secure configuration sources
     * Priority: Environment variables → System properties → local.properties → fallback
     */
    fun initializeFromEnvironment() {
        val url = getSupabaseUrl()
        val anonKey = getSupabaseAnonKey()

        initialize(url, anonKey)
    }
    
    /**
     * Get the Supabase client instance
     * @throws IllegalStateException if the client is not initialized
     */
    val client: SupabaseClient
        get() = _client ?: throw IllegalStateException("Supabase client not initialized. Call initialize() first.")
    
    /**
     * Get the Auth module
     */
    val auth: Auth
        get() = client.auth
    
    /**
     * Get the Postgrest module for database operations
     */
    val postgrest: Postgrest
        get() = client.postgrest
    
    /**
     * Get the Realtime module
     */
    val realtime: Realtime
        get() = client.realtime
    
    /**
     * Get the Storage module
     */
    val storage: Storage
        get() = client.storage
    
    /**
     * Get the Functions module
     */
    val functions: Functions
        get() = client.functions
    
    /**
     * Clear the client instance (useful for testing or logout)
     */
    fun clear() {
        _client = null
        _isInitialized.value = false
    }
}

/**
 * Platform-specific Supabase URL configuration
 */
expect fun getSupabaseUrl(): String

/**
 * Platform-specific Supabase anonymous key configuration
 */
expect fun getSupabaseAnonKey(): String

/**
 * Platform-specific Supabase Functions URL configuration
 */
expect fun getSupabaseFunctionUrl(): String