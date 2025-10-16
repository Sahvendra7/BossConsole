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
            
            // Continue without passkey support if initialization fails
            isInitialized = false
        }
    }

}
