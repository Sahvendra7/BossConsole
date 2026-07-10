package ai.rever.boss.config

/**
 * Configuration for JxBrowser.
 * 
 * The license key can be provided through:
 * 1. Environment variable: JXBROWSER_LICENSE_KEY
 * 2. System property: jxbrowser.license.key
 * 3. local.properties file: jxbrowser.license.key=YOUR_KEY
 * 4. Fallback to development key (should be removed in production)
 */
object JxBrowserConfig {
    /**
     * JxBrowser license key loaded from secure sources.
     * In production, remove the default fallback value.
     */
    val licenseKey: String by lazy {
        ConfigLoader.getConfig("JXBROWSER_LICENSE_KEY")
            ?: ConfigLoader.getConfig("jxbrowser.license.key")
            ?: "58NM0Q9E2PZMOJX1AVU29ISJGWZ8NX2CM2SZ1NUO4I641T6J7QJWXYF88K6T2GDPHV9H6QW0X9BU9A1TXGJI4MOFNE15IGJRII375IUZ6I1NP7DI0YU7XXWRE2BV0X2HGYV2T2963SEHQ5B7K2APDM8MJZKJE5Y6QEGF" // TODO: Remove in production
    }
    
    // Other JxBrowser configuration options
    val defaultUrl: String = ConfigLoader.getConfig("jxbrowser.default.url",
        "https://www.risalabs.ai") ?: "https://www.risalabs.ai"
    
    // OFF_SCREEN mode for lightweight Compose popups compatibility
    // HARDWARE_ACCELERATED has issues with Compose overlays rendering behind browser
    val renderingMode = com.teamdev.jxbrowser.engine.RenderingMode.OFF_SCREEN

} 
