package ai.rever.boss.services.supabase

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
     * Initialize from build-time configuration
     */
    fun initializeFromEnvironment() {
        // Use self-hosted Supabase on GKE cluster
        // Deployed infrastructure endpoints via Kong Gateway
        val url = "https://api.risaboss.com"  // Domain-based unified Supabase API endpoint
        // NOTE: Our self-hosted setup now has a unified API endpoint that routes:
        // - /auth/v1/* -> GoTrue service 
        // - /rest/v1/* -> PostgREST service  
        // - /realtime/v1/* -> Realtime service 
        // - /storage/v1/* -> Storage service
        // - /functions/v1/* -> Edge Functions service
        val anonKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImxvY2FsaG9zdCIsInJvbGUiOiJhbm9uIiwiaWF0IjoxNzU0Nzg1MDU0LCJleHAiOjE3ODYzMjEwNTR9.UR-amMvudG2h3iBBzBfRPjH6psOhyWYrrq3yhc_s-s4"
        
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
    
    /**
     * Get environment variable (placeholder - implement based on your platform)
     */
    private fun getEnvironmentVariable(key: String): String? {
        // This is a placeholder. In a real application, you would:
        // 1. Read from actual environment variables
        // 2. Read from a secure settings file
        // 3. Use a secrets management service
        
        // For now, we'll check system properties and environment
        return System.getProperty(key) ?: System.getenv(key)
    }
}