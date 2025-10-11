package ai.rever.boss.services.passkey

import ai.rever.boss.services.supabase.AuthService

/**
 * Platform-specific initialization for passkey services on desktop
 * This initializes the DesktopPasskeyService and registers it with AuthService
 */
object PasskeyPlatformInit {
    
    private var isInitialized = false
    private var desktopPasskeyService: DesktopPasskeyService? = null
    
    /**
     * Initialize the desktop passkey service
     * Should be called early in the application lifecycle
     */
    fun initialize() {
        if (isInitialized) {
            println("PasskeyPlatformInit: Already initialized")
            return
        }
        
        try {
            println("PasskeyPlatformInit: Initializing desktop passkey service...")
            
            // Create and initialize the desktop passkey service
            desktopPasskeyService = DesktopPasskeyService()
            
            // Register with AuthService
            AuthService.setPasskeyService(desktopPasskeyService!!)
            
            isInitialized = true
            println("PasskeyPlatformInit: Desktop passkey service initialized successfully")
            
        } catch (e: Exception) {
            println("PasskeyPlatformInit: Failed to initialize passkey service: ${e.message}")
            e.printStackTrace()
            
            // Continue without passkey support if initialization fails
            isInitialized = false
        }
    }
    
    /**
     * Check if passkey service is initialized and available
     */
    fun isAvailable(): Boolean {
        return isInitialized && desktopPasskeyService != null
    }
    
    /**
     * Get the current passkey service instance
     */
    fun getService(): DesktopPasskeyService? {
        return if (isInitialized) desktopPasskeyService else null
    }
    
    /**
     * Clean up resources when the application is shutting down
     */
    fun cleanup() {
        try {
            desktopPasskeyService?.cleanup()
            desktopPasskeyService = null
            isInitialized = false
            println("PasskeyPlatformInit: Cleanup completed")
        } catch (e: Exception) {
            println("PasskeyPlatformInit: Error during cleanup: ${e.message}")
        }
    }
}