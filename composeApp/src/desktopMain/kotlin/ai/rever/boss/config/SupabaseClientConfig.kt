package ai.rever.boss.config

/**
 * Configuration for Supabase client.
 *
 * The Supabase credentials can be provided through:
 * 1. Environment variable: SUPABASE_URL, SUPABASE_ANON_KEY
 * 2. System property: SUPABASE_URL, SUPABASE_ANON_KEY
 * 3. local.properties file: SUPABASE_URL=..., SUPABASE_ANON_KEY=...
 * 4. Fallback to production values
 */
object SupabaseClientConfig {
    /**
     * Supabase URL loaded from secure sources.
     */
    val url: String by lazy {
        ConfigLoader.getConfig("SUPABASE_URL")
            ?: "https://api.risaboss.com" // Production default
    }

    /**
     * Supabase anonymous key loaded from secure sources.
     */
    val anonKey: String by lazy {
        ConfigLoader.getConfig("SUPABASE_ANON_KEY")
            ?: "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InBjbndxYW1xZG5zYWRyYW51Zmp2Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3NTkxMDUwMzMsImV4cCI6MjA3NDY4MTAzM30.WZ6jSKuqM2EMyZLgoGJnI8Bn_Sdwk6plW0PkVNLIYVY" // Production default
    }

    /**
     * Supabase Functions URL loaded from secure sources.
     */
    val functionUrl: String by lazy {
        ConfigLoader.getConfig("SUPABASE_FUNCTION_URL")
            ?: "https://api.risaboss.com/functions/v1/passkey" // Production default
    }
}
