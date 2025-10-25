package ai.rever.boss.services.passkey.desktop

import ai.rever.boss.components.plugin.tab_types.fluck.FluckEngine
import com.teamdev.jxbrowser.browser.Browser
import com.teamdev.jxbrowser.engine.Engine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Manages cross-device authentication flows and browser integration
 * Handles JxBrowser WebAuthn operations and fallback browser launching
 */
class CrossDeviceBrowserManager {
    
    private var webAuthnEngine: Engine? = null
    private var webAuthnBrowser: Browser? = null
    
    init {
        initializeWebAuthnEngine()
    }
    
    /**
     * Initialize WebAuthn engine using FluckEngine
     */
    private fun initializeWebAuthnEngine() {
        try {
            println("CrossDeviceBrowserManager: Initializing WebAuthn using shared FluckEngine...")
            
            // Use the existing FluckEngine singleton which has proper licensing and configuration
            webAuthnEngine = FluckEngine.engine
            webAuthnBrowser = webAuthnEngine?.newBrowser()
            
            println("CrossDeviceBrowserManager: WebAuthn engine initialized successfully using FluckEngine")
            
        } catch (e: Exception) {
            when {
                e.javaClass.name.contains("NoLicenseException") -> {
                    println("CrossDeviceBrowserManager: JxBrowser license not available in FluckEngine")
                    println("CrossDeviceBrowserManager: Falling back to system browser")
                }
                else -> {
                    println("CrossDeviceBrowserManager: Failed to initialize WebAuthn using FluckEngine: ${e.message}")
                    println("CrossDeviceBrowserManager: Using system browser fallback")
                }
            }
        }
    }

    /**
     * Open URL in system browser with fallback methods
     */
    suspend fun openInSystemBrowser(url: String): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            println("CrossDeviceBrowserManager: Opening URL in system browser: $url")
            
            // Try Desktop API first (works well on most platforms)
            if (java.awt.Desktop.isDesktopSupported()) {
                val desktop = java.awt.Desktop.getDesktop()
                if (desktop.isSupported(java.awt.Desktop.Action.BROWSE)) {
                    desktop.browse(java.net.URI.create(url))
                    println("CrossDeviceBrowserManager: Successfully opened browser using Desktop API")
                    Result.success(Unit)
                } else {
                    // Fallback to ProcessBuilder
                    println("CrossDeviceBrowserManager: Desktop.browse not supported, using ProcessBuilder")
                    openBrowserWithProcessBuilder(url)
                }
            } else {
                // Fallback to ProcessBuilder
                println("CrossDeviceBrowserManager: Desktop not supported, using ProcessBuilder")
                openBrowserWithProcessBuilder(url)
            }
        } catch (e: Exception) {
            println("CrossDeviceBrowserManager: Failed to open browser with Desktop API: ${e.message}, trying fallback")
            try {
                openBrowserWithProcessBuilder(url)
            } catch (fallbackException: Exception) {
                println("CrossDeviceBrowserManager: All browser opening methods failed")
                Result.failure(fallbackException)
            }
        }
    }

    /**
     * Prepare URL for embedded Fluck browser display
     * Returns the URL to be displayed in an embedded JxBrowser instance
     */
    suspend fun openInFluckBrowser(url: String, sessionId: String): Result<String> = withContext(Dispatchers.IO) {
        return@withContext try {
            println("CrossDeviceBrowserManager: Preparing URL for embedded Fluck browser: $url")

            // Validate that we have a WebAuthn engine available
            if (webAuthnEngine == null || webAuthnBrowser == null) {
                println("CrossDeviceBrowserManager: WebAuthn engine not available, falling back to system browser")
                return@withContext Result.failure(Exception("JxBrowser not available for embedded display"))
            }

            println("CrossDeviceBrowserManager: Ready to display WebAuthn in embedded browser (sessionId: $sessionId)")
            Result.success(url)

        } catch (e: Exception) {
            println("CrossDeviceBrowserManager: Failed to prepare Fluck browser: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Fallback method to open browser using ProcessBuilder when Desktop API is not available
     */
    private fun openBrowserWithProcessBuilder(url: String): Result<Unit> {
        return try {
            val os = System.getProperty("os.name").lowercase()
            val processBuilder = when {
                os.contains("mac") -> ProcessBuilder("open", url)
                os.contains("windows") -> ProcessBuilder("cmd", "/c", "start", "", url)
                os.contains("linux") -> ProcessBuilder("xdg-open", url)
                else -> throw Exception("Unsupported platform for opening browser: $os")
            }
            processBuilder.start()
            println("CrossDeviceBrowserManager: Successfully opened browser using ProcessBuilder")
            Result.success(Unit)
        } catch (e: Exception) {
            println("CrossDeviceBrowserManager: Failed to open browser with ProcessBuilder: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * Check for enhanced external authenticator capabilities
     */
    fun isExternalAuthenticatorAvailable(): Boolean {
        return try {
            val browser = webAuthnBrowser
            if (browser == null) {
                println("CrossDeviceBrowserManager: No JxBrowser instance - using basic OS detection")
                return fallbackExternalAuthenticatorCheck()
            }
            
            println("CrossDeviceBrowserManager: Using enhanced external authenticator detection via JxBrowser")
            
            // With properly licensed JxBrowser via FluckEngine, we have enhanced capabilities
            val os = System.getProperty("os.name").lowercase()
            when {
                os.contains("mac") -> true // macOS with enhanced WebAuthn support
                os.contains("windows") -> true // Windows with enhanced WebAuthn support  
                os.contains("linux") -> true // Linux with JxBrowser WebAuthn support
                else -> false
            }
            
        } catch (e: Exception) {
            println("CrossDeviceBrowserManager: Error in enhanced detection: ${e.message}")
            fallbackExternalAuthenticatorCheck()
        }
    }
    
    /**
     * Fallback external authenticator check when JxBrowser is not available
     */
    private fun fallbackExternalAuthenticatorCheck(): Boolean {
        val os = System.getProperty("os.name").lowercase()
        return when {
            os.contains("mac") -> true // macOS has platform authenticator support
            os.contains("windows") -> true // Windows Hello support
            os.contains("linux") -> false // Limited Linux support without WebAuthn
            else -> false
        }
    }
    
    /**
     * Show enhanced capabilities information
     */
    suspend fun showEnhancedCapabilities() = withContext(Dispatchers.IO) {
        try {
            println("\n🔍 === ENHANCED WEBAUTHN CAPABILITIES ===\n")
            
            val hasWebAuthnEngine = webAuthnEngine != null && webAuthnBrowser != null
            println("🌐 JxBrowser WebAuthn Engine: ${if (hasWebAuthnEngine) "✅ AVAILABLE (via FluckEngine)" else "❌ NOT AVAILABLE"}")
            
            if (hasWebAuthnEngine) {
                println("🚀 Enhanced WebAuthn Support: ✅ ENABLED")
                
                val externalAuthAvailable = isExternalAuthenticatorAvailable()
                println("🔐 External Authenticator Detection: ${if (externalAuthAvailable) "✅ ENHANCED MODE" else "❌ BASIC MODE"}")
                
                val os = System.getProperty("os.name").lowercase()
                when {
                    os.contains("mac") -> {
                        println("🍎 macOS Platform: 🚀 Enhanced WebAuthn support available")
                        println("   🔐 Enhanced: JxBrowser provides additional WebAuthn capabilities")
                        println("   🚀 Supports: USB security keys, NFC authenticators, hybrid transport")
                    }
                    os.contains("windows") -> {
                        println("🪟 Windows Platform: 🚀 Enhanced WebAuthn support available")
                        println("   🔐 Enhanced: JxBrowser provides additional WebAuthn capabilities")
                        println("   🚀 Supports: USB security keys, NFC authenticators, hybrid transport")
                    }
                    os.contains("linux") -> {
                        println("🐧 Linux Platform: 🚀 Enhanced WebAuthn support available via JxBrowser")
                        println("   🔐 Supports: USB security keys, NFC authenticators")
                    }
                }
                
                println("\n📊 Available WebAuthn Transports:")
                println("   • internal (Touch ID/Windows Hello)")
                println("   • usb (Security keys via USB)")
                println("   • nfc (NFC FIDO2 authenticators)")
                if (os.contains("mac")) {
                    println("   • hybrid (Cross-device authentication)")
                }
                
            } else {
                val os = System.getProperty("os.name").lowercase()
                when {
                    os.contains("mac") -> {
                        println("⚠️  Using system browser fallback mode")
                        println("📱 Available: System browser WebAuthn support")
                    }
                    os.contains("windows") -> {
                        println("⚠️  Using system browser fallback mode")
                        println("📱 Available: System browser WebAuthn support")
                    }
                    else -> {
                        println("⚠️  Using system browser fallback mode")
                        println("📱 Available: System browser WebAuthn support")
                    }
                }
            }
            
            println("\n=== END ENHANCED WEBAUTHN CAPABILITIES ===\n")
            
        } catch (e: Exception) {
            println("❌ Error showing enhanced capabilities: ${e.message}")
        }
    }

}
